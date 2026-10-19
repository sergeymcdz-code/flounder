import { ApiError, login, unique, type Api, type Row } from './api.ts';
import { accounts, memberBody, roleBody, userBody } from './bodies.ts';

export async function findUser(api: Api, email: string, adminToken: string) {
  const users = await api.request<{ items: (Row & { roles: string[] })[] }>('GET', `/users?query=${encodeURIComponent(email)}&size=100`, undefined, adminToken);
  return unique(users.items, row => String(row.email).toLowerCase() === email, email);
}

// Look up accounts before login so missing accounts do not consume failed-login limits.
export async function ensureAccounts(api: Api, admin: string, organizationId: string,
  passwords: Record<string, string>, log = (_message: string) => {}) {
  const memberPath = `/organizations/${organizationId}/members`;
  const members = await api.request<Row[]>('GET', memberPath, undefined, admin);
  const tokens = { organizer: '', judge: '', secretary: '' };
  for (const account of accounts) {
    const password = passwords[account.key];
    const existing = await findUser(api, account.email, admin);
    if (!existing) await api.request('POST', '/users', userBody(account, password), admin);
    let token: string;
    try { token = await login(api, account.email, password); }
    catch (error) {
      if (!(error instanceof ApiError) || error.code !== 'identity.invalid_credentials') throw error;
      throw new Error(`Пользователь ${account.email} существует, но пароль не подходит. Проверьте ${account.key}_PASSWORD; пароль не изменяется сидом.`);
    }
    const user = await api.request<Row & { roles: string[] }>('GET', '/auth/me', undefined, token);
    if (user.email !== account.email) throw new Error(`API вернул неверного пользователя для ${account.email}.`);
    if (!user.roles.includes(account.role)) {
      await api.request('POST', `/users/${user.id}/roles`, roleBody(account.role), admin);
      token = await login(api, account.email, password);
    }
    if (account.memberRole) {
      const member = unique(members, row => row.userId === user.id, account.email);
      if (member && member.memberRole !== account.memberRole)
        throw new Error(`Неверное членство ${account.email}: требуется ${account.memberRole}. Исправьте через администратора.`);
      if (!member) await api.request('POST', memberPath, memberBody(user.id, account.memberRole), admin);
    }
    if (account.key === 'ORG') tokens.organizer = token;
    if (account.key === 'SEC') tokens.secretary = token;
    if (account.key === 'JUDGE') tokens.judge = token;
    log(`Пользователь готов: ${account.email} (${account.role}).`);
  }
  return tokens;
}
