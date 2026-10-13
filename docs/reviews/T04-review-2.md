# Повторное ревью PR #4 · feat/T04-organizer-ui · 13.10.2026

**Вердикт: слить после одной правки** (блокирующая: неверный путь загрузки шаблонов дисциплин — на живом бэкенде создать вид по-прежнему нельзя).

Проверялся коммит `4b30499` поверх `9d9a130` (`git diff 9d9a130..4b30499`, 19 файлов, +873/−391). Ревью чтением кода, сборка/lint/Playwright в контейнере недоступны; по описанию PR `npm run build && npm run lint` — exit 0, 10 браузерных тестов проходят. Сверка с фактическими DTO веток `feat/T03-data-model` (`SwimDisciplineController`, `CreateEventDisciplineRequest`, `EventSessionResponse`, `SportsController`) и `feat/T02-scheduling` (`EventGenerationResponse`, `GenerationResponse`, `domain/Violation`, `HeatSeeder`).

## Статус 15 пунктов первого ревью

| № | Пункт | Статус | Где |
|---|---|---|---|
| 1 | Пресеты `{code,name,groups}` | **Выполнен** | `organizer.types.ts:11`; `OrganizerProgramPage.tsx:427,609,619`; мок `fixtures.ts:30–33`. Совпадает с `SwimDisciplineController.AgeGroupPreset(code,name,groups)`. |
| 2 | `disciplineTemplateId` + явное тело POST/PATCH | **Частично** | Тело собирается явно без `id/eventId/status/settingsJson` и без `competitionFormat/unitType/resultType/rankingStrategy` (`organizer.api.ts:62–79`) — верно. Селект «Шаблон», обязательный, по умолчанию первый (`OrganizerProgramPage.tsx:385–411,455–474`); кнопка «Сохранить» заблокирована без шаблона (`:720–723`). **Но** шаблоны грузятся с `GET /api/v1/sports/{sportId}/discipline-templates` (`organizer.api.ts:116–120`), которого нет ни в main, ни в T03 — см. блокирующее замечание А. |
| 3 | Колонка «Допуск» + фильтр «Не допущены» | **Выполнен** | `OrganizerEntriesPage.tsx:33–64` (`admission()`: `excludedRegistrationIds` + `violations[].registrationId`, коды `GENDER_MISMATCH/AGE_GROUP_MISMATCH/DUPLICATE_ENTRY`, русские `title`), фильтр `:77–80,126`, колонка `:170,203–205`. Результат хранится в контексте события (`OrganizerEventLayout.tsx:48–83`, `saveGeneration`). T02 `HeatSeeder` действительно кладёт `registrationId` в эти нарушения. |
| 4 | Сессии: «расч. окончание · N заплывов» | **Выполнен** | `Session.heatsCount?/estimatedEndsAt?` (`organizer.types.ts:18–19`), `sessionSummary()` с fallback по заплывам (`organizer-event-context.ts:87–112`), вывод `OrganizerProgramPage.tsx:78,90–98`. Совпадает с T03 `EventSessionResponse(heatsCount, estimatedEndsAt)`. `SessionInput` стал `Pick<…>`, тело PATCH/POST сессии явное (`sessions.api.ts:22–27`). |
| 5 | Формат ответа генерации по событию | **Выполнен** | `EventGenerationResult.disciplines` — union вложенного `{eventDisciplineId, result}` и плоского (`organizer.types.ts:107–114`), `normalizeGeneration()` (`organizer-event-context.ts:81–86`), использование `OrganizerHeatsPage.tsx:56–60`; мок переведён на `result:` (`handlers.ts:484–486`). Совпадает с T02 `EventGenerationResponse.Discipline(eventDisciplineId, result)`. |
| 6 | `Violation.eventDisciplineId/heatNumber/registrationId`, поиск заплыва по номеру | **Выполнен** | `organizer.types.ts:60–62`; `OrganizerHeatsPage.tsx:186–194` — сначала `eventDisciplineId && sequenceNumber === heatNumber`, затем `unitId/competitionUnitId`. Совпадает с T02 `domain/Violation`. |
| 7 | Селект дорожки без `venue.lanes` | **Выполнен** | `OrganizerHeatsPage.tsx:267` (`disabled={busy}`), число дорожек `venue.lanes ?? max(laneOrPosition) \|\| 8` (`:290–302`), подсказка оставлена. |
| 8 | Точечное обновление вместо полной перезагрузки | **Выполнен** | `run(action, target)` c `RefreshTarget = 'event' \| 'registrations' \| 'none' \| {heatId}` (`organizer-event-context.ts:11–12,33–36`, `OrganizerEventLayout.tsx:138–165`); смена дорожки → `GET /competition-units/{id}` (`OrganizerHeatsPage.tsx:275–283`), действия с заявкой/время/импорт → только список заявок (`OrganizerEntriesPage.tsx:247–255,293–301,327–330,338–340,447,528`), валидация/скачивание шаблона → `'none'`. |
| 9 | Убрать `Europe/Moscow` | **Выполнен** | `organizer-event-context.ts:40–44` — зона браузера; мок отдаёт `Intl…timeZone` (`handlers.ts:218`). |
| 10 | «Код вида» необязателен, текст подтверждения удаления | **Выполнен** | `DisciplineInput.code?` (`organizer.types.ts:25`), поле «Код вида (необязательно)» без `required` (`OrganizerProgramPage.tsx:476–481`); новый текст `:243`. |
| 11 | Формат времени `сс.сс`/`м:сс.сс`, `FORM: 'Форма'` | **Выполнен** | Регэксп `^(?:(\d+):)?(\d+)\.(\d{2})$` с проверкой секунд < 60 при наличии минут (`OrganizerEntriesPage.tsx:358–372`); `statusLabels` без `WEB/MANUAL`, с `FORM` (`organizer-event-context.ts:76–77`). |
| 12 | Описать ограничение публикации результатов в PR | **Выполнен** | Раздел «Ограничение публикации результатов» в описании PR; `resultsPublished` убран из типа/мока, колонка опирается на `status === 'PUBLISHED'` (`OrganizerResultsPage.tsx:86`). |
| 13 | Подсказка «Пересчитайте заплывы» после «Снять» | **Выполнен** | `OrganizerEntriesPage.tsx:32,102–112,332–337` — баннер `role="status"` со ссылкой на вкладку, только если по виду уже есть заплывы. |
| 14 | Ошибка в одном месте | **Выполнен** | `registerDialog()`/`dialogCount` (`OrganizerEventLayout.tsx:52–56,218–220`, `OrganizerDialog.tsx:16–26`): при открытом диалоге шапка ошибку не показывает. |
| 15 | Удалить `OrganizerRegistrationsPage.module.css`; `mockServiceWorker.js` вне `dist` | **Частично** | CSS удалён. `public/mockServiceWorker.js` по-прежнему в `public/` и попадает в `dist` (не критично). |

