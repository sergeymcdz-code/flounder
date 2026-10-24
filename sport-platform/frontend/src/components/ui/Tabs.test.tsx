import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { expect, it, vi } from 'vitest'
import { Tabs, type TabItem } from './Tabs'

it('scrolls on navigation but preserves page position and subscriptions during count refreshes', async () => {
  const scroll = vi.fn()
  const originalScroll = HTMLElement.prototype.scrollIntoView
  const observers: { observe: ReturnType<typeof vi.fn>; disconnect: ReturnType<typeof vi.fn> }[] = []
  class Observer {
    observe = vi.fn()
    disconnect = vi.fn()
    constructor() { observers.push(this) }
  }
  vi.stubGlobal('ResizeObserver', Observer)
  HTMLElement.prototype.scrollIntoView = scroll
  const first: TabItem[] = [{ to: '/entries', label: 'Заявки', count: 1 }, { to: '/program', label: 'Программа' }]
  const view = (items: TabItem[]) => <MemoryRouter initialEntries={['/entries']}><Tabs items={items} /></MemoryRouter>
  const { rerender, unmount } = render(view(first))
  try {
    expect(scroll).toHaveBeenCalledOnce()
    expect(observers).toHaveLength(1)
    expect(observers[0].observe).toHaveBeenCalledTimes(3)
    rerender(view(first.map(item => ({ ...item, count: 20 }))))
    expect(scroll).toHaveBeenCalledOnce()
    expect(observers).toHaveLength(1)
    rerender(view([...first, { to: '/results', label: 'Результаты' }]))
    expect(scroll).toHaveBeenCalledOnce()
    expect(observers).toHaveLength(2)
    expect(observers[0].disconnect).toHaveBeenCalledOnce()
    await userEvent.click(screen.getByRole('link', { name: 'Результаты' }))
    expect(scroll).toHaveBeenCalledTimes(2)
    expect(observers).toHaveLength(2)
  } finally {
    unmount()
    HTMLElement.prototype.scrollIntoView = originalScroll
    vi.unstubAllGlobals()
  }
})
