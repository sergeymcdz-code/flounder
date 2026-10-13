# QA04 — безопасность и ролевая модель

Проверка 13.10.2026. База: актуальный `origin/main` (`0a758521b5acbe3f4daaa4f3b5595bf426c2c19d`), ветка `qa/QA04-security-rbac`. QA00 и QA04 прочитаны из `origin/docs/tasks-qa` через `git show` после fetch; требования сверены с `AGENTS.md`, ТЗ (§2, §4, §5, §6, §8), демо-сценарием и `scripts/demo/README.md`.

Итог: **10 дефектов: 1 blocker, 4 major, 5 minor; 6 исправлены, 4 оставлены**. Blocker относится к запуску с отключённым демо-сидом, а не к возможности показать выделенный демо-стенд. Изменений миграций нет; будущие исправления схемы — только V028+. Структура API/UI сохранена; уточнены ответы ошибок и валидация существующих ограничений хранения/bcrypt. Каждое небольшое исправление укладывается в 40 строк производственного кода.

## Дефекты

Коды «до» подтверждены тестами на исходном коде; «после» — регрессиями. Для QA04-02 до исправления MockMvc выбрасывал исключение, поэтому HTTP-код не приписывается этому прогону. Номера строк ниже относятся к итоговой версии. Java-файлы находятся под `sport-platform/src/main/java/com/acme/sportplatform/`.

