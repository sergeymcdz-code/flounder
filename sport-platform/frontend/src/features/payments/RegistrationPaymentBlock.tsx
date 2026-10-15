import type {
  PaymentDetails,
  PaymentSettings,
} from '../../api/registration-payments.api'
import { money, paymentLabels } from '../../api/registration-payments.api'
export function RegistrationPaymentBlock({
  registration,
  settings,
}: {
  registration: PaymentDetails
  settings?: PaymentSettings
}) {
  const pending =
    registration.paymentStatus === 'PENDING' &&
    (registration.feeAmount ?? 0) > 0 &&
    settings?.enabled !== false
  return (
    <div style={{ overflowWrap: 'anywhere', minWidth: 0 }}>
      <p>
        {settings?.enabled === false && registration.paymentStatus === 'PENDING'
          ? 'Оплата не требуется'
          : paymentLabels[registration.paymentStatus ?? 'NOT_REQUIRED']}
      </p>
      {pending && (
        <p>
          <strong>
            К оплате:{' '}
            {money(registration.feeAmount, registration.feeCurrency ?? 'RUB')}
          </strong>
          {registration.dueAt &&
            ` до ${new Date(registration.dueAt).toLocaleDateString('ru-RU')}`}
        </p>
      )}
      {settings?.enabled && settings.instructions && pending && (
        <details>
          <summary>Реквизиты</summary>
          <p style={{ whiteSpace: 'pre-wrap' }}>{settings.instructions}</p>
        </details>
      )}
    </div>
  )
}
