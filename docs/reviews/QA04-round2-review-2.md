# QA04-round2 — ревью 2 (PR #44, 114f1a5)

Ветка `pr/44` после ребейза: два коммита поверх актуального `main` (028dc5e) — `60a10cc` (раунд 2 QA04, содержимое ревью 1) и `114f1a5` «fix(security): address QA04 review and add environment admin bootstrap» (правки по ревью 1 + bootstrap admin). Ревью 1: `docs/reviews/QA04-round2-review.md` (вершина 076df0e). Диф правок: 11 файлов, +178/−55; production-код — `CreateUserUseCase.java` (+3/−5), новый `AdminBootstrap.java` (40 строк), `RegisterPage.tsx` (+3/−1), `compose.prod.yaml` (+2), `.env.prod.example` (+4). Полный диф PR к `main`: 15 файлов, +747/−23, миграций нет, TSV не менялся. Ревьюер: Claude, 13.10.2026. Docker/Maven Central/npm registry в среде ревью недоступны — тесты не прогонялись, принимаю по CI PR: **backend / frontend / infra — зелёные на 114f1a5**; GitHub: `mergeable=true, clean`. `git merge-tree --write-tree origin/main pr/44` — конфликтов нет.

**Вердикт: слить.** Все пять пунктов правок выполнены, в том числе опциональный п. 5 (bootstrap admin) — реализован в точности по постановке: при существующем email hash не трогается, пароль и email не логируются, пустые переменные — no-op, старт при V020-сиде не ломается, в тестовом профиле бездействует. Ослаблений тестов нет, чужие файлы не затронуты. Ниже — только желательные замечания (не держат слияние) и памятка по переменным для стенда.

## Проверено

