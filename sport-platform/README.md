# Sport Platform

MVP-платформа для управления спортивными мероприятиями, дисциплинами,
заявками участников, платежами, стартовыми списками и публикацией результатов.

## Возможности MVP

- Управление спортивными событиями и дисциплинами
- Регистрация участников на дисциплины
- Проверка и подтверждение заявок
- Платежи: ручной сценарий и интеграция с YooKassa webhook
- Создание соревновательных единиц: заплывов, матчей, групп и финалов
- Назначение подтверждённых заявок в соревновательные единицы
- Внесение результатов, пересчёт мест и публикация
- Публичный каталог событий и опубликованные результаты без авторизации
- JWT-аутентификация и RBAC

## Технологии

- Java 21
- Spring Boot
- Spring Security и JWT
- Spring Modulith
- PostgreSQL
- React + TypeScript + Vite
- Docker Compose
- Testcontainers

## Требования

Для локального запуска нужны:

- JDK 21
- Maven 3.9+
- Node.js 22+
- Docker Desktop / Docker Compose
- PostgreSQL через Docker Compose

## Настройка окружения

Создайте локальный файл `.env` по примеру `.env.example`:

```powershell
Copy-Item .env.example .env
```

`JWT_SECRET` — Base64 от не менее 32 случайных байт: `JwtService` декодирует
его через `Decoders.BASE64`. Обычная строка с дефисами вызывает HTTP 500 при входе.
Сгенерируйте подходящий секрет с OpenSSL (Bash):

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
```

В локальном `.env` замените значение `JWT_SECRET` на сгенерированную Base64-строку,
а не на текст команды. Секрет не должен попадать в Git.

> `.env` не должен попадать в Git. Maven/Spring Boot может не загружать `.env`
> автоматически. Перед локальным запуском установите `JWT_SECRET` как переменную
> окружения или добавьте её в environment variables run configuration в IntelliJ.

PowerShell:

```powershell
$env:JWT_SECRET = (openssl rand -base64 48)
```

## Запуск инфраструктуры

Запустите PostgreSQL:

```powershell
docker compose up -d
```

Проверьте состояние контейнеров:

```powershell
docker compose ps
```

## Запуск backend

Из корня проекта:

```powershell
$env:JWT_SECRET = (openssl rand -base64 48)
mvn spring-boot:run
```

Backend будет доступен по адресу:

```text
http://localhost:8080
```

Проверка health endpoint:

```text
GET http://localhost:8080/actuator/health
```

## Запуск frontend

В отдельном терминале:

```powershell
cd frontend
npm install
npm run dev
```

Frontend обычно доступен по адресу:

```text
http://localhost:5173
```

## Тесты

Запуск полного backend suite:

```powershell
mvn test
```

Запуск security unit test:

```powershell
mvn test "-Dtest=JwtAuthenticationFilterTest"
```

Production build frontend:

```powershell
cd frontend
npm run build
```

## Публичный API

Публичные GET endpoints не требуют JWT:

```text
GET /api/v1/public/events
GET /api/v1/public/events/{publicSlug}
GET /api/v1/public/events/{eventId}/disciplines/{eventDisciplineId}/results
```

Публичные результаты включают только опубликованные соревновательные единицы.

## Основной MVP flow

```text
Организатор создаёт событие
→ создаёт дисциплину
→ открывает регистрацию
→ участник подаёт заявку
→ организатор подтверждает заявку
→ создаётся payment
→ организатор создаёт заплыв / матч / группу / финал
→ подтверждённая заявка назначается в unit
→ оператор вносит результат
→ система пересчитывает места
→ организатор публикует unit
→ гость видит опубликованные результаты без авторизации
```

## Безопасность

- JWT secret передаётся только через `JWT_SECRET`
- `.env` исключён из Git
- Actuator health доступен публично без инфраструктурных details
- Другие Actuator endpoints доступны только роли `PLATFORM_ADMIN`
- Некорректные JWT без roles claim отклоняются как невалидные
## Переменные окружения

| Переменная | Назначение / локальный вариант |
|---|---|
| `JWT_SECRET` | Обязательный секрет JWT: Base64 от ≥32 случайных байт (`openssl rand -base64 48`) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/sport_platform` |
| `SPRING_DATASOURCE_USERNAME` | `sport_user` |
| `SPRING_DATASOURCE_PASSWORD` | `sport_pass` (только локально) |
| `APP_PAYMENTS_PROVIDER` | `MANUAL` или `YOOKASSA`, по умолчанию `MANUAL` |
| `APP_CORS_ALLOWED_ORIGINS` | Разрешённые origins через запятую; локально `http://localhost:5173` |
| `YOOKASSA_SHOP_ID`, `YOOKASSA_SECRET_KEY` | Необязательные реквизиты YooKassa |
| `YOOKASSA_RETURN_URL` | Страница возврата, локально `http://localhost:5173/payment-return` |

