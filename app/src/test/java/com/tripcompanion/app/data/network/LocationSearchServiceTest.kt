package com.tripcompanion.app.data.network

import com.tripcompanion.app.data.search.DefaultLocationSearchService
import com.tripcompanion.app.domain.model.SearchResultLocation
import com.tripcompanion.app.domain.service.LocationSearchError
import com.tripcompanion.app.domain.service.LocationSearchException
import com.tripcompanion.app.domain.service.LocationSearchOutcome
import com.tripcompanion.app.domain.service.LocationSearchProvider
import com.tripcompanion.app.domain.service.LocationSearchService
import com.tripcompanion.app.domain.service.SearchViewport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Search behaviour, provider-independent (§11).
 *
 * The interesting half of this file is the failure taxonomy. §11 lists eight things
 * that must be handled — debounce, cancellation, empty results, timeout, network
 * failure, provider failure, malformed data, rate limiting — and seven of them are
 * exercised here through a fake provider. Debounce is the eighth and lives in the
 * ViewModel, which is why the service does not know about it.
 *
 * A fake provider is the whole point: if any test in this file needed Nominatim, the
 * boundary §11 asks for would not exist.
 */
class LocationSearchServiceTest {

    /**
     * A provider that does exactly what the test tells it to.
     *
     * Records the queries and limits it was called with, so tests can check that
     * policy — trimming, the result limit, the viewport bias — reached the provider intact.
     */
    private class FakeProvider(
        private val behaviour: suspend (String, Int) -> List<SearchResultLocation>
    ) : LocationSearchProvider {
        override val providerName: String = "Fake"
        val calls = mutableListOf<Pair<String, Int>>()
        val viewports = mutableListOf<SearchViewport?>()

        override suspend fun search(
            query: String,
            limit: Int,
            viewport: SearchViewport?
        ): List<SearchResultLocation> {
            calls += query to limit
            viewports += viewport
            return behaviour(query, limit)
        }
    }

    private fun serviceReturning(vararg places: SearchResultLocation) =
        DefaultLocationSearchService(FakeProvider { _, _ -> places.toList() })

    private fun serviceFailingWith(error: LocationSearchError) =
        DefaultLocationSearchService(FakeProvider { _, _ -> throw LocationSearchException(error) })

    private fun place(
        name: String,
        lat: Double = 24.5760,
        lon: Double = 73.6832
    ) = SearchResultLocation(
        name = name,
        formattedAddress = "$name, Udaipur, Rajasthan, India",
        latitude = lat,
        longitude = lon
    )

    // ---- Query gating ------------------------------------------------------

    @Test
    fun `a query shorter than the minimum never reaches the provider`() = runTest {
        val provider = FakeProvider { _, _ -> listOf(place("City Palace")) }
        val service = DefaultLocationSearchService(provider)

        assertEquals(LocationSearchOutcome.Empty, service.searchPlaces(""))
        assertEquals(LocationSearchOutcome.Empty, service.searchPlaces("a"))
        assertEquals(LocationSearchOutcome.Empty, service.searchPlaces("   "))
        assertTrue("a one-character query should not cost a request", provider.calls.isEmpty())
    }

    @Test
    fun `the query is trimmed before it is sent`() = runTest {
        val provider = FakeProvider { _, _ -> listOf(place("City Palace")) }
        val service = DefaultLocationSearchService(provider)

        service.searchPlaces("  city palace  ")

        assertEquals("city palace", provider.calls.single().first)
    }

    @Test
    fun `the provider is asked for the service's result limit`() = runTest {
        val provider = FakeProvider { _, _ -> emptyList() }
        DefaultLocationSearchService(provider).searchPlaces("palace")

        assertEquals(LocationSearchService.RESULT_LIMIT, provider.calls.single().second)
    }

    @Test
    fun `a query at exactly the minimum length is allowed through`() = runTest {
        val provider = FakeProvider { _, _ -> listOf(place("Goa")) }
        val outcome = DefaultLocationSearchService(provider).searchPlaces("Go")

        assertTrue(outcome is LocationSearchOutcome.Results)
        assertEquals(1, provider.calls.size)
    }

    // ---- Results and emptiness --------------------------------------------

