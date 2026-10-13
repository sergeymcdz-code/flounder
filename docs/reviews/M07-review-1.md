# M07 — ревью 1 (PR #40, e316d23)

PR #40 «feat(M07): задержки дня старта и сдвиг расписания», ветка `pr/40` (1 коммит e316d23) от `63263d1` (до M02/M09/QA01-followups). Задание: `tasks/M07_Задержки_дня_старта_и_сдвиг_расписания.md` + уточнения (V034; приоритет `main`/ADR M10 над §5.7; QA02-04 в обычный тест; e2e QA05 шаг 7; TSV). Ревьюер: Claude, 13.10.2026. Диф: 39 файлов, +745/−50; одна миграция V034; CI (backend, frontend) зелёный. Локально backend/frontend не собирались (нет Docker, `mvn -o` без локального репозитория, `npm ci` падает внутри самого npm) — принимаю по CI и описанию PR (1173 backend-теста, 70 Vitest, реальный `test:demo` шаг 7 = 0 ошибок, QA05-01/03 — прежние известные).

**Вердикт: слить после одной мелкой правки во фронте (≤ 10 минут) и ребейза на `origin/main` (два тривиальных текстовых конфликта); плюс два follow-up для #36 и #41.** Backend соответствует заданию и принятому уточнению (start не трогает `scheduled_at`, live отдаёт `delaySec`/`estimatedStartAt`), M10 сохранён дословно, `assertCanManage` и TSV на месте, миграция добавляющая, старые тесты не ослаблены, QA02-04 переехал в обычный прогон с теми же assertions. Единственное, что не хочу выпускать на публичный `/live` демо: подпись «Расчётное время старта» рисуется под **каждым** следующим заплывом даже при нулевой задержке, потому что backend всегда заполняет `estimatedStartAt = scheduledAt + 0`.

## Проверено

### 1. Что именно сделано со `/start` и согласуется ли это с §5.7

Реализован **вариант QA02-04 / M07 п.3**: `POST /start` пишет `actual_started_at = now()`, `actual_finished_at = null`, переводит в `IN_PROGRESS` и **не меняет `scheduled_at`** (`EventLiveUseCase.java:53-73`). Live считает `delaySec = max(0, actual − план)` текущего заплыва и подмешивает `estimatedStartAt = scheduledAt + delaySec` в `currentUnit` и `next` (`PublicPublicationService.java:109-130`). Старый абзац §5.7 (автопересчёт `scheduled_at`, отрицательное отставание, V027) заменён на новую формулировку (`docs/02_ТЗ_демо.md:237-239`), решение зафиксировано в ADR `docs/decisions/2026-10-13-start-delays.md`. Это соответствует заданию (п.3 прямо говорит «`scheduledAt` не менять») и перенесённому тесту `qa02_04_…` (`Qa02ResultsPublicIntegrationTest.java:24-39`, assertions идентичны удалённым из `Qa02KnownDefectsIT`). Противоречий с M10 нет. Принимаю.

### 2. Backend — соответствие заданию

