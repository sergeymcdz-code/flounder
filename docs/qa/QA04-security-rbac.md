# QA04 — безопасность и ролевая модель

Проверка 13.10.2026. База: актуальный `origin/main` (`0a758521b5acbe3f4daaa4f3b5595bf426c2c19d`), ветка `qa/QA04-security-rbac`. QA00 и QA04 прочитаны из `origin/docs/tasks-qa` через `git show` после fetch; требования сверены с `AGENTS.md`, ТЗ (§2, §4, §5, §6, §8), демо-сценарием и `scripts/demo/README.md`.

Итог QA04: **10 дефектов: 1 blocker, 4 major, 5 minor; 9 исправлены (QA04-07 M01, QA04-08 M02, QA04-10 раунд 2), 1 оставлен (QA04-06)**. Blocker относится к запуску с отключённым демо-сидом, а не к возможности показать выделенный демо-стенд. Изменений миграций нет; будущие исправления схемы — только V035+ (V031–V034 закреплены за M04–M07). Дополнительно по ревью исправлен QA05-02 (major для демо): чтение справочника площадок разрешено operator/judge, отказ справочника не прерывает загрузку кабинета события. Структура API/UI сохранена; уточнены ответы ошибок и валидация существующих ограничений хранения/bcrypt. Каждое небольшое исправление укладывается в 40 строк производственного кода.

## Дефекты

Коды «до» подтверждены тестами на исходном коде; «после» — регрессиями. Для QA04-02 до исправления MockMvc выбрасывал исключение, поэтому HTTP-код не приписывается этому прогону. Номера строк ниже относятся к итоговой версии. Java-файлы находятся под `sport-platform/src/main/java/com/acme/sportplatform/`.

