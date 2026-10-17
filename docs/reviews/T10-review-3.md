# Третье ревью PR #11 · feat/T10-demo-seed · 13.10.2026

Проверялся коммит `264846d` (fix(demo): always send pool metadata supported by T01) поверх `0795cea`. Диф `git diff 0795cea..264846d` — 7 файлов, +37/−28: `scripts/demo/{bodies,seed}.ts`, их тесты, `README.md`, `.env.example`, `docs/demo-script.md`. Голова PR по REST (`gh api …/pulls/11`) — `264846d`, описание обновлено (абзац про `venueMeta`, шаг 9 ручной проверки, «Что не сделано», `vitest run` 32/32, `typecheck` exit 0).

**Вердикт: слить.** Все 5 пунктов повторного ревью (Н1–Н5) закрыты или признаны необязательными, регрессий нет, конфликтов с `main` нет (`git merge-tree` по базе — 0 маркеров `<<<<<<<`). Повторного ревью не требуется.

## Пункты повторного ревью

| № | Замечание (ревью 2) | Статус | Где / проверка |
|---|---|---|---|
| Н1 (обяз.) | `venueMeta` отправлять всегда; `REQUIRE_VENUE_META` — только «падать/предупреждать» | **Выполнено** | `bodies.ts:21–24`: `venueBody()` без параметра, всегда `venueMeta: {lanes: 8, courseLength: 25}`; `seed.ts:54` вызывает `venueBody()`, проверка ответа `GET /venues/{id}` `:56–66` не изменилась. Формат совпадает со слитым `main` (T01): `CreateVenueRequest.venueMeta: Map<String,Object>` (null → `Map.of()`), `VenueResponse.venueMeta`, `VenueMapper.serializeMeta`/`toResponse`, `VenueIntegrationTest.venueMetadataSurvivesCreateGetAndList` с `{"lanes":8,"courseLength":25}`. `fail-on-unknown-properties` в `application.yml` на `main` не включён — на старом DTO поле игнорируется, как и заявлено в README. Тесты: `bodies.test.ts:20–24` (`toHaveProperty('venueMeta', …)`), `seed.test.ts:136` (тело POST содержит `venueMeta` и в режиме по умолчанию), `seed.test.ts:150–162` `it.each([false,true])` — на обновлённом DTO в обоих режимах импорт проходит, WARNING нет, `GET` отдаёт сохранённый объект |
| Н2 (жел.) | `FakeApi` не отвергать неизвестные поля, а игнорировать; `supportsVenueMeta` управляет эхом | **Выполнено** | `seed.test.ts:20–25`: `POST /venues` фильтрует тело по `allowed` (с `venueMeta` только при `supportsVenueMeta`), ошибка `request.unknown_field` удалена; поле `api.venueMeta` удалено, `GET /venues/{id}` `:66–71` возвращает `venueMeta` из сохранённой строки только при `supportsVenueMeta`. Тест `:163–171` проверяет: неизвестное `lanes` и `venueMeta` на старом DTO не сохраняются, `GET` без `venueMeta`; строгий тест `:141–149` переведён на `supportsVenueMeta=true` + запись `venueMeta` в строку |
| Н3 (жел.) | Текст ошибки строгого режима без «доработки T01» | **Выполнено** | `seed.ts:63`: «площадка сохранена без venueMeta; API обновления площадки нет — задайте параметры в БД или создайте площадку заново»; WARNING `:65` дополнен той же подсказкой и «T01 уже поддерживает venueMeta». Ожидание в `seed.test.ts:144` обновлено |
| Н4 (мелочь) | Сократить реплику шага 1 | **Выполнено** | `demo-script.md:24`: три предложения сведены в одно («Группы выбираем пресетом…, circleSeedTopHeats сегодня выключен, а после посева покажем расчётный конец сессии»), длина реплики 926 → 688 символов — в лимит 1:30 укладывается, таблицу хронометража менять не нужно |
| Н5 (мелочь) | README про `PGDATABASE` и пароль в окружении / `~/.pgpass` | **Не сделано, не блокирует** | `README.md:62` без изменений. Остаётся как необязательное пожелание |
| — | README / `.env.example` / demo-script / «Вопросы для согласования» | **Выполнено** | `README.md:42–44`: ссылка на `3e2443a`, «сид всегда отправляет», режимы `true/false`, старая площадка без метаданных; `.env.example:15–18`: `REQUIRE_VENUE_META=true` с комментарием; `demo-script.md:9` подготовка, `:130` п.1 переформулирован («есть ли у существующей площадки сохранённые `venueMeta`») |

## Регрессии и общие проверки

- Тесты прогнаны через `node --experimental-transform-types --test` с заглушкой `vitest`: `seed.test.ts` **14/14** (все новые и изменённые кейсы), `bodies.test.ts` 7/8 — единственный красный (`rulesJson.sections` в `toMatchObject`) вызван неполной заглушкой матчера для массивов, не кодом; тест `always includes metadata supported by T01` зелёный. `api.test.ts`/`reset.test.ts` в заглушке не запускаются (`mockResolvedValue`, mock `child_process`), но эти файлы, как и `api.ts`, `reset.ts`, `config.ts`, в `264846d` не менялись (`git diff … -- …` пуст) и были проверены во втором ревью. В PR — `vitest run` 32/32 (было 31: добавлен `it.each`).
- Поведение сида: `ensure()` для уже существующей площадки POST не делает — на слитом `main` первый запуск теперь сразу сохраняет метаданные; площадка из прогонов до этого коммита остаётся без них, что и описано в README/демо-сценарии (ручная правка в БД). `reset.sql` не менялся, площадку по-прежнему сохраняет.
- `.env.example` по умолчанию `REQUIRE_VENUE_META=true`: на старом DTO сид упадёт с понятным сообщением; README `:44` говорит, как поставить `false`. Приемлемо для слитого `main`.
- Диф ветки к `main`: только `scripts/demo/**` и `docs/demo-script.md`; `docs/` (кроме `demo-script.md`) и `tasks/` не тронуты. Паролей в коде нет: в `.env.example` только `<пароль — в менеджере паролей владельца, в репозитории не хранится>` из ТЗ §1 и шаблон `user:password@localhost`; в тестах тестовые строки.
- Конфликтов с `origin/main`: 0.

## Остаток (необязательно, без повторного ревью)

1. Н5: в `README.md:62` упомянуть, что при `postgresql://user:password@…` в `DEMO_DATABASE_URL` пароль попадёт в окружение дочернего `psql`; безопаснее `~/.pgpass` или URL без пароля.
2. В сообщении строгого режима (`seed.ts:63`) можно добавить название площадки и фактически полученный `venueMeta` — упростит диагностику на стенде.
