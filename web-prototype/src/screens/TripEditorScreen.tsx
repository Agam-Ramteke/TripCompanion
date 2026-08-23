/**
 * Trip editor — spec §25, §9.
 *
 * A trip is a name and a date range. That is the whole model, and keeping it that
 * small is what lets the same app hold a four-day city trip and a five-day
 * mountain loop with no code in between (§4).
 *
 * Dates are two separate date controls, never a range picker returning a
 * timestamp. Deleting a trip takes its events and photos with it, and says so
 * before it does it.
 */

import { useMemo, useState } from 'react'
import { daysBetween, formatDateRange } from '../domain/datetime'
import { TRIP_STATUSES, type IsoDate, type Trip, type TripStatus } from '../domain/types'
import { useNav } from '../nav/router'
import { useNow, useStore } from '../data/store'
import { Screen, Section } from '../ui/Screen'
import { DateField, Segmented, TextField } from '../ui/Fields'
import { tripStatusLabel } from '../ui/Chips'
import { IconAlert, IconTrash } from '../ui/Icons'

interface Draft {
  name: string
  startDate: IsoDate
  endDate: IsoDate
  status: TripStatus
}

export function TripEditorScreen({ tripId }: { tripId?: number }) {
  const nav = useNav()
  const store = useStore()
  const now = useNow()
  const existing = useMemo(
    () => store.trips.find((t) => t.id === tripId) ?? null,
    [store.trips, tripId],
  )

  const today = now.split('T')[0]

  const [draft, setDraft] = useState<Draft>(() =>
    existing
      ? {
          name: existing.name,
          startDate: existing.startDate,
          endDate: existing.endDate,
          status: existing.status,
        }
      : { name: '', startDate: today, endDate: today, status: 'PLANNING' },
  )
  const [touched, setTouched] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const patch = (changes: Partial<Draft>) => setDraft((d) => ({ ...d, ...changes }))

  // Inclusive: a trip that starts and ends on the same date is one day long.
  const days = draft.endDate >= draft.startDate ? daysBetween(draft.startDate, draft.endDate) + 1 : 0

  const errors = useMemo(() => {
    const out: { name?: string; endDate?: string } = {}
    if (!draft.name.trim()) out.name = 'A trip needs a name.'
    if (draft.endDate < draft.startDate) out.endDate = 'The last day cannot be before the first.'
    return out
  }, [draft.name, draft.startDate, draft.endDate])

  const valid = !errors.name && !errors.endDate

  // Events that would fall outside the new range: a warning, never a block.
  // Shortening a trip is a legitimate edit and the Timeline still shows them.
  const strandedCount = useMemo(() => {
    if (!existing) return 0
    return store.eventsForTrip(existing.id).filter((e) => {
      const date = e.startTime.split('T')[0]
      return date < draft.startDate || date > draft.endDate
    }).length
  }, [existing, store, draft.startDate, draft.endDate])

  const save = () => {
    setTouched(true)
    if (!valid) return
    const trip: Omit<Trip, 'id'> & { id?: number } = {
      id: existing?.id,
      name: draft.name.trim(),
      startDate: draft.startDate,
      endDate: draft.endDate,
      status: draft.status,
    }
    store.saveTrip(trip)
    nav.pop()
  }

  const eventCount = existing ? store.eventsForTrip(existing.id).length : 0
  const photoCount = existing
    ? store
        .eventsForTrip(existing.id)
        .reduce((sum, e) => sum + store.photosForEvent(e.id).length, 0)
    : 0

  return (
    <Screen
      title={existing ? 'Edit trip' : 'New trip'}
      onBack={() => nav.pop()}
      backKind="close"
      footer={
        <>
          <button type="button" className="btn btn--quiet btn--grow" onClick={() => nav.pop()}>
            Cancel
          </button>
          <button type="button" className="btn btn--primary btn--grow" onClick={save}>
            Save
          </button>
        </>
      }
    >
      <div className="stack" style={{ paddingTop: 'var(--sp-4)', gap: 'var(--sp-4)' }}>
        <TextField
          label="Name"
          value={draft.name}
          onChange={(name) => patch({ name })}
          placeholder="Spiti Valley Loop"
          error={touched ? errors.name : null}
        />

        <div className="field-grid">
          <DateField
            label="First day"
            value={draft.startDate}
            onChange={(startDate) =>
              patch({
                startDate,
                // Keep the range coherent while typing rather than flashing an error.
                endDate: draft.endDate < startDate ? startDate : draft.endDate,
              })
            }
          />
          <DateField
            label="Last day"
            value={draft.endDate}
            onChange={(endDate) => patch({ endDate })}
            min={draft.startDate}
            error={touched ? errors.endDate : null}
          />
        </div>

        <div className="card card--flat">
          <p className="eyebrow">Range</p>
          <p className="title title--sm" style={{ marginTop: 'var(--sp-2)' }}>
            {days > 0 ? formatDateRange(draft.startDate, draft.endDate) : '—'}
          </p>
          <p className="body-text body-text--dim" style={{ marginTop: 'var(--sp-1)' }}>
            {days > 0 ? `${days} ${days === 1 ? 'day' : 'days'}` : 'Not a valid range yet'}
          </p>
        </div>

        <Segmented
          label="Status"
          options={TRIP_STATUSES}
          value={draft.status}
          onChange={(status) => patch({ status })}
          render={tripStatusLabel}
        />
      </div>

      {strandedCount > 0 && (
        <Section>
          <div className="notice notice--warn">
            <IconAlert size={16} />
            <span>
              {strandedCount} {strandedCount === 1 ? 'event falls' : 'events fall'} outside these
              dates. Nothing is deleted — they stay on the timeline under their own day.
            </span>
          </div>
        </Section>
      )}

      {existing && (
        <Section label="Delete">
          {confirmingDelete ? (
            <div className="stack">
              <div className="notice notice--warn">
                <IconAlert size={16} />
                <span>
                  Deleting “{existing.name}” also deletes {eventCount}{' '}
                  {eventCount === 1 ? 'event' : 'events'} and {photoCount}{' '}
                  {photoCount === 1 ? 'photo' : 'photos'}. This cannot be undone.
                </span>
              </div>
              <div className="row">
                <button
                  type="button"
                  className="btn btn--quiet btn--grow"
                  onClick={() => setConfirmingDelete(false)}
                >
                  Keep it
                </button>
                <button
                  type="button"
                  className="btn btn--accent btn--grow"
                  onClick={() => {
                    store.deleteTrip(existing.id)
                    nav.reset([{ name: 'trips' }])
                  }}
                >
                  <IconTrash size={16} />
                  Delete trip
                </button>
              </div>
            </div>
          ) : (
            <button
              type="button"
              className="btn btn--quiet btn--block"
              onClick={() => setConfirmingDelete(true)}
            >
              <IconTrash size={16} />
              Delete this trip
            </button>
          )}
        </Section>
      )}
    </Screen>
  )
}
