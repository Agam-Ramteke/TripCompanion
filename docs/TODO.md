# TODO.md

Work queue for Trip Companion. Living document — check items off, move them between sections, and add
new ones as they surface. Detailed *in-flight* status (what's half-done, what to do next step by
step) lives in [CURRENT_STATE.md](CURRENT_STATE.md); this file is the backlog overview.

**Last updated:** 2026-08-24

---

## 🔴 Current (active now)

- [x] **Task 3 — Activity-card background customization — DONE & user-confirmed (2026-08-24).**
      Added `Event.backgroundImageUri` end-to-end (domain → `EventEntity` → `EntityMappers` → Room
      `MIGRATION_5_6` + exported `6.json` + `MigrationTest` 5→6 cases → `EventEditorViewModel`/State →
      a **compact** "Card background" picker in `EventEditorScreen`). `HomeViewModel.nextUpImageUri`
      now prefers it; `PhotoBackdrop` fade lightened (0.42/0.60/0.80) so the chosen photo shows
      through. Opacity ambiguity resolved (lightened the wash). Unit tests + `assembleDebug` green;
      committed on `main`.
- [ ] **Await the user's pick** for the next feature (below): the **Map & location epic**, the new
      **Stay check-in/check-out** feature, or the deferred **Back-navigation fix**.

## 🟠 Next (soon, after current)

- [x] **Commit the RailRadar + photo + docs work — DONE (2026-08-24).** Landed on `main` as 4
      commits (tip `f247c3f`): RailRadar; photo backdrop + shadow fix; portrait lock; project docs.
      Secrets confirmed git-ignored (`local.properties`, `docs/Trip/*.pdf`). `main` is ahead of
      `origin/main` — **not pushed**. Task 3 will be its own commit(s) when it lands.
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

## 🏨 Stay check-in / check-out (requested 2026-08-24 — "future but essential")

A `STAY` activity currently uses the generic single **Date + Starts/Ends + "Ends the next day"**
model (as seen in the "Add an activity → Stay" editor). The user wants stays to carry **two explicit
datetime fields instead**:

- [ ] **Check-in (date + time)** and **check-out (date + time)** fields, surfaced when Kind = Stay.
- [ ] When the stay **spans multiple days**, the itinerary shows it as **two separate entries** — a
      **check-in** item on the arrival day and a **check-out** item on the departure day (each on its
      own day) — rather than one card that merely "ends the next day".
- [ ] **Design to settle first:** store two datetimes (on `Event`/`StayDetails`) and derive two
      itinerary rows at the grouping layer, *or* model two linked events. If persisted as new
      columns → another Room migration (**v7**) + exported schema + `MigrationTest` case (no
      destructive fallback — ADR-006). Keep the generic-engine principle: branch on
      `event.type == STAY`, never a place name.

## 🐞 Bugs

- [x] **Unwanted screen auto-rotation on app open — FIXED & device-verified (2026-08-24).**
      Root cause: `MainActivity` in [AndroidManifest.xml](../app/src/main/AndroidManifest.xml) had
      **no `android:screenOrientation`**, so the app followed the device and rotated. Fix applied:
      added `android:screenOrientation="portrait"` to the `<activity>`. Nothing calls
      `setRequestedOrientation` in code, so this is the complete cause. **Verified on-device
      2026-08-24:** with auto-rotate ON, forcing the display to landscape left the app portrait.
      Note: this locks the *whole* single-Activity app to portrait, including the map — revisit if
      the redesigned map should allow landscape. osmdroid's *map* rotation gestures in `OsmMap.kt` are
      unrelated (map orientation ≠ screen).
- **None other open.** Two were fixed & device-verified this session (kept here so they aren't
  reintroduced — see [CURRENT_STATE.md](CURRENT_STATE.md) *Do Not Redo*):
  - ✅ RailRadar schedule "MALFORMED" — station nested under `station:{}`, parser fixed.
  - ✅ Grey "shadow-slab" on the next-up card — transparent `Surface` + non-zero `shadow` elevation;
    `TrainCard` now takes `elevation`, Home passes `0.dp`.

## 🟡 Later (someday / not scheduled)

- [ ] **Back-navigation to Home (requested 2026-08-24, "for later").** System **Back** should
      navigate to the **Home** screen from anywhere, **except** the **Settings menu** and the
      **activity editor**, where Back keeps its normal behaviour. Today Back just pops the last
      screen. Touches back-stack handling in `AppNavHost`/`Routes` (single-Activity
      Navigation-Compose). Confirm the exact set of "excepted" screens before building.
- [ ] **Notifications / background refresh (WorkManager)** — spec future-work; no WorkManager dep yet
      ([ARCHITECTURE.md](ARCHITECTURE.md) → Background processing). Only add if a real need appears.
- [ ] Broaden ticket import beyond IRCTC format if other formats are needed.

## 🧹 Technical debt / cleanups

- [x] **Migration test coverage 5→6 — DONE (Task 3).** `MigrationTest` has `LATEST_VERSION = 6`, a
      `seedVersion5()` helper, and two 5→6 cases (schema validates; an upgraded event has a null
      `backgroundImageUri`). Instrumented — run on a device to execute.
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
