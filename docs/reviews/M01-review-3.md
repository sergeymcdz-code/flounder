# M01 — ревью 3 (PR #15, 5243b06)

Ветка `pr/15` (161e093 → df688d0 → **5243b06** «fix(admin): integrate M01 with QA04 security checks») ребейзнута на `origin/main` **a37fcb5** (merge-base = HEAD main, т.е. ветка актуальна). Ревьюер: Claude, 13.10.2026. Диф с `main`: 70 файлов, +1901/−197; диф раунда (df688d0..5243b06): 12 файлов, +136/−57. CI на 5243b06: backend `success`, frontend `success`; GitHub `mergeable_state: clean`. Описание PR дополнено разделом «Правки по ревью 2» (1088 backend-тестов с Docker, 51 Vitest, 728 запросов матрицы QA04).

Не прогонял: Maven/npm/Docker. Проверено статически по `git show pr/15:…`, `git diff origin/main...pr/15`, `git merge-tree --write-tree`.

**Вердикт: слить.** Все пункты инструкции раунда 2 выполнены точно по тексту; из QA04 при ребейзе ничего не потеряно; миграция одна (V028); тесты не ослаблены; с `main` сливается чисто. Единственный конфликт — тривиальный с `pr/21` (`RegistrationReviewPage.tsx`, пустая правка EOF против добавленного компонента), разрешается при ребейзе второго из них.

## Проверено

### (1) Инструкция раунда 2 — пункт за пунктом

| № | Пункт | Где | Результат |
|---|---|---|---|
| 1a | `JwtAuthenticationFilter` — тело M01 + `entryPoint.commence` для expired/invalid, `doFilter` вне `catch` | конструктор `(JwtService, UserRepository, RestAuthenticationEntryPoint)`; `users.findAuthenticationById` (1 SQL); отсутствующий → `InsufficientAuthenticationException("Пользователь не найден")` → 401 `authentication_required`; не-`ACTIVE` → `UserBlockedException` → 401 `identity.user_blocked`; claim `roles` — только `instanceof Collection<?>`, пустой список допустим; `catch (ExpiredTokenException)`/`catch (InvalidTokenException)` → `clearContext()` + `entryPoint.commence(…, new InsufficientAuthenticationException("JWT expired"/"JWT invalid", ex))` + `return`; **`filterChain.doFilter(request, response)` — последняя строка метода, вне `try`** (желательное 1 раунда 2 закрыто) | **выполнен** |
| 1b | `JwtService` — версия `main` | `git diff origin/main pr/15 -- …/JwtService.java` пустой: `requireIssuer`, обязательный `exp` на месте | **выполнен** |
| 1c | `LoginUseCase` — M01 + компромисс, без широкого `catch (AuthenticationException)` | `PasswordEncoder` инжектирован; `catch (DisabledException)` → `loadUserByUsername` + `passwordEncoder.matches(...)` → при совпадении `BusinessException("identity.user_blocked", …)` (403), иначе `InvalidCredentialsException` (401); `catch (BadCredentialsException \| UsernameNotFoundException)` → 401; дополнительно сохранён узкий `catch (AccountStatusException)` из QA04 (Locked/Expired/CredentialsExpired → 401; `DisabledException` перехватывается раньше, порядок корректен). `InternalAuthenticationServiceException`/`AuthenticationServiceException` не ловятся → 500 (`LoginUseCaseTest.infrastructureFailures…` сохранён, `verifyNoInteractions(users, roles, jwt, encoder)`). `findGlobalRoleCodesByUserId` — M01 | **выполнен** |
| 1d | `InvalidCredentialsException` | не изменён относительно `main` («Неверная почта или пароль») | **выполнен** |
| 1e | `CreateUserRequest` объединён | все `@Size(max=…)` по колонкам + `@JsonIgnore @AssertTrue isPasswordWithinBcryptLimit` из `main` **+** `@Size(min = 8, max = 72, message = "Пароль должен содержать от 8 до 72 символов")` на `password`, поле `List<String> roles`, 11-аргументный конструктор-делегат | **выполнен** |
| 1f | `JwtAuthenticationFilterTest` | версия `main` (`rejectsTokenWithoutRolesClaim` с `ArgumentCaptor` + `verify(entryPoint).commence`, `hasMessage("JWT invalid").hasCauseInstanceOf(InvalidTokenException)`), конструктор без `assignments`, `verifyNoInteractions(filterChain, users)`; **добавлен** `missingUserGetsOrdinaryAuthenticationErrorWithoutContinuingTheRequest` (реальный `RestAuthenticationEntryPoint`, `MockHttpServletResponse` → 401, `code == authentication_required`, контекст пуст, `verifyNoInteractions(chain)`) | **выполнен** |
| 1g | `GlobalExceptionHandler` | диф с `main` — ровно две добавленные строки `case` (`identity.access_denied/user_blocked/cannot_modify_self` → 403; `organizations.member_not_found/user_not_found` → 404); `@ExceptionHandler(NoResourceFoundException)` → 404 `common.not_found` (`:269-272`) на месте | **выполнен** |
| 2a | `Qa04InputIntegrationTest.loginForDisabledAccounts…` → два случая | переименован в `loginForDisabledAccountsOnlyRevealsAccountStateWithTheCorrectPassword(String state)` (`inactive`/`blocked`): верный `PASSWORD` → 403 `identity.user_blocked`; `wrong-password` → 401 `identity.invalid_credentials`, тело `doesNotContain("DisabledException","blocked","inactive",PASSWORD)` | **выполнен** |
| 2b | `Qa04DataAccessIntegrationTest.selfRegistrationIgnores…` → разделить | (а) `anonymousSelfRegistrationRejectsInjectedRolesWithoutCreatingAUser`: `roles:["platform_admin"]` + мусор → 403 `identity.access_denied`, `count(*) from users where email=?` = 0; (б) `selfRegistrationWithoutRolesIgnoresInjectedMembershipAndStatus`: 201, роли ровно `participant`, членств 0, **плюс** `status == 'active'` в БД (строже, чем просили) | **выполнен** |
| 2c | `qa04_07` перенесён | из `Qa04KnownDefectsIT` удалён (−6 строк, единственное удаление в тестах), в `Qa04InputIntegrationTest` добавлен с 400 `validation_error`, `violations[?(@.field=='password')]`, и проверкой, что строка в `users` не создана; `docs/qa/QA04-security-rbac.md` — QA04-07 «**Закрыто M01**», итог «7 исправлены (QA04-07 закрыт M01), 3 оставлены», список владельцев и раздел «Наблюдения» обновлены | **выполнен** |
| 3 | 6 строк TSV + `org-member` | `cat -A`: все 6 строк — ровно 7 TAB-полей, включая пустые хвосты (`200<TAB><TAB>$`), пути дословно по `@RequestMapping`; `Qa04RoleMatrixIntegrationTest.prepare()`: `case "org-member" -> member("org", "target", "member");` (помощник `member(String, String, String)` есть в `Qa04SecurityFixture:95`). Строки `main` не тронуты (в дифе TSV только `+`). CI: 728 запросов матрицы + точное покрытие 104 маппингов — зелёные | **выполнен** |
| 4 | §5.8 ТЗ | `docs/02_ТЗ_демо.md` §5.8: «Логин заблокированного: 403 `identity.user_blocked` только при верном пароле, при неверном — 401 `identity.invalid_credentials` без раскрытия статуса аккаунта»; в строке `POST /api/v1/users`: «с `roles` без прав администратора → 403 `identity.access_denied`, пользователь не создаётся» | **выполнен** |
| 5 | описание PR, полный прогон | раздел «Правки по ревью 2» + обновлённые «Проверки»: 1088 backend (перечислены все обязательные классы), 51 Vitest, lint/build; `git diff --check` | **выполнен** |

