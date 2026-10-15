import { execFile } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { HttpApi, login, unique, type Api, type Row } from './api.ts';
import { ORGANIZATION_NAME, VENUE_NAME } from './bodies.ts';
import { config, flags, loadLocalEnv } from './config.ts';
import { selectedEvents } from './plan.ts';

export type ResetOptions = { apply?: boolean; databaseUrl?: string; eventTitleSuffix?: string; demoEvents?: string; demoSlugs?: string };
export type ResetTarget = { id: string; title: string; slug: string };
// Keep the database URL out of argv and diagnostics; libpq reads PGDATABASE.
export function applyReset(targets: ResetTarget[], databaseUrl: string): Promise<string> {
  const args = ['-v', 'ON_ERROR_STOP=1', '-v', `demo_targets=${JSON.stringify(targets)}`,
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
  const selected = options.demoSlugs?.split(',').map(slug => slug.trim());
  if (selected && (selected.some(slug => !/^[a-z0-9-]+$/.test(slug)) || new Set(selected).size !== selected.length))
    throw new Error('DEMO_SLUGS должен содержать уникальные slug через запятую.');
  const token = await login(api, adminEmail, adminPassword);
  const events = await api.request<Row[]>('GET', '/events', undefined, token);
  const targets: ResetTarget[] = [];
  const candidates = selected
    ? selected.map(slug => unique(events, row => row.publicSlug === slug, slug))
    : selectedEvents(options.demoEvents).map(spec => {
      const title = spec.title + (options.eventTitleSuffix ?? '');
      return unique(events, row => row.title === title, title);
    });
  for (const event of candidates) {
    if (!event) continue;
    const title = String(event.title);
    const [organization, venue] = await Promise.all([
      api.request<Row>('GET', `/organizations/${event.organizationId}`, undefined, token),
      api.request<Row>('GET', `/venues/${event.venueId}`, undefined, token),
    ]);
    if (organization.name !== ORGANIZATION_NAME || venue.name !== VENUE_NAME ||
        !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(event.id) ||
        !/^[a-z0-9-]+$/.test(String(event.publicSlug)))
      throw new Error('Событие не соответствует демо-организации/площадке. Сброс остановлен.');
    targets.push({ id: event.id, title, slug: String(event.publicSlug) });
  }
  if (selected?.some(slug => !targets.some(target => target.slug === slug)))
    throw new Error('DEMO_SLUGS содержит отсутствующее или чужое событие. Сброс остановлен.');
  if (!targets.length) { log('Демо-события отсутствуют; сброс не требуется.'); return; }
  const json = JSON.stringify(targets);
  const shellTargets = "'" + json.replaceAll("'", "'\\''") + "'";
  log(`DELETE события отсутствует в API. Цели SQL-сброса: ${json}`);
  log(`На остановленном демо-стенде, после резервной копии, из scripts/demo выполните:
psql "$DEMO_DATABASE_URL" -v ON_ERROR_STOP=1 -v demo_targets=${shellTargets} -f reset.sql`);
  log('SQL удалит выбранные события и зависимые записи; общие профили с заявками в других событиях сохраняются. Файлы документов остаются в хранилище для штатной очистки.');
  if (options.apply) {
    log(await applyReset(targets, options.databaseUrl!));
    log(`SQL-сброс завершён для ${targets.length} событий.`);
  } else log('Этот запуск только подготовил команду: данные ещё не удалены. Для выполнения используйте --apply и DEMO_DATABASE_URL.');
}

async function main() {
  loadLocalEnv();
  const args = flags(process.argv.slice(2), ['--apply', '--help']);
  if (args.has('--help')) { console.log('npx tsx reset.ts [--apply]\nБез флага: HTTP-проверка и команда SQL. --apply: запуск psql с DEMO_DATABASE_URL. DEMO_EVENTS=A,B,C или DEMO_SLUGS=slug1,slug2 выбирает события; EVENT_TITLE_SUFFIX выбирает резерв.'); return; }
  const settings = config(process.env, false);
  await resetPlan(new HttpApi(settings.apiUrl), settings.adminEmail, settings.adminPassword, console.log,
    { apply: args.has('--apply'), databaseUrl: process.env.DEMO_DATABASE_URL, eventTitleSuffix: settings.eventTitleSuffix, demoEvents: settings.demoEvents, demoSlugs: process.env.DEMO_SLUGS });
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => { console.error(error instanceof Error ? error.message : 'Сброс завершился ошибкой.'); process.exitCode = 1; });
}
