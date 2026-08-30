package com.tripcompanion.app.ui.components

import com.tripcompanion.app.BuildConfig

/**
 * Geoapify Vector Basemap Style Endpoints.
 *
 * Configures the MapLibre style JSON URLs for Light and Dark themes,
 * with graceful fallback support.
 */
object GeoapifyVectorStyles {

    const val LIGHT_PRIMARY_STYLE = "osm-bright-smooth"
    const val LIGHT_FALLBACK_STYLE = "positron"

    const val DARK_PRIMARY_STYLE = "dark-matter"
    const val DARK_FALLBACK_STYLE = "dark-matter-brown"

    /**
     * Builds the MapLibre style JSON URL for Geoapify.
     *
     * @param isDark True for Dark theme, false for Light theme.
     * @param apiKey The configured Geoapify API key (defaults to [BuildConfig.GEOAPIFY_API_KEY]).
     * @param useFallback True to use the theme's fallback style, false for primary.
     */
    fun getStyleUrl(
        isDark: Boolean,
        apiKey: String = BuildConfig.GEOAPIFY_API_KEY,
        useFallback: Boolean = false
    ): String {
        val trimmedKey = apiKey.trim()
        val styleName = when {
            isDark && !useFallback -> DARK_PRIMARY_STYLE
            isDark && useFallback -> DARK_FALLBACK_STYLE
            !isDark && !useFallback -> LIGHT_PRIMARY_STYLE
            else -> LIGHT_FALLBACK_STYLE
        }

        return if (trimmedKey.isNotBlank()) {
            "https://maps.geoapify.com/v1/styles/$styleName/style.json?apiKey=$trimmedKey"
        } else {
            // OpenFreeMap vector style fallback if no key is configured
            if (isDark) {
                "https://tiles.openfreemap.org/styles/positron"
            } else {
                "https://tiles.openfreemap.org/styles/bright"
            }
        }
    }

    /**
     * Returns the fallback style URL for the given theme.
     */
    fun getFallbackStyleUrl(
        isDark: Boolean,
        apiKey: String = BuildConfig.GEOAPIFY_API_KEY
    ): String = getStyleUrl(isDark = isDark, apiKey = apiKey, useFallback = true)
}
