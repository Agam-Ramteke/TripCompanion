package com.tripcompanion.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tripcompanion.app.ui.screens.EventDetailScreen
import com.tripcompanion.app.ui.screens.EventEditorScreen
import com.tripcompanion.app.ui.screens.HomeScreen
import com.tripcompanion.app.ui.screens.HotelScreen
import com.tripcompanion.app.ui.screens.LocationPickerScreen
import com.tripcompanion.app.ui.screens.MoreScreen
import com.tripcompanion.app.ui.screens.PlaceDetailScreen
import com.tripcompanion.app.ui.screens.PlacesScreen
import com.tripcompanion.app.ui.screens.PlannedPhotoEditorScreen
import com.tripcompanion.app.ui.screens.SettingsScreen
import com.tripcompanion.app.ui.screens.TimelineScreen
import com.tripcompanion.app.ui.screens.TrainDetailScreen
import com.tripcompanion.app.ui.screens.TrainEditorScreen
import com.tripcompanion.app.ui.screens.TrainTicketScreen
import com.tripcompanion.app.ui.screens.TrainsScreen
import com.tripcompanion.app.ui.screens.TripEditorScreen
import com.tripcompanion.app.ui.screens.TripListScreen
import com.tripcompanion.app.ui.screens.TripMapScreen
import com.tripcompanion.app.ui.theme.AppThemeExtended

/**
 * One tab: where it goes, and the two states of its icon.
 *
 * The filled icon is not decoration — it carries the selected state a second time, so the tab
 * still reads as chosen without relying on the blue alone.
 */
private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

/**
 * The five places the bottom bar goes.
 *
 * The bar used to be labelled "Trips" and navigate to Places, which left the trips list
 * reachable only by accident. Each tab now goes where its word says.
 */
