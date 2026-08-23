/**
 * The lab — the desktop harness the prototype is reviewed in.
 *
 * Three things it has to make possible, none of which a plain browser page does:
 *
 *   Scale.  The design is for a phone. Reviewing it in a 1440px window flatters
 *           every layout decision, so it renders inside a 390×844 frame.
 *   Time.   The state engine is a function of the clock (§21), and five of the six
 *           event statuses are unreachable unless the clock moves. So the clock is
 *           a control here: a slider across the whole trip, plus presets that land
 *           on each moment worth looking at.
 *   Themes. §23 asks whether the two themes preserve data and function. The only
 *           way to answer that is side by side, same screen, same data.
 *
 * The harness itself is deliberately neutral grey — it must not flatter either
 * theme. Nothing in here is a design proposal and none of it ships.
 */

import { useEffect, useMemo, useState } from 'react'
import {
  addDays,
  addMinutes,
  daysBetween,
  dateOf,
  formatDateMedium,
  minutesBetween,
  timeOf,
  toIsoDateTime,
} from '../domain/datetime'
import { sortEventsChronologically } from '../domain/ordering'
import { MIN_NOTABLE_GAP_MINUTES } from '../domain/daySpine'
import type { Event, IsoDateTime, Trip } from '../domain/types'
import type { Route, RouteName } from '../nav/router'
import { NavProvider, useNav } from '../nav/router'
import { DEFAULT_SIM_NOW } from '../data/seed'
import { SearchConfigProvider } from '../data/searchContext'
import {
  ClockProvider,
  StoreProvider,
  useClock,
  useStore,
  type StoreApi,
  type ThemeName,
} from '../data/store'
import { App } from '../App'

export function Lab() {
  return (
    <StoreProvider>
      <SearchConfigProvider>
        <LabShell />
      </SearchConfigProvider>
    </StoreProvider>
  )
}

/**
 * Owns the simulated clock and the nav stack. Both live above the phone frames so
 * that in compare mode the two phones are guaranteed to be showing the same screen
 * at the same instant — otherwise the comparison proves nothing.
 */
function LabShell() {
  const [now, setNow] = useState<IsoDateTime>(DEFAULT_SIM_NOW)
  const [live, setLive] = useState(false)

  useEffect(() => {
    if (!live) return
    const tick = () => setNow(toIsoDateTime(new Date()))
    tick()
    const id = setInterval(tick, 15_000)
    return () => clearInterval(id)
  }, [live])

  const clock = useMemo(
    () => ({
      now,
      live,
      // Choosing a moment by hand means you no longer want the real clock.
      setNow: (iso: IsoDateTime) => {
        setLive(false)
        setNow(iso)
      },
      setLive,
    }),
    [now, live],
  )

  return (
    <ClockProvider value={clock}>
      <NavProvider initial={[{ name: 'trips' }, { name: 'home' }]}>
        <LabBody />
      </NavProvider>
    </ClockProvider>
  )
}

// ── Clock presets ────────────────────────────────────────────────────────────

interface Preset {
  label: string
  iso: IsoDateTime
}

/**
 * Every preset is derived from the loaded data, never written down — and a preset
 * that cannot be derived from the current trip is not rendered at all, because a
 * control that does nothing is worse than a missing one.
 */
function buildPresets(trip: Trip | null, events: readonly Event[]): Preset[] {
  if (!trip) return []
  const ordered = sortEventsChronologically(events)
  const out: Preset[] = [{ label: 'Before departure', iso: `${addDays(trip.startDate, -1)}T18:30` }]

  const first = ordered[0]
  if (first) out.push({ label: 'Starting soon', iso: addMinutes(first.startTime, -18) })

  // The longest event gives the clearest reading of the "in progress" hero.
  const longest = ordered.reduce<Event | null>(
    (best, e) =>
      !best || minutesBetween(e.startTime, e.endTime) > minutesBetween(best.startTime, best.endTime)
        ? e
        : best,
    null,
  )
  if (longest) {
    const half = Math.floor(minutesBetween(longest.startTime, longest.endTime) / 2)
    out.push({ label: 'Mid-event', iso: addMinutes(longest.startTime, half) })
  }

  // A real gap between two consecutive events on one day — the "free time" HUD.
  for (let i = 0; i < ordered.length - 1; i++) {
    const gap = minutesBetween(ordered[i].endTime, ordered[i + 1].startTime)
    const sameDay = dateOf(ordered[i].endTime) === dateOf(ordered[i + 1].startTime)
    if (gap >= MIN_NOTABLE_GAP_MINUTES && sameDay) {
      out.push({ label: 'Free time', iso: addMinutes(ordered[i].endTime, Math.floor(gap / 2)) })
      break
    }
  }

  // Just past an event still marked as scheduled — the only route to MISSED.
  const stale = ordered.find((e) => e.status === 'UPCOMING')
  if (stale) out.push({ label: 'After a miss', iso: addMinutes(stale.endTime, 25) })

  out.push({ label: 'Trip over', iso: `${addDays(trip.endDate, 1)}T11:00` })
  return out
}