| ID | Severity | Где и шаги воспроизведения | Ожидаемо по требованиям | Фактически до исправления | Исправление / статус / файл и строка | Автотест |
|---|---|---|---|---|---|---|
| QA04-01 | major | Получить JWT администратора; заблокировать/деактивировать/удалить пользователя или отозвать роль; старым JWT выполнить GET `/api/v1/users/{id}`. | После отключения пользователя 401, после отзыва admin 403. QA04 п.3; роли ТЗ §1. | Во всех четырёх случаях старый токен сохранял admin-доступ, 200, до истечения 2 часов. | **Исправлено:** пользователь и текущие роли проверяются в БД на каждом JWT-запросе. `identity/infrastructure/security/JwtAuthenticationFilter.java:102`. После интеграции M01 — одно чтение пользователя с глобальными ролями на JWT-запрос, без кеша. | `Qa04JwtIntegrationTest.disabledOrDeletedUserCannotUsePreviouslyIssuedAdminToken`, `revokedAdminRoleCannotBeReusedFromOldJwt` |
| QA04-02 | minor | GET `/api/v1/auth/me` с пустым, malformed, поддельным либо истёкшим Bearer. | JSON 401 без стека/SQL, QA04 п.3–4. | Фильтр выбрасывал `InsufficientAuthenticationException` до `ExceptionTranslationFilter`; MockMvc завершался исключением без штатного JSON-ответа. | **Исправлено:** вызов существующего `RestAuthenticationEntryPoint`, 401. `identity/infrastructure/security/JwtAuthenticationFilter.java:132`. | `Qa04JwtIntegrationTest.invalidJwtReturnsJson401`; существующий `JwtAuthenticationFilterTest` сохранён и адаптирован к ответу через entry point, с проверкой причины и отсутствия вызова цепочки |
| QA04-03 | major | Подписать тестовым ключом токен с чужим issuer либо без exp; запросить `/auth/me`. | Принимаются только токены приложения с ограниченным сроком, QA04 п.3. | Оба токена принимались, 200. | **Исправлено:** обязательные issuer и exp. `identity/infrastructure/security/JwtService.java:51`. | `Qa04JwtIntegrationTest.invalidJwtReturnsJson401` (wrong-issuer, no-expiry) |
| QA04-04 | minor | Установить пользователю inactive/blocked; POST `/auth/login` с верным и неверным паролем. | По M01 §5.8: 403 `identity.user_blocked` только при верном пароле, иначе общий 401. | До QA04 — 500: `DisabledException` не перехватывался. | **Исправлено, интегрировано с M01:** пароль отключённого аккаунта проверяется через PasswordEncoder; ошибочный пароль не раскрывает состояние. Ошибки инфраструктуры аутентификации остаются 500. | `Qa04InputIntegrationTest.loginForDisabledAccountsOnlyRevealsAccountStateWithTheCorrectPassword`, `LoginUseCaseTest` |
| QA04-05 | minor | POST `/users` с phone/middleName/city/countryCode/clubName длиной 300; отдельно пароль из 73 ASCII или 40 кириллических символов (80 UTF-8 байт). | 400 до записи/хеширования; QA04 п.5, ограниченные типы модели. | 500 на ограничениях БД или bcrypt. Длинные ФИО/пол уже отклонялись доменной проверкой; теперь ограничения DTO единообразны. | **Исправлено:** размеры по существующей схеме и предел bcrypt 72 **байта**, без введения новой минимальной политики паролей. `identity/api/CreateUserRequest.java:10`, `:23`. | `Qa04InputIntegrationTest.selfRegistrationRejectsStringsBeyondStorageLimits`, `selfRegistrationRejectsPasswordsBeyondBcryptByteLimit` (без внешней тестовой транзакции — проверен настоящий commit) |
| QA04-06 | blocker | Чистая БД после Flyway, `DEMO_SEED=false`; POST `/auth/login` с демо-admin из ТЗ §1. | Демо-учётные данные недоступны при отключённом сиде; QA04 п.6, ТЗ §1/§8.6. | 200 с admin-JWT. V020 безусловно активирует известный admin-пароль; `DEMO_SEED` из compose нигде не прочитан приложением. | **Оставлено:** вынести демо-bootstrap из обязательных миграций, предусмотреть безопасный admin-bootstrap и ротацию/отключение уже созданного аккаунта. Это меняет bootstrap стенда; bootstrap из APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD добавлен в этом PR, но для отключения seed нужна отдельная задача и V035+, старые миграции не менять. `sport-platform/src/main/resources/db/migration/V020__seed_fixes.sql:10`, `infra/compose.prod.yaml:52`. Скрипт T10 защищён `DEMO_SEED_ENABLED`, но не управляет V020. | `Qa04KnownDefectsIT.qa04_06_seedDisabledMustNotAllowPublicDemoAdminCredentials` — красный: 401/200 |
| QA04-07 | major | POST `/users` с password=`a` и корректным профилем. | Не менее 8 символов; ТЗ §5.8 (M01). | До M01 — 201, только `@NotBlank`. | **Закрыто M01:** `CreateUserRequest.password` валидируется `@Size(min=8,max=72)`; проверка bcrypt 72 байта из QA04 сохранена. Запрос отклоняется до создания пользователя. Остальные задачи политики/восстановления остаются M02. | `Qa04InputIntegrationTest.qa04_07_oneCharacterPasswordMustNotCreateAnAccount` — зелёный: 400 `validation_error`, `violations[field=password]`, пользователей с этим email нет |
| QA04-08 | minor | 50 неверных POST `/auth/login` подряд с одного IP 192.0.2.44. | Ограничение частоты/429; QA04 п.6: minor для демо, major для MVP. | До M02 все 50 ответов — 401. | **Закрыто M02:** после 5 неудачных попыток за скользящие 15 минут по нормализованному email **или** адресу соединения — 429 `identity.too_many_attempts` и `Retry-After`. Caffeine и подменяемый Clock; ограничивается только login. Для нескольких инстансов нужен Redis; в prod IP восстанавливает RemoteIpValve через заголовок Caddy, локально/в тестах используется прямой remoteAddr. | `Qa04InputIntegrationTest.qa04_08_repeatedFailedLoginMustEventuallyBeRateLimited`; `AuthHardeningIntegrationTest.rateLimitByNormalizedEmailIgnoresIpChangesAndResetsAt15Minutes`, `rateLimitByIpIgnoresEmailAndForwardedHeaderChangesAndDoesNotLimitOtherEndpoints` — зелёные |
| QA04-09 | minor | Admin GET `/actuator/env`, endpoint отключён настройкой приложения. | RBAC пропускает admin, затем 404 отсутствующего endpoint; QA04 п.2/п.4. | 500: `NoResourceFoundException` попадает в общий обработчик 500. | **Исправлено:** отдельный безопасный 404 `common.not_found`; закрытые endpoint по-прежнему 401/403 для остальных. `common/web/GlobalExceptionHandler.java:267`. | `Qa04DataAccessIntegrationTest.corsAndActuatorEnforceOriginsAndGlobalRole` |
| QA04-10 | major | Анонимно POST `/users` с ФИО/датой рождения импортированного спортсмена, затем withdraw его заявки. | Профиль нельзя присвоить без подтверждения владения; QA04 п.1–2, ТЗ §1/§5. | После QA03 путь публичной заявки даёт 409, но signup оставался 201 и присваивал профиль. | **Закрыто раундом 2:** signup через `/users` и `/auth/register` возвращает 409 `identity.profile_already_attached` до создания аккаунта; профиль/чужая заявка сохранены. Отдельная задача подтверждения владения нужна для разрешённого присоединения. `identity/application/CreateUserUseCase.java:66`. См. `QA04-round2.md`. | `Qa04Round2AdminIntegrationTest.qa04_10_unverifiedSignupCannotClaimImportedProfile` — основной прогон; legacy IT обновлён на 409 |

