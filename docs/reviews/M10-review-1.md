# M10 — ревью 1 (PR #24, d941583)

PR #24 «fix(results): M10 — публикация результатов и идемпотентный финиш», ветка `pr/24` (3 коммита: 937fc20, 1ef4ea4, d941583) поверх `origin/main` a37fcb5. Задание: `tasks/M10_Публикация_результатов_и_финиш.md`. Ревьюер: Claude, 13.10.2026. Диф: 28 файлов, +421/−71; миграций нет; CI зелёный. Сборки/тесты в контейнере не прогонялись (нет сети) — принимаю по CI и описанию PR (1027 backend-тестов, 49 Vitest, `test:organizer` 15/15, реальный `test:demo` с шагом 7 = 0 ошибок).

**Вердикт: слить после одной правки в `docs/demo-script.md` (1 строка, ≤ 5 минут) и с двумя follow-up для PR #26 (QA02).** Код backend/frontend соответствует заданию по всем пунктам, коды ошибок совпадают дословно, публичный JOIN/кэш не тронуты, старые тесты переписаны, обход QA05-09 в e2e удалён, ожидания не ослаблены. Единственная проблема, которую нельзя оставлять до 15.10, — новая формулировка шага 7 в сценарии демо («нажать «Старт» для каждого заплыва») при буквальном следовании даёт 409 `results.unit_in_progress` на втором заплыве.

## Проверено

### 1. Backend — соответствие заданию (модуль `results`, `publication` не тронут)

| Пункт задания | Где | Результат |
|---|---|---|
| B1 `{published:true}`: `lockById`, PUBLISHED/IN_PROGRESS → FINISHED, проверки полноты дословно, `updated_at`, live не трогать | `PublishCompetitionUnitUseCase.java:44` (`lockById`), `:66-74` (switch по статусам), `:76-103` (проверки — текст не менялся), `:105-107` (FINISHED + `updated_at` + комментарий для M07) | **Выполнено.** `CompetitionUnitStatus.valueOf` безопасен: enum покрывает все 7 значений CHECK-ограничения V025 (`CompetitionUnitStatus.java:3-11`) |
| Коды: FINISHED/COMPLETED → `results.unit_already_published` «Результаты заплыва уже опубликованы» 409; DRAFT/SCHEDULED → `results.start_list_not_published` «Сначала опубликуйте стартовый протокол» 409; CANCELLED → `results.invalid_transition` 400 | `:67-73`; `GlobalExceptionHandler.java:105-108` | **Дословно.** Коды полноты (`unit_has_no_entries/missing/unvalidated/unranked`) переведены 400 → 409 — это соответствует п. 8 задания («незаполненная дорожка → 409 `results.unit_has_missing_results`»), отмечено в PR; `results.invalid_status` остался 400 |
| B2 `{published:false}`: только из FINISHED → PUBLISHED; иначе `results.unit_not_published` 409; COMPLETED → `invalid_transition`; JUDGE → 403; ветка «в DRAFT» удалена | `:51-63`; роль — в контроллере `ResultsController.java:350-355` (`AccessDeniedException` по authorities principal, как в `SwimResultsUseCase:96-110`) | **Выполнено.** `results.unit_already_draft` исчез из кода (grep по `pr/24` пуст). Проверка роли стоит после `assertUnitAccess` — чужой организатор по-прежнему получает 403 от `EventIntegrationTest:310` |
| B3 `finish` идемпотентен: IN_PROGRESS → FINISHED; FINISHED — статус/`updated_at` не меняются; в обоих случаях `lockState`, `nextScheduled`, `set` | `EventLiveUseCase.java:74-91` | **Выполнено.** Сообщение «Завершить можно только начатый или опубликованный заплыв» — как в задании. `start` (`:49-67`) не менялся: из FINISHED нельзя |
| B4 `correcting` без PUBLISHED | `SwimResultsUseCase.java:93-94` — `Set.of("FINISHED","COMPLETED")`; допустимые для ввода (`:81`) не менялись | **Выполнено.** Судья в PUBLISHED/IN_PROGRESS при повторном сохранении получает 200 (покрыто `PublicPublicationIntegrationTest:381-383`, `DemoDataModelIntegrationTest:624-631`). COMPLETED в `correcting` недостижим (`:81` его не пропускает) — мёртвая ветка, безвредно |
| B6 Swagger/Javadoc | `ResultsController.java:335-338`, `EventLiveController.java:38` | Выполнено |
| B7 legacy `RecordResultUseCase:83`/`RecalculatePlacesUseCase:53` не тронуты, в ADR как follow-up | `git diff --stat` их не содержит; ADR «Последствия» | Выполнено |
| Приёмка п. 2: `PUBLISHED` из `results/application` ставится только при снятии с публикации | grep `CompetitionUnitStatus.PUBLISHED\|"PUBLISHED"` по `results/application` на `pr/24`: `PublishCompetitionUnitUseCase:61` (снятие), остальное — чтение (`EventLiveUseCase:54`, `SwimResultsUseCase:81`, legacy use case) | Выполнено |

