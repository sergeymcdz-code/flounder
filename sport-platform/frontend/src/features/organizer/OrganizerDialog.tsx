import { useEffect, useRef, type ReactNode } from 'react'
import { useOrganizerEvent } from './organizer-event-context'
import { ErrorState } from '../../components/ErrorState'
import styles from './OrganizerEventPage.module.css'
export function OrganizerDialog({
  title,
  children,
  onClose,
  side = false,
}: {
  title: string
  children: ReactNode
  onClose: () => void
  side?: boolean
}) {
  const { error, registerDialog } = useOrganizerEvent()
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    dialog?.showModal()
    const unregister = registerDialog()
    return () => {
      dialog?.close()
      unregister()
    }
  }, [registerDialog])
  return (
    <dialog
      ref={ref}
      className={side ? styles.drawer : styles.dialog}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
      aria-label={title}
    >
      <header className={styles.toolbar}>
        <h2>{title}</h2>
        <button type="button" onClick={onClose} aria-label="Закрыть">
          ×
        </button>
      </header>
      {error && <ErrorState title="Ошибка" message={error} />}
      {children}
    </dialog>
  )
}
export function OrganizerConfirm({
  title,
  message,
  busy,
  onConfirm,
  onClose,
}: {
  title: string
  message: string
  busy: boolean
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <OrganizerDialog title={title} onClose={onClose}>
      <p>{message}</p>
      <div className={styles.actions}>
        <button disabled={busy} onClick={onClose}>
          Отмена
        </button>
        <button className={styles.primary} disabled={busy} onClick={onConfirm}>
          Подтвердить
        </button>
      </div>
    </OrganizerDialog>
  )
}
