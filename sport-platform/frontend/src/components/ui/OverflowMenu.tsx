import { useEffect, useId, useRef, useState, type MouseEvent, type ReactNode } from 'react'
import { Link } from 'react-router'
import { Button } from './Button'

export type OverflowMenuItem = { label: string; to?: string; href?: string; onSelect?: () => void; disabled?: boolean; disabledReason?: string; active?: boolean }
export function OverflowMenu({ items, label = 'Другие действия', trigger = '⋯' }: { items: readonly OverflowMenuItem[]; label?: string; trigger?: ReactNode }) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)
  const id = useId()
  function close() { setOpen(false); button.current?.focus() }
  useEffect(() => {
    if (!open) return
    root.current?.querySelector<HTMLElement>('[role="menuitem"]:not([aria-disabled="true"])')?.focus()
    function outside(event: PointerEvent) { if (!root.current?.contains(event.target as Node)) close() }
    document.addEventListener('pointerdown', outside)
    return () => document.removeEventListener('pointerdown', outside)
  }, [open])
  return <div ref={root} className="fl-overflow-menu" onKeyDown={event => {
    if (event.key === 'Escape' && open) { event.preventDefault(); event.stopPropagation(); close() }
    if (event.key === 'Tab' && open) { setOpen(false); return }
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
    event.preventDefault()
    if (!open) { setOpen(true); return }
    const options = Array.from(root.current?.querySelectorAll<HTMLElement>('[role="menuitem"]:not([aria-disabled="true"])') ?? [])
    const current = options.indexOf(document.activeElement as HTMLElement)
    const index = event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1 : (current + (event.key === 'ArrowUp' ? -1 : 1) + options.length) % options.length
    options[index]?.focus()
  }}>
    <Button ref={button} variant="ghost" aria-label={label} aria-haspopup="menu" aria-expanded={open} aria-controls={open ? id : undefined} onClick={() => open ? close() : setOpen(true)}>{trigger}</Button>
    {open && <div id={id} role="menu" aria-label={label}>
      {items.map((item, index) => {
        const props = { role: 'menuitem', tabIndex: -1, 'aria-disabled': item.disabled || undefined, 'aria-current': item.active ? 'page' as const : undefined, title: item.disabledReason, className: 'fl-btn fl-btn-ghost', onClick: (event: MouseEvent) => { if (item.disabled) { event.preventDefault(); return }; item.onSelect?.(); close() } }
        return item.to ? <Link key={index} {...props} to={item.to}>{item.label}</Link> : item.href ? <a key={index} {...props} href={item.href}>{item.label}</a> : <button key={index} {...props} type="button">{item.label}</button>
      })}
    </div>}
  </div>
}
