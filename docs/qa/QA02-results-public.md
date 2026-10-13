# QA02 — результаты, публичное API и live

Проверка 13.10.2026 по `tasks/QA00_Общие_правила_тестирования.md`, `tasks/QA02_Результаты_и_публичное_API.md`, ТЗ §2 US-6/US-7, §4.8, §5.6/5.7 и T06. Ветка `qa/QA02-results-public` создана от `39456a2`, перед PR обновлена до main `a37fcb5`. Docker/PostgreSQL 16 доступны; проверки выполнены через настоящие Spring MVC, Security, JPA и Flyway с Testcontainers, а не MSW. Требуемые документы, демо-сценарий и README сида прочитаны.

**10 дефектов области: blocker 0, major 8, minor 2. Исправлены 5 major и 1 minor; остаются 3 major и 1 minor.** Две строки — ранее известные QA05-07/QA05-09, закреплённые дополнительными backend-тестами; их не следует повторно учитывать как новые дефекты проекта. Новых дефектов — 8 (major 6, minor 2).

## Дефекты

В шагах ниже `U` — заплыв с участником `E`, `D` — его опубликованный вид, событие доступно по `slug`. Ввод выполняется массивом через `POST /api/v1/competition-units/{U}/results`. Для проверки исправления опубликованных результатов тесты явно используют `FINISHED`; они не закрепляют старый целевой статус операции `/publication`. После мутации публичную выдачу следует проверить по истечении TTL 10 с. Файлы в таблице указаны относительно `sport-platform/src/main/java/com/acme/sportplatform/`.

