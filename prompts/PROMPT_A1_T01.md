Ты — разработчик в проекте Flounder (платформа организации соревнований по плаванию). Репозиторий уже открыт. Сначала прочитай `AGENTS.md` в корне, затем `docs/02_ТЗ_демо.md` (разделы, указанные в задании) — это обязательные источники требований. Работай строго по заданию ниже, в ветке, указанной в нём; один Pull Request на задание; перед PR — `cd sport-platform && mvn -q test` и `cd sport-platform/frontend && npm run build && npm run lint`. Тексты интерфейса и сообщений — на русском, код и коммиты — на английском. В описании PR: что сделано, как проверить вручную по шагам, какие миграции/эндпоинты изменились, что не сделано и почему, и раздел «Вопросы» для техдиректора. Не трогай файлы вне области задания без необходимости. Если в репозитории нет папки `docs/` или `tasks/` — остановись и напиши об этом в ответе, не придумывай требования.

Поток A (backend-core). Это первая задача проекта: другие задачи зависят от твоих `EventAccessService`, `V020`, OpenAPI и CI — сделай их в первую очередь и аккуратно.
=====================  ЗАДАНИЕ  =====================

# T01 · Поток A · Блокеры доступа и гигиена репозитория (backend + немного frontend)

Прочитай `AGENTS.md` и `docs/02_ТЗ_демо.md` §1, §5.1. Ветка `feat/T01-blockers`. Срок: 12–13.10, первая половина дня.

## Цель
Организатор без прав `platform_admin` может создать событие и управлять только своими событиями; оплата переводит заявку в «оплачено»; секреты и артефакты сборки убраны из репозитория; есть OpenAPI и CI.

## Контекст (факты из аудита кода)
- `organizations/application/OrganizationService.java`: ни один сценарий не создаёт `organization_members`; `AddOrganizationMemberRequest` — DTO без контроллера; `organization_registration_requests` имеют статусы PENDING/APPROVED/REJECTED, но эндпоинта approve нет. Из-за этого `events/application/CreateEventUseCase` (проверка `canManageOrganization`) пропускает только `platform_admin`.
- `payments/.../confirm` (ручная оплата) и ЮKassa-webhook не меняют `registrations.status`; статус `PAID` нигде не присваивается.
- Контроллеры `competition/web/CompetitionController`, `registrations/web/RegistrationController` (review), `results/web/ResultsController` не проверяют, принадлежит ли событие организации пользователя.
- `frontend/src/components/RequireRole.tsx` и `app/App.tsx`: роль `platformadmin` вместо `platform_admin` (в JWT claim `roles` — `platform_admin`).
- `sport-platform/.env` с `JWT_SECRET` и каталог `sport-platform/target/` закоммичены, хотя есть `.gitignore`.
- `V002` сидит админа с `password_hash = '$2a$10$REPLACE_WITH_REAL_BCRYPT_HASH'`; роль `judge` в `roles` не засеяна, хотя используется в `RoleCodes` и `@PreAuthorize`.
- Нет springdoc/OpenAPI, нет Dockerfile, нет CI. В `compose.yaml` Redis, Kafka, kafka-ui не используются кодом.
- `events/application/*UseCase` слуг-генерация `public_slug` regex `[^a-z0-9]` → для кириллицы получается `-xxxxxxxx`.

## Что сделать
1. **Членство в организации**
   - `POST /api/v1/organization-registration-requests/{id}/approve` и `/reject` (роль `platform_admin`): approve создаёт организацию (если заявка содержит данные организации) или использует указанную, создаёт `organization_members(member_role='owner')` для заявителя, назначает ему роль `organizer` (если нет), ставит статус APPROVED. Reject — REJECTED с комментарием.
   - `POST /api/v1/organizations/{id}/members` `{userId, memberRole}` и `GET /api/v1/organizations/{id}/members` — для `platform_admin` или owner организации.
   - Интеграционный тест: пользователь с ролью organizer, добавленный в организацию, создаёт событие через `POST /api/v1/events` → 201.