| Пункт правок | Статус | Где / что проверено |
|---|---|---|
| 1. Rebase на main, конфликт `QA04-security-rbac.md`: QA04-08 и «Закрытие M02» из main; итог/QA04-10/«Правки по ревью 1»/«QA04 раунд 2» из ветки; убрать «QA04-08 красный»/«после слияния M02» | ✅ выполнено | База ветки = `028dc5e` (текущий main). `docs/qa/QA04-security-rbac.md:5` — итог «9 исправлены (QA04-07 M01, QA04-08 M02, QA04-10 раунд 2), 1 оставлен (QA04-06)»; `:20` строка QA04-08 «Закрыто M02» из main; `:197-207` раздел «Закрытие M02» сохранён; `:22` QA04-10 «Закрыто раундом 2»; `:162,169,183` — «на текущем main QA04-06; QA04-08 закрыт M02, QA04-10 зелёный»; `:220-222` раздел «QA04 раунд 2». `grep` по «кандидат / 8965239 / не слит / временн / V028+ / после слияния M02» в обоих QA04-документах — пусто (единственное «временная» на `:205` — про LoggingMailSender как временную доставку, это по делу) |
| 2. `QA04-round2.md` + описание PR: M02/M09 слиты, V035+, статусы QA04-11/12/13, строка QA04-14 | ✅ выполнено | `docs/qa/QA04-round2.md:13-18`: QA04-06 «Bootstrap реализован в этом PR», QA04-11 «minor, main», QA04-12/13 «закрывается M05 #41», QA04-14 добавлен с шагами, severity «minor (major для демо-дня)», мерами для демо и задачей MVP; `:30` «V035+ (V031–V034 закреплены за M04–M07)»; `:222` «M02 (#29) и M09 (#25) слиты; условные M02-тесты активны в обычном mvn test». Описание PR: раздел «Правки по ревью 1» (4 пункта), V035+, QA04-14, 1290 кейсов / 0 skipped, known-defect IT — 4 ожидаемых failures. Дефект QA04-14 закреплён тестом `Qa04Round2M02KnownDefectsIT.emailAttacksMustNotLockOutTheLegitimateUser:36-45` (5 неверных с разных IP → верный пароль с нового IP ожидает 200; сейчас 429) — сценарий совпадает с описанием. Отличие от текста правок: мера для демо-дня — `APP_AUTH_LOGIN_MAX_FAILURES=20` вместо `APP_AUTH_LOGIN_WINDOW=PT2M`; обе переменные существуют (`application.yml:95-96`), вариант Codex тоже рабочий — см. «Желательные» п. 5 |
| 3. `RegisterPage.getRegisterErrorMessage`: 409 + `code=identity.profile_already_attached` → текст про организатора; Vitest-кейс | ✅ выполнено | `frontend/src/features/auth/RegisterPage.tsx:16-20` — ровно предложенный код, `ApiError.code` существует (`api/http.ts:12`). `RegisterPage.test.tsx` (новый, 30 строк): msw-мок `POST */api/v1/auth/register` → 409 `{code:'identity.profile_already_attached'}`, заполнение всех обязательных полей по `getByLabelText` (метки совпадают с разметкой формы), клик «Создать аккаунт», `findByRole('alert')` с полным русским текстом. Стиль и инфраструктура (`vi.stubEnv`, `server` из `test/setup`, `AuthProvider`) идентичны существующему `LoginPage.test.tsx`. Frontend CI зелёный |
| 4. `CreateUserUseCase`: сообщение + убрать мёртвую ветку | ✅ выполнено | `identity/application/CreateUserUseCase.java:66-67` — новое сообщение на русском, код `identity.profile_already_attached` сохранён; `:77` — просто `new ProfileEntity()`, `setExternalKey` убран (поведение при `imported` с чужим `userId` не изменилось: новый профиль без ключа, как и до PR). Тест `Qa04Round2AdminIntegrationTest.qa04_10_unverifiedSignupCannotClaimImportedProfile` не менялся |
| 5. AdminBootstrap (опционально) | ✅ выполнено, 40 строк, без миграции | `identity/infrastructure/AdminBootstrap.java` — разбор ниже. `AdminBootstrapIntegrationTest` — 3 кейса (а) создание active + bcrypt + глобальная `platform_admin` через `findGlobalRoleCodesByUserId`, (б) повторный `run` с другим паролем → hash равен исходному, (в) три комбинации пустых значений → `users.count()` не меняется. Класс `*IntegrationTest` → попадает в Surefire (`pom.xml:191-194` исключает только `*IT`), CI backend зелёный |
| Особая проверка: hash при существующем email не перезаписывается | ✅ | `AdminBootstrap.java:29-31`: `existsByEmail` → `log.info(...)`; `return` **до** `PasswordPolicy.validate` и `encode`. Никакого `findByEmail(...).setPasswordHash` в классе нет. Тест (б) это фиксирует |
| Особая проверка: пароль не логируется | ✅ | Два `log.info` с константными строками «bootstrap admin exists, password untouched» и «bootstrap admin created» — без email и пароля (`:30,38`). В `.env.prod.example` только закомментированные пустые строки (`:50-52`), `.gitignore:2-5` исключает `**/.env.*` кроме `*.example` |
| Особая проверка: пустые env — no-op | ✅ | `@Value("${APP_ADMIN_EMAIL:}")`/`"${APP_ADMIN_PASSWORD:}"` (default пустая строка), `:28 if (email.isBlank() \|\| password.isBlank()) return;`. В `compose.prod.yaml:53-54` `${APP_ADMIN_EMAIL:-}` → при отсутствии в `.env.prod` в контейнер уходит пустая строка → no-op |
| Особая проверка: compose.prod.yaml и `.env.prod.example` | ✅ | `infra/compose.prod.yaml:53-54` (сервис api, рядом с `DEMO_SEED`), `infra/.env.prod.example:50-52`. `compose.staging.yaml` не прокидывает — см. «Желательные» п. 3 |
| Особая проверка: новых эндпоинтов нет, TSV не менялся | ✅ | `git diff origin/main...pr/44 --stat` — `qa04-endpoints.tsv` отсутствует; контроллеры не менялись |
| Особая проверка: не ломает старт при DEMO_SEED / admin@flounder.demo | ✅ | `admin@flounder.demo` создаётся безусловно миграцией V020 (`V020__seed_fixes.sql:11`), `DEMO_SEED` сервером не читается (подтверждено `grep` по `src/main`). `ApplicationRunner` выполняется после Flyway → роль `platform_admin` (V002:49) и демо-admin уже есть. Если `APP_ADMIN_EMAIL=admin@flounder.demo` → ветка «exists», hash `Demo2026!` не трогается (ротация по-прежнему руками, как и сказано в отчёте `QA04-round2.md:26`). Если другой email → создаётся второй platform_admin, демо-сид `scripts/demo` продолжает логиниться своим `ADMIN_EMAIL/ADMIN_PASSWORD` (`config.ts:23`, `seed.ts:23` требует только роль platform_admin — можно указать и нового admin) |
| Особая проверка: тестовый профиль | ✅ бездействует | Бин без `@Profile`, создаётся во всех `@SpringBootTest`, но `application-test.yml` свойств `APP_ADMIN_*` не задаёт, CI-окружение их не экспортирует → `run` выходит на `:28`. В интеграционном тесте класс инстанцируется напрямую (`new AdminBootstrap(...)`) под `@Transactional` теста — бин контекста не участвует, состояния не остаётся |
| Транзакционность / гонка | ✅ достаточно | `@Transactional` (Spring) на `run` — бин вызывается `SpringApplication` через прокси, работает. Явной блокировки нет, но runner выполняется один раз на инстанс до открытия трафика; при двух инстансах одновременно второй `save` упрётся в `users.email unique` (V002:4) и упадёт — на стенде один инстанс, приемлемо |
| Modulith | ✅ | `identity.infrastructure.AdminBootstrap` использует только `identity.domain.PasswordPolicy` и репозитории своего модуля; `ModulithArchitectureTest` в CI зелёный |
| Старые тесты не ослаблены, чужие файлы не тронуты | ✅ | Изменения в существующих тестах — только комментарии (`Qa04KnownDefectsIT.java:17` «V035+», `Qa04Round2M02KnownDefectsIT.java:18` «Known defects on main») и добавление теста QA04-14. Файлы вне области QA04/identity/infra не менялись |
| Тексты UI на русском | ✅ | Сообщение 409 в `RegisterPage.tsx:18` и в `CreateUserUseCase.java:67` на русском; логи/комментарии на английском по AGENTS.md п. 9 |