| ID | Severity | Где и шаги воспроизведения | Ожидаемо, источник | Фактически до правки | Исправление / причина оставить; файл и строка | Автотест |
|---|---|---|---|---|---|---|
| QA02-01 | major | Внести VALID 46.20; завершить U до FINISHED; operator исправляет E на DNS, DNF или DSQ; GET `/public/events/{slug}/results` и `/athletes?q=Иванов`. | Терминальный исход сохраняет свой короткий код/display, время и оба места null; US-6, §4.8, §5.7. | БД: CORRECTED, time_ms=null, raw_value хранит исход; публично status=VALID, display=null. | **Исправлено:** SQL-проекция восстанавливает терминальный исход из raw_value только для CORRECTED без времени. Запись CORRECTED и аудит сохранены. `publication/infrastructure/PublicationReadRepository.java:92`. 4 добавленные строки. | `Qa02ResultsPublicIntegrationTest.qa02_01_correctedTerminalOutcomeStaysPublic`, 3 исхода; до правки 3 красных assertion VALID вместо DNS/DNF/DSQ. |
| QA02-02 | major | Внести результат в U, перевести U в COMPLETED; GET `/public/competition-units/{U}/results` и `/public/events/{eventId}/disciplines/{D}/results`. | Завершённые опубликованные результаты остаются видны; QA02 п.4, §5.7. | Первый путь — 404; второй — 200 с units=[]; canonical `/public/events/{slug}/results` отдаёт результат. | **Исправлено:** оба legacy use case принимают FINISHED и COMPLETED, добавлен derived repository method. `results/application/GetPublicCompetitionUnitResultsUseCase.java:55`, `GetPublicDisciplineResultsUseCase.java:54`, `results/infrastructure/jpa/CompetitionUnitRepository.java:62`. 6 добавленных строк. Переходы публикации не изменены. | `qa02_02_completedResultsStayPublicOnLegacyEndpoints`; до правки два несовпадения: 404/200 и 0/1 заплыв. |
| QA02-03 | major | У события DRAFT есть U=FINISHED с результатом; анонимно запросить оба legacy-пути из QA02-02. | Неопубликованное событие скрыто через 404; QA02 п.5, §5.7. | Оба пути возвращают 200 с именем и результатом; canonical slug-путь — 404. | **Исправлено:** публичные legacy-пути проверяют статус родительского события через `EventLookup`. `results/web/ResultsController.java:163,187,382`. Вместе с QA02-09 — 17 добавленных строк, без репозиториев чужого модуля. | `qa02_03_legacyEndpointsHideDraftEvents`; до правки два ответа 200 вместо 404. |
| QA02-04 | major | Поставить U по плану на 10 минут раньше текущего времени, следующий на +90 с; POST `/competition-units/{U}/start`; GET `/public/events/{slug}/live` и расписание следующего. | `/start` записывает факт, live содержит числовой delaySec, последующие scheduled_at сдвинуты от факта; ТЗ §5.7, T06 п.2a. | delaySec отсутствует; следующий scheduled_at остаётся в прошлом. | **Оставлено M07:** требует миграции, фактических времён, интеграции scheduling и изменения DTO, >40 строк. `results/application/EventLiveUseCase.java:50`, `publication/api/PublicResponses.java:51`. V027 уже занят; новых миграций здесь нет. M07 предлагает estimatedStartAt без изменения scheduled_at — это расходится с текущим §5.7 и требует согласованной правки ТЗ/ожидания теста. | `Qa02KnownDefectsIT.qa02_04_lateStartExposesDelayAndShiftsFollowingHeats`: оба assertion красные. |
| QA02-05 | major | В одной сессии U — заплыв №2 вида №1, V — заплыв №1 вида №2; scheduled_at обоих одинаков или null. `/schedule` ставит U перед V; начать и завершить U. | Live переходит на V в порядке программы; §4.7, §5.7, US-7. | `/finish` устанавливает currentUnitId=null: сравниваются номера заплывов разных видов, 1 не больше 2. | **Исправлено:** nextScheduled использует sequence_in_session и UUID при одинаковых/null временах. `results/infrastructure/jpa/CompetitionUnitRepository.java:37`. Заменено 10 строк SQL на 16; сигнатура/контракт ответа прежние. | `qa02_05_equalOrNullScheduledTimesAdvanceInDisciplineOrder`, оба случая. До исправления случай одинаковых времён: null вместо UUID следующего. |
| QA02-06 | minor | GET `/public/events/{slug}` у события без опубликованного файла/ссылки регламента. | Поле regulationUrl присутствует со значением null; T06 п.2. | Поля нет в DTO и JSON. | **Оставлено:** QA00 запрещает менять контракт API в дешёвой QA-правке; для непустого URL нужны lookup/выбор опубликованной версии. `publication/api/PublicResponses.java:14`, `publication/application/PublicPublicationService.java:32`. Основной §5.7 использует `{…}` и это поле явно не перечисляет; дополнительное требование взято из T06, противоречия нет. | `Qa02KnownDefectsIT.qa02_06_eventCardHasNullableRegulationUrl`, красный. |
| QA02-07 (QA05-07) | major | Начать U, заполнить VALID, POST `/competition-units/{U}/publication` `{published:true}` под judge; проверить публичные `/results`. | Кнопка публикации делает результаты публичными; US-6. | 204, но `/results` пуст: операция устанавливает PUBLISHED, JOIN требует FINISHED/COMPLETED. | **Оставлено M10 по прямому ограничению задачи.** `results/application/PublishCompetitionUnitUseCase.java:93`. Не добавлять PUBLISHED в публичный JOIN: это раскрывает черновой ввод. | `Qa02KnownDefectsIT.qa02_07_qa05_07_publicationMakesValidatedResultsPublic`, красный; утверждает видимость, не целевой статус. |
| QA02-08 (QA05-09) | major | Продолжить QA02-07: POST `/competition-units/{U}/finish`. | 200, продвижение live; демо-шаг 7, решение M10. | 400 results.invalid_transition, поскольку публикация сбросила IN_PROGRESS. | **Оставлено M10 по прямому ограничению задачи.** `results/application/EventLiveUseCase.java:75`. Сам переход FINISHED и идемпотентность здесь не меняются. | `Qa02KnownDefectsIT.qa02_08_qa05_09_finishAfterPublicationSucceeds`, красный, без ожидания PUBLISHED после публикации. |
| QA02-09 | major | Событие публично, D переведён в DRAFT, U=FINISHED содержит результат. Анонимно запросить оба legacy-пути QA02-02. | Результаты неопубликованного вида не видны; §5.7, QA02 п.4–5. | Legacy use case выбирают U независимо от статуса D; canonical slug-выдача исключает D. | **Исправлено:** общий guard использует существующий `EventDisciplineLookup.findPublishedByEventId`, возвращает 404 для скрытого вида. `results/web/ResultsController.java:382`. 2 строки сверх проверки события QA02-03. | `qa02_09_legacyEndpointsHideUnpublishedDisciplines`; также проверяется пустая canonical-выдача. |
| QA02-10 | minor | GET `/public/competition-units/{U}/results` при U=DRAFT/PUBLISHED/IN_PROGRESS. | 404 с русским сообщением; AGENTS.md п.8–9. | message="Published results were not found." | **Исправлено:** переведено сообщение, код/HTTP-статус прежние. `results/application/GetPublicCompetitionUnitResultsUseCase.java:145`. 1 строка. | `qa02_10_hiddenLegacyResultsReturnRussianError`, 3 исходных статуса. |

