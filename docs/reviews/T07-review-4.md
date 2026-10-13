# Ревью PR #8 · feat/T07-public-pwa · четвёртый проход (после ребейза на T04) · 13.10.2026

**Вердикт: слить.** Ребейз выполнен по правилам ревью-1 (п.1), все 10 конфликтов разрешены в пользу T04, публичные страницы T07 после ребейза не изменились, CI на голове зелёный, PR mergeable.

Проверялась голова `ea25276` (4 коммита поверх `origin/main 647dd9d`: `649319c`, `e9f2598`, `9348340`, `ea25276 fix(public): integrate T04 infrastructure after rebase`). `git merge-base origin/main origin/feat/T07-public-pwa` = `647dd9d` — ветка линейно поверх main; `git merge-tree --write-tree origin/main origin/feat/T07-public-pwa` → дерево `5480594`, без конфликтов; `GET /pulls/8` → `mergeable: true`, `mergeable_state: clean`. Диф к main: 53 файла, +12 705/−1 256 (из них `package-lock.json` +7 650). Сборка в контейнере недоступна — проверка чтением `git show`/`git diff` и по CI.

**CI на `ea25276` (run 38389653234, event `pull_request`): `backend` success, `frontend` success** (`npm ci && npm run lint && npm run build`; `build` = `tsc -b && vite build`, `tsconfig.app.json` включает `src`, т.е. тесты и `src/test/setup.ts` прошли проверку типов).

## Таблица проверок

