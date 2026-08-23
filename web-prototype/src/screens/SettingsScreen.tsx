/**
 * Settings — spec §23, §24, plus the prototype's own instrumentation.
 *
 * Two themes, switchable at runtime, with identical data and identical
 * functionality either side of the switch (§23). The difference is deliberately
 * structural — border weight, corner radius, shadow, type — not a repainted
 * palette, because a theme that only changes colours answers nothing about
 * hierarchy.
 *
 * The provider switch and the failure injector are prototype instruments. They
 * exist because §11's error states cannot be designed if they cannot be reached,
 * and they are labelled as such rather than dressed up as product features.
 */

import { useMemo, useState } from 'react'
import { FAULTS, FAULT_LABEL, useSearchConfig, type ProviderKind } from '../data/searchContext'
import { useStore, type ThemeName } from '../data/store'
import { useNav } from '../nav/router'
import { Screen, Section } from '../ui/Screen'
import { Segmented } from '../ui/Fields'
import { IconAlert, IconCheck } from '../ui/Icons'

const THEMES: { id: ThemeName; title: string; sub: string; swatch: [string, string] }[] = [
  {
    id: 'nothing',
    title: 'Monochrome',
    sub: 'Black ground, one red accent, hairline rules. Hierarchy comes from space and scale.',
    swatch: ['#000000', '#e5322b'],
  },
  {
    id: 'brutalist',
    title: 'Neo-brutalist',
    sub: 'Yellow and black, 3px rules, hard shadows. Hierarchy comes from weight and edges.',
    swatch: ['#ffe500', '#000000'],
  },
]

const PROVIDERS: { id: ProviderKind; title: string; sub: string }[] = [
  {
    id: 'mock',
    title: 'Offline sample',
    sub: 'A fixed set of places across five countries. No network, repeatable results.',
  },
  {
    id: 'osm',
    title: 'OpenStreetMap',
    sub: 'Live geocoding over the network. Rate limited to one request a second.',
  },
]

export function SettingsScreen() {
  const nav = useNav()
  const store = useStore()
  const { providerKind, setProviderKind, fault, setFault } = useSearchConfig()
  const [confirmingReset, setConfirmingReset] = useState(false)

  const counts = useMemo(
    () => ({
      trips: store.trips.length,
      events: store.events.length,
      places: store.locations.length,
      photos: store.photos.length,
    }),
    [store.trips, store.events, store.locations, store.photos],
  )

  return (
    <Screen title="Settings" onBack={() => nav.pop()}>
      <Section label="Theme">
        <div className="stack" role="radiogroup" aria-label="Theme">
          {THEMES.map((theme) => (
            <button
              key={theme.id}
              type="button"
              role="radio"
              aria-checked={store.theme === theme.id}
              className="radio-row"
              onClick={() => store.setTheme(theme.id)}
            >
              <span className="radio-row__dot" />
              <span className="radio-row__text">
                <span className="radio-row__title">{theme.title}</span>
                <span className="radio-row__sub">{theme.sub}</span>
              </span>
              <span className="theme-swatch" aria-hidden="true">
                <span style={{ background: theme.swatch[0] }} />
                <span style={{ background: theme.swatch[1] }} />
              </span>
            </button>
          ))}
        </div>
        <p className="body-text body-text--dim" style={{ marginTop: 'var(--sp-3)' }}>
          Both themes show the same data and do the same things. Nothing is hidden in either one.
        </p>
      </Section>

      <Section label="Place lookup">
        <div className="stack" role="radiogroup" aria-label="Place lookup provider">
          {PROVIDERS.map((provider) => (
            <button
              key={provider.id}
              type="button"
              role="radio"
              aria-checked={providerKind === provider.id}
              className="radio-row"
              onClick={() => setProviderKind(provider.id)}
            >
              <span className="radio-row__dot" />
              <span className="radio-row__text">
                <span className="radio-row__title">{provider.title}</span>
                <span className="radio-row__sub">{provider.sub}</span>
              </span>
            </button>
          ))}
        </div>
        <p className="body-text body-text--dim" style={{ marginTop: 'var(--sp-3)' }}>
          Search sits behind one interface, so the provider is a setting rather than an assumption.
          Saved places never need it.
        </p>
      </Section>

      <Section label="Prototype only · force a failure">
        <Segmented
          label="Next search will"
          options={FAULTS}
          value={fault}
          onChange={setFault}
          render={(f) => FAULT_LABEL[f]}
        />
        <p className="body-text body-text--dim" style={{ marginTop: 'var(--sp-3)' }}>
          Applies to the offline sample provider. Every failure state in the search screen is a
          designed screen, so each one has to be reachable on demand.
        </p>
      </Section>

      <Section label="Stored on this device">
        <div className="fact">
          <span>Trips</span>
          <span>{counts.trips}</span>
        </div>
        <div className="fact">
          <span>Events</span>
          <span>{counts.events}</span>
        </div>
        <div className="fact">
          <span>Places</span>
          <span>{counts.places}</span>
        </div>
        <div className="fact">
          <span>Photos</span>
          <span>{counts.photos}</span>
        </div>
        <p className="body-text body-text--dim" style={{ marginTop: 'var(--sp-3)' }}>
          Everything lives locally and survives a reload. Nothing is sent anywhere.
        </p>
      </Section>

      <Section label="Sample data">
        {confirmingReset ? (
          <div className="stack">
            <div className="notice notice--warn">
              <IconAlert size={16} />
              <span>
                This replaces everything with the two sample trips. Any event, place or photo you
                added is lost.
              </span>
            </div>
            <div className="row">
              <button
                type="button"
                className="btn btn--quiet btn--grow"
                onClick={() => setConfirmingReset(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn btn--accent btn--grow"
                onClick={() => {
                  store.reset()
                  setConfirmingReset(false)
                  nav.reset([{ name: 'trips' }])
                }}
              >
                <IconCheck size={16} />
                Reset
              </button>
            </div>
          </div>
        ) : (
          <button
            type="button"
            className="btn btn--quiet btn--block"
            onClick={() => setConfirmingReset(true)}
          >
            Reset to sample data
          </button>
        )}
      </Section>

      <Section label="Not in this app">
        {/* The non-goals are as much a part of the design as the screens. Keeping
            them visible is how the prototype stays honest about its boundary. */}
        <div className="out-of-scope">
          <span>Itinerary generation</span>
          <span>Cloud sync</span>
          <span>Accounts</span>
          <span>Bookings</span>
          <span>Live location</span>
          <span>Notifications</span>
          <span>Sharing</span>
          <span>Ads</span>
        </div>
        <p className="body-text body-text--dim" style={{ marginTop: 'var(--sp-3)' }}>
          A visual prototype for one trip planner, on sample data. Design decisions only — the
          shipping app is the Android build.
        </p>
      </Section>
    </Screen>
  )
}