## Что проверено и прошло

- Ввод массивом под operator/judge/organizer: `00:46.20`, `46.20`, `1:02:03.45`; сохраняются 46200/46200/3723450 мс. Мусор, отрицательные, пустые, ноль, переполнение секунды/минуты и лишняя точность отклоняются с 400 и violations; валидная строка из того же ошибочного пакета не записывается.
- Время отсутствует у VALID, отрицательное timeMs, несовпадение time/timeMs, дубли entry, entry другого заплыва, PENDING на входе, null-элемент и пустой массив — 400 без результатов в БД.
- Ранжирование по всему виду через два заплыва: 1,1,3,4; внутри групп независимые места и ничьи; BY_TIME_ALL учитывает год/пол из snapshot, BY_AGE_GROUP — код заплыва; участник вне групп имеет абсолютное место и null place_in_age_group. DNS/DNF/DSQ без времени и обоих мест; пустая дорожка не получает выдуманную запись.
- Operator и organizer исправляют FINISHED: CORRECTED, перерасчёт в другом заплыве, предыдущие времена в input_json RANKING, новые — в output_json; неизменный повтор не создаёт второй лог. Judge получает 403 и время в БД не меняется. Публичная выдача после истечения/очистки кеша показывает исправленное время. Терминальные исправления покрыты всеми тремя исходами.
- Результаты в PUBLISHED/IN_PROGRESS скрыты, в FINISHED/COMPLETED видны. unitsPublished=true при наличии публичных заплывов, false когда оба DRAFT. Тесты не требуют, чтобы `/publication` сохранял статус PUBLISHED.
- Анонимные карточка, schedule, start-list, live, results, athletes: неизвестный/draft slug — 404. Чужой eventId для legacy дисциплины — 404; ошибки скрытого legacy-заплыва на русском. В шести canonical-ответах отсутствуют email, телефон, полная дата рождения, participantSnapshot; выводится только birthYear. Проверено на snapshot с явными приватными маркерами.
- Поля ResultRow точно соответствуют §5.7. Существующий `PublicPublicationIntegrationTest` дополнительно покрывает карточку/площадку/сессии, поля heat/live, расписание, пустые и снятые дорожки, группы, скрытые PENDING, нормализацию ё/е, prefix-поиск без регистра, все старты найденного спортсмена, лимит 20 профилей, CORS и Swagger.
- Установка текущего заплыва, старт/финиш, следующие три, переход в дневную сессию, конец программы с null currentUnitId и пустым next; при совпадающих и null временах не теряется следующий вид. Реальный TTL Caffeine проверен отдельным polling-тестом без очистки кеша: изменение PUBLISHED → IN_PROGRESS появилось за **10094 мс** в финальном прогоне, <15 с. Обновление браузера по таймеру относится к QA05; здесь проверен серверный кеш.

### Производительность и SQL на M

Использован штатный `src/test/resources/publication/set-M.json`: 120 профилей, 200 заявок, 4 вида, 2 сессии, 8 дорожек; по одному FINISHED заплыву в каждом виде, остальные PUBLISHED. Тест `PublicPublicationIntegrationTest.publicEndpointsForSetMHaveBoundedQueriesAndLogLatency` работает с PostgreSQL и включает `hibernate.generate_statistics=true`, `spring.jpa.show-sql=true`. После одного прогрева на каждый путь — три запроса с предварительной очисткой всех публичных кешей. Таймер включает MVC/сериализацию и разбор ответа, но не TCP/TLS/браузер. Это локальный benchmark, не гарантия p95 под нагрузкой.

| GET относительно `/api/v1/public/events/{slug}` | 3 замера, мс | Максимум, мс | SQL за запрос |
|---|---|---|---|
| карточка | 2 / 2 / 2 | 2 | 4 |
| `/start-list` | 9 / 9 / 13 | 13 | 5 |
| `/schedule` | 2 / 2 / 2 | 2 | 4 |
| `/live` | 5 / 5 / 5 | 5 | 5 |
| `/results` | 6 / 6 / 6 | 6 | 4 |
| `/athletes?q=К` | 16 / 16 / 17 | 17 | 4 |

Все замеры <300 мс; публичный start-list — 5 SQL, ниже ограничения ≤10. Запросы используют проекции и batch-чтение; N+1 в этих шести публичных ответах на M не обнаружен. Значения взяты из финального полного прогона.

### Автотесты и команды

