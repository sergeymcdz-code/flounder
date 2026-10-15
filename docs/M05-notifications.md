# M05: e-mail и outbox

Модуль `notifications` подписывается на доменные события. Подавать заявки,
рассматривать их и публиковать протоколы можно без SMTP: `MAIL_ENABLED=false`
по умолчанию. Письма сохраняются в outbox, воркер пишет адресата и тему в INFO,
тело — только в DEBUG. После такой тестовой доставки строка получает SENT;
это означает обработку выбранным транспортом, а не подтверждение получения
письма адресатом.

## Транзакции и доставка

Используются синхронные `@EventListener` и `Propagation.MANDATORY`. Spring
Modulith использует тот же `ApplicationEventPublisher`, поэтому модули-источники
не зависят от notifications. Ошибка записи outbox откатывает доменную транзакцию;
откат доменного изменения также удаляет запись outbox. Недоступность outbox или
таблицы настроек намеренно отменяет доменную операцию: без этого атомарность
теряется. Невалидный snapshot имени обрабатывается с резервным именем участника.

В тексте M05 одновременно указаны «та же транзакция» и
`@ApplicationModuleListener`. У этой аннотации по умолчанию AFTER_COMMIT,
`@Async` и REQUIRES_NEW: она не обеспечивает одну транзакцию с издателем.
Здесь приоритет отдан явно требуемой атомарности. Отдельный Modulith publication
для этих синхронных обработчиков не нужен: очередь доставки — outbox.
Существующий `RegistrationWithdrawn` остаётся у слушателя scheduling;
у M05 нет шаблона снятия заявки, поэтому это событие не создаёт письмо.

Воркер запускается каждые 10 секунд после завершения предыдущего запуска,
выбирает до 50 кандидатов и для каждого выполняет отдельную REQUIRES_NEW
транзакцию через TransactionTemplate: повторная проверка PENDING/срока/попыток,
claim `FOR UPDATE SKIP LOCKED`, отправка, отметка результата и commit.
Блокировка и JDBC-соединение удерживаются только на время одного письма;
уже отправленные строки не откатываются при сбое следующей строки. Неудачные попытки повторяются через 10, 20, 40, 80 секунд;
после пятой попытки статус FAILED, автоматических повторов больше нет.
Успешная попытка также учитывается в attempts. Ошибки SMTP сохраняются как
русское сообщение с именем класса исключения, без исходного сообщения, тела,
пароля или reset-токена. У стартового протокола ключ привязан к publicationId
и адресу; у результатов — `results_published:{competitionUnitId}:{email}`.
Снятие результатов с публикации и повторная публикация того же заплыва не
создают второе письмо тому же адресату.

При SENT и при окончательном FAILED payload всех шаблонов очищается до `{}`.
Тело и reset-ссылка сохраняются только пока PENDING требует доставки/повтора.
Метаданные журнала (адресат, шаблон, статус, попытки, даты, безопасная ошибка)
сохраняются. Ручной retry FAILED отсутствует, поэтому очищенное тело не нужно
для дальнейшей обработки.

Доставка имеет семантику at-least-once: если SMTP принял письмо, а процесс упал
до commit отметки SENT, возможен повтор только текущего письма; ранее
закоммиченные письма батча не повторяются. Блокировка защищает от одновременной
отправки двумя воркерами, но не от этого разрыва между SMTP и PostgreSQL.
FAILED остаётся в журнале; endpoint ручной повторной отправки не добавлен.

## События и получатели

Все новые record находятся в корне модуля-источника:

| Событие | Поля | Источник | Шаблон |
| --- | --- | --- | --- |
| `registrations.RegistrationSubmittedEvent` | `UUID registrationId` | CreateRegistrationUseCase при SUBMITTED, ChangeRegistrationUseCase при повторной подаче | registration_submitted |
| `registrations.RegistrationConfirmedEvent` | `UUID registrationId` | ReviewRegistrationUseCase после подтверждения | registration_confirmed |
| `registrations.RegistrationRejectedEvent` | `UUID registrationId, String reason` | ReviewRegistrationUseCase после отклонения | registration_rejected |
| `scheduling.StartListPublishedEvent` | `UUID publicationId, UUID eventId, UUID disciplineId` | PublishUnitsUseCase; disciplineId=null означает всё событие | start_list_published |
| `results.ResultsPublishedEvent` | `UUID competitionUnitId, UUID disciplineId, List<UUID> registrationIds` | PublishCompetitionUnitUseCase после валидации и публикации результатов | results_published |

