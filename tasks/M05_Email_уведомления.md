# M05. Уведомления по e-mail: outbox, шаблоны, базовые события

Прочитай `AGENTS.md`, `docs/03_ТЗ_MVP_50_соревнований.md` §3.7 и §5 (outbox). Ветка `feat/M05-email-notifications` от актуального `main`. Новый модуль `notifications` (`com.acme.sportplatform.notifications`). Один PR. Другие модули **не** вызывают notifications напрямую — только публикуют события Spring Modulith (`ApplicationEventPublisher`), notifications на них подписывается.

## Что сделать
1. Инфраструктура: `spring-boot-starter-mail`; настройки `app.mail.*` (host, port, username, password, from, enabled) из переменных окружения (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_ENABLED`); при `MAIL_ENABLED=false` — `LoggingMailSender` (пишет тему и адресата в лог; тело — только в DEBUG). Добавить в `infra/.env.prod.example` с комментариями. Интерфейс `MailSender` в `notifications` + его использование задачей M02 (если M02 завёл временный интерфейс в `common` — согласовать в PR, какой остаётся).
2. Outbox: миграция `V032__notification_outbox.sql`: `notification_outbox` (id, recipient_email, template_code, payload jsonb, status ∈ PENDING|SENT|FAILED, attempts, last_error, created_at, sent_at; индекс по status, created_at). Запись создаётся в той же транзакции, что и доменное событие (Modulith `@ApplicationModuleListener` + сохранение outbox), отправка — `@Scheduled(fixedDelay=10s)` воркером с батчем 50, ретраи до 5 с экспоненциальной паузой; `@EnableScheduling` в конфиге модуля.
3. Шаблоны (Thymeleaf, `resources/templates/mail/`, русский, plain-text + HTML): `registration_submitted` (участнику: заявка принята, что дальше), `registration_confirmed`, `registration_rejected` (с причиной), `start_list_published` (участникам события: ссылка `/e/{slug}/start-list` и «найти себя»), `results_published` (участникам вида), `password_reset` (для M02). Переменные: имя, событие, дата, ссылки; `APP_PUBLIC_URL` из настроек.
4. События-источники: найти существующие доменные события в `registrations`, `scheduling` (публикация заплывов), `results` (публикация результатов); если их нет — добавить минимальные record-события в корне модуля-источника (`RegistrationConfirmedEvent` и т.п.) и перечислить в PR. Адресат — email профиля/родителя (через `RegistrationLookup`/`UserLookup`).
5. Настройки организатора (минимум): таблица `organization_notification_settings` (organization_id, template_code, enabled) в той же миграции; `GET/PUT /api/v1/organizations/{id}/notification-settings` (owner/admin); по умолчанию всё включено. Фронт: простая страница переключателей в кабинете организатора.
6. Журнал: `GET /api/v1/events/{eventId}/notifications?status=&page=` для организатора — что и кому отправлено (без тел писем), фронт — вкладка «Уведомления» в событии с таблицей.
7. Тесты: событие → запись в outbox в той же транзакции (откат транзакции = нет записи), воркер отправляет через мок `JavaMailSender`, ретраи и FAILED после 5, шаблон рендерится с кириллицей, выключенная настройка организатора → письма нет, доступ чужого организатора к журналу → 403.

## Не делать
Telegram, push, массовые рассылки, уведомления «ваш заплыв через N» — следующие задачи.

## Проверка
`mvn -q test`, `npm test && npm run build && npm run lint`. В PR: переменные окружения, миграция V032, события-источники, как проверить вручную (MailHog/`MAIL_ENABLED=false`), что не сделано.
