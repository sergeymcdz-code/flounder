# S04. Follow-up этапа 1: M07-2, M08-2, M05-2, M04-f, QA04-14

Прочитай `AGENTS.md`, `docs/02_ТЗ_демо.md` §5.7 (M07, задержки), §5.9 (лимит входа), `docs/analytics/M08-analytics.md` (определение задержки публикации, строки «Фактическая задержка» и «Задержка публикации»), `docs/decisions/2026-10-13-start-delays.md`, `docs/M05-notifications.md`, `docs/M04-participant-documents.md`. Ветка `fix/S04-stage1-followups` от актуального `origin/main`. Один PR. Приоритет: средний. **Слияние — после демо 19.10** (пункт 5 меняет лимит входа).

## Зачем
После слияния этапа 1 в ревью остались пять мелких, но известных дефектов. Все они небольшие и сидят в разных модулях — делаем одним PR, чтобы не плодить ветки. Миграций нет.

## Что сделать

### 1. M07-2 — запрет сдвига расписания для архивного события (модуль `scheduling`)
- `scheduling/application/ShiftScheduleUseCase`: заменить `access.assertCanManage(eventId, principal)` + `locks.lockForUpdate(eventId)` на `access.assertCanModify(eventId, principal)` + `locks.lockWritableForUpdate(eventId)` (оба уже есть в `events/EventAccessService` и `events/EventLockService`, так сделано в M06 для остальных пишущих use case). Касается и `shift`, и `shift/preview` — preview для ARCHIVED тоже должен вернуть 400 `events.archived`.
- Фронт `features/organizer/ScheduleShiftForm.tsx` / `OrganizerHeatsPage.tsx`: кнопка «Сдвинуть расписание…» `disabled` при статусе события `ARCHIVED` с подсказкой «Архивное событие доступно только для чтения».
- Тест: интеграционный — сдвиг и preview на ARCHIVED-событии → 400 `events.archived`; существующие тесты сдвига проходят без изменений.

### 2. M08-2 — задержка публикации и фактическое отставание в аналитике (модуль `analytics`)
По определению из `docs/analytics/M08-analytics.md`:
- `sessions[].delaySec` = `max(actual_started_at − scheduled_at)` по заплывам сессии, снизу 0; заплывы без факта старта не учитываются (план не подставляется).
- `medianPublicationSec` / `p95PublicationSec` события: интервал по заплыву = `min(rule_execution_logs.executed_at)` с `kind = 'RANKING'` по той же дисциплине и `executed_at >= actual_finished_at`, минус `actual_finished_at`; fallback, если RANKING-лога нет — `max(results.updated_at)` по дорожкам заплыва. Отсутствующие и отрицательные интервалы исключаются. `latencySamples` = число учтённых заплывов, `latencySource` = `"RANKING_LOG"` / `"RESULTS_UPDATED_AT"` / `"UNAVAILABLE"` (когда выборка пуста).
- `AnalyticsReadRepository.results` — реализовать SQL, убрать `TODO M08-2` и значение `UNAVAILABLE_UNTIL_M07`. Экспорт XLSX (`AnalyticsExport`) должен показывать значения вместо пустых ячеек, лист с пояснением источника — обновить текст.
- Фронт: на странице аналитики события блок «Время публикации результатов» показывает медиану/p95 в формате `м:сс` и число заплывов в выборке; если `UNAVAILABLE` — прежняя пометка «нет данных».
- Тесты: `AnalyticsIntegrationTest` — фикстура с двумя заплывами (факт финиша + RANKING-лог через 90 с и 300 с) → медиана 195, p95 ≈ 300, `latencySamples=2`; заплыв без `actual_finished_at` не учитывается; `delaySec` сессии с одним заплывом, стартовавшим на 7 минут позже плана → 420.

### 3. M05-2 — письмо об изменении расписания (модуль `notifications`)
- Новый шаблон `schedule_changed` (`templates/mail/schedule_changed.html/.txt`, код в `NotificationTemplate`): тема «Изменилось расписание: {название события}», в теле — название, дата, ссылка на `/e/{slug}/schedule`, список «ваш заплыв: вид, № заплыва, было {HH:mm} → стало {HH:mm}» в часовом поясе площадки (использовать уже существующий форматер T11). Настройка организации «Изменение расписания» по аналогии с существующими (`notification-settings`, включена по умолчанию).
- Подписчик `ScheduleChangedEvent` (`scheduling/ScheduleChangedEvent(eventId, unitIds, changedAt)`) в `NotificationEventListener` — отдельный метод в том же стиле, что `startList(StartListPublishedEvent)`. Получатели: участники (пользователи с e-mail) заявок, попавших в сдвинутые `unitIds`; у одного пользователя несколько заплывов — одно письмо со списком. Через outbox, как остальные письма. Дедупликация: повторный сдвиг в течение 10 минут по тем же пользователям — новое письмо (не подавлять; отметить в PR, что рассылка «batching» — отдельная задача).
- Тесты: `MailRenderingTest` на новый шаблон (кириллица, время в поясе площадки), интеграционный тест — после `POST /events/{id}/schedule/shift` в outbox появляется письмо участнику с двумя заплывами (одно письмо, две строки), участник без e-mail (импортированный профиль без аккаунта) пропускается без ошибки.