### 2. Переходы статусов

- **DRAFT → PUBLISHED (стартовый протокол):** модуль `scheduling` в дифе отсутствует; `organizer.spec.ts:260-263` теперь публикует стартовый протокол через штатную кнопку перед публикацией результатов, MSW-хендлер `/events/:id/competition-units/publish` (`handlers.ts:587`) не менялся. Не сломан.
- **`/publication {true}` из PUBLISHED | IN_PROGRESS → FINISHED** с полной проверкой (все дорожки с участниками: результат есть, не PENDING, у VALID/CORRECTED есть место, DNS/DNF/DSQ допустимы) — `:99-103, :110-150`. Тест на незаполненную дорожку с проверкой неизменности статуса: `publicationRejectsInvalidStatesAndMissingResultsWithoutChangingStatus` (`PublicPublicationIntegrationTest.java:427-443`).
- **`{false}`** только operator/organizer/admin, FINISHED → PUBLISHED, стартовый протокол остаётся публичным, `/start` снова доступен — `onlyManagersCanUnpublishFinishedResultsAndStartListRemainsPublic` (`:446-465`).
- **`finish` из FINISHED** — дважды в цикле, `updated_at` сравнивается с моментом публикации, `currentUnitId` = `heats.get(1)` оба раза (`:414-422`). `/finish` из PUBLISHED → 400: это уже покрывает `qa02_10`/существующие тесты live; в M10-тесте нет отдельной строки — см. «Желательные».
- **CORRECTED в FINISHED только operator/organizer:** `:398-410` (OPERATOR → CORRECTED, 1 RANKING-лог; JUDGE → 403, публичная выдача не изменилась).

### 3. Публичная выдача, live, кэш

- `publication/**` в дифе отсутствует: `PublicationReadRepository` (JOIN по `FINISHED`/`COMPLETED`, `res.status <> 'PENDING'`), `PUBLIC_UNITS`, `canRun`, Caffeine 10 с — без изменений.
- Черновики не утекают: `assertHiddenResults()` (`/results` пуст + `/athletes?q=` даёт `result: null`) вызывается после сохранения в PUBLISHED, после `/start` и после сохранения в IN_PROGRESS (`:379-390`); после `{published:false}` (`:452`); после отказа публикации с незаполненной дорожкой (`:440`).
- `/live.currentUnit` после публикации — тот же заплыв со статусом FINISHED (`:393-395`), live-указатель публикация не двигает.
- Сопутствующая правка `RESULTS_POLL_MS` 60 с → 5 с (`usePublicData.ts:4-6`, `Review.test.tsx:165`) — вне задания, но обоснована: с TTL 10 с без неё приёмка «телефон обновился ≤ 15 с» недостижима без reload. Нагрузка: запросы отдаются из Caffeine, polling при скрытой вкладке приостанавливается. Принимаю; см. риски для стенда.

### 4. `publishDisciplineResults`

`scheduling.api.ts:84-96`: пропускает `FINISHED|COMPLETED`, при ошибке — остановка с номером заплыва и сообщением backend (вариант выбран и описан в PR). Unit-тест `scheduling.api.test.ts` (2 кейса). Кнопка «Опубликовать результаты вида» (`OrganizerResultsPage.tsx:40-54`) — `some(PUBLISHED|IN_PROGRESS && полные результаты)`; ячейка «Результаты» (`:83-86`) — «Опубликованы» при FINISHED/COMPLETED, «Внесены» при наличии результатов. Соответствует F3/F4.

