# S10 — ревью 2 (PR #68, bf6f72d)

PR #68 «feat(demo): S10 — календарь 25 недель и еженедельное обновление», ветка `pr/68`, head `bf6f72d`. После ревью 1 (`fff026b`) Codex влил `origin/main` (`8eeb2ff`, слияние #62/#65/#66/#70/#71 — без правок кода) и добавил один коммит `bf6f72d` «fix(demo): address calendar review and preserve manual registrations». Диф раунда 2 (`git diff 8eeb2ff..pr/68`): 14 файлов, +522/−136, только `scripts/demo/**` (новый `accounts.ts`, `calendar.ts`, `calendar-plan.ts`, `seed.ts`, `reset.ts/.sql`, тесты и снимок), `docs/qa/demo-calendar.md`, `scripts/demo/README.md`, новый `docs/scripts/demo/out/S10-review1-verification.json`. `sport-platform/**`, `infra/**`, `.env.example` в раунде 2 не тронуты; миграций и новых эндпоинтов нет (TSV не требуется). `git diff --check` чист, `git merge-tree --write-tree origin/main pr/68` — чисто. CI backend/frontend/infra — зелёный (по-прежнему не гоняет `scripts/demo`). Ревьюер: Claude, 19.10.2026. npm registry из песочницы недоступен (`ENOTFOUND registry.npmjs.org`, кэша нет) — `npm ci`/Vitest/typecheck в worktree не выполнены; вместо этого в worktree через глобальный `tsx` с шимами `vitest`/`fflate` прогнаны in-memory сценарии `CalendarApi` с ручными `assert` по каждому пункту правок (п.1–п.7), плюс сверка снимка и парсера описаний. 87 Vitest + typecheck принимаю по PR и `S10-review1-verification.json`.

**Вердикт: слить после правок: (Б1′) правка 1 выполнена только для незавершённых событий — событие с посторонней заявкой, которое `--advance` завершает (REGISTRATION_OPEN → … → COMPLETED), на следующей неделе попадает под строгую проверку `calendar.ts:147-150` (`event.status === 'COMPLETED'` → `resetError('Неожиданная заявка…')`), и дальше каждый еженедельный `--advance` пишет для него `error` и завершается кодом 1 до сброса календаря. Воспроизведено на in-memory API: посторонняя CONFIRMED-заявка на 31.10 → advance 26.10 и 02.11 проходят (REGISTRATION_CLOSED → COMPLETED, 136 заявок, результат посторонней создан), advance 09.11/16.11/23.11 — `error: Неожиданная заявка в «Осенний кубок SportGate · этап 14»`, cron будет красным каждую неделю. Это тот же сценарий Б1 ревью 1 с задержкой на две недели; формулировка «на COMPLETED проверку можно оставить» в ревью 1 была неточной — строгая проверка допустима только для событий, завершённых уже при первом сиде (`plan.initialOffset < 0`), либо её стоит убрать совсем (наличие своих заявок и так проверяется через `missing` → «В завершённом событии отсутствуют заявки»).** Остальные шесть пунктов выполнены и проверены; желательные правки — ниже.

## Проверено