Локальный запуск из корня репозитория:

```bash
cd sport-platform
cp .env.example .env
docker compose up -d postgres
mvn spring-boot:run -Dspring-boot.run.profiles=local
# В другом терминале:
cd sport-platform/frontend && npm ci && npm run dev
```

Flyway использует те же настройки подключения, что и datasource. В production
задайте все настройки БД и `JWT_SECRET` через окружение. Redis и Kafka пока не нужны.

## Как сделать пользователя организатором

1. Пользователь создаёт аккаунт и входит через `/api/v1/auth/register` и `/api/v1/auth/login`.
2. Пользователь подаёт `POST /api/v1/organization-registration-requests` с
   `organizationType`, `organizationName` и контактами.
3. Администратор вызывает `POST /api/v1/organization-registration-requests/{id}/approve`
   с bearer-токеном. Без тела создаётся организация из заявки; тело
   `{"organizationId":"uuid"}` выбирает существующую организацию.
   Заявитель получает членство `owner` и глобальную роль `organizer`.
   Отказ: `POST .../{id}/reject` с `{"comment":"Причина отказа"}`.
4. Пользователь повторно входит, чтобы получить JWT с новой ролью.
5. Альтернативно администратор назначает существующему пользователю роль
   `organizer` через `POST /api/v1/users/{id}/roles` (`{"roleCode":"organizer"}`),
   а администратор или owner добавляет его через
   `POST /api/v1/organizations/{id}/members` (`{"userId":"uuid","memberRole":"organizer"}`).
   `GET .../members` возвращает участников только администратору или owner.

Допустимые роли членства: `owner`, `admin`, `member`, `organizer`, `operator`, `judge`.
Создавать события могут участники с memberRole `owner`, `admin` или `organizer`.
Членство само по себе не выдаёт глобальную роль JWT. Управление событием доступно
при наличии требуемой глобальной роли и членства в организации события либо авторства.
Чужое событие возвращает 403 `events.access_denied`. Список заявок без `eventId`
содержит только доступные сотруднику события; администратор видит все заявки.
`/api/v1/organizer/events` также показывает события организации и созданные пользователем.

## Оплата и API

- `POST /api/v1/payments/{id}/confirm` подтверждает ручную оплату.
- Webhook `payment.succeeded` также отмечает подтверждённую заявку как `PAID`.
- `POST /api/v1/registrations/{id}/mark-paid` делает это без платёжной записи;
  повторный вызов для `PAID` безопасен. Для остальных неподтверждённых статусов
  возвращается 409 `registrations.payment_not_applicable`.
- До слияния T03 отдельного `payment_status` нет; после V024 потребуется
  дополнить `RegistrationPaymentService` записью `payment_status = PAID`.
- Канонический просмотр оплаты: `GET /api/v1/payments/review/registrations/{id}`.
- Канонические публичные результаты:
  `GET /api/v1/public/events/{eventId}/disciplines/{id}/results`.
- OpenAPI: `/v3/api-docs`, Swagger UI: `/swagger-ui.html`, без токена;
  кнопка Authorize принимает JWT bearer.

Миграция V020 скопирована из `docs/migrations/V020__seed_fixes.sql` дословно:
роль `judge` и демо-администратор `admin@flounder.demo` / `Demo2026!`.
Эти реквизиты предназначены для демо, не для production.

## Docker и CI

```bash
docker build -t flounder-backend .
docker build -t flounder-frontend frontend
```

Backend слушает 8080, использует профиль `prod` и переменные окружения.
Frontend-образ запускает Caddy на порту 80, раздаёт статику из `/srv`
и возвращает `index.html` для маршрутов SPA.
В среде с собственным CA сборка поддерживает `--secret id=proxy_ca,src=/path/to/ca.pem`.
Для Maven можно передать proxy-настройки через `--secret id=maven_settings,src=/path/to/settings.xml`; они не попадают в образ.
CI запускает backend-тесты с Testcontainers и frontend lint/build на PR и push в main.

## Демо: модель плавания (T03)

Swagger UI: `http://localhost:8080/swagger-ui/index.html`, OpenAPI JSON:
`http://localhost:8080/v3/api-docs`. Для защищённых запросов используйте Bearer JWT.

Сессии создаются через `POST /api/v1/events/{eventId}/sessions`:

```json
{"name":"Утро","orderNo":1,"startsAt":"2026-10-15T09:00:00+03:00","breakBetweenEventsSec":60}
```

