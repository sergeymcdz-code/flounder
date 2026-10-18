import type { ReactNode } from 'react'
export function KeyValue({ label, children }: { label: string; children: ReactNode }) { return <div className="fl-key-value"><dt>{label}</dt><dd>{children}</dd></div> }
