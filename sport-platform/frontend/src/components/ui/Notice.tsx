import type { ReactNode } from 'react'
export function Notice({ tone = 'neutral', children, className = '' }: { tone?: 'neutral' | 'info' | 'warning' | 'danger'; children: ReactNode; className?: string }) { return <div className={`fl-notice fl-notice-${tone} ${className}`} role={tone === 'danger' ? 'alert' : undefined}>{children}</div> }
