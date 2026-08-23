/**
 * Picking an image — the prototype's stand-in for §16.
 *
 * On Android the picked image is copied into app-private storage and referenced
 * by path. Here it is downscaled and inlined as a data URI, which keeps the
 * store self-contained and survives a reload. The downscale is not cosmetic:
 * localStorage has a few megabytes to work with, and a photo plan is a board of
 * references, not an archive.
 */

const MAX_EDGE = 900
const QUALITY = 0.82

export function pickImage(): Promise<string | null> {
  return new Promise((resolve) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/*'
    input.onchange = () => {
      const file = input.files?.[0]
      if (!file) {
        resolve(null)
        return
      }
      void downscale(file).then(resolve)
    }
    // A cancelled picker must resolve, not hang the caller forever.
    input.oncancel = () => resolve(null)
    input.click()
  })
}

async function downscale(file: File): Promise<string | null> {
  const dataUri = await readAsDataUri(file)
  if (!dataUri) return null

  const image = await loadImage(dataUri)
  if (!image) return dataUri // Unreadable dimensions — keep the original.

  const scale = Math.min(1, MAX_EDGE / Math.max(image.width, image.height))
  if (scale >= 1 && dataUri.length < 400_000) return dataUri

  const canvas = document.createElement('canvas')
  canvas.width = Math.round(image.width * scale)
  canvas.height = Math.round(image.height * scale)
  const ctx = canvas.getContext('2d')
  if (!ctx) return dataUri
  ctx.drawImage(image, 0, 0, canvas.width, canvas.height)

  try {
    return canvas.toDataURL('image/jpeg', QUALITY)
  } catch {
    return dataUri
  }
}

const readAsDataUri = (file: File) =>
  new Promise<string | null>((resolve) => {
    const reader = new FileReader()
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : null)
    reader.onerror = () => resolve(null)
    reader.readAsDataURL(file)
  })

const loadImage = (src: string) =>
  new Promise<HTMLImageElement | null>((resolve) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => resolve(null)
    img.src = src
  })
