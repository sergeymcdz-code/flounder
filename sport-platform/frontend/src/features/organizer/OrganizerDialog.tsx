import { useEffect, useRef, type ReactNode } from 'react'
import { useOrganizerEvent } from './organizer-event-context'
import { ErrorState } from '../../components/ErrorState'
import styles from './OrganizerEventPage.module.css'
export function OrganizerDialog({
  title,
  children,
  onClose,
  side = false,
  busy = false,
  open = true,
}: {
  title: string
  children: ReactNode
  onClose: () => void
  side?: boolean
  busy?: boolean
  open?: boolean
}) {
  const context = useOrganizerEvent()
  const { error, registerDialog } = context ?? {}
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    if (!open) return
    const previous = document.activeElement as HTMLElement | null
    const dialog = ref.current
    dialog?.showModal()
    const unregister = registerDialog?.()
    return () => {
      dialog?.close()
      unregister?.()
      previous?.focus()
    }
  }, [registerDialog, open])
  return (
    <dialog
      ref={ref}
      className={side ? styles.drawer : styles.dialog}
      onCancel={(e) => {
        e.preventDefault()
        if (!busy) onClose()
      }}
      aria-label={title}
    >
      <header className={styles.toolbar}>
        <h2>{title}</h2>
        <button className="fl-btn fl-btn-secondary" type="button" disabled={busy} title={busy ? 'Дождитесь завершения текущего действия' : undefined} onClick={onClose} aria-label="Закрыть">
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
        <button title={busy ? 'Дождитесь завершения текущего действия' : undefined} className="fl-btn fl-btn-secondary" disabled={busy} onClick={onClose}>
          Отмена
        </button>
        <button title={busy ? 'Дождитесь завершения текущего действия' : undefined} className="fl-btn fl-btn-primary" disabled={busy} onClick={onConfirm}>
          Подтвердить
        </button>
      </div>
    </OrganizerDialog>
  )
}
