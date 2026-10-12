# Organizer demo mocks (T04)

Run from `sport-platform/frontend`:

```sh
npm ci
VITE_API_MOCK=true npm run dev
```

Open `/organizer/events/11111111-1111-4111-8111-111111111111` and log in with any syntactically valid email and nonempty password. Mock login issues an organizer/operator demo token. MSW is started only when `VITE_API_MOCK=true`; it is removed from the normal production bundle by Vite. Do not enable this flag in production.

The fixture contains one event, two sessions, four disciplines, thirty registrations and six heats. Changes remain in memory while navigating tabs; a full reload resets the fixture. The import accepts an `.xlsx` file and returns a fixed report (three new registrations, one update, one error). The downloadable template is a real workbook with the contract headers. This is a UI fixture, not an Excel parser or production seeding implementation.

API assumptions to align when T02/T03 land:

- The event generation summary is `{disciplines: [{eventDisciplineId, ...GenerationResult}], durationMs, violations?}`.
- `GET /age-group-presets` returns `[{id, name, ageGroups}]`.
- Pool data is supplied in `event.venue` or the existing venues list (`lanes`, `courseLength`). Missing pool dimensions are shown as unknown; lane changes are disabled until the lane count is available.
- Heat details may supply `resultsPublished`; otherwise the existing publication status is used together with recorded results. Publishing a discipline uses the existing per-unit `/publication` operation sequentially; the operation can partially succeed and retry safely.
- The organizer registration DTO adds camelCase `seedTimeMs`, `paymentStatus`, `source`. Age group year bounds can be `null`.

Browser verification:

```sh
npx playwright install chromium
npm run test:organizer
ORGANIZER_SCREENSHOT_DIR=../../docs/screenshots/T04 npm run test:organizer
```

For an existing system Chromium, set `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium`. The test server enables MSW automatically. Six tests cover the complete acceptance flow, validation feedback, lifecycle and result publication, 401/403, all tabs at 1280/1024 px, session CRUD, registration actions, and validator focus.
