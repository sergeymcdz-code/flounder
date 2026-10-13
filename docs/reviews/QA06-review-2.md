# QA06 — ревью 2 (PR #21, 8a68297)

Ветка `pr/21` = `2656034` + `6276669` + **`8a68297` «fix(qa): address QA06 review and expose results navigation for judges»**, перебазирована на `origin/main` (`a37fcb5`, merge-base совпадает с HEAD main). Ревьюер: Claude, 13.10.2026. Диф к main: 49 файлов, +2797/−38. CI зелёный (lint+build).

**Вердикт: одобрить, сливать первым** (до D01 и до pr/15). Все 9 пунктов ревью 1 закрыты, включая п.9 (навигация судьи). Блокирующих замечаний нет.

## Проверено

| Пункт ревью 1 | Проверка | Результат |
|---|---|---|
| 1. Бинарники ≤15 файлов / ≤3 МБ | `git ls-tree -r -l pr/21 docs/qa` → 40 файлов, 2 230 435 байт всего; PNG 26, из них **QA06 — 15 PNG, 862 287 байт** (+2 PDF 93 КБ). Остальные 11 PNG и 3 PDF — QA05 из main, не тронуты. В 8a68297 удалено 212 PNG, один (`QA06-judge-unit-1366x768.png`) переснят (нав. ссылка «Результаты») | **закрыт** |
| 2. Таблица покрытия → JSON | `QA06-ui-ux.md:42` — фраза про `work/qa06-screenshots` + `QA06-matrix-*.json`/`QA06-states.json`; все ссылки на `*.png` в отчёте проверены скриптом — битых нет (ссылки только на 15 оставшихся файлов); QA06-22 ссылается на строку JSON | **закрыт** |
| 3. `overflow-wrap: anywhere` сузить | `OrganizerEventPage.module.css`: снято с `.page`, добавлено на `.page h1`, `.muted`, `.card`; `.table { overflow-wrap: normal }` сбрасывает наследование для `td` внутри `.card`. Тест QA06-16 organizer дополнен: `td` → `overflowWrap === 'normal'`, `scrollLeft > 0`, `scrollWidth === 375` | **закрыт** (решение даже строже, чем просили) |
| 4. demo-script шаг 5 | `docs/demo-script.md:64` → «Табло (live) и «Найти себя»»; путь `/e/{slug}/live` сохранён; других «Live» в сценарии нет | **закрыт** |
| 5. Словарь статусов регламента | `CreateEventPage.tsx:43-45` — `regulationStatusLabels` (PUBLISHED/DRAFT/ARCHIVED), `trim().toUpperCase()`, fallback на код. `isPublishedRegulation` в этой строке больше не используется (функция остаётся в модуле — не проверял, вызывается ли ещё; lint зелёный, значит неиспользуемого импорта нет) | **закрыт** |
| 6. Локатор QR | `qa06-regression.spec.ts:29` → `[class*=publicCard] svg` | **закрыт** |
| 7. TSV 150 КБ | `QA06-hardcoded-values.tsv` удалён из дерева (`git ls-tree` — 0 совпадений); команда генерации в отчёте `:138`, скрипт `scripts/qa/qa06-hardcoded.py` оставлен | **закрыт** (см. желательное 1 — `.gitignore`) |
| 8. Абзац про `test.fail` | `QA06-ui-ux.md:93` — дословно по тексту правок | **закрыт** |
| 9. Ссылка «Результаты» для judge + тест | `AppLayout.tsx:32-33` — `isJudge = hasRole('judge')`, `canManageResults = isPlatformAdmin \|\| isOperator \|\| isJudge`. Роли в `AuthProvider.getRoles` приводятся к lowercase, `hasRole` тоже — `judge`/`JUDGE` из JWT сработают. Маршрут `/operator/results` в `App.tsx:222` уже разрешал `judge`. Judge **не получил** других ссылок: «Мои события»/«Заявки» (`isPlatformAdmin \|\| isOrganizer`), «Виды спорта» (`isPlatformAdmin`), «Платежи» (`isPlatformAdmin \|\| isOperator`) — не изменились. Новый `AppLayout.test.tsx` (Vitest, sessionStorage `flounder.auth-session` + JWT с `roles:['judge']`, `within(nav).getByRole('link', {name:'Результаты'})` → `href=/operator/results`, очистка в `finally`). Playwright QA06-22: `knownDefect()` снят → 11 зелёных + 13 expected-fail | **закрыт** |
| Область дифа | `git diff --name-only origin/main...pr/21 \| grep -v docs/ \| grep -v frontend` → только `scripts/qa/qa06-hardcoded.py`. Java/`pom.xml`/миграции не тронуты. В `frontend/src` — CSS-модули, тексты, `AppLayout.tsx` (+2 строки), `CreateEventPage.tsx` | OK |
| Зависимости | `package.json`: только скрипт `test:qa06`; `package-lock.json` не менялся | OK |
| Старые тесты | Изменённые тестовые файлы — только новые `qa06-*`, `playwright.qa06.config.ts`, `AppLayout.test.tsx`. `*.test.tsx`, `e2e/public.spec.ts`, `e2e/organizer.spec.ts`, `tests/` — без изменений. В 8a68297 одна правка существующего QA06-теста — усиление (QA06-16), одно снятие `knownDefect()` (дефект починен) | не ослаблены |
| Конфликты | `git merge-tree --write-tree pr/21 X`: **main — чисто; pr/18, pr/24, pr/26 — чисто** (pr/24 тоже трогает `demo-script.md`, но другие строки). **pr/15 — 1 конфликт** в `RegistrationReviewPage.tsx`, теперь только хвост файла (QA06 добавила `\n` после `}`, M01 дописывает `OrganizationRegistrationReviewPage`); блок импортов уже не конфликтует | как ожидалось, тривиальный |

