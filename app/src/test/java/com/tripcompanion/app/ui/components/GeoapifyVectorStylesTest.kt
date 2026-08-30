package com.tripcompanion.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoapifyVectorStylesTest {

    @Test
    fun `light theme primary style uses osm-bright-smooth`() {
        val url = GeoapifyVectorStyles.getStyleUrl(
            isDark = false,
            apiKey = "test_key_123",
            useFallback = false
        )
        assertEquals(
            "https://maps.geoapify.com/v1/styles/osm-bright-smooth/style.json?apiKey=test_key_123",
            url
        )
    }

    @Test
    fun `light theme fallback style uses positron`() {
        val url = GeoapifyVectorStyles.getStyleUrl(
            isDark = false,
            apiKey = "test_key_123",
            useFallback = true
        )
        assertEquals(
            "https://maps.geoapify.com/v1/styles/positron/style.json?apiKey=test_key_123",
            url
        )
    }

    @Test
    fun `dark theme primary style uses dark-matter`() {
        val url = GeoapifyVectorStyles.getStyleUrl(
            isDark = true,
            apiKey = "test_key_123",
            useFallback = false
        )
        assertEquals(
            "https://maps.geoapify.com/v1/styles/dark-matter/style.json?apiKey=test_key_123",
            url
        )
    }

    @Test
    fun `dark theme fallback style uses dark-matter-brown`() {
        val url = GeoapifyVectorStyles.getStyleUrl(
            isDark = true,
            apiKey = "test_key_123",
            useFallback = true
        )
        assertEquals(
            "https://maps.geoapify.com/v1/styles/dark-matter-brown/style.json?apiKey=test_key_123",
            url
        )
    }

    @Test
    fun `blank apiKey uses openfreemap vector fallback for light and dark`() {
        val lightUrl = GeoapifyVectorStyles.getStyleUrl(
            isDark = false,
            apiKey = "",
            useFallback = false
        )
        val darkUrl = GeoapifyVectorStyles.getStyleUrl(
            isDark = true,
            apiKey = "   ",
            useFallback = false
        )

        assertTrue(lightUrl.contains("openfreemap.org/styles/bright"))
        assertTrue(darkUrl.contains("openfreemap.org/styles/positron"))
    }
}
