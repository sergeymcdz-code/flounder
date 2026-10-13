import { http, HttpResponse } from 'msw'
import type { AdminUser, CreateUser, Role } from '../api/admin.api'
import type { OrganizationMember, OrganizationRegistrationResponse, OrganizationResponse } from '../api/organizations.api'

export const adminRoles: Role[] = [
  { code: 'platform_admin', name: 'Администратор платформы' }, { code: 'organizer', name: 'Организатор' },
  { code: 'operator', name: 'Оператор' }, { code: 'judge', name: 'Судья' }, { code: 'participant', name: 'Участник' },
]
export const adminFixture: AdminUser = { id: 'admin-demo', email: 'admin@flounder.demo', firstName: 'Сергей', lastName: 'Администратор', status: 'ACTIVE', roles: ['platform_admin'], createdAt: '2026-10-12T10:00:00Z' }
const userFixture: AdminUser = { id: 'user-demo', email: 'user@flounder.demo', firstName: 'Анна', lastName: 'Иванова', status: 'ACTIVE', roles: ['participant'], createdAt: '2026-10-13T10:00:00Z' }
let users: AdminUser[] = []
let organizations: OrganizationResponse[] = []
let members: OrganizationMember[] = []
let requests: OrganizationRegistrationResponse[] = []
export function resetAdminMocks() {
  users = [structuredClone(userFixture), structuredClone(adminFixture)]
  organizations = [{ id: 'organization-demo', name: 'Клуб Волна', type: 'club', legalName: null, inn: null,
    contactEmail: 'user@flounder.demo', contactPhone: null, city: 'Самара', owner: 'user@flounder.demo', memberCount: 1,
    createdAt: '2026-10-12T10:00:00Z', updatedAt: '2026-10-12T10:00:00Z' }]
  members = [{ id: 'member-demo', organizationId: 'organization-demo', userId: 'user-demo', email: userFixture.email,
    firstName: userFixture.firstName, lastName: userFixture.lastName, memberRole: 'owner', createdAt: userFixture.createdAt }]
  requests = [{ id: 'request-demo', applicantUserId: 'user-demo', organizationType: 'club', organizationName: 'Новый клуб',
    legalName: null, inn: null, contactEmail: userFixture.email, contactPhone: null, status: 'PENDING', rejectionReason: null,
    reviewedByUserId: null, reviewedAt: null, createdAt: userFixture.createdAt, updatedAt: userFixture.createdAt }]
}
resetAdminMocks()
const failure = (status: number, code: string, message: string) => HttpResponse.json({ code, message, violations: [] }, { status })
function guard(request: Request) {
  try {
    const payload = JSON.parse(atob((request.headers.get('Authorization') ?? '').split('.')[1])) as { roles: string[] }
    if (payload.roles?.includes('platform_admin')) return null
  } catch { /* Invalid mock token. */ }
  return failure(403, 'identity.access_denied', 'Доступ запрещён')
}
export const adminHandlers = [
  http.post('*/api/v1/auth/login', async ({ request }) => {
    const body = await request.clone().json() as { email: string; password: string }
    if (body.email.toLowerCase() !== adminFixture.email) return
    if (body.password !== 'Demo2026!') return failure(401, 'identity.invalid_credentials', 'Неверный email или пароль')
    const accessToken = `demo.${btoa(JSON.stringify({ uid: adminFixture.id, roles: ['platform_admin'] }))}.demo`
    return HttpResponse.json({ accessToken, tokenType: 'Bearer', expiresIn: 7200 })
  }),
  http.get('*/api/v1/roles', ({ request }) => guard(request) ?? HttpResponse.json(adminRoles)),
  http.get('*/api/v1/users', ({ request }) => {
    const denied = guard(request); if (denied) return denied
    const params = new URL(request.url).searchParams
    const query = (params.get('query') ?? '').toLowerCase(); const role = params.get('role')
    const page = Number(params.get('page') ?? 0); const size = Number(params.get('size') ?? 20)
    const found = users.filter(user => (!role || user.roles.includes(role)) && [user.email, user.firstName, user.lastName].some(value => value?.toLowerCase().includes(query)))
    return HttpResponse.json({ items: found.slice(page * size, (page + 1) * size), page, size, total: found.length })
  }),
  http.post('*/api/v1/users', async ({ request }) => {
    const denied = guard(request); if (denied) return denied
    const body = await request.json() as CreateUser
    if (users.some(user => user.email === body.email)) return failure(409, 'identity.user_already_exists', 'Пользователь уже существует')
    const user: AdminUser = { id: crypto.randomUUID(), email: body.email, firstName: body.firstName, lastName: body.lastName, roles: body.roles, status: 'ACTIVE', createdAt: new Date().toISOString() }
    users.unshift(user); return HttpResponse.json(user, { status: 201 })
  }),
  http.get('*/api/v1/users/:id', ({ request, params }) => {
    const user = users.find(user => user.id === params.id)
    return guard(request) ?? (user ? HttpResponse.json({ ...user, roles: user.roles.map(code => adminRoles.find(role => role.code === code)), memberships: members.filter(member => member.userId === user.id).map(member => ({ organizationId: member.organizationId, organizationName: organizations.find(org => org.id === member.organizationId)?.name, memberRole: member.memberRole })) }) : failure(404, 'identity.user_not_found', 'Пользователь не найден'))
  }),
  http.patch('*/api/v1/users/:id', async ({ request, params }) => {
    const denied = guard(request); if (denied) return denied
    const user = users.find(user => user.id === params.id); if (!user) return failure(404, 'identity.user_not_found', 'Пользователь не найден')
    const body = await request.json() as { status: string }
    if (user.id === adminFixture.id && body.status === 'BLOCKED') return failure(403, 'identity.cannot_modify_self', 'Нельзя заблокировать себя')
    user.status = body.status; return HttpResponse.json(user)
  }),
  http.patch('*/api/v1/users/:id/password', async ({ request }) => {
    const denied = guard(request); if (denied) return denied
    const body = await request.json() as { newPassword: string }
    return body.newPassword.length >= 8 ? new HttpResponse(null, { status: 204 }) : failure(400, 'identity.invalid_password', 'Пароль должен содержать не менее 8 символов')
  }),
  http.post('*/api/v1/users/:id/roles', async ({ request, params }) => {
    const denied = guard(request); if (denied) return denied
    const user = users.find(user => user.id === params.id); if (!user) return failure(404, 'identity.user_not_found', 'Пользователь не найден')
    const body = await request.json() as { roleCode: string }
    if (!user.roles.includes(body.roleCode)) user.roles.push(body.roleCode)
    return new HttpResponse(null, { status: 201 })
  }),
  http.delete('*/api/v1/users/:id/roles/:code', ({ request, params }) => {
    const denied = guard(request); if (denied) return denied
    if (params.id === adminFixture.id && params.code === 'platform_admin') return failure(403, 'identity.cannot_modify_self', 'Нельзя снять у себя роль администратора')
    const user = users.find(user => user.id === params.id); if (!user) return failure(404, 'identity.user_not_found', 'Пользователь не найден')
    user.roles = user.roles.filter(code => code !== params.code); return new HttpResponse(null, { status: 204 })
  }),
  http.get('*/api/v1/organizations', ({ request }) => guard(request) ? undefined : HttpResponse.json(organizations.map(org => ({ ...org, memberCount: members.filter(member => member.organizationId === org.id).length })))),
  http.get('*/api/v1/organizations/:id', ({ request, params }) => guard(request) ?? HttpResponse.json(organizations.find(org => org.id === params.id))),
  http.post('*/api/v1/organizations', async ({ request }) => {
    const denied = guard(request); if (denied) return denied
    const body = await request.json() as OrganizationResponse
    const org = { ...body, id: crypto.randomUUID(), createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), memberCount: 0 }
    organizations.push(org); return HttpResponse.json(org, { status: 201 })
  }),
  http.get('*/api/v1/organizations/:id/members', ({ request, params }) => guard(request) ?? HttpResponse.json(members.filter(member => member.organizationId === params.id))),
  http.post('*/api/v1/organizations/:id/members', async ({ request, params }) => {
    const denied = guard(request); if (denied) return denied
    const body = await request.json() as { email?: string; userId?: string; memberRole: string }
    const user = users.find(user => body.userId ? user.id === body.userId : user.email.toLowerCase() === body.email?.toLowerCase())
    if (!user) return failure(404, 'organizations.user_not_found', 'Пользователь с таким email не найден')
    const member = { id: crypto.randomUUID(), organizationId: String(params.id), userId: user.id, email: user.email, firstName: user.firstName, lastName: user.lastName, memberRole: body.memberRole, createdAt: new Date().toISOString() }
    members = members.filter(value => value.organizationId !== params.id || value.userId !== user.id); members.push(member)
    return HttpResponse.json(member, { status: 201 })
  }),
  http.patch('*/api/v1/organizations/:id/members/:userId', async ({ request, params }) => {
    const denied = guard(request); if (denied) return denied
    const member = members.find(value => value.organizationId === params.id && value.userId === params.userId)
    if (!member) return failure(404, 'organizations.member_not_found', 'Участник не найден')
    const body = await request.json() as { memberRole: string }; member.memberRole = body.memberRole
    return HttpResponse.json(member)
  }),
  http.delete('*/api/v1/organizations/:id/members/:userId', ({ request, params }) => {
    const denied = guard(request); if (denied) return denied
    members = members.filter(value => value.organizationId !== params.id || value.userId !== params.userId)
    return new HttpResponse(null, { status: 204 })
  }),
  http.get('*/api/v1/organization-registration-requests', ({ request }) => guard(request) ?? HttpResponse.json(requests.filter(value => value.status === 'PENDING'))),
  http.post('*/api/v1/organization-registration-requests/:id/:decision', async ({ request, params }) => {
    const denied = guard(request); if (denied) return denied
    const value = requests.find(value => value.id === params.id)
    if (!value) return failure(404, 'organizations.registration_not_found', 'Заявка не найдена')
    if (params.decision === 'approve') {
      value.status = 'APPROVED'
      const orgId = crypto.randomUUID()
      organizations.push({ id: orgId, name: value.organizationName, type: value.organizationType, city: null,
        legalName: value.legalName, inn: value.inn, contactEmail: value.contactEmail, contactPhone: value.contactPhone,
        createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), memberCount: 1, owner: userFixture.email })
      members.push({ id: crypto.randomUUID(), organizationId: orgId, userId: userFixture.id, email: userFixture.email,
        firstName: userFixture.firstName, lastName: userFixture.lastName, memberRole: 'owner', createdAt: new Date().toISOString() })
    } else { value.status = 'REJECTED'; value.rejectionReason = (await request.json() as { comment: string }).comment }
    return HttpResponse.json(value)
  }),
  http.get('*/api/v1/events', ({ request }) => guard(request) ?? HttpResponse.json([])),
]
