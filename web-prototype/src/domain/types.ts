/**
 * Domain types — a faithful mirror of the Kotlin domain model in
 * app/src/main/java/com/tripcompanion/app/domain/model/.
 *
 * The prototype must not invent its own product model. If a field changes
 * here it should change in Kotlin too, and vice versa.
 *
 * Dates/times are stored as ISO strings without zone or seconds
 * ("2026-09-08T08:30") — the wire format closest to Kotlin's LocalDateTime
 * for the fields the product actually uses. Seconds and nanoseconds are
 * deliberately absent (spec §9).
 */

/** ISO local date, e.g. "2026-09-08" */
export type IsoDate = string
/** ISO local date-time to minute precision, e.g. "2026-09-08T08:30" */
export type IsoDateTime = string

export const EVENT_TYPES = ['JOURNEY', 'VISIT', 'STAY', 'FOOD', 'CUSTOM'] as const
export type EventType = (typeof EVENT_TYPES)[number]

export const EVENT_STATUSES = [
  'UPCOMING',
  'STARTING_SOON',
  'ACTIVE',
  'COMPLETED',
  'SKIPPED',
  'MISSED',
] as const
export type EventStatus = (typeof EVENT_STATUSES)[number]

export const TRIP_STATUSES = ['PLANNING', 'ACTIVE', 'COMPLETED', 'CANCELLED'] as const
export type TripStatus = (typeof TRIP_STATUSES)[number]

export type TripPhase = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export interface Trip {
  id: number
  name: string
  startDate: IsoDate
  endDate: IsoDate
  status: TripStatus
}

export interface Event {
  id: number
  tripId: number
  type: EventType
  title: string
  startTime: IsoDateTime
  endTime: IsoDateTime
  locationId: number | null
  whatWeAreDoing: string
  notes: string
  /**
   * The *stored* status. Only COMPLETED and SKIPPED are meaningful here —
   * they are manual, terminal overrides. Everything else is derived by the
   * state engine from the clock and must never be read directly by the UI.
   */
  status: EventStatus
  order: number
}

export interface TripLocation {
  id: number
  name: string
  address: string
  latitude: number
  longitude: number
  category: string
  providerPlaceId: string | null
  providerName: string | null
}

/** A place candidate from a search provider, before the user confirms it. */
export interface SearchResultLocation {
  name: string
  formattedAddress: string
  latitude: number
  longitude: number
  category: string
  providerPlaceId: string | null
  providerName: string
}

/**
 * Planned photo — "this is the photo we want to recreate" (spec §15).
 * The image is the primary information; `title` is optional and short.
 *
 * Note what is absent by design: no pose instructions, no framing notes,
 * no shot specification. Spec §15 explicitly removes them from the card.
 */
export interface PlannedPhoto {
  id: number
  eventId: number
  /** Optional. A photo with no text at all is valid. */
  title: string
  /** Object URL or data URI for the prototype; a file path on Android. */
  imageUri: string | null
  order: number
}
