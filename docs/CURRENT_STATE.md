# CURRENT_STATE.md — live project handoff

> **Read this first after any compaction or new session.** It is the single place that says what
> is being worked on right now and what to do next. Update it whenever major implementation state
> changes, and always before ending a session or running low on context.
>
> Source code is the ultimate truth; this file is the memory of *intent and progress* that the code
> alone doesn't carry.

**Last updated:** 2026-08-30 · **App version:** 3.0.5 (`versionCode 4`) · **DB schema:** v7 · **Branch:** `main`

---

## Completed Highlights (2026-08-30)

1. **Strict LocationIQ Geocoding & Place Search Integration:**
   - Exclusively queries LocationIQ forward geocoding and autocomplete endpoints (`countrycodes=in`), eliminating unreliable fallbacks.
   - Idempotent station resolution (`resolveStationLocation`) validates that stored coordinates are within Indian boundaries (`lat in 6..38, lon in 68..98`) and auto-heals stale/out-of-bounds database rows.
   - Added manual **Refresh Locations** capability with spinning indicator in the Map HUD.

2. **Hotel Stay Check-In Lifecycle (Next Up Activity Progression):**
   - Updated `TripStateEngine.kt`: once an active hotel stay is checked in (`actualStartTime != null`), it yields `currentEvent` / `nextEvent` focus to pending intermediate daytime activities (sightseeing, food, tours, etc.).
   - The stay returns to the Next Up HUD card only when intermediate activities are completed and check-out time arrives.
   - Comprehensive unit test `testStayCheckIn_advancesToIntermediateActivitiesAndThenCheckOut` added and verified.

3. **Editorial Map HUD & Pin Badge Overhaul:**
   - **Balanced Top Header**: Replaced crowded layout with a three-pill header (Back, flexible `DayTripPill`, and animated Refresh action).
   - **Frosted Callout Badges**: Replaced raw text labels with high-contrast frosted surface badges (`1. Udaipur City`, `3. Agra Cantt`) with subtle drop shadows and border strokes for 100% legibility on light and dark maps.
   - **Right-Side Recenter FAB**: Floating action button positioned above the bottom sheet for ergonomic thumb reach.
   - **Scrollable Day Switcher**: Laid out `All days`, `Day 1`, `Day 2`... chips with full width and proper spacing.

4. **Database Migration to Schema v7:**
   - Added `actualBoardingTime`, `actualArrivalTime`, and `arrivalSource` to `TrainEntity` (`MIGRATION_6_7`).
   - Clean Room DB migration with zero destructive fallbacks.

---

## Test & Build Verification

- **Unit Test Suite:** `./gradlew testDebugUnitTest` — **BUILD SUCCESSFUL** (31 tasks, all tests passing).
- **Release APK:** `./gradlew assembleRelease` — **BUILD SUCCESSFUL**.
- **On-Device Run:** Installed and verified on physical Android device (`00162352E001788`).
  - Place, trip, and train deduplication active.
  - More screen reactively updates.
- ✅ **Release Build & Installation — SUCCESS (2026-08-27).**
  - Full unit test suite passed (478 passing tests).
  - Release APK built (`assembleRelease`) and installed to physical device `00162352E001788` via ADB.

---

## Modified Files

- `app/src/main/java/com/tripcompanion/app/ui/theme/MotionTokens.kt`
- `app/src/main/java/com/tripcompanion/app/MainActivity.kt`
- `app/src/main/java/com/tripcompanion/app/ui/navigation/AppNavHost.kt`
- `app/src/main/java/com/tripcompanion/app/ui/components/AppPrimitives.kt`
- `app/src/main/java/com/tripcompanion/app/ui/components/Timeline.kt`
- `app/src/main/java/com/tripcompanion/app/ui/components/TeardropPinMarker.kt`
- `app/src/main/java/com/tripcompanion/app/ui/screens/TimelineScreen.kt`
- `app/src/main/java/com/tripcompanion/app/ui/screens/MoreScreen.kt`
- `app/src/main/java/com/tripcompanion/app/ui/screens/EventEditorScreen.kt`
- `app/src/main/java/com/tripcompanion/app/ui/screens/TripEditorScreen.kt`
- `app/src/main/java/com/tripcompanion/app/feature/trip/TimelineViewModel.kt`
- `app/src/main/java/com/tripcompanion/app/feature/more/MoreViewModel.kt`
- `app/src/main/java/com/tripcompanion/app/data/local/dao/LocationDao.kt`
- `app/src/main/java/com/tripcompanion/app/data/local/dao/TrainDao.kt`
- `app/src/main/java/com/tripcompanion/app/data/local/dao/TripDao.kt`
- `app/src/main/java/com/tripcompanion/app/data/repository/LocationRepositoryImpl.kt`
- `app/src/main/java/com/tripcompanion/app/data/SampleTripSeeder.kt`
- `app/src/test/java/com/tripcompanion/app/data/local/fake/InMemoryTripDatabase.kt`
- `docs/API_CONTRACTS.md`
- `docs/ARCHITECTURE.md`
- `docs/CURRENT_STATE.md`
- `docs/DECISIONS.md`
- `docs/TODO.md`

---

## Next Steps

1. Ready for user testing and feedback on physical device.
2. Backlog: Proximity-first search, offline tile pre-caching, GPX export.
