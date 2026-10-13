# M07 — ревью 2 (PR #40, ac613b6)

PR #40 «feat(M07): задержки дня старта и сдвиг расписания», ветка `pr/40`, после правок по ревью 1 (`e316d23`): 2 коммита поверх актуального `origin/main` (`028dc5e`) — перебазированный `8e4377b` (исходный M07) и `ac613b6` «fix(publication): show planned starts when there is no delay» (4 файла, +31/−5). Ревьюер: Claude, 13.10.2026. Диф раунда 2 относительно исходного содержимого PR: `git range-diff 63263d1..e316d23 origin/main..8e4377b` — только контекстные строки (без изменений содержимого), плюс `ac613b6`. Полный диф PR против main: 39 файлов, +771/−50, одна миграция V034. CI на `ac613b6`: backend / frontend / infra — зелёный; `mergeable_state=clean`. Локально тесты не прогонялись (нет Docker, npm registry и `~/.m2` недоступны) — принимаю по CI и описанию PR (1253 backend-теста / 50 классов, `Qa04RoleMatrixIntegrationTest` 785 проверок, 99 Vitest, build/lint PASS).

**Вердикт: слить.** Оба обязательных пункта выполнены точно по тексту правок, опциональный п. 3 сделан, п. 4 (backend) осознанно не сделан — это «по желанию», backend-контракты, V034, тесты и TSV не тронуты, как и требовалось. Ребейз чистый: в `OrganizerHeatsPage.tsx` сохранены и чекбокс «Круговой посев» (#39), и индикатор отставания (#40); в `qa04-endpoints.tsv` на месте 6 auth-строк M02 и 2 shift-строки M07 без дублей. Описание PR дополнено разделом «Правки по ревью 1».

## Проверено

| № | Пункт правок | Где | Результат |
|---|---|---|---|
| 1 | Ребейз на актуальный `origin/main` (#29, #39, #43) | `git merge-base --is-ancestor origin/main pr/40` → да; `git merge-tree --write-tree origin/main pr/40` → чистое дерево `1b1c716`, конфликтов нет; `range-diff` исходного коммита и перебазированного — только контекст | **Выполнено** |
| 1а | `OrganizerHeatsPage.tsx`: и чекбокс «Круговой посев», и абзац «Отставание» | `OrganizerHeatsPage.tsx:109` «Круговой посев трёх сильнейших заплывов», `:112` «Круговой посев выключен…» (из #39); `:114-124` индикатор отставания с `live.data.delaySec`, `:38` polling `usePublicData(loadLive, LIVE_POLL_MS)`, `:362` `<ScheduleShiftForm …>` (из #40) | **Выполнено** |
| 1б | `qa04-endpoints.tsv`: 6 auth (M02) + 2 shift | `qa04-endpoints.tsv:106-111` — change-password, password-reset/request, password-reset/confirm, refresh, logout, logout-all; `:112-113` — `schedule/shift`, `schedule/shift/preview` (`manage`, scoped, `unit-published`). `git diff origin/main pr/40 -- …tsv` — ровно +2 строки, 113 строк против 111 в main | **Выполнено** |
| 1в | Прогнать `Qa04RoleMatrixIntegrationTest` и `npm test` | По описанию PR: 785 проверок матрицы, 99 Vitest; CI backend/frontend зелёный | Принято по CI |
| 2 | `LivePage.tsx`: `estimatedStartAt` и подпись только при `delaySec > 0`; при 0 — `scheduledAt` без подписи | `LivePage.tsx:70` `const delayed = (live.delaySec ?? 0) > 0`; `:72` плашка по `delayed`; `:137-140` `time(delayed ? unit.estimatedStartAt ?? unit.scheduledAt : unit.scheduledAt, timeZone)`; `:143` `{delayed && unit.estimatedStartAt && <small>Расчётное время старта</small>}` | **Выполнено** |
| 2а | `LivePage.test.tsx`: кейс `delaySec: 0` | `LivePage.test.tsx:66-76` `it.each([0, undefined])`: фикстура `scheduledAt 06:00Z` / `estimatedStartAt 06:12Z` (Europe/Samara → 10:00 / 10:12); ожидает «10:00 · Заплыв», отсутствие «10:12», отсутствие подписи и `role="status"`. Прежний кейс с задержкой (`:63`) сохранён | **Выполнено**, даже шире (ещё и `undefined`); оценка в фикстуре намеренно отличается от плана — выбор источника времени реально проверяется |
| 2б | §5.7 ТЗ: «при `delaySec = 0` `estimatedStartAt` равен `scheduledAt`» | `docs/02_ТЗ_демо.md:238` — фраза добавлена дословно в абзац про `/live` | **Выполнено** |
| 3 (опц.) | `OrganizerHeatsPage.tsx`: «Идём по расписанию» при `delaySec = 0` | `OrganizerHeatsPage.tsx:115-118`: `(live.data.delaySec ?? 0) > 0 ? \`Отставание: +N мин\` : 'Идём по расписанию'`; остальные ветки (нет публикации / ошибка / загрузка) сохранены | **Выполнено** |
| 4 (опц.) | `ShiftScheduleUseCase`: без `lockForEvent` для preview; перекрытие от первого заплыва следующей сессии; убрать дубль `assertCanManage` в `EventLiveUseCase` | `git diff 8e4377b ac613b6 -- sport-platform/src/main/java` — пусто; `ShiftScheduleUseCase.java:30, :75-78` и `EventLiveUseCase.java:56, :84` как в ревью 1 | **Не сделано** (допустимо — пункт был «по желанию»; в описании PR прямо сказано «Backend-контракты, V034, ADR и тесты M07/QA02 сохранены») |
| — | Миграция V034 не изменена относительно e316d23 | `git diff e316d23..pr/40 -- '*V034*'` — пусто | Подтверждено |
| — | Миграции только добавлены, чужие тесты не удалены, файлы вне области задания | Раунд 2 трогает 4 файла (ТЗ §5.7, `OrganizerHeatsPage.tsx`, `LivePage.tsx`, `LivePage.test.tsx`); полный диф PR тот же набор 39 файлов, что в ревью 1; `Qa02KnownDefectsIT` −22 / `Qa02ResultsPublicIntegrationTest` +23 — перенос QA02-04 в обычный прогон (принято в ревью 1); `git diff --check` чист | Ок |
| — | Тексты UI на русском | «Идём по расписанию», «Отставание: +N мин», «Расчётное время старта», «Соревнование идёт с задержкой…» | Ок |
| — | Описание PR обновлено | Раздел «Правки по ревью 1» + результаты прогонов после ребейза + порядок слияния M04→M05→M06→M07 | Ок |

## Блокирующие

Нет.

## Желательные (не держат слияние; переносятся из ревью 1 как follow-up)

1. `ShiftScheduleUseCase.java:30` — `preview=true` по-прежнему берёт `FOR UPDATE` на все заплывы события при каждом пересчёте модалки (debounce 300 мс). Для preview достаточно блокировки события или `FOR SHARE`.
2. `ShiftScheduleUseCase.java:75-78` — перекрытие со следующей сессией считается от её `starts_at`, а не от фактического `min(scheduledAt)` её заплывов; при повторном каскаде следующая сессия может быть сдвинута лишний раз.
3. `EventLiveUseCase.java:56, :84` — повторный `access.assertCanManage` после `managedUnit`, который его уже сделал.
4. `ShiftScheduleResponse.shiftedAt` (`now()` в use case) и `scheduleShiftedAt` в `/schedule` (`executed_at` из БД) — два разных момента в одной транзакции.
5. Follow-up в другие PR (из ревью 1, остаются в силе): **#36 (M08)** — `AnalyticsIntegrationTest` добавить `alter table competition_units add column if not exists actual_finished_at …, results_published_at …`; **#41 (M05)** — после слияния #40 подписчик `ScheduleChangedEvent`.

## Текст правок для Codex

(пусто — слить)

## Порядок слияния

- Flyway: V034 — последняя в цепочке **#38 M04 (V031) → #41 M05 (V032) → #46 M06 (V033) → #40 M07 (V034)**; `outOfOrder` не включён, поэтому #40 сливать и деплоить после трёх предыдущих либо один раз включить `spring.flyway.out-of-order: true` (решение из ревью 1, ADR `docs/decisions/2026-10-13-flyway-order.md` в main).
- После слияния #40 обязательно ребейз #36 (M08) с правкой `AnalyticsIntegrationTest` (иначе красный CI) и #46 (M06, `lockWritableForUpdate` → `for no key update`, см. M06-review-1).
- Конфликтов с `origin/main` нет; с #39/#29/#43 уже слито в ветке.
