import { requiredCredential } from './credentials'
import { test, expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { api, login, password, request, submitDialog, token } from './qa07-support'

test('M01: admin manages a new user, revokes blocked JWT, creates and approves organizations', async ({ page, browser }) => {
  const email = `qa07-${randomUUID()}@test.example`
  const club = `Клуб QA07 ${randomUUID()}`
  const changedPassword = 'Qa07Changed2026!'
  await login(page, requiredCredential('ADMIN_EMAIL'), '/admin/users')
  await expect(page.getByRole('heading', { name: 'Пользователи', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Создать пользователя', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Email', { exact: true }).fill(email)
  await dialog.getByLabel('Имя', { exact: true }).fill('Регрессия')
  await dialog.getByLabel('Фамилия', { exact: true }).fill('QA07')
  await dialog.getByLabel('Дата рождения').fill('1990-01-01')
  await dialog.getByLabel('Новый пароль').fill(password)
  await dialog.getByLabel('Повторите пароль').fill(password)
  await submitDialog(page)
  await page.getByLabel('Поиск пользователей').fill(email)
  const row = page.getByRole('row').filter({ hasText: email })
  await expect(row).toHaveCount(1)
  const users = await api<{ items: { id: string }[] }>(page, `/users?query=${encodeURIComponent(email)}`)
  const id = users.items[0].id

  for (const grant of [true, false]) {
    await row.getByRole('button', { name: 'Роли', exact: true }).click()
    await dialog.getByRole('checkbox', { name: 'Судья', exact: true }).setChecked(grant)
    await submitDialog(page)
    const user = await api<{ roles: { code: string }[] }>(page, `/users/${id}`)
    expect(user.roles.some(role => role.code === 'judge')).toBe(grant)
    if (grant) await expect(row).toContainText('Судья')
    else await expect(row).not.toContainText('Судья')
  }

  const userContext = await browser.newContext()
  try {
    const userPage = await userContext.newPage()
    await login(userPage, email, '/', password)
    const oldToken = await token(userPage)
    await row.getByRole('button', { name: 'Заблокировать', exact: true }).click()
    await submitDialog(page, 'Подтвердить')
    await expect(row).toContainText('Заблокирован')
    const blockedToken = await request(page.request, '/auth/me', 'GET', undefined, oldToken)
    expect(blockedToken.status()).toBe(401)
    expect((await blockedToken.json()).code).toBe('identity.user_blocked')
    await userPage.evaluate(() => sessionStorage.removeItem('flounder.auth-session'))
    await userPage.goto('/login')
    await userPage.getByLabel('Email').fill(email)
    await userPage.getByLabel('Пароль', { exact: true }).fill(password)
    const rejectedLogin = userPage.waitForResponse(response => response.url().endsWith('/auth/login'))
    await userPage.getByRole('button', { name: 'Войти', exact: true }).click()
    expect((await rejectedLogin).status()).toBe(403)
    await expect(userPage.getByRole('alert')).toBeVisible()
    await expect(userPage).toHaveURL(/\/login/)
    await row.getByRole('button', { name: 'Разблокировать', exact: true }).click()
    await submitDialog(page, 'Подтвердить')
    await expect(row).toContainText('Активен')
    await login(userPage, email, '/')

    await row.getByRole('button', { name: 'Сменить пароль', exact: true }).click()
    await dialog.getByLabel('Новый пароль').fill(changedPassword)
    await dialog.getByLabel('Повторите пароль').fill(changedPassword)
    await submitDialog(page)
    const stalePassword = await request(page.request, '/auth/login', 'POST', { email, password })
    expect(stalePassword.status()).toBe(401)
    await login(userPage, email, '/', changedPassword)

    await page.goto('/admin/organizations')
    await page.getByRole('button', { name: 'Создать организацию', exact: true }).click()
    await dialog.getByLabel('Название', { exact: true }).fill(club)
    await dialog.getByLabel('Город', { exact: true }).fill('Самара')
    await submitDialog(page, 'Создать')
    await page.getByRole('link', { name: club, exact: true }).click()
    await page.getByRole('button', { name: 'Добавить участника', exact: true }).click()
    await dialog.getByLabel('Email пользователя').fill(email.toUpperCase())
    await dialog.getByLabel('Роль в организации').selectOption('member')
    await submitDialog(page)
    await expect(page.getByRole('row').filter({ hasText: email })).toContainText('Участник')
    const orgId = new URL(page.url()).pathname.split('/').at(-1)
    const members = await api<{ userId: string; memberRole: string }[]>(page, `/organizations/${orgId}/members`)
    expect(members.find(member => member.userId === id)?.memberRole).toBe('member')

    const requestedClub = `${club} по заявке`
    const application = await api<{ id: string }>(userPage, '/organization-registration-requests', 'POST', {
      organizationType: 'club', organizationName: requestedClub, contactEmail: email,
    })
    await page.goto('/admin/registration-requests')
    const card = page.getByRole('article').filter({ has: page.getByRole('heading', { name: requestedClub, exact: true }) })
    await card.getByRole('button', { name: 'Одобрить', exact: true }).click()
    await submitDialog(page, 'Подтвердить')
    await expect(card).toHaveCount(0)
    const approved = await api<{ id: string; status: string }[]>(page, '/organization-registration-requests?status=APPROVED')
    expect(approved.find(item => item.id === application.id)?.status).toBe('APPROVED')
    const organizations = await api<{ id: string; name: string }[]>(page, '/organizations')
    const created = organizations.find(org => org.name === requestedClub)!
    expect(created).toBeDefined()
    const owners = await api<{ userId: string; memberRole: string }[]>(page, `/organizations/${created.id}/members`)
    expect(owners.find(member => member.userId === id)?.memberRole).toBe('owner')
  } finally {
    await userContext.close()
  }
})
