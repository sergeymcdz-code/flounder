import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest'
import { setupServer } from 'msw/node'
import { publicHandlers } from '../mocks/public-handlers'
import { DEMO_TODAY, setEventPhaseClock } from '../features/public/event-phase'

export const server = setupServer(...publicHandlers)
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
// Fixtures are dated around the demo day; UX-24 tests set their own clock for the date guard.
beforeEach(() => setEventPhaseClock(() => DEMO_TODAY))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

// jsdom does not implement native modal dialog methods.
HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