### 4. M04-f — журнал доступа к документам и повреждённый файл (модули `files`, `registrations`)
- `files/application/FileService`: не записывать строку `DENIED` в `file_access_log`, когда `userId == null` (анонимный запрос); анонимный запрос к `GET /api/v1/documents/{id}/download` по-прежнему получает 401 (как сейчас).
- Повреждённый или расшифровываемый с ошибкой файл: `files.corrupt` (`FileCryptography.corrupt()` уже существует) должен доходить до клиента как **409** с русским сообщением, а не 500. Проверить обработку в `GlobalExceptionHandler` (код добавить в карту кодов → статус, если такой карты нет — по текущей схеме проекта), и что `RuntimeException` из потока чтения в `FileService` (строка `catch (RuntimeException ex)`) оборачивается в `files.corrupt`, а не пробрасывается.
- Тесты: анонимный download → 401 и в `file_access_log` нет строк; подменить байты файла на диске (`LocalFileStorage` в temp-dir) → 409 `files.corrupt`, в журнале строка `READ_FAILED` (новое значение `action`, только если CHECK на колонке это допускает; иначе — без записи, указать в PR).

### 5. QA04-14 — лимит попыток входа (модуль `identity`)
Сейчас `LoginAttemptLimiter` считает отдельно по email и по IP с одним порогом (`app.auth.login.max-failures`, по умолчанию 5), из-за чего на демо-стенде стоит `APP_AUTH_LOGIN_MAX_FAILURES=20` (все демо-аккаунты входят с одного ноутбука).
- Разделить пороги: `app.auth.login.max-failures` (по email, по умолчанию 5) и новый `app.auth.login.max-failures-per-ip` (`APP_AUTH_LOGIN_MAX_FAILURES_PER_IP`, по умолчанию 30, окно то же `app.auth.login.window`). Ключ IP учитывает `X-Forwarded-For` только от доверенного прокси (Caddy) — проверить, что текущая реализация берёт правильный адрес за Caddy, иначе все пользователи стенда делят один счётчик `127.0.0.1`; описать в PR, как получен адрес.
- `429` + `Retry-After` — без изменений. Сообщение: «Слишком много попыток входа. Повторите через N минут».
- `infra/.env.prod.example` и `docs/06_Инструкция_запуска_разработки.md`: описать две переменные; рекомендация для стенда после демо — `APP_AUTH_LOGIN_MAX_FAILURES=5`, `APP_AUTH_LOGIN_MAX_FAILURES_PER_IP=30` (сами значения на сервере меняет владелец — отметить в PR как «действие при деплое»).
- Капчу **не делать** (решение: после пилотов).
- Тесты: 5 ошибок по одному email → 429; 5 ошибок по разным email с одного IP → ещё не 429; 30 — 429; успешный вход сбрасывает только счётчик email.

## Не делать
- Не трогать алгоритм сдвига расписания, формулы аналитики помимо задержек, другие шаблоны писем.
- Не добавлять миграции (все данные уже есть: V032–V034). Если для `file_access_log.action` нужен новый CHECK — не менять старую миграцию; в этом случае запись `READ_FAILED` не делать и написать в PR.
- Не менять `APP_AUTH_LOGIN_MAX_FAILURES` в `compose`/`.env.prod` на сервере — это делает владелец.
- Не переименовывать эндпоинты, TSV `qa04-endpoints.tsv` не меняется (новых эндпоинтов нет); если тест матрицы ролей падает из-за изменения кода ответа на ARCHIVED — поправить фикстуру состояния, не политику.

## Проверка
```
cd sport-platform && mvn -q test
cd frontend && npm test && npm run build && npm run lint
```
Вручную (локально): (1) заархивировать событие → кнопка сдвига неактивна, `POST /schedule/shift/preview` → 400; (2) открыть аналитику события с финишировавшими заплывами → медиана/p95 заполнены; (3) сдвинуть расписание на +10 мин → в «Уведомления» события появилось письмо `schedule_changed` участнику; (4) 6 неверных паролей одного пользователя → 429, другой пользователь с того же IP входит.

## Описание PR
Что сделано по пунктам 1–5; как проверить вручную (шаги выше); «миграций нет»; изменённые эндпоинты — только коды ответов (`400 events.archived` для shift на ARCHIVED, `409 files.corrupt`); новые переменные окружения; что не сделано и почему (капча, batching писем, CHECK на `action`).
