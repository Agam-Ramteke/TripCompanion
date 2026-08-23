/**
 * Location search service — spec §11.
 *
 * The single seam between the UI and whatever provider happens to be installed.
 * Screens call `search()` and get back one of four outcomes; they never see a
 * fetch, a provider name, or a rate-limit rule.
 *
 * Responsibilities kept here rather than in a provider, because they are true of
 * every provider:
 *   - debounce typing
 *   - cancel the in-flight request when a newer query arrives
 *   - enforce a minimum query length
 *   - enforce a minimum interval between upstream requests
 *   - fail on timeout instead of hanging
 *   - normalise every failure into a SearchFailure + message
 */

import type { SearchResultLocation } from '../../domain/types'
import {
  FAILURE_MESSAGE,
  SearchError,
  type LocationSearchProvider,
  type SearchFailure,
} from './LocationSearchProvider'

export const MIN_QUERY_LENGTH = 3
export const DEBOUNCE_MS = 350
export const TIMEOUT_MS = 8_000
/** Nominatim's published policy is ~1 request/second. Respect it for everyone. */
export const MIN_REQUEST_INTERVAL_MS = 1_100

export type SearchOutcome =
  | { kind: 'idle' }
  | { kind: 'too-short' }
  | { kind: 'results'; query: string; results: SearchResultLocation[] }
  | { kind: 'empty'; query: string }
  | { kind: 'error'; reason: SearchFailure; message: string }

export class LocationSearchService {
  private inFlight: AbortController | null = null
  private debounceTimer: ReturnType<typeof setTimeout> | null = null
  private lastRequestAt = 0

  constructor(private provider: LocationSearchProvider) {}

  get providerName(): string {
    return this.provider.name
  }

  /** Providers are swappable at runtime; nothing above this line cares. */
  setProvider(provider: LocationSearchProvider): void {
    this.provider = provider
  }

  /** Drop any pending or in-flight work — call this when the screen closes. */
  cancel(): void {
    if (this.debounceTimer != null) {
      clearTimeout(this.debounceTimer)
      this.debounceTimer = null
    }
    this.inFlight?.abort()
    this.inFlight = null
  }

  /**
   * Debounced search. Resolves with the outcome for `query`, or never resolves
   * if a newer query superseded it — callers get exactly one final answer per
   * keystroke burst.
   */
  search(query: string): Promise<SearchOutcome> {
    this.cancel()

    const trimmed = query.trim()
    if (trimmed.length === 0) return Promise.resolve({ kind: 'idle' })
    if (trimmed.length < MIN_QUERY_LENGTH) return Promise.resolve({ kind: 'too-short' })

    return new Promise<SearchOutcome>((resolve) => {
      this.debounceTimer = setTimeout(() => {
        this.debounceTimer = null
        void this.run(trimmed).then(resolve)
      }, DEBOUNCE_MS)
    })
  }

  /** Undebounced, for an explicit submit (Enter / the search button). */
  searchNow(query: string): Promise<SearchOutcome> {
    this.cancel()
    const trimmed = query.trim()
    if (trimmed.length === 0) return Promise.resolve({ kind: 'idle' })
    if (trimmed.length < MIN_QUERY_LENGTH) return Promise.resolve({ kind: 'too-short' })
    return this.run(trimmed)
  }

  private async run(query: string): Promise<SearchOutcome> {
    const controller = new AbortController()
    this.inFlight = controller

    // Space requests out rather than dropping them: a user typing quickly should
    // still get an answer, just slightly later.
    const sinceLast = Date.now() - this.lastRequestAt
    if (sinceLast < MIN_REQUEST_INTERVAL_MS) {
      const wait = MIN_REQUEST_INTERVAL_MS - sinceLast
      const spaced = await new Promise<boolean>((resolve) => {
        const t = setTimeout(() => resolve(true), wait)
        controller.signal.addEventListener('abort', () => {
          clearTimeout(t)
          resolve(false)
        })
      })
      if (!spaced) return { kind: 'idle' }
    }

    const timeout = setTimeout(() => controller.abort(new SearchError('timeout', 'timeout')), TIMEOUT_MS)
    this.lastRequestAt = Date.now()

    try {
      const results = await this.provider.search(query, controller.signal)
      if (controller.signal.aborted) return { kind: 'idle' }
      return results.length > 0 ? { kind: 'results', query, results } : { kind: 'empty', query }
    } catch (err) {
      // An abort carrying our timeout reason IS a failure; a plain abort is a
      // cancellation, and cancellations are silent.
      const reason = controller.signal.reason
      if (reason instanceof SearchError) return fail(reason.reason)
      if (err instanceof DOMException && err.name === 'AbortError') return { kind: 'idle' }
      if (err instanceof SearchError) return fail(err.reason)
      return fail('provider')
    } finally {
      clearTimeout(timeout)
      if (this.inFlight === controller) this.inFlight = null
    }
  }
}

const fail = (reason: SearchFailure): SearchOutcome => ({
  kind: 'error',
  reason,
  message: FAILURE_MESSAGE[reason],
})