| № | Что проверялось | Статус | Где / как |
|---|-----------------|--------|-----------|
| 1 | Ветка поверх `origin/main`, merge-tree чистый, PR mergeable | **ок** | merge-base = `647dd9d`; `merge-tree` без конфликтов; REST `mergeable_state: clean` |
| 2 | `src/mocks/browser.ts` — T04 `handlers` + `publicHandlers` + `authHandlers` | **ок** | `browser.ts:1–6` `setupWorker(...handlers, ...publicHandlers, ...authHandlers)`; `public-handlers.ts` байт-в-байт равен прежнему `handlers.ts` T07 (`git diff 530ebfe:…/handlers.ts ea25276:…/public-handlers.ts` пуст); `src/test/setup.ts:5` импортирует `publicHandlers` из `public-handlers` |
| 3 | `src/mocks/handlers.ts` T04 сохранён | **ок** | Диф к main — только пропуск `/auth/login` для демо-участника (`isDemoParticipant`, `request.clone().json()`) и исключение `/api/v1/public/`, `/api/v1/auth/` из guard-обработчика `http.all(path('/*'))`; вход организатора «любой email» оставлен |
| 4 | Единый флаг `VITE_API_MOCK`, нет `VITE_PUBLIC_API_MOCKS` | **ок** | `git grep VITE_PUBLIC_API_MOCKS origin/feat/T07-public-pwa -- sport-platform/frontend` → пусто; `.env.example` идентичен main (`VITE_API_BASE_URL`, `VITE_API_MOCK=false`), `.env.demo` — `VITE_API_MOCK=true` + `VITE_API_BASE_URL` |
| 5 | Одна версия `msw`, один `mockServiceWorker.js` | **ок** | `package.json` `msw ^3.0.3` (строка T04), в lock одна запись `node_modules/msw` 3.0.3; `public/mockServiceWorker.js` идентичен main (`PACKAGE_VERSION '3.0.3'`, диф пуст); `setup.ts:8` переведён на `onUnhandledFrame` (API msw 3, как в `main.tsx` T04) |
| 6 | `main.tsx`: моки только `DEV && VITE_API_MOCK==='true'`, иначе `registerSW` | **ок** | `main.tsx:10–15`; диф к main — одна строка условия + ветка `else { registerSW({ immediate: true }) }`; bootstrap/AuthProvider T04 сохранены. `vite-env.d.ts` добавлен `vite-plugin-pwa/client` |
| 7 | `api/http.ts`: `API_BASE_URL` T04 + fallback на origin | **ок** | `http.ts:1–5` `VITE_API_BASE_URL \|\| (VITE_API_MOCK==='true' \|\| PROD ? window.location.origin : '')`; `ApiError`/`FieldViolation`/`apiDownload` T04 не тронуты. Прод-образ (`frontend/Dockerfile`, `infra/compose.prod.yaml`) передаёт `VITE_API_BASE_URL=https://${DOMAIN}` — fallback не задействуется |
| 8 | `App.tsx`: маршруты/роли T04 и T08 сохранены, публичные добавлены | **ок** | Диф дерева `<Routes>` main → ветка: добавлен только блок `e/:slug` (7 маршрутов) и `EventDetailsPage` → `LegacyEventRedirect`; группа `RequireRole` с `judge` вокруг `operator/results` и `operator/competition-units/:unitId`, `organizer/events/:eventId/*` с вкладками (`program/entries/heats/results/registrations`), роли `platform_admin/platformadmin/operator` — без изменений |
| 9 | Lazy-импорты не ломают страницы T04/T08 | **ок** | Все 25 `lazy(() => import(...).then(m => ({default: m.X})))` сверены с экспортами: каждый модуль существует и экспортирует именованную функцию `X`; один `Suspense` с `role="status"` поверх дерева |
| 10 | `vite.config.ts`: PWA по флагу | **ок** | `mocksEnabled = (process.env.VITE_API_MOCK ?? loadEnv(mode).VITE_API_MOCK) === 'true'` → `VitePWA({ disable: mocksEnabled })`; покрывает и `dev:demo` (`--mode demo` → `.env.demo`), и `VITE_API_MOCK=true npm run dev` (organizer e2e); `excludeMockWorker` удаляет `dist/mockServiceWorker.js` на build |
| 11 | `.gitignore` объединён, `.env.example` один | **ок** | `.gitignore` = main + `!.env.demo`, `work/`; `.env.example` один, равен main |
| 12 | Удаление `EventDetailsPage.tsx` + CSS | **ок** | Запрошено в ревью-1 п.12; ссылок на компонент в ветке нет (`git grep EventDetailsPage` → пусто), маршрут `/events/:eventCode` → `LegacyEventRedirect` |
| 13 | Публичные страницы/типы после ребейза | **ок, без регрессий** | `git diff --stat 530ebfe ea25276 -- src/features/public src/api/public.api* src/mocks/demo-data.ts src/features/auth src/utils index.html e2e/public-server.ts scripts` → пусто; `public.types.ts:52–57` `venue: {…courseLength: number \| null} \| null`; `index.html:16` `<title>Flounder</title>`, favicon PNG из манифеста |
| 14 | Скрипты | **ок** | `package.json`: `test` = `node --test tests/ && vitest run` (Node-раннер T04 сохранён), `test:organizer` (T04, `playwright.organizer.config.ts`, `testMatch organizer.spec.ts`), `test:e2e` (`playwright.config.ts`, `testMatch public.spec.ts` — конфиги разделены), `dev:demo` |
| 15 | `tasks/`, `docs/` корня, бэкенд не тронуты; `frontend/docs` не вернулся; нет больших бинарников | **ок** | `git diff --stat origin/main … -- docs tasks sport-platform/src sport-platform/pom.xml` пуст; `git ls-tree … frontend/docs` пуст; `work/` в `.gitignore`, в дереве отсутствует; новые бинарники — только иконки 4–12 КБ |
| 16 | CI зелёный на голове | **ок** | `backend` success, `frontend` success на `ea25276` |

## Регрессии

Не найдены. Единственные правки в файлах T04 — описанные в пп.3, 6, 7 (одна строка условия, `else`-ветка, `|| PROD`, пропуск демо-участника и `/auth/` в guard). Тесты T04 (`tests/`, `e2e/organizer.spec.ts`) не изменены; по описанию PR `test:organizer` — 15 сценариев, exit 0 (в CI не запускается — там только lint + build).

## Остаток (не блокирует слияние)

1. Косметика из ревью-3 не закрыта: `public/favicon.svg` (9,5 КБ) остаётся в `includeAssets` (`vite.config.ts:37`) и precache, хотя `index.html` на него не ссылается; два блока `@media print` в `Public.module.css` (`:413`, `:573`).
2. `src/mocks/README.md` (T04) описывает только организаторские моки — одной строкой упомянуть `public-handlers.ts`/`auth-handlers.ts` и демо-аккаунт участника.
3. Вложения к PR (PDF, скриншоты 375×812, Lighthouse) по-прежнему не прикреплены — агент не может загружать attachments; приложить через GitHub UI из `work/review-artifacts/` (в git не возвращать).
4. Из ревью-3 п.3: опциональные `sequenceInSession/status` у `Heat`, `startsAt` у `StartSession` — остаются как есть.
