/**
 * Trip list — spec §25.
 *
 * The app opens here, because a trip companion with one hardcoded trip is a
 * demo, not a product (§4). Two unrelated trips in the seed data make the point
 * without a line of trip-specific code anywhere.
 */

import { useMemo } from 'react'
import { eachDate, formatDateRange } from '../domain/datetime'
import { computeTripState } from '../domain/tripStateEngine'
import type { Trip } from '../domain/types'
import { useNav } from '../nav/router'
import { useNow, useStore } from '../data/store'
import { EmptyState, Screen } from '../ui/Screen'
import { tripStatusLabel } from '../ui/Chips'
import { IconCalendar, IconPlus, IconSettings } from '../ui/Icons'

export function TripListScreen() {
  const nav = useNav()
  const store = useStore()
  const now = useNow()

  const trips = useMemo(
    () => [...store.trips].sort((a, b) => (a.startDate < b.startDate ? -1 : 1)),
    [store.trips],
  )

  const open = (trip: Trip) => {
    store.selectTrip(trip.id)
    nav.reset([{ name: 'trips' }, { name: 'home' }])
  }

  return (
    <Screen
      title="Trips"
      actions={
        <button
          type="button"
          className="icon-btn icon-btn--bare"
          onClick={() => nav.push({ name: 'settings' })}
          aria-label="Settings"
        >
          <IconSettings />
        </button>
      }
    >
      {trips.length === 0 ? (
        <EmptyState
          mark={<IconCalendar size={24} />}
          headline="No trips yet"
          body="A trip is a name and a date range. Everything else hangs off it."
          action={
            <button
              type="button"
              className="btn btn--primary"
              onClick={() => nav.push({ name: 'tripEditor' })}
            >
              New trip
            </button>
          }
        />
      ) : (
        <div className="stack" style={{ paddingTop: 'var(--sp-2)', gap: 'var(--sp-4)' }}>
          {trips.map((trip) => (
            <TripCard key={trip.id} trip={trip} now={now} onOpen={() => open(trip)} />
          ))}
        </div>
      )}

      <button
        type="button"
        className="fab"
        onClick={() => nav.push({ name: 'tripEditor' })}
        aria-label="New trip"
      >
        <IconPlus size={24} />
      </button>
    </Screen>
  )
}

function TripCard({ trip, now, onOpen }: { trip: Trip; now: string; onOpen: () => void }) {
  const store = useStore()
  const state = useMemo(
    () => computeTripState(trip, store.eventsForTrip(trip.id), now),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [trip, store.events, now],
  )

  const today = now.split('T')[0]
  const days = eachDate(trip.startDate, trip.endDate)

  const phaseLabel =
    state.phase === 'IN_PROGRESS'
      ? `Day ${state.currentTripDay} of ${state.totalTripDays}`
      : state.phase === 'NOT_STARTED'
        ? 'Not started'
        : state.phase === 'CANCELLED'
          ? 'Cancelled'
          : 'Complete'

  return (
    <button type="button" className="trip-card card--tappable" onClick={onOpen}>
      <div className="row row--between">
        <span className="eyebrow">{tripStatusLabel(trip.status)}</span>
        <span className="eyebrow">
          {state.totalEventCount} {state.totalEventCount === 1 ? 'event' : 'events'}
        </span>
      </div>

      <h2 className="title title--lg" style={{ marginTop: 'var(--sp-3)' }}>
        {trip.name}
      </h2>
      <p className="trip-card__dates">{formatDateRange(trip.startDate, trip.endDate)}</p>

      {/* One segment per day: where the plan is dense, where it is empty, where
          today is. A whole trip readable in a glance without opening it. */}
      <div className="trip-card__days" aria-hidden="true">
        {days.map((date) => {
          const count = state.events.filter((e) => e.startTime.startsWith(date)).length
          const fill = date === today ? 'now' : count >= 3 ? 'full' : count > 0 ? 'some' : 'none'
          return <span key={date} className="trip-card__day" data-fill={fill} />
        })}
      </div>

      <div className="trip-card__foot">
        <span className="eyebrow eyebrow--accent">{phaseLabel}</span>
        <span className="spacer" />
        <span className="eyebrow">
          {days.length} {days.length === 1 ? 'day' : 'days'}
        </span>
      </div>
    </button>
  )
}