## Блокирующие

Нет.

## Желательные (не держат слияние)

1. **Нормализация email в bootstrap** (`AdminBootstrap.java:28-29`): `existsByEmail` и `setEmail` берут значение как есть, без `trim().toLowerCase()`. Логин (`PlatformUserDetailsService:29`, `findByEmail`) тоже точный, так что вход согласован; но `PasswordService:62` и `UserLookupService:27` ищут `findByEmailIgnoreCase` → если в `.env.prod` указать `Admin@Flounder.Demo` при существующем `admin@flounder.demo`, появится второй пользователь, и восстановление пароля для этого email упадёт с `IncorrectResultSizeDataAccessException`. Правка на 1 строку: `email = email.trim().toLowerCase(Locale.ROOT)` в конструкторе. Задача M09-followup или следующий PR identity.
2. **Поведение при слабом пароле не задокументировано**: `PasswordPolicy.validate` (`:32`) бросает `BusinessException` из `ApplicationRunner` → контекст не поднимается, контейнер `api` уходит в restart-loop, healthcheck красный. Fail-fast правильный, но стоит дописать в `.env.prod.example:50` «пароль: ≥8 символов, буквы и цифры, ≤72 байт; иначе API не стартует» и строку в `docs/runbook-demo.md` (раздел первого деплоя).
3. **`infra/compose.staging.yaml`** (сервис `api`, `environment`) не прокидывает `APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD` — на staging bootstrap недоступен. Две строки для паритета с prod; задача M09-followup.
4. **Устаревший комментарий** `Qa04M02Conditions.java:6` «avoids linking to unmerged M02-specific classes» — M02 слит; сам `@EnabledIf` теперь всегда истинен и может быть убран при следующем касании этих тестов (не сейчас).
5. **Мера для демо-дня по QA04-14**: в отчёте и PR — `APP_AUTH_LOGIN_MAX_FAILURES=20`, в ревью 1 (таблица «Дефекты для стенда», п. 2) — `APP_AUTH_LOGIN_WINDOW=PT2M`. Обе переменные реальные (`application.yml:95-96`); для демо надёжнее сочетать (`MAX_FAILURES=20` + `WINDOW=PT2M`). Привести к одному варианту в `docs/runbook-demo.md` при подготовке стенда — задача координатора, не Codex.
6. После bootstrap пароль остаётся в окружении контейнера (`docker inspect api` покажет `APP_ADMIN_PASSWORD`). Bootstrap идемпотентен, поэтому после первого успешного старта переменные можно удалить из `.env.prod` и перезапустить `api` — зафиксировать в runbook.