Публикация стартового протокола создаёт событие только если были DRAFT-заплывы.
Публикация результатов заплыва уведомляет только участников этого заплыва:
ids взяты из entryRepository.findByCompetitionUnitId(unitId), а слушатель
использует RegistrationLookup.findByIdIn(ids). Участники других заплывов того
же вида и заявки без назначенного заплыва не получают это письмо.
Повторная публикация заплыва не создаёт вторую строку для того же адресата.
Для протоколов/результатов используются только CONFIRMED, PAID, ADMITTED.
На один адрес в рамках одной публикации создаётся одно письмо, даже если у
аккаунта несколько заявок. Настройка организации проверяется один раз перед
рассылкой; аккаунты получателей загружаются пакетно через UserLookup.findAllByIds.

`RegistrationLookup` разрешает владельца профиля, `UserLookup` даёт email аккаунта.
Для прикреплённого детского профиля это аккаунт, которому он принадлежит.
Отдельной модели связи родитель–ребёнок в текущем main нет; произвольный email
из клиентского snapshot не используется. Имя берётся из snapshot участника.
Профили импортированных спортсменов без привязанного аккаунта, отсутствующая
заявка/пользователь/событие пропускаются без ошибки. Контекст соревнования
(организация, название, дата, slug) предоставляется публичным
`events.NotificationEventLookup`; чужие репозитории не используются.
Дата конвертируется в ZoneId.systemDefault() (TZ приложения, на стенде — Самара)
и форматируется `d MMMM yyyy` с русской локалью, например «19 октября 2026».
Старт в 00:30 по Самаре не превращается во вчерашний день UTC.

### Контракт для M06

M06 публикует события внутри своей транзакции через ApplicationEventPublisher.

| Действие M06 | Контракт | Обработка M05 |
| --- | --- | --- |
| Продвижение WAITLISTED → SUBMITTED | Уже существующий `registrations.RegistrationSubmittedEvent(UUID registrationId)` | Письмо registration_submitted с соблюдением настройки организации |
| Закрытие регистрации | Будущий `events.EventRegistrationClosedEvent(UUID eventId, Instant closedAt)` | Нет отдельного шаблона M05; событие допускается и игнорируется |
| Постановка в лист ожидания | Будущий `registrations.RegistrationWaitlistedEvent(UUID registrationId, int waitlistPosition)` | Не считать подачей SUBMITTED; отдельный шаблон вне шести шаблонов M05 |

Последние два имени/контракта закреплены для согласования с M06 в docs;
классы здесь не создаются и не требуются для старта приложения. Их отсутствие
не мешает работе текущих уведомлений. Неподдерживаемое событие не даёт письма и
не вызывает ошибку. Когда M06 появится, для продвижения достаточно публиковать
RegistrationSubmittedEvent; новый тип для этого действия не нужен. При слиянии
CreateRegistrationUseCase сохранять проверку фактического статуса SUBMITTED,
чтобы WAITLISTED не получал письмо «заявка принята».

## Интеграция с M02

Сохранён один контракт, побайтово совпадающий с M02:

```java
package com.acme.sportplatform.common;
public interface MailSender {
    void send(String recipient, String subject, String body);
}
```

`notifications.application.OutboxMailSender` реализует его и выбран через
`@Primary`. Восстановление пароля M02 создаёт password_reset в транзакции
сохранения токена; worker отправляет его тем же транспортом. Русское тело и URL,
уже сформированные M02, сохраняются до доставки; HTML шаблон экранирует тело.
При SENT/FAILED payload очищается, включая исходную reset-ссылку.
Организатор не может выключить восстановление пароля. У reset-писем нет event_id,
они не попадают в журнал какого-либо организатора.

