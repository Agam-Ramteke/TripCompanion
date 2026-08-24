package com.tripcompanion.app.data.transfer

import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The trip file is a hand-written contract (see [TripManifest]'s own KDoc): a field the encoder or
 * decoder forgets does not fail to compile, it just goes quietly missing on the receiving phone.
 * These tests are the loud failure that omission otherwise wouldn't get — they round-trip a bundle
 * through [TripManifest.encode] and [TripManifest.decode] and check the pieces come back.
 *
 * The field under the microscope is [Event.backgroundImageUri] (Task 3): the manifest stores it as
 * an archive entry name like the trip cover and place photos do, and — being optional — it must
 * both survive when present and read back as null when a file predates it.
 */
class TripManifestTest {

    private val exportedAt = LocalDateTime.of(2026, 8, 24, 9, 0)

    private fun bundleOf(vararg events: Event) = TripBundle(
        trip = Trip(
            id = 1,
            name = "Round Trip",
            startDate = LocalDate.of(2026, 11, 1),
            endDate = LocalDate.of(2026, 11, 5)
        ),
        places = emptyList(),
        events = events.toList(),
        activities = emptyMap(),
        photos = emptyMap(),
        stays = emptyMap(),
        trains = emptyList(),
        stops = emptyMap()
    )

    private fun eventOf(id: Long, title: String, background: String?) = Event(
        id = id,
        tripId = 1,
        type = EventType.VISIT,
        title = title,
        startTime = LocalDateTime.of(2026, 11, 2, 14, 0),
        endTime = LocalDateTime.of(2026, 11, 2, 17, 0),
        backgroundImageUri = background
    )

    @Test
    fun `an activity's card background survives the round trip as its archive name`() {
        // The exporter has already swapped the device URI for an archive entry name by the time the
        // manifest sees it (TripTransferServiceImpl.images.carry), so that is what round-trips here.
        val bundle = bundleOf(eventOf(25, "Museum Tour", "images/img_0.jpg"))

        val decoded = TripManifest.decode(TripManifest.encode(bundle, exportedAt))

        assertEquals("images/img_0.jpg", decoded.events.single().backgroundImageUri)
    }

    @Test
    fun `the manifest text actually carries the background field`() {
        // Guards the encode side directly: were `putIfPresent("backgroundImage", ...)` dropped, the
        // decode assertion above could still pass off a default, but this would not.
        val manifest = TripManifest.encode(bundleOf(eventOf(25, "Museum Tour", "images/img_0.jpg")), exportedAt)

        assertTrue("manifest should name the background image", manifest.contains("backgroundImage"))
        assertTrue("manifest should carry its archive value", manifest.contains("images/img_0.jpg"))
    }

    @Test
    fun `an event with no chosen background reads back as null`() {
        // Also the shape of every trip exported before this field existed: the key is simply absent,
        // and the forgiving reader must render that as "no background", never as an empty string.
        val bundle = bundleOf(eventOf(30, "Sunset Walk", background = null))

        val decoded = TripManifest.decode(TripManifest.encode(bundle, exportedAt))

        assertNull(decoded.events.single().backgroundImageUri)
    }

    @Test
    fun `backgrounds stay matched to their own events across a mixed export`() {
        // Order is the only thing tying a decoded event back to its origin here, so a set and an
        // unset event together prove the field is not being smeared across rows.
        val bundle = bundleOf(
            eventOf(25, "Museum Tour", "images/img_0.jpg"),
            eventOf(30, "Sunset Walk", background = null)
        )

        val decoded = TripManifest.decode(TripManifest.encode(bundle, exportedAt))

        val museum = decoded.events.single { it.title == "Museum Tour" }
        val sunset = decoded.events.single { it.title == "Sunset Walk" }
        assertEquals("images/img_0.jpg", museum.backgroundImageUri)
        assertNull(sunset.backgroundImageUri)
    }
}
