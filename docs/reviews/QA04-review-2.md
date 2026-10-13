# QA04 — ревью 2 (PR #16, bc1998a)

Проверка коммита bc1998a «fix(security): address QA04 review and venue read access» поверх 4b499d8 (`git diff 4b499d8..bc1998a`: 12 файлов, +129/−20; production-код — только `LoginUseCase.java` (+3/−2), `VenueController.java` (2 аннотации), `organizer.api.ts` (1 строка)). Ревьюер: Claude, 13.10.2026. CI на bc1998a зелёный (по словам автора задания).

**Вердикт: слить.** Все 6 пунктов закрыты, регрессий нет, merge с `origin/main` (eada404, с #17/#19/#20/#22) чистый.

## Проверено по 6 пунктам

| № | Пункт ревью 1 | Результат |
|---|---|---|
| 1 | `LoginUseCase` ловит только `BadCredentialsException \| AccountStatusException` | **Закрыто.** `LoginUseCase.java:42`; импорт `AuthenticationException` убран. `InternalAuthenticationServiceException`/`AuthenticationServiceException` уходят в `GlobalExceptionHandler` → 500 `internal_error`. Новый `LoginUseCaseTest` (standalone MockMvc с настоящими `AuthController` + `GlobalExceptionHandler`, моки manager/users/roles/jwt): 2 кейса → 500, 5 кейсов (BadCredentials, Disabled, Locked, AccountExpired, CredentialsExpired) → 401 `identity.invalid_credentials`, `verifyNoInteractions(users, roles, jwt)`. Покрывает ровно то, что просилось |
| 2 | `Qa04KnownDefectsIT`: `@Tag("known-defect")` + failsafe `excludedGroups` + владельцы | **Закрыто.** `@Tag("known-defect")` на классе; `pom.xml:197-199` `maven-failsafe-plugin` → `<configuration><excludedGroups>known-defect</excludedGroups></configuration>`; surefire-исключение `**/*IT.java` не тронуто. Javadoc каждого из 4 тестов с владельцем (QA04-06 → M09, V028+; QA04-07/08 → M02; QA04-10 → отдельная задача). Assertions не ослаблены, `@Disabled` нет. В отчёте «Проверки перед PR» добавлен `mvn -q verify -DskipITs=false` и явная команда `-Dtest=Qa04KnownDefectsIT` |
| 3 | `Qa04SecurityFixture`: убрать `DEMO_SEED=false` | **Закрыто.** Осталось только `APP_CORS_ALLOWED_ORIGINS=https://qa04.example`; в Javadoc `qa04_06` объяснено, почему переменная не задаётся |
| 4 | AGENTS.md п. 7 — строка про TSV | **Закрыто.** Формулировка из ревью добавлена дословно (метод, путь, политика, scoped, код, состояние фикстуры, тело; имя падающего теста) |
| 5 | Отчёт: «727 проверок» → «41 тест + матрица 98×7 (686)» | **Закрыто.** `docs/qa/QA04-security-rbac.md:158`; заодно обновлена матрица (строки `GET /venues`, `/venues/{id}`), п. QA04-04 и добавлен раздел «Правки по ревью 1» |
| 6 | QA05-02: чтение площадок для operator/judge | **Закрыто.** `VenueController.java:50,56`: `hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR','JUDGE')` на оба GET; `POST /venues` по-прежнему `hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')` (изменения/удаления площадок в контроллере нет — только create). TSV: обе строки GET переведены на `manage-read` = `judge, operator, organizer, foreign_organizer, platform_admin` → 200, `participant` → 403, `anonymous` → 401 (`Qa04RoleMatrixIntegrationTest:57`); матрица проверяет это реальными запросами. Фронт: `organizer.api.ts:23` `getVenues(accessToken).catch(() => [])` → `venue: undefined`, event/disciplines загружаются; новый `organizer.api.test.ts` (msw, 403 на `/venues`) это фиксирует |

Дополнительно:
- **Регрессии / старые тесты:** в `src/test` удалений нет; единственное изменение существующего теста — парсер TSV в `Qa04RoleMatrixIntegrationTest:41` стал терпеть строки с 5 колонками (`c.length > 5 ? c[5] : ""`). Это не ослабление: политика/код/scoped по-прежнему обязательны, а `fixtureTableCoversEveryApiV1ControllerMapping` не менялся.
- **Миграции:** `git diff --name-only 4b499d8..bc1998a | grep migration` — пусто.
- **Merge с `origin/main` (eada404: #17 QA05, #19 QA01, #20, #22):** `git merge-tree --write-tree origin/main bc1998a` → `cac50188…`, exit 0, конфликтов нет; пересечения по файлам с PR — ни одного. Семантика: на `main` новых маппингов `/api/v1/**` не появилось (TSV на 98 строк остаётся полным); #19 ужесточил `CreateVenueUseCase` (lanes ∈ {6,8,10}, courseLength ∈ {25,50}) и `CreateEventUseCase` (end ≥ start) — тела фикстуры матрицы (`lanes 8 / courseLength 25`, `2030-06-01 10:00→18:00`) под новые правила проходят; остальные правки #19 — только русификация сообщений. Ожидаю зелёный `main` после слияния.
- **Сценарий демо:** единственное поведенческое изменение для стенда — секретарь/судья теперь получают 200 на справочник площадок (раньше 403 рвал `Promise.all` в кабинете события). Откат — redeploy образа.

## Замечания (не блокируют)

1. `qa04-endpoints.tsv:44-45` — две строки `GET /venues*` имеют 5 колонок вместо 7 (убраны хвостовые табы), из-за чего и понадобилось ослабление парсера. Для единообразия вернуть два пустых хвостовых поля (как во всех остальных 96 строках) — тогда правку парсера можно оставить как защиту, но файл будет однородным.
2. `LoginUseCaseTest` по сути контроллерный тест (`standaloneSetup(AuthController)`), а лежит в `identity/application` и назван как unit-тест use case. Работает, но при следующем касании логично переименовать в `AuthLoginErrorMappingTest` или перенести в `identity/web`.
3. Напоминание из ревью 1 остаётся в силе: #15 (M01) после слияния #16 обязан ребейзиться и разрешать семантические конфликты в `JwtAuthenticationFilter`/`LoginUseCase`/`CreateUserRequest` + добавить 6 строк в TSV (см. QA04-review-1, «Порядок слияния»). К #16 это требований не добавляет.

## Текст правок

Не требуются. Слить bc1998a как есть; замечания 1–2 — по желанию в любом следующем PR.