К существующему телу создания дисциплины (`disciplineTemplateId`) добавьте:

```json
{
  "distanceM":50,"stroke":"FREE","gender":"F","seedingMode":"BY_AGE_GROUP",
  "sessionId":"<sessionId>","orderInSession":1,"heatDurationSec":null,
  "ageGroups":[{"code":"F-2014-2015","name":"Девушки 2014–2015","gender":"F","birthYearFrom":2014,"birthYearTo":2015}]
}
```

Если `name` и `code` отсутствуют, сервер создаёт «50 м вольный стиль, девушки»
и `F-50-FREE`. При коллизии кода в одном событии добавляется `-2`, `-3`, … .
Для Excel используйте **точный код из ответа API**, например `F-50-FREE`,
`M-100-BACK` или `F-50-FREE-2`. Один спортсмен в нескольких дисциплинах —
несколько строк Excel. Сам импорт реализуется в T05.

Форматы времени: `32.15`, `0:32.15`, `00:32.15` → `32150` мс;
`1:02:30.50` → `3750500` мс. Разделитель дробной части — точка; парсер допускает
до трёх знаков (миллисекунды). `display` и `seedTime` выводятся с точностью до
сотых: `00:32.15`, `62:30.50` (минуты суммарные). В Excel время может быть пустым;
публичная форма требует положительный `seedTimeMs` для каждой дисциплины.

Публичная форма доступна через
`GET /api/v1/public/events/{slug}/registration-form`. Авторизованный участник
подаёт заявку:

```json
{
  "profile":{"lastName":"Иванова","firstName":"Анна","middleName":null,"birthDate":"2015-06-01","gender":"F","club":"Дельфин"},
  "items":[{"eventDisciplineId":"<disciplineId>","seedTimeMs":32150}]
}
```

Дисциплина должна быть опубликована, событие — в статусе `REGISTRATION_OPEN`
и в пределах дат регистрации. Профиль и весь пакет заявок сохраняются атомарно.
При нулевом взносе `paymentStatus=NOT_REQUIRED`, иначе `PENDING`.
После `POST /api/v1/registrations/{id}/review` с `{"decision":"CONFIRMED"}`
оператор может подтвердить ручной платёж через `POST /api/v1/payments/{id}/confirm`
либо отметить оплату через `POST /api/v1/registrations/{id}/mark-paid`:
заявка получает `status=PAID`, `paymentStatus=PAID`.

`POST /api/v1/registrations/{id}/resubmit` доступен только после
`NEEDS_CORRECTION`; необязательное тело позволяет исправить `profile`, `seedTimeMs`
или `seedTime`. Снятие через `/withdraw` публикует `RegistrationWithdrawn`.
Участник может снять свою заявку до закрытия регистрации; организатор — заявку
своего события. PATCH статуса поддерживает `ADMITTED` и `WITHDRAWN`.

Результаты заплыва вводятся массивом через
`POST /api/v1/competition-units/{unitId}/results`:

```json
[
  {"competitionUnitEntryId":"<entryId>","time":"00:32.15","status":"VALID"},
  {"competitionUnitEntryId":"<otherEntryId>","timeMs":null,"status":"DNS"}
]
```

Допустимы `VALID`, `DNS`, `DNF`, `DSQ`; вместо `time` можно передать `timeMs`.
Равные времена дают места `1,1,3`; DNS/DNF/DSQ не получают мест.
Протокол организатора: `GET /api/v1/event-disciplines/{id}/results`.
Live управляется через `PUT /api/v1/events/{eventId}/live {"currentUnitId":"<unitId>"}`
и `POST /api/v1/competition-units/{id}/start`, `/finish`.
Завершение ставит `FINISHED` и выбирает следующий заплыв того же события
по расписанию; после последнего возвращает `currentUnitId: null`.

Доступ к управлению проверяет EventAccessService: администратор, создатель
или участник организации события. Оплата объединена с RegistrationPaymentService:
CONFIRMED → PAID с paymentStatus=PAID; остальные статусы дают 409
registrations.payment_not_applicable. Результаты считаются опубликованными по
FINISHED. Исправления CORRECTED и аудит RANKING применяются к PUBLISHED/FINISHED;
в DRAFT/SCHEDULED/IN_PROGRESS выполняется обычная перезапись результата.
До интеграции T02 ранжирование остаётся с TODO. T03 добавляет только V021–V026.


### M02: пароли и сессии

Вход `/api/v1/auth/login` возвращает `accessToken`, `tokenType`, `expiresIn`, `refreshToken`.
Все POST ниже принимают JSON:

