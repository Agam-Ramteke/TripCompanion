/**
 * Event detail — spec §18.
 *
 * One event, in full. The order is deliberate: what it is, when it is, where it
 * is, what we're doing, then the notes and the photo plan. Status is always the
 * computed status from the engine (§22) — the stored value is never read here.
 */

import { useMemo, useState } from 'react'
import {
  formatDateLong,
  formatDuration,
  formatRelative,
  formatTimeRange,
  minutesBetween,
} from '../domain/datetime'
import { computeEventStatus } from '../domain/tripStateEngine'
import type { PlannedPhoto, SearchResultLocation } from '../domain/types'
import { useNav } from '../nav/router'
import { useNow, useStore } from '../data/store'
import { EmptyState, Screen, Section } from '../ui/Screen'
import { StatusBadge, TypeChip, isTerminal } from '../ui/Chips'
import { PhotoStrip, PhotoViewer } from '../ui/Photos'
import { formatCoords } from '../ui/MapView'
import {
  IconAlert,
  IconCheck,
  IconEdit,
  IconPin,
  IconSkip,
  IconTrash,
  IconUndo,
} from '../ui/Icons'

export function EventDetailScreen({ eventId }: { eventId: number }) {
  const nav = useNav()
  const store = useStore()
  const now = useNow()
  const [viewing, setViewing] = useState<PlannedPhoto | null>(null)

  const stored = store.eventById(eventId)
  const place = store.locationById(stored?.locationId)
  const photos = store.photosForEvent(eventId)

  const event = useMemo(
    () => (stored ? { ...stored, status: computeEventStatus(stored, now) } : null),
    [stored, now],
  )

  if (!event) {
    return (
      <Screen title="Event" onBack={() => nav.pop()}>
        <EmptyState
          mark={<IconAlert size={24} />}
          headline="Event not found"
          body="It may have been deleted."
        />
      </Screen>
    )
  }

  const duration = minutesBetween(event.startTime, event.endTime)
  const terminal = isTerminal(event.status)

  const adjustPlace = async () => {
    if (!place) return
    const asResult: SearchResultLocation = {
      name: place.name,
      formattedAddress: place.address,
      latitude: place.latitude,
      longitude: place.longitude,
      category: place.category,
      providerPlaceId: place.providerPlaceId,
      providerName: place.providerName ?? 'Manual',
    }
    const adjusted = await nav.pushForResult<SearchResultLocation>({ name: 'map', place: asResult })
    if (adjusted) store.confirmLocation(adjusted, place.id)
  }

  return (
    <Screen
      title={store.selectedTrip?.name}
      onBack={() => nav.pop()}
      actions={
        <>
          <button
            type="button"
            className="icon-btn icon-btn--bare"
            onClick={() =>
              nav.push({ name: 'editor', tripId: event.tripId, eventId: event.id })
            }
            aria-label="Edit event"
          >
            <IconEdit />
          </button>
          <button
            type="button"
            className="icon-btn icon-btn--bare"
            onClick={() => {
              store.deleteEvent(event.id)
              nav.pop()
            }}
            aria-label="Delete event"
          >
            <IconTrash />
          </button>
        </>
      }
      footer={
        terminal ? (
          <button
            type="button"
            className="btn btn--quiet btn--block"
            onClick={() => store.setEventStatus(event.id, 'UPCOMING')}
          >
            <IconUndo size={16} />
            Undo — back to the schedule
          </button>
        ) : (
          <>
            <button
              type="button"
              className="btn btn--primary btn--grow"
              onClick={() => store.setEventStatus(event.id, 'COMPLETED')}
            >
              <IconCheck size={16} />
              Done
            </button>
            <button
              type="button"
              className="btn btn--quiet btn--grow"
              onClick={() => store.setEventStatus(event.id, 'SKIPPED')}
            >
              <IconSkip size={16} />
              Skip
            </button>
          </>
        )
      }
    >
      <div className="row" style={{ paddingTop: 'var(--sp-2)' }}>
        <StatusBadge status={event.status} />
        <TypeChip type={event.type} />
      </div>

      <h1 className={`title detail__title${terminal ? ' is-struck' : ''}`} style={{ marginTop: 'var(--sp-3)' }}>
        {event.title}
      </h1>

      <div className="detail__when">
        <div>
          <p className="detail__date">{formatDateLong(event.startTime)}</p>
          <p className="detail__range">{formatTimeRange(event.startTime, event.endTime)}</p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <p className="detail__dur">{formatDuration(duration)}</p>
          {!terminal && (
            <p className="detail__dur">
              {event.status === 'ACTIVE'
                ? `${formatDuration(Math.max(0, minutesBetween(now, event.endTime)))} left`
                : event.status === 'MISSED'
                  ? 'in the past'
                  : formatRelative(now, event.startTime)}
            </p>
          )}
        </div>
      </div>

      <div className="divider-line" />

      <Section label="Place">
        {place ? (
          <div className="card">
            <div className="place">
              <div style={{ minWidth: 0 }}>
                <p className="place__name">{place.name}</p>
                <p className="place__addr">{place.address}</p>
                <p className="mapwrap__coords">
                  {formatCoords({ lat: place.latitude, lon: place.longitude })}
                </p>
              </div>
              <button
                type="button"
                className="icon-btn"
                onClick={adjustPlace}
                aria-label="Show on the map"
              >
                <IconPin />
              </button>
            </div>
          </div>
        ) : (
          <div className="card card--flat">
            <p className="body-text body-text--dim">No place attached.</p>
            <button
              type="button"
              className="btn btn--quiet btn--sm"
              style={{ marginTop: 'var(--sp-3)' }}
              onClick={() => nav.push({ name: 'editor', tripId: event.tripId, eventId: event.id })}
            >
              <IconPin size={16} />
              Add a place
            </button>
          </div>
        )}
      </Section>

      {event.whatWeAreDoing.trim() && (
        <Section label="What we're doing">
          <p className="body-text">{event.whatWeAreDoing}</p>
        </Section>
      )}

      {event.notes.trim() && (
        <Section label="Notes">
          <p className="body-text body-text--dim">{event.notes}</p>
        </Section>
      )}

      <Section
        label="Photo plan"
        action={
          <button
            type="button"
            className="btn btn--quiet btn--sm"
            onClick={() => nav.push({ name: 'photos', eventId: event.id })}
          >
            {photos.length > 0 ? 'Manage' : 'Add'}
          </button>
        }
      >
        {photos.length > 0 ? (
          <PhotoStrip photos={photos} onOpen={setViewing} />
        ) : (
          <p className="body-text body-text--dim">
            No photos planned. Add references you want to recreate here.
          </p>
        )}
      </Section>

      {viewing && (
        <PhotoViewer
          photos={photos}
          startId={viewing.id}
          onClose={() => setViewing(null)}
          onDelete={(photo) => store.deletePhoto(photo.id)}
        />
      )}
    </Screen>
  )
}
