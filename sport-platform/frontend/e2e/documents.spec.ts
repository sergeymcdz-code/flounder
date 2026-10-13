import { test, expect, type Page } from '@playwright/test'
const registration = {
  id: 'registration', eventId: 'event', eventDisciplineId: 'discipline', participantProfileId: 'child',
  participantUserId: 'parent', status: 'CONFIRMED', seedTimeMs: 32150, paymentStatus: 'NOT_REQUIRED', source: 'FORM',
  participantSnapshot: { lastName: 'Иванов', firstName: 'Иван', birthDate: '2014-03-05' },
  submittedAt: '2026-10-13T10:00:00Z', documents: { MEDICAL_CERT: 'NOT_UPLOADED' }, admissionWarnings: [],
}
const participantDocument = { id: 'document', profileId: 'child', eventId: null, type: 'MEDICAL_CERT', originalName: 'medical.pdf',
  contentType: 'application/pdf', sizeBytes: 9, issuedAt: '2026-10-13', validUntil: '2027-10-13', status: 'PENDING' }
async function session(page: Page, role: string) {
  await page.addInitScript((role) => sessionStorage.setItem('flounder.auth-session', JSON.stringify({
    accessToken: `test.${btoa(JSON.stringify({ roles: [role] }))}.test`, tokenType: 'Bearer', expiresAt: Date.now() + 3600000,
  })), role)
}
test('parent uploads and downloads a document at 375 px without overflow', async ({ page }) => {
  await session(page, 'participant')
  let uploaded = false
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/registrations/me') return route.fulfill({ json: [registration] })
    if (path === '/api/v1/profiles/child/documents') {
      if (route.request().method() === 'POST') {
        expect(route.request().headers()['content-type']).toContain('multipart/form-data; boundary=')
        expect(route.request().postData()).toContain('2027-10-13')
        uploaded = true; return route.fulfill({ status: 201, json: participantDocument })
      }
      return route.fulfill({ json: uploaded ? [participantDocument] : [] })
    }
    if (path === '/api/v1/documents/document/download') return route.fulfill({ json: { url: '/api/v1/public/documents/document/content?expires=123&signature=test', expiresAt: '2026-10-13T10:10:00Z' } })
    if (path === '/api/v1/public/documents/document/content') {
      expect(route.request().headers().authorization).toContain('Bearer test.')
      return route.fulfill({ contentType: 'application/pdf', body: '%PDF-1.7\nprivate' })
    }
    return route.fulfill({ json: [] })
  })
  await page.goto('/my/registrations')
  await expect(page.getByText('Документы пока не загружены.')).toBeVisible()
  await page.getByLabel('Файл документа').setInputFiles({ name: 'medical.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\n') })
  await page.getByLabel('Дата выдачи').fill('2026-10-13')
  await page.getByLabel('Действует до').fill('2027-10-13')
  await page.getByRole('button', { name: 'Загрузить документ' }).click()
  await expect(page.getByText('На проверке · medical.pdf')).toBeVisible()
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Скачать', exact: true }).click()
  expect((await download).suggestedFilename()).toBe('medical.pdf')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
test('organizer sees document indicators and filters missing medical certificates', async ({ page }) => {
  await session(page, 'organizer')
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/events/event') return route.fulfill({ json: { id: 'event', title: 'Кубок M04', status: 'REGISTRATION_OPEN', eventStartAt: '2026-10-19T10:00:00Z', eventEndAt: '2026-10-19T18:00:00Z', venueId: 'venue', disciplines: [] } })
    if (path === '/api/v1/registrations') return route.fulfill({ json: [registration, { ...registration, id: 'second', participantProfileId: 'second-child', participantSnapshot: { lastName: 'Петров', firstName: 'Пётр' }, documents: { MEDICAL_CERT: 'PENDING' } }] })
    return route.fulfill({ json: [] })
  })
  await page.goto('/organizer/events/event/entries')
  await expect(page.getByTitle('Медицинская справка: На проверке')).toBeVisible()
  await page.getByLabel('Нет справки').check()
  await expect(page.getByText('Найдено: 1')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Документы Иванов' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Документы Петров' })).toHaveCount(0)
})
