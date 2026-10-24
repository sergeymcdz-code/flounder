import { Notice } from './ui/Notice'
import { Button } from './ui/Button'
type ErrorStateProps = { title?: string; message: string; onRetry?: () => void }
export function ErrorState({ title = 'Не удалось загрузить данные', message, onRetry }: ErrorStateProps) {
  return <Notice tone="danger" className="fl-error-state"><h2>{title}</h2><p>{message}</p>{onRetry ? <Button onClick={onRetry}>Повторить</Button> : <p>Обновите страницу или вернитесь к предыдущему разделу.</p>}</Notice>
}
