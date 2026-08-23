package com.tripcompanion.app

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import java.io.File

@HiltAndroidApp
class TripCompanionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        configureMapTiles()
    }

    /**
     * osmdroid needs three things set before a MapView is ever constructed.
     *
     * The user agent is not optional: OSM's tile servers refuse requests that do
     * not identify the client, and the failure looks like a blank map rather than
     * an error. The cache paths are moved into app-private storage so tile caching
     * needs no storage permission and is cleaned up with the app.
     */
    private fun configureMapTiles() {
        val config = Configuration.getInstance()
        // osmdroid's own preference file rather than the app's default one, so map
        // bookkeeping never collides with a real user setting.
        config.load(this, getSharedPreferences(OSMDROID_PREFS, Context.MODE_PRIVATE))
        config.userAgentValue = packageName

        val basePath = File(cacheDir, "osmdroid").apply { mkdirs() }
        config.osmdroidBasePath = basePath
        config.osmdroidTileCache = File(basePath, "tiles").apply { mkdirs() }
    }

    private companion object {
        const val OSMDROID_PREFS = "osmdroid_config"
    }
}