Не удалось проверить (как и в ревью 1): сборка/`npm test`/Playwright локально — нет браузера; полагаюсь на CI и отчёт Codex (43 Vitest, 24 Playwright QA06: 11+13, `mvn test` 1024/0).

## Блокирующие замечания

Нет.

## Желательные замечания (не требуют нового раунда)

1. `docs/qa/QA06-hardcoded-values.tsv` не добавлен в `.gitignore` — после локального `python scripts/qa/qa06-hardcoded.py` файл снова появится в `git status` у следующего агента. Одна строка в `.gitignore` — можно сделать в D01 или при слиянии.
2. `App.tsx:222` разрешает `/operator/results` и роли `organizer`, но ссылки «Результаты» у организатора по-прежнему нет (как и до PR). Для демо не нужно (организатор — не судья); отметить в D01/навигационной задаче вместе с QA06-21 (два `main`).
3. Из ревью 1 остаются для D01 (уже отражены в отчёте QA06): `srOnly` без positioned-предка, `AdminPages.css` → `Admin.module.css` после M01, `#71809a` нигде не возвращать; CI без `npm test`/Playwright — инфраструктурная задача.

## Текст правок

Не требуются.

## Порядок слияния / риски для стенда

- **#21 (QA06) → D01 → #15 (M01).** Обоснование из ревью 1 без изменений: QA06 трогает 9 CSS-модулей по одной строке + print-блок `Public.module.css`, D01 перепишет их целиком.
- В раунд правок M01 (#15): после слияния #21 ребейз на main; в `RegistrationReviewPage.tsx` единственный конфликт — хвост файла: оставить `OrganizationRegistrationReviewPage` из M01 (импорты уже не конфликтуют).
- Поведение стенда после деплоя: как в ревью 1 (вкладка «Табло», белая печать, квадратный QR на 375, таблицы внутри карточек, подписи чуть темнее, старый админ оформлен) **плюс**: судья после входа видит в шапке ссылку «Результаты» → `/operator/results`. Это закрывает единственный открытый дефект, важный для шага 7 демо 15.10 (QA06-22). Backend/RBAC не менялись — доступ судьи к API тот же, что был.
- Ручная проверка на репетиции: войти `judge@…` → в шапке есть «Результаты», клик ведёт на список заплывов; остальное — по списку ревью 1.
