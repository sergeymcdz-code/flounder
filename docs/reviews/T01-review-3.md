# Третье ревью PR #2 · feat/T01-blockers · 13.10.2026

**Вердикт: слить.** Остаток второго ревью закрыт полностью, новых блокирующих замечаний нет.

Правки — коммит `3e2443a` («fix(organizations): preserve venue metadata in API responses») поверх `aecf63b`, 11 файлов, +142/−40. Сборка в контейнере ревьюера недоступна — проверка чтением диффа `aecf63b..3e2443a` и кода ветки. CI GitHub на `3e2443a` зелёный: run 38370342377, `backend success`, `frontend success`. `docs/` и `tasks/` не тронуты (`git diff --stat aecf63b..3e2443a -- docs tasks` пуст). Ветка сливается в `main` без конфликтов: `git merge-tree … | grep -c '^<<<<<<<'` = 0, GitHub `mergeable_state: clean`. Описание PR дополнено (venueMeta, 201, `registrations.not_found`, ручная проверка).

## Пункты второго ревью

| № | Замечание | Статус | Где |
|---|-----------|--------|-----|
| 1 | `venueMeta` в API площадок | **Выполнен** | `organizations/api/CreateVenueRequest.java` — поле `Map<String, Object> venueMeta`, компактный конструктор нормализует `null` → `Map.of()`; `VenueResponse.java` — поле `venueMeta`; новый `organizations/application/VenueMapper.java` (`@Component`, `ObjectMapper` Spring): `serializeMeta` → JSON-строка в `VenueEntity.venueMeta` (`@JdbcTypeCode(SqlTypes.JSON)`, колонка `venue_meta jsonb not null default '{}'` из V006 — миграция не нужна), `toResponse` десериализует обратно в `Map`; `CreateVenueUseCase:30` пишет сериализованное значение вместо константы `"{}"`; `GetVenueByIdUseCase:33` и `GetVenuesUseCase:26` используют `venueMapper::toResponse` — т.е. поле возвращается и в `GET /venues/{id}`, и в списке. `VenueLookupService` отдаёт только `existsById` — DTO не затронут. Frontend: `venues.api.ts` — тип `Venue` с `venueMeta: Record<string, unknown>`, `VenueResponse = Venue` (алиас сохранён, `CreateEventPage.tsx` не сломан), `CreateVenueRequest` с необязательным `venueMeta`. |
| 1a | Тест | **Выполнен** | Новый `integration/organizations/VenueIntegrationTest.java`: `venueMetadataSurvivesCreateGetAndList` — `POST` с `{"lanes":8,"courseLength":25}` → 201 и тот же объект, `GET /venues/{id}` → `$.venueMeta.lanes == 8`, `GET /venues` → у созданной площадки `lanes == 8`; `omittedVenueMetadataDefaultsToEmptyObject` — без `venueMeta` → `{}` и в POST, и в GET. Ключи `lanes`/`courseLength` совпадают с тем, что шлёт сид T10 и читает T02. |
| 2 | `registrations.not_found` → 404 | **Выполнен (замечание было ошибочным)** | В `aecf63b` код `registrations.not_found` уже стоял в `switch` как отдельный `case … -> NOT_FOUND` (`GlobalExceptionHandler.java:65–66`), т.е. 404 отдавался и раньше — пункт 3 второго ревью был неточен. Коммит лишь переносит код в общий список NOT_FOUND (`:96`) и добавляет регрессионный тест `missingRegistrationReturnsNotFound` (`GET /registrations/{random}` → 404, `$.code == registrations.not_found`). Поведение не изменилось, тест полезен. |

## Регрессии в правках — проверено, не найдено

- `POST /api/v1/venues` теперь отвечает **201** вместо 200 (`VenueController.java:46`); `EventIntegrationTest.java:450` обновлён на `isCreated()`. Это соответствует просьбе из второго ревью и остальным `POST` создания (организации, события — 201). Frontend `createVenue` не вызывает (только `getVenues`), сид T10 принимает любой 2xx — внешних потребителей, ожидающих ровно 200, нет.
- `CreateVenueRequest` — добавление поля к record: единственный внешний конструктор в `EventIntegrationTest.java:434` дополнен `Map.of()`; других вызовов `new CreateVenueRequest(` в ветке нет.
- JSON-десериализация: `venueMeta: null` → `{}` (компактный конструктор); не-объект (массив/строка) → `HttpMessageNotReadableException` → 400 `invalid_request_body` штатным обработчиком. Колонка `not null default '{}'`, старые строки содержат `{}` — `toResponse` на существующих данных не упадёт.
- `VenueMapper` в слое `application` зависит от `common.exception`, `organizations.api`, `organizations.infrastructure.jpa` и Jackson — внутри модуля, Modulith-правила не нарушены (`ModulithArchitectureTest` в CI прошёл).
- Сообщение `organizations.venue_not_found` переведено на русский («Площадка не найдена») — по AGENTS.md п.9.
- `VenueController` — убран дублирующий импорт `GetVenuesUseCase`, добавлен `HttpStatus`; `@PreAuthorize` на всех трёх методах без изменений.

## Новые замечания (не блокируют)

1. `VenueMapper.toResponse` при повреждённом JSON в БД бросает `IllegalStateException` → 500 `internal_error`. Для данных, которые пишет только наш же код, приемлемо.
2. `venueMeta` принимается без ограничений по составу и размеру (любой JSON-объект). Для демо достаточно; при переходе к MVP имеет смысл описать схему (`lanes`, `courseLength`, …) в ТЗ §5 и валидировать `lanes` как положительное целое — сейчас T02 читает его с дефолтом 8.
3. В `VenueIntegrationTest` используются полные имена `org.hamcrest.Matchers.equalTo`/`java.util.Map.of` вместо импортов — косметика.

## Текст правок для Codex

Не требуется — PR готов к слиянию.