| Пункт | Где | Результат |
|---|---|---|
| 1. V034: nullable `actual_started_at`/`actual_finished_at`, индекс | `V034__unit_actual_times.sql` | **Выполнено.** Индекс `(event_discipline_id, actual_started_at)` вместо `(event_id, …)` — у `competition_units` нет `event_id`, обосновано в PR и ADR. Расширен CHECK `chk_rule_execution_logs_kind` (drop/add — единственный способ, старые миграции не тронуты). `ddl-auto: validate` — колонки в `CompetitionUnitEntity.java:38-47` |
| 2. `/start` → `actual_started_at`; повтор → `results.unit_already_started` | `EventLiveUseCase.java:58-59`, `GlobalExceptionHandler.java:52` → 409 | **Выполнено.** Проверка стоит до общей `invalid_transition`, поэтому IN_PROGRESS даёт 409, FINISHED — по-прежнему 400 |
| 2. `/finish` → `actual_finished_at` только при первом переходе; M10-идемпотентность | `EventLiveUseCase.java:87-92`: время и `updated_at` ставятся только в ветке `IN_PROGRESS → FINISHED`; ветка FINISHED не трогает ничего, `nextScheduled` + `set` в обоих случаях — как в M10 | **Сохранено.** `/publication {true}` ставит `actual_finished_at`, если он ещё null (`PublishCompetitionUnitUseCase.java:106-108`); `{false}` его не чистит; новый `/start` после снятия — чистит. Тесты `finishPreservesFirstFinishTimeOnRetries`, `publicationSetsFinishTimeAndFinishKeepsIt` |
| 3. `delaySec`, `currentUnit.actualStartedAt`, `estimatedStartAt` | `PublicResponses.java:45-50` (`@JsonInclude ALWAYS`), `ReadModels.Unit` +2 поля, `PublicationReadRepository.java:70` | **Выполнено**, только добавленные поля. `delaySec` = 0 при отсутствии факта/плана/текущего (`earlyAndUnscheduledStartsHaveZeroDelayAndNullableEstimate`) |
| 4. `POST /events/{eventId}/schedule/shift {fromUnitId, deltaSec}`, `assertCanManage`, лог `SCHEDULE_SHIFT`, `ScheduleChangedEvent`, `recalculate` не менять | `ShiftScheduleUseCase.java:28-30` (`assertCanManage` → event lock → row lock всех заплывов события), `:78-80` (лог с input=request/output=response, событие в той же транзакции), `SchedulingController.java:90-103`; `RecalculateScheduleUseCase` в дифе отсутствует | **Выполнено.** Плюс `/shift/preview` — тот же use case без записи. Правило переполнения описано (следующая сессия того же локального дня площадки сдвигается на величину перекрытия, каскад до свободного промежутка/границы дня, другие дни/события не трогаются) и покрыто тестами |
| TSV QA04 | `qa04-endpoints.tsv:+106,+107` — `manage`, scoped=true, фикстура `unit-published`, тело `{"fromUnitId":"$unit","deltaSec":1200}` | Выполнено; политика `manage` и фикстура существуют в `Qa04RoleMatrixIntegrationTest:56,144`, 403 чужому организатору проверяется матрицей |

Коды ошибок: `scheduling.invalid_shift` → 400 по умолчанию (не в switch) — верно для валидационных отказов; сообщения на русском. `@Min/@Max` на `deltaSec` и ненулевость в use case.

### 3. Семантика сдвига (прочитано построчно)

- Порядок обхода — `data.disciplines(eventId)` (сессия → `orderInSession` → код) и `findByDisciplineId` (`sequence_number`), то есть ровно тот порядок, в котором `RecalculateScheduleUseCase` раздаёт время. Для суффикса сессии от `fromUnitId` и дальше: статус ∈ {DRAFT, SCHEDULED, PUBLISHED}, новое время ≥ конца предыдущего заплыва (для первого — ≥ `starts_at` сессии), конец заплыва в том же локальном дне площадки (`SchedulingData.timezone`, новый `VenueLookup.getTimezone`). Дорожки/статусы/`starts_at` не трогаются — проверено `previewAndShiftMatchKeepPriorUnitsAndLanesAndAuditParameters`.
- Каскад: `carry = max(0, previousEnd − next.startsAt)` (`:75-78`), отрицательный сдвиг следующие сессии не подтягивает, следующий день — `break`. Тесты `overflowOnlyCarriesOverlapToNextSessionOfVenueDay`, `overflowUsesVenueDayAcrossUtcMidnight` (Europe/Samara, 23:55 UTC), `shiftWithoutOverflowLeavesNextSessionAlone`.
- Атомарность: все проверки до `logs.begin`, любое исключение откатывает транзакцию; `invalidShiftRollsBackAndDoesNotEmitEvent` проверяет 5 отказов + отсутствие лога/события.

### 4. Блокировки (изменение вне области задания, но обоснованное)

`EventLockService.lockForUpdate` переведён с `FOR UPDATE` на `FOR NO KEY UPDATE` (`events/EventLockService.java:21`) — это модуль `events`, затрагивает все use case генерации/публикации/recalculate/withdrawal-listener. Причина верная: `SwimResultsUseCase`/`PublishCompetitionUnitUseCase` держат `lockById(unit)` и вставляют `rule_execution_logs(event_id FK)`; с `FOR UPDATE` на событии shift (event → units) и ввод результата (unit → KEY SHARE на event) давали бы цикл ожидания. `NO KEY UPDATE` по-прежнему взаимоисключающий для всех наших писателей (все берут тот же `lockForUpdate`), пропускает только FK-вставки. Порядок везде event → unit → live (`EventLiveUseCase.managedUnit`, `set` тоже берёт event lock — `PUT /live` теперь сериализуется с генерацией, это плюс). Тест на совместимость с двумя соединениями `eventMutexAllowsAuditForeignKeyInsertButSerializesOtherMutations` есть. Принимаю; в PR это сказано.

