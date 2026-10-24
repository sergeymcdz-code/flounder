import type { ButtonHTMLAttributes, AnchorHTMLAttributes, ReactNode } from 'react'
import { Link, type LinkProps } from 'react-router'

type Common = { variant?: 'primary' | 'secondary' | 'ghost' | 'danger'; size?: 'sm' | 'md' | 'lg'; loading?: boolean; children: ReactNode }
type Props = Common & (({ as?: 'button' } & ButtonHTMLAttributes<HTMLButtonElement>) | ({ as: 'a' } & AnchorHTMLAttributes<HTMLAnchorElement>) | ({ as: typeof Link } & LinkProps))

export function Button({ variant = 'secondary', size = 'md', loading = false, children, className = '', ...props }: Props) {
  const accessibleName = props['aria-label'] ?? (loading && typeof children === 'string' ? children : undefined)
  const classes = `fl-btn fl-btn-${variant} fl-btn-${size} ${className}`
  // Preserve the accessible name for rich labels as well as plain text while loading.
  const content = <><span className="fl-btn-label" aria-hidden={loading || undefined} style={loading ? { visibility: 'hidden' } : undefined}>{children}</span>{loading && <>{typeof children !== 'string' && <span className="fl-sr-only">{children}</span>}<span className="fl-btn-progress" aria-hidden="true">Сохраняем…</span></>}</>
  if (props.as === 'a') {
    const { as: _, ...rest } = props; void _
    const disabled = loading || rest['aria-disabled'] === true || rest['aria-disabled'] === 'true'
    return <a {...rest} aria-disabled={disabled || undefined} tabIndex={disabled ? -1 : rest.tabIndex} onClick={event => { if (disabled) event.preventDefault(); else rest.onClick?.(event) }} onAuxClick={event => { if (disabled) event.preventDefault(); else rest.onAuxClick?.(event) }} className={classes} aria-busy={loading || undefined} aria-label={accessibleName}>{content}</a>
  }
  if ('to' in props) {
    const { as: _, ...rest } = props; void _
    const disabled = loading || rest['aria-disabled'] === true || rest['aria-disabled'] === 'true'
    return <Link {...rest} aria-disabled={disabled || undefined} tabIndex={disabled ? -1 : rest.tabIndex} onClick={event => { if (disabled) event.preventDefault(); else rest.onClick?.(event) }} onAuxClick={event => { if (disabled) event.preventDefault(); else rest.onAuxClick?.(event) }} className={classes} aria-busy={loading || undefined} aria-label={accessibleName}>{content}</Link>
  }
  const { as: _, ...rest } = props; void _
  return <button type="button" {...rest} className={classes} aria-busy={loading || undefined} aria-label={accessibleName} disabled={loading || rest.disabled}>{content}</button>
}
