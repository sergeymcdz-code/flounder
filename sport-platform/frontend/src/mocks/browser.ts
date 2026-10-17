import { calendarHandlers } from './calendar-handlers'
import { notificationHandlers } from './notification-handlers'
import { adminHandlers } from './admin-handlers'
import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'
import { publicHandlers } from './public-handlers'
import { authHandlers } from './auth-handlers'

export const worker = setupWorker(...calendarHandlers, ...notificationHandlers, ...adminHandlers, ...handlers, ...publicHandlers, ...authHandlers)
