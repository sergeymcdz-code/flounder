# Эталонная реализация алгоритма посева (TypeScript)

Файлы `lanes.ts`, `seeding.ts`, `schedule.ts`, `types.ts` — чистые функции без зависимостей; `seeding.test.ts` — тесты (20 проверок, все проходят на Node 22: `node --experimental-strip-types --test`).
Используются как точная спецификация для переноса на Java в задании T02 (пакет `scheduling.domain`). При расхождении текста ТЗ §4 и кода — приоритет у кода + тестов.
