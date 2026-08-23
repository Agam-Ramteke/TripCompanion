#!/usr/bin/env python3
"""
Cuts the launcher icon out of the flat logo artwork.

Run from the repository root, with Pillow installed:

    python branding/generate_launcher_icons.py

The source (`docs/Logo_src.png`) is a *finished* icon: the artwork sits on an opaque
cream rounded square. An adaptive icon needs the opposite — separate layers, with the
ground as a plain fill and the artwork transparent, because the launcher supplies the
mask itself. Handing the launcher the pre-rounded square would round it twice and clip
the corners of the ring.

So the cream is removed rather than cropped around. The logo is flat colour, which makes
that exact: every pixel lies on the line between cream and one of the two inks, so its
coverage is the position along that line and its colour is the ink itself. Edge pixels
come out as partially-transparent *ink* instead of ink blended with cream, which is what
lets the artwork sit cleanly on a themed or monochrome ground later.

### Scale

The artwork is 60% of the source tile, and it stays 60% of the *visible* tile here.
An adaptive icon is a 108dp canvas of which a launcher may show only the middle 72dp,
so 60% of 72dp is 44dp — comfortably inside the 66dp guaranteed-visible safe zone.
Filling more of the frame would look bolder beside other apps and would also throw away
the margin the logo was drawn with.
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "docs" / "Logo_src.png"
RES = ROOT / "app" / "src" / "main" / "res"
PLAY_STORE = ROOT / "branding" / "ic_launcher-playstore.png"

CREAM = (252, 245, 237)
INKS = ((7, 52, 46), (234, 149, 41))  # deep green, amber

# Anything this close to cream is cream. Squared distance, so ~7 per channel.
CREAM_TOLERANCE = 150

# The black outside the source's rounded corners is not artwork. Ignored when measuring
# the bounding box, then cropped away with everything else outside it.
BORDER_FRACTION = 0.12

CANVAS_DP = 108
ARTWORK_DP = 44

DENSITIES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def unmix(image: Image.Image) -> Image.Image:
    """Replaces the cream ground with transparency, keeping each ink's own colour.

    For a pixel `p` covered by ink `C` with coverage `a`, the source holds
    `p = a*C + (1-a)*cream`. With `C` known to be one of two flat inks, `a` is the
    projection of `p - cream` onto `C - cream`, and the ink that leaves the smallest
    residual is the one that drew the pixel.
    """
    width, height = image.size
    source = image.convert("RGB").load()
    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    target = out.load()

    lines = [(ink, tuple(i - c for i, c in zip(ink, CREAM))) for ink in INKS]
    lengths = [sum(d * d for d in direction) for _, direction in lines]

    for y in range(height):
        for x in range(width):
            pixel = source[x, y]
            offset = tuple(p - c for p, c in zip(pixel, CREAM))
            if sum(o * o for o in offset) <= CREAM_TOLERANCE:
                continue

            best = None
            for (ink, direction), length in zip(lines, lengths):
                alpha = sum(o * d for o, d in zip(offset, direction)) / length
                alpha = min(1.0, max(0.0, alpha))
                mixed = tuple(c + alpha * d for c, d in zip(CREAM, direction))
                residual = sum((m - p) ** 2 for m, p in zip(mixed, pixel))
                if best is None or residual < best[0]:
                    best = (residual, ink, alpha)

            _, ink, alpha = best
            if alpha > 0:
                target[x, y] = (*ink, round(alpha * 255))

    return out


def artwork_square(cutout: Image.Image) -> Image.Image:
    """Crops to the artwork and pads it back to a square, keeping it centred."""
    width, height = cutout.size
    margin = int(width * BORDER_FRACTION)
    interior = cutout.crop((margin, margin, width - margin, height - margin))

    box = interior.getbbox()
    if box is None:
        raise SystemExit("No artwork found — is the cream colour right?")
    left, top, right, bottom = box
    cropped = interior.crop(box)

    side = max(cropped.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(
        cropped,
        ((side - cropped.width) // 2, (side - cropped.height) // 2),
    )
    print(f"  artwork {cropped.width}x{cropped.height} of {width}px source "
          f"({cropped.width / width:.1%} wide)")
    return square


def layer(square: Image.Image, canvas_px: int, monochrome: bool) -> Image.Image:
    """One adaptive-icon layer: the artwork centred on a transparent 108dp canvas."""
    artwork_px = round(canvas_px * ARTWORK_DP / CANVAS_DP)
    scaled = square.resize((artwork_px, artwork_px), Image.LANCZOS)

    if monochrome:
        # The system tints the alpha channel and ignores the colour, so flatten to
        # black. Done after the resize so the ink edges resample before flattening.
        black = Image.new("RGBA", scaled.size, (0, 0, 0, 0))
        black.putalpha(scaled.getchannel("A"))
        scaled = black

    canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    offset = (canvas_px - artwork_px) // 2
    canvas.paste(scaled, (offset, offset))
    return canvas


def write_play_store(square: Image.Image) -> None:
    """The 512px store listing icon: full-bleed, square, no rounding — Play adds that."""
    size = 512
    artwork = round(size * 0.602)  # the proportion the logo was drawn at
    icon = Image.new("RGB", (size, size), CREAM)
    scaled = square.resize((artwork, artwork), Image.LANCZOS)
    offset = (size - artwork) // 2
    icon.paste(scaled, (offset, offset), scaled)
    PLAY_STORE.parent.mkdir(parents=True, exist_ok=True)
    icon.save(PLAY_STORE)
    print(f"  {PLAY_STORE.relative_to(ROOT)}")


def main() -> None:
    print(f"Reading {SOURCE.relative_to(ROOT)}")
    square = artwork_square(unmix(Image.open(SOURCE)))

    for folder, canvas_px in DENSITIES.items():
        directory = RES / folder
        directory.mkdir(parents=True, exist_ok=True)
        layer(square, canvas_px, monochrome=False).save(directory / "ic_launcher_foreground.png")
        layer(square, canvas_px, monochrome=True).save(directory / "ic_launcher_monochrome.png")
        print(f"  {folder}/ ({canvas_px}px)")

    write_play_store(square)
    print("Done.")


if __name__ == "__main__":
    main()