### 5. Публичные страницы, кэш

- `/live` и `/schedule` остаются `@Cacheable` с TTL 10 с, код кэша не менялся. `scheduleShiftedAt` берётся коррелированным подзапросом `max(executed_at) … kind='SCHEDULE_SHIFT'` прямо в `UNIT_QUERY` (`PublicationReadRepository.java:21-22`); индекс `idx_rule_execution_logs_event_id` есть, лишних запросов нет. Поле лежит в каждой строке массива `/schedule` (форма массива сохранена, `null` при отсутствии сдвига).
- Frontend: плашка задержки только при `delaySec > 0` (`LivePage.tsx:72-76`), расчётное время у следующих — `estimatedStartAt ?? scheduledAt` в поясе площадки (`:136`); `/schedule` — «Расписание сдвинуто в HH:MM» (`SchedulePage.tsx:23-25`); организатор — «Отставание: +N мин» из публичного live по polling'у и модалка с серверным preview, debounce 300 мс, stale-preview блокирует кнопку (`ScheduleShiftForm.tsx`, 2 Vitest-кейса). Тексты русские, состояния загрузки/ошибки есть.

### 6. Тесты

- `M07StartDelaysIntegrationTest` — 13 кейсов, покрывают все 6 пунктов задания + preview + аудит + событие + каскад + чужое событие + блокировки.
- QA02-04 перенесён в обычный прогон без изменений assertions; `Qa02KnownDefectsIT` оставлен только с QA02-06 (regulationUrl), импорты почищены.
- Старые тесты: в дифе `src/test` удалён только перенесённый метод; `qa05-demo.spec.ts` не менялся (шаг 7 — по описанию PR зелёный на финальном jar).
- Vitest: `LivePage.test.tsx` (+1), `Timezone.test.tsx` (+1), `ScheduleShiftForm.test.tsx` (2).

### 7. Modulith, безопасность

Новые зависимости только через корневые интерфейсы: `scheduling → results.{CompetitionUnitStore, RuleExecutionLog}` (уже использовались), `scheduling → organizations.VenueLookup` (новый метод), `results → events.EventLockService` (корень модуля). SQL — только с параметрами; `lockForEvent` — `for update of u`. Секретов нет.

### 8. Конфликты

