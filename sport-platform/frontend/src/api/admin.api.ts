import { apiFetch } from './http'

export type Role = { code: string; name: string }
export type AdminUser = {
  id: string; email: string; firstName: string | null; lastName: string | null
  status: string; roles: string[]; createdAt: string
}
export type UserPage = { items: AdminUser[]; page: number; size: number; total: number }
export type CreateUser = {
  email: string; firstName: string; lastName: string; password: string
  birthDate: string; roles: string[]
}
export const getRoles = (accessToken: string) => apiFetch<Role[]>('/api/v1/roles', { accessToken })
export const getUsers = (accessToken: string, query: string, role: string, page: number, signal?: AbortSignal) =>
  apiFetch<UserPage>(`/api/v1/users?${new URLSearchParams({ query, role, page: String(page), size: '20' })}`, { accessToken, signal })
export const createUser = (accessToken: string, body: CreateUser) =>
  apiFetch<{ id: string }>('/api/v1/users', { accessToken, method: 'POST', body: JSON.stringify(body) })
export const setUserStatus = (accessToken: string, id: string, status: string) =>
  apiFetch(`/api/v1/users/${id}`, { accessToken, method: 'PATCH', body: JSON.stringify({ status }) })
export const changePassword = (accessToken: string, id: string, newPassword: string) =>
  apiFetch(`/api/v1/users/${id}/password`, { accessToken, method: 'PATCH', body: JSON.stringify({ newPassword }) })
export const assignRole = (accessToken: string, id: string, roleCode: string) =>
  apiFetch(`/api/v1/users/${id}/roles`, { accessToken, method: 'POST', body: JSON.stringify({ roleCode }) })
export const revokeRole = (accessToken: string, id: string, code: string) =>
  apiFetch(`/api/v1/users/${id}/roles/${encodeURIComponent(code)}`, { accessToken, method: 'DELETE' })
export type UserRoleAssignment = Role & { organizationId?: string | null; eventId?: string | null }
export const getUserDetails = (accessToken: string, id: string) =>
  apiFetch<{ roles: UserRoleAssignment[] }>(`/api/v1/users/${id}`, { accessToken })
