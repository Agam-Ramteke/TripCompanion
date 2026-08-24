import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * API keys read from `local.properties` at configure time.
 *
 * `local.properties` is not in version control, so no key ever reaches the repository. Each key is
 * empty when absent, and the app degrades on its own:
 *
 * - `RAILRADAR_API_KEY` — `TrainModule` binds the offline timetable projection instead of the live
 *   provider, and the app works, just without live tracking.
 * - `OPENROUTESERVICE_API_KEY` — the trip map draws straight legs between stops instead of
 *   road-following lines. Everything else on the map is unaffected.
 * - `MAPTILER_API_KEY` — the trip map falls back to the keyless CARTO raster basemap instead of the
 *   low-contrast MapTiler style. The map is fully usable either way; only the cartography changes.
 *
 * Set them as `RAILRADAR_API_KEY=…` / `OPENROUTESERVICE_API_KEY=…` / `MAPTILER_API_KEY=…` in
 * `local.properties` to turn those features on.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val railRadarApiKey: String = localProperties.getProperty("RAILRADAR_API_KEY").orEmpty().trim()
val openRouteServiceApiKey: String =
    localProperties.getProperty("OPENROUTESERVICE_API_KEY").orEmpty().trim()
val mapTilerApiKey: String = localProperties.getProperty("MAPTILER_API_KEY").orEmpty().trim()

android {
    namespace = "com.tripcompanion.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tripcompanion.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"

        testInstrumentationRunner = "com.tripcompanion.app.HiltTestRunner"

        // Quoted because buildConfigField writes its value into generated Java verbatim.
        buildConfigField("String", "RAILRADAR_API_KEY", "\"$railRadarApiKey\"")
        buildConfigField("String", "OPENROUTESERVICE_API_KEY", "\"$openRouteServiceApiKey\"")
        buildConfigField("String", "MAPTILER_API_KEY", "\"$mapTilerApiKey\"")

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Needed for the RAILRADAR / OPENROUTESERVICE / MAPTILER API-key fields above.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // MigrationTestHelper builds an old database by reading its exported schema, so the
    // schema directory has to ship inside the instrumentation APK.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Map (OpenStreetMap - no API keys required, full pan/zoom/caching)
    implementation(libs.osmdroid.android)

    // Coil Image Loading
    implementation(libs.coil.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore — theme preference
    implementation(libs.androidx.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.org.json)

    // Instrumented Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}
