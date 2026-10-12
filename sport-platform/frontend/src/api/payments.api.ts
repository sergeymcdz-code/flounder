import { apiFetch } from './http'

export type PaymentStatus =
  | 'CREATED'
  | 'PENDING'
  | 'WAITING_FOR_CAPTURE'
  | 'SUCCEEDED'
  | 'CANCELED'
  | 'FAILED'
  | 'REFUNDED'
  | string

export type PaymentProvider = 'MANUAL' | 'YOOKASSA' | string

export type Payment = {
  id: string
  registrationId: string
  amount: number
  currency: string
  status: PaymentStatus
  provider: PaymentProvider
  confirmationUrl: string | null
  expiresAt: string | null
  paidAt: string | null
  canceledAt: string | null
  createdAt: string
  updatedAt: string
}

/**
 * Получает платёж текущего участника по его собственной заявке.
 */
export function getPaymentForRegistration(
  registrationId: string,
  accessToken: string,
): Promise<Payment> {
  return apiFetch<Payment>(
    `/api/v1/registrations/${encodeURIComponent(registrationId)}/payments`,
    { accessToken },
  )
}

/**
 * Создаёт платёж для собственной подтверждённой заявки участника.
 */
export function createPaymentForRegistration(
  registrationId: string,
  accessToken: string,
): Promise<Payment> {
  return apiFetch<Payment>(
    `/api/v1/registrations/${encodeURIComponent(registrationId)}/payments`,
    {
      method: 'POST',
      accessToken,
    },
  )
}

/**
 * Получает платёж для проверки сотрудником платформы.
 */
export function getPaymentForReview(
  registrationId: string,
  accessToken: string,
): Promise<Payment> {
  return apiFetch<Payment>(
    `/api/v1/payments/review/registrations/${encodeURIComponent(
      registrationId,
    )}`,
    { accessToken },
  )
}

/**
 * Подтверждает только ручный платёж MANUAL.
 */
export function confirmPayment(
  paymentId: string,
  accessToken: string,
): Promise<Payment> {
  return apiFetch<Payment>(
    `/api/v1/payments/${encodeURIComponent(paymentId)}/confirm`,
    {
      method: 'POST',
      accessToken,
    },
  )
}