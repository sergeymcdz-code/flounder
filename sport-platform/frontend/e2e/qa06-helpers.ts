import { expect, type Page } from '@playwright/test'

export const eventId = '11111111-1111-4111-8111-111111111111'
export const unitId = '00000010-1111-4111-8111-000000000001'
export const routes: [string, string, string][] = [
  [
    "home",
    "/",
    ""
  ],
  [
    "e-demo",
    "/e/demo",
    ""
  ],
  [
    "e-demo-start-list",
    "/e/demo/start-list",
    ""
  ],
  [
    "e-demo-schedule",
    "/e/demo/schedule",
    ""
  ],
  [
    "e-demo-live",
    "/e/demo/live",
    ""
  ],
  [
    "e-demo-results",
    "/e/demo/results",
    ""
  ],
  [
    "e-demo-register",
    "/e/demo/register",
    "participant"
  ],
  [
    "e-demo-heats",
    "/e/demo/heats/M-50-FREE-1",
    ""
  ],
  [
    "login",
    "/login",
    ""
  ],
  [
    "register",
    "/register",
    ""
  ],
  [
    "my-registrations",
    "/my/registrations",
    "participant"
  ],
  [
    "my-payment",
    "/my/registrations/00000004-1111-4111-8111-000000000001/payment",
    "participant"
  ],
  [
    "payment-return",
    "/payment-return?registrationId=00000004-1111-4111-8111-000000000001",
    "participant"
  ],
  [
    "legacy-register",
    "/events/demo/disciplines/M-50-FREE/register",
    "participant"
  ],
  [
    "legacy-results",
    "/events/demo/disciplines/M-50-FREE/results",
    ""
  ],
  [
    "organizer",
    "/organizer",
    "organizer"
  ],
  [
    "organizer-events",
    "/organizer/events?all=1",
    "organizer"
  ],
  [
    "organizer-events-new",
    "/organizer/events/new",
    "organizer"
  ],
  [
    "organizer-regulations-create",
    "/organizer/regulations/create",
    "organizer"
  ],
  [
    "organizer-event-home",
    "/organizer/events/11111111-1111-4111-8111-111111111111",
    "organizer"
  ],
  [
    "organizer-program",
    "/organizer/events/11111111-1111-4111-8111-111111111111/program",
    "organizer"
  ],
  [
    "organizer-entries",
    "/organizer/events/11111111-1111-4111-8111-111111111111/entries",
    "organizer"
  ],
  [
    "organizer-heats",
    "/organizer/events/11111111-1111-4111-8111-111111111111/heats",
    "organizer"
  ],
  [
    "organizer-results",
    "/organizer/events/11111111-1111-4111-8111-111111111111/results",
    "organizer"
  ],
  [
    "operator-registration",
    "/operator/registrations/00000004-1111-4111-8111-000000000001",
    "operator"
  ],
  [
    "operator-payments",
    "/operator/payments",
    "operator"
  ],
  [
    "operator-results",
    "/operator/results",
    "operator"
  ],
  [
    "operator-unit",
    "/operator/competition-units/00000010-1111-4111-8111-000000000001?eventId=11111111-1111-4111-8111-111111111111",
    "operator"
  ],
  [
    "judge-panel",
    "/operator/competition-units/00000010-1111-4111-8111-000000000001?eventId=11111111-1111-4111-8111-111111111111",
    "judge"
  ],
  [
    "admin-review",
    "/admin/registrations/review",
    "platform_admin"
  ],
  [
    "admin-sports",
    "/admin/sports",
    "platform_admin"
  ],
  [
    "legacy-event",
    "/events/demo",
    ""
  ]
]