### 5. Тесты

- **Интеграционный полный цикл судьи** (п. 8 задания) — `judgePublicationCorrectionAndIdempotentFinishKeepPublicResultsAndLiveConsistent` (`PublicPublicationIntegrationTest.java:366-423`): все 9 шагов задания с проверкой публичных ответов после каждого (`clearPublicCaches()` после каждой мутации), места 1,1,3 + DNS без места (`assertPublishedPlaces`, `:497-507`), 48.00 → 48.10, повторный finish.
- `publishedStartLanesStayVisibleButResultsRequireStartAndFinish` (`:347-364`) **переписан, не удалён**: стартовый протокол через `jdbc`-апдейт (как разрешено заданием), результаты видны только после `/finish`.
- `DemoDataModelIntegrationTest:624-634` — осмысленно: повторный ввод судьи 200 без RANKING-лога, затем `/publication` → FINISHED, затем 403 судье на исправление (хвост теста сохранён, `33000` → `33100` в проверке лога корректно отражает новый ввод).
- **TSV QA04** `qa04-endpoints.tsv:87` → фикстура `unit-in-progress` (есть в `Qa04RoleMatrixIntegrationTest:140`). Вторая строка `{published:false}` не добавлена из-за `doesNotHaveDuplicates` — покрыто интеграционным тестом, сказано в PR (как и предусматривало задание).
- **e2e `qa05-demo.spec.ts`:** `:365` локатор «Опубликовать результаты» (`exact: true`) — единственное изменение ожидания, вызванное переименованием; `:366-367, :383, :388, :396, :405` — soft-expect'ы не тронуты; блок обхода «Continue via UI start/finish…» удалён (`:399-407`), ожидание «Заплыв завершён» (`:406`) сохранено; комментарий `:399` заменён по заданию. Ослаблений нет.
- MSW `handlers.ts:594-613` — коды/статусы как у backend; `mocks/README.md:19` обновлён. `organizer.spec.ts:260-263` — добавлен шаг публикации стартового протокола, ожидание ячейки «Опубликованы» (`:274`) сохранено.
- Vitest `JudgePanelPage.test.tsx` — 5 кейсов: (а) IN_PROGRESS → публикация → FINISHED/«Сохранить исправление»/уведомление; (б) судья в FINISHED: поля disabled, «Снять…» отсутствует, «Финиш» активна; (в) оператор снимает публикацию; (г) «Финиш» в FINISHED → «Заплыв завершён.», без alert; (д) судья в PUBLISHED редактирует.

### 6. Frontend JudgePanelPage

`JudgePanelPage.tsx:41-43` `resultsPublished`/`canCorrect`/`canEdit`; `:149` подпись кнопки сохранения; `:150-155` кнопка публикации: видна судье только до публикации, недоступна при `dirty`/неполных результатах/вне PUBLISHED|IN_PROGRESS; `:156-160` «Старт» только DRAFT|SCHEDULED|PUBLISHED, «Финиш» — IN_PROGRESS|FINISHED. После «Опубликовать» — `applyUnit(await changeCompetitionUnitPublication(...))`, которая делает POST+GET (`results.api.ts:216-232`, не менялась) — QA05-06 не регрессировал (кейс (а) Vitest + реальный прогон шага 7). Тексты на русском: «Результаты опубликованы.» дословно, «Публикация результатов снята.», «Снять результаты с публикации», `heatStatusLabel` (`results-ui.ts:15-17`) — как в задании.

### 7. Документы