## Что проверено и прошло

### Матрица роль × эндпоинт

**98 эндпоинтов × 7 ролей = 686 запросов.** Каждая ячейка — `ожидаемый/фактический HTTP-код` после исправлений. Данные сняты `Qa04RoleMatrixIntegrationTest` на настоящем Spring Boot + PostgreSQL 16/Testcontainers: без mock контроллеров, use case, репозиториев или access service. Отдельный тест сравнивает таблицу с `RequestMappingHandlerMapping`, поэтому пропуск нового `/api/v1/**` endpoint ломает проверку. Фикстуры и ожидаемые правила: `sport-platform/src/test/resources/security/qa04-endpoints.tsv`; повторный прогон создаёт `sport-platform/target/qa04-matrix.tsv` с наблюдёнными кодами.

Организатор-член имеет global organizer и memberRole=organizer, **не owner**. Operator/judge — члены целевой организации; organizer другой организации имеет членство и событие только там. Participant владеет обычной заявкой. IDs в URL и теле указывают на существующее целевое событие/дисциплину/заплыв/заявку. Каждая ячейка выполняется с новой фикстурой; мутации откатываются. Body валиден, состояние подбирается для успешной операции. Исключения: создание повторного платежа ожидаемо 409; webhook получает намеренно невалидное тело и 400. `/organizer/events` и общие списки могут возвращать 200 с отфильтрованными данными; запрет отдельной чужой организации скрывается через 404.