| № | Пункт правок | Где | Результат |
|---|---|---|---|
| 1 | Посторонние заявки не ломают seed/`--advance`; учёт в `states` и лимите очереди; тест | `calendar.ts:147-150` (строгая сверка `possibleKeys` только при `event.status === 'COMPLETED'`), `:152-153` (`missing` — только свои несценарные), `:181-183` (лимит очереди считается по всем CONFIRMED строкам, включая чужие), `:19-20` + `:308` (`registrationStates` по всем строкам), тест `calendar-api.test.ts:175-199` | **Частично.** Мой прогон: seed и advance той же недели с чужой CONFIRMED-заявкой — 0 записей, свои ID не изменились, `registrations`/`CONFIRMED` +1; advance 26.10 → REGISTRATION_CLOSED, `maxParticipants` = числу строк вида. **Но** после завершения события (advance 02.11) следующий advance (09.11 и далее) даёт `error` на этом событии — см. Б1′. Тест PR останавливается на 26.10 и этот случай не покрывает |
| 2 | Ошибка одного события в `--advance` не останавливает остальные; `error` в сводке; код 1 | `calendar.ts:274-316` (`try/catch` вокруг события; `if (!options.advance) throw error` — seed по-прежнему падает сразу), `:337` (`process.exitCode = 1`, если есть `error`), тест `:200-217` | **Выполнено.** Мой прогон с `failComplete` на 24.10: 29 сводок, ровно один `error: Simulated complete failure`, 31.10 → REGISTRATION_CLOSED, 21.11 → REGISTRATION_OPEN; повтор без сбоя — `error` нет, событие COMPLETED. Оговорка: `catch` пишет `registrations: observedRows.length`, где `observedRows` заполняется только после `fill` — при ошибке в `prepare`/`fill` в сводке будет `registrations: 0` (так и в моём Б1′-прогоне); это косметика |
| 3 | Человекочитаемое описание + парсер с валидацией; тесты; примечание о reset | `calendar-plan.ts:119-121` (`description`: «Тестовые данные SportGate. Сезон с 2026-07-27, 25 недель, неделя 1, суббота (точка отсчёта 2026-10-19).», для префикса «, копия «…»»), `:122-133` (`readMetadata`: регулярное выражение, затем `calendarSettings(meta)` проверяет понедельник/1–104/даты, затем week/slot), тесты `calendar-plan.test.ts:68-80`, `calendar-api.test.ts:238-245` (старый JSON → ошибка с подсказкой reset, без записей), PR и `docs/qa/demo-calendar.md:128` — про сброс старого календаря | **Выполнено.** Мой прогон: `readMetadata(description(e))` ≡ `metadata(e)` для всех 29 событий и для копии с префиксом «QA «осень»» (кавычки внутри префикса разбираются); `неделя 26`, `105 недель`, старый `\nS10 {…}` → исключения; «Вымышленные данные SportGate. S02» → `undefined`; `{` в описаниях нет. Снимок `calendar-plan.test.ts.snap` не изменён по датам/количествам (только +58 строк `title`/`venue`) |
| 4 | Общий бутстрап аккаунтов; поведение #75 | новый `scripts/demo/accounts.ts:5-39` (`ensureAccounts`: `GET /users?query=` → создание только отсутствующего → login → при `invalid_credentials` остановка без `POST /users` → роль → членство), `calendar.ts:56`, `seed.ts:33-34, 57-60`; тесты `seed.test.ts:226-243`, `calendar-api.test.ts:219-226` | **Выполнено.** `GET /users?query=&size=` существует (`UserController:63-71`, PLATFORM_ADMIN). Токен организатора берётся до добавления членства — безопасно, `EventAccessService:53` проверяет членство по БД, не по claims. Мой прогон: существующий `org@flounder.demo` с другим паролем → ошибка «…ORG_PASSWORD…», `POST /users` не вызывался, событий нет. Конфликт с #75 в `seed.ts` остаётся (см. «Порядок слияния») |
| 5 | `registrationCloseAt` при искусственном DEMO_DATE | `calendar.ts:290-293` (плановое значение, если в будущем, иначе `now+7д`), тест `calendar-api.test.ts:227-237`, `docs/qa/demo-calendar.md:135` | **Выполнено.** Мой прогон для `now` = 18.10.2026 и 18.10.2027: для всех REGISTRATION_OPEN `registrationCloseAt` = плановому либо `now+7д` |
| 6 | `demo_targets.venue` вместо списка площадок в SQL; документ | `reset.ts:10` (поле обязательное), `:58-60` (допускаются и прежние брендированные имена через `replace(/^Бассейн «(.+)», (.+)$/, 'SportGate — $2, $1')`), `:66` (`venue` в цели), `reset.sql:7, 13` (`v.name = target.venue`), `:15-19` (список девяти имён удалён, маркер/префикс и S02-площадка остались), `reset.test.ts:9`, тесты `calendar-api.test.ts:271-289, 303`; README S02 `:41, 54`; `docs/qa/demo-calendar.md:115, 128-130` | **Выполнено.** Мой прогон: `demo_targets` содержит `venue` из списка `venues`. Нюанс: SQL больше не ограничивает календарные цели известными площадками — доверяет `reset.ts` (`permittedVenue`); при ручной правке JSON подмена `venue` всё равно отклоняется (`count(*)` ≠ целей → откат, проверено Codex по SQL) |
| 7 | Названия: месяц/год, «этап» только у серии SportGate, площадки без бренда | `calendar-plan.ts:8-16` («Бассейн «Волга», Самара» и т. п.), `:86-96` (месяц/год для шести шаблонов, `· этап N` только для `кубок SportGate`, при коллизии — суффикс `(YYYY-MM-DD)`), тест `:81-88`, снимок | **Выполнено.** Снимок: 29 названий уникальны, в т. ч. «Кубок города Самары, август 2026», «Клубные старты «Волна» — Тольятти, август 2026», «Осенний кубок SportGate · этап 7/14», «Зимний кубок SportGate · этап 21 (воскресенье)»; на 104 неделях — уникальны без суффикса-даты; имён с «SportGate» среди площадок нет. Косметика: номер этапа = номер недели (7, 14, 21), а не порядковый в серии (ж. 1) |
| 8 | Область, тесты, документы | диф раунда 2 — только разрешённые пути; тесты только добавлены (две изменённые строки — расширение снимка и `wrongPasswordEmail` в фейке); `docs/qa/demo-calendar.md:160-178` — таблица проверки правок 18.10; `S10-review1-verification.json` | **Выполнено.** Тексты на русском; секретов нет; миграции не затронуты |
| 9 | Слияние | `merge-tree origin/main pr/68` — чисто; `pr/68`×`pr/72`, `pr/74`, `pr/77` — чисто; `pr/68`×`pr/75` — **конфликт `scripts/demo/seed.ts`** (оба переписывают цикл по `accounts`) | См. «Порядок слияния» |

