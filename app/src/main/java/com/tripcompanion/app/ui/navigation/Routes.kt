package com.tripcompanion.app.ui.navigation

import com.tripcompanion.app.feature.place.PlaceTab

/**
 * Every route in the app, and the only place that knows how one is spelled.
 *
 * Each destination owns both its pattern and its `createRoute`, so a caller can never build a
 * path the `NavHost` does not declare — the two halves that have to agree are written next to
 * each other. Optional arguments are query parameters, which is what lets a tab open
 * `timeline` with no trip and a card open `timeline?tripId=4`.
 *
 * Argument names are load-bearing: each one is read back by name from a `SavedStateHandle` in
 * the matching ViewModel, so renaming `tripId` here silently breaks that screen. They are all
 * passed as strings and parsed with `toLongOrNull`, so a malformed deep link lands on the
 * screen's empty state rather than crashing.
 */
sealed class Routes(val route: String) {

    // ── The five tabs ──

    data object Home : Routes("home")

    data object TripList : Routes("trips")

    /** The itinerary. With no trip it resolves the current one itself. */
    data object Timeline : Routes("timeline?tripId={tripId}") {
        fun createRoute(tripId: Long? = null) =
            if (tripId != null) "timeline?tripId=$tripId" else "timeline"
    }

    data object Trains : Routes("trains")

    data object More : Routes("more")

    // ── Trips ──

    data object TripEditor : Routes("trip/editor?tripId={tripId}") {
        fun createRoute(tripId: Long? = null) =
            if (tripId != null) "trip/editor?tripId=$tripId" else "trip/editor"
    }

    // ── Activities ──

    /**
     * The activity editor.
     *
     * `locationId` is how Place Detail adds somewhere to the itinerary: it knows the place but
     * not yet the activity, so it hands the place over and the editor opens with it attached.
     */
    data object EventEditor : Routes("event/editor/{tripId}?eventId={eventId}&locationId={locationId}&date={date}") {
        fun createRoute(tripId: Long, eventId: Long? = null, locationId: Long? = null, date: String? = null): String {
            val query = buildList {
                if (eventId != null) add("eventId=$eventId")
                if (locationId != null) add("locationId=$locationId")
                if (date != null) add("date=$date")
            }
            val base = "event/editor/$tripId"
            return if (query.isEmpty()) base else base + "?" + query.joinToString("&")
        }
    }

    data object EventDetail : Routes("event/detail/{eventId}") {
        fun createRoute(eventId: Long) = "event/detail/$eventId"
    }

    data object PhotoEditor : Routes("photo/editor/{eventId}?photoId={photoId}") {
        fun createRoute(eventId: Long, photoId: Long? = null) =
            if (photoId != null) "photo/editor/$eventId?photoId=$photoId"
            else "photo/editor/$eventId"
    }

    // ── Places ──

    /** The places list, optionally opened straight onto one of its three tabs. */
    data object Places : Routes("places?tab={tab}") {
        fun createRoute(tab: PlaceTab? = null) =
            if (tab != null) "places?tab=${tab.name}" else "places"
    }

    data object PlaceDetail : Routes("place/detail/{locationId}") {
        fun createRoute(locationId: Long) = "place/detail/$locationId"
    }

    /**
     * Search-and-pick, used when an activity needs a place attached to it.
     *
     * `tripId` is context, not a filter: the picker points its map at the trip's existing stops
     * so the first search is biased towards the right city. Absent when the picker is opened
     * from the places list, which belongs to no trip.
     *
     * The chosen place comes back through the calling entry's `SavedStateHandle` rather than in
     * a route, because the caller is a half-filled form that must not be recreated.
     */
    data object LocationPicker : Routes("location/picker?tripId={tripId}") {
        fun createRoute(tripId: Long? = null) =
            if (tripId != null) "location/picker?tripId=$tripId" else "location/picker"

        /** The key the picked place's id is handed back under. */
        const val RESULT_LOCATION_ID = "pickedLocationId"

        /** The key its name is handed back under, so the caller need not re-read the row. */
        const val RESULT_LOCATION_NAME = "pickedLocationName"
    }

    // ── Trains ──

    data object TrainDetail : Routes("train/detail/{trainId}") {
        fun createRoute(trainId: Long) = "train/detail/$trainId"
    }

    data object TrainEditor : Routes("train/editor/{tripId}?trainId={trainId}") {
        fun createRoute(tripId: Long, trainId: Long? = null) =
            if (trainId != null) "train/editor/$tripId?trainId=$trainId"
            else "train/editor/$tripId"
    }

    data object TrainTicket : Routes("train/ticket/{trainId}") {
        fun createRoute(trainId: Long) = "train/ticket/$trainId"
    }

    // ── The rest ──

    /** The map. Like the itinerary, it finds the current trip when none is named. */
    data object TripMap : Routes("map?tripId={tripId}") {
        fun createRoute(tripId: Long? = null) =
            if (tripId != null) "map?tripId=$tripId" else "map"
    }

    data object Hotel : Routes("hotel?tripId={tripId}") {
        fun createRoute(tripId: Long? = null) =
            if (tripId != null) "hotel?tripId=$tripId" else "hotel"
    }

    data object Settings : Routes("settings")
}
