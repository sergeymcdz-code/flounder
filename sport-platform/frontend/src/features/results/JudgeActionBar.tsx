import { Children, isValidElement, useEffect, useLayoutEffect, useRef, type ReactNode } from 'react'
import styles from './JudgeActionBar.module.css'

export function JudgeActionBar({ children, mobile = false, status, feedback }: { children: ReactNode; mobile?: boolean; status?: string; feedback?: ReactNode }) {
  const bar = useRef<HTMLDivElement>(null)
  const menu = useRef<HTMLDetailsElement>(null)
  const actions = Children.toArray(children)
  const primary = (action: ReactNode) => isValidElement<{ className?: string }>(action) && action.props.className?.includes('fl-btn-primary')

  useLayoutEffect(() => {
    const element = bar.current!
    const page = element.closest('section')
    const measure = () => page?.style.setProperty('--judge-actions-height', `${element.getBoundingClientRect().height}px`)
    measure()
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure)
    observer?.observe(element)
    window.addEventListener('resize', measure)
    return () => {
      observer?.disconnect()
      window.removeEventListener('resize', measure)
      page?.style.removeProperty('--judge-actions-height')
    }
  }, [mobile])

  useEffect(() => {
    const close = (event: PointerEvent | KeyboardEvent) => {
      if (!menu.current?.open) return
      if (event instanceof KeyboardEvent) {
        if (event.key !== 'Escape') return
        menu.current.open = false
        menu.current.querySelector('summary')?.focus()
      } else if (!menu.current.contains(event.target as Node)) menu.current.open = false
    }
    document.addEventListener('pointerdown', close)
    document.addEventListener('keydown', close)
    return () => {
      document.removeEventListener('pointerdown', close)
      document.removeEventListener('keydown', close)
    }
  }, [])

  const secondary = <div className={styles.secondary}>{actions.filter(action => !primary(action))}</div>
  return <div ref={bar} className={`${styles.bar} fl-judge-actions`} role="group" aria-label="Действия заплыва">
    {feedback && <div className={styles.feedback}>{feedback}</div>}
    {mobile && <details ref={menu} className={styles.more} onToggle={() => window.dispatchEvent(new Event('resize'))}>
      <summary className="fl-btn fl-btn-ghost">Другие действия ⋯</summary>{secondary}
    </details>}
    <div className={styles.primary}>{actions.filter(primary)}</div>
    {!mobile && secondary}
    <p className={styles.status} role="status" aria-live="polite" aria-atomic="true">{status}</p>
  </div>
}