Ветка перебазирована на main с M02 #29, M09 #25 и QA01-followups #39.
Временный `common/LoggingMailSender.java` удалён. `common/MailSender.java` —
единственный интерфейс, OutboxMailSender — единственный bean MailSender,
`notifications.infrastructure.LoggingMailSender` — единственный логгер писем.
Сквозной тест PasswordService.requestReset → outbox → SENT входит в M05;
AuthHardeningIntegrationTest продолжает проверять правила reset с мок-транспортом.

Настройки M02 и app.mail M05 сохранены: APP_PUBLIC_URL/app.public-url указан
один раз, production example оставляет значение пустым, compose использует
`https://${DOMAIN}`. В QA04 сохранены все шесть строк M02 и три строки M05.
V029/V030 M02 уже в main; V032 не переименовывать. Согласованную V031 M04
деплоить перед V032 или одной партией: Flyway out-of-order по умолчанию выключен.
Историю миграций автоматически не менять.

## Конфигурация

| Env | Свойство | Default |
| --- | --- | --- |
| MAIL_HOST | app.mail.host | пусто |
| MAIL_PORT | app.mail.port | 587 |
| MAIL_SECURITY | app.mail.security | auto |
| MAIL_USERNAME | app.mail.username | пусто |
| MAIL_PASSWORD | app.mail.password | пусто |
| MAIL_FROM | app.mail.from | noreply@localhost |
| MAIL_ENABLED | app.mail.enabled | false |
| APP_PUBLIC_URL | app.public-url | http://localhost:5173; production compose: https://${DOMAIN} |

В infra/.env.prod.example MAIL_SECURITY=auto, остальные значения почты пустые,
с комментариями. Compose подставляет defaults для пустых MAIL_* и передаёт
настройки в API. MAIL_SECURITY принимает auto, starttls, ssl или none:

- auto: порт 465 → implicit SSL/TLS, любой другой порт → обязательный STARTTLS;
- ssl: TLS с начала соединения, разрешены TLSv1.2/TLSv1.3, STARTTLS выключен;
- starttls: SSL с начала соединения выключен, STARTTLS включён и обязателен;
- none: обе защиты выключены, только для локальных MailHog/Mailpit.

Для Timeweb: MAIL_HOST=smtp.timeweb.ru, MAIL_PORT=465 и MAIL_SECURITY=ssl
(либо auto); альтернатива — MAIL_PORT=587 и MAIL_SECURITY=starttls (либо auto).
MAIL_USERNAME — полный адрес ящика, MAIL_PASSWORD — пароль этого ящика;
MAIL_FROM — адрес отправителя. Для проверки на стенде после настройки SMTP
включить MAIL_ENABLED=true, открыть «Забыли пароль?» для существующего тестового
аккаунта, дождаться письма и перейти по ссылке восстановления. У outbox должна
быть отметка SENT, payload после доставки очищен. Для смены порта/режима
пересоздать API-контейнер с новой конфигурацией.

Если MAIL_ENABLED=true и host пустой/состоит из пробелов, при старте выводится
WARN без логина/пароля; приложение продолжает стартовать. Неизвестный режим
security отклоняется при конфигурации, без неявного отключения защиты.
SMTP использует UTF-8, multipart plain-text + HTML; таймауты подключения,
чтения и записи сохраняются по 5 секунд. `app.mail.worker-enabled=false` отключает
планировщик для тестов; ручной вызов worker при этом остаётся доступен в коде.

V032__notification_outbox.sql добавляет notification_outbox и
organization_notification_settings. Дополнительные поля outbox: event_id
(изолированный журнал), next_attempt_at (пауза), delivery_key (дедупликация).
Индексы: status/created_at и event_id/created_at; delivery_key уникален.
Существующие миграции не менялись.

## API и интерфейс

Все три mappings зарегистрированы в security/qa04-endpoints.tsv.

