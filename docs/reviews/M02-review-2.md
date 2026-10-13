# M02 — ревью 2 (PR #29, 8965239)

Ветка `pr/29`: `57c5e54` (feat, = `b1aa758` после rebase на `fd834ec`) + `8965239` «fix(identity): address M02 authentication review». Диф правок: 23 файла, +346/−33. CI на 8965239 зелёный. Ревьюер: Claude, 13.10.2026.

**Вердикт: сливать.** Все 8 пунктов правок из M02-review-1 закрыты, регрессий нет, старые тесты не ослаблены, V029/V030 не тронуты, новых миграций и эндпоинтов нет. Блокирующих замечаний нет. По ADR `2026-10-13-demo-19-oktyabrya` M02 сливается сейчас (цель — весь этап 1 на стенде к 16.10), а не «после демо» — формулировки «после демо 15.10 (с 16.10)» в PR/README/QA04 устарели, но на код не влияют.

## Проверено по 8 пунктам

| # | Пункт ревью 1 | Статус | Где |
| --- | --- | --- | --- |
| 1 | `APP_PUBLIC_URL` в compose/.env.example | ✅ | `infra/compose.prod.yaml:51` — одна строка `APP_PUBLIC_URL: ${APP_PUBLIC_URL:-https://${DOMAIN}}`; `infra/.env.prod.example` — `APP_PUBLIC_URL=https://sportgate.pro` с комментарием. Остальной compose не тронут (pr/25 сливается чисто). |
| 2 | `LoginAttemptLimiter`: свойства + `succeeded(email)` + тесты | ✅ | `app.auth.login.max-failures` (5) / `app.auth.login.window` (PT15M) ← `APP_AUTH_LOGIN_MAX_FAILURES` / `APP_AUTH_LOGIN_WINDOW` в `application.yml:93-96`, `.env.example`, README. Конструктор отвергает ≤0. `succeeded()` инвалидирует только `email:*`, вызывается в `LoginUseCase:44` сразу после `authenticate`. Тесты: `rateLimitByNormalizedEmail…` расширен «4 → успех → 4 → 401, 5-я → 401, 6-я → 429» (без сброса вторая серия дала бы 429 на 2-й попытке — тест действительно различает); новый `LoginLimitConfigurationIntegrationTest` (`max-failures=2`, `window=PT2M`, `Retry-After: 120`, разблокировка через 120 с); `successfulLoginDoesNotClearIpFailures`; `LoginUseCaseTest` — `inOrder(authenticate → succeeded → issue)`, `never()` на 401/403/500. |
| 3 | Текст про `X-Forwarded-For` | ✅ | README `:144-148`, QA04 `:78,:90`, описание PR («Что не сделано», п. 3): prod-профиль `forward-headers-strategy: native` → `RemoteIpValve` берёт IP из заголовка Caddy; локально/в тестах — прямой `remoteAddr`, клиентский заголовок не доверяется. Код не менялся. |
| 4 | §5.9 ТЗ | ✅ | `docs/02_ТЗ_демо.md:265-320`: таблица 6 эндпоинтов, расширение `login`, таблица кодов с HTTP-статусами (включая `user_blocked` 403 на refresh), политика пароля, сроки 1 ч / 30 дн, ротация и reuse-detection, access JWT до `exp`, `PATCH /users/{id}/password` отзывает refresh/reset, настройки лимита. |
| 5 | Reuse-detection refresh | ✅ | `AuthSessionService.refresh:47-50`: при `revokedAt != null` → `tokens.invalidateAll(userId)` → 401. Корректно решена проблема отката: новый `InvalidRefreshTokenException extends BusinessException` + `@Transactional(noRollbackFor = …)` только на `refresh` — отзыв коммитится, `GlobalExceptionHandler` по коду `identity.refresh_token_invalid` по-прежнему даёт 401. Тест `reusedRefreshRevokesTheRotatedTokenAndOtherDevicesDespiteReturning401` проверяет 0 живых токенов в БД и 401 для новой пары и второго устройства. Порядок блокировок user → token сохранён. |
| 6 | Убрать `@Primary` | ✅ | `IdentityClockConfig`: `@Bean(name = "identityClock", defaultCandidate = false)` (Spring 6.2 / Boot 3.5.6). Все потребители с `@Qualifier`; `publicationClock` аналогично изолирован. `@MockitoBean(name = "identityClock")` в тестах работает по имени. |
| 7 | `@NotBlank` на `newPassword` | ✅ | Оба DTO: `@NotBlank(message = "Укажите новый пароль")`. Параметризованный тест (null/""/" ") проверяет `validation_error` и что reset-токен **не** расходуется при 400. |
| 8 | Фронт: 429 + `Retry-After`, logout после смены пароля | ✅ | `ApiError.retryAfter` (только `^\d+$`, иначе `undefined`), прокинут и в `apiDownload`; `LoginPage` — «Попробуйте через N мин» (`ceil`, минимум 1); `PasswordPage` (`change`) — после 204 `logout()` (ошибка проглатывается, refresh уже отозван бэкендом), `Navigate` на `/login` подавлен при `success`, текст «Пароль изменён. Войдите заново с новым паролем». 4 новых Vitest-теста. |

