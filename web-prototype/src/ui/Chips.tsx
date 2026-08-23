/**
 * Status and type chips.
 *
 * Status wording lives here and nowhere else, so "STARTING SOON" cannot become
 * "Soon" on one screen and "Starting soon" on another (§22).
 */

import type { EventStatus, EventType, TripStatus } from '../domain/types'

const STATUS_LABEL: Record<EventStatus, string> = {
  UPCOMING: 'Upcoming',
  STARTING_SOON: 'Starting soon',
  ACTIVE: 'Now',
  COMPLETED: 'Done',
  SKIPPED: 'Skipped',
  MISSED: 'Missed',
}

const TYPE_LABEL: Record<EventType, string> = {
  JOURNEY: 'Journey',
  VISIT: 'Visit',
  STAY: 'Stay',
  FOOD: 'Food',
  CUSTOM: 'Custom',
}

/** A trip's stored status, which is a user choice — not the computed phase. */
const TRIP_STATUS_LABEL: Record<TripStatus, string> = {
  PLANNING: 'Planning',
  ACTIVE: 'Active',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
}

export function StatusBadge({ status }: { status: EventStatus }) {
  return (
    <span className={`status status--${status}`}>
      <span className="status__mark" />
      {STATUS_LABEL[status]}
    </span>
  )
}

export function TypeChip({ type }: { type: EventType }) {
  return <span className="type-chip">{TYPE_LABEL[type]}</span>
}

export const statusLabel = (status: EventStatus) => STATUS_LABEL[status]
export const typeLabel = (type: EventType) => TYPE_LABEL[type]
export const tripStatusLabel = (status: TripStatus) => TRIP_STATUS_LABEL[status]

/** COMPLETED and SKIPPED are the only statuses that strike a title through. */
export const isTerminal = (status: EventStatus) => status === 'COMPLETED' || status === 'SKIPPED'
