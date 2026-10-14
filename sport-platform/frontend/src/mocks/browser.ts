import { notificationHandlers } from './notification-handlers'
import { adminHandlers } from './admin-handlers'
import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'
import { publicHandlers } from './public-handlers'
import { authHandlers } from './auth-handlers'

export const worker = setupWorker(...notificationHandlers, ...adminHandlers, ...handlers, ...publicHandlers, ...authHandlers)
