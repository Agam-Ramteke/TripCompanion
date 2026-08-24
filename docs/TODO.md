# TODO.md

Work queue for Trip Companion. Living document — check items off, move them between sections, and add
new ones as they surface. Detailed *in-flight* status (what's half-done, what to do next step by
step) lives in [CURRENT_STATE.md](CURRENT_STATE.md); this file is the backlog overview.

**Last updated:** 2026-08-24

---

## 🔴 Current (active now)

- [ ] **Task 3 — Activity-card background customization.** Let the user set the photo shown on the
      Home next-up card for an activity, via a **compact** control in the event editor, and make that
      photo **"slightly more transparent."** Full step-by-step plan in
      [CURRENT_STATE.md](CURRENT_STATE.md) → *Next Steps*. Touches: `Event` (+ `backgroundImageUri`),
      `EventEntity`, `EntityMappers`, Room `MIGRATION_5_6` (+ `6.json` + `MigrationTest`),
      `EventEditorViewModel`/`State`, `EventEditorScreen` (picker), `HomeViewModel.nextUpImageUri`
      (new field takes precedence), `PhotoBackdrop` fade alphas.
  - [ ] **Resolve first:** Task 3(b) opacity direction is ambiguous (lighten wash vs. fainter photo)
        — see [CURRENT_STATE.md](CURRENT_STATE.md) *Unresolved Questions*. A small nudge + screenshot
        to confirm is acceptable.

## 🟠 Next (soon, after current)

- [ ] **Commit the uncommitted work.** Nothing is committed since the initial commit `31c01a0`. The
      RailRadar integration + photo backdrop + (then) Task 3 should be committed in coherent chunks.
      Verify secrets stay out (`local.properties`, `docs/Trip/*.pdf` are git-ignored).
- [ ] **Confirm the RailRadar API key.** Verify `rg_…` vs `rr_live_…` in the RailRadar dashboard;
      watch `logcat` for `401` on a live fetch. If rejected, re-issue. (See
      [API_CONTRACTS.md](API_CONTRACTS.md).)
- [ ] **Verify RailRadar time format** against a real live response (`HH:mm` vs ISO) and tighten the
      parser if needed (currently accepts both) — [API_CONTRACTS.md](API_CONTRACTS.md).

## 🗺️ Map & location epic (requested 2026-08-24, "after" the docs task)

Requested with a mockup (a clean Google-Maps-style trip map). Ordering vs. Task 3 to be confirmed
with the user. Sub-items:

- [ ] **Map redesign** to match the mockup: rounded map surface; floating **menu** (top-left),
      **trip switcher pill** (top-center: weather glyph + trip name + ▾), **layers** button and
      **locate-me** button (top-right, stacked), **zoom ±** (bottom-right), **navigate** FAB
      (bottom-left); category-colored **pins with type icons** (visit=green, museum/haveli=orange,
      photo=blue camera, palace=purple — key off `EventType`/`location.category`, **not** place
      names, per ADR-004); **blue route polyline** connecting stops in itinerary order; bottom
      **"Trip Itinerary" bottom-sheet** peeking up. Lives in
      [OsmMap.kt](../app/src/main/java/com/tripcompanion/app/ui/components/OsmMap.kt) + the map screen.
- [ ] **Proximity-first search — FEASIBLE, plumbing already exists.**
      [NominatimLocationSearchProvider.kt](../app/src/main/java/com/tripcompanion/app/data/network/NominatimLocationSearchProvider.kt)
      already accepts a `SearchViewport` and, when given one, sends `viewbox=west,north,east,south`
      + `bounded=0` to **bias** toward that area (its KDoc: "the single biggest improvement"). Global
      results today mean **no viewport is being passed at the call site** (the search VM/service).
      Work: (a) supply a viewport/anchor — from the **trip's locations** or the **map camera** (no
      new permission) and/or **device GPS** (needs permission, below); (b) for guaranteed
      closest-first, **sort results by distance** to the anchor using `core/util/GeoUtils`.
- [ ] **Device location (GPS)** — currently **not wired**: the manifest declares only INTERNET +
      ACCESS_NETWORK_STATE (no `ACCESS_FINE/COARSE_LOCATION`). Needed for: the map "locate-me"
      button, GPS-anchored search, and **location-based trip-status tracking** (which event is
      "current" by proximity, complementing the time-based `TripStateEngine`). Work: add
      permission(s) + runtime request + a `domain/service` location port with a fused/Android impl in
      `data/` (keep the §11 boundary; must degrade gracefully with permission denied / no fix).
- [ ] **Navigation → Google Maps.** In-app map is for *display*; the **navigate** action fires an
      `Intent` to Google Maps (`google.navigation:q=<lat>,<lon>` or `geo:` URI), with a chooser
      fallback if Maps isn't installed. No in-app turn-by-turn.

## 🐞 Bugs

- [x] **Unwanted screen auto-rotation on app open — FIXED (applied, pending on-device verify).**
      Root cause: `MainActivity` in [AndroidManifest.xml](../app/src/main/AndroidManifest.xml) had
      **no `android:screenOrientation`**, so the app followed the device and rotated. Fix applied:
      added `android:screenOrientation="portrait"` to the `<activity>`. Nothing calls
      `setRequestedOrientation` in code, so this is the complete cause. Takes effect on next build.
      Note: this locks the *whole* single-Activity app to portrait, including the map — revisit if
      the redesigned map should allow landscape. osmdroid's *map* rotation gestures in `OsmMap.kt` are
      unrelated (map orientation ≠ screen).
- **None other open.** Two were fixed & device-verified this session (kept here so they aren't
  reintroduced — see [CURRENT_STATE.md](CURRENT_STATE.md) *Do Not Redo*):
  - ✅ RailRadar schedule "MALFORMED" — station nested under `station:{}`, parser fixed.
  - ✅ Grey "shadow-slab" on the next-up card — transparent `Surface` + non-zero `shadow` elevation;
    `TrainCard` now takes `elevation`, Home passes `0.dp`.

## 🟡 Later (someday / not scheduled)

- [ ] **Notifications / background refresh (WorkManager)** — spec future-work; no WorkManager dep yet
      ([ARCHITECTURE.md](ARCHITECTURE.md) → Background processing). Only add if a real need appears.
- [ ] Broaden ticket import beyond IRCTC format if other formats are needed.

## 🧹 Technical debt / cleanups

- [ ] **Migration test coverage** must extend to 5→6 when Task 3 lands (policy: no destructive
      fallback — ADR-006).
- [ ] Confirm `Converters` stored representation for `LocalDateTime` is documented somewhere durable
      (currently `UNVERIFIED` in [ARCHITECTURE.md](ARCHITECTURE.md)).
- [ ] The implemented Room schema is a pragmatic subset of spec §18 (no `Traveler`/`Task` tables);
      keep [ARCHITECTURE.md](ARCHITECTURE.md) honest about the gap as the model evolves.

## 💡 Ideas (unfiltered, not commitments)

- Per-event "card background" could later extend to trains (currently trains are intentionally solid).
- Trip export could gain a shareable read-only view.

---

*When you finish something here, also update [CURRENT_STATE.md](CURRENT_STATE.md) if it changes the
"what's happening now" picture.*
