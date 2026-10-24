import { useId, useState, type ComponentPropsWithRef, type ReactNode } from 'react'
import { resultStatusCode, resultStatusDictionary } from './results-ui'
import styles from './ResultStatus.module.css'

export function ResultStatus({ status, children, buttonProps }: {
  status: string
  children?: ReactNode
  buttonProps?: ComponentPropsWithRef<'button'> & { [key: `data-${string}`]: string | undefined }
}) {
  const [open, setOpen] = useState(false)
  const id = useId()
  const code = resultStatusCode(status)
  if (!code) return children ?? status
  const { hint } = resultStatusDictionary[code]
  const label = `${code} — ${hint}`
  const tooltip = open && <span className={styles.hint} role="tooltip" id={id}>{hint}</span>
  if (buttonProps) return <>
    <button {...buttonProps} title={hint} aria-label={label} aria-describedby={open ? id : undefined}
      onClick={event => { setOpen(value => !value); buttonProps.onClick?.(event) }}
      onBlur={event => { setOpen(false); buttonProps.onBlur?.(event) }}
      onKeyDown={event => { if (event.key === 'Escape') setOpen(false); buttonProps.onKeyDown?.(event) }}>
      <abbr className={styles.code} title={hint} aria-label={label}>{children ?? code}</abbr>
    </button>
    {tooltip}
  </>
  return <>
    <abbr className={styles.code} title={hint} aria-label={label}
      tabIndex={0}
      aria-describedby={open ? id : undefined}
      onClick={() => setOpen(value => !value)}
      onBlur={() => setOpen(false)}
      onKeyDown={event => {
        if (event.key === 'Escape') setOpen(false)
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          setOpen(value => !value)
        }
      }}>{children ?? code}</abbr>
    {tooltip}
  </>
}