// ── Screen jump list ─────────────────────────────────────────────────────────

interface Jump {
  label: string
  stack: Route[]
}

/**
 * The nine screens §30 asks questions about, each reached on a stack it could
 * plausibly be reached on in the app — so "back" from search really does land on a
 * half-written event. Ids and places come from the data, so this list works for
 * any trip.
 */
function buildJumps(store: StoreApi): Jump[] {
  const trip = store.selectedTrip
  if (!trip) return [{ label: 'Trip list', stack: [{ name: 'trips' }] }]

  const events = sortEventsChronologically(store.eventsForTrip(trip.id))
  const first = events[0]
  const withPhotos = events.find((e) => store.photosForEvent(e.id).length > 0) ?? first
  const place = store.locationById(events.find((e) => e.locationId != null)?.locationId)

  const base: Route[] = [{ name: 'trips' }, { name: 'home' }]
  const jumps: Jump[] = [
    { label: 'Trip list', stack: [{ name: 'trips' }] },
    { label: 'Home HUD', stack: base },
    { label: 'Timeline', stack: [...base, { name: 'timeline', focusDate: trip.startDate }] },
  ]

  if (first) {
    const editor: Route = { name: 'editor', tripId: trip.id, eventId: first.id }
    jumps.push({ label: 'Event detail', stack: [...base, { name: 'event', eventId: first.id }] })
    jumps.push({ label: 'Event editor', stack: [...base, editor] })
    jumps.push({ label: 'Location search', stack: [...base, editor, { name: 'search' }] })

    if (place) {
      jumps.push({
        label: 'Map confirmation',
        stack: [
          ...base,
          editor,
          { name: 'search' },
          {
            name: 'map',
            place: {
              name: place.name,
              formattedAddress: place.address,
              latitude: place.latitude,
              longitude: place.longitude,
              category: place.category,
              providerPlaceId: place.providerPlaceId,
              providerName: place.providerName ?? 'Manual',
            },
          },
        ],
      })
    }
  }

  if (withPhotos) {
    jumps.push({
      label: 'Photo plan',
      stack: [
        ...base,
        { name: 'event', eventId: withPhotos.id },
        { name: 'photos', eventId: withPhotos.id },
      ],
    })
  }

  jumps.push({ label: 'Settings & themes', stack: [{ name: 'trips' }, { name: 'settings' }] })
  return jumps
}

// ── Shell ────────────────────────────────────────────────────────────────────

