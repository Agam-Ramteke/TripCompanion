/**
 * Interactive map — spec §12.
 *
 * A real Leaflet map with OpenStreetMap tiles: pan, pinch/scroll zoom, centring
 * on a result, a marker for the provider's coordinate, and manual adjustment.
 * Explicitly not a static image and not decorative.
 *
 * The confirmed coordinate is the map centre, under a fixed crosshair. That is
 * the one interaction model where what you confirm is exactly what you see —
 * dragging a pin means the pin can end up under your thumb.
 */

import { useEffect, useRef } from 'react'
import L from 'leaflet'

export interface LatLon {
  lat: number
  lon: number
}

interface MapViewProps {
  /** Where the map opens. Changing this re-centres it. */
  center: LatLon
  zoom?: number
  /** The provider's original coordinate, drawn so a manual nudge is visible. */
  origin?: LatLon | null
  onCenterChange?: (position: LatLon) => void
  children?: React.ReactNode
}

const originIcon = L.divIcon({
  className: '',
  html: '<span class="mapmark"></span>',
  iconSize: [14, 14],
  iconAnchor: [7, 7],
})

export function MapView({ center, zoom = 16, origin, onCenterChange, children }: MapViewProps) {
  const hostRef = useRef<HTMLDivElement | null>(null)
  const mapRef = useRef<L.Map | null>(null)
  const markerRef = useRef<L.Marker | null>(null)
  // Kept in a ref so the map is created once and never re-created on re-render.
  const onCenterChangeRef = useRef(onCenterChange)
  onCenterChangeRef.current = onCenterChange

  useEffect(() => {
    const host = hostRef.current
    if (!host || mapRef.current) return

    const map = L.map(host, {
      center: [center.lat, center.lon],
      zoom,
      zoomControl: false,
      // Leaflet's own attribution control sits at the bottom right, underneath
      // the confirmation sheet. The credit is rendered below instead, top left,
      // where nothing can cover it.
      attributionControl: false,
    })

    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
    }).addTo(map)

    map.on('moveend', () => {
      const c = map.getCenter()
      onCenterChangeRef.current?.({ lat: c.lat, lon: c.lng })
    })

    mapRef.current = map

    // The container is often laid out after the first paint; without this the
    // map renders into a zero-height box and looks broken.
    const raf = requestAnimationFrame(() => map.invalidateSize())
    const observer = new ResizeObserver(() => map.invalidateSize())
    observer.observe(host)

    return () => {
      cancelAnimationFrame(raf)
      observer.disconnect()
      map.remove()
      mapRef.current = null
      markerRef.current = null
    }
    // Deliberately mount-only: later centre changes are handled below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Re-centre when the caller points somewhere new (a different search result).
  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    const current = map.getCenter()
    const moved = Math.abs(current.lat - center.lat) > 1e-6 || Math.abs(current.lng - center.lon) > 1e-6
    if (moved) map.setView([center.lat, center.lon], map.getZoom(), { animate: true })
  }, [center.lat, center.lon])

  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    if (!origin) {
      markerRef.current?.remove()
      markerRef.current = null
      return
    }
    if (markerRef.current) markerRef.current.setLatLng([origin.lat, origin.lon])
    else markerRef.current = L.marker([origin.lat, origin.lon], { icon: originIcon }).addTo(map)
  }, [origin])

  return (
    <div className="mapwrap">
      <div className="mapwrap__canvas" ref={hostRef} />
      <a
        className="mapwrap__credit"
        href="https://www.openstreetmap.org/copyright"
        target="_blank"
        rel="noreferrer"
      >
        © OpenStreetMap
      </a>
      <div className="mapwrap__reticle">
        <span className="mapwrap__ring" />
      </div>
      <div className="mapwrap__zoom">
        <button
          type="button"
          className="icon-btn"
          onClick={() => mapRef.current?.zoomIn()}
          aria-label="Zoom in"
        >
          +
        </button>
        <button
          type="button"
          className="icon-btn"
          onClick={() => mapRef.current?.zoomOut()}
          aria-label="Zoom out"
        >
          −
        </button>
        <button
          type="button"
          className="icon-btn"
          onClick={() => mapRef.current?.setView([center.lat, center.lon], 16, { animate: true })}
          aria-label="Back to the search result"
        >
          ⌖
        </button>
      </div>
      {children}
    </div>
  )
}

/** 5 decimal places is about a metre — more digits would be false precision. */
export const formatCoords = (p: LatLon) => `${p.lat.toFixed(5)}, ${p.lon.toFixed(5)}`
