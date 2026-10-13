import { execFile } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { HttpApi, login, unique, type Api, type Row } from './api.ts';
import { demoEventTitle, ORGANIZATION_NAME, VENUE_NAME } from './bodies.ts';
import { config, flags, loadLocalEnv } from './config.ts';

export type ResetOptions = { apply?: boolean; databaseUrl?: string; eventTitleSuffix?: string };
// Keep the database URL out of argv and diagnostics; libpq reads PGDATABASE.
export function applyReset(eventId: string, title: string, databaseUrl: string): Promise<string> {
  const args = ['-v', 'ON_ERROR_STOP=1', '-v', `demo_event_id=${eventId}`, '-v', `demo_event_title=${title}`,
    '-f', fileURLToPath(new URL('./reset.sql', import.meta.url))];
  const redact = (text: string) => {
    let safe = text.split(databaseUrl).join('[DATABASE_URL]');
    try {
      const password = decodeURIComponent(new URL(databaseUrl).password);
      if (password) safe = safe.split(password).join('[PASSWORD]');
    } catch { /* libpq also supports connection strings. */ }
    return safe;
  };
  return new Promise((resolve, reject) => {
    execFile('psql', args, { env: { ...process.env, PGDATABASE: databaseUrl }, timeout: 60_000, maxBuffer: 1024 * 1024 }, (error, stdout, stderr) => {
      if (error) {
        // Do not print error.message: Node includes the full command and arguments.
        const code = 'code' in error ? String(error.code) : 'unknown';
        reject(new Error(`Сброс psql завершился ошибкой (${code}). Проверьте доступность psql, подключение и журнал БД. Данные могли не измениться; транзакция SQL откатывается при ошибке.`));
      } else resolve(redact([stdout.trim(), stderr.trim()].filter(Boolean).join('\n')));
    });
  });
}
export async function resetPlan(api: Api, adminEmail: string, adminPassword: string, log = console.log, options: ResetOptions = {}) {
  if (options.apply && !options.databaseUrl?.trim()) throw new Error('Для --apply задайте DEMO_DATABASE_URL через окружение или .env.');
  const title = demoEventTitle(options.eventTitleSuffix);
  const token = await login(api, adminEmail, adminPassword);
  const event = unique(await api.request<Row[]>('GET', '/events', undefined, token), row => row.title === title, title);
  if (!event) { log('Демо-событие отсутствует; сброс не требуется.'); return; }
  const [organization, venue] = await Promise.all([
    api.request<Row>('GET', `/organizations/${event.organizationId}`, undefined, token),
    api.request<Row>('GET', `/venues/${event.venueId}`, undefined, token),
  ]);
  if (organization.name !== ORGANIZATION_NAME || venue.name !== VENUE_NAME || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(event.id)) {
    throw new Error('Событие не соответствует демо-организации/площадке. Сброс остановлен.');
  }
  // DELETE /events/{id} is absent from the contract; only explicit --apply runs SQL.
  log(`DELETE события отсутствует в API/ТЗ. Для ${event.id} подготовлен scripts/demo/reset.sql.`);
  const shellTitle = "'" + title.replaceAll("'", "'\\''") + "'";
  log(`На остановленном демо-стенде, после резервной копии, из scripts/demo выполните:\npsql "$DEMO_DATABASE_URL" -v ON_ERROR_STOP=1 -v demo_event_id=${event.id} -v demo_event_title=${shellTitle} -f reset.sql`);
  log('SQL удалит событие, заявки, платежи, заплывы, результаты и осиротевшие импортированные профили. Аккаунты, организация, площадка и шаблоны сохраняются для повторного seed.');
  if (options.apply) {
    log(await applyReset(event.id, title, options.databaseUrl!));
    log(`SQL-сброс завершён: ${event.id}. При несовпадении UUID/названия в БД SQL ничего не удаляет.`);
  } else log('Этот запуск только подготовил команду: данные ещё не удалены. Для выполнения используйте --apply и DEMO_DATABASE_URL.');
}
async function main() {
  loadLocalEnv();
  const args = flags(process.argv.slice(2), ['--apply', '--help']);
  if (args.has('--help')) { console.log('npx tsx reset.ts [--apply]\nБез флага: HTTP-проверка и команда SQL. --apply: запуск psql с DEMO_DATABASE_URL. EVENT_TITLE_SUFFIX выбирает резервное событие.'); return; }
  const settings = config(process.env, false);
  await resetPlan(new HttpApi(settings.apiUrl), settings.adminEmail, settings.adminPassword, console.log,
    { apply: args.has('--apply'), databaseUrl: process.env.DEMO_DATABASE_URL, eventTitleSuffix: settings.eventTitleSuffix });
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => { console.error(error instanceof Error ? error.message : 'Сброс завершился ошибкой.'); process.exitCode = 1; });
}
