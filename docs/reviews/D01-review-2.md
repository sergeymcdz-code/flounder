# D01 — ревью 2 (PR #30, 3446a15 «fix(frontend): address D01 review and clarify color semantics»)

Диапазон: 03d5fad..3446a15, один коммит, 37 файлов (+359/−110): 3 CSS, 1 CSS-module ×2, 7 TSX (только `className`), 1 e2e-спек (+110, только добавления), 2 SVG, 3 PNG иконки, 2 документа, 15 PNG-сравнений. CI на 3446a15: backend/frontend/infra — success. Ревьюер: Claude, 13.10.2026.

**Вердикт: одобрить с одной маленькой правкой до слияния (после демо 15.10).** Оба блокера ревью 1 и все 6 пунктов решения владельца по цветам выполнены и подтверждены по CSS и по снимкам «после». Остался один хвост блокера 1: на 375 px теперь рвётся внутри слова **клуб** («Чайк/а», «СШО/Р №1», «Аква/тика», «Стар/т») на start-list, live и results — та же категория дефекта, что и «Колеснико/в» из ревью 1, одна строка CSS. Можно слить и без неё только при явном согласии владельца.

## Проверено

| Пункт | Способ | Результат |
|---|---|---|
| Р1-блокер 1: перенос цифр на 375 | `Public.module.css` (+`white-space: nowrap` для `.table th`, `td:nth-child(3)`, `td:last-child`; `td:nth-child(2) { overflow-wrap: normal }`; ≤480: `padding 6px 4px`, `.fl-lanes th` 18 px, таблица `display:block; overflow-x:auto`), снимки start-list-375 / live-375 / results-375 | Годы, времена, заголовки, фамилии — в одну строку. **Клуб (4-й столбец) по-прежнему рвётся внутри слова** — `overflow-wrap: anywhere` с main остался на `.table td`, а место ушло под nowrap-столбцы. E2e проверяет только 3-й и последний столбцы, поэтому не ловит |
| Р1-блокер 2: border-top входа | `LoginPage.module.css` — `border-top` после shorthand; снимки login-1366/375; e2e `borderTopWidth === '4px'` | Выполнено, графитовая линейка 4 px видна |
| Р1-п.3: 8 дорожек на 1366×768 | `D01-live-1366-eight-lanes.png` (1366×768), grid-раскладка `.live` при ≥641, h1 32/h2 24/номер 64 (80 при ≥1600), e2e «rows above nav, scrollY 0» | Все 8 строк + «Открыть заплыв» над навигацией без прокрутки |
| Р1-п.5: sticky-колонка судьи на 375 | `JudgePanelPage.module.css` ≤480: `th/td:first-child { position: sticky; left: 0; background: surface; z-index: 1 }`; снимок judge-detail (нижняя часть), e2e по `scrollLeft` | Номер дорожки остаётся на месте при прокрутке |
| Р1-п.7: print-сброс soft/status | `globals.css @media print`: все `--color-*-soft`, `--color-surface-muted` → `#fff`; `--color-danger/success/warning/accent`, все `--status-*` → `#000`, `--status-*-soft` → `#fff`; QA06-15 расширен | Выполнено; тёмный блок обёрнут в `@media screen`, в print не протекает |
| Решение п.1: красный только danger/LIVE | `--status-live: var(--color-danger)`, `.live` border-top/eyebrow через `--status-live`, `--color-board-accent` удалён, DNS/DNF → muted | Выполнено. Иконки/favicon: красная стенка → `#96a5ac`, жёлтый пунктир → `#cbd2d3` (нейтральные) |
| Решение п.2: primary графит ≥7:1 | `--color-primary #334654`, hover `#1f2933`, soft `#e9eff4`; тёмная тема `#d2dde6`/on-primary `#182126` | 9.79:1 / 14.76:1 / dark 11.85:1 — пересчитано, совпало |
| Решение п.3: дорожки 4–5 полосой без заливки | `components.css`: `border-inline-start: 4px transparent` для всех, 4–5 — `var(--color-text)` + 700 + `background: transparent`; селекты 4–5 в кабинете — та же полоса; e2e проверяет rgb(24,33,38)/прозрачный фон/700 | Выполнено; на табло полоса светлая (text в dark) — читается |
| Решение п.4: «Печать» и второстепенные — ghost | `Protocol.tsx` PrintButton, `ResultsPage` сегменты, `LivePage` «Открыть заплыв», «Повторить», «Мои заявки», «Создать аккаунт» → `fl-button-ghost`; primary — «Подать заявку», «Войти», «Отправить заявку» | Не больше одного primary на экран |
| Решение п.5: CORRECTED янтарный, disabled серый | `.resultStatusCorrected` → warning-soft/warning; `body button:is(.fl-button-primary,[class*='primary'],…):disabled` → border-strong, opacity 1 | На снимке judge-detail «исправлено» янтарный, «Опубликовать результаты» серый opaque |
| Решение п.6: переключатель → D02, live/results всегда тёмные | `:root:has(.fl-live-page)` / `:has(.fl-results-page)` в `@media screen`; маркер-класс на существующих h1/div; раздел «Отложено в D02: переключатель темы (требует изменения разметки)» в концепции; e2e «dark без data-theme, print — light» | Выполнено, localStorage/обработчиков нет |
| Контраст AA | пересчёт 24 пар по WCAG 2.1 (python) | все 24 совпали до сотых, минимум 5.60:1 (muted/page), primary/белый 9.79. Доп.: primary/page 8.55, dark primary/page 12.99, danger/белый 7.28, warning/белый 6.56, border-strong/белый 3.59 (≥3 для рамок). Disabled 1.78/1.56 — неактивные, исключены корректно |
| Только CSS/классы/manifest/шрифты | диф 7 TSX | только `className` (в `Protocol.tsx` и `JudgePanelPage.tsx` — тернарники по уже существующему `status`, новых узлов/текстов/обработчиков нет). Backend/API/миграций нет |
| Бинарники | `git ls-tree -l pr/30 docs/design/screenshots` | 15 PNG, 2 106 230 B, max 281 956 B — в лимитах. Иконки 2,8/7,6/3,1 КБ |
| e2e/vitest | диф спеков | только добавления: +8 тестов D01 review/owner, QA06-15 усилен. Ничего не удалено, `knownDefect` не добавлялся |
| Конфликты | `git merge-tree --write-tree pr/30 {origin/main d8824a1, pr/40, pr/42}` | все три чистые. Общие файлы: main — `LoginPage.tsx` (#29), `OrganizerHeatsPage.tsx` (#39, добавлен `<p className={styles.muted}>` — стили D01 применимы); pr/40 — `LivePage.tsx` (добавляет `<p role=status className={styles.card}>` **вне** `.live`, грид не задевает; но при `delaySec>0` блок займёт высоту — тест «8 дорожек над nav» на фикстуре без задержки останется зелёным) и `OrganizerHeatsPage.tsx`; pr/42 — `JudgePanelPage.tsx` (импорты/загрузка, className не трогает) |

## Блокирующие

1. **375 px, столбец «Клуб» рвётся внутри слова** (start-list, live, results — снимки «после»). `Public.module.css`, блок `@media screen` рядом с `.table td:nth-child(2) { overflow-wrap: normal; }` — распространить на клуб: `.table td:nth-child(2), .table td:nth-child(4) { overflow-wrap: normal; }` (или для всех `td` при ≤480, раз таблица уже прокручивается). Дополнить e2e «keeps years and times on one line» проверкой, что ни одна ячейка `td:nth-child(4)` не содержит переноса внутри слова (например, `lines === 1` для ячеек без пробела). Обновить три снимка 375.

## Желательные (D02 / следующий PR)

2. `.table { display: block }` на ≤480 снимает табличную семантику у `<table>` в части браузеров (Safari/VoiceOver). Безопаснее оборачивать прокрутку в существующий контейнер или добавить `role="table"` — последнее требует TSX, поэтому D02.
3. Селектор `body button:is([class*='primary'], [class*='submitButton'], …):disabled` очень широкий (любой класс с подстрокой `primary`); работает, но при появлении новых модулей может неожиданно перекрасить disabled-кнопки. Лучше в D02 заменить на явные классы.
4. В панели судьи три кнопки DNS/DNF/DSQ в незажатом состоянии: DSQ всегда красная рамка+текст — 4 красных квадрата подряд на 1366 заметно «шумят». Вариант: красный только для `[aria-pressed=true]`, в покое — как DNS/DNF.
5. На снимке judge-375 у sticky-ячейки справа просвечивает кромка поля ввода (тонкая «скобка»). Добавить `box-shadow: 1px 0 0 var(--color-border)` на sticky-ячейку.
6. Primary `#334654` и text `#182126` различимы, но близко (1.9:1 между собой); на ghost-кнопках и ссылках primary читается как «чуть светлее чёрного». Если хочется явнее — чуть больше синевы в primary (например `#2f4a60`) без потери 7:1.

## Текст правок для Codex

> Добей п.1 ревью 1 (PR #30): на 375 px столбец «Клуб» рвётся внутри слова («Чайк/а», «Аква/тика», «Стар/т»). В `Public.module.css` рядом с `.table td:nth-child(2) { overflow-wrap: normal; }` добавь `td:nth-child(4)` (или `overflow-wrap: normal` для всех `td` в `@media screen and (max-width: 480px)`, раз таблица прокручивается). Дополни e2e `D01 review 1: … one line at 375px` проверкой ячеек клуба без пробела (`lines === 1`). Обнови `D01-start-list-375`, `D01-live-375`, `D01-results` снимки. Логику/разметку/тексты не трогать.

## Порядок слияния

После демо 15.10: **#30 (после п.1) → pr/40 → pr/42** или в любом порядке — textual-конфликтов нет; при слиянии pr/40 раньше #30 перепроверить снимок live 1366×768 с фикстурой задержки. #29/#39 уже в main — сливается автоматически. D02 (переключатель темы, субсет шрифтов, role=table) — после #30.
