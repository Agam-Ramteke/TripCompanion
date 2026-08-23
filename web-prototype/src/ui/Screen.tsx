/**
 * Screen shell — bar, scrolling body, optional footer.
 *
 * Every screen uses this, so the back affordance, the title treatment and the
 * safe bottom padding are decided once (§22 applied to layout as well as state).
 */

import type { ReactNode } from 'react'
import { IconBack, IconClose } from './Icons'

interface ScreenProps {
  title?: string
  /** Shown when the screen is not the root of the stack. */
  onBack?: () => void
  backKind?: 'back' | 'close'
  actions?: ReactNode
  footer?: ReactNode
  bordered?: boolean
  flush?: boolean
  /** Body stops scrolling and becomes a flex column its child fills (the map). */
  fill?: boolean
  children: ReactNode
}

export function Screen({
  title,
  onBack,
  backKind = 'back',
  actions,
  footer,
  bordered = false,
  flush = false,
  fill = false,
  children,
}: ScreenProps) {
  const hasBar = Boolean(onBack || title || actions)
  const bodyClass = fill
    ? 'screen__body screen__body--fill'
    : `screen__body${flush ? ' screen__body--flush' : ''}`
  return (
    <div className="screen">
      {hasBar && (
        <header className={`screen__bar${bordered ? ' screen__bar--bordered' : ''}`}>
          {onBack && (
            <button
              type="button"
              className="icon-btn icon-btn--bare"
              onClick={onBack}
              aria-label={backKind === 'close' ? 'Close' : 'Back'}
            >
              {backKind === 'close' ? <IconClose /> : <IconBack />}
            </button>
          )}
          <span className="screen__bar-title">{title}</span>
          {actions}
        </header>
      )}
      <div className={bodyClass}>{children}</div>
      {footer && <footer className="screen__footer">{footer}</footer>}
    </div>
  )
}

export function Section({
  label,
  action,
  children,
}: {
  label?: string
  action?: ReactNode
  children: ReactNode
}) {
  return (
    <section className="section">
      {(label || action) && (
        <div className="section__head">
          {label && <h2 className="eyebrow">{label}</h2>}
          {action}
        </div>
      )}
      {children}
    </section>
  )
}

export function EmptyState({
  mark,
  headline,
  body,
  action,
}: {
  mark: ReactNode
  headline: string
  body: string
  action?: ReactNode
}) {
  return (
    <div className="empty">
      <div className="empty__mark">{mark}</div>
      <h2 className="title title--md">{headline}</h2>
      <p className="body-text body-text--dim">{body}</p>
      {action}
    </div>
  )
}
