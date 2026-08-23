/**
 * Photo plan — spec §15, §16.
 *
 * Image-first. A tile is the image plus, at most, a short optional title.
 * Deliberately absent: descriptions, pose notes, framing notes, shot
 * specifications, URLs, provider metadata. A photo with no text at all is valid,
 * and this component must render that case without looking broken.
 */

import { useEffect, useState } from 'react'
import type { PlannedPhoto } from '../domain/types'
import { IconClose, IconPlus, IconTrash } from './Icons'

export function PhotoTile({
  photo,
  onOpen,
}: {
  photo: PlannedPhoto
  onOpen?: (photo: PlannedPhoto) => void
}) {
  return (
    <button type="button" className="photo" onClick={() => onOpen?.(photo)}>
      <span className="photo__frame">
        {photo.imageUri ? (
          <img className="photo__img" src={photo.imageUri} alt={photo.title || 'Planned photo'} />
        ) : null}
      </span>
      {/* No title is a valid photo — render nothing rather than a placeholder. */}
      {photo.title ? <span className="photo__cap">{photo.title}</span> : null}
    </button>
  )
}

export function AddPhotoTile({ onAdd, label = 'Add' }: { onAdd: () => void; label?: string }) {
  return (
    <button type="button" className="photo photo--add" onClick={onAdd}>
      <span className="photo__frame">
        <IconPlus />
      </span>
      <span className="photo__cap">{label}</span>
    </button>
  )
}

export function PhotoStrip({
  photos,
  onOpen,
  onAdd,
  grid = false,
}: {
  photos: readonly PlannedPhoto[]
  onOpen?: (photo: PlannedPhoto) => void
  onAdd?: () => void
  grid?: boolean
}) {
  return (
    <div className={`photos${grid ? ' photos--grid' : ''}`}>
      {photos.map((photo) => (
        <PhotoTile key={photo.id} photo={photo} onOpen={onOpen} />
      ))}
      {onAdd && <AddPhotoTile onAdd={onAdd} />}
    </div>
  )
}

/**
 * Fullscreen viewer. The image gets the whole screen; controls are the caption,
 * delete, and close. Nothing else belongs here.
 */
export function PhotoViewer({
  photos,
  startId,
  onClose,
  onDelete,
}: {
  photos: readonly PlannedPhoto[]
  startId: number
  onClose: () => void
  onDelete?: (photo: PlannedPhoto) => void
}) {
  const [index, setIndex] = useState(() => Math.max(0, photos.findIndex((p) => p.id === startId)))
  const photo = photos[Math.min(index, photos.length - 1)]

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
      if (e.key === 'ArrowRight') setIndex((i) => Math.min(photos.length - 1, i + 1))
      if (e.key === 'ArrowLeft') setIndex((i) => Math.max(0, i - 1))
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, photos.length])

  // The last photo can be deleted from in here; leaving an empty viewer open
  // would be a dead end.
  useEffect(() => {
    if (photos.length === 0) onClose()
  }, [photos.length, onClose])

  if (!photo) return null

  return (
    <div className="viewer" role="dialog" aria-modal="true">
      <div className="viewer__bar">
        <button type="button" className="icon-btn icon-btn--bare" onClick={onClose} aria-label="Close">
          <IconClose />
        </button>
        <span className="viewer__cap">{photo.title}</span>
        <span className="clock" style={{ fontSize: '0.6875rem', opacity: 0.6 }}>
          {index + 1}/{photos.length}
        </span>
        {onDelete && (
          <button
            type="button"
            className="icon-btn icon-btn--bare"
            onClick={() => onDelete(photo)}
            aria-label="Delete photo"
          >
            <IconTrash />
          </button>
        )}
      </div>
      <div
        className="viewer__stage"
        onClick={() => setIndex((i) => (i + 1 < photos.length ? i + 1 : 0))}
      >
        {photo.imageUri && <img className="viewer__img" src={photo.imageUri} alt={photo.title} />}
      </div>
    </div>
  )
}
