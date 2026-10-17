import { ownerEmail, selectedEvents } from './plan.ts';
import { existsSync } from 'node:fs';
import { accounts, demoEventTitle } from './bodies.ts';

export function loadLocalEnv() {
  const path = new URL('./.env', import.meta.url);
  if (existsSync(path)) process.loadEnvFile(path);
}
export function config(env: NodeJS.ProcessEnv, needsUsers = true) {
  if (env.DEMO_SEED_ENABLED !== 'true' || env.NODE_ENV === 'production' || env.SPRING_PROFILES_ACTIVE?.split(',').includes('prod')) {
    throw new Error('Сид/сброс разрешён только на демо-стенде: DEMO_SEED_ENABLED=true, без production/prod.');
  }
  const required = (name: string) => {
    if (!env[name]?.trim()) throw new Error(`Задайте ${name} через окружение или scripts/demo/.env (см. .env.example).`);
    return env[name]!;
  };
  const url = new URL(required('API_URL'));
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash || !['', '/'].includes(url.pathname)) {
    throw new Error('API_URL должен быть HTTP(S) origin без пути /api/v1, пароля и query-параметров.');
  }
  demoEventTitle(env.EVENT_TITLE_SUFFIX);
  selectedEvents(env.DEMO_EVENTS);
  return {
    requireVenueMeta: env.REQUIRE_VENUE_META === 'true', eventTitleSuffix: env.EVENT_TITLE_SUFFIX ?? '',
    apiUrl: url.origin, adminEmail: required('ADMIN_EMAIL'), adminPassword: required('ADMIN_PASSWORD'),
    passwords: Object.fromEntries(needsUsers ? accounts.map(account => [account.key, required(env[`${account.key}_PASSWORD`] ? `${account.key}_PASSWORD` : 'DEMO_PASSWORD')]) : []),
    ownerEmail: ownerEmail(env.DEMO_OWNER_EMAIL), demoDate: env.DEMO_DATE, demoEvents: env.DEMO_EVENTS,
  };
}
export function flags(args: string[], allowed: string[]) {
  for (const arg of args) if (!allowed.includes(arg)) throw new Error(`Неизвестный параметр: ${arg}. Допустимы: ${allowed.join(', ')}.`);
  return new Set(args);
}