const extraMocks = `async () => {
 const resources=performance.getEntriesByType('resource').map(x=>x.name);
 const {worker}=await import(resources.find(x=>new URL(x).pathname==='/src/mocks/browser.ts'));
 const {http,HttpResponse}=await import(resources.find(x=>new URL(x).pathname.endsWith('/msw.js')));
 const {createFixtures}=await import('/src/mocks/fixtures.ts');
 const f=createFixtures();
 const payment={id:'qa06-payment',registrationId:f.registrations[0].id,amount:500,currency:'RUB',status:'PENDING',provider:'MANUAL',confirmationUrl:null,paidAt:null,expiresAt:null,createdAt:'2026-10-10T12:00:00+03:00',updatedAt:'2026-10-10T12:00:00+03:00'};
 const json=HttpResponse.json;
 worker.use(
 http.get('*/api/v1/registrations/me',()=>json(f.registrations.slice(0,2).map(r=>({...r,eventTitle:f.event.title,disciplineName:f.event.disciplines.find(d=>d.id===r.eventDisciplineId)?.name,eventStartAt:f.event.eventStartAt,venueTimezone:f.event.venue?.timezone,publicSlug:f.event.publicSlug})))),
 http.get('*/api/v1/registrations/:id/payments',()=>json(payment)),
 http.get('*/api/v1/payments/review/registrations/:id',()=>json(payment)),
 http.get('*/api/v1/registrations/:id',({params})=>json(f.registrations.find(r=>r.id===params.id))),
 http.get('*/api/v1/sports',()=>json([{id:f.event.sportId,code:'SWIMMING',name:'Плавание',isActive:true}])),
 http.get('*/api/v1/organizations',()=>json([{id:f.event.organizationId,name:'Федерация плавания'}])),
 http.get('*/api/v1/regulation-versions',()=>json([{id:'qa06-regulation',versionNo:1,status:'PUBLISHED',effectiveFrom:'2026-10-01',rulesJson:{}}])),
 http.get('*/api/v1/public/events/:eventId/disciplines/:disciplineId/results',()=>json({eventId:f.event.id,eventDisciplineId:f.event.disciplines[0].id,units:[{id:f.heats[0].id,label:'Заплыв 1',sequenceNumber:1,entries:f.heats[0].entries.map((e,i)=>({participantName:e.participantName,place:i+1,laneOrPosition:e.laneOrPosition,clubName:'Дельфин',rawValue:'00:32.15',resultType:'TIME',status:'VALID'}))}]})),
 http.get('*/api/v1/competition-units/:id',({params})=>json({...f.heats.find(h=>h.id===params.id),eventId:f.event.id})),
 http.get('*/api/v1/organizer/events',()=>json([f.event])),
 http.get('*/api/v1/events/:id',()=>json(f.event)),
 http.get('*/api/v1/event-disciplines/:id/competition-units',({params})=>json(f.heats.filter(h=>h.eventDisciplineId===params.id)))
 );
}`

export async function openAuditPage(page: Page, path: string, role = '') {
  if (role) await page.addInitScript((role) => {
    sessionStorage.setItem('flounder.auth-session', JSON.stringify({
      accessToken: `demo.${btoa(JSON.stringify({ roles: [role] }))}.demo`,
      tokenType: 'Bearer', expiresAt: Date.now() + 3600000,
    }))
  }, role)
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: 30000 })
  await page.evaluate(`(${extraMocks})()`)
  await navigate(page, path)
}

