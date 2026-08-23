/**
 * Location search — spec §11, §17.
 *
 * The screen knows about a service and five outcomes. It does not know what a
 * provider is, whether one is reachable, or what a rate limit is. Swapping
 * OpenStreetMap for anything else changes nothing here.
 *
 * Every outcome the spec lists is a designed state, not a hidden branch:
 * too-short, searching, results, empty, and each failure reason. The failure
 * copy always names something the user can still do — that is the difference
 * between an error and a dead end.
 *
 * Empty results are not a failure. A place with no entry in any gazetteer is a
 * normal travel case, so "use what I typed and put it on the map myself" is a
 * first-class path (§11 custom destinations), not a fallback.
 */

import { useEffect, useMemo, useRef, useState } from 'react'
import type { SearchResultLocation } from '../domain/types'
import { MIN_QUERY_LENGTH, type SearchOutcome } from '../data/search/LocationSearchService'
import { useSearchConfig } from '../data/searchContext'
import { useStore } from '../data/store'
import { useNav } from '../nav/router'
import { Screen, Section } from '../ui/Screen'
import { IconAlert, IconClose, IconPin, IconSearch, IconTarget } from '../ui/Icons'
import type { LatLon } from '../ui/MapView'

/** Where to open the map when there is no coordinate to start from. */
const WORLD_VIEW: LatLon & { zoom: number } = { lat: 20, lon: 0, zoom: 2 }

