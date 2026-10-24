import type { ReactNode } from 'react'
export function EmptyState({ title, children, action, headingLevel = 2 }: { title: string; children?: ReactNode; action: ReactNode; headingLevel?: 1 | 2 | 3 }) {
  const Heading = `h${headingLevel}` as const
  return <div className="fl-empty"><Heading>{title}</Heading>{children}{action}</div>
}
