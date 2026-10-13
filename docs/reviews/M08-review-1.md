# M08 — ревью 1 (PR #36, cdadd16)

Ветка `pr/36` (cdadd16, «feat(analytics): M08 — панель аналитики события и организации») → `origin/main` (63263d1). Ревьюер: Claude, 13.10.2026. Диф: 16 файлов, +1010/−2. CI на cdadd16: backend ✅, frontend ✅. `mergeable_state: clean`.

**Вердикт:** слить после правок (одна небольшая обязательная правка в экспорте + уточнение документации; остальное — follow-up после M07)

Задание закрыто по всем пунктам backend/frontend/тесты: новый read-only модуль `analytics` (native SQL через `EntityManager`, без миграций, без зависимостей на чужие репозитории), 4 эндпоинта, кеш Caffeine 60 с по eventId с проверкой `assertCanManage` **до** кеша, XLSX на POI (12/3 листа), вкладка «Аналитика» и страницы организации, пустые состояния, 9 интеграционных сценариев на наборе M (200 заявок), которые сверяют суммы с прямыми `count(*)`, монотонность воронки, p95 на синтетических данных, 401/403, обе книги POI. Все агрегаты считаются в БД (`count(*) filter`, `percentile_cont`, `row_number`, `generate_series`), в Java только маппинг кортежей — списки заявок в память не поднимаются. Modulith-границы соблюдены: из чужих модулей используются только `events.EventAccessService`, `organizations.OrganizationLookup`, `identity.AuthenticatedUserPrincipal`. Графики — собственный SVG без новой зависимости (+16,5 kB JS суммарно, lazy-чанк 14,9 kB), лицензионных вопросов нет.

Что **не** удалось проверить локально: Docker отсутствует → `mvn test` с Testcontainers не прогнан, `node_modules` нет → `npm test/lint/build` не прогнаны; опираюсь на зелёный CI и описание PR (1182 backend-теста, 73 frontend). Код прочитан полностью, SQL сверен со схемой V002–V028 и с V034 из pr/40.

## Проверено

| Пункт | Способ | Результат |
|---|---|---|
| Merge с main | `git merge-tree --write-tree origin/main pr/36` | чистый (0ef058d), ветка от текущего HEAD main |
| Соответствие заданию M08 | построчно по `tasks/M08_Аналитика_события.md` | все пункты 1–4 backend, фронт, тесты — есть; `delaySec=null` и задержка публикации `null`/`UNAVAILABLE_M07` допустимы по уточнению (M07 не слит) |
| «Опубликовано заплывов» после M10 | `AnalyticsReadRepository.results`: `u.status in ('FINISHED','COMPLETED') and d.status='PUBLISHED'` | совпадает с семантикой M10/`PublicationReadRepository` (FINISHED = результаты опубликованы; PUBLISHED = только стартовый протокол) |
| Схема: все колонки native SQL | сверка с миграциями | `registrations.payment_status/submitted_at` (V024/V011), `venues.timezone` (V006), `event_sessions.break_between_events_sec` (V021), `event_disciplines.age_groups_json/heat_duration_sec/distance_m` (V022), `competition_units.heat_duration_sec/status` (V025), `competition_unit_entries.entry_status` (V025), `rule_execution_logs.kind/violations_json/executed_at` (V025), `payments.status/amount/currency` (V013), `organization_members.member_role` (V006) — всё существует; JSON-ключи возрастных групп совпадают с `competition.AgeGroup` |
| Таблица длительностей заплывов | сравнение с `SchedulingRules.defaults()` | идентична (25→60 … 1500→1500, default 180, пауза `break_between_events_sec`) |
| Нагрузка на 5000 заявок | анализ планов по индексам | CTE `REGISTRATIONS` выполняется 4 раза (totals/age/club/gender), каждый — index scan `idx_registrations_event_id` + join `profiles` по PK + коррелированный `exists` по `idx_cue_registration`; `corrected` — `idx_registrations_event_id → idx_cue_registration → idx_results_entry`; `by day` — один group by + `generate_series`. N+1 нет, всего 10 запросов на событие и 3 на организацию. На 5000 заявок ожидаемо десятки мс (на наборе M 25–31 мс по PR). Тяжёлых запросов без индексов нет |
| Пагинация/агрегация | код | всё в SQL; «топ-20 + Прочие» через `row_number()`; в Java только `.stream().map()` по агрегированным строкам |
| Права: чужое событие / организация | `AnalyticsService` + `GlobalExceptionHandler` | событие: `assertCanManage` → несуществующее 404 (`events.event_not_found`), чужое 403 (`events.access_denied`); организация: `existsById` → 404 (`organizations.organization_not_found`), не owner/admin → 403 (`organizations.access_denied`); `@PreAuthorize hasAnyRole('ORGANIZER','PLATFORM_ADMIN')` на контроллере; экспорт идёт через тот же `analytics.event/organization` — проверка та же |
| Кеш | `AnalyticsService.event` | `assertCanManage` **перед** `events.get(id, reads::event)`; кеш общий для JSON и XLSX; тест `cacheIsSharedWithExportButEveryReadRechecksAccess` покрывает отзыв членства |
| TSV | `qa04-endpoints.tsv` | 4 строки добавлены (`organize`/`owner`, scoped, 200); политика `organize` = organizer/foreign_organizer/platform_admin, `owner` = только platform_admin — соответствует реализации |
| Старые тесты | `git diff --stat` | ни один существующий тест не изменён/удалён; изменения вне модуля — только App.tsx (+11), OrganizerEventLayout (+1), OrganizerLayout (+2), TSV (+4) |
| Modulith | импорты `analytics.*` | нет импортов `*.infrastructure`/репозиториев чужих модулей; чужие таблицы читаются только native SQL (разрешено заданием) |
| Фронт: русские тексты, пустые состояния | `AnalyticsPages.tsx` | «Заявок пока нет», «Валидатор ещё не запускался», «Подтверждённых оплат пока нет», «Событий пока нет», «Организаций пока нет»; 401 → logout+`/login`, 403 → «Нет доступа к этой аналитике.»; тест `AnalyticsPages.test.tsx` (7 сценариев) |
| Фронт: графики | `DailyChart` | SVG `polyline`, `role="img"` + `aria-label`, раскрываемая таблица точных значений; воронка — CSS-бары; CSS-токены (`--color-surface/border/primary/...`) существуют в теме D01 |
| Конфликты с другими PR | `git merge-tree pr/36 pr/NN` | см. раздел «Порядок слияния» |

