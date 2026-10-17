import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { ApiError, ensure, HttpApi, login, unique, type Api, type Row } from './api.ts';
import { accounts, disciplineBody, memberBody, organizationBody, ORGANIZATION_NAME, REGULATION_CODE,
  regulationTemplateBody, regulationVersionBody, roleBody, templateBody, userBody } from './bodies.ts';
import { config, flags, loadLocalEnv } from './config.ts';
import { calendarEntries, calendarPlan, calendarSettings, description, metadata, readMetadata, swimmerPool,
  type CalendarEntry, type CalendarEvent, type CalendarMeta, type CalendarOptions, type Swimmer } from './calendar-plan.ts';
import { calendarCompetition, type CalendarRegistration, type CalendarTokens } from './calendar-competition.ts';
import { findParticipant, importEntries, participantLogin } from './seed.ts';
import { ownerEmail, profileKey, shiftDate } from './plan.ts';
import { workbook } from './workbook.ts';

export type RunOptions = CalendarOptions & {
  adminEmail: string; adminPassword: string; passwords: Record<string, string>; ownerEmail?: string;
  enabled: boolean; now?: Date;
};
type Context = { organization: Row; sport: Row; version: Row; tokens: CalendarTokens; parentTokens: Map<string, string> };
type Summary = { week: number; date: string; slug: unknown; status: unknown; registrations: number; states: Record<string, number>; [key: string]: unknown };
const resetError = (message: string): never => { throw new Error(`${message} Выполните DEMO_CALENDAR=true npx tsx reset.ts, затем повторите calendar.ts.`); };
const registrationKey = (profile: Parameters<typeof profileKey>[0], code: string) => profileKey(profile) + '|' + code;
const registrations = (api: Api, event: Row, token: string) => api.request<CalendarRegistration[]>('GET', `/events/${event.id}/registrations`, undefined, token);
function progressDate(requested: string, meta: Pick<CalendarMeta, 'start' | 'weeks' | 'anchorDate' | 'prefix'>, events: Row[], organizationId: string) {
  let asOf = requested > meta.anchorDate ? requested : meta.anchorDate;
  for (const event of events) {
    if (event.organizationId !== organizationId) continue;
    const actual = readMetadata(event.description);
    if (!actual || actual.prefix !== meta.prefix || actual.start !== meta.start || actual.weeks !== meta.weeks || actual.anchorDate !== meta.anchorDate) continue;
    // Infer already achieved progress without adding a state store or changing the API.
    const delta = event.status === 'COMPLETED' ? 7 : event.status === 'REGISTRATION_CLOSED' ? 0 : event.status === 'REGISTRATION_OPEN' ? -21 : undefined;
    if (delta !== undefined) {
      const observed = shiftDate(meta.start, actual.week * 7 + delta);
      if (observed > asOf) asOf = observed;
    }
  }
  return asOf;
}