export function LocationSearchScreen({ initialQuery }: { initialQuery?: string }) {
  const nav = useNav()
  const store = useStore()
  const { service, providerName } = useSearchConfig()

  const [query, setQuery] = useState(initialQuery ?? '')
  const [outcome, setOutcome] = useState<SearchOutcome>({ kind: 'idle' })
  const [searching, setSearching] = useState(false)
  const inputRef = useRef<HTMLInputElement | null>(null)
  const seq = useRef(0)

  // A superseded request can still resolve (as a cancellation) after a newer one
  // has started. Sequence numbers keep the newest answer on screen.
  const run = (value: string, immediate = false) => {
    const id = ++seq.current
    const trimmed = value.trim()
    setSearching(trimmed.length >= MIN_QUERY_LENGTH)
    void (immediate ? service.searchNow(value) : service.search(value)).then((next) => {
      if (id !== seq.current) return
      setSearching(false)
      setOutcome(next)
    })
  }

  useEffect(() => {
    inputRef.current?.focus()
    if (initialQuery) run(initialQuery, true)
    return () => service.cancel()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /**
   * Somewhere sensible to open the map when the user is placing a pin by hand.
   * The trip's own places are the best guess; failing that, the whole world —
   * never a hard-coded city, which would tie the app to one destination (§4).
   */
  const anchor = useMemo<LatLon & { zoom: number }>(() => {
    const trip = store.selectedTrip
    const tripLocationIds = new Set(
      trip
        ? store
            .eventsForTrip(trip.id)
            .map((e) => e.locationId)
            .filter((id): id is number => id != null)
        : [],
    )
    const nearby = store.locations.filter((l) => tripLocationIds.has(l.id))
    const pool = nearby.length > 0 ? nearby : store.locations
    if (pool.length === 0) return WORLD_VIEW
    const lat = pool.reduce((sum, l) => sum + l.latitude, 0) / pool.length
    const lon = pool.reduce((sum, l) => sum + l.longitude, 0) / pool.length
    return { lat, lon, zoom: nearby.length > 0 ? 12 : 5 }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [store.locations, store.events, store.selectedTripId])

  /** Hand the confirmed place back to whoever opened the search. */
  const confirm = async (place: SearchResultLocation, zoom?: number) => {
    const confirmed = await nav.pushForResult<SearchResultLocation>({ name: 'map', place, zoom })
    if (confirmed) nav.pop(confirmed)
  }

  const useTypedName = () =>
    confirm(
      {
        name: query.trim(),
        formattedAddress: '',
        latitude: anchor.lat,
        longitude: anchor.lon,
        category: '',
        providerPlaceId: null,
        providerName: 'Manual',
      },
      anchor.zoom,
    )

  const savedPlaces = useMemo(
    () => [...store.locations].sort((a, b) => b.id - a.id).slice(0, 8),
    [store.locations],
  )

  return (
    <Screen title="Find a place" onBack={() => nav.pop()} backKind="close" flush>
      <div className="search__sticky">
        <div className="search__field">
          <input
            ref={inputRef}
            className="search__input"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value)
              run(e.target.value)
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') run(query, true)
            }}
            placeholder="Palace, station, café, address"
            aria-label="Search for a place"
            enterKeyHint="search"
            autoComplete="off"
          />
          {query && (
            <button
              type="button"
              className="icon-btn icon-btn--bare search__clear"
              onClick={() => {
                setQuery('')
                seq.current++
                service.cancel()
                setSearching(false)
                setOutcome({ kind: 'idle' })
                inputRef.current?.focus()
              }}
              aria-label="Clear the search"
            >
              <IconClose size={16} />
            </button>
          )}
          <span className="search__icon" aria-hidden="true">
            <IconSearch size={16} />
          </span>
        </div>

        <div className="search__status">
          {searching && <span className="spinner" />}
          <span>
            {searching
              ? 'Searching'
              : outcome.kind === 'results'
                ? `${outcome.results.length} ${outcome.results.length === 1 ? 'match' : 'matches'}`
                : outcome.kind === 'error'
                  ? 'Search failed'
                  : outcome.kind === 'empty'
                    ? 'No matches'
                    : 'Ready'}
          </span>
          <span className="spacer" />
          <span>{providerName}</span>
        </div>
      </div>

      <div className="search__pad">
        {outcome.kind === 'too-short' && (
          <p className="body-text body-text--dim" style={{ paddingTop: 'var(--sp-4)' }}>
            {MIN_QUERY_LENGTH} characters or more.
          </p>
        )}

        {outcome.kind === 'error' && (
          <div className="stack" style={{ paddingTop: 'var(--sp-4)' }}>
            <div className="notice notice--warn">
              <IconAlert size={16} />
              <span>{outcome.message}</span>
            </div>
            <div className="row">
              <button
                type="button"
                className="btn btn--quiet btn--grow"
                onClick={() => run(query, true)}
              >
                Try again
              </button>
              <button type="button" className="btn btn--quiet btn--grow" onClick={useTypedName}>
                <IconTarget size={16} />
                Drop a pin
              </button>
            </div>
          </div>
        )}

        {outcome.kind === 'empty' && (
          <div className="stack" style={{ paddingTop: 'var(--sp-4)' }}>
            <div className="notice">
              <IconPin size={16} />
              <span>
                Nothing matched “{outcome.query}”. Small guesthouses, viewpoints and meeting spots
                often aren’t listed — add it yourself and place it on the map.
              </span>
            </div>
            <button type="button" className="btn btn--primary btn--block" onClick={useTypedName}>
              Use “{outcome.query.length > 24 ? `${outcome.query.slice(0, 24)}…` : outcome.query}”
            </button>
          </div>
        )}

        {outcome.kind === 'results' && (
          <div style={{ paddingTop: 'var(--sp-2)' }}>
            {outcome.results.map((result, index) => (
              <button
                key={`${result.providerPlaceId ?? result.name}-${index}`}
                type="button"
                className="result"
                onClick={() => confirm(result)}
              >
                <span className="result__mark">
                  <IconPin size={15} />
                </span>
                <span style={{ minWidth: 0 }}>
                  <span className="result__name">{result.name}</span>
                  {result.formattedAddress && (
                    <span className="result__addr" style={{ display: 'block' }}>
                      {result.formattedAddress}
                    </span>
                  )}
                  {result.category && (
                    <span className="result__cat" style={{ display: 'block' }}>
                      {result.category}
                    </span>
                  )}
                </span>
              </button>
            ))}
          </div>
        )}

        {outcome.kind === 'idle' && !searching && (
          <>
            {savedPlaces.length > 0 && (
              <Section label="Already saved">
                {savedPlaces.map((place) => (
                  <button
                    key={place.id}
                    type="button"
                    className="result"
                    onClick={() =>
                      confirm({
                        name: place.name,
                        formattedAddress: place.address,
                        latitude: place.latitude,
                        longitude: place.longitude,
                        category: place.category,
                        providerPlaceId: place.providerPlaceId,
                        providerName: place.providerName ?? 'Manual',
                      })
                    }
                  >
                    <span className="result__mark">
                      <IconPin size={15} />
                    </span>
                    <span style={{ minWidth: 0 }}>
                      <span className="result__name">{place.name}</span>
                      {place.address && (
                        <span className="result__addr" style={{ display: 'block' }}>
                          {place.address}
                        </span>
                      )}
                    </span>
                  </button>
                ))}
              </Section>
            )}

            <Section label="No network?">
              <p className="body-text body-text--dim">
                Saved places above always work offline. You can also place a pin on the map by hand.
              </p>
              <button
                type="button"
                className="btn btn--quiet btn--block"
                style={{ marginTop: 'var(--sp-3)' }}
                onClick={useTypedName}
              >
                <IconTarget size={16} />
                Drop a pin on the map
              </button>
            </Section>
          </>
        )}
      </div>
    </Screen>
  )
}
