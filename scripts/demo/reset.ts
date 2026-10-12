import { pathToFileURL } from 'node:url';
import { HttpApi, login, unique, type Api, type Row } from './api.ts';
import { EVENT_TITLE, ORGANIZATION_NAME, VENUE_NAME } from './bodies.ts';
import { config, flags, loadLocalEnv } from './config.ts';

export async function resetPlan(api: Api, adminEmail: string, adminPassword: string, log = console.log) {
  const token = await login(api, adminEmail, adminPassword);
  const event = unique(await api.request<Row[]>('GET', '/events', undefined, token), row => row.title === EVENT_TITLE, EVENT_TITLE);
  if (!event) { log('Демо-событие отсутствует; сброс не требуется.'); return; }
  const [organization, venue] = await Promise.all([
    api.request<Row>('GET', `/organizations/${event.organizationId}`, undefined, token),
    api.request<Row>('GET', `/venues/${event.venueId}`, undefined, token),
  ]);
  if (organization.name !== ORGANIZATION_NAME || venue.name !== VENUE_NAME || !/^[0-9a-f-]{36}$/i.test(event.id)) {
    throw new Error('Событие не соответствует демо-организации/площадке. Сброс остановлен.');
  }
  // Neither the current API nor specification §5 defines DELETE /events/{id}.
  // SQL is an explicit operator fallback; this HTTP client never opens a DB connection.
  log(`DELETE события отсутствует в API/ТЗ. Для ${event.id} подготовлен scripts/demo/reset.sql.`);
  log(`На остановленном демо-стенде, после резервной копии, из scripts/demo выполните:\npsql "$DEMO_DATABASE_URL" -v ON_ERROR_STOP=1 -v demo_event_id=${event.id} -f reset.sql`);
  log('SQL удалит событие, заявки, платежи, заплывы, результаты и осиротевшие импортированные профили. Аккаунты, организация, площадка и шаблоны сохраняются для повторного seed.');
  log('Этот запуск только подготовил команду: данные ещё не удалены.');
}
async function main() {
  loadLocalEnv();
  const args = flags(process.argv.slice(2), ['--help']);
  if (args.has('--help')) { console.log('npx tsx reset.ts\nHTTP-проверка демо-события и команда SQL-сброса; DELETE события отсутствует в контракте.'); return; }
  const settings = config(process.env, false);
  await resetPlan(new HttpApi(settings.apiUrl), settings.adminEmail, settings.adminPassword);
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => { console.error(error instanceof Error ? error.message : 'Сброс завершился ошибкой.'); process.exitCode = 1; });
}