    @Test
    fun `results come back in the provider's order`() = runTest {
        val outcome = serviceReturning(
            place("City Palace", 24.5760, 73.6832),
            place("Jagdish Temple", 24.5794, 73.6836),
            place("Ambrai Ghat", 24.5789, 73.6796)
        ).searchPlaces("udaipur")

        assertEquals(
            listOf("City Palace", "Jagdish Temple", "Ambrai Ghat"),
            (outcome as LocationSearchOutcome.Results).places.map { it.name }
        )
    }

    @Test
    fun `no matches is Empty, not an empty Results`() = runTest {
        // §11 treats these as different things to say. A screen should not have to
        // infer "nothing found" from a list size.
        val outcome = serviceReturning().searchPlaces("qwertyuiop asdfgh")

        assertEquals(LocationSearchOutcome.Empty, outcome)
        assertFalse(outcome is LocationSearchOutcome.Results)
    }

    @Test
    fun `the same building returned three ways is shown once`() = runTest {
        // Nominatim habitually returns the node, the way and the relation for one
        // place. Three identical rows read as a broken app.
        val outcome = serviceReturning(
            place("City Palace", 24.57601, 73.68321),
            place("City Palace", 24.57602, 73.68322),
            place("City Palace", 24.57603, 73.68323)
        ).searchPlaces("city palace")

        assertEquals(1, (outcome as LocationSearchOutcome.Results).places.size)
    }

    @Test
    fun `two different places with the same name are both kept`() = runTest {
        val outcome = serviceReturning(
            place("Jagdish Temple", 24.5794, 73.6836),
            place("Jagdish Temple", 26.9124, 75.7873)
        ).searchPlaces("jagdish temple")

        assertEquals(2, (outcome as LocationSearchOutcome.Results).places.size)
    }

    @Test
    fun `deduplication ignores case in the name`() = runTest {
        val outcome = serviceReturning(
            place("City Palace", 24.5760, 73.6832),
            place("CITY PALACE", 24.5760, 73.6832)
        ).searchPlaces("city palace")

        assertEquals(1, (outcome as LocationSearchOutcome.Results).places.size)
    }

    // ---- Where the user is looking ----------------------------------------

    /** Udaipur's old city, roughly one screen wide. */
    private val udaipurView = SearchViewport(
        north = 24.60, east = 73.72, south = 24.55, west = 73.65
    )

    @Test
    fun `the viewport reaches the provider untouched`() = runTest {
        val provider = FakeProvider { _, _ -> listOf(place("City Palace")) }

        DefaultLocationSearchService(provider).searchPlaces("palace", udaipurView)

        assertEquals(udaipurView, provider.viewports.single())
    }

    @Test
    fun `with no viewport the provider is told so rather than given a guess`() = runTest {
        val provider = FakeProvider { _, _ -> listOf(place("City Palace")) }

        DefaultLocationSearchService(provider).searchPlaces("palace")

        assertNull(provider.viewports.single())
    }

    @Test
    fun `results are reordered so the nearest one is first`() = runTest {
        // The complaint this fixes: typing a common name and being offered the famous
        // one in another state, with the one on screen four rows down.
        val outcome = serviceReturning(
            place("Jagdish Temple, Jaipur", 26.9124, 75.7873),
            place("Jagdish Temple, Delhi", 28.6139, 77.2090),
            place("Jagdish Temple, Udaipur", 24.5794, 73.6836)
        ).searchPlaces("jagdish temple", udaipurView)

        assertEquals(
            "Jagdish Temple, Udaipur",
            (outcome as LocationSearchOutcome.Results).places.first().name
        )
    }

    @Test
    fun `a far-away match is moved down the list, never removed from it`() = runTest {
        // Ranking is a re-order and nothing more. Someone searching from Udaipur for a
        // place in Delhi is doing something reasonable, and must still find it.
        val outcome = serviceReturning(
            place("Red Fort", 28.6562, 77.2410),
            place("Ambrai Ghat", 24.5789, 73.6796)
        ).searchPlaces("fort", udaipurView)

        assertEquals(
            listOf("Ambrai Ghat", "Red Fort"),
            (outcome as LocationSearchOutcome.Results).places.map { it.name }
        )
    }

