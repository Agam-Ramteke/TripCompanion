# CURRENT_STATE.md — live project handoff

> **Read this first after any compaction or new session.** It is the single place that says what
> is being worked on right now and what to do next. Update it whenever major implementation state
> changes, and always before ending a session or running low on context.
>
> Source code is the ultimate truth; this file is the memory of *intent and progress* that the code
> alone doesn't carry.

**Last updated:** 2026-08-24 · **App version:** 3.0 (`versionCode 3`) · **DB schema:** v5 · **Branch:** `main`

---

## Current Goal

Two active feature threads on the Home **"next-up" card** and train integration:

1. **Activity-card customization (Task 3 — IN PROGRESS, not started in code).** Let the user
   explicitly **set the background photo** shown for an activity on the Home next-up card, via a
   **compact** control ("not visually very large") in the activity editor. And make that photo
   **"slightly more transparent."**
2. **RailRadar train integration + next-up photo backdrop (DONE + device-verified, UNCOMMITTED).**
   See *Recent Changes*.
3. **Map & location epic (NEW — requested 2026-08-24, "after" the docs task; not started).** Redesign
   the trip map to match a supplied mockup; make place search **proximity-first**; wire **device GPS**
   (currently *not* declared in the manifest); make **navigation open Google Maps**; and fix
   **unwanted screen auto-rotation**. Full breakdown in [TODO.md](TODO.md) → *Map & location epic* and
   *Bugs*. Key finding: proximity search plumbing **already exists** in
   `NominatimLocationSearchProvider` (`viewbox`+`bounded=0`); it's just not fed a viewport at the call
   site.

## Current Status

- ✅ **RailRadar integration** (live status, schedule, PNR lookup) — implemented, unit-tested,
  device-verified (Route tab showed "245 stops · with actual times", no error banner).
- ✅ **Next-up card photo backdrop** — activity next-up cards show the activity photo blurred +
  faded; **train** next-up cards stay solid; the grey "shadow-slab" bug is fixed. Device-verified.
- ✅ **Auto-rotation fixed** — `MainActivity` locked to portrait (pending on-device verify).
- ✅ **Persistent memory layer** (this doc system: `CLAUDE.md` + `docs/*`) — created & committed.
- ⏳ **Task 3** — designed but **not implemented**. Needs a new `Event` field + Room migration +
  editor UI + resolver change (details under *Next Steps*).
- 📦 **Committed** on branch **`railradar-integration-and-docs`** (4 commits on top of `31c01a0`):
  RailRadar; photo backdrop + shadow fix; portrait lock; project docs. **`main` is NOT yet updated**
  — fast-forward it (`git checkout main && git merge --ff-only railradar-integration-and-docs`) when
  ready.
- ⚠️ **RailRadar API key format** unverified: current key is `rg_e04caaa9deb04b8c849f5411d7bdec0e`;
  vendor docs show `rr_live_…`. App degrades gracefully if rejected (falls back to projection), but
  labels status "Live" whenever a key is merely *present*. Watch `logcat` for `401`.

## Active Work

Setting up the persistent-context docs (this file and siblings). **Immediately after this**, the
next code task is **Task 3** (activity-card background picker + opacity tweak).

## Files Being Modified (uncommitted — `git status`)

