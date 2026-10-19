import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { ApiError, ensure, HttpApi, login, unique, type Api, type Row } from './api.ts';
import { config, flags, loadLocalEnv } from './config.ts';
import { dataset, DEFAULT_DEMO_DATE, eventDates, ownerEmail, profileKey, selectedEvents, type DemoEvent, type Entry } from './plan.ts';
import { workbook } from './workbook.ts';
import {
  accounts, disciplineBody, disciplineSpecs, demoEventTitle, importBody, memberBody,
  ORGANIZATION_NAME, organizationBody, REGULATION_CODE, regulationTemplateBody,
  regulationVersionBody, roleBody, sessionBodies, templateBody, userBody, validateDemoDate, VENUE_NAME, venueBody,
} from './bodies.ts';

export type SeedOptions = {
  adminEmail: string; adminPassword: string; passwords: Record<string, string>;
  requireVenueMeta?: boolean; eventTitleSuffix?: string; demoDate?: string; demoEvents?: string; ownerEmail?: string; skipImport: boolean; now?: Date; workbook?: Uint8Array;
};
export async function seed(api: Api, options: SeedOptions, log = console.log) {
  const started = Date.now();
  const demoDate = validateDemoDate(options.demoDate ?? DEFAULT_DEMO_DATE);
  const selected = selectedEvents(options.demoEvents);
  const email = ownerEmail(options.ownerEmail);
  const data = await dataset(demoDate, email);
  const adminToken = await login(api, options.adminEmail, options.adminPassword);
  const admin = await api.request<Row & { roles: string[] }>('GET', '/auth/me', undefined, adminToken);
  if (!admin.roles.includes('platform_admin')) throw new Error('ADMIN_EMAIL должен иметь роль platform_admin (T01).');
  let ownerToken: string | undefined;
  if (selected.some(event => event.key === 'C') && !options.skipImport) {
    const existing = await findParticipant(api, email, adminToken);
    if (existing) ownerToken = await participantLogin(api, email, options.passwords.PARENT);
  }
  const organization = await ensure(api, adminToken, '/organizations', row => row.name === ORGANIZATION_NAME, organizationBody(), ORGANIZATION_NAME);
  const membersPath = `/organizations/${organization.id}/members`;
  const members = await api.request<Row[]>('GET', membersPath, undefined, adminToken);
  let organizerToken = '';
  for (const account of accounts) {
    const password = options.passwords[account.key];
    const existing = await findUser(api, account.email, adminToken);
    if (!existing) {
      await api.request('POST', '/users', userBody(account, password), adminToken);
    }
    let userToken: string;
    try { userToken = await login(api, account.email, password); } catch (error) {
      if (!(error instanceof ApiError) || error.code !== 'identity.invalid_credentials') throw error;
      throw new Error(`Пользователь ${account.email} существует, но пароль не подходит. Проверьте ${account.key}_PASSWORD; пароль не изменяется сидом.`);
    }
    const user = await api.request<Row & { roles: string[] }>('GET', '/auth/me', undefined, userToken);
    if (user.email !== account.email) throw new Error(`API вернул неверного пользователя для ${account.email}.`);
    if (!user.roles.includes(account.role)) {
      await api.request('POST', `/users/${user.id}/roles`, roleBody(account.role), adminToken);
    }
    if (account.memberRole) {
      const member = unique(members, row => row.userId === user.id, account.email);
      if (member && member.memberRole !== account.memberRole) throw new Error(`Неверное членство ${account.email}: требуется ${account.memberRole}. Исправьте через администратора.`);
      if (!member) await api.request('POST', membersPath, memberBody(user.id, account.memberRole), adminToken);
    }
    if (account.key === 'ORG') organizerToken = await login(api, account.email, password);
    log(`Пользователь готов: ${account.email} (${account.role}).`);
  }
  const venue = await ensure(api, adminToken, '/venues', row => row.name === VENUE_NAME, venueBody(), VENUE_NAME);
  const venueDetails = await api.request<Row>('GET', `/venues/${venue.id}`, undefined, adminToken);
  if (venueDetails.timezone !== 'Europe/Samara') throw new Error('Демо-площадка должна использовать Europe/Samara. Создайте площадку заново через API.');
  let meta: unknown = venueDetails.venueMeta;
  if (typeof meta === 'string') {
    try { meta = JSON.parse(meta); } catch { meta = undefined; }
  }
  const pool = meta as { lanes?: number; courseLength?: number } | undefined;
  if (pool?.lanes !== 8 || pool.courseLength !== 25) {
    if (options.requireVenueMeta) {
      throw new Error('площадка сохранена без venueMeta; API обновления площадки нет — сбросьте демо-события и создайте площадку заново через API');
    }
    log('WARNING: площадка сохранена без подтверждённых venueMeta {lanes:8, courseLength:25}; без метаданных бэкенд использует 8 дорожек по умолчанию, длина бассейна на публичной карточке не отобразится. T01 уже поддерживает venueMeta; API обновления площадки нет — сбросьте демо-события и создайте площадку заново через API.');
  }
  const sport = unique(await api.request<Row[]>('GET', '/sports', undefined, adminToken), row => row.code === 'swim', 'swim');
  if (!sport) throw new Error('Не найден спорт swim. Примените штатные миграции backend.');
  const regulationTemplate = await ensure(api, adminToken, '/regulation-templates',
    row => row.code === REGULATION_CODE && row.sportId === sport.id, regulationTemplateBody(sport.id), REGULATION_CODE);
  let version = unique(await api.request<Row[]>('GET', '/regulation-versions', undefined, adminToken),
    row => row.regulationTemplateId === regulationTemplate.id && row.versionNo === 1, `${REGULATION_CODE} v1`);
  if (!version) version = await api.request<Row>('POST', `/regulation-templates/${regulationTemplate.id}/versions`, regulationVersionBody(), adminToken);
  if (String(version.status).toUpperCase() !== 'PUBLISHED') await api.request('POST', `/regulation-versions/${version.id}/publish`, undefined, adminToken);
  const tokens = {
    organizer: organizerToken,
    judge: await login(api, 'judge@flounder.demo', options.passwords.JUDGE),
    secretary: await login(api, 'sec@flounder.demo', options.passwords.SEC),
    admin: adminToken, owner: ownerToken,
  };
  const events: Row[] = [];
  for (const spec of selected) {
    const dates = eventDates(demoDate, spec);
    const title = spec.title + (options.eventTitleSuffix ?? '');
    demoEventTitle(options.eventTitleSuffix);
    const { date, ...times } = dates;
    let event = unique(await api.request<Row[]>('GET', '/events', undefined, adminToken), row => row.title === title, title);
    if (!event) event = await api.request<Row>('POST', '/events', {
      organizationId: organization.id, venueId: venue.id, sportId: sport.id, regulationVersionId: version.id,
      title, description: `Вымышленные данные SportGate. S02, событие ${spec.key}; DEMO_DATE=${demoDate}.`,
      ...times, ...(spec.key === 'C' ? { maxParticipants: 150 } : {}),
    }, organizerToken);
    if (event.organizationId !== organization.id || event.venueId !== venue.id)
      throw new Error('Демо-событие с таким названием относится к другой организации/площадке.');
    for (const [field, expected] of Object.entries(times)) {
      if (Date.parse(String(event[field])) !== Date.parse(expected))
        throw new Error(`DEMO_DATE отличается от существующего события ${spec.key} (${field}). Выполните reset, затем seed.`);
    }
    if (spec.key === 'C' && event.maxParticipants !== 150) throw new Error('Лимит события C отличается от 150. Выполните reset.');
    if (!['DRAFT', 'PUBLISHED', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED', ...(spec.key === 'A' ? ['COMPLETED'] : [])].includes(String(event.status)))
      throw new Error(`Неподходящий статус события ${spec.key}: ${event.status}. Выполните reset.`);
    const sessions: Row[] = [];
    for (const body of sessionBodies(date)) {
      const session = await ensure(api, organizerToken, `/events/${event.id}/sessions`, row => row.name === body.name, body, body.name);
      if (session.orderNo !== body.orderNo || session.breakBetweenEventsSec !== body.breakBetweenEventsSec ||
          Date.parse(String(session.startsAt)) !== Date.parse(body.startsAt)) throw new Error(`Сессия ${body.name} отличается от демо-плана. Выполните reset.`);
      sessions.push(session);
    }
    const disciplines: Row[] = [];
    for (const disciplineSpec of disciplineSpecs(spec.key === 'C')) {
      const template = await ensure(api, adminToken, '/discipline-templates',
        row => row.code === `T10-${disciplineSpec.code}` && row.sportId === sport.id, templateBody(sport.id, disciplineSpec), `T10-${disciplineSpec.code}`);
      const body = { ...disciplineBody(disciplineSpec, template.id, sessions[disciplineSpec.sessionIndex].id),
        ...(spec.key === 'C' ? { maxParticipants: 16 } : {}) };
      const path = `/events/${event.id}/disciplines`;
      let discipline = unique(await api.request<Row[]>('GET', path, undefined, organizerToken), row => row.code === body.code, body.code);
      if (!discipline) {
        if (!['DRAFT', 'PUBLISHED'].includes(String(event.status))) throw new Error(`Дисциплина ${body.code} отсутствует после открытия регистрации. Выполните reset.`);
        discipline = await api.request<Row>('POST', path, body, organizerToken);
      }
      for (const field of ['distanceM', 'gender', 'stroke', 'seedingMode', 'sessionId', 'orderInSession', ...(spec.key === 'C' ? ['maxParticipants'] : [])] as const) {
        if (discipline[field] !== (body as Record<string, unknown>)[field]) throw new Error(`Дисциплина ${body.code}: отличается ${field}. Выполните reset.`);
      }
      if (Number(discipline.entryFeeAmount) !== 0 || JSON.stringify(discipline.ageGroups) !== JSON.stringify(body.ageGroups))
        throw new Error(`Дисциплина ${body.code}: отличаются взнос/возрастные группы. Выполните reset.`);
      if (discipline.status === 'DRAFT') await api.request('POST', `${path}/${discipline.id}/publish`, undefined, organizerToken);
      else if (discipline.status !== 'PUBLISHED') throw new Error(`Неподходящий статус дисциплины ${body.code}. Выполните reset.`);
      disciplines.push(discipline);
    }
    if (event.status === 'DRAFT') event = await api.request<Row>('POST', `/events/${event.id}/publish`, undefined, organizerToken);
    if (event.status === 'PUBLISHED') {
      try { event = await api.request<Row>('POST', `/events/${event.id}/open-registration`, undefined, organizerToken); }
      catch (error) {
        if (!(error instanceof ApiError) || error.code !== 'events.invalid_transition') throw error;
        event = await api.request<Row>('GET', `/events/${event.id}`, undefined, organizerToken);
        if (!['REGISTRATION_OPEN', ...(spec.key === 'C' ? [] : ['REGISTRATION_CLOSED'])].includes(String(event.status))) throw error;
      }
    }
    let statistics: Record<string, number> = {};
    if (options.skipImport) {
      log(`Событие ${spec.key}: --skip-import, подготовлены только настройки; целевой статус/данные не сформированы.`);
    } else {
      if (spec.key === 'C') {
        if (event.status !== 'REGISTRATION_OPEN') throw new Error('Регистрация C закрыта. Проверьте DEMO_DATE; даты относительно демо не сдвигаются от времени запуска.');
        await registrationEvent(api, event, disciplines, data.C, options, tokens);
        statistics = { heats: 0, results: 0, documents: 2 };
      } else {
        let rows = await registrations(api, event.id, organizerToken);
        if (rows.length !== data[spec.key].length) {
          if (event.status === 'COMPLETED') throw new Error('Завершённое A содержит неверное число заявок. Выполните reset.');
          const bytes = spec.key === 'B' ? options.workbook ?? await readFile(new URL('../../docs/scripts/demo/out/set-M.xlsx', import.meta.url)) : workbook(data.A);
          await importEntries(api, event.id, bytes, data[spec.key].length, organizerToken, log);
          rows = await registrations(api, event.id, organizerToken);
        }
        assertEntries(rows, disciplines, data[spec.key]);
        if (event.status === 'REGISTRATION_OPEN') {
          event = await api.request<Row>('GET', `/events/${event.id}`, undefined, organizerToken);
          if (event.status === 'REGISTRATION_OPEN') {
            try { event = await api.request<Row>('POST', `/events/${event.id}/close-registration`, undefined, organizerToken); }
            catch (error) {
              if (!(error instanceof ApiError) || error.code !== 'events.invalid_transition') throw error;
              event = await api.request<Row>('GET', `/events/${event.id}`, undefined, organizerToken);
              if (event.status !== 'REGISTRATION_CLOSED') throw error;
            }
          }
          if (event.status !== 'REGISTRATION_CLOSED') throw new Error(`Не удалось подтвердить закрытие регистрации ${spec.key}. Получен статус ${event.status}.`);
        }
        statistics = await competition(api, event, disciplines, rows, spec, tokens);
        if (spec.key === 'A' && event.status !== 'COMPLETED') event = await api.request<Row>('POST', `/events/${event.id}/complete`, undefined, organizerToken);
      }
      const rows = await registrations(api, event.id, organizerToken);
      const states = Object.fromEntries([...new Set(rows.map(row => String(row.status)))].sort().map(status => [status, rows.filter(row => row.status === status).length]));
      log(`S02 ${spec.key}: ${JSON.stringify({ eventId: event.id, slug: event.publicSlug, status: event.status, registrations: rows.length, profiles: new Set(rows.map(row => row.participantProfileId)).size, states, ...statistics })}`);
    }
    log(`Готово: eventId=${event.id}, дата=${date}, публичная страница /e/${event.publicSlug}.`);
    events.push(event);
  }
  log(`Время сида: ${((Date.now() - started) / 1000).toFixed(2)} с.`);
  return events;
}

type Tokens = { organizer: string; admin: string; judge: string; secretary: string; owner?: string };
type Registration = Row & { participantProfileId: string; eventDisciplineId: string; participantUserId?: string; participantSnapshot: Entry['profile']; seedTimeMs: number | null };
type HeatEntry = { entryId: string; registrationId: string; laneOrPosition?: number; resultStatus?: string; timeMs?: number | null };
type Heat = Row & { entries: HeatEntry[] };
const registrations = (api: Api, id: string, token: string) => api.request<Registration[]>('GET', `/events/${id}/registrations`, undefined, token);
function assertEntries(rows: Registration[], disciplines: Row[], entries: Entry[]) {
  if (rows.length !== entries.length || entries.some(entry => !rows.some(row =>
    profileKey(row.participantSnapshot) === profileKey(entry.profile) && disciplines.find(d => d.id === row.eventDisciplineId)?.code === entry.code)))
    throw new Error('Состав заявок отличается от демо-плана. Выполните reset, затем seed.');
}
async function importEntries(api: Api, id: string, bytes: Uint8Array, count: number, token: string, log: (message: string) => void) {
  const report = await api.request<{ total: number; created: number; updated: number; skipped: number; errors: unknown[] }>('POST', `/events/${id}/registrations/import`, importBody(bytes), token);
  if (!Array.isArray(report.errors) || report.errors.length || report.total !== count || report.created + report.updated + report.skipped !== count)
    throw new Error(`Импорт не подтвердил ${count} корректных строк без ошибок. Проверьте отчёт импорта. SQL-обход запрещён.`);
  log(`Импорт: всего ${report.total}, создано ${report.created}, обновлено ${report.updated}, пропущено ${report.skipped}, ошибок 0.`);
}
async function findUser(api: Api, email: string, adminToken: string) {
  const users = await api.request<{ items: (Row & { roles: string[] })[] }>('GET', `/users?query=${encodeURIComponent(email)}&size=100`, undefined, adminToken);
  return unique(users.items, row => String(row.email).toLowerCase() === email, email);
}
async function findParticipant(api: Api, email: string, adminToken: string) {
  const user = await findUser(api, email, adminToken);
  const roles = user?.roles;
  if (user && (!Array.isArray(roles) || !roles.includes('participant') || roles.some(role => role !== 'participant')))
    throw new Error(`Аккаунт ${email} уже существует и не является отдельным участником. Задайте другой DEMO_OWNER_EMAIL; роли и профиль существующего пользователя не изменяются.`);
  return user;
}
async function participantLogin(api: Api, email: string, password: string) {
  try { return await login(api, email, password); }
  catch (error) {
    if (!(error instanceof ApiError) || error.code !== 'identity.invalid_credentials') throw error;
    throw new Error(`Аккаунт ${email} уже существует, но пароль не подходит. Проверьте PARENT_PASSWORD/DEMO_PASSWORD или задайте другой DEMO_OWNER_EMAIL. Пароль и профиль не изменяются.`);
  }
}
async function registrationEvent(api: Api, event: Row, disciplines: Row[], entries: Entry[], options: SeedOptions, tokens: Tokens) {
  const imported = entries.filter(entry => !entry.state);
  let rows = await registrations(api, event.id, tokens.organizer);
  if (imported.some(entry => !rows.some(row => profileKey(row.participantSnapshot) === profileKey(entry.profile)))) {
    await importEntries(api, event.id, workbook(imported), imported.length, tokens.organizer, () => {});
    rows = await registrations(api, event.id, tokens.organizer);
  }
  for (const entry of entries.filter(entry => entry.state)) {
    const discipline = disciplines.find(d => d.code === entry.code)!;
    let registration = rows.find(row => profileKey(row.participantSnapshot) === profileKey(entry.profile) && row.eventDisciplineId === discipline.id);
    const existing = await findParticipant(api, entry.email!, tokens.admin);
    if (registration && (!existing || registration.participantUserId !== existing.id))
      throw new Error('E-mail участника C изменился. Проверьте DEMO_OWNER_EMAIL и выполните reset C перед повторным сидом.');
    if (!registration) {
      if (!existing) {
        await api.request('POST', '/users', { ...entry.profile, clubName: entry.profile.club, countryCode: 'RU', city: 'Самара',
          email: entry.email, password: options.passwords.PARENT }, tokens.admin);
      }
      const token = entry.email === ownerEmail(options.ownerEmail) && tokens.owner ? tokens.owner : await participantLogin(api, entry.email!, options.passwords.PARENT);
      const created = await api.request<Registration[]>('POST', `/public/events/${event.publicSlug}/registrations`, {
        profile: entry.profile, items: [{ eventDisciplineId: discipline.id, seedTimeMs: entry.seedTimeMs }],
      }, token);
      registration = created[0];
      rows.push(registration);
    }
    if (entry.state === 'NEEDS_CORRECTION' && registration.status === 'SUBMITTED')
      registration = await api.request<Registration>('POST', `/registrations/${registration.id}/review`, {
        decision: 'NEEDS_CORRECTION', reviewNote: 'Демо: уточните данные медицинской справки.',
      }, tokens.organizer);
    if (registration.status !== entry.state) throw new Error(`Заявка C должна иметь статус ${entry.state}, получен ${registration.status}. Выполните reset.`);
  }
  rows = await registrations(api, event.id, tokens.organizer);
  assertEntries(rows, disciplines, entries);
  if (rows.filter(row => row.status === 'CONFIRMED').length !== 114 || rows.filter(row => row.status === 'NEEDS_CORRECTION').length !== 6 || rows.filter(row => row.status === 'WAITLISTED').length !== 4)
    throw new Error('C: ожидались 6 NEEDS_CORRECTION и 4 WAITLISTED. Выполните reset.');
  for (const entry of imported.slice(0, 2)) {
    const row = rows.find(row => profileKey(row.participantSnapshot) === profileKey(entry.profile))!;
    const path = `/profiles/${row.participantProfileId}/documents`;
    const docs = await api.request<Row[]>('GET', `${path}?eventId=${event.id}`, undefined, tokens.organizer);
    if (!docs.some(doc => doc.originalName === 'sportgate-demo-medical.pdf' && doc.type === 'MEDICAL_CERT')) {
      const form = new FormData();
      form.append('file', new Blob([await readFile(new URL('./fixtures/demo-medical.pdf', import.meta.url))], { type: 'application/pdf' }), 'sportgate-demo-medical.pdf');
      await api.request('POST', `${path}?eventId=${event.id}&type=MEDICAL_CERT`, form, tokens.organizer);
    }
  }
}
async function competition(api: Api, event: Row, disciplines: Row[], rows: Registration[], spec: DemoEvent, tokens: Tokens) {
  let generated = false;
  for (const discipline of disciplines) {
    const units = await api.request<Row[]>('GET', `/event-disciplines/${discipline.id}/competition-units`, undefined, tokens.organizer);
    if (!units.length) {
      if (event.status === 'COMPLETED') throw new Error('A завершено без заплывов. Выполните reset.');
      await api.request('POST', `/event-disciplines/${discipline.id}/generate-competition-units`, { force: false, circleSeedTopHeats: false }, tokens.organizer);
      generated = true;
    }
  }
  if (generated) await api.request('POST', `/events/${event.id}/schedule/recalculate`, undefined, tokens.organizer);
  const summaries: Row[] = [];
  for (const discipline of disciplines) summaries.push(...await api.request<Row[]>('GET', `/event-disciplines/${discipline.id}/competition-units`, undefined, tokens.organizer));
  summaries.sort((a, b) => Date.parse(String(a.scheduledAt)) - Date.parse(String(b.scheduledAt)) || Number(a.sequenceNumber) - Number(b.sequenceNumber));
  if (summaries.some(unit => ['DRAFT', 'SCHEDULED'].includes(String(unit.status)))) {
    const validation = await api.request<{ violations: { severity: string }[] }>('POST', `/events/${event.id}/competition-units/publish`, undefined, tokens.organizer);
    if (validation.violations.some(v => v.severity === 'ERROR')) throw new Error('Валидатор отклонил публикацию заплывов. SQL-обход запрещён.');
  }
  const target = spec.key === 'A' ? summaries : summaries.filter(unit => disciplines.find(d => d.id === unit.eventDisciplineId)?.sessionId === disciplines[0].sessionId).slice(0, 2);
  if (spec.key === 'B' && target.length !== 2) throw new Error('Для B нужны два заплыва в сессии 1.');
  let index = 0;
  let corrections = 0;
  for (const unit of target) {
    let heat = await api.request<Heat>('GET', `/competition-units/${unit.id}`, undefined, tokens.organizer);
    heat.entries.sort((a, b) => (a.laneOrPosition ?? 0) - (b.laneOrPosition ?? 0) || a.entryId.localeCompare(b.entryId));
    const values = heat.entries.map(entry => {
      const serial = index++;
      const seed = rows.find(row => row.id === entry.registrationId)?.seedTimeMs ?? 48_000;
      const status = spec.key === 'A' && [3, 5, 7].includes(serial) ? (serial === 5 ? 'DNF' : 'DNS') : 'VALID';
      return { competitionUnitEntryId: entry.entryId, status, timeMs: status === 'VALID' ? Math.round(seed * .98 / 10) * 10 : null };
    });
    if (heat.status !== 'FINISHED') {
      if (heat.status === 'PUBLISHED') await api.request('POST', `/competition-units/${unit.id}/start`, undefined, tokens.judge);
      else if (heat.status !== 'IN_PROGRESS') throw new Error(`Неподходящий статус заплыва: ${heat.status}.`);
      await api.request('POST', `/competition-units/${unit.id}/results`, values, tokens.judge);
      await api.request('POST', `/competition-units/${unit.id}/publication`, { published: true }, tokens.judge);
      await api.request('POST', `/competition-units/${unit.id}/finish`, undefined, tokens.judge);
      heat = await api.request<Heat>('GET', `/competition-units/${unit.id}`, undefined, tokens.organizer);
    }
    if (spec.key === 'A') {
      for (const value of values) {
        if (corrections >= 2 || value.status !== 'VALID') continue;
        corrections++;
        const current = heat.entries.find(entry => entry.entryId === value.competitionUnitEntryId)!;
        if (current.resultStatus !== 'CORRECTED') {
          if (event.status === 'COMPLETED') throw new Error('A завершено без двух исправлений. Выполните reset.');
          await api.request('POST', `/competition-units/${unit.id}/results`, [{ ...value, timeMs: value.timeMs! + 100 }], tokens.secretary);
        }
      }
    }
  }
  const all: Heat[] = [];
  for (const summary of summaries) all.push(await api.request<Heat>('GET', `/competition-units/${summary.id}`, undefined, tokens.organizer));
  if (all.some(heat => target.some(unit => unit.id === heat.id) && heat.entries.some(entry => !entry.resultStatus)))
    throw new Error('Завершённый заплыв содержит неопубликованные результаты. Выполните reset.');
  if (all.some(heat => heat.status !== (target.some(unit => unit.id === heat.id) ? 'FINISHED' : 'PUBLISHED')))
    throw new Error('Статусы заплывов отличаются от демо-плана.');
  if (spec.key === 'A' && (all.flatMap(h => h.entries).filter(e => e.resultStatus === 'CORRECTED').length !== 2 ||
      all.flatMap(h => h.entries).filter(e => ['DNS', 'DNF', 'DID_NOT_START', 'DID_NOT_FINISH'].includes(e.resultStatus ?? '')).length !== 3))
    throw new Error('A: ожидались 2 CORRECTED и 3 DNS/DNF.');
  return { heats: all.length, finishedHeats: target.length, results: all.flatMap(heat => heat.entries).filter(entry => entry.resultStatus).length };
}

async function main() {
  loadLocalEnv();
  const args = flags(process.argv.slice(2), ['--skip-import', '--help']);
  if (args.has('--help')) { console.log('npx tsx seed.ts [--skip-import]\nНастройки: scripts/demo/.env.example\nDEMO_DATE=2026-10-19, DEMO_EVENTS=A,B,C; EVENT_TITLE_SUFFIX — резерв; REQUIRE_VENUE_META=true — строгая проверка площадки'); return; }
  const settings = config(process.env);
  await seed(new HttpApi(settings.apiUrl), { ...settings, skipImport: args.has('--skip-import') });
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => { console.error(error instanceof Error ? error.message : 'Сид завершился ошибкой.'); process.exitCode = 1; });
}
