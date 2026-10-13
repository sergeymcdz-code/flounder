import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { ApiError, ensure, HttpApi, login, unique, type Api, type Row } from './api.ts';
import { config, flags, loadLocalEnv } from './config.ts';
import {
  accounts, disciplineBody, disciplineSpecs, demoEventTitle, samaraDate, eventBody, importBody, memberBody,
  nextSaturday, ORGANIZATION_NAME, organizationBody, REGULATION_CODE, regulationTemplateBody,
  regulationVersionBody, roleBody, sessionBodies, templateBody, userBody, validateDemoDate, VENUE_NAME, venueBody,
} from './bodies.ts';

export type SeedOptions = {
  adminEmail: string; adminPassword: string; passwords: Record<string, string>;
  requireVenueMeta?: boolean; eventTitleSuffix?: string; demoDate?: string; skipImport: boolean; now?: Date; workbook?: Uint8Array;
};
export async function seed(api: Api, options: SeedOptions, log = console.log) {
  const title = demoEventTitle(options.eventTitleSuffix);
  const now = options.now ?? new Date();
  const requestedDate = validateDemoDate(options.demoDate ?? nextSaturday(now));
  const workbook = options.skipImport ? undefined : options.workbook ??
    await readFile(new URL('../../docs/scripts/demo/out/set-M.xlsx', import.meta.url));
  const adminToken = await login(api, options.adminEmail, options.adminPassword);
  const admin = await api.request<Row & { roles: string[] }>('GET', '/auth/me', undefined, adminToken);
  if (!admin.roles.includes('platform_admin')) throw new Error('ADMIN_EMAIL должен иметь роль platform_admin (T01).');
  const organization = await ensure(api, adminToken, '/organizations', row => row.name === ORGANIZATION_NAME, organizationBody(), ORGANIZATION_NAME);
  const membersPath = `/organizations/${organization.id}/members`;
  const members = await api.request<Row[]>('GET', membersPath, undefined, adminToken);
  let organizerToken = '';
  for (const account of accounts) {
    const password = options.passwords[account.key];
    let userToken: string;
    try { userToken = await login(api, account.email, password); } catch (error) {
      if (!(error instanceof ApiError) || error.code !== 'identity.invalid_credentials') throw error;
      try { await api.request('POST', '/users', userBody(account, password), adminToken); } catch (creationError) {
        if (creationError instanceof ApiError && creationError.code === 'identity.user_already_exists') {
          throw new Error(`Пользователь ${account.email} существует, но пароль не подходит. Проверьте ${account.key}_PASSWORD; пароль не изменяется сидом.`);
        }
        throw creationError;
      }
      userToken = await login(api, account.email, password);
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
  const venue = await ensure(api, adminToken, '/venues', row => row.name === VENUE_NAME, venueBody(options.requireVenueMeta), VENUE_NAME);
  const venueDetails = await api.request<Row>('GET', `/venues/${venue.id}`, undefined, adminToken);
  let meta: unknown = venueDetails.venueMeta;
  if (typeof meta === 'string') {
    try { meta = JSON.parse(meta); } catch { meta = undefined; }
  }
  const pool = meta as { lanes?: number; courseLength?: number } | undefined;
  if (pool?.lanes !== 8 || pool.courseLength !== 25) {
    if (options.requireVenueMeta) {
      throw new Error('REQUIRE_VENUE_META=true: API площадки не вернул venueMeta {lanes:8, courseLength:25}. Требуется доработка T01.');
    }
    log('WARNING: площадка сохранена без подтверждённых venueMeta {lanes:8, courseLength:25}; без метаданных бэкенд использует 8 дорожек по умолчанию, длина бассейна на публичной карточке не отобразится. Поле появится после доработки T01.');
  }
  const sport = unique(await api.request<Row[]>('GET', '/sports', undefined, adminToken), row => row.code === 'swim', 'swim');
  if (!sport) throw new Error('Не найден спорт swim. Примените штатные миграции backend.');
  const regulationTemplate = await ensure(api, adminToken, '/regulation-templates',
    row => row.code === REGULATION_CODE && row.sportId === sport.id, regulationTemplateBody(sport.id), REGULATION_CODE);
  let version = unique(await api.request<Row[]>('GET', '/regulation-versions', undefined, adminToken),
    row => row.regulationTemplateId === regulationTemplate.id && row.versionNo === 1, `${REGULATION_CODE} v1`);
  if (!version) version = await api.request<Row>('POST', `/regulation-templates/${regulationTemplate.id}/versions`, regulationVersionBody(), adminToken);
  if (version.status !== 'PUBLISHED') await api.request('POST', `/regulation-versions/${version.id}/publish`, undefined, adminToken);
  let event = unique(await api.request<Row[]>('GET', '/events', undefined, adminToken),
    row => row.title === title, title);
  if (!event) event = await api.request<Row>('POST', '/events', eventBody({
    organizationId: organization.id, venueId: venue.id, sportId: sport.id, regulationVersionId: version.id,
  }, requestedDate, now, options.eventTitleSuffix), organizerToken);
  if (event.organizationId !== organization.id || event.venueId !== venue.id) {
    throw new Error('Демо-событие с таким названием относится к другой организации/площадке.');
  }
  const date = samaraDate(String(event.eventStartAt));
  validateDemoDate(date);
  if (options.demoDate && date !== options.demoDate) throw new Error('DEMO_DATE отличается от существующего события. Выполните reset, затем seed.');
  if (!['DRAFT', 'PUBLISHED', 'REGISTRATION_OPEN'].includes(String(event.status))) {
    throw new Error(`Демо-событие в статусе ${event.status}. Для нового прогона выполните reset, затем seed.`);
  }
  const sessions: Row[] = [];
  for (const body of sessionBodies(date)) {
    const session = await ensure(api, organizerToken, `/events/${event.id}/sessions`,
      row => row.name === body.name, body, body.name);
    if (session.orderNo !== body.orderNo || session.breakBetweenEventsSec !== body.breakBetweenEventsSec || new Date(String(session.startsAt)).getTime() !== new Date(body.startsAt).getTime()) {
      throw new Error(`Сессия ${body.name} отличается от демо-плана. Выполните reset, затем seed.`);
    }
    sessions.push(session);
  }
  for (const spec of disciplineSpecs()) {
    const template = await ensure(api, adminToken, '/discipline-templates',
      row => row.code === `T10-${spec.code}` && row.sportId === sport.id, templateBody(sport.id, spec), `T10-${spec.code}`);
    const body = disciplineBody(spec, template.id, sessions[spec.sessionIndex].id);
    const path = `/events/${event.id}/disciplines`;
    let discipline = unique(await api.request<Row[]>('GET', path, undefined, organizerToken), row => row.code === spec.code, spec.code);
    if (!discipline) {
      if (event.status === 'REGISTRATION_OPEN') {
        throw new Error(`Дисциплина ${spec.code} отсутствует, но регистрация уже открыта: выполните reset, затем seed. Добавление видов разрешено только в DRAFT/PUBLISHED.`);
      }
      discipline = await api.request<Row>('POST', path, body, organizerToken);
    }
    if (discipline.distanceM !== spec.distanceM || discipline.gender !== spec.gender ||
        discipline.stroke !== 'FREE' || discipline.seedingMode !== 'BY_AGE_GROUP' ||
        discipline.sessionId !== body.sessionId || discipline.orderInSession !== body.orderInSession ||
        !Array.isArray(discipline.ageGroups) || discipline.ageGroups.length !== body.ageGroups.length ||
        !body.ageGroups.every((group, index) => {
          const actual = (discipline.ageGroups as Record<string, unknown>[])[index];
          return actual && Object.entries(group).every(([key, value]) => actual[key] === value);
        }) ||
        Number(discipline.entryFeeAmount) !== 0) {
      throw new Error(`Дисциплина ${spec.code} не соответствует ТЗ или API не возвращает поля T03. Выполните reset после обновления backend.`);
    }
    if (discipline.status === 'DRAFT') {
      await api.request('POST', `/events/${event.id}/disciplines/${discipline.id}/publish`, undefined, organizerToken);
    } else if (discipline.status !== 'PUBLISHED') throw new Error(`Неподходящий статус дисциплины ${spec.code}. Выполните reset.`);
  }
  if (event.status === 'DRAFT') event = await api.request<Row>('POST', `/events/${event.id}/publish`, undefined, organizerToken);
  if (event.status === 'PUBLISHED') event = await api.request<Row>('POST', `/events/${event.id}/open-registration`, undefined, organizerToken);
  if (event.status !== 'REGISTRATION_OPEN') throw new Error('API не подтвердил открытие регистрации.');
  if (options.skipImport) {
    log('Импорт пропущен (--skip-import). Заявки не загружены этим запуском; импортируйте набор M в UI или повторите seed без флага.');
  } else {
    const report = await api.request<{ total: number; created: number; updated: number; skipped: number; errors: unknown[] }>(
      'POST', `/events/${event.id}/registrations/import`, importBody(workbook!), organizerToken);
    if (!Array.isArray(report.errors) || report.errors.length || report.total !== 200 ||
        report.created + report.updated + report.skipped !== 200) {
      throw new Error('Импорт набора M не подтвердил 200 корректных строк без ошибок. Проверьте отчёт T05 в UI; повторный импорт не создаёт дублей по ТЗ §5.4.');
    }
    log(`Импорт M: всего ${report.total}, создано ${report.created}, обновлено ${report.updated}, пропущено ${report.skipped}, ошибок 0.`);
  }
  log(`Готово: eventId=${event.id}, дата=${date}, публичная страница /e/${event.publicSlug}. Заплывы не формировались.`);
  return event;
}

async function main() {
  loadLocalEnv();
  const args = flags(process.argv.slice(2), ['--skip-import', '--help']);
  if (args.has('--help')) { console.log('npx tsx seed.ts [--skip-import]\nНастройки: scripts/demo/.env.example\nEVENT_TITLE_SUFFIX — резервное событие; REQUIRE_VENUE_META=true — строгая проверка площадки'); return; }
  const settings = config(process.env);
  await seed(new HttpApi(settings.apiUrl), { ...settings, skipImport: args.has('--skip-import') });
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => { console.error(error instanceof Error ? error.message : 'Сид завершился ошибкой.'); process.exitCode = 1; });
}