    @Test
    fun `an impossible viewport is ignored rather than scrambling the order`() = runTest {
        // A map that has not laid out yet reports a zero-span box. Ranking against its
        // centre is meaningless, so the provider's own order stands.
        val flat = SearchViewport(north = 0.0, east = 0.0, south = 0.0, west = 0.0)

        val outcome = serviceReturning(
            place("Second nearest", 26.9124, 75.7873),
            place("Nearest", 24.5794, 73.6836)
        ).searchPlaces("temple", flat)

        assertEquals(
            listOf("Second nearest", "Nearest"),
            (outcome as LocationSearchOutcome.Results).places.map { it.name }
        )
    }

    // ---- The failure taxonomy (§11) ---------------------------------------

    @Test
    fun `a timeout is reported as a timeout`() = runTest {
        val service = DefaultLocationSearchService(
            FakeProvider { _, _ ->
                delay(LocationSearchService.SEARCH_TIMEOUT_MS + 1_000)
                listOf(place("Too late"))
            }
        )

        // runTest's virtual clock makes this instant rather than an eight-second test.
        val outcome = service.searchPlaces("slow provider")

        assertEquals(
            LocationSearchOutcome.Failed(LocationSearchError.TIMEOUT),
            outcome
        )
    }

    @Test
    fun `a provider that answers just inside the deadline still succeeds`() = runTest {
        val service = DefaultLocationSearchService(
            FakeProvider { _, _ ->
                delay(LocationSearchService.SEARCH_TIMEOUT_MS - 1)
                listOf(place("Just in time"))
            }
        )

        val outcome = service.searchPlaces("nearly slow")

        assertTrue(outcome is LocationSearchOutcome.Results)
    }

    @Test
    fun `network failure, provider failure and rate limiting each keep their identity`() = runTest {
        // Collapsing these would cost the screen its wording: one means retry later,
        // one means this is not your fault, one means wait a moment.
        listOf(
            LocationSearchError.NETWORK_UNAVAILABLE,
            LocationSearchError.PROVIDER_ERROR,
            LocationSearchError.RATE_LIMITED,
            LocationSearchError.MALFORMED_RESPONSE,
            LocationSearchError.TIMEOUT,
            LocationSearchError.UNKNOWN
        ).forEach { error ->
            assertEquals(
                "error $error was reclassified on its way out of the service",
                LocationSearchOutcome.Failed(error),
                serviceFailingWith(error).searchPlaces("palace")
            )
        }
    }

    @Test
    fun `an unclassified exception becomes UNKNOWN rather than escaping`() = runTest {
        // The service's contract is that it never throws — a raw IOException reaching
        // a ViewModel would crash the screen instead of showing a message.
        val service = DefaultLocationSearchService(
            FakeProvider { _, _ -> throw IOException("socket closed") }
        )

        assertEquals(
            LocationSearchOutcome.Failed(LocationSearchError.UNKNOWN),
            service.searchPlaces("palace")
        )
    }

    @Test
    fun `an IllegalStateException from a broken provider does not escape either`() = runTest {
        val service = DefaultLocationSearchService(
            FakeProvider { _, _ -> throw IllegalStateException("provider misconfigured") }
        )

        assertEquals(
            LocationSearchOutcome.Failed(LocationSearchError.UNKNOWN),
            service.searchPlaces("palace")
        )
    }

    // ---- Cancellation -----------------------------------------------------

