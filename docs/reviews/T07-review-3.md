# Ревью PR #8 · feat/T07-public-pwa · третий проход · 13.10.2026

**Вердикт: правки A, B, C и обе необязательные выполнены; код готов — слить после слияния T04 и ребейза (п.1 ревью-1, по-прежнему 10 конфликтов).** Новых блокирующих замечаний нет; ниже две косметики, которые можно закрыть при ребейзе.

Проверялся коммит `530ebfe` поверх `4319781` (`git diff 4319781..530ebfe`): 15 файлов, +169/−23, один коммит `fix(public): align mock flags and handle nullable venue`. Сборка/тесты в контейнере недоступны — проверка чтением диффа и файлов ветки. `docs/` и `tasks/` корня, бэкенд `sport-platform/src` не тронуты (`git diff --stat 4319781..530ebfe -- docs tasks sport-platform/src` пуст; `git diff --stat ed47c88..origin/feat/T07-public-pwa -- docs tasks` пуст). Бинарники в этом коммите не менялись; иконки в ветке небольшие: `public/icons/flounder-192.png` 4 467 Б, `flounder-512.png` 12 471 Б, `flounder-maskable-512.png` 5 895 Б, `flounder.svg` 209 Б (`git ls-tree -r -l`).

## Таблица по запрошенным правкам (ревью-2)

| № | Правка | Статус | Где |
|---|--------|--------|-----|
| A | Единый флаг `VITE_API_MOCK` в `.env.demo`/`.env.example`, удалить `build:demo`, README | **выполнено** | `.env.demo:1` `VITE_API_MOCK=true`; `.env.example:3` `VITE_API_MOCK=false`; `package.json` — строка `build:demo` удалена (остались `dev:demo`, `build`, `test`, `test:e2e`); `README.md:39–42` описывает `npm run dev:demo` и `VITE_API_MOCK=true npm run dev`, `:51–53` — «production build always excludes MSW». `git grep VITE_PUBLIC_API_MOCKS origin/feat/T07-public-pwa -- sport-platform/frontend` → пусто. Код читает флаг в `src/main.tsx:10–14` (`DEV && (VITE_API_MOCK === 'true' \|\| MODE === 'demo')`) — соответствует env-файлам. |
| B1 | `venue` nullable, `courseLength` nullable в типах | **выполнено** | `public.types.ts:52–57` `venue: { name; city; lanes: number; courseLength: number \| null } \| null`. |
| B2 | `null` в PublicLayout/EventPage/Protocol → «Площадка не указана» | **выполнено** | `PublicLayout.tsx:113` `venue?.city ?? 'Площадка не указана'`; `EventPage.tsx:22–24` — текст-заглушка, `:26–33` блок «дорожек · бассейн» рендерится только при `venue`, при `courseLength === null` — «длина бассейна не указана»; `Protocol.tsx:21–23` `PrintHeader`. Другие обращения к `event.venue` в `src/` отсутствуют (`git grep venue` — остальное organizer `venueId`). |
| B3 | `lanes ?? 8` в LivePage/HeatPage/StartListPage | **выполнено** | `LivePage.tsx:287`, `HeatPage.tsx:32`, `StartListPage.tsx:38` — `event.venue?.lanes ?? 8` (совпадает с `asInt(8)` бэкенда T06). |
| B4 | `swims[].lane: number \| null` → «—» | **выполнено** | `public.types.ts:122` `lane: number \| null`; `LivePage.tsx:223` `дорожка {swim.lane ?? '—'}`. |
| C | `<title>Flounder</title>` + favicon из манифеста | **выполнено** | `index.html:5–10` `<link rel="icon" type="image/png" sizes="192x192" href="/icons/flounder-192.png">` = `manifest.icons[0].src` (`vite.config.ts:47–53`); `:15` `apple-touch-icon` тот же файл; `:16` `<title>Flounder</title>` (был и раньше; `PublicLayout.tsx:72–73` переопределяет `document.title` названием события). |
| доп. 1 | e2e: в `dist/assets` нет `msw`/`demo` | **выполнено** | `e2e/public.spec.ts:2` импорт `readdir`, `:158–162` `readdir('dist/assets').filter(/msw\|demo/i)` → `[]` в тесте прод-PWA; `:184–187` favicon `href` === `manifest.icons[0].src`, `toHaveTitle(/Flounder/)`. Требует `npm run build` перед `test:e2e` — README так и описывает (`:43–49`), `e2e/public-server.ts` раздаёт `dist`. |
| доп. 2 | `@media print { .heatNumber { font-size: 18pt } }` | **выполнено** | `Public.module.css:573–577`. |
| тесты | Покрытие новых веток | **выполнено** | `Review.test.tsx:190–292`: `venue: null` в layout + карточке (2 вхождения заглушки), `courseLength: null` («8 дорожек · длина бассейна не указана»), `PrintHeader` без площадки, 8 дорожек на Live (1 таблица × 9 строк), стартовом протоколе (6 × 9) и странице заплыва, «дорожка —» в «Найти себя» через `user-event` (`package.json:25`, зависимость есть). Хелперы `renderPage`/`PublicContext` (`:22–35`) переиспользованы. Запустить не могу — проверено чтением. |

## Регрессии

Не найдены. Изменения в UI-компонентах — только добавление `?.`/`??` и условных блоков; прежнее поведение при заполненном `venue` сохранено (`EventPage.tsx:26–33` выводит те же «N дорожек · бассейн M м»). Типы ужесточены (`| null`), поэтому любое новое обращение к `event.venue.x` без проверки не пройдёт `tsc -b` в `npm run build` — защита от повторения.

## Косметика (не блокирует; можно при ребейзе)

- `public/favicon.svg` (9,5 КБ) больше нигде не упоминается (`index.html` теперь ссылается на PNG), но остаётся в `includeAssets` (`vite.config.ts:34`) и попадает в precache. Либо удалить файл и строку, либо вернуть `<link rel="icon" type="image/svg+xml">` как вторую иконку.
- `Public.module.css` содержит два блока `@media print` (`:413`, `:573`); новый `.heatNumber` можно перенести в первый.

## Остаток

1. **Ребейз на T04 после его слияния** (п.1 ревью-1) — обязателен. `git merge-tree --write-tree origin/feat/T04-organizer-ui origin/feat/T07-public-pwa` (T04 = `6cfba22`) → **10 конфликтов**: add/add — `.env.example`, `public/mockServiceWorker.js`, `src/mocks/browser.ts`, `src/mocks/handlers.ts`; content — `.gitignore`, `package-lock.json`, `package.json`, `src/api/http.ts`, `src/app/App.tsx`, `src/main.tsx`. Список и план разрешения — в ревью-1 (переименование `handlers.ts` → `public-handlers.ts`, единый `VITE_API_MOCK` — уже совпадает с T04, одна версия `msw`, роли и маршруты T04 в `App.tsx`, `registerSW` в `main.tsx`).
2. Косметика выше (favicon.svg, два `@media print`) — по желанию.
3. Расхождения с T06 пп.2, 4 из ревью-2 (опциональные `sequenceInSession/status` у `Heat`, `startsAt` у `StartSession`) — необязательны, остаются как есть.