async function setup(api: Api, options: RunOptions, pool: Swimmer[]): Promise<Context> {
  const admin = await login(api, options.adminEmail, options.adminPassword);
  const me = await api.request<Row & { roles: string[] }>('GET', '/auth/me', undefined, admin);
  if (!me.roles.includes('platform_admin')) throw new Error('ADMIN_EMAIL должен иметь роль platform_admin.');
  // Validate an existing owner before creating any demo objects.
  const existingOwner = await findParticipant(api, ownerEmail(options.ownerEmail), admin);
  if (existingOwner) {
    await participantLogin(api, ownerEmail(options.ownerEmail), options.passwords.PARENT);
    const details = await api.request<Row>('GET', `/users/${existingOwner.id}`, undefined, admin);
    const expected = pool.find(p => p.email === ownerEmail(options.ownerEmail))!;
    if (profileKey(details.profile as Parameters<typeof profileKey>[0]) !== profileKey(expected.profile))
      throw new Error('Профиль DEMO_OWNER_EMAIL отличается от ребёнка S02 C. Существующий профиль не меняется.');
  }
  const organization = await ensure(api, admin, '/organizations', row => row.name === ORGANIZATION_NAME, organizationBody(), ORGANIZATION_NAME);
  const memberPath = `/organizations/${organization.id}/members`;
  const members = await api.request<Row[]>('GET', memberPath, undefined, admin);
  const tokens = { admin, organizer: '', judge: '', secretary: '' };
  for (const account of accounts) {
    const password = options.passwords[account.key];
    let token: string;
    try { token = await login(api, account.email, password); }
    catch (error) {
      if (!(error instanceof ApiError) || error.code !== 'identity.invalid_credentials') throw error;
      await api.request('POST', '/users', userBody(account, password), admin);
      token = await login(api, account.email, password);
    }
    const user = await api.request<Row & { roles: string[] }>('GET', '/auth/me', undefined, token);
    if (user.email !== account.email) throw new Error('API вернул другого пользователя.');
    if (!user.roles.includes(account.role)) {
      await api.request('POST', `/users/${user.id}/roles`, roleBody(account.role), admin);
      token = await login(api, account.email, password);
    }
    if (account.memberRole) {
      const member = unique(members, row => row.userId === user.id, account.email);
      if (member && member.memberRole !== account.memberRole) throw new Error(`Неверное членство ${account.email}.`);
      if (!member) await api.request('POST', memberPath, memberBody(user.id, account.memberRole), admin);
    }
    if (account.key === 'ORG') tokens.organizer = await login(api, account.email, password);
    if (account.key === 'SEC') tokens.secretary = token;
    if (account.key === 'JUDGE') tokens.judge = token;
  }
  const sport = unique(await api.request<Row[]>('GET', '/sports', undefined, admin), row => row.code === 'swim', 'swim');
  if (!sport) throw new Error('Не найден спорт swim; примените миграции main.');
  const regulation = await ensure(api, admin, '/regulation-templates', row => row.code === REGULATION_CODE && row.sportId === sport.id,
    regulationTemplateBody(sport.id), REGULATION_CODE);
  let version = unique(await api.request<Row[]>('GET', '/regulation-versions', undefined, admin), row => row.regulationTemplateId === regulation.id && row.versionNo === 1, 'v1');
  if (!version) version = await api.request<Row>('POST', `/regulation-templates/${regulation.id}/versions`, regulationVersionBody(), admin);
  if (String(version.status).toUpperCase() !== 'PUBLISHED') await api.request('POST', `/regulation-versions/${version.id}/publish`, undefined, admin);
  return { organization, sport, version, tokens, parentTokens: new Map() };
}
async function parent(api: Api, swimmer: Swimmer, context: Context, password: string): Promise<string> {
  const email = swimmer.email!;
  if (context.parentTokens.has(email)) return context.parentTokens.get(email)!;
  const existing = await findParticipant(api, email, context.tokens.admin);
  if (!existing) await api.request('POST', '/users', { ...swimmer.profile, email, password, city: swimmer.city, countryCode: 'RU', clubName: swimmer.profile.club }, context.tokens.admin);
  const token = await participantLogin(api, email, password);
  const user = await api.request<Row>('GET', '/auth/me', undefined, token);
  const details = await api.request<Row>('GET', `/users/${user.id}`, undefined, context.tokens.admin);
  if (profileKey(details.profile as Parameters<typeof profileKey>[0]) !== profileKey(swimmer.profile))
    throw new Error(`Профиль ${email} отличается от плана. Существующий профиль не меняется; проверьте DEMO_OWNER_EMAIL и reset.`);
  context.parentTokens.set(email, token);
  return token;
}
async function transition(api: Api, event: Row, action: string, expected: string, token: string, allowed = [expected]) {
  try { return await api.request<Row>('POST', `/events/${event.id}/${action}`, undefined, token); }
  catch (error) {
    if (!(error instanceof ApiError) || !['events.invalid_transition', 'events.invalid_dates'].includes(error.code)) throw error;
    const latest = await api.request<Row>('GET', `/events/${event.id}`, undefined, token);
    if (!allowed.includes(String(latest.status))) throw error;
    return latest;
  }
}
async function prepare(api: Api, plan: CalendarEvent, context: Context, known: Row[], create: boolean) {
  const { admin, organizer } = context.tokens;
  let event = unique(known, row => row.title === plan.title, plan.title);
  if (!event && !create) return;
  if (event && (event.organizationId !== context.organization.id || JSON.stringify(readMetadata(event.description)) !== JSON.stringify(metadata(plan))))
    resetError(`Событие «${plan.title}» не соответствует календарю/демо-организации.`);
  if (event && (Date.parse(String(event.eventStartAt)) !== Date.parse(plan.dates.eventStartAt) || Date.parse(String(event.eventEndAt)) !== Date.parse(plan.dates.eventEndAt)))
    resetError(`Отличаются даты события «${plan.title}».`);
  if (event && (event.registrationOpenAt == null || event.registrationCloseAt == null))
    event = await api.request<Row>('PATCH', `/events/${event.id}`, { registrationOpenAt: plan.dates.registrationOpenAt, registrationCloseAt: plan.dates.registrationCloseAt }, organizer);
  const venue = await ensure(api, admin, '/venues', row => row.name === plan.venue.name,
    { name: plan.venue.name, city: plan.venue.city, countryCode: 'RU', timezone: plan.venue.timezone,
      venueMeta: { lanes: plan.venue.lanes, courseLength: plan.venue.courseLength } }, plan.venue.name);
  let meta = venue.venueMeta;
  if (typeof meta === 'string') { try { meta = JSON.parse(meta); } catch { meta = undefined; } }
  if (venue.timezone !== plan.venue.timezone || (meta as { lanes?: number })?.lanes !== plan.venue.lanes ||
      (meta as { courseLength?: number })?.courseLength !== plan.venue.courseLength) resetError(`Метаданные площадки «${plan.venue.name}» отличаются.`);
  if (!event) {
    event = await api.request<Row>('POST', '/events', { organizationId: context.organization.id, sportId: context.sport.id,
      venueId: venue.id, regulationVersionId: context.version.id, title: plan.title, description: description(plan), ...plan.dates,
      ...(plan.week % 2 === 0 ? { maxParticipants: plan.capacity } : {}) }, organizer);
    known.push(event);
  }
  if (event.venueId !== venue.id) resetError('Событие использует другую площадку.');
  const sessions: Row[] = [];
  for (let index = 0; index < plan.sessionCount; index++) {
    const body = { name: `Сессия ${index + 1} (${index ? 'день' : 'утро'})`, orderNo: index + 1,
      startsAt: `${plan.date}T${index ? '15' : '10'}:00:00${plan.venue.offset}`, breakBetweenEventsSec: 60 };
    const session = await ensure(api, organizer, `/events/${event.id}/sessions`, row => row.name === body.name, body, body.name);
    if (Date.parse(String(session.startsAt)) !== Date.parse(body.startsAt)) resetError('Дата сессии отличается от плана.');
    sessions.push(session);
  }
  const disciplines: Row[] = [];
  for (const spec of plan.disciplines) {
    const template = await ensure(api, admin, '/discipline-templates', row => row.code === `T10-${spec.code}` && row.sportId === context.sport.id,
      templateBody(context.sport.id, spec), spec.code);
    const body = { ...disciplineBody(spec, template.id, sessions[spec.sessionIndex].id), stroke: spec.stroke };
    const path = `/events/${event.id}/disciplines`;
    let discipline = unique(await api.request<Row[]>('GET', path, undefined, organizer), row => row.code === body.code, body.code);
    if (!discipline) {
      if (!['DRAFT', 'PUBLISHED'].includes(String(event.status))) resetError('Дисциплина отсутствует после открытия регистрации.');
      discipline = await api.request<Row>('POST', path, body, organizer);
    }
    for (const field of ['stroke', 'gender', 'distanceM', 'sessionId', 'orderInSession', 'seedingMode'] as const)
      if (discipline[field] !== body[field]) resetError(`Дисциплина ${spec.code}: отличается ${field}.`);
    if (Number(discipline.entryFeeAmount) !== 0) resetError('Неожиданный взнос в календаре без S05.');
    if (discipline.status === 'DRAFT') await api.request('POST', `${path}/${discipline.id}/publish`, undefined, organizer);
    disciplines.push(discipline);
  }
  if (event.status === 'DRAFT' && plan.status !== 'DRAFT') event = await transition(api, event, 'publish', 'PUBLISHED', organizer);
  return { event, disciplines };
}