| Метод / путь | Контракт | Доступ |
| --- | --- | --- |
| GET /api/v1/organizations/{id}/notification-settings | Массив `{templateCode,enabled}`, пять типов, defaults=true | organizer с memberRole owner/admin; platform_admin |
| PUT /api/v1/organizations/{id}/notification-settings | Такой же массив; частичное обновление, ответ — полные пять настроек | То же |
| GET /api/v1/events/{eventId}/notifications?status=&page= | `{items,page,totalElements,totalPages}`, размер страницы 50, page от 0 | organizer/operator/platform_admin + EventAccessService.assertCanManage |

Пустой PUT-массив ничего не меняет. Неизвестный/повторный templateCode,
password_reset, отсутствующий enabled, отрицательная page или неизвестный status
дают 400 с notifications.*. Для GET статус нужно опустить либо передать
PENDING/SENT/FAILED. Items содержит id, recipientEmail, templateCode, status,
attempts, lastError, createdAt, sentAt; тела и payload отсутствуют.
Изменение настроек относится к будущим событиям, уже созданные письма остаются
в очереди.

Фронт: `/organizer/notification-settings` в навигации кабинета и вкладка
`/organizer/events/:eventId/notifications` с фильтром, таблицей, страницами,
обновлением, состояниями загрузки/ошибки/пустого журнала. MSW поддерживает
настройки и пустой журнал для demo-режима.

## Ручная проверка без реальной почты

1. Поднять локальную PostgreSQL и backend по AGENTS.md. MAIL_ENABLED не задавать
   или задать false, APP_PUBLIC_URL=http://localhost:5173. Проверить применение V032.
2. Запустить frontend. От имени участника подать заявку; организатором подтвердить
   другую заявку либо отклонить с причиной. В outbox должны появиться соответствующие
   строки, затем за 10 секунд — DEV mail to/subject в INFO и SENT/attempts=1.
3. Открыть вкладку «Уведомления» события. Проверить адресат, тип письма, статус,
   фильтр, отсутствие тел и payload. Чужой организатор получает 403.
4. Владельцем/admin организации открыть настройки, выключить «Заявка подтверждена»,
   сохранить, подтвердить следующую заявку. Нового письма этого типа быть не должно.
   Прежние строки журнала сохраняются. Обычный member/organizer настройки менять не может.
5. Опубликовать DRAFT-заплывы, затем результаты двух заплывов одного вида.
   Проверить start_list_published/results_published, адресатов конкретного заплыва и ссылку
   `/e/{slug}/start-list`. Тела можно посмотреть только локально при
   `logging.level.com.acme.sportplatform.notifications.infrastructure.LoggingMailSender=DEBUG`.
6. Запросить восстановление пароля M02: строка password_reset PENDING в outbox
   без event_id, тестовая доставка в лог, исходная reset-ссылка только в DEBUG.
   После SENT проверить payload::text='{}'. То же очищение происходит после пятой
   неудачной попытки при FAILED. В журнал события такое письмо не попадает.
7. Автотесты: `cd sport-platform && mvn -q test`; frontend:
   `npm ci && npm test && npm run build && npm run lint`.

SMTP проверяется автоматически через мок JavaMailSender; реальная доставка,
production-стенд и внешние адресаты в этой задаче не используются.
Telegram/push, массовые кампании, уведомления «ваш заплыв через N», отдельные
письма закрытия регистрации/WAITLISTED и ручной retry FAILED вне объёма M05.

## S04: изменение расписания

`scheduling.ScheduleChangedEvent` содержит eventId, unitIds, changedAt и неизменяемый
снимок changes (unitId, previousScheduledAt, scheduledAt). Слушатель создаёт
`schedule_changed` в той же транзакции outbox, что и остальные письма M05.
Preview не создаёт письмо. Получатели — аккаунты с e-mail подтверждённых заявок
на действующих дорожках изменённых заплывов; импорт без аккаунта пропускается.
Один аккаунт получает одно письмо со всеми его заплывами. Время HH:mm и дата
переводятся в часовой пояс площадки, как в T11; ссылка ведёт на `/e/{slug}/schedule`.
Настройка организации «Изменение расписания» включена по умолчанию.
Каждый повторный сдвиг создаёт новое письмо даже в пределах десяти минут;
batching рассылки — отдельная задача.
