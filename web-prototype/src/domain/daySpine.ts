/**
 * Day Spine — the geometry behind the Home HUD's graphical progress element.
 *
 * Spec §19 asks for "graphical progress" and suggests a ring. A ring only
 * encodes *fraction of events completed*, which is a weak signal on a trip.
 * The thing a traveller actually needs at a glance is the shape of the day:
 * how long each stop runs, and where the empty space is. So the spine maps one
 * calendar day onto a time-proportional scale — blocks sized by duration, gaps
 * left visibly empty, and a marker at the true position of "now".
 *
 * Pure geometry. Both themes render the same numbers very differently.
 */

import { minutesBetween, parseLocal } from './datetime'
import type { Event, IsoDate, IsoDateTime } from './types'

/** Gaps shorter than this are not worth calling out as free time. */
export const MIN_NOTABLE_GAP_MINUTES = 45
/** A day always spans at least this much, so a single short event isn't full-height. */
const MIN_WINDOW_MINUTES = 6 * 60

export interface SpineBlock {
  event: Event
  /** Position of the block's start, 0–100, measured down the spine. */
  startPct: number
  /** Block extent as a percentage of the window. Never below a legible floor. */
  sizePct: number
  durationMinutes: number
  /** Horizontal lane for overlapping events; 0 is the primary lane. */
  lane: number
}

export interface SpineGap {
  startPct: number
  sizePct: number
  minutes: number
  /** The clock time the gap opens up, "HH:mm". */
  fromTime: string
}

export interface SpineTick {
  /** Whole hour, 0–23. */
  hour: number
  pct: number
  /** Every third hour is emphasised, so the scale reads without labelling all of it. */
  major: boolean
}

export interface DaySpine {
  date: IsoDate
  blocks: SpineBlock[]
  gaps: SpineGap[]
  ticks: SpineTick[]
  /** Position of now, or null when the day being shown is not today. */
  nowPct: number | null
  /** Highest lane index in use; 0 means nothing overlaps. */
  laneCount: number
  windowStartMinutes: number
  windowEndMinutes: number
}

const minutesFromMidnight = (iso: IsoDateTime): number => {
  const d = parseLocal(iso)
  return d.getHours() * 60 + d.getMinutes()
}

const floorToHour = (m: number) => Math.floor(m / 60) * 60
const ceilToHour = (m: number) => Math.ceil(m / 60) * 60

/**
 * Build the spine for one date.
 *
 * `events` should already be the events for that date. `now` is used only to
 * place the marker and to keep the window open far enough to include it.
 */
export function buildDaySpine(date: IsoDate, events: readonly Event[], now: IsoDateTime): DaySpine {
  const onDate = events.filter((e) => e.startTime.startsWith(date))
  const isToday = now.startsWith(date)
  const nowMinutes = isToday ? minutesFromMidnight(now) : null

  // ── Window ────────────────────────────────────────────────────────────────
  // Span the day's real extent, padded to whole hours, widened to include now.
  let lo: number
  let hi: number
  if (onDate.length > 0) {
    lo = floorToHour(Math.min(...onDate.map((e) => minutesFromMidnight(e.startTime))))
    hi = ceilToHour(
      Math.max(
        ...onDate.map((e) => {
          const end = minutesFromMidnight(e.endTime)
          // An event ending past midnight reads as "to end of day" on this date.
          return end <= minutesFromMidnight(e.startTime) ? 24 * 60 : end
        }),
      ),
    )
  } else {
    // Empty day: show a plausible waking window so the scale still means something.
    lo = 8 * 60
    hi = 20 * 60
  }

  if (nowMinutes !== null) {
    lo = Math.min(lo, floorToHour(nowMinutes))
    hi = Math.max(hi, ceilToHour(nowMinutes + 1))
  }

  if (hi - lo < MIN_WINDOW_MINUTES) {
    const deficit = MIN_WINDOW_MINUTES - (hi - lo)
    lo = Math.max(0, lo - floorToHour(deficit / 2))
    hi = Math.min(24 * 60, lo + MIN_WINDOW_MINUTES)
    if (hi - lo < MIN_WINDOW_MINUTES) lo = Math.max(0, hi - MIN_WINDOW_MINUTES)
  }

  const window = Math.max(1, hi - lo)
  const pctOf = (m: number) => ((m - lo) / window) * 100

  // ── Blocks, with lanes for overlaps ───────────────────────────────────────
  // Greedy lane packing: an event takes the first lane free at its start time.
  const laneEnds: number[] = []
  const blocks: SpineBlock[] = onDate.map((event) => {
    const start = minutesFromMidnight(event.startTime)
    const rawEnd = minutesFromMidnight(event.endTime)
    const end = rawEnd <= start ? 24 * 60 : rawEnd
    const durationMinutes = Math.max(0, minutesBetween(event.startTime, event.endTime))

    let lane = laneEnds.findIndex((laneEnd) => laneEnd <= start)
    if (lane === -1) {
      lane = laneEnds.length
      laneEnds.push(end)
    } else {
      laneEnds[lane] = end
    }

    return {
      event,
      startPct: pctOf(start),
      // Floor the visual size so a 15-minute stop is still tappable.
      sizePct: Math.max(2.5, ((end - start) / window) * 100),
      durationMinutes,
      lane,
    }
  })

  // ── Gaps ──────────────────────────────────────────────────────────────────
  // Only lane-0 events define free time; an overlapping event fills the gap.
  const gaps: SpineGap[] = []
  const primary = blocks
    .filter((b) => b.lane === 0)
    .sort((a, b) => a.startPct - b.startPct)

  for (let i = 0; i < primary.length - 1; i++) {
    const current = primary[i]
    const next = primary[i + 1]
    const gapMinutes = minutesBetween(current.event.endTime, next.event.startTime)
    if (gapMinutes >= MIN_NOTABLE_GAP_MINUTES) {
      const from = minutesFromMidnight(current.event.endTime)
      gaps.push({
        startPct: pctOf(from),
        sizePct: (gapMinutes / window) * 100,
        minutes: gapMinutes,
        fromTime: current.event.endTime.split('T')[1],
      })
    }
  }

  // ── Hour ticks ────────────────────────────────────────────────────────────
  const ticks: SpineTick[] = []
  for (let m = ceilToHour(lo); m <= hi; m += 60) {
    const hour = Math.floor(m / 60) % 24
    ticks.push({ hour, pct: pctOf(m), major: hour % 3 === 0 })
  }

  return {
    date,
    blocks,
    gaps,
    ticks,
    nowPct: nowMinutes !== null && nowMinutes >= lo && nowMinutes <= hi ? pctOf(nowMinutes) : null,
    laneCount: Math.max(1, laneEnds.length),
    windowStartMinutes: lo,
    windowEndMinutes: hi,
  }
}

/** Total scheduled minutes on a date — used for the "committed vs free" readout. */
export function scheduledMinutes(spine: DaySpine): number {
  return spine.blocks.filter((b) => b.lane === 0).reduce((sum, b) => sum + b.durationMinutes, 0)
}
