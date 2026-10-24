import { useEffect, useLayoutEffect, useId, useRef, useState, type MouseEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router'
import { Button } from './Button'

export type OverflowMenuItem = { label: string; target?: '_blank'; to?: string; href?: string; onSelect?: () => void; disabled?: boolean; disabledReason?: string; active?: boolean }
export function OverflowMenu({ items, label = 'Другие действия', trigger = '⋯' }: { items: readonly OverflowMenuItem[]; label?: string; trigger?: ReactNode }) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)
  const panel = useRef<HTMLDivElement>(null)
  const [anchor, setAnchor] = useState({ x: 0, y: 0 })
  const id = useId()
  function close() { setOpen(false); button.current?.focus() }
  // A body portal prevents scroll containers from clipping row actions.
  useLayoutEffect(() => {
    if (!open) return
    const update = () => {
      const triggerBox = button.current?.getBoundingClientRect()
      const menuBox = panel.current?.getBoundingClientRect()
      if (!triggerBox || !menuBox) return
      const gap = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--space-4')) || 0
      const next = window.innerWidth < 768 ? { x: 0, y: 0 } : {
        x: Math.min(window.innerWidth - gap, Math.max(menuBox.width + gap, triggerBox.right)),
        y: triggerBox.bottom + menuBox.height <= window.innerHeight - gap ? triggerBox.bottom : Math.max(gap, triggerBox.top - menuBox.height),
      }
      setAnchor(previous => previous.x === next.x && previous.y === next.y ? previous : next)
    }
    update()
    window.addEventListener('resize', update)
    window.addEventListener('scroll', update, true)
    return () => { window.removeEventListener('resize', update); window.removeEventListener('scroll', update, true) }
  }, [open])
  useEffect(() => {
    if (!open) return
    panel.current?.querySelector<HTMLElement>('[role="menuitem"]:not([aria-disabled="true"])')?.focus({ preventScroll: true })
    function outside(event: PointerEvent) { if (!root.current?.contains(event.target as Node) && !panel.current?.contains(event.target as Node)) close() }
    document.addEventListener('pointerdown', outside)
    return () => document.removeEventListener('pointerdown', outside)
  }, [open])
  return <div ref={root} className="fl-overflow-menu" onKeyDown={event => {
    if (event.key === 'Escape' && open) { event.preventDefault(); event.stopPropagation(); close() }
    if (event.key === 'Tab' && open) { close(); return }
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
    event.preventDefault()
    if (!open) { setOpen(true); return }
    const options = Array.from(panel.current?.querySelectorAll<HTMLElement>('[role="menuitem"]:not([aria-disabled="true"])') ?? [])
    const current = options.indexOf(document.activeElement as HTMLElement)
    const index = event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1 : (current + (event.key === 'ArrowUp' ? -1 : 1) + options.length) % options.length
    options[index]?.focus()
  }}>
    <Button ref={button} variant="ghost" aria-label={label} aria-haspopup="menu" aria-expanded={open} aria-controls={open ? id : undefined} onClick={() => open ? close() : setOpen(true)}>{trigger}</Button>
    {open && createPortal(<div className="fl-overflow-menu" style={{ position: 'fixed', left: anchor.x, top: anchor.y, zIndex: 'var(--z-drawer)' }}><div ref={panel} id={id} role="menu" aria-label={label}>
      {items.map((item, index) => {
        const props = { role: 'menuitem', tabIndex: -1, 'aria-disabled': item.disabled || undefined, 'aria-current': item.active ? 'page' as const : undefined, title: item.disabledReason, className: 'fl-btn fl-btn-ghost', onClick: (event: MouseEvent) => { if (item.disabled) { event.preventDefault(); return }; item.onSelect?.(); close() } }
        return item.to ? <Link key={index} {...props} to={item.to} target={item.target} rel={item.target ? 'noreferrer' : undefined}>{item.label}</Link> : item.href ? <a key={index} {...props} href={item.href} target={item.target} rel={item.target ? 'noreferrer' : undefined}>{item.label}</a> : <button key={index} {...props} type="button">{item.label}</button>
      })}
    </div></div>, document.body)}
  </div>
}