| ID | Severity | Где и шаги воспроизведения | Ожидаемо по требованиям | Фактически до исправления | Исправление / статус / файл и строка | Автотест |
|---|---|---|---|---|---|---|
| QA04-01 | major | Получить JWT администратора; заблокировать/деактивировать/удалить пользователя или отозвать роль; старым JWT выполнить GET `/api/v1/users/{id}`. | После отключения пользователя 401, после отзыва admin 403. QA04 п.3; роли ТЗ §1. | Во всех четырёх случаях старый токен сохранял admin-доступ, 200, до истечения 2 часов. | **Исправлено:** пользователь и текущие роли проверяются в БД на каждом JWT-запросе. `identity/infrastructure/security/JwtAuthenticationFilter.java:102`. Добавлены 2 чтения БД на JWT-запрос. | `Qa04JwtIntegrationTest.disabledOrDeletedUserCannotUsePreviouslyIssuedAdminToken`, `revokedAdminRoleCannotBeReusedFromOldJwt` |
| QA04-02 | minor | GET `/api/v1/auth/me` с пустым, malformed, поддельным либо истёкшим Bearer. | JSON 401 без стека/SQL, QA04 п.3–4. | Фильтр выбрасывал `InsufficientAuthenticationException` до `ExceptionTranslationFilter`; MockMvc завершался исключением без штатного JSON-ответа. | **Исправлено:** вызов существующего `RestAuthenticationEntryPoint`, 401. `identity/infrastructure/security/JwtAuthenticationFilter.java:132`. | `Qa04JwtIntegrationTest.invalidJwtReturnsJson401`; существующий `JwtAuthenticationFilterTest` сохранён и адаптирован к ответу через entry point, с проверкой причины и отсутствия вызова цепочки |
| QA04-03 | major | Подписать тестовым ключом токен с чужим issuer либо без exp; запросить `/auth/me`. | Принимаются только токены приложения с ограниченным сроком, QA04 п.3. | Оба токена принимались, 200. | **Исправлено:** обязательные issuer и exp. `identity/infrastructure/security/JwtService.java:51`. | `Qa04JwtIntegrationTest.invalidJwtReturnsJson401` (wrong-issuer, no-expiry) |
| QA04-04 | minor | Установить пользователю inactive/blocked; POST `/auth/login` с правильным паролем. | Общий 401, не 500; QA04 п.3–4, вход ТЗ §1. | 500: `DisabledException` не перехватывался веткой `BadCredentialsException`. | **Исправлено:** обрабатываются ошибки аутентификации, сообщение на русском. `identity/application/LoginUseCase.java:41`, `identity/domain/InvalidCredentialsException.java:8`. | `Qa04InputIntegrationTest.loginForDisabledAccountsReturns401WithoutLeakingAccountState` |
| QA04-05 | minor | POST `/users` с phone/middleName/city/countryCode/clubName длиной 300; отдельно пароль из 73 ASCII или 40 кириллических символов (80 UTF-8 байт). | 400 до записи/хеширования; QA04 п.5, ограниченные типы модели. | 500 на ограничениях БД или bcrypt. Длинные ФИО/пол уже отклонялись доменной проверкой; теперь ограничения DTO единообразны. | **Исправлено:** размеры по существующей схеме и предел bcrypt 72 **байта**, без введения новой минимальной политики паролей. `identity/api/CreateUserRequest.java:10`, `:23`. | `Qa04InputIntegrationTest.selfRegistrationRejectsStringsBeyondStorageLimits`, `selfRegistrationRejectsPasswordsBeyondBcryptByteLimit` (без внешней тестовой транзакции — проверен настоящий commit) |
| QA04-06 | blocker | Чистая БД после Flyway, `DEMO_SEED=false`; POST `/auth/login` с демо-admin из ТЗ §1. | Демо-учётные данные недоступны при отключённом сиде; QA04 п.6, ТЗ §1/§8.6. | 200 с admin-JWT. V020 безусловно активирует известный admin-пароль; `DEMO_SEED` из compose нигде не прочитан приложением. | **Оставлено:** вынести демо-bootstrap из обязательных миграций, предусмотреть безопасный admin-bootstrap и ротацию/отключение уже созданного аккаунта. Это меняет bootstrap стенда; нужна отдельная задача и V028+, старые миграции не менять. `sport-platform/src/main/resources/db/migration/V020__seed_fixes.sql:10`, `infra/compose.prod.yaml:52`. Скрипт T10 защищён `DEMO_SEED_ENABLED`, но не управляет V020. | `Qa04KnownDefectsIT.qa04_06_seedDisabledMustNotAllowPublicDemoAdminCredentials` — красный: 401/200 |
| QA04-07 | major | POST `/users` с password=`a` и корректным профилем. | Непустоты недостаточно: нужна согласованная минимальная политика, QA04 п.7. | 201; только `@NotBlank`, минимальной длины/политики нет. | **Оставлено:** согласовать минимальную длину (например 8+) и правила, отразить в API/UI; новые требования к допустимому паролю меняют контракт. bcrypt cost=10 уже есть. Смена/восстановление пароля отсутствуют; вне демо-сценариев, отмечено как ограничение. `identity/api/CreateUserRequest.java:11`, `config/PasswordConfig.java:13`. | `Qa04KnownDefectsIT.qa04_07_oneCharacterPasswordMustNotCreateAnAccount` — красный: 400/201 |
| QA04-08 | minor | 50 неверных POST `/auth/login` подряд с одного IP 192.0.2.44. | Ограничение частоты/429; QA04 п.6: minor для демо, major для MVP. | Все 50 ответов 401; ни API, ни Caddy не содержат rate-limit. | **Оставлено:** определить лимиты по IP/аккаунту, доверенные proxy, хранилище счётчиков и Retry-After; не вводить произвольную политику в малом патче. `identity/web/AuthController.java:42`, `infra/Caddyfile:27`. | `Qa04KnownDefectsIT.qa04_08_repeatedFailedLoginMustEventuallyBeRateLimited` — красный: нет 429 |
| QA04-09 | minor | Admin GET `/actuator/env`, endpoint отключён настройкой приложения. | RBAC пропускает admin, затем 404 отсутствующего endpoint; QA04 п.2/п.4. | 500: `NoResourceFoundException` попадает в общий обработчик 500. | **Исправлено:** отдельный безопасный 404 `common.not_found`; закрытые endpoint по-прежнему 401/403 для остальных. `common/web/GlobalExceptionHandler.java:267`. | `Qa04DataAccessIntegrationTest.corsAndActuatorEnforceOriginsAndGlobalRole` |
| QA04-10 | major | Импортированный профиль без user_id с ФИО/датой/полом и заявкой другого события. Саморегистрировать аккаунт с теми же данными; им выполнить POST `/registrations/{victimId}/withdraw`. | Нельзя получать владение чужим профилем/заявкой без подтверждения; IDOR, QA04 п.1–2, «свои заявки» ТЗ §1/§5.4. | CreateUserUseCase присваивает импортированный профиль новому user_id по external_key; чужая заявка снимается, 200. Глобальная роль остаётся participant — проблема в владении данными. | **Оставлено:** отдельный проверяемый процесс привязки профиля (приглашение/подтверждение организатора или иной proof of ownership), а не совпадение демографических полей. Простое отключение объединения ломает импорт/идемпотентность и требует решения по контракту и данным. `identity/application/CreateUserUseCase.java:57`, `registrations/application/ChangeRegistrationUseCase.java:56`. | `Qa04KnownDefectsIT.qa04_10_importedAthleteCannotBeClaimedByUnverifiedSelfRegistration` — красный: 403/200 |

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
| `GET /api/v1/venues` | 401/401 | 403/403 | 403/403 | 403/403 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/venues/{id}` | 401/401 | 403/403 | 403/403 | 403/403 | 200/200 | 200/200 | 200/200 |
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
cd frontend
npm test
npm run build
npm run lint
```