Пункты 16–17 первого ревью: скриншоты — см. замечание Б; конфликтов с T08 теперь нет: `git merge-tree … T04 T08` даёт 0 маркеров `<<<<<<<` (2 файла изменены в обеих ветках, сливаются автоматически); с `origin/main` слияние также чистое.

## Проверено дополнительно
- `tasks/`, `docs/*.md`, `sport-platform/src` (backend), `src/app/App.tsx` коммитом не тронуты; маршруты прежние, `…/registrations` остаётся редиректом на `…/entries` (`OrganizerRegistrationsPage.tsx`).
- MSW по-прежнему только при `VITE_API_MOCK=true` через динамический импорт (`main.tsx:9–12`).
- Файлы T08 (`features/results/*`) не изменены; `results.api.ts` используется только на чтение (`getCompetitionUnitDetails`).
- Мок стал ближе к T02: отбраковка по полу/возрасту/дубликатам в `generate()` (`handlers.ts:30–61`), нарушения адресуются `eventDisciplineId + heatNumber`; `disciplineErrors()` отклоняет служебные поля и требует `disciplineTemplateId` (`handlers.ts:593–610`), что защищает тесты от регрессии п. 2.
- `useEffectEvent` (`OrganizerProgramPage.tsx:1,387–398`) — стабильный API React 19.2 (в `package.json` `react ^19.2.8`), использование корректное (не в зависимостях эффекта).
- PATCH вида отправляет `disciplineTemplateId` — T03 `UpdateEventDisciplineUseCase` это поле игнорирует, вреда нет; `sessionId: null`/`heatDurationSec: null` в PATCH трактуются T03 как очистка — ожидаемое поведение формы.

## Новые замечания