- `docs/demo-script.md:92, :99` — обход из QA05 (в main его не было) не появился; последовательность возвращена, «Запасной ход» сохранён. **Но см. Блокирующие.**
- `docs/02_ТЗ_демо.md:227` (§5.6), `:236` (§5.7 `/finish`), `:247` (§6.2 — кнопки переименованы). `docs/01_Архитектура.md:65` — одна фраза про PUBLISHED/FINISHED.
- ADR `docs/decisions/2026-10-13-publikaciya-rezultatov.md` — формат как у `2026-10-12-…`; контекст, решение, последствия (V035, legacy, M07 `actual_finished_at`) — всё из задания. 
- `docs/qa/QA05-e2e-demo.md:17, :21` — QA05-07/09 «Исправлено в M10», колонка «Для демо» — «Обход больше не нужен»; раздел «Повтор после M10» с UUID прогона. Таблица — 8 колонок везде (проверил `:7-8`).

### 8. Миграции, Modulith

Миграций нет (`src/main/resources/db` не в дифе). Новых межмодульных зависимостей нет: `results` по-прежнему ходит в `competition` только через `EventDisciplineLookup`, в `events` — через `EventAccessService`; `ResultsController` импортирует только `org.springframework.security.access.AccessDeniedException`. `ModulithArchitectureTest` — в CI зелёный.

### 9. Конфликты

`git merge-tree --write-tree pr/24 X` для `origin/main`, `pr/15`, `pr/18`, `pr/21`, `pr/26` — **все без конфликтов** (exit 0). Семантика:

- **pr/26 (QA02)** — единственное содержательное пересечение. (а) `Qa02ResultsPublicIntegrationTest` **совместим с M10**: исправления проверяются через `state(first, "FINISHED")` (jdbc), а не через `/publication`; `judgeCannotCorrectFinishedResults`, `finishedCorrectionReranksOtherHeats…` ожидают ровно то, что даёт M10; отчёт прямо говорит «Тесты не требуют, чтобы `/publication` сохранял статус PUBLISHED» (`docs/qa/QA02-results-public.md:36`). (б) `Qa02KnownDefectsIT.qa02_07_qa05_07_…` и `qa02_08_qa05_09_…` (`:41-64`, тег `known-defect`, в обычный прогон не входят) после M10 **станут зелёными** — и останутся навсегда исключёнными из прогона; строки QA02-07/08 отчёта (`QA02-results-public.md:25-26`) говорят «Оставлено M10». (в) pr/26 переписывает `CompetitionUnitRepository.nextScheduled` (сортировка по `sequence_in_session`) — M10 `finish` его только вызывает, сигнатура прежняя; совместимо. (г) pr/26 и M10 правят разные участки `ResultsController` — merge-tree чистый.
- **pr/15 (M01)** — пересечение только в `GlobalExceptionHandler` (другие `case`, разные строки) и `qa04-endpoints.tsv` (+6 строк админки; M10 меняет строку 87) — текстовых конфликтов нет.
- **pr/21 (QA06)** — `demo-script.md:64` (заголовок шага 5) и CSS панели судьи; M10 трогает `:92, :99` и TSX. Чисто.
- **pr/18 (QA03)** — пересечений нет.

## Блокирующие

1. **`docs/demo-script.md:92` — «Перед вводом убедиться, что нет снятых участников, затем нажать «Старт» для каждого заплыва».** Если ведущий сделает буквально (Старт у первого → Старт у второго → ввод/сохранение/публикация обоих), второй «Старт» вернёт 409 `results.unit_in_progress` «Другой заплыв ещё не завершён» (`EventLiveUseCase.java:58-63`: другой заплыв IN_PROGRESS и он текущий в live), а в панели появится красный alert на сцене. Рабочая последовательность — та, что в e2e (`qa05-demo.spec.ts:349-369`) и в задании: **для каждого заплыва по очереди** «Старт → ввод → Сохранить и пересчитать → Опубликовать результаты» (после публикации первый становится FINISHED, и Старт второго проходит). Исправить строку `:92` и начало `:99` («Нажать «Опубликовать результаты» для обоих заплывов» → «после сохранения каждого заплыва сразу нажать «Опубликовать результаты», затем переходить ко второму»). Это документ, не код, но это сценарий, по которому пойдёт показ 15.10.

## Желательные (не держат слияние)

