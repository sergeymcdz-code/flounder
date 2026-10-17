import { apiFetch, apiDownload } from './http'
export type PaymentSettings = {
  enabled: boolean
  instructions: string
  feeMode: 'PER_DISCIPLINE' | 'PER_EVENT'
  eventFeeAmount: number | null
  currency: string
  dueDays: number
}
export const defaultPaymentSettings: PaymentSettings = {
  enabled: true,
  instructions: '',
  feeMode: 'PER_DISCIPLINE',
  eventFeeAmount: null,
  currency: 'RUB',
  dueDays: 3,
}
export type PaymentMethod =
  | 'CASH'
  | 'TRANSFER'
  | 'ONLINE'
  | 'CLUB_INVOICE'
  | 'OTHER'
export const methodLabels: Record<PaymentMethod, string> = {
  CASH: 'Наличные',
  TRANSFER: 'Перевод',
  ONLINE: 'Онлайн',
  CLUB_INVOICE: 'Счёт клуба',
  OTHER: 'Другое',
}
export const paymentLabels: Record<string, string> = {
  NOT_REQUIRED: '—',
  PENDING: 'Ожидает',
  PAID: 'Оплачено',
  WAIVED: 'Освобождён',
  REFUNDED: 'Возвращено',
}
export type PaymentDetails = {
  feeAmount?: number | null
  feeCurrency?: string | null
  paidAmount?: number | null
  paidAt?: string | null
  paidBy?: { userId: string; name: string } | null
  paymentMethod?: string | null
  paymentReference?: string | null
  paymentComment?: string | null
  dueAt?: string | null
  paymentStatus?: string
}
export type PaymentInput = {
  amount?: number
  method?: PaymentMethod
  reference?: string
  comment?: string
  paidAt?: string
}
export type PaymentAction =
  | 'mark-paid'
  | 'waive-payment'
  | 'refund-payment'
  | 'reset-payment'
export type BulkResult = {
  updated: number
  skipped: { id: string; reason: string }[]
}
export type PaymentLog = {
  id: string
  action: string
  amount: number | null
  currency: string | null
  method: string | null
  reference: string | null
  comment: string | null
  performedByName: string
  performedBy: string
  performedAt: string
}
export type PaymentSummary = {
  currency: string
  totals: {
    due: number
    paid: number
    pending: number
    waived: number
    refunded: number
  }
  byClub: {
    club: string
    participants: number
    registrations: number
    due: number
    paid: number
    pending: number
    pendingRegistrationIds: string[]
  }[]
}
export const paymentAction = (
  id: string,
  action: PaymentAction,
  input: PaymentInput,
  token: string,
) =>
  apiFetch(`/api/v1/registrations/${id}/${action}`, {
    method: 'POST',
    accessToken: token,
    body: JSON.stringify(input),
  })
export const bulkPayment = (
  eventId: string,
  registrationIds: string[],
  input: PaymentInput,
  token: string,
) =>
  apiFetch<BulkResult>(`/api/v1/events/${eventId}/payments/mark-paid-bulk`, {
    method: 'POST',
    accessToken: token,
    body: JSON.stringify({ registrationIds, ...input }),
  })
export const paymentHistory = (id: string, token: string) =>
  apiFetch<PaymentLog[]>(`/api/v1/registrations/${id}/payment-log`, {
    accessToken: token,
  })
export const paymentSummary = (eventId: string, token: string) =>
  apiFetch<PaymentSummary>(`/api/v1/events/${eventId}/payments/summary`, {
    accessToken: token,
  })
export const paymentInstructions = (eventId: string, token: string) =>
  apiFetch<PaymentSettings>(`/api/v1/events/${eventId}/payment-instructions`, {
    accessToken: token,
  })
export const savePaymentSettings = (
  eventId: string,
  payment: PaymentSettings,
  token: string,
) =>
  apiFetch(`/api/v1/events/${eventId}`, {
    method: 'PATCH',
    accessToken: token,
    body: JSON.stringify({ payment }),
  })
export const recalculateFees = (eventId: string, token: string) =>
  apiFetch<{ updated: number }>(
    `/api/v1/events/${eventId}/payments/recalculate-fees`,
    { method: 'POST', accessToken: token },
  )
export async function downloadPaymentSummary(eventId: string, token: string) {
  const blob = await apiDownload(
    `/api/v1/events/${eventId}/payments/summary.xlsx`,
    token,
  )
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'Оплата-по-клубам.xlsx'
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
export function money(amount: number | null | undefined, currency = 'RUB') {
  return amount == null
    ? '—'
    : new Intl.NumberFormat('ru-RU', { style: 'currency', currency }).format(
        amount,
      )
}