## Блокирующие замечания

1. **`AnalyticsExport.bytes`: `sheet.autoSizeColumn(col)` требует AWT-шрифтов.** Это первое использование `autoSizeColumn` в проекте; образ `eclipse-temurin:21-jre` (Dockerfile) не содержит шрифтов, и POI в headless-окружении без fontconfig/шрифтов падает в `SheetUtil.getCellWidth` (`java.lang.Error: Probable fatal error: No fonts found` / `NullPointerException`) → `GET …/export.xlsx` на стенде вернёт 500, хотя в CI (образ с JDK) всё зелёное. Скачивание XLSX — шаг демо. Правка 5 минут: заменить на фиксированные ширины (`setColumnWidth(col, 256*N)`) без автоподбора — или доказать на стенде, что экспорт открывается из Docker-образа. (Заодно это уберёт самую медленную операцию POI на листе «Заявки по дням».)

2. **Документация PR/`docs/analytics/M08-analytics.md` расходится с реальным M07 (pr/40).** Описание обещает чтение будущих колонок `actual_finished_at` / `results_published_at` через `to_jsonb(u)`. В `V034__unit_actual_times.sql` (pr/40) есть только `actual_started_at` и `actual_finished_at`; колонки `results_published_at` нет и не будет — `actual_finished_at` в M07 проставляется ровно в момент перехода в FINISHED (`EventLiveUseCase.finish`, `PublishCompetitionUnitUseCase`), т.е. «финиш» и «публикация» в модели данных — одна отметка. После слияния M07 метрика так и останется `null`, а ветка `samples` — мёртвым кодом; при этом PR и `docs/analytics` подают её как «готово, ждём M07». Нужно либо (а) убрать спекулятивное чтение `results_published_at` и честно написать «источник определяется в follow-up после M07», либо (б) сразу зафиксировать реализуемый источник (см. желательное 1). Минимум — поправить текст документов и `latencySource`, чтобы владелец не ждал автоматического «включения» метрики после M07.

## Желательные замечания

