# QA01 — ревью follow-ups (PR #39, 823abaa)

Ветка `pr/39` (`fix/QA01-review`, **823abaa** «fix(qa01): address organizer core review follow-ups»), один коммит поверх `origin/main` **63263d1** (merge-base = HEAD main, ветка актуальна). Ревьюер: Claude, 13.10.2026. Диф с `main`: 13 файлов, +225/−31. CI зелёный (по треду). Не прогонял Maven/npm/Docker — проверено статически по `git show pr/39:…`, `git diff origin/main...pr/39`, `git merge-tree --write-tree`.

**Вердикт: слить.** Все 6 пунктов выполнены; поведение изменено только там, где просило ревью QA01 (отдельный WARNING на снятого, `null` в `venueMeta` = «не задано»); структура ответов §5 не менялась; старые тесты не ослаблены (единственное удаление — два `null`-случая из `rejectsInvalidPoolMetadata`, перенесённые в новый позитивный тест по п. 3); миграций и новых эндпоинтов нет; `SeedingValidator`, `GenerateCompetitionUnitsUseCase`, `scripts/demo` не тронуты (как требовало ревью). Единственный конфликт — с `pr/40` (M07), один файл `OrganizerHeatsPage.tsx`, тривиальный.

## Проверено по пунктам

| № | Пункт | Где | Результат |
|---|---|---|---|
| 1 | `Violation` с `registrationId` для `PUBLISHED_HEAT_CHANGED` | `SchedulingData.publishedWithdrawalViolations(Unit)`: для `PUBLISHED` — по одному `PUBLISHED_HEAT_CHANGED/WARNING` на каждую запись `WITHDRAWN` с `disciplineId`, `number`, `registrationId = entry.registrationId()`, текст «Опубликованный заплыв изменён: участник снят, дорожка N». `RecalculateScheduleUseCase`: условие сужено до изменения времени (`!Objects.equals(unit.scheduledAt(), time)`), прежний WARNING «…изменён при пересчёте» без `registrationId` сохранён. `ValidateUnitsUseCase`: прежний блок заменён на `addAll(helper)`. Unit-тест `PublishedWithdrawalTest` (6 статусов + ASSIGNED + поля WARNING); интеграция `qa01PublishedWithdrawalWarnsWithoutMovingOtherLanes` расширена: 2 снятия в одном заплыве, оба ответа (recalculate/validate) содержат ровно 2 WARNING с правильными `registrationId`/дорожками/`heatNumber`, остальные дорожки не сдвинуты, аудит SCHEDULE+VALIDATION содержит `registrationId`, после сдвига сессии на 1 мин — 2 «временных» WARNING без `registrationId` + 2 с ним | **выполнен** |
| 2 | Общий helper «PUBLISHED+WITHDRAWN» | один статический метод в `SchedulingData`, оба use case вызывают его; дублирования больше нет. Метод package-private статический — M07 (`ShiftScheduleUseCase` в том же пакете) сможет переиспользовать | **выполнен** |
| 3 | `{"lanes":null}` = не задано | `CreateVenueUseCase`: `containsKey` → `Object value = meta.get(field); if (value == null) continue;`. Неверные ненулевые (7, 0, 8.5, "8", 30, 25.5) по-прежнему 400 с тем же кодом. Новый `nullPoolMetadataIsTreatedAsUnspecified` (4 комбинации → 201, round-trip через `GET /venues/{id}`: число сохраняется, `null` отсутствует или `null`). Семантика `null`-`venueMeta` целиком не менялась (NPE был бы и раньше на `containsKey`) | **выполнен** |
| 4 | Точные русские строки в тестах | `Qa01OrganizerCoreIntegrationTest`: все пять `matches(".*[А-Яа-я].*")` заменены на `assertEquals` с точными текстами (4 перехода события через `Map`, 2 сообщения дисциплины, сообщение `venueMeta`, «Укажите название события»). Строки сверены с источниками в `main` (CI зелёный подтверждает) | **выполнен** |
| 5 | `JWT_SECRET` — валидный Base64 с пояснением | `AGENTS.md`, `tasks/QA00_…md`, `sport-platform/README.md` (4 места: Bash, PowerShell ×2, таблица переменных): `JWT_SECRET="$(openssl rand -base64 48)"` + фраза «Base64 от ≥32 случайных байт, `JwtService` использует `Decoders.BASE64`, строка с дефисами → 500 при входе, не хранить в Git». 48 байт > 32 — удовлетворяет `JwtService`. Вариант отличается от буквального предложения ревью (`printf … | base64`), но выполняет требование «валидный Base64 с пояснением» и безопаснее (нет общего известного секрета в документации) | **выполнен** |
| 6 | Read-only чекбокс `circleSeedTopHeats` (QA05-01) | `OrganizerHeatsPage.tsx`: в первой карточке `<label><input type="checkbox" checked={false} disabled aria-describedby="circle-seed-help"/>Круговой посев трёх сильнейших заплывов</label>` + пояснение «Круговой посев выключен. Настройка доступна только для просмотра.» (`styles.muted` существует в `OrganizerEventPage.module.css`). Запросы генерации не изменены (`scheduling.api.ts` шлёт `circleSeedTopHeats: false`). Совпадает с локатором e2e `qa05-demo.spec.ts:202` (`/circleSeedTopHeats|круговой посев/i`, count 1, not checked). Новый `OrganizerHeatsPage.test.tsx`: доступное имя, disabled, описание, клик не меняет состояние; для событийной и дисциплинной генерации — тело запроса ровно `{force:false, circleSeedTopHeats:false}` | **выполнен** |

