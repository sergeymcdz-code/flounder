# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend enabling type-aware lint rules by installing `oxlint-tsgolint` and editing `.oxlintrc.json`:

```json
{
  "$schema": "./node_modules/oxlint/configuration_schema.json",
  "plugins": ["react", "typescript", "oxc"],
  "options": {
    "typeAware": true
  },
  "rules": {
    "react/rules-of-hooks": "error",
    "react/only-export-components": ["warn", { "allowConstantExport": true }]
  }
}
```

See the [Oxlint rules documentation](https://oxc.rs/docs/guide/usage/linter/rules) for the full list of rules and categories.

## T07: public pages, print and PWA

See [PR #8](https://github.com/sergeymcdz-code/flounder/pull/8) for the implementation report, manual checks and verification attachments.

```bash
npm ci
npm run dev:demo
# Alternatively, enable mocks in the development server:
VITE_API_MOCK=true npm run dev
# Open http://localhost:5173/e/demo (MSW, no backend required)
npm run test
npm run build
npm run lint
npx playwright install chromium
npm run test:e2e
```

The production build always excludes MSW. Development mocks use only `npm run dev:demo` or `VITE_API_MOCK=true npm run dev`. Public API requests default to the current origin; configure
`VITE_API_BASE_URL` for a separate backend. The development mock modes register
MSW and disable PWA through the same `VITE_API_MOCK` flag. Production registers
the PWA worker when that flag is disabled. Both workers share the root scope.

The combined mock worker preserves T04 organizer handlers and adds public and
participant authentication handlers. `npm run test` runs both the existing Node
tests and public Vitest tests; `npm run test:organizer` runs the organizer browser
suite separately from `npm run test:e2e`.
