import { Children, isValidElement, useLayoutEffect, useRef, type ReactNode } from 'react'
import styles from './JudgeActionBar.module.css'

export function JudgeActionBar({ children, mobile = false }: { children: ReactNode; mobile?: boolean }) {
  const bar = useRef<HTMLDivElement>(null)
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

  return <div ref={bar} className={`${styles.bar} fl-judge-actions`} role="group" aria-label="Действия заплыва">
    <div className={styles.primary}>{actions.filter(primary)}</div>
    {mobile ? <details className={styles.more}><summary>Другие действия ⋯</summary>
      <div className={styles.secondary}>{actions.filter(action => !primary(action))}</div>
    </details> : <div className={styles.secondary}>{actions.filter(action => !primary(action))}</div>}
  </div>
}
