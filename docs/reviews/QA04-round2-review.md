# QA04 раунд 2 — ревью (PR #44, 076df0e)

Ветка `pr/44` = один коммит `076df0e` «fix(security): re-audit M01 and M02 and prevent imported profile claims» от базы **63263d1** (до слияния M09 #25 и M02 #29). Диф к базе: 9 файлов, +617/−16; production-код — только `CreateUserUseCase.java` (+9/−5). Ревьюер: Claude, 13.10.2026. Docker и Maven Central в среде ревью недоступны — прогон тестов не воспроизводился, проверка статическая (диф, `git show`, `git merge-tree`, сверка с кодом main после M02/M09).

**Вердикт: сливать после rebase на актуальный `main` и трёх правок текста (≤15 минут работы Codex).** Единственный production-фикс (QA04-10) корректен, минимален (14 строк), демо-сценарий не ломает, контракты §5 не меняет (только статус 201→409 в заранее дефектном случае). Тесты M02 написаны против реального API M02 и совпадают с тем, что уже в main. Блокирует только техническое: GitHub помечает PR `mergeable=false / dirty` (конфликт в `docs/qa/QA04-security-rbac.md` с правкой M02), поэтому CI на PR **не запускался вообще** — «823 зелёных кейса» пока подтверждены только локальным прогоном Codex.

## Проверено

| Что | Результат | Где |
|---|---|---|
| Фикс QA04-10 (signup присваивает импортированный профиль) | ✅ корректен. Проверка `profileRepository.findByExternalKey(key)` перенесена **до** `userRepository.save` и `encode(password)`; при `imported.userId == null` → `BusinessException("identity.profile_already_attached")` → 409 через существующий `GlobalExceptionHandler:60`. Аккаунт не создаётся, профиль/заявка не меняются. Покрывает оба маршрута (`POST /users`, `POST /auth/register` — один use case). | `identity/application/CreateUserUseCase.java:60-68` |
| Не ломает демо | ✅. Seed (`scripts/demo/seed.ts:28-44`) создаёт `org@/sec@/judge@/parent@` **до** импорта с ФИО «Демо Организатор/…/Родитель 1990-01-01» — таких строк в `set-M.xlsx` нет. Родитель в сценарии (demo-script §3) заявляет «Тимофея Демова» через `/e/{slug}/register` → `ProfileLookupService.upsertForUser`, этот путь PR не трогает (там guard QA03). | `scripts/demo/bodies.ts:6-16`, `docs/demo-script.md:40-46` |
| Побочный эффект фикса | ⚠️ admin через `POST /users` (панель M01) тоже получает 409 для импортированного спортсмена без аккаунта. В демо не нужно; для MVP — часть задачи «подтверждение владения» (admin/организатор как proof of ownership). В отчёте об этом сказано («административный механизм не переписывается»). | — |
| Мёртвый код после фикса | ℹ️ `imported != null && imported.getUserId()==null ? imported : new ProfileEntity()` теперь всегда `new`. Не ошибка; можно убрать 2 строки при rebase (не обязательно). | `CreateUserUseCase.java:84-86` |
| Фронт на новый 409 | ⚠️ `RegisterPage.getRegisterErrorMessage` любой 409 показывает как «Пользователь с таким email уже зарегистрирован» — для импортированного спортсмена текст вводит в заблуждение. 3 строки, см. «Текст правок» п. 3. | `frontend/src/features/auth/RegisterPage.tsx:16` |
| Тесты M02 включены, а не в known-defect | ✅. `Qa04Round2M02IntegrationTest` — обычный `*IntegrationTest` (Surefire), `@EnabledIf(Qa04M02Conditions#available)` — рефлексия по `@PostMapping("/refresh")` в `AuthController`; на текущем main (`9be55d9`, M02 слит) условие истинно → матрица 6×7 + 10 сценариев войдут в `mvn test`/CI сразу после rebase. В known-defect только 3 новых дефекта M02 (QA04-11/12/13). | `security/Qa04M02Conditions.java`, `Qa04Round2M02IntegrationTest.java` |
| Сверка M02-тестов с кодом main | ✅ ожидания совпадают с `LoginAttemptLimiter` (нормализация email `trim().toLowerCase`, ключи `email:`/`ip:`), `AuthSessionService` (reuse → `invalidateAll`, blocked → 403 `identity.user_blocked`), `PasswordService` (202 всегда, `reset_token_invalid` 400, reset инвалидирует refresh). `app.auth.login.max-failures=5` и `server.forward-headers-strategy=none` заданы `@TestPropertySource` в `Qa04M02Fixture` — отдельный контекст, кэш Caffeine не пересекается с `AuthHardeningIntegrationTest`. Уникальные IP `198.18.x.y` на тест, уникальные email на фикстуру — утечек состояния между тестами нет. | `Qa04M02Fixture.java` |
| Password-reset enumeration/timing | ✅ проверено: 202 для существующего/отсутствующего/blocked/другой регистр; timing только измеряется (медианы 3200/1396 мкс, без flaky-порога) — честно, см. «Дефекты для стенда» п. 4. | отчёт §«Что проверено» |
| Refresh reuse | ✅ `refreshRotatesAndReuseRevokesTheReplacement`: старый → 401, замена → 401, в БД 2 записи. Совпадает с `AuthSessionService.refresh:40-50`. | — |
| Rate-limit обходы | ✅ регистр email (5 попыток чередуя регистр → 429), поддельный `X-Forwarded-For` при `forward-headers-strategy=none` не влияет, новый email с того же IP → 429. IPv6-формы — QA04-11 (minor, согласен: Tomcat/Caddy отдают адрес из сокета в канонической форме, по сети не воспроизводится). На стенде `prod` → `native` + `RemoteIpValve`, Caddy ≥2.5 без `trusted_proxies` отбрасывает клиентский XFF — обхода нет (подтверждено M02-review-1 B3). | `LoginAttemptLimiter.java:37-44`, `infra/Caddyfile` |
| IDOR/эскалация M01 | ✅ 5 ролей × (свой, чужой) × (PATCH status+roles, PATCH password) → 403, hash/status в БД не изменены; admin не блокирует/не удаляет себя, не снимает `platform_admin` (403 `identity.cannot_modify_self`), `roles` в PATCH игнорируется; owner организации меняет memberRole только у себя, в чужой — 403; GET чужого пользователя — 403. | `Qa04Round2AdminIntegrationTest.java` |
| Старые тесты не ослаблены | ✅. Единственная правка существующего теста — `Qa04KnownDefectsIT.qa04_10`: ожидание **201 → 409** плюс ассерты «профиль без владельца, аккаунта нет, withdraw чужой заявки 403» — это усиление. Других удалений/изменений тестов нет. | `Qa04KnownDefectsIT.java` |
| TSV | ✅ не менялся — новых маршрутов нет. На main TSV уже содержит 6 строк M02 (110 маршрутов). | — |
| Миграции | ✅ нет. В отчёте «следующий свободный V028+» устарело: main — V030, pr/38 — V031, pr/41 — V032 → bootstrap-миграция **V033+** (безопаснее — V035+, как в треде). | `docs/qa/QA04-round2.md:26`, PR body |
| Known-defect IT не ломают сборку после M05 | ✅. `Qa04Round2M02KnownDefectsIT` ссылается на `LoggingMailSender`/`MailSender` только строками (`LoggerFactory.getLogger("…common.LoggingMailSender")`, `Class.forName`), поэтому перенос класса в `notifications` в pr/41 компиляцию не ломает. После M05 тест QA04-12 пройдёт «вакуумно» (логгер другой), QA04-13 — через outbox; отчёт надо будет обновить (задача M05, не этот PR). | `Qa04Round2M02KnownDefectsIT.java:36,53` |
| CORS/заголовки после M09 | ✅ статическая часть верна: Caddy HSTS/nosniff/SAMEORIGIN/Referrer-Policy/Permissions-Policy/-Server, порты только 80/443, `APP_CORS_ALLOWED_ORIGINS` точные origins, `allowCredentials=false`. CSP нет (ожидаемо). Внешняя проверка стенда — отдельная приёмка M09. | `infra/Caddyfile:12-20`, `application-prod.yml:38` |
| Bootstrap admin (QA04-06) | ⚠️ PR **не реализует** bootstrap, оформлена задача M09 с приёмкой. Для варианта «≤40 строк» постановка в отчёте корректна по безопасности: создавать только при отсутствии admin, **не перезаписывать** существующий hash, не печатать пароль, проверять политику/72 байта, транзакция/блокировка от гонки. Решение «задача, а не частичный bootstrap» принимаю — см. «Дефекты для стенда» п. 1: для стенда 19.10 достаточно ротации пароля руками, а bootstrap нужен для MVP. Если всё же делать здесь — текст в «Текст правок» п. 5 (опционально). | `docs/qa/QA04-round2.md:21-30` |
| «Imported profile protection» в названии | = фикс QA04-10 (запрет signup-захвата). Процедура подтверждения владения **не** реализована — в описании PR сказано явно. | — |
| Конфликты (`git merge-tree`) | `origin/main` (6e68daa): **CONFLICT** `docs/qa/QA04-security-rbac.md` (M02 правил строку QA04-08 и раздел «Закрытие M02»; PR 44 — строки QA04-10/итога/«Правки по ревью 1» + новый раздел «Раунд 2»); `Qa04KnownDefectsIT.java` автослился (M02 удалил `qa04_08`, PR 44 правил `qa04_10` — разные hunks). `pr/37`, `pr/38`, `pr/41`, `pr/42` — **чисто**. | — |
| CI | На `076df0e` check-runs **нет** (статус `pending`): из-за `dirty` GitHub не строит merge-ref и workflow `pull_request` не стартует. После rebase CI обязателен — это первый реальный прогон M02-матрицы в CI. | `gh api …/check-runs` |

## Блокирующие (до слияния)

1. **Rebase на `origin/main` (6e68daa) и разрешить конфликт `docs/qa/QA04-security-rbac.md`.** Правило: в строке QA04-08 и разделе «Закрытие M02» оставить версию main (M02 слит, дефект закрыт), в строке QA04-10, итоге («8 исправлены … 2 оставлены» → **«9 исправлены (QA04-07 M01, QA04-08 M02, QA04-10 раунд 2), 1 оставлен (QA04-06)»**) и новом разделе «QA04 раунд 2» — версию PR. Фразы «QA04-08 на текущем main ещё красный», «после слияния M02 дефект 08 закрывается» заменить на «закрыт M02 (#29)». В `Qa04KnownDefectsIT` после автослияния остаются `qa04_06` (красный) и `qa04_10` (зелёный) — ок.
2. **Актуализировать `docs/qa/QA04-round2.md` и описание PR под слитые M02/M09**: «M02 ещё не слит / кандидат 8965239 / временное объединение» → «M02 слит в main (#29, 9be55d9), M09 слит (#25, 5e5259f); условные тесты активны в `mvn test`»; «следующий свободный V028+» → «V033+ (V031 — pr/38, V032 — pr/41), в треде зарезервировано V035+»; в таблице QA04-11/12/13 статус «M02 кандидат» → «main». Пути/строки M02 перепроверить по main (они совпадают — класс/методы те же).
3. **Дождаться зелёного CI после rebase** — обязательный шаг, так как ни один прогон этого PR в GitHub Actions ещё не выполнялся, а `Qa04Round2M02IntegrationTest` впервые пойдёт в CI вместе с `AuthHardeningIntegrationTest` (оба используют Caffeine-лимитер; контексты разные, но это надо увидеть зелёным, а не предполагать).

## Желательные (не блокируют)

1. **`RegisterPage.tsx`** — различать 409 по коду (3 строки): `identity.profile_already_attached` → показывать `error.message` сервера («Импортированный профиль требует подтверждения владения» — лучше дополнить «…обратитесь к организатору»), иначе прежний текст про email. Иначе родитель импортированного спортсмена получит «email уже зарегистрирован» и будет пробовать другие email.
2. Текст сообщения в `CreateUserUseCase`: «Спортсмен с такими ФИО, датой рождения и полом уже есть в списке заявок организатора. Для привязки аккаунта обратитесь к организатору» — понятнее пользователю и согласовано с текстом QA03-guard в `ProfileLookupService:122`. Код `identity.profile_already_attached` оставить (уже в `GlobalExceptionHandler` и TSV-ожиданиях QA03).
3. Убрать мёртвую ветку `imported != null && … ? imported : new ProfileEntity()` (−2 строки) — после фикса `imported` в этом месте либо `null`, либо уже чужой.
4. `qa04_10` в `Qa04KnownDefectsIT` теперь зелёный в классе с `@Tag("known-defect")` — либо удалить (сценарий есть в `Qa04Round2AdminIntegrationTest`), либо оставить с комментарием «regression guard». Сейчас комментарий есть — приемлемо.
5. Отчёт: в таблице матрицы `GET /organizations/{id}` чужой organizer — 404 вместо 403 (существующее поведение, не PR): раскрывает/скрывает существование организации непоследовательно с `members` (403). Записать как minor-наблюдение для M01-followups, не исправлять здесь.
6. В отчёте нет оценки **lockout-DoS по email** (5 неверных паролей от любого → 15 минут 401/429 для известного аккаунта) — для публичного репозитория с публичными демо-email это риск демо-дня. Добавить абзац в «Дефекты для стенда» (см. ниже п. 2) с мерой «вход заранее + refresh» и ручкой `APP_AUTH_LOGIN_WINDOW`.

## Текст правок для Codex

```
1. git fetch origin && git rebase origin/main
   Конфликт docs/qa/QA04-security-rbac.md: строка QA04-08 и раздел «Закрытие M02» — взять main;
   итог (строка 5), строка QA04-10, «Правки по ревью 1» и раздел «QA04 раунд 2» — взять ветку.
   Итог: «9 исправлены (QA04-07 M01, QA04-08 M02, QA04-10 раунд 2), 1 оставлен (QA04-06)».
   Убрать фразы «QA04-08 на текущем main красный» / «после слияния M02».

2. docs/qa/QA04-round2.md + описание PR:
   - «M02 кандидат 8965239 / не слит / временное объединение» → «M02 слит (#29, 9be55d9), M09 слит (#25); условные тесты активны в mvn test».
   - «следующий свободный V028+» → «V033+ (V031 pr/38, V032 pr/41); по треду — V035+».
   - QA04-11/12/13: статус «M02 кандидат» → «main; QA04-12/13 закрываются M05 (pr/41, OutboxMailSender)».
   - Добавить в «Дефекты» строку: «QA04-14 minor (major для демо-дня): lockout по email — 5 неверных попыток от любого адреса блокируют login известного аккаунта на 15 мин; мера — вход демо-аккаунтов заранее, refresh-сессии 30 дней, на день демо APP_AUTH_LOGIN_WINDOW=PT2M; задача MVP — капча/лимит по IP+email или прогрессивная задержка вместо блокировки email».

3. sport-platform/frontend/src/features/auth/RegisterPage.tsx, getRegisterErrorMessage:
     if (error.status === 409) {
       return error.code === 'identity.profile_already_attached'
         ? 'Спортсмен с такими ФИО, датой рождения и полом уже есть в списке заявок организатора. Для привязки аккаунта обратитесь к организатору.'
         : 'Пользователь с таким email уже зарегистрирован.'
     }
   (поле code в ApiError есть — используется в LoginPage/organizer; если имя другое — взять его.)
   + 1 Vitest-кейс в RegisterPage: 409 с code=identity.profile_already_attached показывает текст про организатора.

4. CreateUserUseCase.java: сообщение → «Спортсмен с такими ФИО, датой рождения и полом уже есть в списке заявок. Для привязки аккаунта обратитесь к организатору»; убрать мёртвую ветку
     ProfileEntity profile = new ProfileEntity();
     if (imported == null) profile.setExternalKey(externalKey);
   (поведение при imported с чужим userId не меняется: новый профиль без ключа, как и раньше).

5. (ОПЦИОНАЛЬНО, если решено делать bootstrap здесь, ≤40 строк, без миграции)
   identity/infrastructure/AdminBootstrap.java — @Component implements ApplicationRunner, @Transactional run():
     email = env APP_ADMIN_EMAIL, password = env APP_ADMIN_PASSWORD (оба @Value, default "").
     if (email.isBlank() || password.isBlank()) return;
     if (userRepository.existsByEmail(email)) { log.info("bootstrap admin exists, password untouched email={}", email); return; }  // НИКОГДА не перезаписывать hash
     PasswordPolicy.validate(password); создать UserEntity(active, encode(password)) + UserRoleAssignment(platform_admin); log.info без пароля.
   Отдельно НЕ блокировать admin@flounder.demo автоматически (на стенде DEMO_SEED=true намеренно) — ротация руками, см. ниже.
   Тест: AdminBootstrapIntegrationTest — (а) создаёт при отсутствии, (б) повторный run с другим паролем не меняет hash, (в) пустые env — ничего. compose.prod.yaml: прокинуть APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD. TSV не меняется.
   Если не делать — оставить задачей M09-followup с текстом из отчёта (п. 1–4 «Постановка M09»), номер миграции V035+.
```

## Порядок слияния

1. `pr/37` (M01 followups, 11 файлов, независим) — первым, чисто.
2. **`pr/44`** после rebase (п. 1–3 «Блокирующие») и зелёного CI. С pr/37/38/41/42 конфликтов нет (`merge-tree` чист), поэтому место в очереди не критично; ставлю вторым, потому что это единственный PR, который включает M02-матрицу в CI — лучше увидеть её зелёной до M04/M05.
3. `pr/38` (M04 documents, V031, TSV) → `pr/41` (M05 notifications, V032, TSV; после слияния обновить `QA04-round2.md`: QA04-12/13 закрыты) → `pr/42` (QA07 docs). Между 38 и 41 — rebase второго (append-конфликты TSV/compose, как в M02-review-2).
4. После всего: явный прогон `mvn -q test -Dtest=Qa04KnownDefectsIT,Qa04Round2M02KnownDefectsIT` на main — ожидаемо красные только `qa04_06` и `equivalentIpv6…`; это и есть актуальный список открытых дефектов.

## Дефекты для стенда (sportgate.pro, до 19.10) — severity и действия Сергея

| # | Дефект | Severity для стенда в интернете | Действие Сергея | Срок |
|---|---|---|---|---|
| 1 | **QA04-06**: `admin@flounder.demo` / `<пароль — в менеджере паролей владельца, в репозитории не хранится>` зашит в V020, пароль опубликован в `docs/02_ТЗ_демо.md:18` **публичного** репозитория; сервер не читает `DEMO_SEED`, bootstrap из env нет. | **Blocker** для любого выхода в интернет (полный platform_admin любому прочитавшему репозиторий). | Сразу после первого деплоя/миграции: войти `admin@flounder.demo`, сменить пароль через `/password` (M02 `change-password`, отзывает refresh) на уникальный; записать в `infra/.env.prod` `ADMIN_PASSWORD=<новый>` (seed.ts логинится им). Для `org@/sec@/judge@/parent@` задать уникальный `DEMO_PASSWORD` **до** первого `seed.ts` (seed создаёт их с этим паролем). Проверка: `curl -X POST https://sportgate.pro/api/v1/auth/login -d '{"email":"admin@flounder.demo","password":"<пароль — в менеджере паролей владельца, в репозитории не хранится>"}'` → 401. Повторять после каждого `reset.sql` (reset не трогает admin — проверить один раз). Bootstrap из env — задача M09-followup для MVP. | До открытия стенда наружу; повторно после каждого reset |
| 2 | **Lockout по email** (`LoginAttemptLimiter`): любой с интернета 5 раз вводит неверный пароль для `org@flounder.demo` → 15 мин login невозможен и с правильным паролем (429). Email демо-аккаунтов публичны. | **Major для демо-дня** (отказ входа ведущего на сцене), minor для безопасности. | За 30–60 мин до показа войти всеми аккаунтами на всех устройствах, не выходить (JWT 2 ч + refresh 30 дн, фронт сам обновляет). На день демо в `.env.prod`: `APP_AUTH_LOGIN_WINDOW=PT2M` (лимит остаётся, блокировка 2 мин вместо 15). Задача MVP: лимит IP+email/капча вместо чистого email-lockout. | Перед репетицией 16–18.10 |
| 3 | **QA04-12**: ссылка восстановления пароля с токеном пишется в `docker logs api` (`LoggingMailSender`, INFO), писем нет — «Забыли пароль?» на стенде не работает, токен виден всем с доступом к логам. | Minor для демо (логи только у Сергея; функция не в сценарии), major для MVP. | Не показывать восстановление пароля на демо; слить `pr/41` (M05 outbox + SMTP) до 19.10 **или** принять, что функции нет. Если M05 слит — настроить `MAIL_*` в `.env.prod`. | До 16.10 (M05) или решение «не показывать» |
| 4 | **QA04-13 + timing**: при отказе почты 500 для известного email vs 202 для неизвестного; локально медианы 3,2 мс / 1,4 мс. | Minor (на стенде почта не падает — LoggingMailSender; timing по сети не доказан). | Ничего до MVP; закрывается M05 (очередь) + задача «одинаковый путь lookup». | MVP |
| 5 | **QA04-11**: IPv6-формы адреса не делят квоту (только MockMvc). | Minor/информационно (сокет/Caddy дают каноническую форму). | Ничего; задача M02-followup «канонизация адреса». | MVP |
| 6 | **Новый 409 на регистрации** для ФИО импортированного спортсмена — фронт показывает «email уже зарегистрирован». | Minor (в сценарии демо не встречается; Тимофея Демова в Excel нет). | Принять п. 3 «Текст правок» в этом PR. Ведущему: если кто-то из зала попробует зарегистрироваться «как спортсмен из списка» — ожидаемый отказ, объяснить «привязка через организатора». | С этим PR |
| 7 | Заголовки/CORS/TLS стенда проверены только статически по конфигам. | Info. | После деплоя: `curl -sI https://sportgate.pro/` — HSTS/nosniff/X-Frame-Options; `curl -sI -H 'Origin: https://evil.example' https://sportgate.pro/api/v1/public/events` — без `Access-Control-Allow-Origin`; `docker compose ps` — наружу только 80/443. | Приёмка M09, до 16.10 |
