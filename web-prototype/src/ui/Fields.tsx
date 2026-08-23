/**
 * Form controls — spec §9, §14.
 *
 * Date and time are always separate controls: one date, one start time, one end
 * time. `step={60}` is not cosmetic — it is what keeps a seconds spinner off the
 * time picker, so the format the spec forbids can't be entered in the first place.
 */

import type { ReactNode } from 'react'

interface FieldShellProps {
  label: string
  hint?: string
  error?: string | null
  children: ReactNode
}

function FieldShell({ label, hint, error, children }: FieldShellProps) {
  return (
    <label className="field">
      <span className="field__label">{label}</span>
      {children}
      {error ? (
        <span className="field__error">{error}</span>
      ) : hint ? (
        <span className="field__hint">{hint}</span>
      ) : null}
    </label>
  )
}

export function TextField({
  label,
  value,
  onChange,
  placeholder,
  hint,
  error,
  autoFocus,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  placeholder?: string
  hint?: string
  error?: string | null
  autoFocus?: boolean
}) {
  return (
    <FieldShell label={label} hint={hint} error={error}>
      <input
        className="field__control"
        type="text"
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        autoFocus={autoFocus}
      />
    </FieldShell>
  )
}

export function TextArea({
  label,
  value,
  onChange,
  placeholder,
  hint,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  placeholder?: string
  hint?: string
}) {
  return (
    <FieldShell label={label} hint={hint}>
      <textarea
        className="field__control field__control--area"
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
      />
    </FieldShell>
  )
}

/** One date. Never a date-time. */
export function DateField({
  label,
  value,
  onChange,
  min,
  max,
  hint,
  error,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  min?: string
  max?: string
  hint?: string
  error?: string | null
}) {
  return (
    <FieldShell label={label} hint={hint} error={error}>
      <input
        className="field__control field__control--clock"
        type="date"
        value={value}
        min={min}
        max={max}
        onChange={(e) => onChange(e.target.value)}
      />
    </FieldShell>
  )
}

/** "HH:mm" only — minute precision, enforced by step. */
export function TimeField({
  label,
  value,
  onChange,
  error,
  hint,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  error?: string | null
  hint?: string
}) {
  return (
    <FieldShell label={label} hint={hint} error={error}>
      <input
        className="field__control field__control--clock"
        type="time"
        step={60}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </FieldShell>
  )
}

export function Segmented<T extends string>({
  label,
  options,
  value,
  onChange,
  render,
}: {
  label: string
  options: readonly T[]
  value: T
  onChange: (value: T) => void
  render?: (value: T) => string
}) {
  return (
    <div className="field">
      <span className="field__label">{label}</span>
      <div className="segmented" role="group" aria-label={label}>
        {options.map((option) => (
          <button
            key={option}
            type="button"
            className="segmented__opt"
            aria-pressed={option === value}
            onClick={() => onChange(option)}
          >
            {render ? render(option) : option}
          </button>
        ))}
      </div>
    </div>
  )
}
