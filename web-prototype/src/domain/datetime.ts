/**
 * Date/time formatting — spec §9.
 *
 * Two hard rules from the spec:
 *   - Never show "2026-08-21 00:00:00.517484".
 *   - Show "21 August 2026" and "00:00 – 02:00".
 * Seconds and nanoseconds are not part of trip-planning UX, so they are
 * absent from the storage format entirely rather than merely hidden.
 */

import type { IsoDate, IsoDateTime } from './types'

const MONTHS_LONG = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]
const MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const DAYS_LONG = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']

const pad = (n: number) => String(n).padStart(2, '0')

/**
 * Parse a zone-less ISO local date-time into a Date in the runtime's local
 * zone. We build it field-by-field rather than relying on Date parsing so the
 * result is identical across engines.
 */
export function parseLocal(iso: IsoDateTime | IsoDate): Date {
  const [datePart, timePart = '00:00'] = iso.split('T')
  const [y, m, d] = datePart.split('-').map(Number)
  const [hh, mm] = timePart.split(':').map(Number)
  return new Date(y, m - 1, d, hh || 0, mm || 0, 0, 0)
}

/** Serialise to "YYYY-MM-DDTHH:mm" — minute precision, no zone, no seconds. */
export function toIsoDateTime(d: Date): IsoDateTime {
  return `${toIsoDate(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** Serialise to "YYYY-MM-DD". */
export function toIsoDate(d: Date): IsoDate {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** The calendar date a date-time falls on — the grouping key for the Timeline. */
export function dateOf(iso: IsoDateTime): IsoDate {
  return iso.split('T')[0]
}

/** The clock time portion, "HH:mm". */
export function timeOf(iso: IsoDateTime): string {
  return iso.split('T')[1] ?? '00:00'
}

/** Recombine a date and a "HH:mm" time into a storable date-time. */
export function combine(date: IsoDate, time: string): IsoDateTime {
  return `${date}T${time}`
}

// ── Display ──────────────────────────────────────────────────────────────────

/** "21 August 2026" */
export function formatDateLong(iso: IsoDate | IsoDateTime): string {
  const d = parseLocal(iso)
  return `${pad(d.getDate())} ${MONTHS_LONG[d.getMonth()]} ${d.getFullYear()}`
}

/** "21 Aug 2026" */
export function formatDateMedium(iso: IsoDate | IsoDateTime): string {
  const d = parseLocal(iso)
  return `${pad(d.getDate())} ${MONTHS_SHORT[d.getMonth()]} ${d.getFullYear()}`
}

/** "21 August" — used where the year is already established by context. */
export function formatDayAndMonth(iso: IsoDate | IsoDateTime): string {
  const d = parseLocal(iso)
  return `${d.getDate()} ${MONTHS_LONG[d.getMonth()]}`
}

/** "FRIDAY, 21 AUGUST" — the Timeline date header (spec §20). */
export function formatDayHeader(iso: IsoDate | IsoDateTime): string {
  const d = parseLocal(iso)
  return `${DAYS_LONG[d.getDay()]}, ${d.getDate()} ${MONTHS_LONG[d.getMonth()]}`.toUpperCase()
}

/** "FRI" */
export function formatWeekdayShort(iso: IsoDate | IsoDateTime): string {
  return DAYS_LONG[parseLocal(iso).getDay()].slice(0, 3).toUpperCase()
}

/** "08:30" */
export function formatTime(iso: IsoDateTime): string {
  return timeOf(iso)
}

/** "08:30 – 10:15" (en dash, per spec) */
export function formatTimeRange(start: IsoDateTime, end: IsoDateTime): string {
  return `${formatTime(start)} – ${formatTime(end)}`
}

/** "08 Sep 2026 — 11 Sep 2026" (em dash, per spec §25) */
export function formatDateRange(start: IsoDate, end: IsoDate): string {
  return `${formatDateMedium(start)} — ${formatDateMedium(end)}`
}

/** "1h 45m", "45m", "2h" — for durations shown next to an event. */
export function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes}m`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return m === 0 ? `${h}h` : `${h}h ${m}m`
}

/**
 * Coarse relative phrasing for the HUD: "in 25 min", "in 3 h", "in 2 days".
 * Deliberately imprecise past an hour — the HUD should not read like a stopwatch.
 */
export function formatRelative(from: IsoDateTime, to: IsoDateTime): string {
  const mins = minutesBetween(from, to)
  if (mins <= 0) return 'now'
  if (mins < 60) return `in ${mins} min`
  if (mins < 60 * 24) {
    const h = Math.round(mins / 60)
    return `in ${h} h`
  }
  const days = Math.round(mins / (60 * 24))
  return days === 1 ? 'tomorrow' : `in ${days} days`
}

// ── Arithmetic ───────────────────────────────────────────────────────────────

export function minutesBetween(a: IsoDateTime, b: IsoDateTime): number {
  return Math.round((parseLocal(b).getTime() - parseLocal(a).getTime()) / 60000)
}

export function daysBetween(a: IsoDate | IsoDateTime, b: IsoDate | IsoDateTime): number {
  const da = parseLocal(dateOf(a as IsoDateTime))
  const db = parseLocal(dateOf(b as IsoDateTime))
  return Math.round((db.getTime() - da.getTime()) / 86400000)
}

export function addMinutes(iso: IsoDateTime, minutes: number): IsoDateTime {
  const d = parseLocal(iso)
  d.setMinutes(d.getMinutes() + minutes)
  return toIsoDateTime(d)
}

export function addDays(iso: IsoDate, days: number): IsoDate {
  const d = parseLocal(iso)
  d.setDate(d.getDate() + days)
  return toIsoDate(d)
}

/** Every date from `start` to `end` inclusive. */
export function eachDate(start: IsoDate, end: IsoDate): IsoDate[] {
  const out: IsoDate[] = []
  let cursor = start
  // Guard against an inverted range producing an unbounded loop.
  const span = daysBetween(start, end)
  for (let i = 0; i <= Math.max(0, span); i++) {
    out.push(cursor)
    cursor = addDays(cursor, 1)
  }
  return out
}
