import { Children, isValidElement, type ReactNode } from 'react'
export function JudgeActionBar({ children }: { children: ReactNode }) {
 const actions = Children.toArray(children)
 const primary = (action: ReactNode) => isValidElement<{ className?: string }>(action) && action.props.className?.includes('fl-btn-primary')
 return <div className="fl-judge-actions fl-sticky-bottom"><div className="fl-judge-primary">{actions.filter(primary)}</div><div className="fl-judge-secondary">{actions.filter(action => !primary(action))}</div></div>
}
