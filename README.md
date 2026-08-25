# 🚆 Trip Companion

> **A focused, offline-first travel HUD for Android.**  
> Track trains live, manage detailed itineraries, explore interactive maps, and preserve all your travel memories — entirely private and stored on-device.

---

## ✨ Features

### 📍 Itinerary & Smart Timelines
- **Chronological Timeline**: Unified view of flights, trains, stays, and activities.
- **Dynamic Context**: Surfaces the active or next upcoming event based on real-time clock and location.
- **Media & Photo Attachments**: Attach photos and reference images stored locally inside app-private sandbox.
- **Export & Import**: Full archive backup (JSON + media in ZIP) for seamless migration between devices without third-party cloud dependence.

### 🚂 Indian Railways Live Tracking
- **Real-Time Running Status**: Powered by **RailRadar API** with offline fallback to scheduled timetable.
- **Journey-Segmented Route**: Clear station-by-station progress trimmed specifically from your boarding to deboarding stop.
- **PNR & e-Ticket Parser**: Extract passenger allotments, berths, quota, and coach sequence automatically.
- **Custom Traveler Overrides**: Set manual platform notes or heard delay announcements directly on your itinerary.

### 🗺️ Interactive Maps & Place Search
- **OpenStreetMap Integration**: Powered by OSMDroid with low-contrast, battery-friendly CARTO Dark Matter / Positron vector/raster tiles.
- **Places & Geocoding**: Integrated **Geoapify** and OpenRouteService endpoints for cafés, restaurants, attractions, and turn-by-turn road geometry.
- **Offline Geometry**: Draws straight-line fallback bearings when network is unavailable.

### 🎨 Design System & Aesthetics
- **Two-Font Pairing**:
  - **Poppins** (Bold / SemiBold / Medium): Expressive typography for screen titles, trip headers, and prominent stat figures.
  - **Plus Jakarta Sans** (SemiBold / Medium / Regular): Crisp, modern neo-grotesque for body reading, buttons, tabs, and metadata.
- **AMOLED Dark Mode**: True pitch-black surfaces (`#000000`) for high contrast, minimal glare, and power efficiency on OLED screens.
- **Predictable Motion & Glassmorphism**: Clean Material 3 surfaces, micro-animations, and glanceable HUD layouts.

---

## 🛠️ Architecture & Tech Stack

The application adheres to **Clean Architecture** and **Hexagonal (Ports & Adapters)** principles:

```text
┌────────────────────────────────────────────────────────┐
│                   UI Layer (Compose)                   │
│   Screens, Components, Navigation, ViewModels, Themes  │
└───────────────────────────┬────────────────────────────┘
                            │ Domain Models & State
┌───────────────────────────▼────────────────────────────┐
│                      Domain Layer                      │
│   Use Cases, Repositories, Engine Services, Ports      │
└───────────────────────────▲────────────────────────────┘
                            │ Concrete Implementations
┌───────────────────────────┴────────────────────────────┐
│                       Data Layer                       │
│   Room Database, DataStore, Geoapify, RailRadar, OSM   │
└────────────────────────────────────────────────────────┘
```

- **Language & Runtime**: Kotlin 2.0 (Targeting JVM 17, Android minSdk 26, targetSdk 35)
- **UI Framework**: 100% Jetpack Compose with Material 3
- **Dependency Injection**: Dagger Hilt
- **Local Persistence**: Room DB (SQLite) + Jetpack DataStore Preferences
- **Asynchrony**: Kotlin Coroutines & Reactive `StateFlow` / `Flow`
- **Mapping**: OSMDroid (OpenStreetMap) + CARTO Basemaps
- **Image Loading**: Coil 2

---

## 🚀 Getting Started

### 1. Prerequisites
- **Android Studio Ladybug (or newer)** / Android SDK 35
- **JDK 17**

### 2. Configure API Keys
Create or update `local.properties` in the root directory:

```properties
sdk.dir=C\:\\Users\\<YourUser>\\AppData\\Local\\Android\\Sdk

# Optional: Real-time Transit & Live Tracking
RAILRADAR_API_KEY=your_railradar_api_key_here

# Optional: Places, Geocoding & Road Routing
GEOAPIFY_API_KEY=your_geoapify_api_key_here
OPENROUTESERVICE_API_KEY=your_openrouteservice_api_key_here
```

> **Note**: All keys degrade gracefully when absent. The app operates completely offline using scheduled timetables and straight-line trip geometry if no keys are provided.

### 3. Build and Run

```bash
# Compile and run unit test suite
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug

# Build and install signed Release build directly onto connected device
./gradlew installRelease
```

---

## 🔒 Privacy & Offline Philosophy

- **Zero Tracker SDKs**: No analytics, no advertising networks, and no external tracking pixels.
- **On-Device Storage**: All trips, tickets, notes, and photos stay in sandboxed local app storage.
- **Full Data Ownership**: Export your entire trip history at any time as an open JSON/ZIP archive.