`git merge-tree --write-tree origin/main pr/40` — **2 текстовых конфликта**, оба тривиальные (соседние добавления):
- `OrganizerHeatsPage.tsx` — #39 добавил disabled-чекбокс «Круговой посев» в начало карточки, #40 — абзац «Отставание» в то же место. Оставить оба.
- `qa04-endpoints.tsv` — #29 (M02) добавил 6 строк auth в конец, #40 — 2 строки shift. Оставить все 8; `doesNotHaveDuplicates` не пострадает.
Семантически с `main` (M10, QA02 r2 `nextScheduled`, #39 `publishedWithdrawalViolations`) пересечений нет: `SchedulingData` слился автоматически, `nextScheduled` только вызывается.

Другие открытые PR:
- **#36 (M08)** — текстовый конфликт только в TSV. **Семантический:** `AnalyticsIntegrationTest.java:137` делает `alter table competition_units add column actual_finished_at timestamptz, add column results_published_at timestamptz` — после V034 первая колонка уже есть, тест упадёт на «column already exists». Чтение `to_jsonb(u)->>'actual_finished_at'` в `AnalyticsReadRepository:142` с V034 начнёт отдавать реальные данные — это ожидаемо. Правка в #36: `add column if not exists`.
- **#41 (M05)** — конфликт в `PublishCompetitionUnitUseCase.java:106-108` (оба меняют одни строки: #40 ставит `actual_finished_at`, #41 публикует `ResultsPublishedEvent`) — оставить оба; и TSV. Слушателя `ScheduleChangedEvent` в #41 нет — M05 должен добавить подписку отдельно. **Flyway:** #41 несёт V032, #40 — V034; на стенде, где V034 уже применена, последующий деплой с V032 упадёт на validate («resolved migration not applied»), т.к. `outOfOrder` не включён — см. порядок слияния.
- **#42 (QA07)** — конфликтов нет; `qa07-judge.spec.ts:35` нажимает «Старт» один раз — семантика сохранена.

## Блокирующие

1. **`LivePage.tsx:139` + backend `PublicPublicationService.java:130`** — `estimatedStartAt` заполняется всегда (`scheduledAt + 0`), и подпись `<small>Расчётное время старта</small>` показывается под каждым следующим заплывом даже без задержки; на публичном `/live` демо каждый зритель увидит «расчётное» вместо планового времени. Задание: «расчётное время у следующих заплывов» — как следствие задержки. Исправить во фронте: показывать подпись (и подставлять `estimatedStartAt`) только при `(live.delaySec ?? 0) > 0`; обновить `LivePage.test.tsx` (кейс с `delaySec: 0` → подписи нет). Backend можно оставить (поле стабильно non-null при наличии плана — это удобнее клиентам), но тогда в §5.7 одной фразой сказать, что при `delaySec = 0` `estimatedStartAt == scheduledAt`.

## Желательные (не держат слияние)

1. `OrganizerHeatsPage.tsx:107` — при `delaySec = 0` индикатор пишет «Отставание: +0 мин», при 1–59 с — «+1 мин» (`Math.ceil`). Лучше «Идём по расписанию» при 0 и округление вниз/формат «+N мин SS с» — на усмотрение.
2. `ShiftScheduleUseCase.java:75-78` — перекрытие считается относительно `starts_at` следующей сессии, а не фактического плана её первого заплыва. Если следующая сессия уже отодвинута прошлым каскадом (зазор между `starts_at` и первым заплывом), новый сдвиг снова прибавит всю величину перекрытия к `starts_at` и «перетолкнёт» сессию лишний раз. Считать `carry` от `min(scheduledAt)` заплывов следующей сессии (или `max(starts_at, firstUnit)`).
3. `ShiftScheduleUseCase.java:30` — preview тоже берёт `FOR UPDATE` на все заплывы события (организатор печатает в модалке → каждые 300 мс эксклюзивные блокировки, конкурирующие с `lockById` судьи и FK-вставками результатов). Для `preview=true` достаточно event lock (или `FOR SHARE`).
4. `ShiftScheduleUseCase.java:86-87` — CANCELLED-заплыв в хвосте сессии блокирует весь сдвиг. Сейчас статус никто не ставит (grep по `pr/40` — только enum), но для MVP логичнее CANCELLED пропускать, а IN_PROGRESS/FINISHED — отклонять.
5. `EventLiveUseCase.java:56, :84` — `access.assertCanManage` вызывается повторно сразу после `managedUnit`, который уже его сделал; убрать дубль.
6. `ShiftScheduleResponse.shiftedAt` (`OffsetDateTime.now()` в use case) и `scheduleShiftedAt` в `/schedule` (`executed_at` default now() в БД) — два разных момента в одной транзакции, разница в миллисекунды. Передавать `shiftedAt` в лог явно или брать его из лога.
7. Документы: `docs/02_ТЗ_демо.md:239` описывает `/shift` целиком в §5.7 — ок, но §4.7 («Расписание») не упоминает ручной сдвиг; одна ссылка на ADR там не помешает. `docs/demo-script.md` без изменений — в сценарии демо сдвиг не показывается; если захотим показать «+20 мин» на репетиции 16.10, добавить шаг.

## Текст правок для Codex

Внеси правки по ревью M07 (PR #40):

1. Ребейз на актуальный `origin/main` (#29, #39, #43). Конфликты: `OrganizerHeatsPage.tsx` — оставить и чекбокс «Круговой посев» из #39, и абзац «Отставание» из #40; `qa04-endpoints.tsv` — оставить все строки (6 auth из M02 + 2 shift). Прогнать `Qa04RoleMatrixIntegrationTest` и `npm test`.
2. `sport-platform/frontend/src/features/public/LivePage.tsx`: в списке следующих заплывов подставлять `estimatedStartAt` и показывать подпись «Расчётное время старта» только когда `(live.delaySec ?? 0) > 0`; при нулевой задержке — обычное `scheduledAt` без подписи. В `LivePage.test.tsx` добавить кейс `delaySec: 0` (подписи нет, время = `scheduledAt`). В `docs/02_ТЗ_демо.md` §5.7 одной фразой: «при `delaySec = 0` `estimatedStartAt` равен `scheduledAt`».
3. (по желанию, в этом же PR) `OrganizerHeatsPage.tsx`: при `delaySec = 0` выводить «Идём по расписанию» вместо «Отставание: +0 мин».
4. (по желанию) `ShiftScheduleUseCase`: для `preview=true` не вызывать `data.units.lockForEvent`; перекрытие со следующей сессией считать от фактического времени её первого заплыва, а не от `starts_at`; убрать повторный `assertCanManage` в `EventLiveUseCase.start/finish`.

Backend-контракты, миграцию V034, тесты M07/QA02, TSV, ADR менять не нужно.

Follow-up в другие PR (не в #40):
- **#36 (M08):** `AnalyticsIntegrationTest` — `alter table competition_units add column if not exists actual_finished_at …, add column if not exists results_published_at …`.
- **#41 (M05):** после слияния #40 добавить подписчика `ScheduleChangedEvent` (письмо/уведомление об изменении расписания) — по заданию M07 это зона M05.

## Порядок слияния и риски для стенда

1. **Сливать #40 после #41 (M05, V032)** или перед первым деплоем #40 на стенд убедиться, что V032 уже применена. Иначе: на БД с V034 последующий `migrate` с V032 упадёт на validate (`outOfOrder` не включён в `application*.yml`). Альтернатива, если порядок менять неудобно: один раз включить `spring.flyway.out-of-order: true` в `application-prod.yml` (решение за руководителем; это снимет проблему и для V031/V033, если они зарезервированы).
2. #36 (M08) — после #40 обязательно поправить `AnalyticsIntegrationTest` (см. выше), иначе красный CI в #36 после ребейза.
3. Миграция V034 — добавляющая, откат кода безопасен (старый код не читает новые колонки; старый CHECK не восстанавливается, но `SCHEDULE_SHIFT`-строки ему и не нужны при чтении). Удалять колонки при откате не нужно.
4. Смена `FOR UPDATE → FOR NO KEY UPDATE` на `events` затрагивает все мутации события: функционально то же (писатели по-прежнему сериализуются), но параллельная регистрация участника (FK на `events`) больше не ждёт генерацию заплывов — это улучшение, не регрессия. Следить за deadlock'ами в логах PG первые дни.
5. Организаторская страница «Заплывы» теперь polling'ует публичный `/live` каждые `LIVE_POLL_MS` для опубликованных событий — нагрузка из Caffeine, несущественно.
6. Публичный `/schedule` отдаёт `scheduleShiftedAt` в каждой строке — для клиентов это только новое nullable-поле.

## Что проверить на стенде после деплоя руками (6 шагов)

1. В БД сдвинуть `scheduled_at` первого заплыва демо-события на −10 мин от текущего; под `sec@` нажать «Старт». В БД: `actual_started_at` ≈ now, `scheduled_at` не изменился (и у следующих тоже). На телефоне `/e/{slug}/live`: плашка «Соревнование идёт с задержкой 10 мин…», у следующих заплывов время = план + 10 мин с подписью «Расчётное время старта»; `/e/{slug}/schedule` — время плановое, без пометки. Повторный `POST /start` через curl → 409 `results.unit_already_started`.
2. Ввести результаты → «Опубликовать результаты»: статус FINISHED, `actual_finished_at` заполнен, live остался на этом заплыве, публичные результаты видны. «Финиш» дважды: live ушёл на следующий, `actual_finished_at` и `updated_at` не изменились (QA05 шаг 7).
3. Старт следующего заплыва «вовремя» (план = now): плашки на `/live` нет, у следующих заплывов — плановое время (после правки п.1 — без подписи «расчётное»).
4. Кабинет организатора → «Заплывы»: индикатор отставания соответствует `/live`; «Сдвинуть расписание…» → выбрать ещё не начатый заплыв, +20 мин → предпросмотр первого/последнего времени в поясе площадки; до подтверждения БД не изменилась. Подтвердить: заплывы с выбранного сдвинуты на 20 мин, дорожки/статусы те же, предыдущие не тронуты; в `rule_execution_logs` строка `SCHEDULE_SHIFT` с `input_json = {fromUnitId, deltaSec}`; через ≤ 10 с на `/schedule` — «Расписание сдвинуто в HH:MM».
5. Отрицательный сдвиг через предыдущий заплыв, сдвиг от начатого заплыва, сдвиг за полночь площадки → 400 с русским текстом в модалке, БД не меняется. Под чужим организатором и судьёй `POST /schedule/shift` и `/preview` → 403.
6. Событие с двумя сессиями в один день с близкими `starts_at`: сдвиг первой на величину больше зазора → вторая сессия сдвинута ровно на перекрытие, следующий день не тронут.