Новые файлы: `results/Qa02ResultsFixture.java`, `results/Qa02ResultsPublicIntegrationTest.java`, `results/Qa02KnownDefectsIT.java` в `sport-platform/src/test/java/com/acme/sportplatform/`. Обычные регрессии проверяют исправленные дефекты. Оставшиеся четыре теста имеют `@Tag("known-defect")` в `*IT`; существующий Surefire исключает `*IT`, Failsafe исключает этот тег. Assertions не отключены и не ослаблены.

```bash
cd sport-platform
mvn -q test
# Только незакрытые требования: ожидается RED до M07/M10/правки карточки.
mvn -q test -Dtest=Qa02KnownDefectsIT
cd frontend
npm test
npm run build
npm run lint
```

В этой среде Maven запускается через `/workspace/tools/mvn` (Maven 3.9.11, Java 21); сетевые/Docker-команды требуют sandbox network permission.

| Проверка окончательного кода | Результат |
|---|---|
| `mvn -q test` с Docker/Testcontainers | **PASS**: 1064 теста в 42 классах, failures=0, errors=0, skipped=0; включая Modulith и матрицу QA04. |
| `mvn -q test -Dtest=Qa02ResultsPublicIntegrationTest` | **PASS**: 40 новых regression-проверок. Они также проходят в полном прогоне. |
| `mvn -q test -Dtest=Qa02KnownDefectsIT` | **Ожидаемо RED**: 4 теста, 4 failures, 0 errors/skip — только QA02-04/06/07/08. |
| `npm ci` | PASS, lockfile не изменён. |
| `npm test` | **PASS**: 3 node-теста + 42 Vitest в 9 файлах. |
| `npm run build` | **PASS**, включая PWA-сборку. |
| `npm run lint` | **PASS**, существующие предупреждения. |
| `git diff --check` | PASS. |

## Ручная проверка

1. На локальном backend с демо-данными M опубликовать стартовый протокол. Под judge начать заплыв, внести 46.20/46.20/48.00 и DNS. Сохранить: места 1,1,3, DNS без места. До завершения публичный `/e/{slug}/results` не показывает эти времена.
2. Пока M10 не слит, для открытия публичных результатов использовать «Финиш», а не «Опубликовать» (обход QA05-07/09). После M10 — штатную публикацию результатов. Это обход для проверки, не исправление QA02-07/08.
3. Под operator исправить 48.00 → 45.00. Проверить CORRECTED в ответе сохранения, места 1,2,2 и лог RANKING с предыдущей таблицей. По истечении 10 с публичный результат обновляется. Под judge повторить исправление: 403.
4. Под operator исправить один результат на DSQ; спустя TTL публичные `/results` и «Найти себя» показывают DSQ, display=DSQ, timeMs/place/placeInAgeGroup=null. Повторить с DNS и DNF.
5. В изолированной тестовой БД, не на стенде, проверить COMPLETED, DRAFT события/вида и одинаковые/null времена через указанные regression-тесты. Публичные результаты COMPLETED остаются видны; скрытые родители дают 404 в обоих legacy-путях; `/finish` не пропускает следующий вид.
6. Для оставшихся дефектов запустить `Qa02KnownDefectsIT`; четыре красных теста соответствуют QA02-04/06/07/08 и дают воспроизведение без ручных SQL-правок на демо-стенде.

## Не удалось проверить и почему

- Браузерный прогон, печать A4/PDF, мобильная PWA и сетевое обновление UI здесь не выполнялись: задача QA02 проверена на backend; результаты QA05 не выдаются за собственный ручной прогон. sportgate.pro не изменялся и его производительность не измерялась.
- `/api/v1/public/events/{slug}/heats/{unitId}`, упомянутый в QA02 п.5, не описан в приоритетном ТЗ §5.7/T06 и отсутствует в mapping. Не добавлялся выдуманный контракт: публичные данные заплыва проверены в start-list/live и существующем legacy `/public/competition-units/{unitId}/results`. Требуется уточнить задание, если нужен отдельный slug-scoped heat endpoint.
- Задержка старта и regulationUrl не реализованы; причины, владельцы и красные автотесты приведены выше. QA05-07/09 оставлены M10 по прямому указанию. M07 и нынешний §5.7 расходятся в том, менять ли scheduled_at автоматически.
- Метрики не включают сеть и конкурентную нагрузку. Новых миграций и эндпоинтов нет, таблица `security/qa04-endpoints.tsv` не требует новых строк. Меняются только поведение существующих двух legacy GET, терминальный исход в canonical results/search и выбор следующего заплыва при `/finish`; формат ответов сохранён.