## Блокирующие

**Б1′. Посторонняя заявка ломает `--advance` после того, как событие завершено.** `calendar.ts:147-150`:

```ts
for (const row of event.status === 'COMPLETED' ? rows : []) {
  …
  if (!possibleKeys.has(registrationKey(row.participantSnapshot, code))) resetError(`Неожиданная заявка в «${plan.title}».`);
}
```

`event.status` — фактический статус на стенде. Событие недели +1 с живой заявкой зрителя проходит advance как REGISTRATION_CLOSED, затем как COMPLETED (здесь проверка ещё не срабатывает, т. к. статус до `complete` — REGISTRATION_CLOSED; посев и результаты для чужой заявки создаются штатно). Со следующей недели `event.status === 'COMPLETED'`, чужая заявка не входит в `possibleKeys` → `resetError` → в режиме advance это `error` в сводке и `process.exitCode = 1` (`:337`) — **каждый понедельник до сброса всего календаря**, а сброс удаляет и результаты этого события. Воспроизведение (in-memory `CalendarApi`, outsider CONFIRMED на 31.10): advance 26.10 — REGISTRATION_CLOSED/136, 02.11 — COMPLETED/136 без ошибок, 09.11, 16.11, 23.11 — `error: Неожиданная заявка в «Осенний кубок SportGate · этап 14»…`. Правка: строгую сверку оставить только для событий, которые были COMPLETED уже при первом сиде и никогда не открывались публично — условие `event.status === 'COMPLETED' && plan.initialOffset < 0` (`initialOffset` считается от `anchorDate`, поэтому стабилен при advance), либо убрать цикл совсем: отсутствие своих заявок в завершённом событии и так ловится `:152-153` («В завершённом событии отсутствуют заявки»). Тест `calendar-api.test.ts:175-199` продлить: после `demoDate: '2026-10-26'` выполнить advance с `'2026-11-02'` и `'2026-11-09'` — `events.every(e => !e.error)`, событие COMPLETED, посторонняя заявка сохранена, у неё есть результат (`resultStatus`).