| Эндпоинт | Аноним | participant | judge | operator | organizer-член | organizer другой орг. | platform_admin |
|---|---|---|---|---|---|---|---|
| `DELETE /api/v1/event-disciplines/{id}` | 401/401 | 403/403 | 403/403 | 204/204 | 204/204 | 403/403 | 204/204 |
| `DELETE /api/v1/event-sessions/{id}` | 401/401 | 403/403 | 403/403 | 204/204 | 204/204 | 403/403 | 204/204 |
| `DELETE /api/v1/users/{userId}` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 204/204 |
| `DELETE /api/v1/users/{userId}/roles/{roleCode}` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 204/204 |
| `GET /api/v1/admin/ping` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `GET /api/v1/age-group-presets` | 401/401 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/auth/me` | 401/401 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/competition-units/{unitId}` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 403/403 | 200/200 |
| `GET /api/v1/discipline-templates` | 401/401 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/event-disciplines/{eventDisciplineId}/competition-units` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 403/403 | 200/200 |
| `GET /api/v1/event-disciplines/{id}/competition-units/validate` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `GET /api/v1/event-disciplines/{id}/results` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 403/403 | 200/200 |
| `GET /api/v1/events` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `GET /api/v1/events/{eventId}/disciplines` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 403/403 | 200/200 |
| `GET /api/v1/events/{eventId}/registrations/export` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `GET /api/v1/events/{eventId}/registrations/import-template` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `GET /api/v1/events/{eventId}/sessions` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 403/403 | 200/200 |
| `GET /api/v1/events/{id}` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 403/403 | 200/200 |
| `GET /api/v1/organization-registration-requests/me/pending` | 401/401 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/organizations` | 401/401 | 403/403 | 403/403 | 403/403 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/organizations/{id}` | 401/401 | 403/403 | 403/403 | 403/403 | 200/200 | 404/404 | 200/200 |
| `GET /api/v1/organizations/{id}/members` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `GET /api/v1/organizer/events` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/payments/review/registrations/{registrationId}` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `GET /api/v1/public/competition-units/{unitId}/results` | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/public/events` | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/public/events/{eventId}/disciplines/{eventDisciplineId}/results` | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/public/events/{slug}` | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/public/events/{slug}/athletes` | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/public/events/{slug}/live` | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/public/events/{slug}/registration-form` | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/public/events/{slug}/results` | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/public/events/{slug}/schedule` | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/public/events/{slug}/start-list` | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/registrations` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `GET /api/v1/registrations/me` | 401/401 | 200/200 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 |
| `GET /api/v1/registrations/{registrationId}` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `GET /api/v1/registrations/{registrationId}/payments` | 401/401 | 200/200 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 |
| `GET /api/v1/regulation-templates` | 401/401 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/regulation-templates/{id}` | 401/401 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/regulation-versions` | 401/401 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/regulation-versions/{id}` | 401/401 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/sports` | 401/401 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/users/{userId}` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `GET /api/v1/venues` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/venues/{id}` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `PATCH /api/v1/competition-unit-entries/{id}` | 401/401 | 403/403 | 403/403 | 204/204 | 204/204 | 403/403 | 204/204 |
| `PATCH /api/v1/event-disciplines/{id}` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `PATCH /api/v1/event-sessions/{id}` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `PATCH /api/v1/registrations/{id}` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `PATCH /api/v1/users/{userId}` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `POST /api/v1/auth/login` | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `POST /api/v1/auth/register` | 201/201 | 201/201 | 201/201 | 201/201 | 201/201 | 201/201 | 201/201 |
| `POST /api/v1/competition-units` | 401/401 | 403/403 | 403/403 | 201/201 | 201/201 | 403/403 | 201/201 |
| `POST /api/v1/competition-units/{id}/finish` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/competition-units/{id}/start` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/competition-units/{unitId}/entries` | 401/401 | 403/403 | 403/403 | 201/201 | 201/201 | 403/403 | 201/201 |
| `POST /api/v1/competition-units/{unitId}/publication` | 401/401 | 403/403 | 204/204 | 204/204 | 204/204 | 403/403 | 204/204 |
| `POST /api/v1/competition-units/{unitId}/recalculate` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/competition-units/{unitId}/results` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/discipline-templates` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `POST /api/v1/event-disciplines/{id}/competition-units/publish` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/event-disciplines/{id}/generate-competition-units` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/events` | 401/401 | 403/403 | 403/403 | 403/403 | 201/201 | 403/403 | 201/201 |
| `POST /api/v1/events/{eventId}/competition-units/publish` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/events/{eventId}/disciplines` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/events/{eventId}/disciplines/{disciplineId}/publish` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/events/{eventId}/generate-competition-units` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/events/{eventId}/registrations/import` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/events/{eventId}/schedule/recalculate` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/events/{eventId}/sessions` | 401/401 | 403/403 | 403/403 | 201/201 | 201/201 | 403/403 | 201/201 |
| `POST /api/v1/events/{id}/close-registration` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/events/{id}/complete` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/events/{id}/open-registration` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/events/{id}/publish` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/organization-registration-requests` | 401/401 | 201/201 | 201/201 | 201/201 | 201/201 | 201/201 | 201/201 |
| `POST /api/v1/organization-registration-requests/{id}/approve` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `POST /api/v1/organization-registration-requests/{id}/reject` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `POST /api/v1/organizations` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 201/201 |
| `POST /api/v1/organizations/{id}/members` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 201/201 |
| `POST /api/v1/payments/webhooks/yookassa` | 400/400 | 400/400 | 400/400 | 400/400 | 400/400 | 400/400 | 400/400 |
| `POST /api/v1/payments/{paymentId}/confirm` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/public/events/{slug}/registrations` | 401/401 | 200/200 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 |
| `POST /api/v1/registrations` | 401/401 | 200/200 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 |
| `POST /api/v1/registrations/{id}/resubmit` | 401/401 | 200/200 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 |
| `POST /api/v1/registrations/{id}/withdraw` | 401/401 | 200/200 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/registrations/{registrationId}/mark-paid` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/registrations/{registrationId}/payments` | 401/401 | 409/409 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 |
| `POST /api/v1/registrations/{registrationId}/review` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/regulation-templates` | 401/401 | 403/403 | 403/403 | 403/403 | 200/200 | 200/200 | 200/200 |
| `POST /api/v1/regulation-templates/{id}/versions` | 401/401 | 403/403 | 403/403 | 403/403 | 200/200 | 200/200 | 200/200 |
| `POST /api/v1/regulation-versions/{id}/publish` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `POST /api/v1/results` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 403/403 | 200/200 |
| `POST /api/v1/sports` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `POST /api/v1/users` | 201/201 | 201/201 | 201/201 | 201/201 | 201/201 | 201/201 | 201/201 |
| `POST /api/v1/users/{userId}/roles` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 201/201 |
| `POST /api/v1/venues` | 401/401 | 403/403 | 403/403 | 403/403 | 201/201 | 201/201 | 201/201 |
| `PUT /api/v1/events/{eventId}/live` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 403/403 | 200/200 |

### Дополнительное покрытие