export async function navigate(page: Page, path: string) {
  const pathname = path.split('?')[0]
  await page.evaluate((path) => {
    history.pushState(null, '', path)
    dispatchEvent(new PopStateEvent('popstate'))
  }, path)
  if (pathname !== '/') await expect(page.getByRole('heading', { name: 'Соревнования по плаванию', exact: true })).toHaveCount(0)
  await expect(page.getByText('Загружаем страницу…', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { level: 1 }).or(page.getByRole('alert')).first()).toBeVisible({ timeout: 30000 })
  await expect(page.getByText('Загружаем соревнование…', { exact: true })).toHaveCount(0)
  await expect(page.getByText('Загрузка…', { exact: true })).toHaveCount(0)
  if (pathname.includes('/organizer/events/11111111-1111-4111-8111-111111111111')) {
    if (pathname.endsWith('/entries') || pathname.endsWith('/registrations')) await expect(page.getByRole('button', { name: 'Импорт из Excel', exact: true })).toBeVisible()
    else if (pathname.endsWith('/program')) await expect(page.getByRole('button', { name: 'Добавить сессию', exact: true })).toBeVisible()
    else if (pathname.endsWith('/heats')) await expect(page.getByText('Сформировать заплывы ▾', { exact: true })).toBeVisible()
    else if (pathname.endsWith('/results')) await expect(page.getByRole('heading', { name: 'Результаты соревнования', exact: true })).toBeVisible()
    else if (await page.getByRole('alert').count() === 0) await expect(page.getByRole('heading', { name: 'Регистрация', exact: true })).toBeVisible()
  }
  if (pathname.startsWith('/operator/competition-units/') && await page.getByRole('alert').count() === 0)
    await expect(page.getByRole('table').or(page.getByRole('article').first())).toBeVisible()
  if (pathname === '/organizer/events/new' || pathname === '/organizer/regulations/create') await expect(page.getByRole('combobox').first()).toBeEnabled()
}

export async function scenario(page: Page, path: string, response: unknown, mode = 'json', method = 'get') {
  await page.evaluate(async ({path, response, mode, method}) => {
    const resources = performance.getEntriesByType('resource').map(x => x.name)
    const { worker } = await import(resources.find(x => new URL(x).pathname === '/src/mocks/browser.ts')!)
    const { http, HttpResponse, delay } = await import(resources.find(x => new URL(x).pathname.endsWith('/msw.js'))!)
    worker.use(http[method](`*/api/v1/${path}`, async () => {
      if (mode === 'network') return HttpResponse.error()
      if (mode === 'loading') await delay(60000)
      return HttpResponse.json(response, {status: mode === '500' ? 500 : 200})
    }))
  }, {path, response, mode, method})
}

export async function readState(page: Page, mode: string) {
  await page.evaluate(async (mode) => {
    const resources = performance.getEntriesByType('resource').map(x => x.name)
    const { worker } = await import(resources.find(x => new URL(x).pathname === '/src/mocks/browser.ts')!)
    const { http, HttpResponse, delay } = await import(resources.find(x => new URL(x).pathname.endsWith('/msw.js'))!)
    const { createFixtures } = await import('/src/mocks/fixtures.ts')
    const { demoEvent } = await import('/src/mocks/demo-data.ts')
    const f = createFixtures()
    worker.use(http.get('*/api/v1/*', async ({ request }: { request: Request }) => {
      if (mode === 'network') return HttpResponse.error()
      if (mode === '500') return HttpResponse.json({ code: 'qa06.unavailable', message: 'Сервис временно недоступен. Повторите попытку.' }, { status: 500 })
      if (mode === 'loading') await delay(60000)
      const path = new URL(request.url).pathname
      if (/\/public\/events\/demo$/.test(path)) return HttpResponse.json(demoEvent)
      if (/\/events\/11111111-1111-4111-8111-111111111111$/.test(path)) return HttpResponse.json(f.event)
      if (path.endsWith('/start-list')) return HttpResponse.json({ sessions: [] })
      if (path.endsWith('/live')) return HttpResponse.json({ currentUnit: null, next: [], updatedAt: new Date().toISOString() })
      if (path.endsWith('/registration-form')) return HttpResponse.json({ disciplines: [], requiresSeedTime: true })
      if (path.endsWith('/results')) return HttpResponse.json(path.includes('/disciplines/') ? { units: [] } : { disciplines: [] })
      if (/\/competition-units\/[^/]+$/.test(path)) return HttpResponse.json({ ...f.heats[0], entries: [] })
      if (/\/registrations\/[^/]+$/.test(path) && !path.endsWith('/me')) return HttpResponse.json({ message: 'Заявка не найдена.' }, { status: 404 })
      return HttpResponse.json([])
    }))
  }, mode)
}