## Желательные

1. **Нумерация этапов серии** (`calendar-plan.ts:92-93`): «Осенний кубок SportGate · этап 7», «… этап 14», «Зимний кубок SportGate · этап 21» — номер недели, а не порядковый номер этапа в серии; на стенде ожидают 1/2/3. Считать `series`-события по порядку (`result.filter(e => e.series).length + 1`), снимок обновить.
2. **Сводка при ошибке до `fill`** (`calendar.ts:310-313`): `registrations: observedRows.length` даёт 0, если исключение случилось в `prepare`/`fill` (в т. ч. Б1′). Подставлять строки из `registrations(api, latest, …)` в `catch` (одно чтение) либо писать `registrations: null`.
3. **`reset.sql`** (`:13-19`): для календарных целей SQL теперь не проверяет площадку по известному списку, только `v.name = target.venue`; защита целиком на `reset.ts:58-60`. Приемлемо (маркер в описании и демо-организация остаются обязательными), но стоит сказать в `docs/qa/demo-calendar.md` одной фразой, что `venue` в JSON — из `reset.ts`, руками не редактировать.
4. **`docs/qa/demo-calendar.md:76`** — `python3 … json.load(sys.stdin)` предполагает, что `/api/v1/public/events` возвращает массив; если ответ — страница (`items`/`content`), команда упадёт. Можно оставить вариант из ревью 1 (с ветвлением `isinstance(d, list)`).
5. Из ревью 1 без изменений: CI не гоняет `scripts/demo` (отдельная задача); `--help` не упоминает `DEMO_CALENDAR`; имитация оплат S05 — отдельная задача.

## Текст правок для Codex

1. `scripts/demo/calendar.ts:147-150` — строгая сверка заявок с `possibleKeys` срабатывает для любого события с фактическим статусом COMPLETED, в том числе завершённого через `--advance` с посторонней (живой) заявкой: со следующей недели каждый `--advance` даёт для него `error` «Неожиданная заявка…» и код 1 до сброса календаря. Воспроизведение на in-memory `CalendarApi`: посторонняя CONFIRMED-заявка на событие 31.10 → advance 26.10 и 02.11 проходят, advance 09.11/16.11/23.11 — `error`. Оставь строгую проверку только для событий, завершённых уже при первом сиде (`event.status === 'COMPLETED' && plan.initialOffset < 0`), либо убери цикл совсем (отсутствие своих заявок ловится `:152-153`). Продли тест `calendar-api.test.ts:175-199`: после `demoDate: '2026-10-26'` выполни advance с `'2026-11-02'` и `'2026-11-09'` — `events.every(e => !e.error)`, событие COMPLETED, посторонняя заявка сохранена и имеет результат.
2. `scripts/demo/calendar-plan.ts:92-93` — номер этапа в «… кубок SportGate · этап N» сделай порядковым в серии (1, 2, 3…), а не номером недели (сейчас 7/14/21); обнови снимок.
3. `scripts/demo/calendar.ts:310-313` — в `catch` не писать `registrations: 0`, если ошибка случилась до `fill`: прочитать заявки события (`registrations(api, latest, …)`) или писать `null`.
4. `docs/qa/demo-calendar.md:76` — команда сверки каталога должна работать и при ответе-странице (`items`/`content`), не только при массиве.

## Порядок слияния

- `merge-tree origin/main pr/68` — чисто; с #72, #74, #77 — чисто.
- **#75 (SEC01)** — конфликт в `scripts/demo/seed.ts`: оба PR переписывают цикл по `accounts` (S10 выносит его в `accounts.ts`, #75 добавляет `findUser` перед login). Рекомендую сливать **S10 первым** (после Б1′): `accounts.ts` уже реализует правило #75 («существующий аккаунт с другим паролем → остановка без `POST /users`», тесты `seed.test.ts:226-243`), и #75 при ребейзе оставляет только `findUser`/`findParticipant`, отбрасывая свой хунк по циклу. При обратном порядке S10 ребейзится на удаление цикла — тоже просто, но правку дублирует.
- #72 (SEC02) меняет импорт заявок и доступ к документам — после его слияния повторить `npm test` в `scripts/demo` и один локальный прогон календаря.
- #64 (S05) — календарь без взносов; имитация оплат — отдельная задача.