- IDOR: чужие eventId, disciplineId, unitId, registrationId, entryId дают 403 и не изменяются; неотфильтрованный список заявок содержит только доступные события. Подмена дочернего ID в URL собственного события также отклоняется: публикация чужой дисциплины/назначение чужой заявки/foreign currentUnitId — 400, foreign result entry — 403. Обычный participant не снимает чужую заявку. Исключение захвата импортированного профиля — QA04-10, отдельно воспроизведено.
- Проверки `EventAccessService.assertCanManage`: часть контроллеров вызывает напрямую, часть — через `EventSessionService`, `UpdateEventDisciplineUseCase`, scheduling use case, `ResultsAccessService`, `PaymentReviewAccess`, `ChangeRegistrationUseCase`. Проверено не только поиском текста в контроллере, но и отказами в матрице на реальной БД. Создание события проверяет права на организацию. Общие справочники и списки организаций проверяются своей политикой, так как eventId у них нет.
- Саморегистрация не принимает injected `roles`, `status`, `organizationId`, `memberRole`: в БД только participant, членства нет. Управление memberRole разрешено owner или platform_admin, обычные organizer/operator/judge/participant и чужой owner получают 403; неизвестный memberRole=platform_admin отклонён, назначение членства не выдаёт глобальную роль.
- JWT: срок 7200 секунд, подпись, issuer, exp, отсутствие/пустой Authorization, malformed/forged/expired, inactive/blocked/deleted user, отозванная роль; JSON 401/403 и отсутствие session cookie. `JWT_SECRET` приложения только из окружения; ключи в test-profile и новых JWT-тестах явно тестовые.
- Все 10 публичных GET-маршрутов, включая legacy unit/discipline results и registration-form, проверены с ПДн-маркерами в snapshots/БД. Нет email, phone, полной birthDate, address, password или participantSnapshot. Публичный DTO площадки не содержит адрес. SQL/стек не попадают в ответ общего 500.
- Native SQL: slug и «Найти себя» с `' OR 1=1 --`, `'; DROP TABLE profiles; --`, UNION, `%`, `_`, обратным слэшем. Slug отклоняется 400/404, поиск возвращает пустой массив, таблица/профили сохранены. Запросы параметризованы. Поиск длиной 129 символов возвращает 400.
- XSS: `PublicSecurity.test.tsx` рендерит вредоносные title, lastName, club через реальные PrintHeader/HeatProtocol/ResultsTable. Они видны как текст; script/img/svg и onerror/onload DOM-узлы отсутствуют. `dangerouslySetInnerHTML`/`innerHTML` в исходниках не обнаружены.
- Excel: >5 МБ отклоняется до парсинга; валидная OOXML zip-бомба с сильным сжатием отклоняется POI, без вызова сохранения; FORMULA-cell отклонён, строка с `=HYPERLINK(...)` остаётся строкой. Существующие проверки 5000 строк, размеров полей, формата/типа/дисциплин остаются включёнными. Экспорт пишет строки через `setCellValue(String)`, формулы не вычисляет.
- CORS: разрешён только явно заданный APP_CORS_ALLOWED_ORIGINS, evil origin, localhost вне списка и origin=null получают 403 без ACAO; wildcard/credentials не включены. Actuator health/info открыт, прочее 401/403; disabled env для admin — 404.
- `infra/Caddyfile`: HSTS max-age=31536000/includeSubDomains, nosniff, SAMEORIGIN, Referrer-Policy, Permissions-Policy; X-Frame-Options выполняет требование защиты фрейминга, CSP не настроен. У API Spring Security дополнительно проверены nosniff/DENY. Git содержит только `.env.example`, `.env.prod.example`, frontend `.env.demo` с публичными VITE-настройками, **не секретный .env**. Пароли/JWT не логируются явно кодом login/filter; сидинговые скрипты читают пароли из окружения. Просмотр реальных журналов стенда не выполнялся.

### Проверки перед PR

```bash
cd sport-platform
mvn -q test
mvn -q verify -DskipITs=false
cd frontend
npm test
npm run build
npm run lint
```

Итог: `mvn -q test` — exit 0, включая **41 тест + матрица 98 эндпоинтов × 7 ролей (686 кейсов)**, матрицу и обе архитектурные проверки; `npm test` — 3 Node-теста + 42 Vitest-теста, всё зелёное; `npm run build` — exit 0; `npm run lint` — exit 0, 22 существующих предупреждений вне изменений QA04. `git diff --check` — чисто.

Docker доступен; backend прогон выполнен с PostgreSQL 16, не только unit-тестами. Использован установленный wrapper `/workspace/tools/mvn` (Maven 3.9.11, Java 21). В teardown полного Maven-прогона остаются сообщения о закрытой БД и принудительном завершении fork JVM через 30 секунд после System.exit(0); это также воспроизводилось на исходном main, все Surefire test suites без failures/errors и код Maven 0. Исходные четыре намеренно красных *IT не входили в этот набор проверок; после M01 осталось три, QA04-07 перенесён в обычный прогон. По ревью добавлены 7 unit-кейсов LoginUseCase и frontend-регрессия QA05-02.

Команда для **оставленных дефектов**, намеренно красная (на текущем main QA04-06; QA04-08 закрыт M02, QA04-10 зелёный):

