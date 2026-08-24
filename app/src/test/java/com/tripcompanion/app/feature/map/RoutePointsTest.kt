package com.tripcompanion.app.feature.map

import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Location
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/**
 * [routePoints] is the one piece of the map's route line that can be tested without a device: it
 * promises the polyline walks the stops in the exact order the pins arrive, and drops nothing.
 *
 * The ordering is the whole point — a route line that visits three places out of order is worse
 * than no line, because it draws a plan the traveller never made. So the tests pin order, not just
 * membership, and use coordinates a reversal or a set would visibly scramble.
 */
class RoutePointsTest {

    private var nextId = 1L

    /** A pin at a coordinate; the times and title are filler — only the place's lat/lon matter. */
    private fun pinAt(latitude: Double, longitude: Double, order: Int): MapPin {
        val id = nextId++
        val start = LocalDateTime.of(2026, 1, 1, 9, 0)
        return MapPin(
            event = Event(
                id = id,
                tripId = 1L,
                type = EventType.VISIT,
                title = "Stop $id",
                startTime = start,
                endTime = start.plusHours(1),
                locationId = id
            ),
            place = Location(id = id, name = "Place $id", latitude = latitude, longitude = longitude),
            orderInDay = order
        )
    }

    @Test
    fun `each pin becomes its place's latitude then longitude`() {
        val points = routePoints(listOf(pinAt(24.57, 73.68, 1)))
        assertEquals(listOf(24.57 to 73.68), points)
    }

    @Test
    fun `the line follows pin order, not coordinate order`() {
        // Deliberately not sorted by latitude or longitude: if the function sorted or de-duped,
        // this sequence would come back rearranged.
        val pins = listOf(
            pinAt(24.60, 73.70, 1),
            pinAt(24.55, 73.68, 2),
            pinAt(24.58, 73.75, 3)
        )

        val points = routePoints(pins)

        assertEquals(
            listOf(24.60 to 73.70, 24.55 to 73.68, 24.58 to 73.75),
            points
        )
    }

    @Test
    fun `repeated coordinates are all kept, so a there-and-back route still closes`() {
        // Returning to the hotel means the same point appears twice; a de-dupe would erase the leg.
        val hotel = 24.57 to 73.68
        val pins = listOf(
            pinAt(hotel.first, hotel.second, 1),
            pinAt(24.59, 73.71, 2),
            pinAt(hotel.first, hotel.second, 3)
        )

        val points = routePoints(pins)

        assertEquals(3, points.size)
        assertEquals(hotel, points.first())
        assertEquals(hotel, points.last())
    }

    @Test
    fun `no pins is no line`() {
        assertEquals(emptyList<Pair<Double, Double>>(), routePoints(emptyList()))
    }
}