### Блокирующее
**А. `organizer.api.ts:116–120`, `mocks/handlers.ts:285` — неверный путь шаблонов дисциплин.** Реальный эндпоинт в main и T03 — `GET /api/v1/discipline-templates` (`sports/web/SportsController.java:42`, без `sportId` в пути, без ограничений по роли), ответ `DisciplineTemplateResponse{id, sportId, code, name, competitionFormat, unitType, resultType, rankingStrategy, defaultMeta}`. Фронт зовёт `/api/v1/sports/{sportId}/discipline-templates` → 404 → `templates=[]`, `disciplineTemplateId=''`, кнопка «Сохранить вид» заблокирована навсегда, в шапке — ошибка. То есть на живом API создание вида не работает так же, как и до правки. Путь `/sports/{sportId}/…` был предложен в первом ревью ошибочно — ответственность ревьюера; Codex действовал по тексту правок. Исправление: вызывать `GET /api/v1/discipline-templates`, фильтровать по `t.sportId === event.sportId` (T03 `CreateEventDisciplineUseCase` отвергает шаблон другого вида спорта кодом `competition.sport_mismatch`), тип `DisciplineTemplate = {id, sportId, code, name}`, мок — на тот же путь с `sportId` в фикстуре.

### Желательные
**Б. Скриншоты в двух местах.** Старые 10 файлов остались в `docs/screenshots/T04/` (~2,5 МБ), новые 4 добавлены в `sport-platform/frontend/screenshots/T04/` (~1 МБ); описание PR ссылается на оба каталога. Оставить один каталог (лучше вообще не коммитить — приложить к PR) и удалить второй.

**В. `public/mockServiceWorker.js`** (п. 15) — всё ещё в `dist`. Можно оставить как известное, либо перенести генерацию воркера в dev-скрипт (`msw init` в `predev`) и добавить файл в `.gitignore`.

**Г. `OrganizerEntriesPage.tsx:44–46,54`** — T02 `HeatSeeder` отдаёт ещё код `ATHLETE_NOT_FOUND`; он не в списке, заявка покажется как «Не допущена» с общим текстом. Добавить `ATHLETE_NOT_FOUND: 'Спортсмен не найден в справочнике.'` и включить код в фильтр нарушений.

**Д. `OrganizerProgramPage.tsx:395–398`** — ошибка загрузки шаблонов прокидывается через `run(() => Promise.reject(cause), 'none')`: это переключает `busy` и общий `error` всего макета ради ошибки одного селекта. Достаточно локального сообщения под полем «Шаблон» (как было у пресетов), оставив `run` только для 401/403.

**Е. Допуск живёт только в памяти вкладки** (`generationResults` в состоянии макета): после перезагрузки страницы колонка показывает «—» до нового формирования. Для демо приемлемо; стоит упомянуть в PR и в `mocks/README.md`.

**Ж. `organizer-event-context.ts:103`** — для заплывов без `scheduledAt` окончание не считается, а длительность по умолчанию 180 с захардкожена; лучше вынести в константу с комментарием и показывать «—», если ни у одного заплыва сессии нет времени (сейчас так и выходит через `formatDate(null)` = «Не назначено», но текст рядом с «—» читается странно: «старт — Не назначено · 0 заплывов»).

## Текст правок для Codex
Внеси правки по повторному ревью PR #4: (1) шаблоны дисциплин грузи с реального эндпоинта `GET /api/v1/discipline-templates` (без `sportId` в пути; см. `sports/web/SportsController.java` в main) и фильтруй по `template.sportId === event.sportId`; тип `DisciplineTemplate` расширь до `{id, sportId, code, name}`; обнови мок (`handlers.ts`, `fixtures.ts`: путь `/discipline-templates`, у шаблонов поле `sportId` = `event.sportId`) и e2e-тест выбора шаблона; если после фильтра список пуст, покажи под селектом «Для вида спорта события нет шаблонов дисциплин». (2) Оставь скриншоты в одном каталоге: удали `docs/screenshots/T04/*` (или, наоборот, `sport-platform/frontend/screenshots/T04/*`) и поправь ссылки в описании PR. (3) В `OrganizerEntriesPage` добавь код `ATHLETE_NOT_FOUND` с подсказкой «Спортсмен не найден в справочнике.». (4) Ошибку загрузки шаблонов показывай локально под полем «Шаблон», через `run` прокидывай только 401/403. (5) В `mocks/README.md` и описании PR отметь, что допуск хранится в памяти до перезагрузки страницы. Сборку, lint и `npm run test:organizer` прогони заново.
