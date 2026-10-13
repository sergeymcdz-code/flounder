# QA04 — раунд 2: security re-audit этапа 1

Проверка 13.10.2026, правки ревью PR #44. Ветка qa/QA04-round2 перебазирована на origin/main **028dc5e**: M02 слит (#29), M09 слит (#25), учтён #39. Условные M02-тесты активны в обычном mvn test. Требования: QA00/QA04, AGENTS.md, ТЗ §1/§5/§8, задания M01/M02/M09. Стенд проверен только статически по конфигам.

QA04-10 закрыт; QA04-08 закрыт M02. Для QA04-06 добавлен bootstrap admin (40 строк, без миграции), но известный admin из V020 автоматически не блокируется, поэтому этот остаточный риск открыт. QA04-11/12/13 относятся к main; QA04-12/13 закрываются M05 (PR #41, OutboxMailSender). Добавлен QA04-14 minor (major для демо-дня). Намеренно красные IT tagged known-defect исключены из обычного прогона.

## Дефекты и задачи

Пути Java относительно `sport-platform/src/main/java/com/acme/sportplatform/`; номера строк M02 относятся к слитой реализации.

| ID | Severity / статус | Где и воспроизведение | Ожидаемо / фактически | Исправление или задача-владелец | Автотест |
|---|---|---|---|---|---|
| QA04-06 | **blocker, открыт** | Чистая БД после V020; POST `/auth/login` с admin из демо-ТЗ при отключённом демо-сиде. `db/migration/V020__seed_fixes.sql:10`, `infra/compose.prod.yaml:52`. | QA04 п.6, ТЗ §8: известных admin-credentials вне явно включённого демо нет. По-прежнему **ожид.401 / факт.200**. DEMO_SEED не читается сервером. | **Bootstrap реализован в этом PR**; M09 остаётся отключение/ручная ротация известного seed-admin. Описание ниже. Старые миграции не менять. | `Qa04KnownDefectsIT.qa04_06_seedDisabledMustNotAllowPublicDemoAdminCredentials`, красный |
| QA04-10 | **major, исправлен** | Снять user_id импортированного профиля, анонимно зарегистрировать аккаунт с совпадающими ФИО/датой рождения/полом. `identity/application/CreateUserUseCase.java:66`. | QA04 п.1–2: чужой профиль не присваивается. До фикса signup **ожид.409 / факт.201**; QA03 закрывал только конфликт публичной заявки существующего аккаунта. После: **409/409** на `/users` и `/auth/register`, нет нового аккаунта, профиль без владельца, чужая заявка CONFIRMED. | Проверка до создания/хеширования пользователя; Небольшой guard до сохранения пользователя, меньше 40 строк; текст и создание нового профиля уточнены по ревью. Для законного присоединения остаётся отдельная задача «Подтверждение владения импортированным профилем». | `Qa04Round2AdminIntegrationTest.qa04_10_unverifiedSignupCannotClaimImportedProfile`; legacy known-defect тест сохранён и обновлён на безопасный 409 |
| QA04-11 | **minor, main** | Пять неверных login с разными email и remoteAddr `2001:db8:42::1`; шестой с эквивалентным `2001:0db8:0042:0000:0000:0000:0000:0001`. `identity/application/LoginAttemptLimiter.java:37,44`. | M02 §5.8: один IP — одна квота. MockMvc: **ожид.429 / факт.401**. IP хранится строкой без нормализации. Возможность подачи таких форм через реальный Tomcat/Caddy не подтверждена; это дефект нормализации, а не доказанный обход на стенде. | **M02: канонизация числовых IPv4/IPv6 без DNS**, одинаковые ключи для эквивалентных адресов, тесты форм и регистра hex. Задача M02-followup; сетевой обход не подтверждён. | `Qa04Round2M02KnownDefectsIT.equivalentIpv6AddressesMustShareTheLoginLimit`, красный |
| QA04-12 | **major, main; закрывается M05 #41** | POST reset-request известному активному email; штатный LoggingMailSender пишет полный body со ссылкой/сырой credential. `common/LoggingMailSender.java:14`, `identity/application/PasswordService.java:72`. | QA04 п.6: логи без токенов/паролей. Ожидалось отсутствие `reset-password?token=`; фактически есть. LoggingMailSender без ограничения профиля, поэтому компонент активен также в prod. | **M02/M05: ограничить DEV sender dev/test-профилем; production sender/очередь не логирует body, reset URL и email**. Требуется согласовать отправку писем и профили; нельзя просто убрать единственный способ доставки. **Закрывается M05 (PR #41, OutboxMailSender)**. | `Qa04Round2M02KnownDefectsIT.resetCredentialsMustNotAppearInDefaultApplicationLogs`, красный |
| QA04-13 | **minor, main; закрывается M05 #41** | Подменить MailSender на бросающий исключение, вызвать reset-request для неизвестного и известного email. `identity/application/PasswordService.java:57–74`. | M02 §5.8: валидный reset-request всегда 202, не раскрывает наличие аккаунта. **Неизвестный 202, известный 500**. Проверен сценарий отказа зависимости; текущий DEV sender не имитирует SMTP. | **M02/M05: асинхронная доставка с одинаковым ответом/поведением при отказе**, операционная диагностика без credentials. **Закрывается M05 (PR #41, OutboxMailSender)**; связать с оценкой timing. | `Qa04Round2M02KnownDefectsIT.mailDeliveryFailureMustNotEnumerateRegisteredUsers`, красный |
| QA04-14 | **minor (major для демо-дня), main** | Пять неверных login известного email от разных IP, затем правильный пароль от нового IP. `identity/application/LoginAttemptLimiter.java:37,43`. | Email lockout: любой атакующий блокирует вход известного аккаунта на 15 минут; фактически 429 даже с верным паролем. | Демо: войти заранее, refresh-сессии живут 30 дней, на день демо **APP_AUTH_LOGIN_MAX_FAILURES=20**. MVP: капча/лимит по IP+email или прогрессивная задержка вместо блокировки email. | `Qa04Round2M02KnownDefectsIT.emailAttacksMustNotLockOutTheLegitimateUser`, ожидаемо красный |

QA04-07 закрыт M01: минимальная длина и bcrypt byte limit проверяются обычными тестами. QA04-08 закрыт слитым M02: email/IP limiter отвечает 429 после пяти ошибок. Новые уточнения лимитера отражены отдельно в QA04-11.

### Bootstrap admin и остаток QA04-06

Реализован `identity/infrastructure/AdminBootstrap.java`: @Component/ApplicationRunner, транзакционный run, APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD с пустыми default. Если любой параметр пуст — ничего; если email уже существует — лог «bootstrap admin exists, password untouched» и return до валидации/encode. Иначе PasswordPolicy.validate, active user с bcrypt и глобальная роль platform_admin. Лог без email/пароля. Compose передаёт обе переменные, .env.prod.example содержит только закомментированные пустые настройки.

AdminBootstrapIntegrationTest проверяет создание, повторный run с другим паролем без изменения hash, пустые параметры. Миграций нет; существующий admin@flounder.demo не блокируется автоматически. Для QA04-06 остаётся задача M09 по ручной ротации известного admin на стенде и безопасному отключению обязательного демо-сидирования для новых production-БД. Любая будущая миграция — **V035+ (V031–V034 закреплены за M04–M07)**. Серверный bootstrap не заменяет ротацию существующей учётки и не является процедурой подтверждения владения профилем.

## Матрица M01 и M02

Все числа получены MockMvc + реальный PostgreSQL 16/Flyway, а не выведены только из аннотаций. Ячейка **ожидаемый/фактический**. Организатор — член своей организации с memberRole=organizer, чужой организатор — owner другой организации. На owner-only эндпоинтах обычному organizer ожидается 403; отдельный положительный тест повышает его до owner своей организации и проверяет 200.

Историческая матрица M01 до M02: **104 эндпоинта × 7 ролей = 728 кейсов**, плюс точная проверка соответствия всем controller mappings. Шесть новых строк M01 уже внесены в TSV самим M01; повторно проверены здесь. Полная фактическая матрица — ниже. Артефакт прогона `sport-platform/target/qa04-matrix.tsv`.

| Эндпоинт | Аноним | Participant | Judge | Operator | Organizer | Чужой organizer | Platform admin |
|---|---|---|---|---|---|---|---|
| `GET /api/v1/users` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `GET /api/v1/roles` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `PATCH /api/v1/users/{userId}/password` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 204/204 |
| `PATCH /api/v1/organizations/{id}/members/{userId}` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `DELETE /api/v1/organizations/{id}/members/{userId}` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 204/204 |
| `GET /api/v1/organization-registration-requests` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |

### M02 (слит, PR #29)

Дополнительно 6 маршрутов × 7 ролей = 42 кейса; собственная матрица получает реальные refresh/reset tokens. Публичный confirm/refresh/logout требует владения токеном, роль аккаунта не является разрешением. Анонимная ячейка этих маршрутов также содержит валидный тестовый токен. Все остальные тела валидны. Артефакт `target/qa04-round2-m02-matrix.tsv`. TSV main содержит 110 маршрутов; полный тест controller mappings и условный M02-класс выполняются в mvn test.

| Эндпоинт | Аноним | Participant | Judge | Operator | Organizer | Чужой organizer | Platform admin |
|---|---|---|---|---|---|---|---|
| `POST /api/v1/auth/change-password` | 401/401 | 204/204 | 204/204 | 204/204 | 204/204 | 204/204 | 204/204 |
| `POST /api/v1/auth/logout` | 204/204 | 204/204 | 204/204 | 204/204 | 204/204 | 204/204 | 204/204 |
| `POST /api/v1/auth/logout-all` | 401/401 | 204/204 | 204/204 | 204/204 | 204/204 | 204/204 | 204/204 |
| `POST /api/v1/auth/password-reset/confirm` | 204/204 | 204/204 | 204/204 | 204/204 | 204/204 | 204/204 | 204/204 |
| `POST /api/v1/auth/password-reset/request` | 202/202 | 202/202 | 202/202 | 202/202 | 202/202 | 202/202 | 202/202 |
| `POST /api/v1/auth/refresh` | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |

### Базовые 104 маршрута (плюс 6 M02 выше)

| Эндпоинт | Аноним | Participant | Judge | Operator | Organizer | Чужой organizer | Platform admin |
|---|---|---|---|---|---|---|---|
| `DELETE /api/v1/event-disciplines/{id}` | 401/401 | 403/403 | 403/403 | 204/204 | 204/204 | 403/403 | 204/204 |
| `DELETE /api/v1/event-sessions/{id}` | 401/401 | 403/403 | 403/403 | 204/204 | 204/204 | 403/403 | 204/204 |
| `DELETE /api/v1/organizations/{id}/members/{userId}` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 204/204 |
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
| `GET /api/v1/organization-registration-requests` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
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
| `GET /api/v1/roles` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `GET /api/v1/sports` | 401/401 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/users` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `GET /api/v1/users/{userId}` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `GET /api/v1/venues` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `GET /api/v1/venues/{id}` | 401/401 | 403/403 | 200/200 | 200/200 | 200/200 | 200/200 | 200/200 |
| `PATCH /api/v1/competition-unit-entries/{id}` | 401/401 | 403/403 | 403/403 | 204/204 | 204/204 | 403/403 | 204/204 |
| `PATCH /api/v1/event-disciplines/{id}` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `PATCH /api/v1/event-sessions/{id}` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `PATCH /api/v1/organizations/{id}/members/{userId}` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `PATCH /api/v1/registrations/{id}` | 401/401 | 403/403 | 403/403 | 200/200 | 200/200 | 403/403 | 200/200 |
| `PATCH /api/v1/users/{userId}` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 200/200 |
| `PATCH /api/v1/users/{userId}/password` | 401/401 | 403/403 | 403/403 | 403/403 | 403/403 | 403/403 | 204/204 |
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

## Что проверено и прошло

- M01: не-admin (participant/judge/operator/оба organizer) не меняет свой или чужой status/password/roles, получает 403; БД сохраняет hash/status. Admin не блокирует себя даже смешанным регистром `bLoCkEd`, не удаляет себя и не отзывает собственный platform_admin (403 `identity.cannot_modify_self`). PATCH чужого пользователя администратором меняет status, но поле roles не выдаёт роль. Роли назначаются отдельным защищённым ресурсом.
- M01 IDOR: owner своей организации получает 200 на изменение её memberRole, но 403 на PATCH/DELETE членства той же персоны в чужой организации; обратное направление также 403. Изменение memberRole не меняет глобальную роль, чужое членство сохранено. GET чужого глобального пользователя — 403. Отдельно повторены старые IDOR/JWT/PII/injection тесты полным `mvn test`.
- QA03/QA04-10: публичная заявка существующего аккаунта с совпадением чужого импортированного профиля — 409; оба signup-маршрута теперь также 409 до создания аккаунта. Импортированный профиль и чужая подтверждённая заявка не изменены. Административный/импортный механизм прикрепления профиля этой правкой не переписывается; безопасное подтверждение владения не реализовано.
- M02 refresh: новый opaque token 43 символа, SHA-256 hash в БД; старый после ротации — 401; reuse отзывает замену (тоже 401). Expired/random — 401, blocked user — 403 `identity.user_blocked`. Logout идемпотентен, отзывает одну сессию; logout-all отзывает только собственные сессии, подставленный userId в теле не затрагивает другого пользователя.
- M02 password: неверный currentPassword — 400, слабый новый пароль — 400; смена собственного пароля — 204, собственный refresh отозван, чужой hash не меняется при добавлении userId. Reset подтверждается один раз (204, затем 400), expired/blocked/random tokens отклонены общим 400 `identity.reset_token_invalid`; 16 случайных попыток не дали успешного ответа. Новая reset-request инвалидирует старую ссылку; confirm отзывает refresh. Токены генерируются SecureRandom 256 бит и хранятся SHA-256; 16 попыток проверяют обработку неверного токена, не доказывают исчерпывающее сопротивление перебору.
- M02 enumeration/rate-limit: корректные известный/неизвестный/blocked email и другой регистр email возвращают одинаковый пустой 202 при работоспособной почте. Пять ошибок login с разным регистром одного email дают 401, следующая — 429 + Retry-After; смена реального IP не снимает email-квоту. Поддельный X-Forwarded-For без прокси не меняет IP-квоту; новый email на том же IP также 429. IPv6 представления — исключение QA04-11.
- Reset timing: `resetTimingIsMeasuredWithoutFlakySecurityThreshold` записывает 24 пары локальных измерений в `target/qa04-round2-reset-timing.txt`. Известный email делает дополнительный lock/write/send, неизвестный возвращается после lookup. Итоговые медианы указаны в проверках ниже. Это наблюдение, а не доказательство эксплуатации по сети: MockMvc, общий тестовый transaction, журналирование и JVM влияют на время, сетевые распределения не сняты. Не вводится нестабильный тест по произвольному отношению миллисекунд. Задача M02/M05 по очереди доставки должна также оценить timing и одинаковую обработку lookup.

## M09: статическая проверка слитого PR #25

- `infra/Caddyfile:14–20`: HSTS max-age=31536000/includeSubDomains, nosniff, SAMEORIGIN, Referrer-Policy strict-origin-when-cross-origin, Permissions-Policy запрета camera/microphone/geolocation, удаление Server. HTTPS/HTTP redirect настраивает Caddy автоматически. CSP отсутствует; X-Frame-Options покрывает требование QA04 о защите фрейминга. Фактический набор заголовков ответов и сертификат стенда не проверялись.
- `infra/compose.prod.yaml`: API/Postgres без опубликованных host ports; опубликованы только Caddy 80/443. JWT_SECRET и POSTGRES_PASSWORD обязательны; разрешённый CORS по умолчанию `https://${DOMAIN}`, переменная явно передана api. SENTRY env в PR #25 не содержит встроенного секрета. Дополнительные Caddy sites подключаются оператором; содержимое развёрнутых sites неизвестно.
- `application-prod.yml` → `CorsConfig`: APP_CORS_ALLOWED_ORIGINS разбирается как точные origins, allowCredentials=false, допустимые заголовки ограничены. Повторён существующий интеграционный тест `corsAndActuatorEnforceOriginsAndGlobalRole`: явно разрешённый origin принят, посторонний/localhost/null отклонены 403 без ACAO; API nosniff/DENY присутствуют. Это локальные backend-ответы, не ответы edge.
- Prod `server.forward-headers-strategy=native`: IP после Caddy зависит от доверия Tomcat к proxy. Тест поддельного XFF выполнен с forwarding=none и проверяет режим «без прокси». Доверенные proxy/реальный IP на стенде требуют отдельной приёмки M09; наружный обход IP-квоты через заголовки в режиме native не утверждается.
- В git только примеры env и frontend .env.demo с публичными VITE-настройками; секретного .env нет. `DEMO_SEED` в compose по-прежнему декоративен для сервера — QA04-06. Проверка M09 не считается устранением bootstrap-дефекта.

## Проверки перед PR

```bash
cd sport-platform
mvn -q test
cd frontend
npm test
npm run build
npm run lint
```

Docker доступен; Java 21, Maven 3.9.11, PostgreSQL 16/Testcontainers. M02 слит, условные тесты активны в обычном прогоне. Итоговые численные результаты приведены в разделе «Правки по ревью 1» после завершения проверок.

Явные known-defect прогоны (ожидаемо RED; не входят в обычные test/verify):

```bash
mvn -q test -Dtest=Qa04KnownDefectsIT
mvn -q test -Dtest=Qa04Round2M02KnownDefectsIT
```

QA04-06 RED, QA04-10 GREEN. QA04-11/12/13/14 проверяются отдельно; QA04-12/13 закрываются M05 (PR #41, OutboxMailSender). Исторический локальный reset timing: 24 пары, медианы 3200/1396 мкс, без порога; сетевой вывод не делается.

## Не удалось проверить и почему

- Внешний стенд, реальные SMTP/доставка, сетевое timing-распределение, заголовки Caddy по HTTP, TLS, настройка trusted proxies и канонизация IPv6 Tomcat: задание на M09 требует статическую проверку конфигов; внешние security-запросы не выполнялись.
- M02 и M09 слиты. M05 PR #41 ещё требует отдельной проверки; QA04-12/13 отмечены как закрывающиеся его OutboxMailSender. Нормализация IPv6 — задача M02-followup.
- Bootstrap реализован, но V020 admin не отключён автоматически. Безопасное подтверждение владения и миграционное отключение обязательного demo seed остаются отдельными задачами; запрет signup-захвата не заменяет подтверждение.

## Правки по ревью 1

- Rebase на main с M02 #29, M09 #25 и #39; конфликт QA04-security-rbac.md разрешён с сохранением закрытия M02 и фикса QA04-10.
- Обновлены статусы и резерв миграций V035+; добавлен QA04-14 с мерами демо и задачей MVP.
- RegisterPage различает 409 imported-profile и duplicate-email; добавлен Vitest-кейс. Backend выдаёт согласованный текст об обращении к организатору; убрана мёртвая ветка создания профиля.
- Добавлен 40-строчный AdminBootstrap с APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD и тремя интеграционными проверками. Существующий hash никогда не перезаписывается; миграций и изменений TSV нет.

Проверки после ребейза: полный `mvn -q test` — exit 0, **1290 кейсов, 0 failures/errors/skipped**. `Qa04Round2M02IntegrationTest` активен: **52 кейса, 0 skipped**; полная матрица — **771 кейс (110×7 + coverage)**; AdminBootstrapIntegrationTest — **3 зелёных кейса**. Frontend: **3 Node + 94 Vitest**, test/build/lint — exit 0; существующие lint-предупреждения сохранены. `infra/tests/compose-config-test.sh` и `git diff --check` — зелёные. В teardown остаётся ранее наблюдавшееся сообщение Surefire о завершении fork через 30 секунд после System.exit(0); Maven завершился с кодом 0, suites без failures/errors.

Явный `Qa04Round2M02KnownDefectsIT` — ожидаемо exit 1: **4 failures, 0 errors** (QA04-11/12/13/14). Новый QA04-14 воспроизведён: законный вход после чужих пяти попыток получает 429 вместо требуемого для MVP 200.