Документация: `docs/qa/QA01-organizer-core.md` — «Закрывает QA05-08…» + абзац об уточнениях и прогоне на `63263d1` (1155 тестов). Описание PR содержит ту же фразу и список файлов.

### Регрессии / контракты / тесты / миграции

- Контракты §5: `Violation` уже имеет поле `registrationId` — заполняется существующее поле; `POST /venues` стал мягче только для явного `null`; `validate`/`recalculate` — та же структура, больше элементов в `violations`. UI (`OrganizerHeatsPage`) показывает violations списком — несколько WARNING на заплыв отобразятся корректно.
- Старые тесты: кроме переноса двух `null`-кейсов — только добавления и усиления (`anyMatch` → точные количества/поля).
- Миграций нет, новых эндпоинтов нет (`qa04-endpoints.tsv` не менялся).
- `ModulithArchitectureTest`: helper внутри `scheduling.application`, использует уже импортируемый `results.CompetitionUnitStore`.

## Блокирующие замечания

Нет.

## Желательные замечания

1. `openssl rand -base64 48` генерирует новый секрет при каждом запуске: все выданные JWT становятся недействительными после перезапуска backend (dev-удобство — нужно заново логиниться, фронт с `VITE_API_MOCK=false` покажет 401). Стоит добавить одну фразу «для постоянной работы сохраните сгенерированное значение в `.env`» (в README это есть, в AGENTS.md/QA00 — нет).
2. README PowerShell: `(openssl rand -base64 48)` требует `openssl` в `PATH` на Windows (есть в Git for Windows, но не по умолчанию в PATH). Альтернатива без зависимостей: `[Convert]::ToBase64String((1..48 | % { Get-Random -Max 256 }) -as [byte[]])` или указать путь `C:\Program Files\Git\usr\bin\openssl.exe`.
3. `SchedulingIntegrationTest.qa01PublishedWithdrawalWarnsWithoutMovingOtherLanes` стал длинным (сдвиг сессий, подсчёт аудита) — при следующем касании разделить на два теста (снятия / сдвиг времени), чтобы падение указывало на причину.
4. `PublishedWithdrawalTest.withdrawalWarningAppliesOnlyToPublishedHeats` перечисляет статусы строками («FINISHED», «CANCELLED» и т. п.) — если часть из них не существует в домене, тест ничего не проверяет для них; лучше брать из перечисления/констант статусов.

## Порядок слияния / конфликты

`git merge-tree --write-tree`:

| С чем | Результат |
|---|---|
| `origin/main` 63263d1 | чисто |
| `pr/37` (M01 follow-ups) | чисто |
| `pr/29` (M02), `pr/36` (M08), `pr/38` (M04) | чисто |
| `pr/40` (M07) | **конфликт**, 1 файл: `sport-platform/frontend/src/features/organizer/OrganizerHeatsPage.tsx` — обе ветки вставляют блок в начало первой `<article className={styles.card}>` (M07: `<p aria-live="polite">Отставание…</p>` + кнопка «Сдвинуть расписание…»; #39: `<label>` чекбокса + `<p id="circle-seed-help">`). `SchedulingData.java` авто-сливается (M07 добавляет `timezone(eventId)`, #39 — helper). `CompetitionUnitStore.Unit/Entry` в M07 не меняются — `PublishedWithdrawalTest` компилируется после слияния. Тест #39 строит `EventContext` с `status: 'DRAFT'` → в M07 `hasPublicLive=false`, запрос `/public/.../live` не делается, MSW `onUnhandledFrame: 'error'` не сработает; `timezone?` в M07 опциональный — типизация контекста в тесте не ломается |

Разрешение в `OrganizerHeatsPage.tsx` при ребейзе M07: оставить оба блока (сначала абзац «Отставание», затем label чекбокса и пояснение, затем `actions` с кнопкой сдвига). `getAllByRole('checkbox')` в тесте #39 ожидает ровно 1 — M07 чекбоксов на страницу не добавляет (`ScheduleShiftForm` рендерится только в диалоге), ожидание остаётся верным.

**Рекомендуемый порядок:** #37 и #39 слить первыми (маленькие, без миграций, чисто с `main` и между собой), затем ребейз M07 (#40) с разрешением одного файла и M02 (#29) — см. M01-followups-review. При ребейзе M07 helper `SchedulingData.publishedWithdrawalViolations` можно сразу переиспользовать в `ShiftScheduleUseCase` для опубликованных заплывов (ревью QA01, п. 2 — ради этого он и выносился).
