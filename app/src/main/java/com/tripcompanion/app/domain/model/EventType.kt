package com.tripcompanion.app.domain.model

/**
 * The kinds of thing that can occupy a slot in a trip (spec §8).
 *
 * These are shapes of time, not categories of place: a JOURNEY is time spent
 * getting somewhere, a STAY is time spent with luggage down, a VISIT is time
 * spent at a place, FOOD is a meal, and CUSTOM is everything a plan needs that
 * the other four do not describe.
 *
 * Never branch on the name of a trip, city, or place to decide a type (§4).
 */
enum class EventType {
    JOURNEY,
    VISIT,
    STAY,
    FOOD,
    CUSTOM
}
