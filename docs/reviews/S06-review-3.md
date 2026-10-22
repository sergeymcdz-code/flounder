# S06 — ревью 3 (PR #61, 08f28fc): проверка ребейза

PR #61 «feat(standings): S06 team and medal standings», ветка `pr/61`. Ревью 2 (`docs/reviews/S06-review-2.md`, cbe90a6) — «слить» после финального rebase. По заданию «S06» (`rebase-messages.json`) Codex выполнил `git merge origin/main` (57cea5d, включает S03-A и D03 PR-1/-2: новая навигация, шапка, логотип по роли, визуал v2) в ветку; merge-коммит `08f28fc` с родителями `cbe90a6` + `57cea5d`, rebase/force-push не применялись. Конфликтовали 3 файла: `OrganizerEventLayout.tsx`, `PublicLayout.tsx`, `qa04-endpoints.tsv`. Ревьюер: Claude, 19.10.2026. CI на 08f28fc: backend/frontend/infra — зелёные. Локально: `npm run build`, `npm run lint`, `vitest run` (49 файлов, 239 тестов) — прошли в worktree `pr/61`; `mvn -q -o compile` недоступен (нет локального `.m2`, а `repo.maven.apache.org` закрыт прокси — 403), компиляцию бэкенда принимаю по зелёному CI; при этом ни один Java-файл в конфликт не попадал, и интердифф бэкенда до/после merge пуст (см. ниже).

**Вердикт: слить.**

## Проверено

| № | Пункт | Где / как | Результат |
|---|---|---|---|
| 1 | `OrganizerEventLayout.tsx`: навигация D03 сохранена, вкладка S06 добавлена | `git diff origin/main pr/61 -- …/organizer/OrganizerEventLayout.tsx` — ровно одна добавленная строка `OrganizerEventLayout.tsx:206` `{ path: 'standings', label: 'Командный зачёт' }` между «Результаты» и «Уведомления», в новой структуре `TabItem` D03 (счётчики, `attention`, `satisfies TabItem`, фильтр `judgeOnly` — вкладка у судьи скрыта, как и остальные). Ни одной удалённой строки main. До merge ветка добавляла `['standings', 'Зачёт']` в том же месте старого списка — функциональность на месте, подпись переименована в «Командный зачёт» по тексту задания | **OK** |
| 2 | `PublicLayout.tsx`: навигация D03 сохранена, ссылка S06 добавлена | `git diff origin/main pr/61 -- …/public/PublicLayout.tsx` — только +3 строки: `:54-55` (`loadStandings` + `usePublicData(loadStandings)` без `pollMs`, один запрос на slug — как принято в ревью 2 п. 3) и `:89` условная ссылка `['standings', 'Командный зачёт']` после «Результаты» при `data && !errorStatus`. Список D03 `['start-list','Протокол'] … ['results','Результаты']` не тронут; `['live#find-me','Найти себя']`, который был в старом списке, в main уже удалён D03 — ветка его не возвращает (правильно). Шапка/тема/логотип по роли из main без изменений | **OK** |
| 3 | `qa04-endpoints.tsv`: строки main + строки ветки, без потерь и дублей | Путь `sport-platform/src/test/resources/security/qa04-endpoints.tsv`. main 133 строки, ветка до merge 138, после merge 139 = 133 + 6 S06 (`:134-139` — 4 `manage`-маршрута standings/config/standings/standings.pdf с 200 и 2 публичных с 404). Строка main `POST /api/v1/profiles/{profileId}/documents/{id}/review` (`:133`) сохранена; `sort | uniq -d` — дублей нет. 6 строк S06 побайтно те же, что до merge | **OK** |
| 4 | Файлы вне области ветки | `git diff origin/main...pr/61 --stat` = 42 файла, +1369/−14; до merge (`8d709be...cbe90a6`) = те же 42 файла, +1366/−14. Разница +3 строки — только `PublicLayout.standings.test.tsx` (обёртка `AuthProvider` из D03 и две дополнительные проверки ссылок D03). Интердифф бэкенда (`sport-platform/src`) до/после merge — пустой (все Java-/SQL-/HTML-правки ветки идентичны) | **OK** |
| 5 | Тесты не удалены и не ослаблены | `git diff origin/main pr/61 -- '*test*' '*spec*' '*.test.tsx'`: единственная изменённая существующая строка — `DocumentServiceTest.java:26` (конструктор `DocumentService` + mock `StandingsLookup`, как в ревью 2). `PublicLayout.standings.test.tsx` усилен: `:30-31` проверяют ссылки D03 «Протокол» → `/e/demo/start-list` и «На главную» → `/`, `:32` — «Командный зачёт» только при 200; остальные ожидания (1 вызов, через 90 с всё ещё 1, 2-й вызов с `'other'`) без изменений. Тесты main из D03 (`OrganizerEventLayout`, `PublicLayout`, `AuthProvider` и др.) не тронуты | **OK** |
| 6 | `git merge-tree --write-tree origin/main pr/61` | чисто (`8f1d333`) | **OK** |
| 7 | `git merge-tree --write-tree origin/fix/S04-stage1-followups pr/61` (aa75a1c) | чисто (`b2298a4`) | **OK** |
| 8 | Особая проверка: «Командный зачёт» в обоих layout | организаторский — `OrganizerEventLayout.tsx:206`; публичный — `PublicLayout.tsx:89`; маршруты `standings` в `App.tsx` (организаторский `OrganizerStandingsPage`, публичный `StandingsPage`) на месте (`git diff origin/main pr/61 -- App.tsx` = +5 строк, как до merge) | **OK** |
| 9 | Сборка/линт/тесты фронта | worktree `pr/61`, зависимости поставлены через `bun install --frozen-lockfile` (npm ci в этой среде падает внутри npm, «Exit handler never called»): `npm run build` — 0 (PWA, 94 precache-записи); `npm run lint` — 0, 4 предупреждения oxlint в `AuthProvider.tsx:131,160` и `MyRegistrationsPage.tsx:67` — файлы ветка не меняет (код main); `vitest run` — 49 файлов, 239 тестов, все прошли (совпадает с цифрой Codex в описании PR) | **OK** |
| 10 | `mvn -q -o compile` | невозможно: пустой локальный репозиторий Maven, offline-режим не находит `spring-boot-starter-parent:3.5.6`, а online через прокси — 403 на `repo.maven.apache.org`. CI backend на 08f28fc зелёный (Codex: `mvn -q test` — 1 720 тестов / 76 классов, `Qa04RoleMatrixIntegrationTest` 967 проверок, 0 failures); Java-файлы в конфликт не попадали, интердифф пуст | **Принято по CI** |
| 11 | Описание PR | раздел «Ребейз 18.10» есть: merge-коммит, перечень решений по 3 файлам, прогоны, ручная проверка; новых миграций/эндпоинтов нет, V036 без изменений | **OK** |

