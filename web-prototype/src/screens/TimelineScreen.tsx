/**
 * Timeline — spec §20.
 *
 * Grouped by the event's own date, chronological within the day, one comparator
 * (§10). Empty trip days are shown rather than hidden: a hole in the plan is
 * information, and the Timeline is where you would want to notice it.
 */

import { useEffect, useMemo, useRef } from 'react'
import { MIN_NOTABLE_GAP_MINUTES } from '../domain/daySpine'
import {
  eachDate,
  formatDayHeader,
  formatDuration,
  formatTime,
  minutesBetween,
} from '../domain/datetime'
import { computeTripState } from '../domain/tripStateEngine'
import type { Event, IsoDate } from '../domain/types'
import { useNav } from '../nav/router'
import { useNow, useStore } from '../data/store'
import { EmptyState, Screen } from '../ui/Screen'
import { StatusBadge, TypeChip, isTerminal } from '../ui/Chips'
import { IconCalendar, IconPlus } from '../ui/Icons'

export function TimelineScreen({ focusDate }: { focusDate?: IsoDate }) {
  const nav = useNav()
  const store = useStore()
  const now = useNow()
  const trip = store.selectedTrip
  const focusRef = useRef<HTMLDivElement | null>(null)

  const state = useMemo(
    () => computeTripState(trip, trip ? store.eventsForTrip(trip.id) : [], now),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [trip, store.events, now],
  )

  // Every trip day, plus any date carrying events outside the range, so no event
  // can be silently invisible.
  const days = useMemo(() => {
    if (!trip) return []
    const inRange = eachDate(trip.startDate, trip.endDate)
    const withEvents = state.events.map((e) => e.startTime.split('T')[0])
    return [...new Set([...inRange, ...withEvents])].sort()
  }, [trip, state.events])

  useEffect(() => {
    focusRef.current?.scrollIntoView({ block: 'start' })
  }, [focusDate])

  if (!trip) {
    return (
      <Screen title="Timeline" onBack={() => nav.pop()}>
        <EmptyState mark={<IconCalendar size={24} />} headline="No trip" body="Pick a trip first." />
      </Screen>
    )
  }

  return (
    <Screen title={`${trip.name} · timeline`} onBack={() => nav.pop()}>
      {days.map((date) => {
        const dayEvents = state.events.filter((e) => e.startTime.startsWith(date))
        const isFocus = date === focusDate
        return (
          <div key={date} ref={isFocus ? focusRef : undefined}>
            <div className="tl__day">
              <h2 className="eyebrow">{formatDayHeader(date)}</h2>
              {date === now.split('T')[0] && <span className="eyebrow eyebrow--accent">TODAY</span>}
              <span className="tl__day-count">
                {dayEvents.length === 0 ? 'CLEAR' : `${dayEvents.length}`}
              </span>
            </div>

            {dayEvents.length === 0 ? (
              <button
                type="button"
                className="list-row"
                onClick={() => nav.push({ name: 'editor', tripId: trip.id, date })}
              >
                <span className="list-row__body">
                  <span className="list-row__label body-text--dim">Nothing planned</span>
                </span>
                <IconPlus size={16} />
              </button>
            ) : (
              dayEvents.map((event, index) => {
                const previous = dayEvents[index - 1]
                const gap = previous ? minutesBetween(previous.endTime, event.startTime) : 0
                return (
                  <div key={event.id}>
                    {gap >= MIN_NOTABLE_GAP_MINUTES && <GapRow minutes={gap} />}
                    <TimelineItem
                      event={event}
                      onOpen={() => nav.push({ name: 'event', eventId: event.id })}
                    />
                  </div>
                )
              })
            )}
          </div>
        )
      })}

      <button
        type="button"
        className="fab"
        onClick={() =>
          nav.push({ name: 'editor', tripId: trip.id, date: focusDate ?? trip.startDate })
        }
        aria-label="Add event"
      >
        <IconPlus size={24} />
      </button>
    </Screen>
  )
}

function GapRow({ minutes }: { minutes: number }) {
  return (
    <div className="tl__gap">
      <span className="tl__gap-label">{formatDuration(minutes)}</span>
      <div className="tl__gap-rail" />
      <span className="tl__gap-label">free</span>
    </div>
  )
}

function TimelineItem({ event, onOpen }: { event: Event; onOpen: () => void }) {
  const store = useStore()
  const place = store.locationById(event.locationId)
  const photos = store.photosForEvent(event.id)

  return (
    <button type="button" className="tl__item" onClick={onOpen}>
      <span className="tl__time">
        {formatTime(event.startTime)}
        <span className="tl__time-end">{formatTime(event.endTime)}</span>
      </span>

      <span className="tl__rail">
        <span className="tl__dot" data-status={event.status} />
      </span>

      <span className="tl__content">
        <span className="tl__row">
          <span className={`tl__title${isTerminal(event.status) ? ' is-struck' : ''}`}>
            {event.title}
          </span>
        </span>
        {place && <span className="tl__place">{place.name}</span>}
        <span className="tl__tags">
          <TypeChip type={event.type} />
          <StatusBadge status={event.status} />
        </span>
        {photos.length > 0 && (
          <span className="tl__photos">
            {photos.slice(0, 4).map((photo) =>
              photo.imageUri ? (
                <img key={photo.id} className="tl__photo-dot" src={photo.imageUri} alt="" />
              ) : null,
            )}
          </span>
        )}
      </span>
    </button>
  )
}