## Текст правок для Codex

(пусто — слить без правок; пункты 1–3 «Желательных» — в задачу M09-followup)

## Порядок слияния

- Зависимостей от открытых PR нет: ветка уже на `028dc5e`, `merge-tree` чистый, миграций нет, TSV не менялся. Можно сливать первым из текущей пачки.
- С `pr/41` (M05, OutboxMailSender): известно из ревью 1 — `Qa04Round2M02KnownDefectsIT` ссылается на `LoggingMailSender` только строками, компиляцию не ломает; после M05 тесты QA04-12/13 станут зелёными в known-defect — обновить отчёт в M05-followup.
- С `pr/38`, `pr/41`, `pr/42` и M06/M07: пересечений по файлам нет (identity/application, identity/infrastructure, auth-фронт, infra compose/env, docs/qa/QA04-*).

## Переменные для стенда (infra/.env.prod) под bootstrap

Добавить две строки (имена, без значений):

```
APP_ADMIN_EMAIL=
APP_ADMIN_PASSWORD=
```

- `APP_ADMIN_EMAIL` — email нового администратора платформы (точное написание, в нижнем регистре — см. «Желательные» п. 1). `APP_ADMIN_PASSWORD` — пароль по политике M02: не менее 8 символов, буквы и цифры, не более 72 байт.
- `compose.prod.yaml:53-54` уже читает их из `.env.prod` с пустыми default, править compose не нужно; после правки `.env.prod` — `docker compose -f infra/compose.prod.yaml up -d api` (перезапуск api).

Что произойдёт, если их **не добавить**: ничего не ломается — compose передаст пустые строки, `AdminBootstrap` выйдет на первой проверке, в логе ничего. В БД останется только `admin@flounder.demo` с паролем `Demo2026!` из V020 (опубликован в `docs/02_ТЗ_демо.md`). Тогда обязательна ручная ротация по таблице «Дефекты для стенда» ревью 1 (п. 1): войти, сменить пароль через `/password`, записать новый в `scripts/demo` `.env` как `ADMIN_PASSWORD`, проверить `curl … /auth/login` с `Demo2026!` → 401, повторять после каждого `reset.sql`.

Что произойдёт, если **добавить**:
- email новый → при старте создаётся active platform_admin с bcrypt-хешем, лог «bootstrap admin created»; дальше можно указать этот же email/пароль в `scripts/demo/.env` (`ADMIN_EMAIL/ADMIN_PASSWORD`) для сида — `seed.ts` требует только роль platform_admin. `admin@flounder.demo` при этом **остаётся** с `Demo2026!` — его всё равно нужно ротировать или заблокировать руками (`PATCH /users/{id}` status=BLOCKED админом), bootstrap этого не делает намеренно.
- email = `admin@flounder.demo` → лог «bootstrap admin exists, password untouched», пароль не меняется — bootstrap не является способом ротации.
- пароль слабее политики или пустой при заданном email → при пустом — no-op; при слабом — API не стартует (BusinessException в ApplicationRunner, контейнер в restart-loop) до исправления `.env.prod`.
- повторные старты с теми же значениями — идемпотентно; после первого успешного старта переменные можно убрать.
