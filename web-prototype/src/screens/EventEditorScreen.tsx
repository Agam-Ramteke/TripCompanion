/**
 * Event editor — spec §9, §14.
 *
 * Date, start and end are three separate controls. There is no single
 * "date-time" field anywhere, which is what makes the forbidden
 * "2026-08-21 00:00:00.517484" format unreachable rather than merely hidden.
 *
 * The overnight toggle exists because "end must be after start" and "the train
 * leaves at 22:00 and arrives at 06:00" are both true. Without it the rule turns
 * into a dead end for a real, common event.
 */

import { useMemo, useState } from 'react'
import { addDays, combine, dateOf, formatDuration, minutesBetween, timeOf } from '../domain/datetime'
import { EVENT_TYPES, type Event, type EventType, type IsoDate, type SearchResultLocation } from '../domain/types'
import { useNav } from '../nav/router'
import { useStore } from '../data/store'
import { Screen, Section } from '../ui/Screen'
import { DateField, Segmented, TextArea, TextField, TimeField } from '../ui/Fields'
import { typeLabel } from '../ui/Chips'
import { IconPin, IconSearch, IconTrash } from '../ui/Icons'

interface Draft {
  type: EventType
  title: string
  date: IsoDate
  startTime: string
  endTime: string
  overnight: boolean
  locationId: number | null
  whatWeAreDoing: string
  notes: string
}

export function EventEditorScreen({
  tripId,
  eventId,
  date,
}: {
  tripId: number
  eventId?: number
  date?: IsoDate
}) {
  const nav = useNav()
  const store = useStore()
  const existing = store.eventById(eventId)
  const trip = store.trips.find((t) => t.id === tripId) ?? null

  const [draft, setDraft] = useState<Draft>(() =>
    existing
      ? {
          type: existing.type,
          title: existing.title,
          date: dateOf(existing.startTime),
          startTime: timeOf(existing.startTime),
          endTime: timeOf(existing.endTime),
          overnight: dateOf(existing.endTime) !== dateOf(existing.startTime),
          locationId: existing.locationId,
          whatWeAreDoing: existing.whatWeAreDoing,
          notes: existing.notes,
        }
      : {
          type: 'VISIT',
          title: '',
          date: date ?? trip?.startDate ?? '',
          startTime: '09:00',
          endTime: '10:30',
          overnight: false,
          locationId: null,
          whatWeAreDoing: '',
          notes: '',
        },
  )
  const [touched, setTouched] = useState(false)

  const patch = (changes: Partial<Draft>) => setDraft((d) => ({ ...d, ...changes }))

  const place = store.locationById(draft.locationId)

  const start = combine(draft.date, draft.startTime)
  const end = combine(draft.overnight ? addDays(draft.date, 1) : draft.date, draft.endTime)
  const duration = minutesBetween(start, end)

  const errors = useMemo(() => {
    const out: { title?: string; end?: string } = {}
    if (!draft.title.trim()) out.title = 'An event needs a name to appear on the timeline.'
    // §9: end must be after start. Same-clock-time is not a duration.
    if (duration <= 0) {
      out.end = draft.overnight
        ? 'The end is still not after the start.'
        : 'The end must be after the start. If it runs overnight, turn on “Ends next day”.'
    }
    return out
  }, [draft.title, draft.overnight, duration])

  const valid = !errors.title && !errors.end

  const pickPlace = async () => {
    const result = await nav.pushForResult<SearchResultLocation>({ name: 'search' })
    if (result) patch({ locationId: store.confirmLocation(result) })
  }

  const save = () => {
    setTouched(true)
    if (!valid) return
    const event: Omit<Event, 'id'> & { id?: number } = {
      id: existing?.id,
      tripId,
      type: draft.type,
      title: draft.title.trim(),
      startTime: start,
      endTime: end,
      locationId: draft.locationId,
      whatWeAreDoing: draft.whatWeAreDoing.trim(),
      notes: draft.notes.trim(),
      // A manual status is never reset by an edit; a new event starts scheduled.
      status: existing?.status ?? 'UPCOMING',
      order: existing?.order ?? 0,
    }
    store.saveEvent(event)
    nav.pop()
  }

  return (
    <Screen
      title={existing ? 'Edit event' : 'New event'}
      onBack={() => nav.pop()}
      backKind="close"
      actions={
        existing ? (
          <button
            type="button"
            className="icon-btn icon-btn--bare"
            onClick={() => {
              store.deleteEvent(existing.id)
              nav.pop()
            }}
            aria-label="Delete event"
          >
            <IconTrash />
          </button>
        ) : null
      }
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
      <div className="stack" style={{ paddingTop: 'var(--sp-2)', gap: 'var(--sp-4)' }}>
        <Segmented
          label="Type"
          options={EVENT_TYPES}
          value={draft.type}
          onChange={(type) => patch({ type })}
          render={typeLabel}
        />

        <TextField
          label="Name"
          value={draft.title}
          onChange={(title) => patch({ title })}
          placeholder="City Palace"
          error={touched ? errors.title : null}
        />

        <DateField label="Date" value={draft.date} onChange={(d) => patch({ date: d })} />

        <div className="field-grid">
          <TimeField
            label="Starts"
            value={draft.startTime}
            onChange={(startTime) => patch({ startTime })}
          />
          <TimeField
            label="Ends"
            value={draft.endTime}
            onChange={(endTime) => patch({ endTime })}
            error={touched ? errors.end : null}
          />
        </div>

        <button
          type="button"
          className="list-row"
          aria-pressed={draft.overnight}
          onClick={() => patch({ overnight: !draft.overnight })}
        >
          <span className="radio-row__dot" style={{ borderRadius: 3 }}>
            {draft.overnight && (
              <span style={{ width: 10, height: 10, background: 'var(--accent)' }} />
            )}
          </span>
          <span className="list-row__body">
            <span className="list-row__label">Ends next day</span>
            <span className="list-row__value">
              {duration > 0 ? formatDuration(duration) : 'Not a valid duration yet'}
            </span>
          </span>
        </button>
      </div>

      <Section label="Place">
        {place ? (
          <div className="card">
            <div className="place">
              <div style={{ minWidth: 0 }}>
                <p className="place__name">{place.name}</p>
                <p className="place__addr">{place.address}</p>
              </div>
              <button type="button" className="icon-btn" onClick={pickPlace} aria-label="Change place">
                <IconSearch />
              </button>
            </div>
            <button
              type="button"
              className="btn btn--quiet btn--sm"
              style={{ marginTop: 'var(--sp-3)' }}
              onClick={() => patch({ locationId: null })}
            >
              Remove place
            </button>
          </div>
        ) : (
          <button type="button" className="btn btn--quiet btn--block" onClick={pickPlace}>
            <IconPin size={16} />
            Find a place
          </button>
        )}
      </Section>

      <Section label="Details">
        <div className="stack" style={{ gap: 'var(--sp-4)' }}>
          <TextArea
            label="What we're doing"
            value={draft.whatWeAreDoing}
            onChange={(whatWeAreDoing) => patch({ whatWeAreDoing })}
            placeholder="Explore the palace, visit the museum, and take architecture/couple photos."
            hint="The plan in your own words. This is the line you'll read on the day."
          />
          <TextArea
            label="Notes"
            value={draft.notes}
            onChange={(notes) => patch({ notes })}
            placeholder="Tickets, seat numbers, opening hours"
          />
        </div>
      </Section>
    </Screen>
  )
}