function LabBody() {
  const store = useStore()
  const nav = useNav()
  const clock = useClock()
  const [compare, setCompare] = useState(false)

  const trip = store.selectedTrip

  // The slider spans every trip in the store with two days of slack either side,
  // so "before departure" and "after the trip" are both reachable by dragging.
  const range = useMemo(() => {
    if (store.trips.length === 0) {
      const today = dateOf(DEFAULT_SIM_NOW)
      return { from: `${today}T00:00`, to: `${today}T23:59` }
    }
    const starts = store.trips.map((t) => t.startDate).sort()
    const ends = store.trips.map((t) => t.endDate).sort()
    return {
      from: `${addDays(starts[0], -2)}T00:00`,
      to: `${addDays(ends[ends.length - 1], 2)}T23:59`,
    }
  }, [store.trips])

  const span = Math.max(1, minutesBetween(range.from, range.to))
  const offset = Math.min(span, Math.max(0, minutesBetween(range.from, clock.now)))

  // `store` is a fresh object on every mutation, so it is the whole dependency.
  const presets = useMemo(
    () => buildPresets(trip, trip ? store.eventsForTrip(trip.id) : []),
    [store, trip],
  )
  const jumps = useMemo(() => buildJumps(store), [store])

  const topRoute: RouteName = nav.stack[nav.stack.length - 1]?.route.name ?? 'trips'
  const today = dateOf(clock.now)
  const dayNumber =
    trip && today >= trip.startDate && today <= trip.endDate
      ? daysBetween(trip.startDate, today) + 1
      : null

  return (
    <div className="lab">
      <aside className="lab__rail">
        <div className="lab__brand">
          <p className="lab__brand-title">Trip Companion · visual prototype</p>
          <p className="lab__brand-sub">
            Spec v2.1 §30. Sample data, stored locally, no account. Design decisions only — the
            shipping app is the Android build.
          </p>
        </div>

        <hr className="lab__hr" />

        <div className="lab__group">
          <p className="lab__legend">Theme</p>
          <div className="lab__row">
            <ThemeButton theme="nothing" label="Monochrome" swatch="#e5322b" locked={compare} />
            <ThemeButton theme="brutalist" label="Brutalist" swatch="#ffe500" locked={compare} />
          </div>
          <button
            type="button"
            className="lab__btn lab__btn--wide"
            aria-pressed={compare}
            onClick={() => setCompare((c) => !c)}
          >
            {compare ? 'Showing both themes' : 'Compare side by side'}
          </button>
        </div>

        <hr className="lab__hr" />

        <div className="lab__group">
          <p className="lab__legend">Simulated clock</p>
          <div>
            <p className="lab__clock">
              {timeOf(clock.now)} · {formatDateMedium(clock.now)}
            </p>
            <p className="lab__clock-sub">
              {trip && dayNumber != null
                ? `Day ${dayNumber} of ${daysBetween(trip.startDate, trip.endDate) + 1} · ${trip.name}`
                : trip
                  ? `Outside ${trip.name}`
                  : 'No trip selected'}
            </p>
          </div>
          <input
            className="lab__slider"
            type="range"
            min={0}
            max={span}
            step={5}
            value={offset}
            onChange={(e) => clock.setNow(addMinutes(range.from, Number(e.target.value)))}
            aria-label="Simulated time"
          />
          <div className="lab__presets">
            {presets.map((preset) => (
              <button
                key={preset.label}
                type="button"
                className="lab__btn"
                onClick={() => clock.setNow(preset.iso)}
              >
                {preset.label}
              </button>
            ))}
          </div>
          <button
            type="button"
            className="lab__btn lab__btn--wide"
            aria-pressed={clock.live}
            onClick={() => (clock.live ? clock.setNow(DEFAULT_SIM_NOW) : clock.setLive(true))}
          >
            {clock.live ? 'Live — click to go back' : 'Follow the real clock'}
          </button>
        </div>

        <hr className="lab__hr" />

        <div className="lab__group">
          <p className="lab__legend">Screens</p>
          <nav className="lab__nav">
            {jumps.map((jump, index) => (
              <button
                key={jump.label}
                type="button"
                className="lab__nav-item"
                aria-current={jump.stack[jump.stack.length - 1].name === topRoute}
                onClick={() => nav.reset(jump.stack)}
              >
                <span className="lab__nav-num">{String(index + 1).padStart(2, '0')}</span>
                {jump.label}
              </button>
            ))}
          </nav>
        </div>

        <hr className="lab__hr" />

        <div className="lab__group">
          <p className="lab__legend">Data</p>
          <button
            type="button"
            className="lab__btn lab__btn--wide"
            onClick={() => {
              store.reset()
              nav.reset([{ name: 'trips' }, { name: 'home' }])
            }}
          >
            Reset to sample data
          </button>
          <p className="lab__note">
            <strong>Two unrelated trips</strong> are seeded on purpose. Nothing in the app branches
            on a place name, so a city trip and a mountain loop run identical code.
          </p>
          <p className="lab__note">
            Adding events, picking photos, searching places and the map all work for real. Search
            runs offline by default — point it at live OpenStreetMap, or force any failure, in the
            app's own Settings.
          </p>
        </div>
      </aside>

      <main className={`lab__stage${compare ? ' lab__stage--compare' : ''}`}>
        {compare ? (
          <>
            <Device theme="nothing" label="Nothing-inspired" now={clock.now} />
            <Device theme="brutalist" label="Neo-brutalist" now={clock.now} />
          </>
        ) : (
          <Device
            theme={store.theme}
            label={store.theme === 'nothing' ? 'Nothing-inspired' : 'Neo-brutalist'}
            now={clock.now}
          />
        )}
      </main>
    </div>
  )
}

function ThemeButton({
  theme,
  label,
  swatch,
  locked,
}: {
  theme: ThemeName
  label: string
  swatch: string
  locked: boolean
}) {
  const store = useStore()
  return (
    <button
      type="button"
      className="lab__btn"
      aria-pressed={!locked && store.theme === theme}
      disabled={locked}
      title={locked ? 'Both themes are on screen' : undefined}
      onClick={() => store.setTheme(theme)}
    >
      <span className="lab__swatch" style={{ background: swatch }} />
      {label}
    </button>
  )
}

/**
 * One phone. `data-theme` sits on the screen rather than inside <App/> so the
 * phone's own status bar is themed with the app it contains.
 */
function Device({ theme, label, now }: { theme: ThemeName; label: string; now: IsoDateTime }) {
  return (
    <div className="device">
      <span className="device__label">{label}</span>
      <div className="phone">
        <div className="phone__screen" data-theme={theme}>
          <div className="phone__notch" />
          <div className="phone__statusbar">
            <span className="clock">{timeOf(now)}</span>
            <span aria-hidden="true">▮▮▮ ▮</span>
          </div>
          <div className="phone__app">
            <App />
          </div>
        </div>
      </div>
    </div>
  )
}