    @Test
    fun `cancellation propagates instead of becoming a failure`() = runTest {
        val started = CompletableDeferred<Unit>()
        val service = DefaultLocationSearchService(
            FakeProvider { _, _ ->
                started.complete(Unit)
                delay(Long.MAX_VALUE)
                emptyList()
            }
        )

        var outcome: LocationSearchOutcome? = null
        var cancelled = false

        val job = launch {
            try {
                outcome = service.searchPlaces("palace")
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }

        started.await()
        job.cancel()
        job.join()

        assertTrue("a cancelled search must not be swallowed into an outcome", cancelled)
        assertNull("a cancelled search must not produce a result", outcome)
    }

    @Test
    fun `a cancelled search does not report a timeout`() = runTest {
        // The service catches TimeoutCancellationException before CancellationException.
        // Getting that order wrong turns every abandoned keystroke into a red banner.
        val started = CompletableDeferred<Unit>()
        val service = DefaultLocationSearchService(
            FakeProvider { _, _ ->
                started.complete(Unit)
                delay(Long.MAX_VALUE)
                emptyList()
            }
        )

        var outcome: LocationSearchOutcome? = null
        val job = launch {
            runCatching { outcome = service.searchPlaces("palace") }
        }

        started.await()
        job.cancel()
        job.join()

        assertNull(outcome)
    }

    // ---- The Nominatim parser ---------------------------------------------

    private val provider = NominatimLocationSearchProvider()

    @Test
    fun `a well-formed response parses into places`() {
        val json = """
            [
              {
                "place_id": 240109189,
                "lat": "24.5760",
                "lon": "73.6832",
                "display_name": "City Palace, Old City, Udaipur, Rajasthan, India",
                "name": "City Palace",
                "class": "tourism",
                "type": "attraction"
              }
            ]
        """.trimIndent()

        val results = provider.parseResponse(json)

        assertEquals(1, results.size)
        val first = results.single()
        assertEquals("City Palace", first.name)
        assertEquals("City Palace, Old City, Udaipur, Rajasthan, India", first.formattedAddress)
        assertEquals(24.5760, first.latitude, 0.0000001)
        assertEquals(73.6832, first.longitude, 0.0000001)
        assertEquals("Tourism / attraction", first.category)
        assertEquals("240109189", first.providerPlaceId)
        assertEquals(NominatimLocationSearchProvider.PROVIDER_NAME, first.providerName)
    }

    @Test
    fun `an empty array parses to no places, which is not an error`() {
        assertEquals(emptyList<SearchResultLocation>(), provider.parseResponse("[]"))
    }

    @Test
    fun `a malformed document is a failure, not an empty result`() {
        // Reporting "no results" here would tell the user their query was wrong when
        // in fact the provider sent something unreadable.
        listOf("", "not json at all", "{\"error\":\"nope\"}", "[{", "null").forEach { body ->
            val thrown = runCatching { provider.parseResponse(body) }.exceptionOrNull()
            assertTrue(
                "body <$body> should have raised a search exception, got $thrown",
                thrown is LocationSearchException
            )
            assertEquals(
                LocationSearchError.MALFORMED_RESPONSE,
                (thrown as LocationSearchException).error
            )
        }
    }

    @Test
    fun `one unusable row does not cost the user the other rows`() {
        val json = """
            [
              {"lat": "24.5760", "lon": "73.6832", "display_name": "Good One", "name": "Good One"},
              {"display_name": "No Coordinates", "name": "No Coordinates"},
              {"lat": "not-a-number", "lon": "73.6", "name": "Bad Latitude"},
              {"lat": "24.6", "lon": "73.7", "name": ""},
              {"lat": "24.7", "lon": "73.8", "display_name": "Another Good, Udaipur"}
            ]
        """.trimIndent()

        val results = provider.parseResponse(json)

        assertEquals(listOf("Good One", "Another Good"), results.map { it.name })
    }

    @Test
    fun `a row with an out-of-range coordinate is dropped`() {
        val json = """
            [
              {"lat": "91.0", "lon": "73.6", "name": "Above the pole"},
              {"lat": "24.5", "lon": "181.0", "name": "Past the date line"},
              {"lat": "24.5", "lon": "73.6", "name": "Actually on Earth"}
            ]
        """.trimIndent()

        assertEquals(listOf("Actually on Earth"), provider.parseResponse(json).map { it.name })
    }

    @Test
    fun `a missing name falls back to the first part of the display name`() {
        val json = """
            [{"lat": "24.5", "lon": "73.6", "display_name": "Ambrai Ghat, Udaipur, India"}]
        """.trimIndent()

        assertEquals("Ambrai Ghat", provider.parseResponse(json).single().name)
    }

    @Test
    fun `a row with neither name nor display name is dropped`() {
        val json = """[{"lat": "24.5", "lon": "73.6"}]"""
        assertEquals(emptyList<SearchResultLocation>(), provider.parseResponse(json))
    }

    @Test
    fun `category is assembled from whichever of class and type is present`() {
        val json = """
            [
              {"lat":"24.1","lon":"73.1","name":"Both","class":"tourism","type":"museum"},
              {"lat":"24.2","lon":"73.2","name":"ClassOnly","class":"historic"},
              {"lat":"24.3","lon":"73.3","name":"TypeOnly","type":"restaurant"},
              {"lat":"24.4","lon":"73.4","name":"Neither"}
            ]
        """.trimIndent()

        assertEquals(
            listOf("Tourism / museum", "Historic", "Restaurant", ""),
            provider.parseResponse(json).map { it.category }
        )
    }

    @Test
    fun `southern and western coordinates parse with their sign intact`() {
        val json = """
            [{"lat": "-33.8688", "lon": "-70.6693", "name": "Somewhere else"}]
        """.trimIndent()

        val result = provider.parseResponse(json).single()

        assertEquals(-33.8688, result.latitude, 0.0000001)
        assertEquals(-70.6693, result.longitude, 0.0000001)
    }

    @Test
    fun `a place with no place_id still parses`() {
        // providerPlaceId is provenance, not identity (§13). Missing it is survivable.
        val json = """[{"lat": "24.5", "lon": "73.6", "name": "Unregistered corner"}]"""
        assertNull(provider.parseResponse(json).single().providerPlaceId)
    }

    @Test
    fun `a nameless object is named from its name tags rather than a house number`() {
        // display_name for an unnamed OSM way often starts with a number, which would
        // put "12" in the results list where the user expects a place.
        val json = """
            [{
              "lat": "24.5", "lon": "73.6",
              "display_name": "12, Lake Palace Road, Udaipur, India",
              "namedetails": {"official_name": "Lake Pichola Boat Jetty"}
            }]
        """.trimIndent()

        assertEquals("Lake Pichola Boat Jetty", provider.parseResponse(json).single().name)
    }

    @Test
    fun `the plain name wins over the name tags when both are present`() {
        val json = """
            [{
              "lat": "24.5", "lon": "73.6", "name": "City Palace",
              "namedetails": {"alt_name": "Rajmahal"}
            }]
        """.trimIndent()

        assertEquals("City Palace", provider.parseResponse(json).single().name)
    }

    // ---- The Nominatim request --------------------------------------------

    @Test
    fun `a request without a viewport carries no viewbox at all`() {
        val url = provider.buildRequestUrl("city palace", 25, null)

        assertFalse("an absent viewport must not become a box", url.contains("viewbox"))
        assertFalse(url.contains("bounded"))
        assertTrue(url.contains("limit=25"))
    }

    @Test
    fun `a viewport becomes a viewbox in Nominatim's own corner order`() {
        // left, top, right, bottom — west, north, east, south. Getting this order wrong
        // silently biases the search towards the wrong hemisphere.
        val url = provider.buildRequestUrl(
            "palace",
            25,
            SearchViewport(north = 24.60, east = 73.72, south = 24.55, west = 73.65)
        )

        assertTrue(
            "expected a west,north,east,south viewbox in <$url>",
            url.contains("viewbox=73.650000,24.600000,73.720000,24.550000")
        )
    }

    @Test
    fun `the viewbox is a bias and not a boundary`() {
        // bounded=0 is the whole point: results outside the screen still come back, they
        // just come back lower down.
        val url = provider.buildRequestUrl("palace", 25, udaipurView)

        assertTrue(url.contains("bounded=0"))
    }

    @Test
    fun `an unusable viewport is left out of the request`() {
        val url = provider.buildRequestUrl(
            "palace",
            25,
            SearchViewport(north = 0.0, east = 0.0, south = 0.0, west = 0.0)
        )

        assertFalse(url.contains("viewbox"))
    }

    @Test
    fun `the query is encoded rather than pasted into the URL`() {
        val url = provider.buildRequestUrl("café & bar", 25, null)

        assertFalse("a raw ampersand would split the query into two parameters", url.contains("& bar"))
        assertTrue(url.contains("q=caf%C3%A9+%26+bar"))
    }

    @Test
    fun `every request asks for the details the parser depends on`() {
        val url = provider.buildRequestUrl("palace", 25, null)

        assertTrue(url.contains("format=json"))
        assertTrue(url.contains("addressdetails=1"))
        // namedetails feeds the name fallback; dedupe cuts the node/way/relation triplets.
        assertTrue(url.contains("namedetails=1"))
        assertTrue(url.contains("dedupe=1"))
        assertTrue(url.contains("accept-language="))
    }
}
