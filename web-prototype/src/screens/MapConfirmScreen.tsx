/**
 * Map confirmation — spec §12, §17.
 *
 * The last step before a place is attached to an event. A search result is a
 * guess; this screen is where the guess becomes a decision.
 *
 * The map is real: pan, zoom, and the confirmed coordinate is whatever sits
 * under the crosshair. The provider's original coordinate stays drawn as a small
 * ring, so a manual nudge is visible rather than silent — if you moved the pin
 * 200 m, you can see that you did.
 *
 * The name is editable here because geocoders return things like "Unnamed road"
 * and because a place typed by hand arrives with no name at all. Confirming
 * without a name is not allowed: an unnamed place is useless on a timeline.
 */

import { useMemo, useState } from 'react'
import type { SearchResultLocation } from '../domain/types'
import { useNav } from '../nav/router'
import { Screen } from '../ui/Screen'
import { MapView, formatCoords, type LatLon } from '../ui/MapView'
import { IconTarget } from '../ui/Icons'

/** Metres between two coordinates. Only used to describe a manual nudge. */
function metresBetween(a: LatLon, b: LatLon): number {
  const R = 6_371_000
  const toRad = (deg: number) => (deg * Math.PI) / 180
  const dLat = toRad(b.lat - a.lat)
  const dLon = toRad(b.lon - a.lon)
  const lat1 = toRad(a.lat)
  const lat2 = toRad(b.lat)
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)))
}

const formatDistance = (m: number) => (m < 1000 ? `${Math.round(m)} m` : `${(m / 1000).toFixed(1)} km`)

export function MapConfirmScreen({
  place,
  zoom,
}: {
  place: SearchResultLocation
  zoom?: number
}) {
  const nav = useNav()

  const origin = useMemo<LatLon>(
    () => ({ lat: place.latitude, lon: place.longitude }),
    [place.latitude, place.longitude],
  )
  const [picked, setPicked] = useState<LatLon>(origin)
  const [name, setName] = useState(place.name)

  // No provider id means no provider coordinate — there is nothing to compare a
  // nudge against, so no origin ring and no "moved" readout.
  const fromProvider = place.providerPlaceId != null
  const moved = fromProvider ? metresBetween(origin, picked) : 0

  const named = name.trim().length > 0

  const confirm = () => {
    if (!named) return
    const result: SearchResultLocation = {
      ...place,
      name: name.trim(),
      latitude: picked.lat,
      longitude: picked.lon,
    }
    nav.pop(result)
  }

  return (
    <Screen
      title="Confirm the spot"
      onBack={() => nav.pop()}
      backKind="close"
      fill
      footer={
        <>
          <button type="button" className="btn btn--quiet btn--grow" onClick={() => nav.pop()}>
            Cancel
          </button>
          <button
            type="button"
            className="btn btn--primary btn--grow"
            onClick={confirm}
            disabled={!named}
          >
            Use this spot
          </button>
        </>
      }
    >
      <MapView
        center={origin}
        zoom={zoom ?? 16}
        origin={fromProvider ? origin : null}
        onCenterChange={setPicked}
      >
        <div className="mapwrap__sheet">
          <input
            className="mapsheet__name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Name this place"
            aria-label="Place name"
          />

          {place.formattedAddress && (
            <p className="place__addr" style={{ marginTop: 'var(--sp-2)' }}>
              {place.formattedAddress}
            </p>
          )}

          <p className="mapwrap__coords">{formatCoords(picked)}</p>

          {moved >= 5 ? (
            <span className="mapsheet__moved">
              <IconTarget size={12} />
              Moved {formatDistance(moved)} from the search result
            </span>
          ) : (
            <p className="body-text body-text--dim" style={{ marginTop: 'var(--sp-2)' }}>
              {fromProvider
                ? 'Drag the map to move the pin.'
                : 'Drag the map until the crosshair is on the spot.'}
            </p>
          )}
        </div>
      </MapView>
    </Screen>
  )
}