async function fill(api: Api, event: Row, plan: CalendarEvent, disciplines: Row[], pool: Swimmer[], context: Context, options: RunOptions) {
  const token = context.tokens.organizer;
  let rows = await registrations(api, event, token);
  const desired = calendarEntries(plan, pool);
  const possible = calendarEntries({ ...plan, count: plan.capacity - 10 }, pool);
  const possibleKeys = new Set(possible.map(e => registrationKey(e.profile, e.code)));
  for (const row of rows) {
    const code = String(disciplines.find(d => d.id === row.eventDisciplineId)?.code);
    if (!possibleKeys.has(registrationKey(row.participantSnapshot, code))) resetError(`Неожиданная заявка в «${plan.title}».`);
  }
  const find = (entry: CalendarEntry) => rows.find(r => r.eventDisciplineId === disciplines.find(d => d.code === entry.code)?.id && profileKey(r.participantSnapshot) === profileKey(entry.profile));
  const missing = desired.filter(entry => !entry.scenario && !find(entry));
  if (missing.length) {
    if (event.status === 'COMPLETED') resetError('В завершённом событии отсутствуют заявки.');
    // Excel import is an API batch; keep events sequential and requests bounded.
    for (let index = 0; index < missing.length; index += 100) {
      const batch = missing.slice(index, index + 100);
      await importEntries(api, event.id, workbook(batch), batch.length, token, () => {});
    }
    rows = await registrations(api, event, token);
  }
  const specials = desired.filter(entry => entry.scenario);
  const owned = specials.find(entry => entry.scenario === 'OWNER');
  if (owned && find(owned)) {
    const user = await findParticipant(api, owned.email!, context.tokens.admin);
    if (!user || (find(owned)!.participantUserId && find(owned)!.participantUserId !== user.id))
      resetError('DEMO_OWNER_EMAIL существующего календаря изменён. Проверьте владельца.');
  }
  const isOpen = plan.status === 'REGISTRATION_OPEN';
  const publicWork = specials.filter(entry => {
    const row = find(entry);
    return !row || (!isOpen && row.status === 'NEEDS_CORRECTION') || row.status === 'SUBMITTED' || (!isOpen && row.status === 'WAITLISTED');
  });
  if (publicWork.length) {
    if (event.status === 'COMPLETED') resetError('Сценарная заявка отсутствует в завершённом событии.');
    // Public submission validates the real wall clock, while DEMO_DATE can be simulated.
    // Restore scheduled deadlines in finally; null deadlines also prevent scheduler races.
    const deadlines = { registrationOpenAt: event.registrationOpenAt ?? null, registrationCloseAt: event.registrationCloseAt ?? null };
    await api.request('PATCH', `/events/${event.id}`, { registrationOpenAt: null, registrationCloseAt: null }, token);
    if (event.status !== 'REGISTRATION_OPEN') event = await transition(api, event, 'open-registration', 'REGISTRATION_OPEN', token);
    try {
      if (plan.waitlisted) {
        const discipline = disciplines.find(d => d.code === 'M-50-FREE')!;
        const limit = rows.filter(r => r.eventDisciplineId === discipline.id && (isOpen ? r.status === 'CONFIRMED' : true)).length +
          (isOpen ? 0 : publicWork.filter(entry => entry.scenario === 'WAITLIST' && !find(entry)).length);
        if (discipline.maxParticipants !== limit) await api.request('PATCH', `/event-disciplines/${discipline.id}`, { maxParticipants: limit || 1 }, token);
      }
      for (const entry of publicWork) {
        const swimmer = pool.find(p => p.email === entry.email)!;
        const parentToken = await parent(api, swimmer, context, options.passwords.PARENT);
        let row = find(entry);
        if (!row) {
          const created = await api.request<CalendarRegistration[]>('POST', `/public/events/${event.publicSlug}/registrations`, {
            profile: entry.profile, items: [{ eventDisciplineId: disciplines.find(d => d.code === entry.code)!.id, seedTimeMs: entry.seedTimeMs }],
          }, parentToken);
          row = created[0]; rows.push(row);
        }
        if (!isOpen && row.status === 'NEEDS_CORRECTION') row = await api.request<CalendarRegistration>('POST', `/registrations/${row.id}/resubmit`, {}, parentToken);
        if (row.status === 'SUBMITTED' || (!isOpen && row.status === 'WAITLISTED')) {
          row = await api.request<CalendarRegistration>('POST', `/registrations/${row.id}/review`, {
            decision: isOpen && entry.scenario === 'CORRECTION' ? 'NEEDS_CORRECTION' : 'CONFIRMED',
            reviewNote: isOpen && entry.scenario === 'CORRECTION' ? 'Тестовые данные: уточните сведения медицинской справки.' : null,
          }, token);
        }
        if (isOpen && entry.scenario === 'WAITLIST' && row.status !== 'WAITLISTED') resetError('Не удалось сформировать очередь штатной подачей.');
      }
    } finally {
      await api.request('PATCH', `/events/${event.id}`, deadlines, token);
    }
  }
  rows = await registrations(api, event, token);
  if (plan.documents) {
    for (const entry of desired.filter(e => !e.scenario).slice(0, plan.documents)) {
      const row = find(entry)!;
      const path = `/profiles/${row.participantProfileId}/documents?eventId=${event.id}`;
      const documents = await api.request<Row[]>('GET', path, undefined, token);
      if (!documents.some(d => d.originalName === 'sportgate-demo-medical.pdf' && d.type === 'MEDICAL_CERT')) {
        const form = new FormData();
        form.append('file', new Blob([await readFile(new URL('./fixtures/demo-medical.pdf', import.meta.url))], { type: 'application/pdf' }), 'sportgate-demo-medical.pdf');
        await api.request('POST', `${path}&type=MEDICAL_CERT`, form, token);
      }
    }
  }
  return { event, rows };
}

