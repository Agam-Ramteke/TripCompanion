/**
 * Home — the HUD (spec §19).
 *
 * The hero answers one question: what is happening right now. Not the clock —
 * the phone already has a clock. Below it, the Day Spine shows the shape of the
 * day to scale, and then the single next thing.
 *
 * Intentionally omitted (§30): weather, distance, travel time, notes, photo
 * counts, a map, and any count of days remaining. All of it is either unknowable
 * offline or noise at a glance.
 */

import { useMemo } from 'react'
import {
  formatDayAndMonth,
  formatDuration,
  formatRelative,
  formatTimeRange,
  minutesBetween,
  timeOf,
} from '../domain/datetime'
import { computeTripState, describeState } from '../domain/tripStateEngine'
import type { Event } from '../domain/types'
import { useNav } from '../nav/router'
import { useNow, useStore } from '../data/store'
import { EmptyState, Screen, Section } from '../ui/Screen'
import { DaySpine } from '../ui/DaySpine'
import { StatusBadge, TypeChip } from '../ui/Chips'
import { IconCheck, IconList, IconPlus, IconSkip } from '../ui/Icons'

export function HomeScreen() {
  const nav = useNav()
  const store = useStore()
  const now = useNow()
  const trip = store.selectedTrip

  const state = useMemo(
    () => computeTripState(trip, trip ? store.eventsForTrip(trip.id) : [], now),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [trip, store.events, now],
  )

  if (!trip) {
    return (
      <Screen title="Home">
        <EmptyState
          mark={<IconList size={24} />}
          headline="No trip selected"
          body="Pick a trip to see what's happening."
          action={
            <button type="button" className="btn btn--primary" onClick={() => nav.popTo('trips')}>
              Choose a trip
            </button>
          }
        />
      </Screen>
    )
  }

  const today = now.split('T')[0]
  const { currentEvent, nextEvent } = state
  const description = describeState(state, now)
  const subject = currentEvent ?? nextEvent

  const openEvent = (event: Event) => nav.push({ name: 'event', eventId: event.id })

  return (
    <Screen
      title={trip.name}
      onBack={() => nav.popTo('trips')}
      actions={
        <button
          type="button"
          className="icon-btn icon-btn--bare"
          onClick={() => nav.push({ name: 'timeline', focusDate: today })}
          aria-label="Timeline"
        >
          <IconList />
        </button>
      }
    >
      <div className="hud__head">
        <div className="hud__trip">
          <h1 className="title title--lg">{trip.name}</h1>
          <div className="hud__day">
            <span className="hud__day-count">
              {state.phase === 'IN_PROGRESS'
                ? `DAY ${state.currentTripDay} / ${state.totalTripDays}`
                : state.phase === 'NOT_STARTED'
                  ? 'BEFORE DEPARTURE'
                  : state.phase === 'CANCELLED'
                    ? 'CANCELLED'
                    : 'TRIP COMPLETE'}
            </span>
            <span className="eyebrow">{formatDayAndMonth(today)}</span>
          </div>
        </div>
      </div>

      <div className={`hud__hero${currentEvent ? '' : ' hud__hero--idle'}`}>
        <div className="hud__state">
          {currentEvent ? (
            <StatusBadge status={currentEvent.status} />
          ) : (
            <span className="eyebrow eyebrow--accent">{description.label}</span>
          )}
          <span className="hud__time">{timeOf(now)}</span>
        </div>

        {subject ? (
          <>
            <button
              type="button"
              className="hud__subject"
              onClick={() => openEvent(subject)}
              style={{ display: 'block', textAlign: 'left', color: 'inherit', width: '100%' }}
            >
              {subject.title}
            </button>
            <p className="hud__meta">
              {formatTimeRange(subject.startTime, subject.endTime)}
              {currentEvent
                ? ` · ${formatDuration(Math.max(0, minutesBetween(now, subject.endTime)))} left`
                : ` · ${formatRelative(now, subject.startTime)}`}
            </p>
            {currentEvent && <ElapsedBar event={currentEvent} now={now} />}
          </>
        ) : (
          <>
            <p className="hud__subject">{description.detail}</p>
            <p className="hud__meta">Nothing scheduled from here on</p>
          </>
        )}

        {currentEvent && (
          <div className="hud__actions">
            <button
              type="button"
              className="btn btn--primary btn--grow btn--sm"
              onClick={() => store.setEventStatus(currentEvent.id, 'COMPLETED')}
            >
              <IconCheck size={16} />
              Done
            </button>
            <button
              type="button"
              className="btn btn--quiet btn--grow btn--sm"
              onClick={() => store.setEventStatus(currentEvent.id, 'SKIPPED')}
            >
              <IconSkip size={16} />
              Skip
            </button>
          </div>
        )}
      </div>

      <Section
        label="Today, to scale"
        action={
          <button
            type="button"
            className="btn btn--quiet btn--sm"
            onClick={() => nav.push({ name: 'timeline', focusDate: today })}
          >
            Timeline
          </button>
        }
      >
        <DaySpine date={today} events={state.events} now={now} onSelect={openEvent} />
      </Section>

      {nextEvent && nextEvent.id !== subject?.id && (
        <Section label="Next">
          <NextCard event={nextEvent} now={now} onOpen={() => openEvent(nextEvent)} />
        </Section>
      )}

      {state.events.filter((e) => e.startTime.startsWith(today)).length === 0 && (
        <Section label="Today">
          <div className="card card--flat">
            <p className="body-text body-text--dim">
              Nothing planned for today. That is a valid plan.
            </p>
            <button
              type="button"
              className="btn btn--quiet btn--sm"
              style={{ marginTop: 'var(--sp-3)' }}
              onClick={() => nav.push({ name: 'editor', tripId: trip.id, date: today })}
            >
              <IconPlus size={16} />
              Add an event
            </button>
          </div>
        </Section>
      )}
    </Screen>
  )
}

function ElapsedBar({ event, now }: { event: Event; now: string }) {
  const total = Math.max(1, minutesBetween(event.startTime, event.endTime))
  const done = Math.min(total, Math.max(0, minutesBetween(event.startTime, now)))
  return (
    <div className="hud__bar" aria-hidden="true">
      <div className="hud__bar-fill" style={{ width: `${(done / total) * 100}%` }} />
    </div>
  )
}

function NextCard({ event, now, onOpen }: { event: Event; now: string; onOpen: () => void }) {
  const store = useStore()
  const place = store.locationById(event.locationId)
  return (
    <button type="button" className="card card--tappable" onClick={onOpen}>
      <div className="row row--between">
        <span className="clock" style={{ fontSize: 'var(--step-2)', fontWeight: 500 }}>
          {formatTimeRange(event.startTime, event.endTime)}
        </span>
        <span className="eyebrow">{formatRelative(now, event.startTime)}</span>
      </div>
      <h3 className="title title--md" style={{ marginTop: 'var(--sp-2)' }}>
        {event.title}
      </h3>
      {place && <p className="tl__place">{place.name}</p>}
      <div className="tl__tags">
        <TypeChip type={event.type} />
        <StatusBadge status={event.status} />
      </div>
    </button>
  )
}
