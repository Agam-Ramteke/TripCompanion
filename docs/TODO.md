# TODO.md

Work queue for Trip Companion. Living document — check items off, move them between sections, and add
new ones as they surface. Detailed *in-flight* status (what's half-done, what to do next step by
step) lives in [CURRENT_STATE.md](CURRENT_STATE.md); this file is the backlog overview.

**Last updated:** 2026-08-30

---

## 🔴 Current (active now)

- [x] **Strict LocationIQ Forward Geocoding, Station Resolution, and Auto-Healing (ADR-026) — DONE & VERIFIED (2026-08-30).**
      Integrated LocationIQ forward geocoding & autocomplete with strict `countrycodes=in` constraint;
      eliminated third-party fallback search providers; updated `resolveStationLocation` to validate Indian
      coordinates (`lat in 6..38, lon in 68..98`) and auto-heal stale database entities; added on-map
      Refresh Locations with rotating spinner.
- [x] **Hotel Stay Check-In Lifecycle & Next-Up Daytime Activity Progression (ADR-027) — DONE & VERIFIED (2026-08-30).**
      Once checked in (`actualStartTime != null`), active stay events yield Next Up focus to daytime
      intermediate sightseeing and food events until check-out time arrives; comprehensive unit test
      `testStayCheckIn_advancesToIntermediateActivitiesAndThenCheckOut` passing.
- [x] **Editorial Map HUD & Pin Marker Badges (ADR-028) — DONE & VERIFIED (2026-08-30).**
      Three-pill balanced top bar with Back, flex `DayTripPill`, and animated Refresh action; dedicated
      right-side Recenter FAB; high-contrast frosted callout badges for map pins with border strokes and
      shadows; full-width scrollable day filter chips.
- [x] **Database Migration to Schema v7 — DONE (2026-08-30).**
      Added `actualBoardingTime`, `actualArrivalTime`, `arrivalSource` to `TrainEntity` (`MIGRATION_6_7`).
- [x] **Release APK Build & On-Device Verification — DONE (2026-08-30).**
      Clean release build assembled and installed on physical device `00162352E001788` via ADB.

## 🟠 Next (soon, after current)

- [ ] **Confirm the RailRadar API key.** Verify `rg_…` vs `rr_live_…` in the RailRadar dashboard;
      watch `logcat` for `401` on a live fetch. If rejected, re-issue. (See
      [API_CONTRACTS.md](API_CONTRACTS.md).)
- [ ] **Verify RailRadar time format** against a real live response (`HH:mm` vs ISO) and tighten the
      parser if needed (currently accepts both) — [API_CONTRACTS.md](API_CONTRACTS.md).

## 🟢 Backlog (future milestones)

- [ ] **Offline tiles pre-caching** (stretch from original prompt) — pre-fetch OSM tiles along trip
      bounding box before travel.
- [ ] **Export trip as GPX / KML** — for loading routes into Garmin / OsmAnd.
- [ ] **Trip statistics sheet** — total distance travelled, days on the road, count of stops by type.
- [ ] **Multi-passenger PNR tracking** — surface individual seat numbers and status changes per
      passenger on live status updates.
- [ ] **Train station platform numbers** — where reported by RailRadar, show the platform badge on the
      train card.
