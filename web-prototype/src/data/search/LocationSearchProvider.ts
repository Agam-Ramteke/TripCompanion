/**
 * Location search provider contract — spec §11.
 *
 *     LocationSearchService
 *             |
 *             v
 *     LocationSearchProvider
 *
 * The UI and the domain layer never import a provider. They talk to the service,
 * the service talks to whichever provider is installed. Swapping OpenStreetMap
 * for anything else must not touch a screen.
 */

import type { SearchResultLocation } from '../../domain/types'

export type SearchFailure =
  | 'network'
  | 'timeout'
  | 'rate-limited'
  | 'provider'
  | 'malformed'
  | 'too-short'

export class SearchError extends Error {
  constructor(
    readonly reason: SearchFailure,
    message: string,
  ) {
    super(message)
    this.name = 'SearchError'
  }
}

/** Human-readable, actionable copy for each failure. Never blames the user. */
export const FAILURE_MESSAGE: Record<SearchFailure, string> = {
  network: 'No connection. Saved places still work — search needs the network.',
  timeout: 'Search took too long. Try again, or drop a pin on the map instead.',
  'rate-limited': 'Searching too fast. Waiting a moment before the next request.',
  provider: 'The place service is unavailable. Try again shortly.',
  malformed: 'The place service returned something unreadable.',
  'too-short': 'Keep typing to search.',
}

export interface LocationSearchProvider {
  readonly name: string
  /**
   * Resolve a free-text query to place candidates.
   * Must reject with a SearchError, and must honour `signal`.
   */
  search(query: string, signal: AbortSignal): Promise<SearchResultLocation[]>
}
