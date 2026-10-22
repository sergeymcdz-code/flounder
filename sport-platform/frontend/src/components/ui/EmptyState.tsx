import type { ReactNode } from 'react'
export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action: ReactNode }) { return <div className="fl-empty"><p><strong>{title}</strong></p>{children}{action}</div> }