Итог: `mvn -q test` — exit 0, включая **727 новых зелёных backend-проверок QA04**, матрицу и обе архитектурные проверки; `npm test` — 3 Node-теста + 41 Vitest-тест, всё зелёное; `npm run build` — exit 0; `npm run lint` — exit 0, 22 существующих предупреждений вне изменений QA04. `git diff --check` — чисто.

Docker доступен; backend прогон выполнен с PostgreSQL 16, не только unit-тестами. Использован установленный wrapper `/workspace/tools/mvn` (Maven 3.9.11, Java 21). В teardown полного Maven-прогона остаются сообщения о закрытой БД и принудительном завершении fork JVM через 30 секунд после System.exit(0); это также воспроизводилось на исходном main, все Surefire test suites без failures/errors и код Maven 0. Четыре намеренно красных *IT не входят в эти 727 проверок.

Команда для **четырёх оставленных дефектов**, намеренно красная:

```bash
cd sport-platform
mvn -q test -Dtest=Qa04KnownDefectsIT
```

Наблюдения: DEMO_SEED=false → login admin 200; односимвольный пароль → 201; 50 неудачных login → 50×401 без 429; захват импортированного профиля → withdraw чужой заявки 200. Эти четыре теста содержат ожидаемое безопасное поведение, не отключены и не ослаблены. Репозиторий уже исключает `*IT` из обычного `mvn test`; `mvn verify` или явный запуск `Qa04KnownDefectsIT` будет красным до ремонта QA04-06/07/08/10. Это не означает, что оставшиеся риски устранены.

## Не удалось проверить и почему

- Реальный https://sportgate.pro не изменялся и не использовался для атаки/сидинга: факты выше получены из изолированных локальных PostgreSQL/Testcontainers и тестового frontend. Реальные HTTP-заголовки Caddy, production-переменные, журналы с редактированием секретов, распределённый rate-limit и поведение при внешней нагрузке не измерены; инфраструктура проверена по файлам и локальным тестам.
- Интеграция с настоящей YooKassa и проверка происхождения валидного webhook не выполнялись: в матрице проверены публичность и отклонение malformed payload. Внешние платёжные реквизиты не использовались.
- Браузерная проверка XSS проведена в jsdom, а не на мобильном устройстве/Caddy. Установка PWA, печать и весь сценарий US-1…US-7 — вне области QA04 и не выдаются за пройденные.
- Политика паролей, смена/восстановление, безопасный admin-bootstrap и подтверждение владения импортированным профилем требуют отдельного продуктового/архитектурного решения. Открытые риски перечислены с воспроизводящими тестами, а не закрыты допущениями.