## Блокирующие

Нет.

## Желательные

1. Подпись вкладки изменена с «Зачёт» на «Командный зачёт» (по тексту задания) в обоих layout; заголовок страницы и имя файла PDF (`StandingsPage.tsx` — `filename="Зачёт.pdf"`, `OrganizerStandingsPage.tsx`) остались «Зачёт». Не ошибка, но при желании унифицировать отдельным docs/UI-коммитом после слияния.
2. Пункты 1–4 из «Желательных» ревью 2 (ключ PDF-кеша без хеша конфига, spy-бины в `StandingsSecurityIntegrationTest`, разные коды 404 у JSON/PDF, раздел в `13_Инструкция_пользователя.md`) остаются в силе — вне объёма ребейза.

## Текст правок для Codex

(пусто — слить)

## Порядок слияния

Проверка соседних веток, которые тоже меняют `OrganizerEventLayout.tsx` (обе содержат `origin/main` 57cea5d):

- `git merge-tree --write-tree pr/64 pr/61` (S05, 4102aa2) — **чисто** (`ed0cff0`). S05 вставляет `{ path: 'payments', label: 'Оплата' }` после строки «Аналитика» (`OrganizerEventLayout.tsx:208`), S06 — «Командный зачёт» после «Результаты» (`:206`); хвосты `qa04-endpoints.tsv` (S05 +9 строк, S06 +6) git объединил автоматически. Порядок S05 ↔ S06 безразличен; единственное: V035 (S05) должна оказаться в main до V036 (S06) — при слиянии #64 первым `outOfOrder` не потребуется (как и зафиксировано в ревью 2).
- `git merge-tree --write-tree pr/58 pr/61` (S09, c96f1d2) — **7 конфликтов**: `sport-platform/frontend/src/app/App.tsx`, `sport-platform/frontend/src/features/organizer/OrganizerEventLayout.tsx` (S09 вставляет `{ path: 'diploma', label: 'Грамоты' }` в тот же участок списка), `sport-platform/src/main/java/com/acme/sportplatform/documents/application/PublicDocumentService.java`, `…/documents/infrastructure/PdfRenderer.java`, `…/documents/package-info.java`, `…/documents/web/DocumentsController.java`, `sport-platform/src/test/resources/security/qa04-endpoints.tsv` (S09 +8 строк в хвост). Оба PR расширяют модуль `documents` (S06 — `standings.pdf` и `StandingsLookup` в `allowedDependencies`; S09 — грамоты, `PdfRenderer` +69 строк), так что конфликты содержательные, не только в навигации.

Рекомендуемый порядок: **#64 (S05) → #61 (S06) → #58 (S09)**. S06 чисто ложится и на main, и на S04, и на S05; S09 после слияния S06 потребует отдельного задания на merge main с разрешением 7 файлов (в `documents/*` — объединить обе ветки: публичный кеш/контроллер/`package-info` для standings и diploma; в `OrganizerEventLayout.tsx` — обе вкладки; в `qa04-endpoints.tsv` — объединить хвосты). Если же S09 решат сливать раньше S06, тот же набор конфликтов придётся разрешать в #61 повторно.
