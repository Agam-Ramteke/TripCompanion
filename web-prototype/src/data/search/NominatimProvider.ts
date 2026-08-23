/**
 * OpenStreetMap / Nominatim provider — the web counterpart of
 * OpenStreetMapLocationSearchProvider.kt.
 *
 * Nominatim's usage policy caps clients at roughly one request per second; the
 * service layer enforces that, not this file.
 */

import type { SearchResultLocation } from '../../domain/types'
import { SearchError, type LocationSearchProvider } from './LocationSearchProvider'

const ENDPOINT = 'https://nominatim.openstreetmap.org/search'

interface NominatimRow {
  display_name?: unknown
  name?: unknown
  lat?: unknown
  lon?: unknown
  type?: unknown
  class?: unknown
  osm_type?: unknown
  osm_id?: unknown
  address?: Record<string, unknown>
}

/** Nominatim returns lat/lon as strings; anything unparseable is malformed. */
function coord(value: unknown): number {
  const n = typeof value === 'string' ? Number.parseFloat(value) : Number(value)
  if (!Number.isFinite(n)) throw new SearchError('malformed', 'Coordinate was not a number')
  return n
}

/**
 * Prefer the place's own short name over the full comma-separated display name,
 * so a result reads "City Palace" rather than
 * "City Palace, City Palace Road, Udaipur, Rajasthan, 313001, India".
 */
function shortName(row: NominatimRow): string {
  if (typeof row.name === 'string' && row.name.trim()) return row.name.trim()
  const display = typeof row.display_name === 'string' ? row.display_name : ''
  return display.split(',')[0]?.trim() || 'Unnamed place'
}

function toResult(row: NominatimRow): SearchResultLocation {
  const display = typeof row.display_name === 'string' ? row.display_name : ''
  const name = shortName(row)
  // Drop the leading name from the address so it isn't printed twice.
  const address = display.startsWith(name)
    ? display.slice(name.length).replace(/^[,\s]+/, '')
    : display

  const category =
    typeof row.type === 'string' && row.type !== 'yes'
      ? row.type.replace(/_/g, ' ')
      : typeof row.class === 'string'
        ? row.class.replace(/_/g, ' ')
        : ''

  const placeId =
    typeof row.osm_type === 'string' && (typeof row.osm_id === 'number' || typeof row.osm_id === 'string')
      ? `osm:${row.osm_type}:${row.osm_id}`
      : null

  return {
    name,
    formattedAddress: address,
    latitude: coord(row.lat),
    longitude: coord(row.lon),
    category,
    providerPlaceId: placeId,
    providerName: 'OpenStreetMap',
  }
}

export class NominatimProvider implements LocationSearchProvider {
  readonly name = 'OpenStreetMap'

  async search(query: string, signal: AbortSignal): Promise<SearchResultLocation[]> {
    const url = new URL(ENDPOINT)
    url.searchParams.set('q', query)
    url.searchParams.set('format', 'jsonv2')
    url.searchParams.set('addressdetails', '1')
    url.searchParams.set('limit', '12')

    let response: Response
    try {
      response = await fetch(url, { signal, headers: { Accept: 'application/json' } })
    } catch (err) {
      // An abort is a cancellation, not a failure — let it propagate untouched.
      if (err instanceof DOMException && err.name === 'AbortError') throw err
      throw new SearchError('network', 'Could not reach the place service')
    }

    if (response.status === 429) {
      throw new SearchError('rate-limited', 'Place service rate limit reached')
    }
    if (!response.ok) {
      throw new SearchError('provider', `Place service returned ${response.status}`)
    }

    let payload: unknown
    try {
      payload = await response.json()
    } catch {
      throw new SearchError('malformed', 'Place service response was not JSON')
    }
    if (!Array.isArray(payload)) {
      throw new SearchError('malformed', 'Place service response was not a list')
    }

    // Skip individual rows we cannot read rather than failing the whole search.
    const results: SearchResultLocation[] = []
    for (const row of payload) {
      try {
        if (row && typeof row === 'object') results.push(toResult(row as NominatimRow))
      } catch {
        continue
      }
    }
    return results
  }
}
