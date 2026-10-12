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
- Redis
- Kafka — инфраструктурный задел для будущего event-driven слоя
- React + TypeScript + Vite
- Docker Compose
- Testcontainers

## Требования

Для локального запуска нужны:

- JDK 21
- Maven 3.9+
- Node.js 20+
- Docker Desktop / Docker Compose
- PostgreSQL, Redis и Kafka через Docker Compose

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

Запустите PostgreSQL, Redis и Kafka:

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