**Modified:**
- [AndroidManifest.xml](app/src/main/AndroidManifest.xml) — **added `android:screenOrientation="portrait"`** to `MainActivity` (fixes unwanted auto-rotation; 2026-08-24). Pending on-device verify.
- [app/build.gradle.kts](app/build.gradle.kts) — `versionCode 3`/`versionName "3.0"`; `RAILRADAR_API_KEY` build field (renamed from `INDIANRAIL_API_KEY`).
- [SampleTripSeeder.kt](app/src/main/java/com/tripcompanion/app/data/SampleTripSeeder.kt) — minor.
- [TripImagesDir.kt](app/src/main/java/com/tripcompanion/app/data/transfer/TripImagesDir.kt) — minor.
- [TrainModule.kt](app/src/main/java/com/tripcompanion/app/di/TrainModule.kt) — bind RailRadar provider + `PnrLookupService`; select live vs projection by key.
- [TrainEditorViewModel.kt](app/src/main/java/com/tripcompanion/app/feature/train/TrainEditorViewModel.kt) — PNR fetch state/logic; `applying(ticket)` refactored to `withParsedTicket(ticket)` shared by e-ticket import + PNR.
- [Cards.kt](app/src/main/java/com/tripcompanion/app/ui/components/Cards.kt) — `TrainCard` gained an `elevation` param (fixes grey slab).
- [Media.kt](app/src/main/java/com/tripcompanion/app/ui/components/Media.kt) — **`PhotoBackdrop`** added (blur + vertical-gradient fade). **Task 3 (b) edits its fade alphas here.**
- [HomeScreen.kt](app/src/main/java/com/tripcompanion/app/ui/screens/HomeScreen.kt) — `NextUpSection` draws `PhotoBackdrop` for activities (`imageUri = state.nextUpImageUri`), solid for trains; `TrainCard` called with `elevation = 0.dp`.
- [TrainEditorScreen.kt](app/src/main/java/com/tripcompanion/app/ui/screens/TrainEditorScreen.kt) — "Fetch booking status" (PNR) button + notice; gender chips reduced to Male/Female.
- [network_security_config.xml](app/src/main/res/xml/network_security_config.xml) — cleartext fully off (indianrailapi exception removed).
- [TrainEditorViewModelTest.kt](app/src/test/java/com/tripcompanion/app/feature/train/TrainEditorViewModelTest.kt) — PNR cases.

**Added (untracked):**
- `app/src/main/java/com/tripcompanion/app/data/network/railradar/` — `RailRadarClient`, `RailRadarParser`, `RailRadarError`, `RailRadarTrainStatusProvider`, `RailRadarPnrLookupService`, `RailRadarApiKey`.
- [PnrLookupService.kt](app/src/main/java/com/tripcompanion/app/domain/service/PnrLookupService.kt) — domain port for PNR.
- `app/src/test/java/com/tripcompanion/app/data/network/railradar/RailRadarParserTest.kt`.

**Deleted:** `IndianRailApiKey.kt`, `IndianRailApiParser.kt`, `IndianRailApiTrainStatusProvider.kt`, `IndianRailApiParserTest.kt` (old vendor).

## Recent Changes (this working session, chronological)

1. Fixed RailRadar **schedule parser**: the `GET /v1/trains/{number}` route nests the station under
   `station:{code,name}` with bare `arrival`/`departure`; parser now reads that shape. Route tab no
   longer shows "The railway sent something this app could not read." (MALFORMED).
2. Added **`PhotoBackdrop`** and wired activity next-up cards to show a blurred/faded photo; trains
   stay solid.
3. Fixed the **grey shadow-slab**: a transparent `Surface` + `Modifier.shadow(elevation>0)` renders
   its shadow through its own fill. `TrainCard` now takes `elevation` and Home passes `0.dp`.
4. Built the **persistent memory layer** (`CLAUDE.md`, `docs/CURRENT_STATE.md`, `ARCHITECTURE.md`,
   `DECISIONS.md`, `API_CONTRACTS.md`, `TODO.md`, `.claude/rules/*`).

## Current Bugs

- **None open/blocking.** The two bugs hit this session (schedule MALFORMED; grey slab) are both
  fixed and verified — see *Do Not Redo* so they aren't reintroduced.
- **Watch item (not a confirmed bug):** RailRadar key may `401` (see *Current Status*). If live
  fetches fail, confirm the key in the RailRadar dashboard; the app still works on the projection.

## Current Architecture State (only what's relevant to active work)

- **Home next-up photo source:** `HomeViewModel.nextUpImageUri(event, place, trip)`
  (in [HomeViewModel.kt](app/src/main/java/com/tripcompanion/app/feature/trip/HomeViewModel.kt),
  resolver ~lines 315–325) currently resolves, in order: STAY→`StayDetails.photoUri`,
  `Location.photoUri`, first `PlannedPhoto.referenceImageUri` for the event, then `Trip.coverImageUri`.
  Task 3 must add a **user-set per-activity background** that takes precedence.
- **`Event` has no photo field** ([Event.kt](app/src/main/java/com/tripcompanion/app/domain/model/Event.kt)).
  Task 3 needs one added end-to-end: domain `Event` → `EventEntity` → `EntityMappers` → Room
  `MIGRATION_5_6` (+ exported `6.json` + `MigrationTest`) → `EventEditorViewModel`/`State` → editor UI.
