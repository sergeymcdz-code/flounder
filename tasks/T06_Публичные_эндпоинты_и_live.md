# T06 · Поток B · Публичные представления: стартовый протокол, расписание, live, результаты, поиск спортсмена (backend)

Прочитай `AGENTS.md`, `docs/02_ТЗ_демо.md` §5.7. Ветка `feat/T06-public-api` от `main` после слияния T02 и T03. Срок: 14.10.

## Цель
Публичные страницы и PWA получают всё одним запросом каждая, без авторизации, быстро (кэш 10 с в памяти допустим).

## Что сделать
1. Модуль `publication` (новый) или расширение `results.web.PublicResultsController` + `competition.web.PublicEventController` — выбрать модуль `publication` с собственными read-моделями (запросы через `*Lookup` или JPQL-проекции; не тянуть сущности чужих модулей).
2. Эндпоинты из §5.7 с точными формами ответов:
   - `GET /api/v1/public/events/{slug}` — расширенная карточка (площадка, сессии, дисциплины с флагом `unitsPublished`, статус регистрации `registrationOpen: bool`, `regulationUrl` — если у события есть опубликованная версия регламента с файлом/ссылкой, иначе null).
   - `GET /api/v1/public/events/{slug}/start-list` — только PUBLISHED-заплывы; структура `sessions[] → disciplines[] → heats[] → lanes[]` (для пустых дорожек — элемент с `lane` и `empty: true`); снятые участники (`entry_status = WITHDRAWN`) — `withdrawn: true`.
   - `GET /api/v1/public/events/{slug}/schedule` — плоский список по времени.
   - `GET /api/v1/public/events/{slug}/live` — текущий заплыв из `event_live_state` + следующие 3 по `scheduled_at`/`sequence`; если состояние не задано — первый заплыв с `scheduled_at >= now` или null.
   - `GET /api/v1/public/events/{slug}/results` — виды с опубликованными результатами: по каждому — `overall[]` и `byAgeGroup[{code,name,rows[]}]`, строки `{place, lastName, firstName, birthYear, club, display, timeMs, status}`; DNS/DNF/DSQ в конце с `place: null`.
   - `GET /api/v1/public/events/{slug}/athletes?q=` — поиск по фамилии (prefix, без регистра, «ё/е»), максимум 20, со всеми заплывами и результатами спортсмена в событии.
2a. **Задержки в день старта** (ТЗ §5.7, правка по CJM): миграция `V027__competition_units_actual_started_at.sql`; `POST /competition-units/{id}/start` пишет `actual_started_at`, пересчитывает `scheduled_at` последующих заплывов сессии от фактического времени (через `RecalculateScheduleUseCase` из T02 с параметром «якорь = этот заплыв, время = now»); `/live` возвращает `delaySec`. Тест: старт заплыва с опозданием на 10 минут сдвигает все следующие на 10 минут.
3. Кэш: `@Cacheable` с Caffeine (TTL 10 с) на публичные ответы; инвалидация по событиям публикации/изменения результата/live — проще: TTL без инвалидации, т.к. требование демо — 15–30 с.
4. CORS для публичных эндпоинтов — из переменной `APP_CORS_ALLOWED_ORIGINS` (T01).
5. Тесты: интеграционный — после посева и публикации (используй сценарий из T02) `start-list` содержит все заплывы, `schedule` отсортирован, `live` возвращает первый, после `/start` — текущий, `results` после публикации одного заплыва содержит ровно его результаты, поиск «ива» находит Иванова.

## Критерии приёмки
- Все ответы ≤ 300 мс на наборе M (200 заявок) локально.
- Swagger с примерами.
- Нет N+1: проверка логом Hibernate `show_sql` в тесте (не более 10 запросов на `start-list`).