1. **Источник «финиш → публикация» после M07 (ТЗ 03 §1, критерий 2 — p95 ≤ 7 мин).** Реализуемое определение на текущей схеме + V034: `t_finish = competition_units.actual_finished_at`; `t_publish = min(rule_execution_logs.executed_at)` для `kind='RANKING'` того же `event_discipline_id` с `executed_at >= actual_finished_at` (публичные результаты появляются только когда `results.status <> 'PENDING'`, т.е. после пересчёта), fallback — `max(results.updated_at)` по дорожкам заплыва. Отрицательные/отсутствующие интервалы исключать, как сейчас. Это follow-up-PR «M08-2» после слияния pr/40; там же `delaySec` сессии = `max(actual_started_at − scheduled_at)` по заплывам сессии (0 если раньше срока) — контракт `delaySec` из M07 §5.7.

2. **Проверка owner/admin организации дублирует `organizations` native-SQL'ом** (`AnalyticsReadRepository.organizationOwnerOrAdmin`), хотя в `organizations.OrganizationManagementAccess` уже есть `canManageOrganization` (owner/admin/organizer) и `isMember`. Лучше добавить в интерфейс `boolean isOwnerOrAdmin(UUID organizationId, UUID userId)` и вызывать его из `AnalyticsService` — тогда правило «кто считается владельцем» живёт в одном модуле.

3. **Список «Аналитика организаций»** показывает все организации, где пользователь состоит (`GET /api/v1/organizations`), включая те, где его роль `organizer`/`member`, — клик даёт 403 «Нет доступа». Либо фильтровать по `membershipRole in (owner, admin)` на фронте (если роль есть в ответе), либо подписать «доступна владельцам и администраторам» рядом с каждой карточкой и не рендерить ссылку.

4. **Невалидный `venues.timezone` → 500.** `at time zone e.tz` в SQL и `Intl.DateTimeFormat({timeZone})` на фронте упадут на значении вроде «Москва» (`CreateVenueUseCase` не валидирует IANA-имя). Не проблема M08 (та же дыра у публичных страниц T11), но стоит завести задачу: валидация `ZoneId.of(timezone)` при создании/правке площадки.

5. **График по дням**: только первая и последняя дата на оси X, нет промежуточных делений и подсказок по точкам. Для демо приемлемо (есть таблица значений), но если останется время — подписи каждые N дней и `<title>` на точках.

6. «Подано» = все статусы кроме DRAFT, включая REJECTED/WITHDRAWN — корректно как текущий срез и описано в docs, но в UI стоит добавить одну фразу в подпись под воронкой («включая отклонённые и снятые»), иначе организатор удивится, что «подано» > «подтверждено + на рассмотрении».

7. `AnalyticsIntegrationTest` лежит в пакете `security` (ради наследования `Qa04SecurityFixture`). Можно оставить, но тогда в `docs/analytics` стоит указать, где искать тесты модуля.

8. В описании PR сказано, что `pom.xml` не менялся — верно; но фраза задания «конфликты только pom/App.tsx» не выполняется: фактические конфликты — `qa04-endpoints.tsv` с четырьмя открытыми PR и `OrganizerEventLayout.tsx` с pr/41 (см. ниже). Все тривиальные.

## Текст правок для Codex

Внеси правки по ревью M08:

1. `AnalyticsExport.bytes`: убрать `s.autoSizeColumn(col)` (требует AWT-шрифтов, которых нет в `eclipse-temurin:21-jre`; на стенде экспорт даст 500). Задать ширины явно: первая колонка `s.setColumnWidth(0, 256*36)`, остальные `256*18`; для листов «Виды»/«Сессии» колонка «ID» — `256*40`. Автофильтр оставить. В `AnalyticsIntegrationTest.bothExportsOpenInPoiAndContainTheSameAggregatesAndUnavailableCells` ничего менять не нужно.

2. `AnalyticsReadRepository.results`: убрать чтение `to_jsonb(u)->>'results_published_at'` и CTE `samples` (колонки `results_published_at` в M07/V034 нет и не планируется). Оставить `publishedHeats` и `corrected`; `medianPublicationSec`/`p95PublicationSec` возвращать `null`, `latencySamples=0`, `latencySource="UNAVAILABLE_UNTIL_M07"`. Тест `optionalImmutableTimestampsGiveInterpolatedMedianAndP95WithoutUsingUpdatedAt` удалить вместе с test-only DDL (его заменит тест в follow-up M08-2). В `docs/analytics/M08-analytics.md` и описании PR переписать строки «Задержка публикации» и «Медиана/p95 при наличии источника»: источник будет определён в M08-2 после слияния M07 как `actual_finished_at → min(rule_execution_logs.executed_at, kind='RANKING', executed_at >= actual_finished_at)` по дисциплине (fallback `max(results.updated_at)` по дорожкам заплыва); `delaySec` сессии — `max(actual_started_at − scheduled_at)`.

