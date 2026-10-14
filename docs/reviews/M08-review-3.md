# M08 — ревью 3 (PR #36, c70c2a1)

Ветка `pr/36` (c70c2a1, «feat(analytics): M08 — панель аналитики события и организации»), ребейз на `origin/main` fe3b45f (M03 #33 — вершина main на момент пуша); раунд 3 — один коммит `c70c2a1 fix(analytics): restore organization dashboard links for all users` (3 файла, +15/−7: `AnalyticsPages.tsx`, `AnalyticsPages.test.tsx`, `docs/analytics/M08-analytics.md`). Ревью 2: `docs/reviews/M08-review-2.md` (вершина c7bf9ca). Полный диф PR относительно main: 19 файлов, +1061/−3, миграций нет. CI на c70c2a1: backend ✅, frontend ✅, infra ✅; `mergeable_state: clean`. Ревьюер: Claude, 13.10.2026. Локально `mvn`/`npm` не запускались (нет Docker, нет доступа к registry/~/.m2) — принимаю по CI и описанию PR (116 frontend-тестов, build/lint код 0; backend этим раундом не менялся); код правки прочитан построчно.

**Вердикт: слить.** Единственная правка ревью 2 выполнена точно по тексту; ребейз чистый, в дифе раунда ничего лишнего. Ветка отстаёт от `origin/main` ровно на один коммит `39249c7` (docs/reviews round 8 — только `docs/reviews/*.md` и `tasks/WAVE4_*.md`), `merge-tree` с текущим main без конфликтов — повторный ребейз не нужен.

## Проверено

| Пункт правок | Где | Результат |
|---|---|---|
| 1. Ссылка «Открыть аналитику» на каждой карточке для всех пользователей, `useAuth` убран | `AnalyticsPages.tsx:107-111` | **Выполнено.** Строка `const { isPlatformAdmin } = useAuth()` удалена из `OrganizationsAnalyticsListPage`; `<Link to={`/organizer/organizations/${o.id}/analytics`}>Открыть аналитику</Link>` рендерится безусловно внутри каждой `<section>` вместе с `<p>{organizationAccessMessage}</p>`. `useAuth` остался только в `useAnalytics` (`:16`, нужен для `session`/`logout` на 401), импорт `:3` используется. Пояснение на карточке и сообщение при 403 (`useAnalytics(orgId, getOrganizationAnalytics, organizationAccessMessage)`, `:94`) не тронуты |
| 2. Тест переписан: две карточки, у каждой пояснение и ссылка | `AnalyticsPages.test.tsx:89-98` | **Выполнено.** Тест `offers links with an access explanation on each card when membership roles are unavailable`: две организации (`org`/«Волна», `other`/«Чайка»), для каждой `closest('section')` → `within(card)` проверяет текст «Доступно владельцам и администраторам организации» и ссылку с `href=/organizer/organizations/<id>/analytics`. `within` импортирован (`:1`). `authFlags.isPlatformAdmin=false` в `beforeEach` (`:21`) — т.е. тест идёт от обычного пользователя. Тест для platform admin (`:82-87`) оставлен; тест 403 (`:99-104`) не тронут |
| 3. Docs без «администратору платформы»/«прямой маршрут» | `docs/analytics/M08-analytics.md:14,62,96-100` | **Выполнено.** Абзац «API и интерфейс»: «каждая карточка показывает это же пояснение и ссылку „Открыть аналитику“ для всех пользователей. Сервер проверяет owner/admin-членство при открытии аналитики». Шаг 6: «под организатором с owner/admin-членством и нажать „Открыть аналитику“ на карточке… при 403 показывается то же пояснение». `git grep -E 'ссылки предлагаются|прямой маршрут' pr/36` → пусто. Добавлен раздел «Правка по ревью 2» с итогами прогонов. Единственное оставшееся «администратору платформы» (`:3`) — про доступ к панели в целом, по смыслу верно |
| 4. Описание PR | `gh api …/pulls/36 --jq .body` | **Выполнено.** «Правки по ревью 1» п. 4 и «Как проверить вручную» п. 4 переписаны («ссылку „Открыть аналитику“ для всех пользователей», «нажать „Открыть аналитику“ на карточке своей организации»); добавлен раздел «Правка по ревью 2» (116 тестов/25 файлов, build/lint код 0); раздел «Слияние» упоминает ребейз с #37, #30, #44, #42, #33 и разрешение TSV объединением строк. Фраз «администратору платформы»/«прямой маршрут» в теле PR нет |
| Ребейз на актуальный main | `git merge-base origin/main pr/36` = fe3b45f (#33); `git merge-base --is-ancestor origin/main pr/36` → нет; `git log pr/36..origin/main` = 1 коммит `39249c7` | **Отстаёт на 1 коммит** — только `docs/reviews/*-review-2.md` (9 файлов) и `tasks/WAVE4_План_волны…md`, общих файлов с PR нет. `git merge-tree --write-tree origin/main pr/36` — exit 0, без конфликтов. Ребейз не требуется |
| Диф раунда без лишнего | `git range-diff 028dc5e..c7bf9ca fe3b45f..pr/36` | коммиты 1–3 идентичны (единственное отличие — контекст hunk в `qa04-endpoints.tsv`: теперь строки добавляются после PDF-маршрутов M03, а не после `auth/logout-all`); коммит 4 — только три перечисленных файла. Backend-часть PR (`sport-platform/src`) до и после ребейза совпадает побайтно (сравнение дифов отличается лишь заголовками hunk) |
| TSV после ребейза | `qa04-endpoints.tsv:116-119` (main 115 строк → PR 119) | все строки main сохранены, 4 analytics-маршрута в хвосте после `public/events/{slug}/results.pdf`, дубликатов нет |
| Миграции / файлы вне области | `git diff --name-status origin/main...pr/36` | миграций нет; тот же список из 19 файлов, что в ревью 2 (analytics backend+frontend, `OrganizationManagementAccess`/`OrganizationService`/`OrganizationServiceTest`, `App.tsx`, `OrganizerEventLayout.tsx`, `OrganizerLayout.tsx`, TSV, docs) |
| Тесты не ослаблены | диф c70c2a1 | один тест переименован и усилен (2 карточки × 2 проверки вместо `queryByRole… not.toBeInTheDocument`); удалённых тестов нет |
| Тексты UI на русском | `AnalyticsPages.tsx:111` | да, новых строк нет |

## Блокирующие

Нет.

## Желательные

1. Стиль `OrganizationServiceTest.analyticsRequiresOwnerOrAdminMembership` (полные имена классов вместо импортов) — из ревью 2, не блокер.
2. Валидация IANA-`timezone` площадки (ревью 1, отдельная задача); промежуточные подписи оси X графика.
3. Follow-up M08-2 после M07 (#40): латентность публикации и `delaySec` по определению в `docs/analytics/M08-analytics.md`.

## Текст правок для Codex

—

## Порядок слияния

- **С `origin/main` (39249c7) — чисто**; ветка от fe3b45f, отстаёт на один docs-коммит без пересечений — сливать как есть.
- После M08 ребейзятся из-за хвоста `qa04-endpoints.tsv`: #40 (M07), #38, #41; #41 дополнительно — `App.tsx`/`OrganizerEventLayout.tsx` (соседние строки, тривиально).
- Рекомендуемый порядок: M08 (#36) → M07 (#40) → M08-2.