## Что проверить на стенде

1. После сида (`DEMO_DATE=2026-10-19`): 28 карточек в каталоге, описания «Тестовые данные SportGate. Сезон с 2026-07-27, 25 недель, неделя N, …» без JSON; названия «Кубок города Самары, август 2026» и т. п.; площадки «Бассейн «…», Город».
2. Зарегистрировать владельцем ребёнка на «Осенний кубок SportGate · этап 14» (31.10), повторить сид — сводка без изменений, заявка сохранена (`registrations` +1).
3. `--advance` вручную в понедельник 26.10 и далее; после Б1′ — проверить, что событие 31.10 с живой заявкой завершается 09.11 и последующие недели идут без `error` (код выхода 0 в логе).
4. `DEMO_CALENDAR=true … reset.ts` — 29 целей, каждая с `venue`, без slug A/B/C.

## Прогон на стенде

Все команды — пользователем `deploy` на сервере, после `git pull` слитого S10. `scripts/demo/.env` уже содержит `API_URL`, `ADMIN_EMAIL`/`ADMIN_PASSWORD`, `DEMO_PASSWORD`, `DEMO_OWNER_EMAIL`, `DEMO_SEED_ENABLED=true` и переменные S10 (`CALENDAR_WEEKS=25`, `CALENDAR_PREFIX=""`, `DEMO_DATE=2026-10-19`; `DEMO_CALENDAR` и `CALENDAR_START` — закомментированы). `.env` не печатать. Если на сервере есть Node ≥ 22 (`node --version`), использовать прямые команды; иначе — контейнерные из `docs/qa/demo-calendar.md` (они же ниже под «без Node»). В обоих вариантах `flock` защищает от наложения с cron.

```bash
# 0. Резервная копия БД и файлов (как перед S02)
"$HOME/flounder/infra/backup.sh"
```

```bash
# 1. Первый сид 25 недель (−12…+12 от 19.10.2026). DEMO_DATE берётся из .env (2026-10-19).
#    Ожидание: локально 59 с, через HTTPS ≈2–10 мин; бюджет скрипта 90 мин.
cd ~/flounder/scripts/demo && mkdir -p ~/backups && npm ci && flock -n ~/backups/demo-calendar.lock npx tsx calendar.ts 2>&1 | tee -a ~/backups/demo-calendar.log
```

Ожидаемый результат — последняя строка лога `S10 {"mode":"seed","weeks":25,"events":[…],"seconds":…}` с 29 событиями: 14 `COMPLETED` (01.08…17.10), 1 `REGISTRATION_CLOSED` (24.10, «Осенний кубок SportGate · этап 14»), 4 `REGISTRATION_OPEN` (31.10, 07.11, 08.11, 14.11), 9 `PUBLISHED`, 1 `DRAFT` (16.01.2027, публично не виден); суммарно 2 641 заявка, 1 872 результата. В каталоге `https://sportgate.pro` — 28 карточек.

```bash
# 1а. То же без Node на сервере (контейнер node:22-alpine, как в docs/qa/demo-calendar.md)
cd ~/flounder && mkdir -p ~/backups && flock -n ~/backups/demo-calendar.lock docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e DEMO_DATE=2026-10-19 -e CALENDAR_WEEKS=25 -v "$PWD":/repo -w /repo/scripts/demo node:22-alpine sh -eu -c 'npm ci && npx tsx calendar.ts' 2>&1 | tee -a ~/backups/demo-calendar.log
```

