# CURRENT_STATE.md — live project handoff

> **Read this first after any compaction or new session.** It is the single place that says what
> is being worked on right now and what to do next. Update it whenever major implementation state
> changes, and always before ending a session or running low on context.
>
> Source code is the ultimate truth; this file is the memory of *intent and progress* that the code
> alone doesn't carry.

**Last updated:** 2026-08-24 · **App version:** 3.0 (`versionCode 3`) · **DB schema:** v6 · **Branch:** `main`

---

## Current Goal

**Task 3 is complete and user-confirmed.** No feature is actively in flight. The next code task is
the user's pick among the backlog below (details in [TODO.md](TODO.md)):

1. **Map & location epic** (requested 2026-08-24) — redesign the trip map to a supplied mockup,
   make place search **proximity-first**, wire **device GPS**, make **navigation open Google Maps**.
   (Auto-rotation, originally part of this epic, is already fixed.) Key finding: proximity plumbing
   **already exists** in `NominatimLocationSearchProvider` (`viewbox`+`bounded=0`); it's just not fed
   a viewport at the call site.
2. **Stay check-in / check-out (NEW — requested 2026-08-24, "future but essential").** A `STAY`
   activity should carry **two** datetime fields — **check-in (date+time)** and **check-out
   (date+time)** — replacing the generic single date + start/end + "ends next day". When the stay
   **spans days**, the itinerary shows it as **two entries**: check-in on the arrival day, check-out
   on the departure day.
3. **Back-navigation fix (NEW — requested 2026-08-24, "for later").** System Back should return to
   **Home** from anywhere, **except** the Settings menu and the activity editor (where Back keeps its
   normal behaviour). Today Back just pops the last screen.

## Current Status

- ✅ **Task 3 — activity-card background (schema v6)** — DONE & **user-confirmed on-device**
  (2026-08-24: "the activity card background is working fine"). New `Event.backgroundImageUri`
  end-to-end (domain → entity → mappers → `MIGRATION_5_6` + exported `6.json` + `MigrationTest` 5→6
  cases); a **compact** "Card background" picker in the event editor; `HomeViewModel.nextUpImageUri`
  prefers it; `PhotoBackdrop` fade lightened (0.42/0.60/0.80) so the chosen photo shows through.
  Unit tests + `assembleDebug` green. **Committing now** as a new commit on `main`.
- ✅ **RailRadar integration** (live status, schedule, PNR lookup) — implemented, unit-tested,
  device-verified (Route tab showed "245 stops · with actual times", no error banner).
- ✅ **Next-up card photo backdrop** — activity next-up cards show the activity photo blurred +
  faded; **train** next-up cards stay solid; the grey "shadow-slab" bug is fixed. Device-verified.
- ✅ **Auto-rotation fixed & device-verified (2026-08-24)** — `MainActivity` locked to portrait.
- ✅ **Persistent memory layer** (this doc system: `CLAUDE.md` + `docs/*`) — created & committed.
- 📦 **On `main`** (tip `f247c3f`, ahead of `origin/main` — **not pushed**): RailRadar; photo
  backdrop + shadow fix; portrait lock; project docs. **Task 3 lands as the next commit on `main`.**
- ⚠️ **RailRadar API key format** unverified: current key is `rg_e04caaa9deb04b8c849f5411d7bdec0e`;
  vendor docs show `rr_live_…`. App degrades gracefully if rejected (falls back to projection), but
  labels status "Live" whenever a key is merely *present*. Watch `logcat` for `401`.

## Active Work

Nothing in flight. **Task 3 is finished and user-confirmed on-device**; it's being committed to
`main` now. Awaiting the user's pick for the next code task — the **Map & location epic**, the new
**Stay check-in/check-out** feature, or the deferred **Back-navigation fix** (all in [TODO.md](TODO.md)).

## Files Changed — Task 3 (this session; committing as a new commit on `main`)

**Modified:**
- [Event.kt](app/src/main/java/com/tripcompanion/app/domain/model/Event.kt) — `val backgroundImageUri: String? = null`.
- [EventEntity.kt](app/src/main/java/com/tripcompanion/app/data/local/entity/EventEntity.kt) — matching column.
- [EntityMappers.kt](app/src/main/java/com/tripcompanion/app/data/local/EntityMappers.kt) — mapped both directions.
- [TripDatabase.kt](app/src/main/java/com/tripcompanion/app/data/local/TripDatabase.kt) — `version = 6`.
- [Migrations.kt](app/src/main/java/com/tripcompanion/app/data/local/Migrations.kt) — `MIGRATION_5_6` (`ALTER TABLE events ADD COLUMN backgroundImageUri TEXT DEFAULT NULL`) + appended to `ALL`.
- [EventEditorViewModel.kt](app/src/main/java/com/tripcompanion/app/feature/event/EventEditorViewModel.kt) — `backgroundImageUri` state, `updateBackgroundImage`/`clearBackgroundImage`, included in `save()`.
- [EventEditorScreen.kt](app/src/main/java/com/tripcompanion/app/ui/screens/EventEditorScreen.kt) — compact `CardBackgroundPicker` row + `PickVisualMedia`/`ImageStorageHelper` plumbing.
- [HomeViewModel.kt](app/src/main/java/com/tripcompanion/app/feature/trip/HomeViewModel.kt) — `nextUpImageUri` prefers `event.backgroundImageUri` first.
- [Media.kt](app/src/main/java/com/tripcompanion/app/ui/components/Media.kt) — `PhotoBackdrop` fade lightened to 0.42/0.60/0.80.
- [MigrationTest.kt](app/src/androidTest/java/com/tripcompanion/app/data/local/MigrationTest.kt) — `LATEST_VERSION = 6`, `seedVersion5()`, two 5→6 cases.

