import type { ButtonHTMLAttributes, AnchorHTMLAttributes, ReactNode } from 'react'
import { Link, type LinkProps } from 'react-router'
type Common = { variant?: 'primary' | 'secondary' | 'ghost' | 'danger'; size?: 'sm' | 'md' | 'lg'; loading?: boolean; children: ReactNode }
type Props = Common & (({ as?: 'button' } & ButtonHTMLAttributes<HTMLButtonElement>) | ({ as: 'a' } & AnchorHTMLAttributes<HTMLAnchorElement>) | ({ as: typeof Link } & LinkProps))
export function Button({ variant = 'secondary', size = 'md', loading = false, children, className = '', ...props }: Props) {
  const classes = `fl-btn fl-btn-${variant} fl-btn-${size} ${className}`
  const content = <><span className="fl-btn-label" style={loading ? { visibility: 'hidden' } : undefined}>{children}</span>{loading && <span className="fl-btn-progress" aria-hidden="true">Сохраняем…</span>}</>
  if (props.as === 'a') { const { as: _, ...rest } = props; void _; return <a {...rest} aria-disabled={loading || undefined} onClick={event => { if (loading) event.preventDefault(); else rest.onClick?.(event) }} className={classes} aria-busy={loading || undefined} aria-label={loading && typeof children === 'string' ? children : props['aria-label']}>{content}</a> }
  if ('to' in props) { const { as: _, ...rest } = props; void _; return <Link {...rest} aria-disabled={loading || undefined} onClick={event => { if (loading) event.preventDefault(); else rest.onClick?.(event) }} className={classes} aria-busy={loading || undefined} aria-label={loading && typeof children === 'string' ? children : props['aria-label']}>{content}</Link> }
  const { as: _, ...rest } = props; void _
  return <button type="button" {...rest} className={classes} aria-busy={loading || undefined} aria-label={loading && typeof children === 'string' ? children : props['aria-label']} disabled={loading || rest.disabled}>{content}</button>
}