1. `scheduling.api.ts:89` — цикл пропускает только `FINISHED|COMPLETED`, но идёт по `DRAFT|SCHEDULED|CANCELLED` заплывам вида: при смешанном виде (часть заплывов не опубликована в стартовом протоколе) кнопка «Опубликовать результаты вида» остановится на первом таком с 409 `start_list_not_published`, хотя сама кнопка была активна (`OrganizerResultsPage.tsx:43`). Лучше публиковать только `PUBLISHED|IN_PROGRESS` с полными результатами (тот же предикат, что у `disabled`), остальные пропускать.
2. `EventLiveUseCase.java:83-91` — идемпотентный `finish` на FINISHED заплыве, который **не** является текущим в live, передвинет `current_unit_id` на заплыв, следующий за ним, даже если в этот момент идёт другой (IN_PROGRESS). Для демо это неважно (панель секретаря открыта на нужном заплыве), но для MVP стоит либо не трогать указатель, если `state.currentUnitId != id`, либо оговорить в ADR. Прежнее поведение `finish` для IN_PROGRESS было таким же, так что это не регрессия.
3. `docs/demo-script.md:99` — «Закончить первый заплыв кнопкой «Финиш» и показать переход live к следующему»: после публикации обоих заплывов «следующим» в live станет второй, уже FINISHED (на табло — завершённый заплыв как текущий). Зрелищнее нажать «Финиш» у первого **до** старта второго либо указать это в сценарии.
4. `PublicPublicationIntegrationTest` — нет отдельной строки `/finish` из PUBLISHED (не начатого) → 400 `results.invalid_transition` (п. 8 задания). Поведение закреплено существующими live-тестами и `qa02_10` в pr/26, поэтому не блокирует.
5. `SwimResultsUseCase.java:94` — `COMPLETED` в `correcting` недостижим из-за `:81`; либо убрать, либо добавить COMPLETED в допустимые для ввода (второе — отдельное решение, не для M10).
6. `ResultsController.java:350-355` — проверка роли дублирует набор `ROLE_ORGANIZER/OPERATOR/PLATFORM_ADMIN` из `SwimResultsUseCase:103-106`; вынести в общий helper (`ResultsAccessService.assertCanCorrect(principal)`), чтобы M07/QA02 не разъехались.
7. После слияния #24 и #26: перенести `qa02_07`/`qa02_08` из `Qa02KnownDefectsIT` в `Qa02ResultsPublicIntegrationTest` (иначе зелёные тесты никогда не выполняются) и поправить строки QA02-07/08 в `docs/qa/QA02-results-public.md:25-26` на «Исправлено в M10 (#24)». Это правка в PR #26, не в #24.

## Текст правок для Codex

Внеси правки по ревью M10:

1. `docs/demo-script.md`, шаг 7: строку «Перед вводом убедиться, что нет снятых участников, затем нажать «Старт» для каждого заплыва» заменить на «Перед вводом убедиться, что нет снятых участников. Заплывы проходить строго по очереди: «Старт» → ввод времён → «Сохранить и пересчитать» → «Опубликовать результаты» для первого заплыва, и только затем «Старт» второго (пока первый IN_PROGRESS, «Старт» другого заплыва даёт 409 «Другой заплыв ещё не завершён»)». В следующем абзаце фразу «Нажать «Опубликовать результаты» для обоих заплывов: статус становится FINISHED, live остаётся на текущем заплыве до «Финиш»» согласовать с этим порядком (публикация — сразу после сохранения каждого заплыва).
2. (по желанию, в этом же PR) `sport-platform/frontend/src/api/scheduling.api.ts publishDisciplineResults`: пропускать все заплывы, кроме `PUBLISHED|IN_PROGRESS` с полными результатами (тот же предикат, что у `disabled` кнопки в `OrganizerResultsPage.tsx`), чтобы кнопка не падала на DRAFT-заплывах вида; обновить `scheduling.api.test.ts`.
3. (по желанию) `PublicPublicationIntegrationTest`: добавить один вызов `/finish` на PUBLISHED-заплыве → 400 `results.invalid_transition`.

Код backend/frontend, тесты, TSV, e2e и ADR менять не нужно.

## Порядок слияния и риски для стенда

