import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../api/http'
import {
  type RegistrationReviewDecision,
  reviewRegistration,
} from '../../api/registrations.api'
import { useAuth } from '../../app/providers/AuthProvider'

type DecisionOption = {
  value: RegistrationReviewDecision
  label: string
  hint: string
}

const DECISIONS: DecisionOption[] = [
  {
    value: 'CONFIRMED',
    label: 'Подтвердить',
    hint: 'Участник допущен к дальнейшему payment flow.',
  },
  {
    value: 'NEEDS_CORRECTION',
    label: 'Запросить уточнение',
    hint: 'Участник увидит комментарий и сможет исправить данные.',
  },
  {
    value: 'REJECTED',
    label: 'Отклонить',
    hint: 'Используйте, если заявка не соответствует условиям соревнования.',
  },
]

function getErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) {
    return 'Не удалось обработать заявку. Попробуйте ещё раз.'
  }

  if (error.status === 403) {
    return 'У вашей учётной записи нет прав на review заявок.'
  }

  if (error.status === 404) {
    return 'Заявка с таким ID не найдена.'
  }

  return error.message
}

export function RegistrationReviewPage() {
  const { session } = useAuth()

  const [registrationId, setRegistrationId] = useState('')
  const [decision, setDecision] =
    useState<RegistrationReviewDecision>('CONFIRMED')
  const [reviewNote, setReviewNote] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!session) {
      return
    }

    const normalizedRegistrationId = registrationId.trim()
    const normalizedReviewNote = reviewNote.trim()

    if (!normalizedRegistrationId) {
      setErrorMessage('Укажите ID заявки.')
      return
    }

    if (!normalizedReviewNote) {
      setErrorMessage('Добавьте комментарий для участника.')
      return
    }

    setErrorMessage(null)
    setSuccessMessage(null)
    setIsSubmitting(true)

    try {
      const reviewedRegistration = await reviewRegistration(
        normalizedRegistrationId,
        {
          decision,
          reviewNote: normalizedReviewNote,
        },
        session.accessToken,
      )

      const label =
        DECISIONS.find((item) => item.value === reviewedRegistration.status)
          ?.label ??
        DECISIONS.find((item) => item.value === decision)?.label ??
        'Обработана'

      setSuccessMessage(`Заявка успешно обработана: ${label}.`)
      setRegistrationId(reviewedRegistration.id)
    } catch (error) {
      setErrorMessage(getErrorMessage(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className="admin-section">
      <p className="eyebrow">Панель организатора</p>

      <h1>Рассмотрение заявки</h1>

      <p className="page-description">
        Укажите ID заявки, выберите решение и оставьте понятный комментарий
        участнику.
      </p>

      <div className="admin-warning">
        Не меняйте подтверждённые заявки в production без необходимости.
        Для тестов используйте отдельную заявку в статусе «На рассмотрении».
      </div>

      <form className="admin-review-form" onSubmit={handleSubmit}>
        <label>
          <span>ID заявки</span>
          <input
            disabled={isSubmitting}
            onChange={(event) => setRegistrationId(event.target.value)}
            placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            required
            value={registrationId}
          />
        </label>

        <fieldset disabled={isSubmitting}>
          <legend>Решение</legend>

          <div className="review-decisions">
            {DECISIONS.map((option) => (
              <label
                className="decision-option"
                key={option.value}
              >
                <input
                  checked={decision === option.value}
                  name="registration-review-decision"
                  onChange={() => setDecision(option.value)}
                  type="radio"
                  value={option.value}
                />

                <span>
                  <strong>{option.label}</strong>
                  <small>{option.hint}</small>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <label>
          <span>Комментарий для участника</span>
          <textarea
            disabled={isSubmitting}
            maxLength={2000}
            onChange={(event) => setReviewNote(event.target.value)}
            placeholder="Например: Заявка подтверждена. Ожидайте информацию об оплате."
            required
            rows={5}
            value={reviewNote}
          />
        </label>

        {errorMessage ? (
          <p className="form-error" role="alert">
            {errorMessage}
          </p>
        ) : null}

        {successMessage ? (
          <p className="form-success" role="status">
            {successMessage}
          </p>
        ) : null}

        <button
          className="button button-primary"
          disabled={isSubmitting}
          type="submit"
        >
          {isSubmitting ? 'Сохраняем…' : 'Сохранить решение'}
        </button>
      </form>
    </section>
  )
}