# QA06 — UI/UX-аудит Flounder

Дата: 13.10.2026 (Москва). Ветка `qa/QA06-ui-ux`, аудит начат на **31bfd35c142a1ef01ab287b2be6f737ceb51105a**, перед PR выполнен rebase на актуальный `origin/main` **0a758521b5acbe3f4daaa4f3b5595bf426c2c19d** (включая T11). Требования: [QA00](https://github.com/sergeymcdz-code/flounder/blob/5c2de00103d240e2dde180e4f65ce54c9f31252e/tasks/QA00_Общие_правила_тестирования.md), [QA06](https://github.com/sergeymcdz-code/flounder/blob/5c2de00103d240e2dde180e4f65ce54c9f31252e/tasks/QA06_UI_UX_аудит_и_визуальные_дефекты.md) из `origin/docs/tasks-qa` (`5c2de001`, PR #12, уже слит в main), AGENTS.md, ТЗ демо §§2/4/5/6/8, demo-script.md и scripts/demo/README.md. После первоначальной ошибки сетевого sandbox fetch выполнен с сетевым доступом; для ветки задан явный remote refspec, поскольку clone первоначально отслеживал только main.

Среда: Linux, Chromium **154.0.8037.57**, Node 24.19.0, npm 11.9.0, Java 21/Maven 3.9.11. Локаль `ru-RU`, часовой пояс браузера `Europe/Moscow`; дополнительно контролируем ISO-время Самары. Основной UI: `VITE_API_MOCK=true`, штатные MSW — 30 заявок/6 заплывов кабинета и компактный публичный набор из M (40 строк, 4 вида, 2 сессии/6 заплывов). Для не покрытых штатными моками старых платёжных/административных GET добавлены **только тестовые** MSW-ответы в e2e/qa06-helpers.ts. Это аудит фронта, а не подтверждение импорта/посева всех 200 строк M через настоящий backend. Production PWA проверена отдельно на dist + существующем e2e/public-server.ts, без MSW-worker.

**22 дефекта: blocker 0, major 11, minor 11. Исправлено этим PR 7 (01–05, 15–16); QA06-20 закрыт upstream T11. Всего закрыто 8, открыто 14.** Исправления — малые CSS/текстовые изменения; максимальная отдельная правка — 31 строка нового CSS админа плюс два импорта. Структура компонентов, API, миграции и токены `styles/globals.css` не менялись. Полная унификация оставлена D01.

## Дефекты

| ID | Severity | Где / доказательство | Шаги | Ожидаемо / требование | Фактически до правки | Предложение / статус | Файл, строка |
|---|---|---|---|---|---|---|---|
| QA06-01 | major | Списки организатора, оператора; старые публичные результаты. [снимок](screenshots/QA06-legacy-results-before-375x812.png) | Открыть таблицу на 375 px; прокрутить вправо. | QA06 п.1; ТЗ §6: таблица прокручивается внутри контейнера. | Страница шире экрана: 503 / 710 / 816 px. Скрытые th-подписи вне positioned-контейнера; у старого протокола grid-карточка растёт по min-content. | Исправлено: position:relative у двух обёрток, min-width:0 у карточки; скролл таблиц сохранён. | [PublicDisciplineResultsPage.module.css:102](../../sport-platform/frontend/src/features/results/PublicDisciplineResultsPage.module.css#L102); [OperatorPaymentsPage.module.css:52](../../sport-platform/frontend/src/features/payments/OperatorPaymentsPage.module.css#L52) |
| QA06-02 | major | Обзор события; [снимок](screenshots/QA06-organizer-qr-375x812.png) | На 375 px дойти до публичной ссылки и QR. | ТЗ US-4, §6.1: QR читается как квадрат. | SVG 140×140 сжимался до 81.8×140 из-за flex-shrink. | Исправлено: QR не сжимается; карточка на телефоне идёт в колонку. | [OrganizerEventPage.module.css:84](../../sport-platform/frontend/src/features/organizer/OrganizerEventPage.module.css#L84) |
| QA06-03 | minor | Админ: рассмотрение заявки, виды спорта; [снимок](screenshots/QA06-admin-review-before-1366x768.png) | Открыть /admin/registrations/review. | QA06 п.1–2, ТЗ §6: подписанные, визуально разделённые поля. | admin-* классы не имели CSS: радиокнопки, подсказки и textarea шли слитно; оформление таблицы отсутствовало. | Исправлено: восстановлены стили существующих классов в AdminPages.css (31 строка), без изменения структуры. | [AdminPages.css:4](../../sport-platform/frontend/src/features/admin/AdminPages.css#L4) |
| QA06-04 | major | Таблицы, подписи платежей/заявок; [снимок](screenshots/QA06-operator-payments-375x812.png) | Измерить цвет #71809a на белой и muted-поверхности. | QA06 п.5, WCAG 1.4.3: ≥4.5:1 для обычного текста. | Контраст 4.00:1 на #fff и 3.81:1 на #f7fafc. | Исправлено 15 вхождений в 9 CSS-модулях: существующий --color-text-muted даёт 5.54 / 5.29:1. | [OperatorPaymentsPage.module.css:76](../../sport-platform/frontend/src/features/payments/OperatorPaymentsPage.module.css#L76) |
| QA06-05 | minor | Админ, оплата, табло, создание события; [снимок](screenshots/QA06-admin-review-before-1366x768.png) | Просмотреть подсказки и подписи, открыть платёж/версию регламента. | QA06 п.3, ТЗ §6: русский UI. | payment flow, review, production, MANUAL, webhook, Live и PUBLISHED в подписи версии. | Исправлено: русские тексты, «Ручная оплата», «Табло», «Опубликована»; машинные коды/URL сохранены. | [RegistrationReviewPage.tsx:21](../../sport-platform/frontend/src/features/admin/RegistrationReviewPage.tsx#L21); [PublicLayout.tsx:80](../../sport-platform/frontend/src/features/public/PublicLayout.tsx#L80) |
| QA06-06 | minor | Все даты; [снимок](screenshots/QA06-e-demo-375x812.png) | Сравнить карточку события, кабинет и платёж. | QA06 п.2: ДД.ММ.ГГГГ и ЧЧ:ММ; ТЗ §6 не задаёт форму записи даты. | 15 октября 2026 г. / 14 окт. 2026 г., 10:00 вместо единого цифрового формата. | Открыто: единый форматтер даты/времени для публичного UI и кабинета; есть красный тест. | [format.ts:12](../../sport-platform/frontend/src/features/public/format.ts#L12); [organizer-event-context.ts:38](../../sport-platform/frontend/src/features/organizer/organizer-event-context.ts#L38) |
| QA06-07 | major | Обзор, заплывы, результаты; [снимок](screenshots/QA06-organizer-event-1366x768.png) | Нажать публикацию события, стартового протокола или готовых результатов вида. | QA06 п.4: явное подтверждение значимых публикаций. | Запрос отправляется сразу; модалки подтверждения нет во всех трёх местах. | Открыто: показывать предмет и последствия публикации, затем подтверждать. Три красных теста. | [OrganizerOverviewPage.tsx:67](../../sport-platform/frontend/src/features/organizer/OrganizerOverviewPage.tsx#L67); [OrganizerHeatsPage.tsx:118](../../sport-platform/frontend/src/features/organizer/OrganizerHeatsPage.tsx#L118) |
| QA06-08 | major | Панель оператора/судьи; [снимок](screenshots/QA06-operator-unit-empty-375x812.png) | Вернуть GET заплыва с entries:[]; открыть панель. | ТЗ US-6, QA06 п.4: публикация допустима для заполненного протокола. | «Опубликовать» активно при 0 участниках (entries.some возвращает false); кнопка сохранения уже заблокирована. | Открыто: блокировать публикацию пустого заплыва с понятным пояснением. API-публикация здесь не утверждается. | [JudgePanelPage.tsx:149](../../sport-platform/frontend/src/features/results/JudgePanelPage.tsx#L149) |
| QA06-09 | minor | Кабинет при потере сети; [снимок](screenshots/QA06-organizer-event-network-375x812.png) | Отказ сети для GET события через HttpResponse.error(). | QA06 п.1,3: понятный русский текст ошибки и восстановление. | Пользователь видит Failed to fetch из TypeError, хотя сообщение API 500 по-русски отображается правильно. | Открыто: отличать сетевые ошибки от бизнес-ошибок, давать совет проверить связь/повторить. | [OrganizerEventLayout.tsx:101](../../sport-platform/frontend/src/features/organizer/OrganizerEventLayout.tsx#L101); [CreateEventPage.tsx:67](../../sport-platform/frontend/src/features/organizer/CreateEventPage.tsx#L67) |
| QA06-10 | major | Модалка снятия заявки; [снимок](screenshots/QA06-withdraw-focus-lost-375x812.png) | Открыть «Снять», закрыть Esc. | QA06 п.4–5: фокус возвращается на кнопку, открывшую модалку. | activeElement становится BODY; дальнейший Tab начинает навигацию заново. | Открыто: сохранить триггер перед showModal и вернуть фокус при закрытии/размонтаже. | [OrganizerDialog.tsx:23](../../sport-platform/frontend/src/features/organizer/OrganizerDialog.tsx#L23) |
| QA06-11 | major | Фокус во всех разделах; [снимок](screenshots/QA06-login-375x812.png) | Tab до ссылки/поля; измерить полупрозрачный outline на белом. | QA06 п.5, WCAG 1.4.11: видимый индикатор с контрастом ≥3:1. | rgb(11 91 123 / .35) даёт только 1.80:1. Локальные outline .25 ещё слабее. | Открыто для D01: непрозрачный контрастный цвет фокуса. globals.css не менялся. | [globals.css:76](../../sport-platform/frontend/src/styles/globals.css#L76); [OrganizerEventsPage.module.css:227](../../sport-platform/frontend/src/features/organizer/OrganizerEventsPage.module.css#L227) |
| QA06-12 | minor | Валидатор заплывов; [снимок](screenshots/QA06-organizer-heats-375x812.png) | Включить prefers-reduced-motion:reduce, нажать нарушение. | QA06 п.5: учитывать reduced motion. | JS явно вызывает scrollIntoView({behavior:"smooth"}); CSS scroll-behavior:auto не отменяет явный аргумент. | Открыто: выбрать auto при reduce; тест перехватывает фактический вызов. | [OrganizerHeatsPage.tsx:198](../../sport-platform/frontend/src/features/organizer/OrganizerHeatsPage.tsx#L198) |
| QA06-13 | major | Мои заявки и старые платёжные экраны; [снимок](screenshots/QA06-my-registrations-375x812.png) | GET /registrations/me возвращает допустимый WITHDRAWN. | ТЗ §5.4/US-5: снятая заявка отображается как снятая. | У участника исчезают бейдж статуса и подсказка; Record знает только старые статусы и PAID. Для ADMITTED/UNDER_REVIEW есть та же неполнота. | Открыто: покрыть актуальные статусы единым русским словарём; проверять безопасный fallback. Тест именно WITHDRAWN. | [MyRegistrationsPage.tsx:22](../../sport-platform/frontend/src/features/registrations/MyRegistrationsPage.tsx#L22); [registration.types.ts:25](../../sport-platform/frontend/src/features/registrations/registration.types.ts#L25) |
| QA06-14 | minor | Табло; [снимок](screenshots/QA06-e-demo-live-375x812.png) | Открыть live; посчитать новые fetch за 5.5 с после начальной загрузки. | ТЗ US-7 и QA06: обновление каждые 15 с. | Новый опрос уже через 5 с; подпись честно говорит «каждые 5 секунд». | Открыто: согласовать период с ТЗ (приоритет ТЗ по AGENTS); изменить таймер и подпись вместе. | [usePublicData.ts:3](../../sport-platform/frontend/src/features/public/usePublicData.ts#L3); [LivePage.tsx:144](../../sport-platform/frontend/src/features/public/LivePage.tsx#L144) |
| QA06-15 | minor | Печать протоколов; [снимок](screenshots/QA06-start-list-pdf-before-page-2.png) | Напечатать PDF с printBackground:true и посмотреть конец страницы 2. | ТЗ §6.4: белая печатная поверхность без UI-фона. | Фон html/body из globals.css перекрывал print-правило: внизу PDF был большой голубоватый блок. | Исправлено локальным background:white!important внутри print; новый PDF белый. | [Public.module.css:444](../../sport-platform/frontend/src/features/public/Public.module.css#L444) |
| QA06-16 | major | Обзор и Мои заявки; [снимок](screenshots/QA06-my-long-before-375x812.png) | Задать длинные непрерывные название, фамилию и клуб в пределах длины полей. | QA06 п.1: переносы длинных значений без скролла документа. | Ширина документа 2953 px для названия и 1618 px для фамилии/клуба. Публичный протокол уже переносил эти строки правильно. | Исправлено: overflow-wrap:anywhere и min-width:0 в карточке; после правки ширина 375 px. Два зелёных теста. | [OrganizerEventPage.module.css:5](../../sport-platform/frontend/src/features/organizer/OrganizerEventPage.module.css#L5); [MyRegistrationsPage.module.css:88](../../sport-platform/frontend/src/features/registrations/MyRegistrationsPage.module.css#L88) |
| QA06-17 | minor | Вся дизайн-система; [снимок](screenshots/QA06-organizer-events-new-1366x768.png) | Сравнить первичные кнопки, таблицы, бейджи и просканировать CSS/JSX. | QA06 п.2, ТЗ §6: существующие токены и единое оформление. | Параллельные палитры #0d6178/#0b5b7b, радиусы 0.5rem/0.75rem/999px, десятки размеров; повторные определения statusBadge/status* в одном модуле. | Открыто для D01: инвентарь литералов ниже; унифицировать стили и словари без редизайна в QA06. | [OrganizerEventsPage.module.css:209](../../sport-platform/frontend/src/features/organizer/OrganizerEventsPage.module.css#L209); [OrganizerLayout.module.css:64](../../sport-platform/frontend/src/features/organizer/OrganizerLayout.module.css#L64) |
| QA06-18 | minor | Загрузка оплаты, старых кабинетов, админа; [снимок](screenshots/QA06-my-payment-loading-375x812.png) | Задержать GET на 60 с, открыть экран оплаты. | QA06 п.1,5: состояние загрузки доступно для вспомогательных технологий. | Текст/спиннер есть, но нет status/aria-live. В создании регламента только disabled-select без объяснения загрузки. Полный список в QA06-states.json. | Открыто: семантика status и объяснение ожидания; требует правки атрибутов/компонентов, не CSS/текста. | [PaymentPage.tsx:292](../../sport-platform/frontend/src/features/payments/PaymentPage.tsx#L292); [CreateRegulationPage.tsx:207](../../sport-platform/frontend/src/features/regulations/CreateRegulationPage.tsx#L207) |
| QA06-19 | minor | UX оплаты и просмотра заявки; [снимок](screenshots/QA06-my-payment-375x812.png) | Открыть оплату или /operator/registrations/:id. | QA06 п.4: понятно, какую заявку и соревнование пользователь обрабатывает. | Основные сведения — ID события/дисциплины, а не название соревнования и вид программы; на экране оператора нет ФИО спортсмена. | Открыто, UX-рекомендация: показывать читаемые контекст и участника. Нужна структурная доработка/получение метаданных. | [PaymentPage.tsx:366](../../sport-platform/frontend/src/features/payments/PaymentPage.tsx#L366); [OperatorRegistrationPage.tsx:297](../../sport-platform/frontend/src/features/payments/OperatorRegistrationPage.tsx#L297) |
| QA06-20 | major | Время публичных стартов (история 31bfd35); [снимок](screenshots/QA06-e-demo-schedule-375x812.png) | Контрольный ISO-start 10:00+04:00 (Самара); браузер Europe/Moscow. | ТЗ §2/§5.7, сценарий демо: старт по времени площадки. | time() использует часовой пояс браузера: получается 09:00. Ряд старых форматтеров жёстко задаёт Москву. | Закрыто upstream T11 (PR #13, bd1b0b2) при rebase на 0a75852: venue.timezone передаётся в форматтер. Контрольный тест теперь зелёный; backend/API в QA06 не менялись. | [format.ts:3](../../sport-platform/frontend/src/features/public/format.ts#L3); [MyRegistrationsPage.tsx:18](../../sport-platform/frontend/src/features/registrations/MyRegistrationsPage.tsx#L18) |
| QA06-21 | minor | Кабинет организатора; [снимок](screenshots/QA06-organizer-event-1366x768.png) | Открыть обзор и проверить доступные landmarks. | QA06 п.5: однозначный основной ориентир страницы; валидная структура main. | Два вложенных видимых main — AppLayout и OrganizerLayout; getByRole("main") возвращает 2. | Открыто: оставить один main, вложенный контейнер сделать обычным. Структуру в QA06 не меняли. | [AppLayout.tsx:100](../../sport-platform/frontend/src/components/AppLayout.tsx#L100); [OrganizerLayout.tsx:65](../../sport-platform/frontend/src/features/organizer/OrganizerLayout.tsx#L65) |
| QA06-22 | major | Навигация судьи; [снимок](screenshots/QA06-judge-unit-375x812.png) | Войти с ролью judge и посмотреть основную навигацию. | ТЗ §1/US-6, QA06 п.4: судья может найти свою панель без ввода технического URL. | Маршруты результатов разрешены judge, но canManageResults в AppLayout включает только admin/operator. Ссылка «Результаты» отсутствует у судьи, вход приходится начинать с прямого URL. | Открыто: согласовать навигацию с разрешёнными ролями; красный тест наличия ссылки. Это логика ролей, не CSS/текстовая правка. | [AppLayout.tsx:31](../../sport-platform/frontend/src/components/AppLayout.tsx#L31) |

## Что проверено и прошло

### Покрытие страниц

Все leaf-маршруты и оба legacy-редиректа из `src/app/App.tsx`: **33 сценария × 3 размера = 99 снимков**, включая отдельно судью и оператора на одной панели. URL-параметры ниже конкретизированы фикстурой: `E=11111111-1111-4111-8111-111111111111`, `R=00000004-1111-4111-8111-000000000001`, `D=M-50-FREE`, `U=00000010-1111-4111-8111-000000000001`; публичный slug `demo`. Редирект `/organizer/events/E/registrations` проверен до `/entries`; `/events/demo` — до `/e/demo`.

| Маршрут / роль | 1366×768 | 1920×1080 | 375×812 |
|---|---|---|---|
| `/login` / аноним | [PNG](screenshots/QA06-login-1366x768.png) | [PNG](screenshots/QA06-login-1920x1080.png) | [PNG](screenshots/QA06-login-375x812.png) |
| `/register` / аноним | [PNG](screenshots/QA06-register-1366x768.png) | [PNG](screenshots/QA06-register-1920x1080.png) | [PNG](screenshots/QA06-register-375x812.png) |
| `/my/registrations` / participant | [PNG](screenshots/QA06-my-registrations-1366x768.png) | [PNG](screenshots/QA06-my-registrations-1920x1080.png) | [PNG](screenshots/QA06-my-registrations-375x812.png) |
| `/my/registrations/R/payment` / participant | [PNG](screenshots/QA06-my-payment-1366x768.png) | [PNG](screenshots/QA06-my-payment-1920x1080.png) | [PNG](screenshots/QA06-my-payment-375x812.png) |
| `/payment-return?registrationId=R` / participant | [PNG](screenshots/QA06-payment-return-1366x768.png) | [PNG](screenshots/QA06-payment-return-1920x1080.png) | [PNG](screenshots/QA06-payment-return-375x812.png) |
| `/events/demo/disciplines/D/register` / participant | [PNG](screenshots/QA06-legacy-register-1366x768.png) | [PNG](screenshots/QA06-legacy-register-1920x1080.png) | [PNG](screenshots/QA06-legacy-register-375x812.png) |
| `/organizer` / organizer | [PNG](screenshots/QA06-organizer-1366x768.png) | [PNG](screenshots/QA06-organizer-1920x1080.png) | [PNG](screenshots/QA06-organizer-375x812.png) |
| `/organizer/events` / organizer | [PNG](screenshots/QA06-organizer-events-1366x768.png) | [PNG](screenshots/QA06-organizer-events-1920x1080.png) | [PNG](screenshots/QA06-organizer-events-375x812.png) |
| `/organizer/events/new` / organizer | [PNG](screenshots/QA06-organizer-events-new-1366x768.png) | [PNG](screenshots/QA06-organizer-events-new-1920x1080.png) | [PNG](screenshots/QA06-organizer-events-new-375x812.png) |
| `/organizer/regulations/create` / organizer | [PNG](screenshots/QA06-organizer-regulations-create-1366x768.png) | [PNG](screenshots/QA06-organizer-regulations-create-1920x1080.png) | [PNG](screenshots/QA06-organizer-regulations-create-375x812.png) |
| `/organizer/events/E` / organizer | [PNG](screenshots/QA06-organizer-event-1366x768.png) | [PNG](screenshots/QA06-organizer-event-1920x1080.png) | [PNG](screenshots/QA06-organizer-event-375x812.png) |
| `/organizer/events/E/program` / organizer | [PNG](screenshots/QA06-organizer-program-1366x768.png) | [PNG](screenshots/QA06-organizer-program-1920x1080.png) | [PNG](screenshots/QA06-organizer-program-375x812.png) |
| `/organizer/events/E/entries` / organizer | [PNG](screenshots/QA06-organizer-entries-1366x768.png) | [PNG](screenshots/QA06-organizer-entries-1920x1080.png) | [PNG](screenshots/QA06-organizer-entries-375x812.png) |
| `/organizer/events/E/heats` / organizer | [PNG](screenshots/QA06-organizer-heats-1366x768.png) | [PNG](screenshots/QA06-organizer-heats-1920x1080.png) | [PNG](screenshots/QA06-organizer-heats-375x812.png) |
| `/organizer/events/E/results` / organizer | [PNG](screenshots/QA06-organizer-results-1366x768.png) | [PNG](screenshots/QA06-organizer-results-1920x1080.png) | [PNG](screenshots/QA06-organizer-results-375x812.png) |
| `/organizer/events/E/registrations` / organizer | [PNG](screenshots/QA06-organizer-registrations-1366x768.png) | [PNG](screenshots/QA06-organizer-registrations-1920x1080.png) | [PNG](screenshots/QA06-organizer-registrations-375x812.png) |
| `/operator/registrations/R` / operator | [PNG](screenshots/QA06-operator-registration-1366x768.png) | [PNG](screenshots/QA06-operator-registration-1920x1080.png) | [PNG](screenshots/QA06-operator-registration-375x812.png) |
| `/operator/payments` / operator | [PNG](screenshots/QA06-operator-payments-1366x768.png) | [PNG](screenshots/QA06-operator-payments-1920x1080.png) | [PNG](screenshots/QA06-operator-payments-375x812.png) |
| `/operator/results` / operator | [PNG](screenshots/QA06-operator-results-1366x768.png) | [PNG](screenshots/QA06-operator-results-1920x1080.png) | [PNG](screenshots/QA06-operator-results-375x812.png) |
| `/operator/competition-units/U?eventId=E` / operator | [PNG](screenshots/QA06-operator-unit-1366x768.png) | [PNG](screenshots/QA06-operator-unit-1920x1080.png) | [PNG](screenshots/QA06-operator-unit-375x812.png) |
| `/operator/competition-units/U?eventId=E` / judge | [PNG](screenshots/QA06-judge-unit-1366x768.png) | [PNG](screenshots/QA06-judge-unit-1920x1080.png) | [PNG](screenshots/QA06-judge-unit-375x812.png) |
| `/admin/registrations/review` / platform_admin | [PNG](screenshots/QA06-admin-review-1366x768.png) | [PNG](screenshots/QA06-admin-review-1920x1080.png) | [PNG](screenshots/QA06-admin-review-375x812.png) |
| `/admin/sports` / platform_admin | [PNG](screenshots/QA06-admin-sports-1366x768.png) | [PNG](screenshots/QA06-admin-sports-1920x1080.png) | [PNG](screenshots/QA06-admin-sports-375x812.png) |
| `/` / аноним | [PNG](screenshots/QA06-home-1366x768.png) | [PNG](screenshots/QA06-home-1920x1080.png) | [PNG](screenshots/QA06-home-375x812.png) |
| `/e/demo` / аноним | [PNG](screenshots/QA06-e-demo-1366x768.png) | [PNG](screenshots/QA06-e-demo-1920x1080.png) | [PNG](screenshots/QA06-e-demo-375x812.png) |
| `/e/demo/start-list` / аноним | [PNG](screenshots/QA06-e-demo-start-list-1366x768.png) | [PNG](screenshots/QA06-e-demo-start-list-1920x1080.png) | [PNG](screenshots/QA06-e-demo-start-list-375x812.png) |
| `/e/demo/schedule` / аноним | [PNG](screenshots/QA06-e-demo-schedule-1366x768.png) | [PNG](screenshots/QA06-e-demo-schedule-1920x1080.png) | [PNG](screenshots/QA06-e-demo-schedule-375x812.png) |
| `/e/demo/live` / аноним | [PNG](screenshots/QA06-e-demo-live-1366x768.png) | [PNG](screenshots/QA06-e-demo-live-1920x1080.png) | [PNG](screenshots/QA06-e-demo-live-375x812.png) |
| `/e/demo/results` / аноним | [PNG](screenshots/QA06-e-demo-results-1366x768.png) | [PNG](screenshots/QA06-e-demo-results-1920x1080.png) | [PNG](screenshots/QA06-e-demo-results-375x812.png) |
| `/e/demo/register` / participant | [PNG](screenshots/QA06-e-demo-register-1366x768.png) | [PNG](screenshots/QA06-e-demo-register-1920x1080.png) | [PNG](screenshots/QA06-e-demo-register-375x812.png) |
| `/e/demo/heats/D-1` / аноним | [PNG](screenshots/QA06-e-demo-heats-1366x768.png) | [PNG](screenshots/QA06-e-demo-heats-1920x1080.png) | [PNG](screenshots/QA06-e-demo-heats-375x812.png) |
| `/events/demo/disciplines/D/results` / аноним | [PNG](screenshots/QA06-legacy-results-1366x768.png) | [PNG](screenshots/QA06-legacy-results-1920x1080.png) | [PNG](screenshots/QA06-legacy-results-375x812.png) |
| `/events/demo` / аноним | [PNG](screenshots/QA06-legacy-event-1366x768.png) | [PNG](screenshots/QA06-legacy-event-1920x1080.png) | [PNG](screenshots/QA06-legacy-event-375x812.png) |

Машинные журналы: [1366](QA06-matrix-1366x768.json), [1920](QA06-matrix-1920x1080.json), [375](QA06-matrix-375x812.json). После исправлений на штатных данных ни один из 99 сценариев не расширяет документ за viewport; ошибок JavaScript нет. Это геометрическая проверка, не гарантия отсутствия любых возможных визуальных дефектов на других данных.

[QA06-states.json](QA06-states.json): **108 GET-сценариев** (27 страниц × empty/loading/HTTP 500/ошибка сети) на 375×812, с отдельными PNG и подсчётом status-landmarks. Пустые заявки, заплывы и результаты показываются без падения. При отсутствии записи на платёжных экранах видна ошибка «Заявка не найдена/недоступна», а не пустой белый экран. Формы без GET-загрузки (`login`, `register`, legacy register, admin review) и редиректы не дублируются в этой матрице; их поля/отправка/возврат проверены штатными тестами и просмотром кода. Состояния мутаций не утверждаются проверенными для каждой формы/каждого viewport.

### Доступность, тексты и UX

- Подписи полей, `th` и русские aria-label кнопки × модалки присутствуют. Основные публичные страницы имеют skip-link. Таблицы кабинета прокручиваются внутри собственных контейнеров; таблицы новых публичных протоколов переносят текст в ширину 375 px.
- `--color-text-muted` на #fff: **5.54:1**, на #f4f7fb: **5.16:1**, на #f7fafc: **5.29:1** — AA проходит. Низкий контраст фокуса — отдельный открытый QA06-11. Полного заявления о соответствии WCAG нет.
- После CSS-правок длинная фамилия (70 знаков), клуб (156 знаков) и непрерывное название соревнования (164 знака) не расширяют страницу. [Заявки](screenshots/QA06-my-long-375x812.png), [обзор](screenshots/QA06-organizer-long-375x812.png), [публичный протокол](screenshots/QA06-start-list-long-375x812.png). Тестовые значения в пределах максимальной длины полей, не реальные персональные данные.
- Просмотр keyboard-навигации по кабинету: ссылки/кнопки доступны Tab, в модалке работают ограничение фокуса и Esc; возвращение фокуса не проходит (QA06-10). CSS для reduced motion присутствует, JS-прокрутка валидатора не учитывает его (QA06-12).
- Подтверждение снятия заявки и force-переформирования есть. На время генерации/импорта элементы блокируются; штатный E2E подтверждает весь поток и восстановление после конфликтов. Сообщения об успехе есть для ввода результатов, импорта и копирования ссылки; для отдельных переходов события/публикации виден только изменившийся статус. Прямые публикации остаются QA06-07.
- Видимые `undefined`, `null`, `NaN`, `Submit`, `Loading`, `Error` на штатных данных не найдены. Известный английский сетевой текст и неполные словари статусов описаны отдельно. Native date-инпуты зависят от локали самого браузера; русскую маску нативного Android/iOS-контрола этот прогон не подтверждает.

Количество **нажатий** от открытого обзора события (выбор/ввод файла и значений полей не считается кликом):

| Действие | Нажатия | Путь |
|---|---:|---|
| Импорт M | 4 | «Заявки» → «Импорт из Excel» → выбор файла → «Импортировать»; загрузка шаблона ещё +1 |
| Посев события | 2 | «Заплывы» → «Сформировать заплывы»; при force ещё +1 подтверждение |
| Публикация события | 1 | «Опубликовать» на обзоре; отсутствие подтверждения — QA06-07 |
| Публикация стартового протокола | 2 | «Заплывы» → «Опубликовать стартовый протокол» |
| Публикация результатов вида | 2 | «Результаты» → «Опубликовать результаты вида» (если готовы) |
| Печать старта/итогов | 2 до диалога Chrome | «Заплывы»/«Результаты» → ссылка печати с `print=1`; кнопка сохранения PDF в Chrome ещё +1 |

### Печать и PWA

[Стартовый PDF](pdf/QA06-start-list.pdf) и [итоговый PDF по группам](pdf/QA06-results.pdf): по **2 страницы A4 portrait**, CSS-поля **15 мм**, таблицы 10 pt; шапка с названием, датой и площадкой повторяется на обеих страницах. Все 6 заплывов остаются целыми; дорожки/ФИО/год/клуб/время не обрезаны. Навигация, кнопки и переключатель групп скрыты. Рендеры страниц находятся рядом с остальными PNG. Это компактные MSW-данные; печать полного набора 200 через реальный посев не проверена. Голубоватый фон до правки исправлен в QA06-15.

Production manifest: `Flounder`/`Flounder`, `lang:ru`, `display:standalone`, theme `#0b5b7b`; иконки 192/512 и maskable 512 есть. Production-сборка не содержит mockServiceWorker/MSW. Зарегистрирован `sw.js`, фактически создан cache `flounder-public-api-v1`; после загрузки online и offline-перезагрузки остаются табло/дорожки, виден [понятный статус связи](screenshots/QA06-pwa-offline-375x812.png). После подключения обновление продолжается. Статика: CacheFirst, публичный API: NetworkFirst — проверены конфигурация и фактический offline cache. CDP `Page.getInstallabilityErrors` в **обычном persistent-профиле** вернул `[]`.

### Проверки кода

| Проверка | Результат |
|---|---|
| `mvn -q test` | **252 теста, 0 failures/errors/skips**, exit 0; Docker/Testcontainers доступны, Maven/JDK из `/workspace/tools`. При shutdown Surefire завершил fork после 30 с ожидания; тестовых ошибок нет |
| `npm test` | **3 Node + 40 Vitest**, все зелёные |
| `npm run build` | Успех, включая production PWA |
| `npm run lint` | Exit 0; существующие warnings React/hooks, ошибок нет |
| Штатный `test:organizer` | **15/15** |
| Штатный public E2E | **3/4**; PWA-кейс остановился на Chromium `in-incognito`, а не на дефекте приложения; тест не ослаблен |
| QA06 матрица | **3/3**, 99 снимков; состояния **108**, 1/1 сценарий захвата |
| QA06 регрессии | **24 проверки: 10 зелёных, 14 ожидаемых падений**, не считаются исправленными; ожидаемые падения проверяют QA06-06…14, 18, 21, 22 (публикация — три теста); QA06-20 зелёный после T11 |

Ожидаемые падения помечены `test.fail`, чтобы отчётный запуск мог завершиться успешно. При `QA06_EXPECT_FIX=1` метки снимаются и **исходные assertions падают**, фиксируя открытые функциональные/доступностные дефекты. Старые тесты не удалены и не ослаблены. Проверены фактические падения строгого прогона; новые случаи 18/21/22 также воспроизводятся в своих assertions. В UI исправлены только CSS/тексты; остальные изменения — доказательства, тесты и команда запуска.

Из `sport-platform/frontend`:

```sh
npm ci
npm test
npm run build
npm run lint
# If Chromium is not installed, run npx playwright install chromium.
PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium npm run test:qa06 -- qa06-regression
QA06_EXPECT_FIX=1 PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium npm run test:qa06 -- qa06-regression
QA06_CAPTURE=1 QA06_SCREENSHOTS=../../docs/qa/screenshots PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium npm run test:qa06 -- qa06-audit
```

Сервер MSW запускается конфигом автоматически. Для штатного public E2E нужен production build и Chromium Playwright; в этом окружении применялся локальный override executablePath для системного Chromium (исходный конфиг не менялся). После rebase проверены изменившиеся публичные страницы и их состояния; остальные снимки относятся к неизменившимся компонентам. Повторный захват перезаписывает только QA06-артефакты. Частичный захват: `QA06_ROUTE_FILTER` (имена из helpers) либо `QA06_STATE_FILTER=empty,loading`; журналы сливаются с существующими. Одновременным запускам задавать разные `--output work/<name>`, иначе Playwright удаляет временные trace-файлы другого прогона.

## Не удалось проверить и почему

- Реальная установка на **Android Chrome**, iOS Safari и Lighthouse на физическом устройстве: устройства в окружении нет. CDP installability и desktop offline-PWA проверены отдельно; это не заявление об установленном Android-приложении. В штатном тесте incognito мешает самой проверке installability, поэтому он сохраняет своё падение.
- Реальные backend-сеансы всех пяти ролей, production RBAC, импорт/повторный импорт и посев полного M через UI: работа выполнялась с разрешёнными MSW-моками. Роль судьи проверяет UI-допуск; дополнительные моки не подтверждают серверные права. Backend-тесты пройдены, но не заменяют демо на sportgate.pro.
- Печать реального большого посева M/особо длинных многостраничных групп; нет результатов такого сида в этом UI-прогоне. PDF и границы заплывов проверены на 6 заплывах публичной фикстуры.
- Загрузка/ошибки отправки **каждой** формы на всех трёх размерах и чтение экранов настоящим screen reader: проверены штатные тесты, DOM/keyboard, 27 GET-страниц на мобильном размере и все страницы на штатных данных; исчерпывающий мутационный/AT-прогон не выполнялся.

## Глоссарий и расхождения

| Термин | Предлагаемое единое употребление | Найденные варианты / смысл |
|---|---|---|
| Соревнование | Само событие плавания | «Событие», «мероприятие», «соревнование» на соседних экранах; Event в коде остаётся |
| Вид программы | Дистанция + стиль + пол/группы | «Дисциплина», «вид», «вид программы»; «вид спорта» — отдельный справочник, не синоним |
| Заплыв | Heat, конкретная группа дорожек | Старый протокол использует «соревновательная единица», «дорожка / позиция», «заплыв/матч/финал»; «заезд» в видимом UI не найден |
| Заявка | Участник + один вид программы | «Регистрация» — процесс/период, не отдельная заявка; аккаунт — регистрация пользователя |
| Заявочное время | Seed time, формат мм:сс.сс | `NT` — без заявочного времени; не DNS и не отсутствие результата; кабинет показывает «—», публичный протокол NT |
| Результат | Время финиша мм:сс.сс | DNS — не стартовал; DNF — не финишировал; DSQ — дисквалифицирован; официальные сокращения допустимы |
| Стартовый протокол | Заплывы и дорожки до старта | «Стартовый лист», «стартовый протокол» — унифицировать в D01 |
| Итоговый протокол | Результаты по виду и возрастным группам | «Таблица результатов», «протокол», «абсолют» — общий зачёт против «по группам» |
| Табло | Текущий и следующие заплывы, поиск | `live` остаётся техническим URL; видимый `Live` исправлен |
| Снята | WITHDRAWN, участник снят с вида | Не путать с «Снять с публикации»; CANCELLED — отменена, REJECTED — отклонена |
| На исправлении | NEEDS_CORRECTION | «Требует уточнения», «на исправлении», «вернуть» расходятся; выбрать одну формулировку при D01 |
| Оплата | Процесс оплаты заявки | «Оплачено» для факта/кнопки, «Оплачена» для заявки; MANUAL отображается «Ручная оплата» |

После малых правок остались семантические расхождения старого и нового UI (QA06-17), технические UUID вместо контекста (QA06-19), а также грамматика «4 участников» в старом протоколе. Все эти места переданы на дальнейшую унификацию, без массовой замены JSX в QA06.

## Захардкоженные цвета и размеры

Полный построчный список: [QA06-hardcoded-values.tsv](QA06-hardcoded-values.tsv), воспроизводится из корня `python scripts/qa/qa06-hardcoded.py`. Это инвентарь литералов, **не утверждение, что каждый литерал — дефект**: 15 мм/A4/10 pt печати, проценты колонок, размеры PWA-иконок и нули/размеры доступностного srOnly имеют назначение. `globals.css` исключён как источник токенов; матрица CSS/JSX вне него предназначена D01. Сканер находит HEX/RGB/HSL, именованные цвета в декларациях, размеры с единицами и JSX size/width/height, но не обещает разбирать все CSS-выражения как AST.

Зафиксировано **1414 строк с литералами в 21 файлах**. Примеры: `#0d6178`, `#084b5d`, `#6b7280`, `#111827`, `#e5e7eb`, `#fecaca`, `#fff7f7`, `#fff3d6`, `#674200`, белый/чёрный; `0.5rem`, `0.75rem`, `1rem`, `999px`, `680px`, `760px`, `820px`, `1120px`, `140` у QR, `48px` номера заплыва. Часть цветов совпадает с токенами, часть создаёт параллельную палитру. Нет общей шкалы отступов/типографики, поэтому её добавление остаётся D01.

| Файл | Строк с литералами |
|---|---:|
| [src/components/AppLayout.module.css](../../sport-platform/frontend/src/components/AppLayout.module.css) | 21 |
| [src/features/admin/AdminPages.css](../../sport-platform/frontend/src/features/admin/AdminPages.css) | 13 |
| [src/features/auth/LoginPage.module.css](../../sport-platform/frontend/src/features/auth/LoginPage.module.css) | 37 |
| [src/features/auth/RegisterPage.module.css](../../sport-platform/frontend/src/features/auth/RegisterPage.module.css) | 47 |
| [src/features/events/EventsPage.module.css](../../sport-platform/frontend/src/features/events/EventsPage.module.css) | 69 |
| [src/features/organizer/CreateEventPage.module.css](../../sport-platform/frontend/src/features/organizer/CreateEventPage.module.css) | 69 |
| [src/features/organizer/OrganizerDashboardPage.module.css](../../sport-platform/frontend/src/features/organizer/OrganizerDashboardPage.module.css) | 88 |
| [src/features/organizer/OrganizerEventPage.module.css](../../sport-platform/frontend/src/features/organizer/OrganizerEventPage.module.css) | 69 |
| [src/features/organizer/OrganizerEventsPage.module.css](../../sport-platform/frontend/src/features/organizer/OrganizerEventsPage.module.css) | 93 |
| [src/features/organizer/OrganizerLayout.module.css](../../sport-platform/frontend/src/features/organizer/OrganizerLayout.module.css) | 32 |
| [src/features/organizer/OrganizerOverviewPage.tsx](../../sport-platform/frontend/src/features/organizer/OrganizerOverviewPage.tsx) | 1 |
| [src/features/payments/OperatorPaymentsPage.module.css](../../sport-platform/frontend/src/features/payments/OperatorPaymentsPage.module.css) | 54 |
| [src/features/payments/OperatorRegistrationPage.module.css](../../sport-platform/frontend/src/features/payments/OperatorRegistrationPage.module.css) | 79 |
| [src/features/payments/PaymentPage.module.css](../../sport-platform/frontend/src/features/payments/PaymentPage.module.css) | 90 |
| [src/features/public/Public.module.css](../../sport-platform/frontend/src/features/public/Public.module.css) | 155 |
| [src/features/registrations/MyRegistrationsPage.module.css](../../sport-platform/frontend/src/features/registrations/MyRegistrationsPage.module.css) | 84 |
| [src/features/registrations/RegistrationPage.module.css](../../sport-platform/frontend/src/features/registrations/RegistrationPage.module.css) | 56 |
| [src/features/regulations/CreateRegulationPage.module.css](../../sport-platform/frontend/src/features/regulations/CreateRegulationPage.module.css) | 63 |
| [src/features/results/CompetitionUnitsPage.module.css](../../sport-platform/frontend/src/features/results/CompetitionUnitsPage.module.css) | 71 |
| [src/features/results/JudgePanelPage.module.css](../../sport-platform/frontend/src/features/results/JudgePanelPage.module.css) | 134 |
| [src/features/results/PublicDisciplineResultsPage.module.css](../../sport-platform/frontend/src/features/results/PublicDisciplineResultsPage.module.css) | 89 |

Дополнительно вне src: `vite.config.ts` фиксирует PWA `theme_color:#0b5b7b`, `background_color:#ffffff` и иконки 192/512. Это требования §6.5, не предложение изменить их в QA06.