Дополнительно: `git diff origin/main pr/29` по тестам — удалён только `Qa04KnownDefectsIT.qa04_08…` (перенос без ослабления, подтверждено в ревью 1); в `8965239` ни одного удалённого теста. Изменение ассерта в `concurrentRefreshHasOnlyOneWinner` (`isEqualTo(1)` → `isZero()`) — не ослабление, а следствие reuse-detection: проигравший гонку запрос отзывает и пару победителя (см. «Желательные» п. 1). TSV не менялся в правках; 6 строк M02 из первого коммита на месте. Миграции: `git diff 57c5e54..8965239 -- db/migration` пуст; относительно `main` только V029/V030.

## Блокирующие

Нет.

## Желательные (не блокируют слияние)

1. **Reuse-detection без grace-окна** (`AuthSessionService.refresh:47`). Два легитимных параллельных refresh с одним токеном (две вкладки с общим refresh, повторная отправка при обрыве сети) → проигравший получает 401 **и** отзывает только что выданную пару победителя → пользователь выходит на всех устройствах. Фронт M02 защищён (`refreshInFlight`, sessionStorage per-tab), для демо риск минимален. Для MVP: считать повторное предъявление в пределах ~10–30 с после `revokedAt` идемпотентным (вернуть 401 без `invalidateAll`) или хранить `replacedBy` и отдавать ту же пару. Зафиксировать как TODO/задачу.
2. Устаревшие даты: README `:152`, QA04 `:103`, ТЗ/PR «слияние и деплой после демо 15.10 (с 16.10)» — по ADR от 13.10 демо 19.10, M02 сливается сейчас. Поправить одной строкой при слиянии или в следующем docs-PR; на код не влияет.
3. Остаются открытыми желательные из ревью 1: raw reset-токен в INFO-логе (`common/LoggingMailSender`) — закрывается M05 удалением файла; ссылка «Забыли пароль?» без почты — закрывается M05; частотный лимит на `password-reset/request` и очистка `refresh_tokens` — отдельная задача MVP; `npm test` в CI — QA00/M09.
4. `LoginAttemptLimiter.succeeded` — `synchronized`, как и `assertAllowed`; TODO про Redis уже есть в javadoc класса. Ок.

## Порядок слияния, конфликты, env для стенда

**`git merge-tree` pr/29 против:**

- `origin/main` (63263d1) — чисто.
- `pr/25` (M09, 9288f2a) — чисто (compose/.env.prod.example/package.json автослияние, пересечений по строкам нет).
- `pr/38` (M06 documents, 66bb8b5) — 2 тривиальных append-конфликта: `application.yml` (блок `app.auth` M02 vs блок `app.files` M06 — оставить оба), `qa04-endpoints.tsv` (6 строк M02 + строки M06 — оставить все).
- `pr/41` (M05 notifications, 3cdb48a) — 4 тривиальных append-конфликта: `infra/compose.prod.yaml` (`APP_PUBLIC_URL` M02 + `MAIL_*` M05 — оставить оба), `application.yml` (`app.auth` M02 + `app.mail` M05, строка `public-url` общая — оставить один раз), `README.md` (два абзаца подряд), `qa04-endpoints.tsv` (6 строк M02 + 3 строки M05).

