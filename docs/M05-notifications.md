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
откат доменного изменения также удаляет запись outbox.

В тексте M05 одновременно указаны «та же транзакция» и
`@ApplicationModuleListener`. У этой аннотации по умолчанию AFTER_COMMIT,
`@Async` и REQUIRES_NEW: она не обеспечивает одну транзакцию с издателем.
Здесь приоритет отдан явно требуемой атомарности. Отдельный Modulith publication
для этих синхронных обработчиков не нужен: очередь доставки — outbox.
Существующий `RegistrationWithdrawn` остаётся у слушателя scheduling;
у M05 нет шаблона снятия заявки, поэтому это событие не создаёт письмо.

Воркер запускается каждые 10 секунд после завершения предыдущего запуска,
обрабатывает до 50 строк с `FOR UPDATE SKIP LOCKED` и держит блокировки до конца
транзакции. Неудачные попытки повторяются через 10, 20, 40, 80 секунд;
после пятой попытки статус FAILED, автоматических повторов больше нет.
Успешная попытка также учитывается в attempts. Ошибки SMTP сохраняются как
русское сообщение с именем класса исключения, без исходного сообщения, тела,
пароля или reset-токена. По одному publicationId и адресу повторная обработка
публикации стартов/результатов не создаёт второй строки.

Доставка имеет семантику at-least-once: если SMTP принял письмо, а процесс упал
до commit отметки SENT, возможен повтор. Блокировка защищает от одновременной
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
| `results.ResultsPublishedEvent` | `UUID publicationId, UUID disciplineId` | PublishCompetitionUnitUseCase после валидации и публикации результатов | results_published |

Публикация стартового протокола создаёт событие только если были DRAFT-заплывы.
Публикация результатов заплыва уведомляет подтверждённых участников его вида;
новая публикация другого заплыва того же вида является отдельным событием.
Для протоколов/результатов используются только CONFIRMED, PAID, ADMITTED.
На один адрес в рамках одной публикации создаётся одно письмо, даже если у
аккаунта несколько заявок.

`RegistrationLookup` разрешает владельца профиля, `UserLookup` даёт email аккаунта.
Для прикреплённого детского профиля это аккаунт, которому он принадлежит.
Отдельной модели связи родитель–ребёнок в текущем main нет; произвольный email
из клиентского snapshot не используется. Имя берётся из snapshot участника.
Профили импортированных спортсменов без привязанного аккаунта, отсутствующая
заявка/пользователь/событие пропускаются без ошибки. Контекст соревнования
(организация, название, дата, slug) предоставляется публичным
`events.NotificationEventLookup`; чужие репозитории не используются.

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

## Совместимость с M02 / PR #29

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
уже сформированные M02, сохраняются; HTML шаблон экранирует тело.
Организатор не может выключить восстановление пароля. У reset-писем нет event_id,
они не попадают в журнал какого-либо организатора.

После слияния M02 удалить только временный
`sport-platform/src/main/java/com/acme/sportplatform/common/LoggingMailSender.java`.
`common/MailSender.java` оставить единственным интерфейсом; копию в notifications
не создавать. Временный логгер M02 может безопасно существовать до удаления:
у нового логгера отдельное bean-имя notificationLoggingMailSender, а выбор
MailSender через @Primary направляет M02 в outbox. Новую реализацию
`notifications.infrastructure.LoggingMailSender` оставить для MAIL_ENABLED=false.
Её тело письма никогда не выводится в INFO.

При разрешении конфликтов application.yml/compose/.env сохранить настройки M02
и app.mail M05. APP_PUBLIC_URL в production example оставить пустым согласно
уточнению M05; compose использует `https://${DOMAIN}` при пустом значении.
V029/V030 (M02) и V032 (M05) не переименовывать. В первый деплой объединённой
версии включить все согласованные миграции; если V032 применён раньше V029/V030,
порядок применения более ранних миграций нужно отдельно согласовать с оператором
Flyway, не исправлять историю миграций автоматически.

## Конфигурация

| Env | Свойство | Default |
| --- | --- | --- |
| MAIL_HOST | app.mail.host | пусто |
| MAIL_PORT | app.mail.port | 587 |
| MAIL_USERNAME | app.mail.username | пусто |
| MAIL_PASSWORD | app.mail.password | пусто |
| MAIL_FROM | app.mail.from | noreply@localhost |
| MAIL_ENABLED | app.mail.enabled | false |
| APP_PUBLIC_URL | app.public-url | http://localhost:5173; production compose: https://${DOMAIN} |

В infra/.env.prod.example все семь значений пустые, с комментариями.
Compose подставляет defaults для пустых MAIL_* и передаёт настройки в API.
SMTP использует STARTTLS, UTF-8, multipart plain-text + HTML, таймауты
подключения/чтения/записи по 5 секунд. `app.mail.worker-enabled=false` отключает
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
5. Опубликовать DRAFT-заплывы, затем результаты заплыва с валидными результатами.
   Проверить start_list_published/results_published, адресатов своего вида и ссылку
   `/e/{slug}/start-list`. Тела можно посмотреть только локально при
   `logging.level.com.acme.sportplatform.notifications.infrastructure.LoggingMailSender=DEBUG`.
6. После объединения с M02 запросить восстановление пароля: строка password_reset
   в outbox без event_id, тестовая доставка в лог, исходная reset-ссылка в DEBUG.
   В журнал события такое письмо не попадает.
7. Автотесты: `cd sport-platform && mvn -q test`; frontend:
   `npm ci && npm test && npm run build && npm run lint`.

SMTP проверяется автоматически через мок JavaMailSender; реальная доставка,
production-стенд и внешние адресаты в этой задаче не используются.
Telegram/push, массовые кампании, уведомления «ваш заплыв через N», отдельные
письма закрытия регистрации/WAITLISTED и ручной retry FAILED вне объёма M05.