**Added (untracked):**
- `app/schemas/com.tripcompanion.app.data.local.TripDatabase/6.json` — auto-exported schema; matches the migration (nullable TEXT).

## Prior work committed on `main` (tip `f247c3f`) — for reference

RailRadar vendor swap (new `data/network/railradar/*`, `PnrLookupService`, `TrainModule` binding,
old `indianrailapi.com` vendor + cleartext exception deleted), `PhotoBackdrop`, grey-slab fix
(`TrainCard` `elevation` param), portrait lock in `AndroidManifest.xml`, version 3.0, and the whole
persistent-docs layer. See git log `31c01a0..f247c3f` for the exact file list.

## Current Bugs

- **None open/blocking.** Bugs hit earlier (schedule MALFORMED; grey slab; auto-rotation) are all
  fixed and verified — see *Do Not Redo* so they aren't reintroduced.
- **Watch item (not a confirmed bug):** RailRadar key may `401` (see *Current Status*). If live
  fetches fail, confirm the key in the RailRadar dashboard; the app still works on the projection.

## Current Architecture State (only what's relevant to active work)

- **Home next-up photo source:** `HomeViewModel.nextUpImageUri(event, place, trip)`
  (in [HomeViewModel.kt](app/src/main/java/com/tripcompanion/app/feature/trip/HomeViewModel.kt))
  now resolves, in order: **`event.backgroundImageUri` (Task 3, user-set) →** STAY→`StayDetails.photoUri`
  → `Location.photoUri` → first `PlannedPhoto.referenceImageUri` for the event → `Trip.coverImageUri`.
- **`Event.backgroundImageUri`** is the Task 3 field, persisted via schema v6 (`MIGRATION_5_6`).
- **Image picking pattern (reused by Task 3):**
  `rememberLauncherForActivityResult(PickVisualMedia())` → copy into app storage with
  `ImageStorageHelper.saveImageToInternalStorage(context, uri)` (in `data.local`) → store the
  returned `file://` path. Same pattern in `PlannedPhotoEditorScreen`, `TripEditorScreen`, `HotelScreen`.
- **`PhotoBackdrop`** fade gradient is in
  [Media.kt](app/src/main/java/com/tripcompanion/app/ui/components/Media.kt): now
  `0.0f→0.42f, 0.55f→0.60f, 1.0f→0.80f` of `surface` alpha over the blurred photo (lightened for
  Task 3(b) so the user's chosen photo reads through; the 0.80 floor at the bottom keeps text legible).

## Unresolved Questions

- **Ordering of the remaining backlog** — Map epic vs. Stay check-in/check-out vs. Back-navigation
  fix. User to pick which is next.
- **Stay check-in/check-out — design not settled:** store two datetimes on the event/`StayDetails`
  and split into two itinerary rows at the grouping layer, *or* model two linked events? If persisted
  as new columns it's another migration (v7). Decide before building.
- *(Task 3's opacity direction and storage choice — RESOLVED: lightened the wash to 0.42/0.60/0.80,
  and used a dedicated `Event.backgroundImageUri` column. User confirmed the card background works.)*

## Next Steps (exact, in order)

1. **Commit Task 3** (code + `6.json` + these doc updates) to `main`. ← doing now.
2. **(Optional) Run the instrumented `MigrationTest`** on device
   (`./gradlew :app:connectedDebugAndroidTest`) to confirm the two new 5→6 cases pass now that
   `6.json` exists.
3. **Await the user's pick** for the next feature: Map & location epic, Stay check-in/check-out, or
   the Back-navigation fix (all in [TODO.md](TODO.md)). Do **not** start the deferred items without
   confirmation ("for later" / "future").

## Do Not Redo (settled — don't revert without a strong, documented reason)

- **Don't** re-add the `indianrailapi.com` vendor or a cleartext network exception — RailRadar
  replaced it; app is TLS-only.
- **Don't** re-add the "Transgender" gender chip to the train editor UI (removed per user request;
  the `PassengerGender.TRANSGENDER` enum value stays in the model).
- **Don't** reintroduce `fallbackToDestructiveMigration` in `DatabaseModule`.
- **Don't** revert the RailRadar schedule parser to reading flat `arrival`/`departure` — the real
  payload nests them under `station:{}` (the MALFORMED bug).
- **Don't** delete a `JOURNEY` event when a train is deleted.
- **Grey-slab fix:** don't give a transparent `Surface`/card a non-zero `shadow` elevation; keep
  `TrainCard` at `elevation = 0.dp` when it sits inside an already-elevated container.
- **Task 3:** keep the next-up **train** cards solid (no `PhotoBackdrop`); the background photo is an
  **activities-only** feature.

## Environment / device verification

- **adb** is not on PATH: `C:\Users\agamr\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- **Device id** `00162352E001788` (product AsteroidsIND, model A059) is **flaky** — if "device not
  found," run `adb kill-server` → `adb start-server` → `adb wait-for-device`, then retry.
- **Screenshots:** `adb -s <id> exec-out screencap -p > /e/Projects/Trip/.verify/x.png`, then Read
  the Windows path `E:\Projects\Trip\.verify\x.png` (the `/tmp` path is not readable by the Read tool).
  `.verify/` is scratch (git-ignored) — clean it up after. Device is ~1080×2392; screenshots display
  at 903×2000 (multiply displayed coords by 1.20 for raw device pixels).