### (2) Ничего из QA04 не потеряно при ребейзе

- Просмотрены **все** удалённые строки `git diff origin/main...pr/15 -- sport-platform/src/main sport-platform/frontend/src` (≈120 строк). Все удаления — осознанные правки M01, уже принятые в раундах 1–2 (старый `findById`+`assignments` в фильтре, `findRoleCodesByUserId` → `findGlobalRoleCodesByUserId`, английские сообщения → русские, `@NotNull userId` → `email|userId` в `AddOrganizationMemberRequest`, перенос `/admin/registrations/review`, `Виды спорта` → меню «Администрирование»). Ничего из QA04 (`@Size` по колонкам, `isPasswordWithinBcryptLimit`, `requireIssuer`/`exp`, `NoResourceFoundException`→404, `RestAuthenticationEntryPoint` JSON-401, CORS/actuator) не удалено.
- Файлы QA04 без изменений относительно `main`: `JwtService.java`, `InvalidCredentialsException.java`, `Qa04JwtIntegrationTest.java`, `Qa04SecurityFixture.java`, `Qa04RoleMatrixIntegrationTest.java` (кроме +1 строки `org-member`). В `Qa04JwtIntegrationTest` теста на пустой `roles:[]` нет — снятие требования непустого списка в фильтре (решение раунда 2 ради `oldTokenReflectsRoleChangesAndZeroRolesRemainAuthenticated`) ничего в `main` не ломает.
- `RestAuthenticationEntryPoint`: дефолт `authentication_required` / «Для доступа требуется авторизация» сохранён, добавлена только ветка `instanceof UserBlockedException` (переставлены импорты — косметика).
- `LoginUseCaseTest`: `DisabledException` убран из `credentialFailures()` (теперь он — отдельный параметризованный тест на 403/401 по `encoder.matches`), `Locked/AccountExpired/CredentialsExpired/BadCredentials/UsernameNotFound` → 401 и 500 для инфраструктурных ошибок сохранены.

### (3) Миграции и тесты