2. **`EventAccessService`** (модуль `events`, публичный класс в корне модуля): `assertCanManage(UUID eventId, AuthenticatedUserPrincipal p)` → ок, если `platform_admin`, либо пользователь член организации события (любая роль), либо `created_by_user_id == userId`. Иначе `BusinessException("events.access_denied")` → 403 (добавить маппинг кода в `GlobalExceptionHandler`). Применить во всех методах контроллеров: events (кроме create/list/public), competition (дисциплины), registrations (review/list by event), payments (review/confirm), results (units, entries, results, publish). Тест: организатор Б получает 403 на дисциплину события организатора А.
3. **Оплата → статус заявки**: в `ConfirmManualPayment…` и в обработке webhook `payment.succeeded`: `registration.payment_status = PAID` (поле появится в T03; если T03 ещё не слит — ставить `status = PAID`, когда текущий статус CONFIRMED; после слияния T03 обновить). Добавить `POST /api/v1/registrations/{id}/mark-paid` (operator/organizer/admin, с `assertCanManage`) для демо без платёжной записи. Тест.
4. **Seed**: скопировать готовый файл `docs/migrations/V020__seed_fixes.sql` в `src/main/resources/db/migration/` без изменений (роль `judge`, seed-админ `admin@flounder.demo` с проверенным bcrypt-хешем пароля `Demo2026!`). Нумерация согласована с T03 (V021–V026) — здесь только V020.
5. **Slug**: транслитерация кириллицы (простая таблица ГОСТ 7.79 / «ru → lat»), затем `[^a-z0-9]+ → -`, обрезка, суффикс из 4 символов при коллизии. Тест: «Кубок Самары 2026» → `kubok-samary-2026`.
6. **Frontend роль**: заменить `platformadmin` → `platform_admin` везде (`RequireRole`, `App.tsx`, `AdminRoute.tsx`); удалить неиспользуемые `AdminRoute.tsx`, `ProtectedRoute.tsx`, `features/.../OperatorResultsPage.tsx`, если они нигде не импортируются (проверить `npm run build`).
7. **Гигиена репозитория**: `git rm --cached sport-platform/.env sport-platform/target -r`; убедиться, что `.gitignore` их покрывает; добавить `sport-platform/.env.example` со всеми переменными (`JWT_SECRET`, `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `APP_PAYMENTS_PROVIDER=MANUAL`, `APP_CORS_ALLOWED_ORIGINS`); `application.yml`: datasource и CORS из переменных окружения с дефолтами для local; убрать Redis/Kafka зависимости из `pom.xml`, сервисы из `compose.yaml`, свойства из `application*.yml` (оставить комментарий «вернуть при появлении event-driven слоя»). Удалить дубли эндпоинтов: оставить `GET /api/v1/public/events/{eventId}/disciplines/{id}/results` и `GET /api/v1/payments/review/registrations/{id}`, вторые варианты удалить (обновить фронтовые `api/*.ts`, если они их используют).
8. **OpenAPI**: добавить `springdoc-openapi-starter-webmvc-ui`; `/v3/api-docs` и `/swagger-ui.html` открыты без токена; `@Operation(summary=…)` на новых эндпоинтах; schemes JWT bearer.
9. **CI**: `.github/workflows/ci.yml`: на PR и push в main — job backend (`mvn -B test`, с Docker для Testcontainers — ubuntu-latest подходит), job frontend (`npm ci && npm run lint && npm run build`).
10. **Dockerfile**: `sport-platform/Dockerfile` (multi-stage: maven build → eclipse-temurin:21-jre, порт 8080, `SPRING_PROFILES_ACTIVE=prod`), `sport-platform/frontend/Dockerfile` (node build → статика в `/dist`, используется Caddy в T09). Проверить `docker build`.

## Критерии приёмки
- Новые тесты + все старые зелёные (`mvn test`), `ModulithArchitectureTest` проходит.
- Организатор (не admin) создаёт событие, дисциплину, видит заявки; чужое событие → 403.
- `git ls-files | grep -E '\.env$|/target/'` пусто.
- `/swagger-ui.html` показывает все контроллеры.
- CI зелёный на PR.
- README: раздел «Переменные окружения», «Как сделать пользователя организатором» (approve заявки или members).
