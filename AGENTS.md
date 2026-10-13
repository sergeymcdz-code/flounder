# AGENTS.md — правила для агентов-разработчиков (ChatGPT Codex) в репозитории Flounder

## О проекте
Flounder — B2B-платформа организации массовых соревнований по плаванию (регистрация, заявки, посев заплывов, стартовые протоколы, результаты, публичные страницы). Код: `sport-platform/` — Spring Boot 3.5 (Java 21, Spring Modulith 1.4, JPA, Flyway, PostgreSQL 16, JWT/RBAC, Testcontainers); `sport-platform/frontend/` — React 19 + Vite + TypeScript + react-router, CSS Modules.

Документы-источники требований: `docs/01_Архитектура.md`, `docs/02_ТЗ_демо.md` (контракты API в §5 — обязательны), задания `tasks/T0X_*.md`. Если задание и ТЗ расходятся — приоритет у ТЗ; напиши об этом в описании PR.

## Как работать
1. Ветка `feat/T0X-короткое-имя` от актуального `main`. Один PR на задание. Коммиты на английском, `feat|fix|chore(scope): …`.
2. Перед PR: `cd sport-platform && mvn -q test` (Testcontainers требует Docker; если недоступен — укажи это в PR и прогони хотя бы unit-тесты `mvn -q test -Dtest='*Test'`), `cd frontend && npm run build && npm run lint`.
3. Не меняй файлы вне области задания без необходимости; не переформатируй чужой код; не удаляй тесты.
4. Миграции Flyway только добавлять (`V0NN__name.sql`), существующие не редактировать. Нумерация продолжается с V020.
5. Секреты только через переменные окружения; `.env` в `.gitignore`; никаких ключей в коде и тестах кроме явно тестовых.
6. Архитектура модулей Spring Modulith: модуль = пакет верхнего уровня `com.acme.sportplatform.<module>` со слоями `api` (DTO), `application` (use case), `domain`, `infrastructure`, `web`. Межмодульные вызовы — через публичные интерфейсы `*Lookup` в корне модуля, не через репозитории чужого модуля. `ModulithArchitectureTest` должен проходить.
7. Все эндпоинты организатора/оператора обязаны вызывать `EventAccessService.assertCanManage(eventId, principal)` (появится в T01); публичные — под `/api/v1/public/**`. Каждый новый эндпоинт `/api/v1/**` добавляй строкой в `sport-platform/src/test/resources/security/qa04-endpoints.tsv` (метод, путь, политика, scoped, ожидаемый код, состояние фикстуры, тело) — иначе падает `Qa04RoleMatrixIntegrationTest.fixtureTableCoversEveryApiV1ControllerMapping`.
8. Ошибки — через `BusinessException(code, message)` и существующий `GlobalExceptionHandler`; коды вида `module.reason`; сообщения на русском.
9. Тексты интерфейса, сообщения об ошибках, письма — на русском. Идентификаторы, комментарии в коде — на английском.
10. В описании PR: что сделано, как проверить вручную (пошагово), какие миграции добавлены, какие эндпоинты изменились, что не сделано и почему.

## Запуск локально
```bash
cd sport-platform
docker compose up -d postgres           # Redis/Kafka не нужны
export JWT_SECRET="$(openssl rand -base64 48)"
mvn spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm install && npm run dev   # http://localhost:5173, VITE_API_BASE_URL=http://localhost:8080
```

`JWT_SECRET` — Base64 от не менее 32 случайных байт: `JwtService` декодирует его через `Decoders.BASE64`. Команда `openssl rand -base64 48` генерирует подходящий секрет; обычная строка с дефисами вызывает ошибку при входе. Хранить значение только в окружении или локальном `.env`, не в Git.