3. `organizations.OrganizationManagementAccess`: добавить `boolean isOwnerOrAdmin(UUID organizationId, UUID userId)`; реализовать в `OrganizationService` рядом с `canManageOrganization` (`Set.of("owner","admin")`, `toLowerCase(Locale.ROOT)`). В `AnalyticsService.organization` использовать его вместо `reads.organizationOwnerOrAdmin`; метод и SQL из `AnalyticsReadRepository` удалить. Проверить `ModulithArchitectureTest`.

4. `OrganizationsAnalyticsListPage`: не показывать ссылку «Открыть аналитику» для организаций, где роль текущего пользователя не owner/admin (если `OrganizationResponse` не содержит роль — добавить в карточку текст «Доступно владельцам и администраторам организации» и оставить ссылку, но на 403 показывать это же сообщение). Platform admin видит ссылки везде.

5. `EventAnalyticsView`, подпись под воронкой: дополнить «„Подано“ включает отклонённые и снятые заявки; это текущее состояние, а не история переходов».

6. В описании PR в разделе «Слияние» заменить «конфликты только pom/App.tsx» на фактический список: `qa04-endpoints.tsv` (с #33, #38, #40, #41), `App.tsx` и `OrganizerEventLayout.tsx` (с #41).

После правок: `mvn -q test` (с Docker; если нет — `-Dtest='AnalyticsIntegrationTest,ModulithArchitectureTest,Qa04RoleMatrixIntegrationTest'` и указать), `cd frontend && npm test && npm run build && npm run lint`. Отдельно — собрать Docker-образ и скачать XLSX из него (пункт 1).

## Порядок слияния / риски для стенда

- **С `origin/main` (63263d1) — чисто.** Миграций нет, `pom.xml` не трогается, `GlobalExceptionHandler` не трогается (новые коды `organizations.*`/`events.*` уже замаплены).
- **Относительно M07 (pr/40, e316d23):** M08 не зависит от M07 и может быть слит **до** него. Единственное пересечение — `qa04-endpoints.tsv` (оба добавляют строки в конец → конфликт разрешается объединением строк). Рекомендуемый порядок: M08 → M07 → follow-up M08-2 (delaySec + латентность публикации из `actual_*`). Если M07 сольётся первым, M08 нужно ребейзнуть только из-за TSV.
- **pr/33 (документы PDF), pr/38 (документы участников):** конфликт только в `qa04-endpoints.tsv` (хвост файла). Тривиально.
- **pr/41 (M05 уведомления):** конфликты в `App.tsx` (lazy-импорты/маршруты рядом), `OrganizerEventLayout.tsx` (оба добавляют вкладку в массив `['results','Результаты'] / ['notifications','Уведомления'] / ['analytics','Аналитика']`), `qa04-endpoints.tsv`. Тоже тривиально, но кто сливается вторым — ребейзится руками.
- Риски для стенда: (1) XLSX-экспорт в Docker-образе (блокирующее 1); (2) `venues.timezone` с не-IANA значением → 500 на аналитике события (желательное 4; проверить данные стенда `select distinct timezone from venues`); (3) кеш 60 с — после правки заявок организатор увидит старые числа до минуты, в UI это подписано («Обновление не чаще раза в минуту»).

## Что проверить на стенде

1. Организатор → своё событие с набором M → вкладка «Аналитика»: «Всего заявок» = число в разделе «Заявки»; суммы по статусам/дням/видам/группам/клубам/полу совпадают; воронка не возрастает.
2. «Скачать XLSX» **из Docker-образа стенда**: файл открывается, 12 листов, числа совпадают с панелью; лист «Результаты» — медиана/p95 пустые; «Импорты» — причина отсутствия.
3. Чужой организатор: JSON и XLSX события → 403; оператор/судья → 403; без токена → 401; несуществующий eventId → 404.
4. «Аналитика организаций» → своя организация (owner/admin): события по статусам, участия/уникальные/повторные, выручка по валютам (только `SUCCEEDED`); XLSX — 3 листа. Член с ролью `organizer` → 403.
5. Изменить статус заявки, обновить панель сразу (старые числа) и через 60 с («Обновить») — новые.
6. Ширина 360 px: таблицы прокручиваются горизонтально внутри карточек, страница не переполняется.
7. `select distinct timezone from venues` на стенде — все значения должны быть валидными IANA (`Europe/Moscow`, не `Москва`).