private val BottomTabs = listOf(
    BottomTab(Routes.Home.route, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    BottomTab(Routes.TripList.route, "Trips", Icons.Outlined.Luggage, Icons.Filled.Luggage),
    BottomTab(
        Routes.Timeline.route,
        "Itinerary",
        Icons.Outlined.CalendarMonth,
        Icons.Filled.CalendarMonth
    ),
    BottomTab(Routes.Trains.route, "Trains", Icons.Outlined.Train, Icons.Filled.Train),
    BottomTab(Routes.More.route, "More", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz)
)

/**
 * The whole app's navigation.
 *
 * The bar appears on exactly the five tab roots and nowhere else: everything deeper is pushed
 * and carries its own back button, so a detail screen is never half-tab and half-page.
 *
 * The `Scaffold`'s padding is applied to the `NavHost` rather than screen by screen. That is
 * what keeps every screen clear of the status bar and the system gesture area with one line
 * instead of nineteen — and it means a new screen is inset-safe the moment it is added.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showBottomBar = BottomTabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                val colors = AppThemeExtended.colors
                val metrics = AppThemeExtended.metrics
                Column {
                    // A hairline instead of a shadow: the bar is the same white as the cards
                    // above it, so it needs an edge rather than a lift.
                    HorizontalDivider(
                        thickness = metrics.dividerWidth,
                        color = MaterialTheme.colorScheme.outline
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        BottomTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTopLevel(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.accent,
                                    selectedTextColor = colors.accent,
                                    indicatorColor = colors.accentSoft,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {

            // ── Tab 1 · Home ──

            composable(Routes.Home.route) {
                HomeScreen(
                    onNavigateToItinerary = { tripId ->
                        navController.navigate(Routes.Timeline.createRoute(tripId))
                    },
                    onNavigateToEventDetail = { eventId ->
                        navController.navigate(Routes.EventDetail.createRoute(eventId))
                    },
                    onNavigateToAddEvent = { tripId ->
                        navController.navigate(Routes.EventEditor.createRoute(tripId))
                    },
                    onNavigateToNewTrip = {
                        navController.navigate(Routes.TripEditor.createRoute())
                    },
                    onNavigateToTrips = {
                        navController.navigateToTopLevel(Routes.TripList.route)
                    },
                    onNavigateToPlaces = {
                        navController.navigate(Routes.Places.createRoute())
                    },
                    onNavigateToTrains = {
                        navController.navigateToTopLevel(Routes.Trains.route)
                    },
                    onNavigateToTrainDetail = { trainId ->
                        navController.navigate(Routes.TrainDetail.createRoute(trainId))
                    },
                    onNavigateToMap = {
                        navController.navigate(Routes.TripMap.createRoute())
                    },
                    onNavigateToHotel = {
                        navController.navigate(Routes.Hotel.createRoute())
                    },
                    onNavigateToSettings = {
                        navController.navigate(Routes.Settings.route)
                    }
                )
            }

            // ── Tab 2 · Trips ──

            composable(Routes.TripList.route) {
                TripListScreen(
                    onNavigateToTrip = { tripId ->
                        navController.navigate(Routes.Timeline.createRoute(tripId))
                    },
                    onNavigateToNewTrip = {
                        navController.navigate(Routes.TripEditor.createRoute())
                    },
                    onNavigateToEditTrip = { tripId ->
                        navController.navigate(Routes.TripEditor.createRoute(tripId))
                    }
                )
            }

            // ── Tab 3 · Itinerary ──
            //
            // One route, not two. There used to be a `timeline/{tripId}` and a
            // `timeline?tripId={tripId}` with identical bodies, which meant the same screen
            // could be reached two ways and drift in one of them.
            composable(
                Routes.Timeline.route,
                arguments = listOf(nullableLongArg("tripId"))
            ) {
                TimelineScreen(
                    onNavigateToEventDetail = { eventId ->
                        navController.navigate(Routes.EventDetail.createRoute(eventId))
                    },
                    onNavigateToAddEvent = { tripId ->
                        navController.navigate(Routes.EventEditor.createRoute(tripId))
                    },
                    onNavigateToEditEvent = { tripId, eventId ->
                        navController.navigate(Routes.EventEditor.createRoute(tripId, eventId))
                    },
                    onNavigateToMap = { tripId ->
                        navController.navigate(Routes.TripMap.createRoute(tripId))
                    },
                    onNavigateToTrainDetail = { trainId ->
                        navController.navigate(Routes.TrainDetail.createRoute(trainId))
                    },
                    onNavigateToEditTrain = { tripId, trainId ->
                        navController.navigate(Routes.TrainEditor.createRoute(tripId, trainId))
                    },
                    onNavigateToNewTrip = {
                        navController.navigate(Routes.TripEditor.createRoute())
                    }
                )
            }

            // ── Tab 4 · Trains ──

            composable(Routes.Trains.route) {
                TrainsScreen(
                    onNavigateToTrainDetail = { trainId ->
                        navController.navigate(Routes.TrainDetail.createRoute(trainId))
                    },
                    onNavigateToTicket = { trainId ->
                        navController.navigate(Routes.TrainTicket.createRoute(trainId))
                    },
                    onNavigateToAddTrain = { tripId ->
                        navController.navigate(Routes.TrainEditor.createRoute(tripId))
                    },
                    onNavigateToEditTrain = { tripId, trainId ->
                        navController.navigate(Routes.TrainEditor.createRoute(tripId, trainId))
                    },
                    onNavigateToNewTrip = {
                        navController.navigate(Routes.TripEditor.createRoute())
                    }
                )
            }

            // ── Tab 5 · More ──

            composable(Routes.More.route) {
                MoreScreen(
                    onNavigateToMap = { tripId ->
                        navController.navigate(Routes.TripMap.createRoute(tripId))
                    },
                    onNavigateToHotel = { tripId ->
                        navController.navigate(Routes.Hotel.createRoute(tripId))
                    },
                    onNavigateToPlaces = { tab ->
                        navController.navigate(Routes.Places.createRoute(tab))
                    },
                    onNavigateToItinerary = { tripId ->
                        navController.navigate(Routes.Timeline.createRoute(tripId))
                    },
                    onNavigateToSettings = {
                        navController.navigate(Routes.Settings.route)
                    }
                )
            }

            // ── Trips ──

            composable(
                Routes.TripEditor.route,
                arguments = listOf(nullableLongArg("tripId"))
            ) {
                TripEditorScreen(onNavigateBack = { navController.popBackStack() })
            }

            // ── Activities ──

            composable(
                Routes.EventEditor.route,
                arguments = listOf(
                    requiredLongArg("tripId"),
                    nullableLongArg("eventId"),
                    nullableLongArg("locationId")
                )
            ) { entry ->
                // The picked place is read from *this* entry's handle, which is the one the
                // picker writes to. The editor's ViewModel has a SavedStateHandle of its own,
                // and observing that one instead is how a chosen place used to vanish: two
                // different handles, the write landing in the one nobody was watching.
                val pickedId by entry.savedStateHandle
                    .getStateFlow<Long?>(Routes.LocationPicker.RESULT_LOCATION_ID, null)
                    .collectAsStateWithLifecycle()
                val pickedName by entry.savedStateHandle
                    .getStateFlow(Routes.LocationPicker.RESULT_LOCATION_NAME, "")
                    .collectAsStateWithLifecycle()

                EventEditorScreen(
                    pickedLocationId = pickedId,
                    pickedLocationName = pickedName,
                    onPickedLocationHandled = {
                        // Cleared once applied, or coming back to this form a second time
                        // would silently re-attach the place the user just removed.
                        entry.savedStateHandle
                            .remove<Long>(Routes.LocationPicker.RESULT_LOCATION_ID)
                        entry.savedStateHandle
                            .remove<String>(Routes.LocationPicker.RESULT_LOCATION_NAME)
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLocationPicker = { tripId ->
                        // A new activity has no id yet, and the picker has nothing to do with a
                        // zero — it hands its answer back up the stack either way.
                        navController.navigate(
                            Routes.LocationPicker.createRoute(tripId.takeIf { it != 0L })
                        )
                    }
                )
            }

            composable(
                Routes.EventDetail.route,
                arguments = listOf(requiredLongArg("eventId"))
            ) {
                EventDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEventEditor = { tripId, eventId ->
                        navController.navigate(Routes.EventEditor.createRoute(tripId, eventId))
                    },
                    onNavigateToEvent = { targetId ->
                        // Previous/Next replace this screen rather than stacking, so walking a
                        // day's activities doesn't build a back stack twelve deep.
                        navController.navigate(Routes.EventDetail.createRoute(targetId)) {
                            popUpTo(Routes.EventDetail.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToPhotoEditor = { eventId, photoId ->
                        navController.navigate(Routes.PhotoEditor.createRoute(eventId, photoId))
                    },
                    onNavigateToPlace = { locationId ->
                        navController.navigate(Routes.PlaceDetail.createRoute(locationId))
                    }
                )
            }

            composable(
                Routes.PhotoEditor.route,
                arguments = listOf(requiredLongArg("eventId"), nullableLongArg("photoId"))
            ) {
                PlannedPhotoEditorScreen(onNavigateBack = { navController.popBackStack() })
            }

            // ── Places ──

            composable(
                Routes.Places.route,
                arguments = listOf(
                    navArgument("tab") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                PlacesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPlaceDetail = { locationId ->
                        navController.navigate(Routes.PlaceDetail.createRoute(locationId))
                    },
                    onNavigateToAddPlace = {
                        navController.navigate(Routes.LocationPicker.createRoute())
                    }
                )
            }

            composable(
                Routes.PlaceDetail.route,
                arguments = listOf(requiredLongArg("locationId"))
            ) {
                PlaceDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onAddToItinerary = { tripId, locationId ->
                        // The place travels in the route, so the editor opens with it already
                        // attached instead of asking for it again.
                        navController.navigate(
                            Routes.EventEditor.createRoute(tripId, locationId = locationId)
                        )
                    },
                    onNavigateToEvent = { eventId ->
                        navController.navigate(Routes.EventDetail.createRoute(eventId))
                    },
                    onNavigateToPlace = { locationId ->
                        navController.navigate(Routes.PlaceDetail.createRoute(locationId)) {
                            popUpTo(Routes.PlaceDetail.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToNewTrip = {
                        navController.navigate(Routes.TripEditor.createRoute())
                    }
                )
            }

            composable(
                Routes.LocationPicker.route,
                arguments = listOf(nullableLongArg("tripId"))
            ) {
                LocationPickerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onLocationSelected = { locationId, locationName ->
                        // Handed back up the stack rather than down: the caller is already
                        // holding a half-filled form, so it must not be recreated. The name
                        // goes first, so the id — which is what the caller watches — is never
                        // the newer of the two.
                        navController.previousBackStackEntry?.savedStateHandle?.apply {
                            set(Routes.LocationPicker.RESULT_LOCATION_NAME, locationName)
                            set(Routes.LocationPicker.RESULT_LOCATION_ID, locationId)
                        }
                        navController.popBackStack()
                    }
                )
            }

            // ── Trains ──

            composable(
                Routes.TrainDetail.route,
                arguments = listOf(requiredLongArg("trainId"))
            ) {
                TrainDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTicket = { trainId ->
                        navController.navigate(Routes.TrainTicket.createRoute(trainId))
                    },
                    onNavigateToEditTrain = { tripId, trainId ->
                        navController.navigate(Routes.TrainEditor.createRoute(tripId, trainId))
                    },
                    onNavigateToEvent = { eventId ->
                        navController.navigate(Routes.EventDetail.createRoute(eventId))
                    }
                )
            }

            composable(
                Routes.TrainEditor.route,
                arguments = listOf(requiredLongArg("tripId"), nullableLongArg("trainId"))
            ) {
                TrainEditorScreen(
                    onNavigateBack = { navController.popBackStack() },
                    // The saved id is deliberately unused: going back is where both entry
                    // points already show the train that was just saved.
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(
                Routes.TrainTicket.route,
                arguments = listOf(requiredLongArg("trainId"))
            ) {
                TrainTicketScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEditTrain = { tripId, trainId ->
                        navController.navigate(Routes.TrainEditor.createRoute(tripId, trainId))
                    },
                    // The passenger name is a setting, so the ticket sends you to where it
                    // lives rather than editing it in two places.
                    onNavigateToProfile = { navController.navigate(Routes.Settings.route) }
                )
            }

            // ── The rest ──

            composable(
                Routes.TripMap.route,
                arguments = listOf(nullableLongArg("tripId"))
            ) {
                TripMapScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEvent = { eventId ->
                        navController.navigate(Routes.EventDetail.createRoute(eventId))
                    },
                    onNavigateToItinerary = { tripId ->
                        navController.navigate(Routes.Timeline.createRoute(tripId))
                    }
                )
            }

            composable(
                Routes.Hotel.route,
                arguments = listOf(nullableLongArg("tripId"))
            ) {
                HotelScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEvent = { eventId ->
                        navController.navigate(Routes.EventDetail.createRoute(eventId))
                    },
                    onNavigateToItinerary = { tripId ->
                        navController.navigate(Routes.Timeline.createRoute(tripId))
                    }
                )
            }

            composable(Routes.Settings.route) {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * A required id, carried as a string.
 *
 * Ids travel as strings and are parsed with `toLongOrNull` on arrival, so a malformed deep link
 * lands on the screen's empty state instead of throwing inside the navigation library.
 */
private fun requiredLongArg(name: String) = navArgument(name) {
    type = NavType.StringType
}

/** The same, for an id that may legitimately be absent. */
private fun nullableLongArg(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

/**
 * Switching tabs, rather than stacking them.
 *
 * `saveState`/`restoreState` are what make a tab remember where it was — tapping Itinerary
 * returns you to the day you were reading, not to the top.
 */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