export async function runCalendar(api: Api, options: RunOptions, log = console.log) {
  if (!options.enabled) throw new Error('Календарь разрешён только с DEMO_SEED_ENABLED=true.');
  const settings = calendarSettings(options);
  const started = Date.now();
  if (options.advance) {
    const token = await login(api, options.adminEmail, options.adminPassword);
    const organizations = await api.request<Row[]>('GET', '/organizations', undefined, token);
    const organization = unique(organizations, row => row.name === ORGANIZATION_NAME, ORGANIZATION_NAME);
    const events = await api.request<Row[]>('GET', '/events', undefined, token);
    if (!organization || !events.some(event => event.organizationId === organization.id && readMetadata(event.description) &&
        (options.prefix === undefined || readMetadata(event.description)!.prefix === options.prefix))) {
      const summary = { mode: 'advance', weeks: 0, events: [], seconds: 0 };
      log(`S10 ${JSON.stringify(summary)}`);
      return summary;
    }
  }
  const pool = await swimmerPool(options.ownerEmail);
  const context = await setup(api, options, pool);
  const known = await api.request<Row[]>('GET', '/events', undefined, context.tokens.admin);
  let plans: CalendarEvent[];
  if (options.advance) {
    const groups = new Map<string, ReturnType<typeof readMetadata>>();
    for (const event of known) {
      if (event.organizationId !== context.organization.id) continue;
      const meta = readMetadata(event.description);
      if (!meta || (options.prefix !== undefined && meta.prefix !== options.prefix) || (options.start && meta.start !== options.start)) continue;
      groups.set(JSON.stringify([meta.start, meta.weeks, meta.prefix, meta.anchorDate]), meta);
    }
    plans = [...groups.values()].flatMap(meta => calendarPlan({ ...meta!, demoDate: progressDate(options.demoDate ?? (options.now ?? new Date()).toISOString().slice(0, 10), meta!, known, context.organization.id) }));
  } else {
    for (const event of known) {
      if (event.organizationId !== context.organization.id) continue;
      const meta = readMetadata(event.description);
      if (!meta || meta.prefix !== settings.prefix) continue;
      if (meta.start !== settings.start || meta.weeks !== settings.weeks || meta.anchorDate !== settings.anchorDate)
        resetError('Границы/DEMO_DATE существующего календаря отличаются. Для другого сезона используйте CALENDAR_PREFIX.');
    }
    plans = calendarPlan({ ...settings, demoDate: progressDate(settings.demoDate, settings, known, context.organization.id) });
  }
  // Attach the one owner profile with a public form before importing that athlete into history.
  plans.sort((a, b) => Number(b.owner && b.status === 'REGISTRATION_OPEN') - Number(a.owner && a.status === 'REGISTRATION_OPEN') || a.date.localeCompare(b.date));
  const summaries: Summary[] = [];
  for (const [index, plan] of plans.entries()) {
    log(`S10 [${index + 1}/${plans.length}] ${plan.date} · ${plan.title} → ${plan.status}`);
    const prepared = await prepare(api, plan, context, known, !options.advance);
    if (!prepared) continue;
    let { event } = prepared;
    const { disciplines } = prepared;
    // Completed/open events never move backwards during a rerun with an older DEMO_DATE.
    const rank = ['DRAFT', 'PUBLISHED', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'COMPLETED'];
    if (!rank.includes(String(event.status))) resetError(`Неподходящий статус ${event.status}.`);
    const effectivePlan = rank.indexOf(String(event.status)) > rank.indexOf(plan.status) ? { ...plan, status: String(event.status) } : plan;
    const filled = await fill(api, event, effectivePlan, disciplines, pool, context, options);
    event = filled.event;
    if (effectivePlan.status === 'REGISTRATION_OPEN') {
      event = await api.request<Row>('GET', `/events/${event.id}`, undefined, context.tokens.organizer);
      const now = options.now ?? new Date();
      const patch: Record<string, string> = {};
      if (event.registrationOpenAt && Date.parse(String(event.registrationOpenAt)) > now.getTime()) patch.registrationOpenAt = new Date(now.getTime() - 86_400_000).toISOString();
      if (event.registrationCloseAt && Date.parse(String(event.registrationCloseAt)) <= now.getTime()) patch.registrationCloseAt = new Date(now.getTime() + 86_400_000).toISOString();
      if (Object.keys(patch).length) event = await api.request<Row>('PATCH', `/events/${event.id}`, patch, context.tokens.organizer);
      if (event.status !== 'REGISTRATION_OPEN') event = await transition(api, event, 'open-registration', 'REGISTRATION_OPEN', context.tokens.organizer);
    }
    let statistics: Record<string, number> = { heats: 0, finishedHeats: 0, results: 0, corrected: 0 };
    if (['REGISTRATION_CLOSED', 'COMPLETED'].includes(effectivePlan.status)) {
      event = await api.request<Row>('GET', `/events/${event.id}`, undefined, context.tokens.organizer);
      if (event.status === 'PUBLISHED') event = await transition(api, event, 'open-registration', 'REGISTRATION_OPEN', context.tokens.organizer, ['REGISTRATION_OPEN', 'REGISTRATION_CLOSED']);
      if (event.status === 'REGISTRATION_OPEN') event = await transition(api, event, 'close-registration', 'REGISTRATION_CLOSED', context.tokens.organizer);
      statistics = await calendarCompetition(api, event, effectivePlan, disciplines, filled.rows, context.tokens);
      if (effectivePlan.status === 'COMPLETED' && event.status !== 'COMPLETED') event = await transition(api, event, 'complete', 'COMPLETED', context.tokens.organizer);
    }
    const states = Object.fromEntries([...new Set(filled.rows.map(r => String(r.status)))].sort().map(state => [state, filled.rows.filter(r => r.status === state).length]));
    const summary = { week: plan.week + 1, date: plan.date, slug: event.publicSlug, status: event.status, registrations: filled.rows.length, states, ...statistics };
    summaries.push(summary);
    log(`S10 event: ${JSON.stringify(summary)}`);
  }
  summaries.sort((a, b) => a.date.localeCompare(b.date));
  const summary = { mode: options.advance ? 'advance' : 'seed', weeks: new Set(plans.map(p => p.start + ':' + p.week)).size,
    events: summaries, seconds: Math.round((Date.now() - started) / 10) / 100 };
  log(`S10 ${JSON.stringify(summary)}`);
  return summary;
}
async function main() {
  const explicitDemoDate = process.env.DEMO_DATE;
  loadLocalEnv();
  const args = flags(process.argv.slice(2), ['--advance', '--plan', '--help']);
  if (args.has('--help')) { console.log('npx tsx calendar.ts [--advance|--plan]\nCALENDAR_WEEKS=25, CALENDAR_START=понедельник за 12 недель, CALENDAR_PREFIX; DEMO_DATE=YYYY-MM-DD.\n--advance без DEMO_DATE использует текущую дату и исходные границы из описаний событий.'); return; }
  const advance = args.has('--advance');
  const options: CalendarOptions = { demoDate: advance ? explicitDemoDate ?? new Date().toISOString().slice(0, 10) : process.env.DEMO_DATE,
    start: process.env.CALENDAR_START, weeks: process.env.CALENDAR_WEEKS === undefined ? undefined : Number(process.env.CALENDAR_WEEKS), prefix: process.env.CALENDAR_PREFIX, advance };
  calendarSettings(options);
  if (args.has('--plan')) { console.log(JSON.stringify(calendarPlan(options), null, 2)); return; }
  const settings = config(process.env);
  await runCalendar(new HttpApi(settings.apiUrl, advance ? 600_000 : 5_400_000), { ...settings, ...options, enabled: process.env.DEMO_SEED_ENABLED === 'true' });
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href)
  main().catch(error => { console.error(error instanceof Error ? error.message : 'Ошибка календаря.'); process.exitCode = 1; });