```bash
cd sport-platform
mvn -q test -Dtest=Qa04KnownDefectsIT
```

Актуализация раунда 2: M02 слит (#29), QA04-08 закрыт; QA04-10 проверяет signup 409, отсутствие присвоения профиля/нового аккаунта и отказ withdraw чужой заявки, он зелёный. QA04-06 остаётся красным: bootstrap из APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD теперь реализован, но известный V020 admin автоматически не отключается. Surefire исключает *IT, Failsafe исключает known-defect.

### Правки по ревью 1

- LoginUseCase обрабатывает неверные/отсутствующие credentials и ограничения аккаунта; для DisabledException после M01 отдельно проверяет пароль. Unit-тесты с настоящими AuthController/GlobalExceptionHandler подтверждают 500 internal_error для InternalAuthenticationServiceException/AuthenticationServiceException, 403 для отключённого аккаунта с верным паролем, 401 для неверного пароля и прочих ограничений.
- Qa04KnownDefectsIT помечен `@Tag("known-defect")`; Failsafe исключает этот тег. Владельцы: QA04-06 → M09 (bootstrap admin реализован без миграции; отключение seed — V035+), QA04-08 закрыт M02; QA04-07 закрыт M01 и перенесён в Qa04InputIntegrationTest, QA04-10 → отдельная задача «Подтверждение владения импортированным профилем». Тесты по-прежнему проверяют безопасное поведение; после раунда 2 при явном запуске падает только 06, 10 зелёный.
- Из TestPropertySource удалён DEMO_SEED=false: приложение не читает эту переменную; фикстура задаёт только разрешённый CORS origin.
- AGENTS.md п.7 требует добавлять каждый новый `/api/v1/**` маршрут в TSV матрицы.
- QA05-02 (major для демо), шаги: войти sec@/operator, открыть кабинет события. До исправления GET `/venues` возвращал 403 и прерывал Promise.all; ожидаемо по ТЗ §1/§6.1 секретарь открывает кабинет. Исправлено в `organizations/web/VenueController.java:49`: GET `/venues` и `/venues/{id}` доступны admin/organizer/operator/judge, participant получает 403, создание площадок сохраняет прежние права. `frontend/src/api/organizer.api.ts:24`: getVenues().catch(() => []) сохраняет event/disciplines при отказе справочника. Реальные коды покрыты матрицей; `organizer.api.test.ts` воспроизводит 403 справочника и успешную загрузку кабинета.

## Интеграция с M01 (ревью 2)

После ребейза M01 на QA04 (#16, `39456a2`) полный `mvn -q test` с Docker/PostgreSQL 16: **1088 тестов, 0 failures/errors/skipped**. Матрица расширена до **104 маршрутов × 7 ролей = 728 кейсов**, плюс проверка точного покрытия всех `/api/v1/**` маппингов. Все `Qa04RoleMatrixIntegrationTest`, `Qa04InputIntegrationTest`, `Qa04DataAccessIntegrationTest`, `Qa04JwtIntegrationTest`, `AdminPanelIntegrationTest` и `ModulithArchitectureTest` зелёные. Frontend: **51 Vitest-тест** и Node test runner, lint и build — exit 0; предупреждения линтера сохранены.

- QA04-07 закрыт M01; его тест перенесён из known-defect IT в обычный `Qa04InputIntegrationTest`. На том историческом прогоне оставались QA04-06/08/10; теперь QA04-08 закрыт M02, QA04-10 — раундом 2, красным остаётся QA04-06.
- Неверный пароль отключённого аккаунта даёт общий 401 `identity.invalid_credentials`; 403 `identity.user_blocked` возвращается только после проверки верного пароля. Инфраструктурные ошибки остаются 500.
- Анонимный POST `/users` с полем roles запрещён (403 `identity.access_denied`, пользователя нет); без roles поля status/memberRole/organizationId игнорируются, выдаётся только participant без членств.
- M01 сохраняет один SQL-запрос статуса и глобальных ролей на JWT-запрос; QA04 issuer/exp и JSON 401 для malformed/expired/invalid JWT сохранены. Цепочка запроса выполняется вне блока обработки ошибок аутентификации.
- Миграции QA04 не менялись; единственная миграция M01 — V028.

## Не удалось проверить и почему

- Реальный https://sportgate.pro не изменялся и не использовался для атаки/сидинга: факты выше получены из изолированных локальных PostgreSQL/Testcontainers и тестового frontend. Реальные HTTP-заголовки Caddy, production-переменные, журналы с редактированием секретов, распределённый rate-limit и поведение при внешней нагрузке не измерены; инфраструктура проверена по файлам и локальным тестам.
- Интеграция с настоящей YooKassa и проверка происхождения валидного webhook не выполнялись: в матрице проверены публичность и отклонение malformed payload. Внешние платёжные реквизиты не использовались.
- Браузерная проверка XSS проведена в jsdom, а не на мобильном устройстве/Caddy. Установка PWA, печать и весь сценарий US-1…US-7 — вне области QA04 и не выдаются за пройденные.
- Дальнейшая политика паролей, самостоятельная смена/восстановление, безопасный admin-bootstrap и подтверждение владения импортированным профилем требуют отдельного продуктового/архитектурного решения. Открытые риски перечислены с воспроизводящими тестами, а не закрыты допущениями.


## Закрытие M02 (13.10.2026)

- QA04-08 перенесён без ослабления проверки из `Qa04KnownDefectsIT` в обычный `Qa04InputIntegrationTest`. QA04-07 уже перенесён M01; его проверка короткого пароля и отсутствия созданного аккаунта сохранена. Остались два known-defect IT: QA04-06/QA04-10. Предыдущие разделы с тремя красными тестами описывают прогон до M02.
- Добавлены V029 `password_reset_tokens` и V030 `refresh_tokens`; существующие миграции, включая V028 M01, не изменены. Только SHA-256 хеши токенов сохраняются в БД; raw-токены генерируются из 32 случайных байт. Reset действует 1 час, refresh — 30 дней. При повторном запросе reset предыдущие ссылки становятся недействительными.
- В матрицу `qa04-endpoints.tsv` добавлены все шесть новых endpoint mappings: change-password, password-reset/request, password-reset/confirm, refresh, logout, logout-all. Logout/refresh доступны по владению refresh-токеном; смена пароля и logout-all требуют авторизации. `/auth/login` возвращает дополнительный `refreshToken`.
- Ротация refresh и подтверждение reset защищены транзакциями и блокировкой пользователя, затем токена. Конкурентные запросы имеют только одного победителя; повторное предъявление уже отозванного refresh дополнительно отзывает все refresh пользователя, включая пару победителя гонки. Смена/восстановление пароля, включая существующий административный сброс M01, отзывают refresh-токены и reset-ссылки пользователя.
- `app.jwt.access-ttl` позволяет включить `PT1H`; по умолчанию сохранён прежний `PT2H` для демо. `expiresIn` соответствует выбранному TTL. Logout/logout-all отзывают refresh-токены; уже выданный access JWT действует до exp. Мгновенный отзыв access JWT не добавлялся.
- Лимит login не затрагивает публичное live, импорт и прочие API. Параметры `app.auth.login.max-failures` / `APP_AUTH_LOGIN_MAX_FAILURES` (default 5) и `app.auth.login.window` / `APP_AUTH_LOGIN_WINDOW` (default `PT15M`) настраиваются без пересборки. Успех сбрасывает только счётчик email, IP сохраняется. На стенде профиль `prod` уже включает `server.forward-headers-strategy: native`: Tomcat `RemoteIpValve` берёт IP клиента из `X-Forwarded-For`, который добавляет Caddy; docker-сеть входит в `internal-proxies`, поэтому `remoteAddr` — адрес клиента. Без этой настройки (локально, тесты) используется прямой `remoteAddr`, присланный клиентом заголовок не доверяется. Код обработки IP не менялся. При нескольких серверах нужен Redis; рестарт одного сервера очищает Caffeine. Хранилище ограничено 100 000 ключами, вытеснение при превышении сбрасывает соответствующие счётчики.
- До M05 используется интерфейс `common.MailSender` и `LoggingMailSender`: письмо на русском, ссылка `${APP_PUBLIC_URL}/reset-password?token=…` в INFO-логе с пометкой DEV. Это временная доставка для разработки, требуется SMTP/notifications для настоящей рассылки. Лог содержит одноразовый reset-токен и должен быть доступен только разработчикам стенда.
- Добавлены интеграционные проверки M02 на настоящем PostgreSQL без внешней тестовой транзакции: политика пароля и bcrypt-лимит 72 байта, ошибки текущего пароля, нераскрывающий 202, хеши/TTL, одноразовый/истёкший/заменённый reset, блокировка refresh, ротация, logout/logout-all, конкурентные запросы, ограничение по email/IP и сброс окна с Clock. Добавлены Vitest-проверки форм, хранения/обновления сессии, объединения запросов refresh, повторного 401, скачивания и гонки refresh/logout.
- Итоговый последовательный прогон M02: `mvn -q test` на PostgreSQL 16/Testcontainers — 1 197 тестов в 46 наборах, 0 failures/errors/skipped; в том числе 18 сценариев M02 и 771 проверка матрицы (110 endpoints × 7 ролей + проверка полноты). Архитектурные проверки Modulith прошли. Frontend: `npm test` — Node-набор и 86 Vitest-тестов, `npm run build`, `npm run lint` — успешно (lint содержит существующие предупреждения). Проверялся локальный код; production-стенд не использовался.


### Правки по ревью 1 M02

- В compose добавлен только `APP_PUBLIC_URL` для API с fallback `https://${DOMAIN}`, в production env example — `https://sportgate.pro`. Остальные строки compose сохранены для совместимости с PR #25.
- Лимит настраивается свойствами/окружением; успех сбрасывает email, без сброса IP. Регрессии проверяют 4 ошибки → успех → новые ошибки, отдельную конфигурацию 2 ошибки / `PT2M`, сохранение IP-счётчика и отсутствие сброса на неудачных/инфраструктурных путях.
- Повторное предъявление отозванного refresh отзывает все refresh пользователя. Отдельный `InvalidRefreshTokenException` с `noRollbackFor` на refresh сохраняет отзыв при ответе 401; интеграционный тест проверяет commit без внешней тестовой транзакции. Гонка ротации по-прежнему даёт 200+401, затем нет живых refresh.
- `@Primary` у identityClock удалён; `defaultCandidate=false` оставляет часы доступными через `@Qualifier` и исключает неоднозначность Clock для Spring Modulith.
- DTO проверяют непустой новый пароль с русским сообщением. Фронт показывает ожидание при 429 из `Retry-After`; после смены пароля вызывает logout и показывает «Пароль изменён. Войдите заново с новым паролем».
- Контракты внесены в ТЗ §5.9; описание IP исправлено для prod/локального режима. Слияние и деплой — после демо 15.10.2026 (с 16.10).
- Проверки после ревью 1: `mvn -q test` с Docker/PostgreSQL 16 — 1 213 тестов в 47 наборах, 0 failures/errors/skipped, код Maven 0. В том числе 23 сценария AuthHardening, настройка лимита 2/PT2M, 10 LoginUseCase и архитектурные проверки. Frontend: Node-набор и 90 Vitest-тестов, build и lint — успешно. Compose config проверяет APP_PUBLIC_URL=https://sportgate.pro и fallback через DOMAIN без запуска сервисов. Production-стенд не изменялся.

## QA04 раунд 2

Актуальная проверка: [QA04-round2.md](QA04-round2.md). M02 (#29) и M09 (#25) слиты; условные M02-тесты активны в обычном mvn test. QA04-10 закрыт запретом signup-присвоения импортированного профиля. Bootstrap admin из APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD добавлен без миграции, существующий hash никогда не меняется. QA04-06 остаётся открытым в части известного V020 demo-admin: его не блокируем автоматически. QA04-08 закрыт M02. Будущие миграции — V035+ (V031–V034 закреплены за M04–M07). QA04-12/13 закрываются M05 (PR #41, OutboxMailSender). Добавлен QA04-14: email lockout, меры для демо и задача MVP описаны в отчёте.

## S07: членства и приглашения (15.10.2026)

Модель: `docs/decisions/2026-10-15-org-roles.md`. Актуальный TSV содержит 138 эндпоинтов; матрица — 966 запросов и отдельная проверка полноты. В каждой подставной principal фикстура теперь передаёт снимок членств, как фильтр JWT.

Изменения ожиданий: `DELETE /event-disciplines/{id}` для operator — 403 вместо 204; `PATCH /events/{id}` и `POST /events/{id}/archive` — 403 вместо 200. Политика этих трёх строк изменена с `manage` на `organize`. Owner/admin notification-settings не менялись. Состояние `org-member` создаёт **target**, а не одного из акторов матрицы; строки PATCH/DELETE members сохраняют ожидания. Наблюдатель `member` проверяется отдельными интеграционными тестами: GET события — 200, операции — 403 даже при глобальном operator.

Добавлены шесть строк S07 (приглашения и `/organizations/mine`) и owner PATCH реквизитов организации. Состояние `invitation` создаёт настоящее приглашение с хешем токена; accept аутентифицированными стандартными акторами даёт 403 из-за несовпадения email. `S07OrganizationRolesIntegrationTest` использует настоящие JWT и проверяет роли без глобальных назначений, смену/удаление членства без перевыпуска JWT, чужую организацию, цикл outbox → accept, регистрацию нового адресата, дубликаты, отзыв и ленивое истечение. Счётчик Hibernate фиксирует два SQL для `/auth/me`, включая одно чтение аутентификации с членствами.

После слияния (только после демо 19.10 и ретро) обязательны повтор QA04 и QA05 на staging, проверка входа и доступа демо-аккаунтов. Локальные тесты не заменяют этот прогон.
