# Ревью PR #13 — T11: время публичных страниц в поясе площадки

Ветка `fix/T11-venue-timezone` (bd1b0b2) → `main` (31bfd35). Ревьюер: Claude, 13.10.2026.

## Вердикт: **слить**

Задание выполнено полностью и аккуратно: контракт расширен одним полем, все публичные форматтеры принимают `timeZone`, ни одного вызова форматирования без пояса в `features/public` не осталось, fallback на пояс браузера работает, подпись «время местное (UTC+04)» выводится ровно один раз на `/e/{slug}` и `/schedule`, смещение считается на дату события. Кабинет организатора не затронут, миграций нет, лишних файлов в диффе нет. Блокирующих замечаний нет.

## Проверено

| Пункт | Команда / способ | Результат |
|---|---|---|
| Merge с main | `git merge-tree --write-tree origin/main origin/fix/T11-venue-timezone` | чистый, без конфликтов (tree f5cfb67) |
| CI на bd1b0b2 | `gh api repos/sergeymcdz-code/flounder/commits/bd1b0b2…/check-runs` | `backend: success`, `frontend: success` |
| Что гоняет CI | `.github/workflows/ci.yml` | backend: `mvn -B test` (252 теста по описанию PR); frontend: `npm ci && npm run lint && npm run build` — **`npm test` в CI не запускается** (см. «Желательные») |
| Список файлов | `git diff --stat origin/main...origin/fix/T11-venue-timezone` | 17 файлов: 5 Java (`PublicResponses`, `PublicPublicationService`, `ReadModels`, `PublicationReadRepository`, `PublicPublicationIntegrationTest`), 11 frontend в `features/public` + `mocks/demo-data.ts` + `scripts/generate-public-demo.py`. Ничего вне области задания |
| (1) `venue.timezone` в `GET /api/v1/public/events/{slug}` | diff `PublicResponses.Venue`, `ReadModels.Event`, `PublicationReadRepository.event()` (`v.timezone as venue_timezone`), `PublicPublicationService` | поле добавлено; `@JsonInclude(ALWAYS)` на `Venue` сохранён; колонка `venues.timezone varchar(64) not null` (V006) — строка всегда есть. Другие публичные ответы и кеш не изменены |
| (1) тест бэкенда | `PublicPublicationIntegrationTest:66` (фикстура `Europe/Samara`), `:153` assert `venue.timezone == "Europe/Samara"` | есть |
| (2) типы и форматтеры | `public.types.ts` (`venue.timezone: string`), `format.ts` (`time`, `date`, новая `localTimeLabel` — все через `Intl.DateTimeFormat('ru-RU', { timeZone, … })`) | ок; пустой/пробельный пояс → `timeZone: undefined` → пояс браузера |
| (2) все страницы | `git grep -nE 'toLocale(Time\|Date)String\|Intl\.DateTimeFormat\|\btime\(\|\bdate\(' origin/fix/T11-venue-timezone -- sport-platform/frontend/src/features/public` | все 15 вызовов `time()/date()` получают `event.venue?.timezone` или проп `timeZone`: `EventPage` (даты, сессии, подпись), `SchedulePage` (подпись, заплывы), `StartListPage` → `HeatProtocol timeZone`, `HeatPage` → `HeatProtocol timeZone`, `LivePage` (`LiveBoard`: текущий, следующие, «Обновлено»; `AthleteSearch`), `Protocol.tsx` (`PrintHeader` дата, `HeatProtocol` время), `PublicLayout` (шапка). `ResultsPage` времени не выводит, только `PrintHeader` — покрыт. `Intl.DateTimeFormat` напрямую только в `format.ts`. `toLocale*String` в публичной части нет |
| (2) подпись пояса | `EventPage.tsx:60-62`, `SchedulePage.tsx:21` | один `<p className={styles.muted}>` на страницу; считается от `event.eventStartAt`; формат `longOffset` → `UTC+04`, `UTC+05:30`, `UTC+00`, учитывает DST (тест с `Europe/Berlin` январь/июль) |
| (3) тесты фронта | `Timezone.test.tsx` (новый, 123 строки) | `2026-10-17T06:00:00Z` + `Europe/Samara` → `10:00` ✔; `Europe/Moscow` → `09:00`; переход через полночь в `date()`; пустой/пробельный пояс = пояс браузера и `localTimeLabel` не бросает; смещения Samara/Berlin DST/Kolkata/UTC; `it.each` по 6 маршрутам через `PublicLayout` с MSW; `it.each(['', 'schedule'])` с `timezone: ''` |
| (3) логика форматтеров вживую | `npm ci` из контейнера недоступен (registry.npmjs.org не резолвится); логика `format.ts` перенесена 1:1 в `fmtcheck.mjs` и прогнана `TZ=Europe/Moscow node` (Node 22, тот же ICU, что у Vitest) | `10:00 09:00 17 октября 2026 г.` / `09:00 09:00 время местное (UTC+03)` / `UTC+04 UTC+01 UTC+02 UTC+05:30 UTC+00 UTC-04` — все ожидания `Timezone.test.tsx` сходятся |
| (4) кабинет организатора | `git diff --name-only origin/main...origin/fix/T11-venue-timezone \| grep -v -E 'features/public\|publication\|mocks\|scripts'` | пусто — `OrganizerProgramPage` и прочее не тронуты |
| (5) lint/build | CI frontend job (`npm run lint && npm run build`) зелёный | ок |
| (6) MSW/демо-данные | `demo-data.ts` + генератор `generate-public-demo.py` обновлены синхронно (`timezone='Europe/Samara'`) | ок |
| Прочие конструкторы `ReadModels.Event` | `git grep 'new Event('` в `sport-platform/src` | только два места, оба обновлены |

## Блокирующие

Нет.

## Желательные (не блокируют слияние)

1. `sport-platform/frontend/src/features/public/format.ts:3-28` — некорректный IANA-идентификатор (например, опечатка в `venues.timezone` у организатора) бросит `RangeError: Invalid time zone specified` и уронит все публичные страницы. Задание требует fallback только для пустого значения, так что это не блокер, но дёшево защититься: один helper `safeTimeZone(tz)` с `try { new Intl.DateTimeFormat(undefined, { timeZone: tz }) } catch { return undefined }`, использовать во всех трёх функциях. Проверено: `time(s, 'Europe/Samar')` → RangeError.
2. `sport-platform/frontend/src/features/public/SchedulePage.tsx:21` — строка начинается со строчной буквы: «время местное (UTC+04). Время старта ориентировочное…». Либо разделить на два `<p>`/`<span>`, либо поменять порядок: «Время старта ориентировочное, время местное (UTC+04). Текущий заплыв выделен.»
3. `.github/workflows/ci.yml:34` — frontend job не запускает `npm test`, поэтому `Timezone.test.tsx` и остальные Vitest-тесты в CI не выполняются (проверены только локально автором и логика форматтеров — мной через Node). Стоит добавить `npm test` в CI отдельным PR (вне области T11).
4. `format.ts:localTimeLabel` — `timeZoneName: 'longOffset'` требует Chrome 95+/Safari 15.4+/Firefox 91+. Для демо достаточно; если важны старые мобильные браузеры — вычислять смещение через `Date.getTimezoneOffset`-подобную арифметику на `formatToParts`.

## Текст правок для Codex

(пусто — PR можно сливать как есть; пункты 1–2 из «Желательных» можно сделать отдельным мелким коммитом при желании)
