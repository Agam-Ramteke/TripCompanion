package com.tripcompanion.app.data.network.geoapify

import com.tripcompanion.app.domain.service.RoutePoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoapifyClientTest {

    @Test
    fun `isConfigured returns true when apiKey is present`() {
        val client = GeoapifyClient(apiKey = "test_key_123")
        assertTrue(client.isConfigured)
    }

    @Test
    fun `isConfigured returns false when apiKey is blank`() {
        val client = GeoapifyClient(apiKey = "")
        assertFalse(client.isConfigured)
    }

    @Test
    fun `location provider returns expected provider name`() {
        val client = GeoapifyClient(apiKey = "test_key")
        val provider = GeoapifyLocationSearchProvider(client)
        assertEquals("Geoapify", provider.providerName)
    }

    @Test
    fun `route provider isConfigured delegates to client`() {
        val client1 = GeoapifyClient(apiKey = "test_key")
        val provider1 = GeoapifyRoutePlanProvider(client1)
        assertTrue(provider1.isConfigured)

        val client2 = GeoapifyClient(apiKey = "")
        val provider2 = GeoapifyRoutePlanProvider(client2)
        assertFalse(provider2.isConfigured)
    }
}
