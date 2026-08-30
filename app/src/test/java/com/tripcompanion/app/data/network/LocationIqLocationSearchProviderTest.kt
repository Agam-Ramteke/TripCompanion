package com.tripcompanion.app.data.network

import com.tripcompanion.app.data.network.locationiq.LocationIqClient
import com.tripcompanion.app.data.network.locationiq.LocationIqLocationSearchProvider
import com.tripcompanion.app.domain.service.LocationSearchError
import com.tripcompanion.app.domain.service.LocationSearchException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LocationIqLocationSearchProviderTest {

    private val client = LocationIqClient(apiKey = "test_key")
    private val provider = LocationIqLocationSearchProvider(client)

    @Test
    fun providerName_isLocationIQ() {
        assertEquals("LocationIQ", provider.providerName)
    }

    @Test
    fun parseResponse_parsesAutocompleteStationCorrectly() {
        val json = """
            [
              {
                "place_id": "321442004435",
                "lat": "27.1569623",
                "lon": "77.991183",
                "class": "railway",
                "type": "proposed",
                "display_name": "Agra Cantt. Station, Railway Colony, Agra, Uttar Pradesh, 282008, India",
                "display_place": "Agra Cantt. Station",
                "display_address": "Agra Cantonment, Agra, Uttar Pradesh, 282008, India",
                "address": {
                  "name": "Agra Cantt. Station",
                  "city": "Agra",
                  "state": "Uttar Pradesh",
                  "country": "India"
                }
              }
            ]
        """.trimIndent()

        val results = client.parseResponse(json)
        assertEquals(1, results.size)
        val first = results.first()
        assertEquals("Agra Cantt. Station", first.name)
        assertEquals(27.1569623, first.latitude, 0.0001)
        assertEquals(77.991183, first.longitude, 0.0001)
        assertEquals("Transit", first.category)
        assertEquals("LocationIQ", first.providerName)
    }

    @Test
    fun parseResponse_parsesHotelAndCafeCategories() {
        val json = """
            [
              {
                "place_id": "101",
                "lat": "17.3916461",
                "lon": "78.4767902",
                "class": "tourism",
                "type": "hotel",
                "display_place": "Taj Mahal Hotel",
                "display_address": "Hyderabad, Telangana, India"
              },
              {
                "place_id": "102",
                "lat": "12.912761",
                "lon": "77.6448505",
                "class": "amenity",
                "type": "cafe",
                "display_place": "Blue Tokai Coffee",
                "display_address": "Bengaluru, Karnataka, India"
              }
            ]
        """.trimIndent()

        val results = client.parseResponse(json)
        assertEquals(2, results.size)
        assertEquals("Stay", results[0].category)
        assertEquals("Food", results[1].category)
    }

    @Test
    fun parseResponse_emptyJsonOrErrorObjectReturnsEmptyList() {
        assertTrue(client.parseResponse("[]").isEmpty())
        assertTrue(client.parseResponse("").isEmpty())
        assertTrue(client.parseResponse("{\"error\":\"Unable to geocode\"}").isEmpty())
    }

    @Test
    fun parseResponse_malformedJsonThrowsMalformedResponse() {
        try {
            client.parseResponse("not a json string")
            fail("Expected LocationSearchException")
        } catch (e: LocationSearchException) {
            assertEquals(LocationSearchError.MALFORMED_RESPONSE, e.error)
        }
    }
}
