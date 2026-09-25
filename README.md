# Trip Companion

> **A focused, offline-first travel companion & trip HUD for Android.**  
> Track trains live, manage detailed multi-day itineraries, explore interactive maps with road-following routing, and preserve all your travel memories — entirely private and stored on-device.

---

## Features

### Smart Itinerary & Day Paging
- **Chronological Multi-Day Timeline**: Horizontal date paging with independent vertical scroll positions and swipe gestures.
- **Dynamic Context (Next Up HUD)**: Automatically surfaces the active or next upcoming activity, train, or stay based on real-time clock and trip state.
- **Smart Stay Lifecycle**: Checking into a hotel yields Next Up focus to daytime sightseeing and dining activities until check-out time arrives.
- **Media & Photo Attachments**: Attach reference photos and travel memories stored securely inside the app-private sandbox.
- **Export & Import**: Full archive backup (JSON + media in ZIP) for seamless offline migration between devices.

### 🚂 Indian Railways Live Tracking & Automation
- **Real-Time Running Status**: Powered by **RailRadar API** with offline fallback to scheduled timetables.
- **Journey-Segmented Route**: Station-by-station progress trimmed specifically between your boarding and destination stations.
- **PNR & e-Ticket Parser**: Extract passenger allotments, berths, quotas, and coach sequences automatically from PDF e-tickets.
- **Arrival Automation & Overrides**: Automatically detects and prompts for train arrival with manual station notes and overrides.

### 🗺️ Interactive Maps & Place Search
- **LocationIQ Geocoding**: Accurate street-style forward geocoding and autocomplete for Indian railway stations, hotels, cafes, and attractions with automatic coordinate healing.
- **Geoapify Road Routing & Vector Tiles**: Crisp turn-by-turn road-following polyline geometry and high-performance vector basemaps.
- **Editorial Map HUD**: High-contrast frosted pin badges, rotating refresh indicator, day filter switcher, and thumb-friendly floating action buttons.
- **Offline Geometry**: Seamlessly falls back to direct geodesic bearings when network connectivity is unavailable.

### 🎨 Design System & Aesthetics
- **Two-Font Pairing**:
  - **Poppins** (Bold / SemiBold / Medium): Expressive typography for screen titles, trip headers, and prominent stat figures.
  - **Plus Jakarta Sans** (SemiBold / Medium / Regular): Crisp, modern neo-grotesque for body reading, buttons, tabs, and metadata.
- **AMOLED Dark Mode & Theming**: True pitch-black surfaces (`#000000`) for high contrast, minimal glare, and power efficiency on OLED screens.
- **Predictable Motion System**: Standardized 220–280ms easing tokens, sliding bottom navigation indicator pill, directional tab slides, and tactile press physics.

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
│  Room DB (v7), DataStore, LocationIQ, Geoapify, RailRadar │
└────────────────────────────────────────────────────────┘
```

- **Language & Runtime**: Kotlin 2.0 (Targeting JVM 17, Android minSdk 26, targetSdk 35)
- **UI Framework**: 100% Jetpack Compose with Material 3
- **Dependency Injection**: Dagger Hilt
- **Local Persistence**: Room DB (SQLite, schema v7) + Jetpack DataStore Preferences
- **Asynchrony**: Kotlin Coroutines & Reactive `StateFlow` / `Flow`
- **Mapping**: OSMDroid (OpenStreetMap) + Geoapify Vector Basemaps / CARTO
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

# Places & Geocoding (LocationIQ)
LOCATIONIQ_API_KEY=your_locationiq_api_key_here

# Real-time Transit & Live Tracking (RailRadar)
RAILRADAR_API_KEY=your_railradar_api_key_here

# Road Routing & Vector Styles (Geoapify / OpenRouteService)
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