- `git diff --name-status origin/main...pr/15 -- '*/db/migration/*'` → только `A V028__users_search_index.sql` (`create index users_lower_email_idx on users (lower(email));`), не менялась с 161e093.
- Удаления в тестах (`--numstat`): `Qa04KnownDefectsIT` −6 (перенос), остальные файлы — замены конструкторов/одной строки с эквивалентным или более строгим ожиданием. Удалённых или `@Disabled` тестов нет. Vitest-файлы — только `+`.

### Сверх инструкции

- Желательное 1 раунда 2 (`doFilter` вне `catch`) — закрыто. Желательное 3 (72 символа vs 72 байта) — закрыто объединением `@Size` + `@AssertTrue`.
- Открытыми (не блокируют) остаются желательные 2, 4, 5 раунда 2: `findRoleCodesByUserIds` без фильтра `organizationId/eventId is null` (scoped-роли в бейджах списка `/admin/users`); `admin-handlers.ts:134` `GET /events → []` перекрывает мок событий в dev-MSW; `changePassword` не трогает `updated_at`; CI frontend без `npm test`; Hibernate statistics на общем `SessionFactory` в `assertSqlCount`. Отдельным PR/задачей, не в M01.
- Фронт `LoginPage.tsx` показывает «Неверная почта или пароль» только на 401 — на 403 `identity.user_blocked` покажет общий текст ошибки (отмечено в раунде 2, для демо приемлемо; кандидат в M02).

## Блокирующие замечания

Нет.

## Желательные замечания

1. `identity/infrastructure/persistence/repository/UserRoleAssignmentRepository.java:21` — `findRoleCodesByUserIds` без `organizationId is null and eventId is null`: бейджи в списке пользователей показывают scoped-роли, которые диалог «Роли» снять не может (открыто с раунда 1). Одна строка в JPQL.
2. `frontend/src/mocks/admin-handlers.ts:134` — `GET */api/v1/events → []` в `admin`-обработчиках перекрывает мок событий для администратора в `VITE_API_MOCK=true` (открыто с раунда 1).
3. `AdminUserService.changePassword` — не обновляет `updated_at` (открыто с раунда 1).
4. CI frontend job без `npm test` — Vitest (`UsersAdminPage.test.tsx`, `OrganizationsAdminPage.test.tsx`) в CI не исполняются; отдельный PR по инфраструктуре.
5. `LoginPage.tsx` — ветка для 403 `identity.user_blocked` с текстом «Пользователь заблокирован. Обратитесь к администратору» (M02).

## Текст правок для Codex

Не требуется. PR готов к слиянию без изменений.

## Порядок слияния / конфликты

`git merge-tree --write-tree` (13.10, все ветки, кроме pr/18, от текущего `main` a37fcb5; pr/18 — от eada404):

| Пара | Результат |
|---|---|
| `origin/main` ↔ `pr/15` | **чисто** |
| `pr/15` ↔ `pr/18` (QA03) | **чисто** (pr/18 трогает только `identity/application/ProfileLookupService.java` + seed; pr/15 его не касается) |
| `pr/15` ↔ `pr/21` (QA06) | **1 текстовый конфликт**: `frontend/src/features/admin/RegistrationReviewPage.tsx` — pr/21 добавил `\n` в конец файла и поправил 3 русских текста, pr/15 дописал после `RegistrationReviewPage` новый компонент `OrganizationRegistrationReviewPage`. Разрешение: взять правки текста pr/21 + блок pr/15 целиком (конфликтный хунк — «ничего против блока M01»). `AppLayout.tsx` сливается автоматически (pr/21 — `hasRole('judge')` для «Результаты», pr/15 — меню «Администрирование»); новый `AppLayout.test.tsx` из pr/21 проверяет только ссылку «Результаты» — семантического конфликта нет |
| `pr/15` ↔ `pr/24` (M10) | **чисто**; pr/24 правит TSV в середине (`publication` → `unit-in-progress`) и добавляет `case` в `GlobalExceptionHandler` в другом месте — автослияние; новых маппингов без строк TSV pr/24 не вносит (CI обоих зелёный) |
| `pr/15` ↔ `pr/26` (QA02) | **чисто** (`ResultsController` + отчёт) |

**Рекомендуемый порядок:** `#15` (M01) слить **первым** — он самый крупный, уже ребейзнут на HEAD `main`, конфликтует только с #21 и тривиально. Затем #24 / #26 / #18 в любом порядке (с #15 чистые). #21 — после #15, с ребейзом и разрешением одного хунка в `RegistrationReviewPage.tsx` (оставить обе части) и повторным `npm run build && npm run lint && npm test`. Если #21 по какой-то причине сольют раньше — то же самое делает автор #15, инструкция симметрична.

**После деплоя** (повтор из раунда 2): сменить пароль `admin@flounder.demo` через `PATCH /api/v1/users/{id}/password` (QA04-06); при наличии на стенде строк `users.status = 'ACTIVE'` — `update users set status = lower(status)`; ручная проверка: логин заблокированного с верным паролем → 403, с неверным → 401 «Неверная почта или пароль», `DELETE /api/v1/users/{свой id}` под admin → 403.
