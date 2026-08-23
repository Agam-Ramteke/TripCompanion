/**
 * Trip state engine — spec §21, §22.
 *
 * A direct port of TripStateEngine.kt. Pure: clock in, state out. No GPS, no
 * network, no storage. Every screen reads from this one snapshot so Home,
 * Timeline and Event Detail can never disagree (spec §22).
 *
 * If the Kotlin rules change, change them here in the same commit.
 */

import { sortEventsChronologically } from './ordering'
import { daysBetween, minutesBetween } from './datetime'
import type { Event, EventStatus, IsoDateTime, Trip, TripPhase } from './types'

/** Minutes before start at which an event begins reading as STARTING_SOON. */
export const STARTING_SOON_MINUTES = 30

export interface TripState {
  trip: Trip | null
  currentEvent: Event | null
  nextEvent: Event | null
  previousEvent: Event | null
  currentTripDay: number
  totalTripDays: number
  phase: TripPhase
  /** Chronologically sorted, each with its *computed* status. */
  events: Event[]
  completedEventCount: number
  totalEventCount: number
  /** 0..1 */
  progress: number
}

export const EMPTY_TRIP_STATE: TripState = {
  trip: null,
  currentEvent: null,
  nextEvent: null,
  previousEvent: null,
  currentTripDay: 0,
  totalTripDays: 0,
  phase: 'NOT_STARTED',
  events: [],
  completedEventCount: 0,
  totalEventCount: 0,
  progress: 0,
}

/**
 * Effective status of one event.
 *
 * COMPLETED and SKIPPED are manual and terminal — they are never recalculated,
 * which is what stops a completed event reverting to ACTIVE when the clock
 * moves back inside its window.
 */
export function computeEventStatus(event: Event, now: IsoDateTime): EventStatus {
  if (event.status === 'COMPLETED' || event.status === 'SKIPPED') return event.status

  if (now > event.endTime) return 'MISSED'
  if (now >= event.startTime) return 'ACTIVE'
  if (minutesBetween(now, event.startTime) <= STARTING_SOON_MINUTES) return 'STARTING_SOON'
  return 'UPCOMING'
}

export function computeTripState(
  trip: Trip | null,
  events: readonly Event[],
  now: IsoDateTime,
): TripState {
  if (!trip) return EMPTY_TRIP_STATE

  const computed = sortEventsChronologically(events).map((e) => ({
    ...e,
    status: computeEventStatus(e, now),
  }))

  const currentEvent = computed.find((e) => e.status === 'ACTIVE') ?? null

  const isPending = (e: Event) => e.status === 'UPCOMING' || e.status === 'STARTING_SOON'
  const nextEvent = currentEvent
    ? computed.slice(computed.findIndex((e) => e.id === currentEvent.id) + 1).find(isPending) ?? null
    : computed.find(isPending) ?? null

  // "Previous" is the most recent event already behind us, relative to
  // whichever of current/next anchors the present moment.
  const anchorIndex = currentEvent
    ? computed.findIndex((e) => e.id === currentEvent.id)
    : nextEvent
      ? computed.findIndex((e) => e.id === nextEvent.id)
      : computed.length

  const isPast = (e: Event) =>
    e.status === 'COMPLETED' || e.status === 'SKIPPED' || e.status === 'MISSED'
  const previousEvent =
    anchorIndex > 0 ? [...computed.slice(0, anchorIndex)].reverse().find(isPast) ?? null : null

  const today = now.split('T')[0]
  const dayOffset = daysBetween(trip.startDate, today)
  const currentTripDay = dayOffset < 0 ? 0 : dayOffset + 1
  const totalTripDays = daysBetween(trip.startDate, trip.endDate) + 1

  const completedEventCount = computed.filter((e) => e.status === 'COMPLETED').length
  const totalEventCount = computed.length
  const progress = totalEventCount > 0 ? completedEventCount / totalEventCount : 0

  const phase: TripPhase =
    trip.status === 'CANCELLED'
      ? 'CANCELLED'
      : today < trip.startDate
        ? 'NOT_STARTED'
        : today > trip.endDate
          ? 'COMPLETED'
          : computed.length > 0 && computed.every(isPast)
            ? 'COMPLETED'
            : 'IN_PROGRESS'

  return {
    trip,
    currentEvent,
    nextEvent,
    previousEvent,
    currentTripDay,
    totalTripDays,
    phase,
    events: computed,
    completedEventCount,
    totalEventCount,
    progress,
  }
}

// ── Derived helpers the UI needs repeatedly ─────────────────────────────────

/**
 * The single headline the HUD shows. Derived here, not in the view, so Home and
 * Event Detail describe the same moment with the same words.
 */
export function describeState(state: TripState, now: IsoDateTime): {
  label: string
  detail: string
} {
  const { trip, currentEvent, nextEvent, phase } = state
  if (!trip) return { label: 'NO TRIP', detail: 'Create a trip to begin' }

  if (phase === 'CANCELLED') return { label: 'CANCELLED', detail: trip.name }
  if (phase === 'NOT_STARTED') {
    const days = daysBetween(now, trip.startDate)
    return {
      label: 'NOT STARTED',
      detail: days === 0 ? 'Starts today' : days === 1 ? 'Starts tomorrow' : `Starts in ${days} days`,
    }
  }
  if (phase === 'COMPLETED') return { label: 'TRIP COMPLETE', detail: 'Nothing left to do' }

  if (currentEvent) return { label: 'ACTIVE NOW', detail: currentEvent.title }
  if (nextEvent && nextEvent.status === 'STARTING_SOON') {
    return { label: 'STARTING SOON', detail: nextEvent.title }
  }
  if (nextEvent) return { label: 'FREE', detail: `Next: ${nextEvent.title}` }
  return { label: 'DAY DONE', detail: 'Nothing else scheduled' }
}

/** Events on one calendar date, already chronological. */
export function eventsOnDate(state: TripState, date: string): Event[] {
  return state.events.filter((e) => e.startTime.startsWith(date))
}

/** Group events by their actual event date, in chronological order (spec §20). */
export function groupEventsByDate(events: readonly Event[]): Array<{ date: string; events: Event[] }> {
  const groups = new Map<string, Event[]>()
  for (const e of sortEventsChronologically(events)) {
    const date = e.startTime.split('T')[0]
    const bucket = groups.get(date)
    if (bucket) bucket.push(e)
    else groups.set(date, [e])
  }
  return [...groups.entries()]
    .sort((a, b) => (a[0] < b[0] ? -1 : 1))
    .map(([date, events]) => ({ date, events }))
}
