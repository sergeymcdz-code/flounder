import { useEffect, useId, useRef } from 'react'
import { NavLink, useLocation } from 'react-router'

export type TabItem = { to: string; label: string; count?: string | number; attention?: number; status?: string }

export function Tabs({ items, label = 'Разделы соревнования' }: { items: TabItem[]; label?: string }) {
  const ref = useRef<HTMLElement>(null)
  const id = useId()
  const location = useLocation()
  useEffect(() => {
    ref.current?.querySelector<HTMLElement>('[aria-current="page"]')
      ?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' })
    const node = ref.current
    if (!node) return
    const update = () => {
      const wrap = node.parentElement!
      wrap.dataset.overflowStart = String(node.scrollLeft > 1)
      wrap.dataset.overflowEnd = String(node.scrollLeft + node.clientWidth < node.scrollWidth - 1)
    }
    update()
    node.addEventListener('scroll', update, { passive: true })
    const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(update) : null
    observer?.observe(node)
    return () => { node.removeEventListener('scroll', update); observer?.disconnect() }
  }, [location.pathname, items])

  return <div className="fl-tabs-wrap">
    <nav ref={ref} className="fl-tabs fl-scroll-x" aria-label={label}>
      {items.map((item, index) => {
        const countId = `${id}-${index}-count`
        const attentionId = `${id}-${index}-attention`
        const description = [item.count !== undefined && countId, !!item.attention && attentionId].filter(Boolean).join(' ')
        return <NavLink key={item.to} to={item.to} end aria-label={item.label} aria-describedby={description || undefined}>
          {item.label}
          {item.count !== undefined && <small id={countId} className="fl-tab-count">{item.count}</small>}
          {!!item.attention && <small id={attentionId} className="fl-tab-count fl-tab-attention" aria-label={`${item.attention} требуют внимания`}>·{item.attention}</small>}
          {item.status && <small className="fl-tab-count">{item.status}</small>}
        </NavLink>
      })}
    </nav>
  </div>
}
