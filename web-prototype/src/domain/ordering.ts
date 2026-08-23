/**
 * The single definition of chronological order — spec §10.
 *
 * Timeline, Home, Event Detail navigation, the store and the state engine all
 * import this one comparator. No screen is allowed to sort events its own way.
 *
 * Order: startTime ASC, then explicit `order` ASC, then id ASC as the final
 * deterministic tie-breaker.
 *
 * Mirrors TripStateEngine.EVENT_CHRONOLOGICAL_COMPARATOR in Kotlin.
 */

import type { Event } from './types'

export function compareEventsChronologically(a: Event, b: Event): number {
  // Both are fixed-width "YYYY-MM-DDTHH:mm", so lexical order is chronological.
  if (a.startTime !== b.startTime) return a.startTime < b.startTime ? -1 : 1
  if (a.order !== b.order) return a.order - b.order
  return a.id - b.id
}

/** Returns a new chronologically sorted array; never mutates the input. */
export function sortEventsChronologically(events: readonly Event[]): Event[] {
  return [...events].sort(compareEventsChronologically)
}
