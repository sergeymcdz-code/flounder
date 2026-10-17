# Проверка инфраструктуры SportGate

Проверка 17.10.2026 на локальной одноразовой инфраструктуре, база origin/main `57cea5d`.
Production-сервер не изменялся. Изменения ограничены `infra/`, `docs/`, `.github/`.

## Автоматические проверки

| Проверка | Результат |
|---|---|
| `mvn -q clean test` в sport-platform | PASS: 1644 теста, 0 failures/errors/skipped; в том числе 925 проверок QA04 и покрытие qa04-endpoints.tsv |
| ShellCheck `infra/*.sh infra/tests/*.sh` | PASS |
| `bash infra/tests/staging-test.sh` | PASS, включая harden.sh |
| `bash infra/tests/workflow-test.sh` | PASS: входные параметры, отпечаток, strict SSH |
| `bash infra/tests/compose-config-test.sh` | PASS: лимиты S03, security_opt, версии, env |
| `bash infra/tests/maintenance-test.sh` | PASS: реальный PostgreSQL 16.14, legacy/age, архив файлов, ошибки, ротация, отдельная БД и страховочная копия |
| `bash infra/tests/caddy-test.sh sport-platform/frontend/dist/index.html` | PASS: оба режима CSP, prod/staging, auth, отказ без хеша, точные границы 12 MB/16 KB |
| `python3 infra/tests/csp-test.py sport-platform/frontend/dist/index.html` | PASS: точный SHA-256 исходного и собранного inline-скрипта |
| `npm run build && npm run lint` в frontend | PASS; существующие предупреждения React остаются |

Для infra нужны Bash, Docker Compose, Node, Python 3 + PyYAML, age и shellcheck.
CI устанавливает дополнительные инструменты и запускает infra-проверки; frontend CI
проверяет хеш также после production build. Отдельные host-изменения harden.sh
проверяются только через изолированные подмены sudo/путей, без изменения SSH машины теста.

## Браузер и QA05

Backend — реальный Spring Boot с отдельной локальной PostgreSQL, без MSW.
Frontend — production build/preview, перед ним Caddy 2.10.2 с **enforced** CSP.
Адрес API в сборке/QA05 совпадает с origin Caddy, как в production.
Chromium локальный. Команды из `sport-platform/frontend`:

```bash
QA05_API_URL=http://localhost:5173 PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium npx playwright test --config ../../infra/tests/qa05-csp.config.mts
QA05_API_URL=http://localhost:5173 PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium npx playwright test --config ../../infra/tests/csp-browser.config.mts
```

Перед запуском подготовить зависимости frontend и `scripts/demo` через npm ci,
поднять отдельный backend на 8080 с тестовой БД и валидными тестовыми JWT/files keys.
5173/5174 и локальный Caddy admin port должны быть свободны. Инструмент сида создаёт
только тестовые события в локальной БД. Не перенаправлять эти команды на production.

- Исходный QA05/S02: **S02 PASS**, семь шагов QA05 — **FAIL на шаге 4**.
  Шаги 1–3 прошли, ожидаемые тестом 32 `article` с `table` отсутствуют в текущей
  вёрстке протокола; шаги 5–7 исходного теста пропущены. Сохранён исходный тест без
  ослабления проверок; приложение в этом PR не меняется. Аудит QA05 `audit` пуст.
- Дополнительный CSP-аудит: **PASS**, 20 страниц (публичные обзор/расписание/протокол/live/
  результаты трёх событий, login, обзор/программа/заявки/заплывы организатора).
  Браузерные `securitypolicyviolation` = **0**, CSP-сообщения консоли = **0**.
  Проверяется и наличие enforced заголовка у каждого HTML-ответа.

Это подтверждает совместимость проверенных страниц с CSP; весь QA05 зелёным не объявляется.
HTTPS/ACME, реальные Sentry DSN, SSH/UFW/fail2ban/systemd и учётные записи S3/GitHub
проверяет владелец на своих системах по [инструкции](../infra-hardening.md).
Опция первого выката — Report-Only; enforced включается после проверки владельцем.

Миграций, новых/изменённых контроллеров API нет; `qa04-endpoints.tsv` не меняется.
