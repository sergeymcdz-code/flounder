import { useEffect, useId, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { OverflowMenu, type OverflowMenuItem } from '../../components/ui/OverflowMenu'
import styles from './Entries.module.css'

// Preserve the UX-19 portal and focus lifecycle outside the scrolling table.
type EntryRowMenuProps = { children?: ReactNode; label?: string; triggerName?: string; items?: readonly OverflowMenuItem[] }
export function EntryRowMenu({ items, ...props }: EntryRowMenuProps) {
  return items ? <OverflowMenu items={items} label={props.triggerName ?? props.label} /> : <EntryActions {...props} />
}
// Keep the QA06-10 / UX-19 row-region contract until the coordinated D04-3 migration.
function EntryActions({ children, label = 'Действия заявки', triggerName = 'Ещё ▾' }: Omit<EntryRowMenuProps, 'items'>) {
  const [open, setOpen] = useState(false)
  const trigger = useRef<HTMLButtonElement>(null)
  const panel = useRef<HTMLDivElement>(null)
  const id = useId()
  useLayoutEffect(() => {
    if (!open || !trigger.current || !panel.current) return
    const position = () => {
      if (!trigger.current || !panel.current) return
      const rect = trigger.current.getBoundingClientRect()
      const menu = panel.current
      const gap = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--space-2')) || 0
      menu.style.left = `${Math.max(gap, Math.min(rect.right - menu.offsetWidth, window.innerWidth - menu.offsetWidth - gap)) + window.scrollX}px`
      const top = rect.bottom + menu.offsetHeight + gap <= window.innerHeight ? rect.bottom + gap : Math.max(gap, rect.top - menu.offsetHeight - gap)
      menu.style.top = `${top + window.scrollY}px`
    }
    position()
    panel.current.querySelector<HTMLButtonElement>('button:not(:disabled)')?.focus({ preventScroll: true })
    window.addEventListener('resize', position)
    window.addEventListener('scroll', position, true)
    return () => {
      window.removeEventListener('resize', position)
      window.removeEventListener('scroll', position, true)
    }
  }, [open])
  useEffect(() => {
    if (!open) return
    const outside = (event: PointerEvent) => {
      if (!panel.current?.contains(event.target as Node) && !trigger.current?.contains(event.target as Node))  { setOpen(false); trigger.current?.focus({ preventScroll: true }) }
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { setOpen(false); trigger.current?.focus({ preventScroll: true }) }
    }
    document.addEventListener('pointerdown', outside)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('pointerdown', outside)
      document.removeEventListener('keydown', escape)
    }
  }, [open])
  return <>
    <button className="fl-btn fl-btn-ghost" type="button" ref={trigger} aria-label={triggerName} title={label} aria-haspopup="true" aria-expanded={open} aria-controls={open ? id : undefined} onClick={() => setOpen(value => !value)}>⋯</button>
    {open && createPortal(<div ref={panel} id={id} className={styles.entryActions} role="region" aria-label={label} onClick={event => { if (!(event.target as HTMLElement).closest('button:not(:disabled), a')) return; setOpen(false); trigger.current?.focus({ preventScroll: true }) }}>{children}</div>, document.body)}
  </>
}
