import { loginBody } from './bodies.ts';

export class ApiError extends Error {
  constructor(public status: number, public code: string, method: string, path: string) {
    super((status === 404 || status === 405) && !code
      ? `Эндпоинт не найден: ${method} ${path} (HTTP ${status}). Сид остановлен: проверьте наличие API в main; SQL-обход шагов сида запрещён.`
      : `Ошибка API: ${method} ${path} (HTTP ${status}, код ${code || 'не указан'}). Проверьте права, параметры и журнал сервера.`);
  }
}
export interface Api {
  request<T>(method: string, path: string, body?: unknown, token?: string): Promise<T>;
}
export class HttpApi implements Api {
  private readonly deadline = Date.now() + 600_000;
  constructor(private readonly baseUrl: string) {}
  async request<T>(method: string, path: string, body?: unknown, token?: string): Promise<T> {
    const remaining = this.deadline - Date.now();
    if (remaining <= 0) throw new Error('Превышен бюджет 600 секунд. Повторите запуск: созданные данные будут найдены.');
    const multipart = body instanceof FormData;
    let response: Response;
    try {
      response = await fetch(`${this.baseUrl}/api/v1${path}`, {
        method, headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(body !== undefined && !multipart ? { 'Content-Type': 'application/json' } : {}),
        },
        body: body === undefined ? undefined : multipart ? body : JSON.stringify(body),
        signal: AbortSignal.timeout(Math.min(15_000, remaining)), redirect: 'error',
      });
    } catch {
      // Never include fetch options, credentials, server bodies or tokens in diagnostics.
      throw new Error(`Нет ответа API: ${method} ${path}. Проверьте API_URL, доступность стенда и тайм-аут (15 с).`);
    }
    const data = await response.text();
    let value: unknown;
    try { value = data ? JSON.parse(data) : undefined; } catch {
      if (response.ok) throw new Error(`API вернул не JSON: ${method} ${path} (HTTP ${response.status}). API_URL должен указывать на backend.`);
    }
    if (!response.ok) {
      const rawCode = typeof value === 'object' && value !== null && 'code' in value ? String(value.code) : '';
      const code = /^[a-z][a-z0-9_.]{0,100}$/.test(rawCode) ? rawCode : '';
      throw new ApiError(response.status, code, method, path);
    }
    return value as T;
  }
}
export async function login(api: Api, email: string, password: string): Promise<string> {
  const response = await api.request<{ accessToken: string }>('POST', '/auth/login', loginBody(email, password));
  if (!response?.accessToken) throw new Error('Ответ /auth/login не содержит accessToken.');
  return response.accessToken;
}
export type Row = { id: string; [key: string]: unknown };
export function unique(rows: Row[], predicate: (row: Row) => boolean, label: string): Row | undefined {
  if (!Array.isArray(rows)) throw new Error(`Неверный ответ списка: ${label}. Ожидается JSON-массив.`);
  const matches = rows.filter(predicate);
  if (matches.length > 1) throw new Error(`Найдено несколько объектов «${label}». Устраните неоднозначность перед повторным запуском.`);
  return matches[0];
}
export async function ensure(api: Api, token: string, path: string, predicate: (row: Row) => boolean, body: unknown, label: string): Promise<Row> {
  const found = unique(await api.request<Row[]>('GET', path, undefined, token), predicate, label);
  if (found) return found;
  return api.request<Row>('POST', path, body, token);
}
