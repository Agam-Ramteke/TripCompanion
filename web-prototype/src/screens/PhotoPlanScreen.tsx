/**
 * Photo plan — spec §15, §16.
 *
 * The plan is the images. Everything else is optional, and most of it is
 * forbidden: no descriptions, no pose or framing notes, no shot specifications,
 * no URLs, no provider metadata. A photo saved with no text at all is a complete,
 * valid photo — which is why "Add" goes straight from the picker to the grid with
 * no dialog in between. Titles are added afterwards, by choice, or never.
 *
 * Two views, in priority order:
 *   Board — the grid. This is the screen you look at on the day.
 *   Titles — a list, only for naming or removing. Deliberately secondary.
 */

import { useState } from 'react'
import type { PlannedPhoto } from '../domain/types'
import { pickImage } from '../data/images'
import { useStore } from '../data/store'
import { useNav } from '../nav/router'
import { EmptyState, Screen } from '../ui/Screen'
import { PhotoStrip, PhotoViewer } from '../ui/Photos'
import { IconCamera, IconGrid, IconList, IconPlus, IconTrash } from '../ui/Icons'

type View = 'board' | 'titles'

export function PhotoPlanScreen({ eventId }: { eventId: number }) {
  const nav = useNav()
  const store = useStore()
  const event = store.eventById(eventId)
  const photos = store.photosForEvent(eventId)

  const [view, setView] = useState<View>('board')
  const [viewing, setViewing] = useState<PlannedPhoto | null>(null)
  const [busy, setBusy] = useState(false)

  const add = async () => {
    setBusy(true)
    try {
      const imageUri = await pickImage()
      if (!imageUri) return
      // No title, no prompt. The image is the plan (§15).
      store.savePhoto({
        eventId,
        title: '',
        imageUri,
        order: photos.length,
      })
      setView('board')
    } finally {
      setBusy(false)
    }
  }

  if (!event) {
    return (
      <Screen title="Photo plan" onBack={() => nav.pop()}>
        <EmptyState
          mark={<IconCamera size={24} />}
          headline="Event not found"
          body="It may have been deleted."
        />
      </Screen>
    )
  }

  return (
    <Screen
      title={event.title}
      onBack={() => nav.pop()}
      actions={
        photos.length > 0 ? (
          <button
            type="button"
            className="icon-btn icon-btn--bare"
            onClick={() => setView(view === 'board' ? 'titles' : 'board')}
            aria-label={view === 'board' ? 'Edit titles' : 'Back to the board'}
          >
            {view === 'board' ? <IconList /> : <IconGrid />}
          </button>
        ) : null
      }
      footer={
        <button type="button" className="btn btn--primary btn--block" onClick={add} disabled={busy}>
          <IconPlus size={16} />
          {busy ? 'Choosing…' : 'Add a photo'}
        </button>
      }
    >
      {photos.length === 0 ? (
        <EmptyState
          mark={<IconCamera size={24} />}
          headline="No photos planned"
          body="Add the shots you want to come home with. A reference image is enough — no title needed."
        />
      ) : view === 'board' ? (
        <>
          <p className="body-text body-text--dim" style={{ padding: 'var(--sp-4) 0 var(--sp-3)' }}>
            {photos.length} {photos.length === 1 ? 'shot' : 'shots'} planned. Tap one to fill the
            screen.
          </p>
          <PhotoStrip photos={photos} grid onOpen={setViewing} onAdd={add} />
        </>
      ) : (
        <div style={{ paddingTop: 'var(--sp-4)' }}>
          <p className="body-text body-text--dim" style={{ paddingBottom: 'var(--sp-3)' }}>
            A title is optional. Leave it empty and the image speaks for itself.
          </p>
          {photos.map((photo) => (
            <div className="photo-row" key={photo.id}>
              {photo.imageUri ? (
                <img className="photo-row__thumb" src={photo.imageUri} alt="" />
              ) : (
                <span className="photo-row__thumb" />
              )}
              <input
                className="photo-row__input"
                value={photo.title}
                onChange={(e) => store.savePhoto({ ...photo, title: e.target.value })}
                placeholder="Untitled"
                aria-label="Photo title"
                maxLength={60}
              />
              <button
                type="button"
                className="icon-btn icon-btn--bare"
                onClick={() => store.deletePhoto(photo.id)}
                aria-label="Delete photo"
              >
                <IconTrash />
              </button>
            </div>
          ))}
        </div>
      )}

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
