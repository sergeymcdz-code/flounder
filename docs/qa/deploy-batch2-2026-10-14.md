# Деплой партии 2 (14.10): весь этап 1 — M03, M04, M05, M06, M07, M08, D01, D02, QA04 r2, QA07

Слиты в `main` в ночь на 14.10 (01:00–04:05 МСК): #37, #30 D01, #44 QA04 r2, #42 QA07, #33 M03, #38 M04, #49 D02, #41 M05, #36 M08, #46 M06, #40 M07. CI `main` зелёный. Ревью — `docs/reviews/*-review-2.md`, `*-review-3.md`, `D02-review-1.md`.

Миграции (только create/alter add): V031 `participant_documents` (M04), V032 `notification_outbox` (M05), V033 `event_status_lifecycle` (M06), V034 `unit_actual_times` (M07). Flyway применит по порядку; `outOfOrder` не включать (ADR `2026-10-13-flyway-order`).

## Что меняется для стенда
- **Обязательно:** `APP_FILES_ENCRYPTION_KEY` в `infra/.env.prod` — без него `compose` откажется стартовать `api` (`:?` в `compose.prod.yaml`). Ключ = base64 от 32 случайных байт. **Потеря ключа = потеря всех загруженных документов** — сохранить копию в менеджере паролей отдельно от сервера.
- Новый docker-том `flounder_files` (документы участников); `infra/backup.sh` по умолчанию берёт `FILES_VOLUME=flounder_files` — ничего задавать не нужно.
- Письма (M05): без `MAIL_*` приложение стартует, письма складываются в outbox и помечаются отправленными через лог (`MAIL_ENABLED=false`). Для реальных писем нужны `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_ENABLED=true` — решение и данные владельца.
  - **Сделано 14.10 13:35 МСК:** ящик `noreply@sportgate.pro` (Timeweb, `smtp.timeweb.ru:587` STARTTLS; MX/SPF домена уже на Timeweb), `MAIL_ENABLED=true`; письмо «сброс пароля» на внешний адрес владельца дошло. Поддержка порта 465/SSL — задача M05-3 (Codex, в работе). Пароль ящика — только в `.env.prod` и менеджере паролей.
- Письма: `MAIL_SECURITY=auto` выбирает SSL для 465 и обязательный STARTTLS для остальных портов; для Timeweb `smtp.timeweb.ru` используйте 465/ssl или 587/starttls, логин — полный адрес ящика.
- `APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD` (QA04 r2, bootstrap второго platform_admin) — опционально; пароль `admin@flounder.demo` этим не меняется, ротация руками через UI.
- Планировщики: `EventLifecycleScheduler` (раз в минуту, авто-закрытие регистрации по `registrationCloseAt`), outbox-воркер M05. Демо-событие с датой 24.10 закроется 24.10 09:30 Самары — не раньше.

## Шаг 0 — бэкап и ключ (под `deploy`, в `~/flounder`)
```bash
ssh root@82.97.248.229
su - deploy
cd ~/flounder
./infra/backup.sh                                   # свежий дамп до миграций
KEY="$(openssl rand -base64 32)"; echo "$KEY"       # показать ключ — скопировать в менеджер паролей
printf 'APP_FILES_ENCRYPTION_KEY=%s\n' "$KEY" >> infra/.env.prod
tail -2 infra/.env.prod                             # убедиться, что строка добавилась (одна строка, без пробелов)
```

## Шаг 1 — деплой (~4–5 минут: сборка образов + 4 миграции)
```bash
./infra/deploy.sh
curl -s https://sportgate.pro/actuator/health       # {"status":"UP",...}
docker logs flounder-api 2>&1 | grep -i 'flyway\|Successfully applied\|Migrating' | tail -6   # ожидается V031..V034
```

## Шаг 2 — проверки на стенде (10 минут, в браузере)
1. Публичная страница события → «Скачать PDF» стартового протокола (M03): файл открывается, кириллица на месте.
2. Табло `/e/<slug>/live`: переключатель «Светлая/Тёмная» (D02), выбор сохраняется после перезагрузки.
3. Кабинет `org@flounder.demo`: Событие → вкладка «Аналитика» (M08) открывается, экспорт XLSX скачивается.
4. Событие → обзор: кнопки «Закрыть регистрацию»/«Продлить» (M06); бейдж «Лимит» у вида после задания лимита.
5. Заплывы: блок «Идём по расписанию»/«Отставание» (M07), «Сдвинуть расписание…» с предпросмотром.
6. Кабинет организатора → «Уведомления» события (M05): очередь писем; без SMTP статусы SENT с пометкой лога.
7. Кабинет `parent@flounder.demo` → заявка → «Документы» (M04): загрузить PDF/JPG ≤10 МБ, скачать обратно, удалить.
8. Повторный сид не нужен; если запускали `reset.ts` — см. `docs/qa/rehearsal-2026-10-17.md`.

## Откат
`./infra/restore-production.sh <дамп из шага 0>` — разрушительный, только если стенд неработоспособен; перед ним `git checkout <sha до партии 2> && ./infra/deploy.sh --no-pull`. Миграции V031–V034 только добавляют таблицы/колонки, поэтому откат кода без отката БД тоже безопасен.

## Follow-up (не для демо)
- M07-2: `ShiftScheduleUseCase` — `assertCanModify` + `lockWritableForUpdate` (запрет сдвига для ARCHIVED), кнопка disabled.
- M08-2: задержка публикации по `actual_finished_at` (M07 в main).
- M05-2: подписчик `ScheduleChangedEvent` (письмо об изменении расписания).
- M04-followup: не писать `DENIED` для анонимных запросов; `files.corrupt` вместо 500 при повреждённом файле.
- QA04-14: лимит входа по email — капча/IP+email после демо; вернуть `APP_AUTH_LOGIN_MAX_FAILURES=5`.
