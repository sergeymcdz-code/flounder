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

Задайте минимум JWT secret длиной от 32 символов:

```dotenv
JWT_SECRET=replace-with-a-long-random-secret-at-least-32-characters
```

> `.env` не должен попадать в Git. Maven/Spring Boot может не загружать `.env`
> автоматически. Перед локальным запуском установите `JWT_SECRET` как переменную
> окружения или добавьте её в environment variables run configuration в IntelliJ.

PowerShell:

```powershell
$env:JWT_SECRET = "local-development-secret-must-be-at-least-32-characters-long"
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
$env:JWT_SECRET = "local-development-secret-must-be-at-least-32-characters-long"
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
| `JWT_SECRET` | Обязательный секрет JWT, не менее 32 символов |
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
   `POST /api/v1/organizations/{id}/members` (`{"userId":"uuid","memberRole":"member"}`).
   `GET .../members` возвращает участников только администратору или owner.

Допустимые роли членства: `owner`, `admin`, `member`, `organizer`, `operator`, `judge`.
Членство само по себе не выдаёт глобальную роль JWT. Управление событием доступно
при наличии требуемой глобальной роли и членства в организации события либо авторства.
Чужое событие возвращает 403 `events.access_denied`. Список заявок без `eventId`
содержит только доступные сотруднику события; администратор видит все заявки.
`/api/v1/organizer/events` также показывает события организации и созданные пользователем.

## Оплата и API

- `POST /api/v1/payments/{id}/confirm` подтверждает ручную оплату.
- Webhook `payment.succeeded` также отмечает подтверждённую заявку как `PAID`.
- `POST /api/v1/registrations/{id}/mark-paid` делает это без платёжной записи;
  повторный вызов безопасен. Другие статусы заявки сохраняются.
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
Frontend-образ содержит статику в `/dist` для Caddy (T09).
В среде с собственным CA сборка поддерживает `--secret id=proxy_ca,src=/path/to/ca.pem`.
Для Maven можно передать proxy-настройки через `--secret id=maven_settings,src=/path/to/settings.xml`; они не попадают в образ.
CI запускает backend-тесты с Testcontainers и frontend lint/build на PR и push в main.
