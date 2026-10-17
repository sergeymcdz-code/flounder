import type { HTMLAttributes } from 'react'
export function ActionBar({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) { return <div {...props} className={`fl-actionbar ${className}`} /> }