| Эндпоинт | Тело | Доступ / успех |
| --- | --- | --- |
| `/api/v1/auth/change-password` | `{currentPassword, newPassword}` | Bearer access, 204 |
| `/api/v1/auth/password-reset/request` | `{email}` | без Bearer, 202 для существующего/неизвестного email |
| `/api/v1/auth/password-reset/confirm` | `{token, newPassword}` | без Bearer, 204 |
| `/api/v1/auth/refresh` | `{refreshToken}` | владение refresh, 200 с новой парой |
| `/api/v1/auth/logout` | `{refreshToken}` | владение refresh, 204 (повтор безопасен) |
| `/api/v1/auth/logout-all` | без тела | Bearer access, 204 |

Ссылки «Забыли пароль?» и «Сменить пароль» ведут на `/forgot-password`,
`/reset-password?token=…` и `/account/password`. Пока нет notifications/SMTP,
`LoggingMailSender` пишет письмо и ссылку в INFO с пометкой DEV; открывать ссылку из
лога нужно на адресе `APP_PUBLIC_URL` (по умолчанию `http://localhost:5173`).
Reset-ссылки действуют час и используются один раз; новый запрос заменяет прежнюю ссылку.
Новый пароль: минимум 8 символов, буквы и цифры, максимум 72 UTF-8 байта (bcrypt).

Refresh живёт 30 дней и ротируется при каждом обновлении. Повторное предъявление
уже отозванного refresh возвращает 401 и отзывает все refresh-токены пользователя,
включая новую пару после ротации. Смена/сброс пароля
отзывают refresh и reset-токены всех устройств. После самостоятельной смены
пароля фронт завершает текущую сессию и предлагает войти заново с новым паролем. Logout/logout-all отзывают refresh;
access JWT продолжает действовать до exp. Фронт сохраняет пару в sessionStorage,
при 401 обновляет её и повторяет запрос один раз; при отказе показывает
«Сессия истекла, войдите снова».

Срок access по умолчанию **остаётся 2 часа** для демо 15.10.2026. После обновления
фронта можно включить `APP_JWT_ACCESS_TTL=PT1H` или `--app.jwt.access-ttl=PT1H`.
`expiresIn` берётся из настройки. Ранее существовавшее
`app.security.jwt.access-token-ttl` продолжает работать как прямое переопределение.

По умолчанию после 5 неудачных входов за скользящие 15 минут по email или IP
следующий login отклоняется 429 `identity.too_many_attempts` с `Retry-After` в секундах.
Настройки API: `app.auth.login.max-failures` / `APP_AUTH_LOGIN_MAX_FAILURES`
(default 5), `app.auth.login.window` / `APP_AUTH_LOGIN_WINDOW` (default `PT15M`).
Передавайте переменные в окружение процесса/контейнера API; например, порог 20
можно включить без пересборки, сохранив окно `PT15M`. Оба значения должны быть положительными.
Успешная аутентификация сбрасывает только счётчик email; счётчик IP сохраняется.
На странице входа время ожидания из `Retry-After` показывается в минутах с округлением вверх.
Остальные endpoints (включая live и импорт) не ограничиваются. Caffeine рассчитан
на один сервер, очищается при рестарте, максимум 100 000 ключей; для нескольких
серверов нужен Redis.

На стенде профиль `prod` уже включает `server.forward-headers-strategy: native`:
Tomcat `RemoteIpValve` получает IP клиента из `X-Forwarded-For`, который добавляет
Caddy (docker-сеть входит в `internal-proxies`); `getRemoteAddr()` возвращает IP клиента.
Без этой настройки (локально и в тестах) используется прямой `remoteAddr`,
присланный клиентом заголовок не доверяется. Код обработки IP не менялся.

В `infra/compose.prod.yaml` API получает `APP_PUBLIC_URL` из окружения с fallback
`https://${DOMAIN}`. В примере production env указано `APP_PUBLIC_URL=https://sportgate.pro`.
Изменения M02 предназначены для слияния и деплоя после демо 15.10.2026 (с 16.10).

### E-mail уведомления (M05)

По умолчанию MAIL_ENABLED=false: письма сохраняются в notification_outbox и
доставляются в лог (адресат/тема INFO, тело только DEBUG). SMTP включается через
MAIL_ENABLED, MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM.
APP_PUBLIC_URL задаёт базовый URL ссылок. Настройки в кабинете организатора:
`/organizer/notification-settings`; журнал — вкладка «Уведомления» события.
Контракты, транзакции, V032, интеграция M02/M06 и ручная проверка описаны в
[docs/M05-notifications.md](../docs/M05-notifications.md).