```bash
# 2. Сверка публичного каталога: 28 строк «дата статус slug», DRAFT отсутствует
curl -fsS https://sportgate.pro/api/v1/public/events | python3 -c 'import json,sys; d=json.load(sys.stdin); items=d if isinstance(d,list) else d.get("items",d.get("content",[])); print(len(items)); [print(e.get("eventStartAt","")[:10], e.get("status"), e.get("publicSlug")) for e in sorted(items,key=lambda e:e.get("eventStartAt",""))]'
```

```bash
# 3. Повторный сид — идемпотентность: та же сводка, «Импорт: … создано 0», новых событий/заявок нет
cd ~/flounder/scripts/demo && flock -n ~/backups/demo-calendar.lock npx tsx calendar.ts 2>&1 | tail -3
```

```bash
# 4. Еженедельное обновление вручную (каждый понедельник после демо). Дата — серверная UTC, DEMO_DATE из .env игнорируется;
#    для проверки на фиксированной дате: DEMO_DATE=2026-10-26 npx tsx calendar.ts --advance. Бюджет 10 мин, локально 13 с.
cd ~/flounder/scripts/demo && flock -n ~/backups/demo-calendar.lock npx tsx calendar.ts --advance >> ~/backups/demo-calendar.log 2>&1; echo "rc=$?"; tail -3 ~/backups/demo-calendar.log
```

Ожидаемый результат (первый запуск 26.10): `rc=0`; в сводке `"mode":"advance"` — неделя 13 (24.10) → `COMPLETED`, 31.10 → `REGISTRATION_CLOSED` (посев и первые два заплыва), 07.11/08.11/14.11 +8…+13 заявок, 21.11 `PUBLISHED` → `REGISTRATION_OPEN`, далее по плану; поле `error` отсутствует. Повтор в ту же неделю — те же статусы, 0 добавлений. Если в какой-то сводке есть `error`, `rc=1`: остальные события обновлены, причину устранить и повторить команду.

```bash
# 4а. То же без Node (контейнер)
cd ~/flounder && flock -n ~/backups/demo-calendar.lock docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$PWD":/repo -w /repo/scripts/demo node:22-alpine sh -eu -c 'npm ci && npx tsx calendar.ts --advance' >> ~/backups/demo-calendar.log 2>&1; echo "rc=$?"; tail -3 ~/backups/demo-calendar.log
```

```bash
# 5. Включить cron (только после слияния Б1′ и одного ручного п. 4). Хост в Europe/Moscow — 06:00 МСК; для UTC-хоста заменить «0 6» на «0 3».
( crontab -l 2>/dev/null; echo '0 6 * * 1 cd /home/deploy/flounder && /usr/bin/flock -n /home/deploy/backups/demo-calendar.lock /usr/bin/docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$PWD":/repo -w /repo/scripts/demo node:22-alpine sh -eu -c '"'"'npm ci && npx tsx calendar.ts --advance'"'"' >> /home/deploy/backups/demo-calendar.log 2>&1' ) | crontab -
crontab -l | tail -1
```

```bash
# 6. План сброса календаря (read-only): печатает demo_targets (29 целей с полем venue, без slug A/B/C) и точную команду psql.
#    Нужен перед повторным сидом, если на стенде уже есть календарь старой версии (с JSON в описании).
cd ~/flounder/scripts/demo && DEMO_CALENDAR=true npx tsx reset.ts
```

```bash
# 7. (При необходимости) применить сброс: остановить API, выполнить SQL с JSON из п. 6 в S10_DEMO_TARGETS, запустить API
cd ~/flounder && docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod stop api && docker exec -i flounder-postgres psql -X -U sport_user -d sport_platform -v ON_ERROR_STOP=1 -v "demo_targets=$S10_DEMO_TARGETS" < scripts/demo/reset.sql && docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod start api && curl -fsS https://sportgate.pro/actuator/health
```

Для отдельной QA-копии добавить `CALENDAR_PREFIX="QA S10"` в окружение команд 1/3/4/6 (в `.env` оставить `CALENDAR_PREFIX=""`, иначе cron будет обновлять только копию). Без `CALENDAR_PREFIX` в окружении сброс (п. 6) выбирает **все** копии календаря.