1. **#24 сливать первым среди results-веток** — до #26 (QA02): #26 вносит `nextScheduled` с `sequence_in_session`; при обратном порядке ничего не ломается (merge-tree чистый), но тогда `Qa02KnownDefectsIT` в #26 при ребейзе уже будут зелёными и п. 7 «Желательных» надо делать сразу в #26.
2. Относительно #15/#18/#21 порядок произвольный; у #15 и #24 общий `qa04-endpoints.tsv` — git сольёт без конфликта (разные строки), но `fixtureTableCoversEveryApiV1ControllerMapping` после слияния обоих стоит прогнать один раз.
3. **Семантика на стенде меняется для уже существующих данных:** заплывы, которые до деплоя «опубликовали результаты» старой кнопкой, стоят в `PUBLISHED` с результатами; после деплоя их результаты публично **не видны** (и не были видны до этого — QA05-07), ячейка «Результаты» покажет «Внесены», а кнопка «Опубликовать результаты» переведёт их в FINISHED. Для демо-события сид (`scripts/demo/seed.ts`) результаты не вносит, так что перед репетицией достаточно `reset.sql` + сид. Если на стенде есть реальные события с результатами в PUBLISHED — предупредить организатора, что нужно нажать «Опубликовать результаты (вида)».
4. **`RESULTS_POLL_MS` 5 с:** каждый открытый `/e/{slug}/results` даёт 12 запросов/мин вместо 1. Caffeine 10 с держит БД, но на демо с 20–30 телефонами это ~5 RPS на `/results` — для Spring Boot на одном инстансе некритично; следить за логом латентности `publication` (бенчмарк `publicEndpointsForSetMHaveBoundedQueriesAndLogLatency`). Откат — одна константа.
5. Коды 400 → 409 для `results.unit_has_*`: если у кого-то есть внешний клиент/скрипт, ждущий 400, — он сломается; в репозитории таких нет (grep).
6. Без миграций — откат деплоя безопасен: старый код снова будет ставить PUBLISHED при публикации, FINISHED-заплывы для него валидны.

## Что проверить на стенде после деплоя руками (6 шагов)

1. Под `sec@` (operator) открыть первый заплыв M-50-FREE: «Старт» → 200, статус «Идёт заплыв»; ввести 46.20 / 46.20 / 48.00 / DNS → «Сохранить и пересчитать» → места 1, 1, 3, DNS без места. На телефоне `/e/{slug}/results` — вид **пуст** (черновик не утёк), «Найти себя» у участника первого заплыва — без результата.
2. «Опубликовать результаты» → уведомление «Результаты опубликованы.», статус «Завершён, результаты опубликованы», alert нет, панель остаётся с данными (QA05-06). Повторное нажатие невозможно (кнопка сменилась на «Снять результаты с публикации»); через API повтор `{published:true}` → 409 `results.unit_already_published`. На телефоне в течение ≤ 15 с без reload появились 1, 1, 3 и DNS.
3. Исправить 48.00 → 48.10, «Сохранить исправление» → бейдж «исправлено», места 1, 1, 3; на телефоне ≤ 15 с без reload — 00:48.10. Под `judge@` открыть тот же заплыв: поля заблокированы, кнопки «Снять…» нет; `curl` POST `/results` → 403 с русским текстом.
4. «Финиш» → 200, «Заплыв завершён.», `/e/{slug}/live` показывает следующий заплыв; второй «Финиш» → 200, live на том же месте, `updated_at` заплыва не изменился (проверить в БД или по `GET /competition-units/{id}`).
5. Второй заплыв: «Старт» проходит (первый уже FINISHED), ввод второго набора (49.10 … 51.00 с DNF/DSQ), публикация; «Опубликовать результаты вида» на странице «Результаты» — активна только если остался PUBLISHED/IN_PROGRESS заплыв с полными результатами, повторное нажатие не даёт 409 на FINISHED.
6. Под `sec@` «Снять результаты с публикации» у второго заплыва → «Публикация результатов снята.», статус «Стартовый протокол опубликован», `/start-list` по-прежнему содержит заплыв, `/results` через ≤ 10 с без него, «Старт» снова доступен; вернуть публикацию. Открыть `/e/{slug}/results?print=1` → PDF с отдельным блоком DNS/DNF/DSQ.