- **Image picking pattern to reuse:**
  [PlannedPhotoEditorScreen.kt](app/src/main/java/com/tripcompanion/app/ui/screens/PlannedPhotoEditorScreen.kt)
  lines 60–83 — `rememberLauncherForActivityResult(PickVisualMedia())` → copy into app storage with
  `ImageStorageHelper.saveImageToInternalStorage(context, uri)` → store the returned `file://` path.
  Same pattern in `TripEditorScreen` (cover) and `HotelScreen`.
- **`PhotoBackdrop`** fade gradient (Task 3 (b)) is in
  [Media.kt](app/src/main/java/com/tripcompanion/app/ui/components/Media.kt): currently
  `0.0f→0.55f, 0.55f→0.72f, 1.0f→0.86f` of `surface` alpha over the blurred photo.

## Unresolved Questions

- **Task 3 (b) direction is ambiguous.** "reduce the opacity make it slightly more transparent"
  reads two opposite ways: (A) *lighten the fade/wash so the photo is more visible*, or (B) *make
  the photo itself fainter/more see-through*. **Confirm with the user before finalizing**, or make a
  small change and show a screenshot for a yes/no. Literal wording leans (B); the feature intent
  (user picks a background to show it off) leans (A).
- **Task 3 storage choice:** new `Event.backgroundImageUri` column (recommended, self-contained) vs.
  reusing the existing per-event `PlannedPhoto`/`referenceImageUri` mechanism. Leaning toward the new
  column so "the card background" is distinct from the photo-ideas board. Confirm if unsure.

## Next Steps (exact, in order)

1. **Task 3 (b) — quick:** adjust the `PhotoBackdrop` fade alphas in
   [Media.kt](app/src/main/java/com/tripcompanion/app/ui/components/Media.kt) for "slightly more
   transparent." Resolve the ambiguity above first (a small nudge + screenshot is fine).
2. **Task 3 (a) — feature:**
   a. Add `backgroundImageUri: String? = null` to `Event` (domain) and `EventEntity`; update
      `EntityMappers.kt`.
   b. Bump `TripDatabase` to `version = 6`; add `MIGRATION_5_6` (`ALTER TABLE events ADD COLUMN
      backgroundImageUri TEXT`); export `app/schemas/.../6.json`; add a `MigrationTest` 5→6 case.
      **Do not** use destructive migration (shipping policy) even though current test data is disposable.
   c. Add a **compact** picker to
      [EventEditorScreen.kt](app/src/main/java/com/tripcompanion/app/ui/screens/EventEditorScreen.kt)
      — e.g. a small "Card background" row with a thumbnail + Set/Change/Remove — using the
      `PickVisualMedia` + `ImageStorageHelper` pattern. Wire `EventEditorViewModel` (state field +
      `updateBackgroundImage`/`clearBackgroundImage`, include it in the `save()` `Event(...)`).
   d. Update `HomeViewModel.nextUpImageUri` to **prefer** `event.backgroundImageUri` first.
3. Build (`:app:testDebugUnitTest`, `:app:assembleDebug`), install, device-verify (Home next-up shows
   the chosen photo at the new opacity; editor control works).
4. Update this file + `docs/TODO.md`; consider committing the RailRadar + photo + Task 3 work.

**Then the Map & location epic** (see [TODO.md](TODO.md)). Confirm ordering vs. Task 3 with the user.
Quick independent win available any time: the **auto-rotation** fix is a one-line manifest change
(`android:screenOrientation="portrait"` on `MainActivity` in
[AndroidManifest.xml](app/src/main/AndroidManifest.xml)).

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

## Environment / device verification

- **adb** is not on PATH: `C:\Users\agamr\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- **Device id** `00162352E001788` (product AsteroidsIND, model A059) is **flaky** — if "device not
  found," run `adb kill-server` → `adb start-server` → `adb wait-for-device`, then retry.
- **Screenshots:** `adb -s <id> exec-out screencap -p > /e/Projects/Trip/.verify/x.png`, then Read
  the Windows path `E:\Projects\Trip\.verify\x.png` (the `/tmp` path is not readable by the Read tool).
  `.verify/` is scratch — clean it up after. Device is ~1080×2392; taps use original device pixels.