**MailSender и M05 (pr/41).** M05 намеренно совместим с M02: `common/MailSender.java` в обеих ветках **побайтово одинаков** (add/add без конфликта), `notifications.application.OutboxMailSender implements MailSender` с `@Primary` кладёт письмо в outbox; его `LoggingMailSender` живёт в `notifications.infrastructure` под bean-именем `notificationLoggingMailSender` и **не** реализует интерфейс. `docs/M05-notifications.md` §«Совместимость с M02» прямо описывает порядок. При слиянии второго из PR #29/#41:

1. **Удалить `sport-platform/src/main/java/com/acme/sportplatform/common/LoggingMailSender.java`** (временная реализация M02). Иначе в контексте два бина `MailSender`; `@Primary` на `OutboxMailSender` разрешает инъекцию, но `@MockitoBean MailSender mail` в `AuthHardeningIntegrationTest` и `@Autowired MailSender` в `NotificationIntegrationTest` надёжнее с единственным бином, а reset-ссылка перестанет попадать в INFO-лог (желательное п. 1 ревью 1).
2. `common/MailSender.java` оставить единственным интерфейсом; `identity/package-info.java` (`allowedDependencies = "common"`) не менять — identity зависит только от интерфейса в `common`, реализация из `notifications` подставляется контейнером, `ModulithArchitectureTest` это не нарушает.
3. `infra/.env.prod.example`: M05 просит оставить `APP_PUBLIC_URL` пустым — допустимо: compose-fallback `${APP_PUBLIC_URL:-https://${DOMAIN}}` срабатывает и на пустое значение. Любой из вариантов рабочий; главное — переменная присутствует в compose (M02).
4. После разрешения прогнать `mvn -q test` (ожидать зелёными `AuthHardeningIntegrationTest`, `NotificationIntegrationTest`, `Qa04RoleMatrixIntegrationTest.fixtureTableCoversEveryApiV1ControllerMapping`). Flyway: M05 добавляет V032; если стенд получит M05 **раньше** M02, то V029/V030 окажутся ниже уже применённой V032 и Flyway откажется их применять без `spring.flyway.out-of-order=true` на один деплой. Проще деплоить M02 раньше M05 (или одной партией).

**Рекомендуемый порядок:** M09 (pr/25, staging) → **M02 (pr/29)** → M06 (pr/38) → M05 (pr/41, с удалением `common/LoggingMailSender`). M02 раньше M05 снимает вопрос out-of-order Flyway и даёт M05 ребейз по его же инструкции.

**Env для стенда (infra/.env.prod):**

```
APP_PUBLIC_URL=https://sportgate.pro      # ссылки восстановления пароля (иначе fallback https://${DOMAIN})
APP_AUTH_LOGIN_MAX_FAILURES=20            # на время демо/репетиций 17–19.10 (5 демо-аккаунтов с одного IP); после демо вернуть 5
# APP_AUTH_LOGIN_WINDOW — не задавать (PT15M)
# APP_JWT_ACCESS_TTL — не задавать (PT2H); PT1H включать отдельным шагом после проверки refresh на стенде
```

**Проверить после деплоя (5 шагов):**

1. Логин каждым из 5 демо-аккаунтов с одного ноутбука; ответ `/auth/login` содержит `refreshToken`, `expiresIn=7200`.
2. Одна намеренная опечатка пароля → «Проверьте email и пароль», затем верный пароль → вход (счётчик email сброшен). `curl` 21 неверный логин одним email → 429 с `Retry-After`, на странице входа «Попробуйте через N мин».
3. «Выйти» → в devtools `POST /auth/logout` 204; повторный `POST /auth/refresh` старым токеном → 401 и после него refresh других вкладок этого пользователя тоже 401 (reuse-detection).
4. «Забыли пароль?» → 202; `docker logs api | grep "DEV mail"` содержит `https://sportgate.pro/reset-password?token=…` (не `localhost`); переход по ссылке и смена пароля → 204, повтор ссылки → 400 `identity.reset_token_invalid`.
5. «Сменить пароль» в кабинете → «Пароль изменён. Войдите заново с новым паролем», сессия завершена, вход новым паролем успешен; публичное live и импорт работают без изменений.

Откат — как в ревью 1: `git revert` merge-коммита; таблицы V029/V030 оставить (`spring.flyway.ignore-migration-patterns=*:missing` на период отката).
