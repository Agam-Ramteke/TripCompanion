package com.tripcompanion.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tripcompanion.app.ui.components.BottomBarController
import com.tripcompanion.app.ui.components.LocalBottomBarController
import com.tripcompanion.app.ui.components.cartographicBackground
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
import com.tripcompanion.app.ui.theme.LocalReducedMotion
import com.tripcompanion.app.ui.theme.MotionTokens
import kotlin.math.roundToInt

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
) {
    /** Pattern matching base path (e.g. "timeline" for "timeline?tripId={tripId}"). */
    val baseRoute: String get() = route.substringBefore('?').substringBefore('/')
}

/**
 * The five primary destinations in the bottom navigation bar.
 */
private val BottomTabs = listOf(
    BottomTab(Routes.Home.route, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    BottomTab(Routes.TripList.route, "Trips", Icons.Outlined.Luggage, Icons.Filled.Luggage),
    BottomTab(
        Routes.Timeline.createRoute(),
        "Plan",
        Icons.Outlined.CalendarMonth,
        Icons.Filled.CalendarMonth
    ),
    BottomTab(Routes.Trains.route, "Trains", Icons.Outlined.Train, Icons.Filled.Train),
    BottomTab(Routes.More.route, "More", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz)
)

private fun tabIndexOf(route: String?): Int {
    if (route == null) return -1
    val base = route.substringBefore('?').substringBefore('/')
    return BottomTabs.indexOfFirst { it.baseRoute == base }
}

/**
 * Premium floating bottom navigation bar with an expandable active destination pill.
 * Features a substantial rounded container (74dp high, 37dp radius), 5 evenly balanced destinations,
 * an expanding active indicator capsule (icon + label, 48dp high, tonal accent background),
 * and smooth transitions.
 */
@Composable
private fun AppBottomNavigation(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = AppThemeExtended.colors
    val reducedMotion = LocalReducedMotion.current
    val selectedIndex = tabIndexOf(currentRoute).coerceAtLeast(0)
    val density = LocalDensity.current

    val surfaceColor = if (colors.isDark) Color(0xFF14181E) else Color(0xFFFFFFFF)
    val borderColor = if (colors.isDark) Color(0x2EFFFFFF) else Color(0x18000000)
    val activeCapsuleColor = colors.accentSoft
    val shadowSpotColor = if (colors.isDark) Color(0x55000000) else Color(0x18000000)
    val shadowAmbientColor = if (colors.isDark) Color(0x2A000000) else Color(0x0A000000)

    val hideOffsetPx = remember(density) { with(density) { 160.dp.toPx() } }
    val translationY by animateFloatAsState(
        targetValue = if (visible || reducedMotion) 0f else hideOffsetPx,
        animationSpec = tween(
            durationMillis = 220,
            easing = FastOutSlowInEasing
        ),
        label = "bottomNavTranslationY"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "bottomNavAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.translationY = translationY
                this.alpha = alpha
            }
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 14.dp, top = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(37.dp),
                    spotColor = shadowSpotColor,
                    ambientColor = shadowAmbientColor,
                    clip = false
                ),
            shape = RoundedCornerShape(37.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                BottomTabs.forEachIndexed { index, tab ->
                    val selected = selectedIndex == index

                    val iconScale by animateFloatAsState(
                        targetValue = if (selected && !reducedMotion) 1.05f else 0.95f,
                        animationSpec = tween(
                            durationMillis = MotionTokens.NAV_PILL_DURATION,
                            easing = MotionTokens.StandardEasing
                        ),
                        label = "navIconScale"
                    )

                    val activeColor by animateColorAsState(
                        targetValue = if (selected) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(
                            durationMillis = MotionTokens.NAV_PILL_DURATION,
                            easing = MotionTokens.StandardEasing
                        ),
                        label = "navColor"
                    )

                    val capsuleBgColor by animateColorAsState(
                        targetValue = if (selected) activeCapsuleColor else Color.Transparent,
                        animationSpec = tween(
                            durationMillis = MotionTokens.NAV_PILL_DURATION,
                            easing = MotionTokens.StandardEasing
                        ),
                        label = "capsuleBgColor"
                    )

                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(capsuleBgColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTabSelected(tab.route) }
                            )
                            .animateContentSize(
                                animationSpec = if (reducedMotion) tween(0) else tween(
                                    durationMillis = MotionTokens.NAV_PILL_DURATION,
                                    easing = MotionTokens.StandardEasing
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = if (selected) 16.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.label,
                                tint = activeColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )

                            if (selected) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    ),
                                    color = colors.accent,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The whole app's navigation.
 *
 * The bar appears on exactly the five tab roots and nowhere else: everything deeper is pushed
 * and carries its own back button, so a detail screen is never half-tab and half-page.
 *
 * Back-navigation: System Back returns to Home from anywhere in the app, EXCEPT Settings, the
 * activity editor (and its sub-pickers), and Home itself (which exits the app).
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val isReducedMotion = LocalReducedMotion.current
    val bottomBarController = remember { BottomBarController() }

    LaunchedEffect(currentRoute) {
        bottomBarController.isVisible = true
    }

    // System Back returns to Home from anywhere, EXCEPT Settings, the activity editor, and sub-pickers
    val isExceptedRoute = currentRoute in listOf(
        Routes.Settings.route,
        Routes.EventEditor.route,
        Routes.LocationPicker.route,
        Routes.PhotoEditor.route
    )
    val shouldInterceptBackToHome = currentRoute != null &&
        currentRoute != Routes.Home.route &&
        !isExceptedRoute

    BackHandler(enabled = shouldInterceptBackToHome) {
        navController.navigateToTopLevel(Routes.Home.route)
    }

    val colors = AppThemeExtended.colors

    CompositionLocalProvider(LocalBottomBarController provides bottomBarController) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .cartographicBackground(isDark = colors.isDark, accentColor = colors.accent)
        ) {
        NavHost(
            navController = navController,
            startDestination = Routes.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val fromTab = tabIndexOf(initialState.destination.route)
                val toTab = tabIndexOf(targetState.destination.route)
                if (fromTab >= 0 && toTab >= 0) {
                    val forward = toTab > fromTab
                    if (isReducedMotion) {
                        fadeIn(tween(150))
                    } else {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> if (forward) (fullWidth * 0.05f).roundToInt() else (-fullWidth * 0.05f).roundToInt() },
                            animationSpec = tween(MotionTokens.PAGE_TRANSITION_DURATION, easing = MotionTokens.StandardEasing)
                        ) + fadeIn(tween(MotionTokens.PAGE_TRANSITION_DURATION))
                    }
                } else if (targetState.destination.route == Routes.TripMap.route || initialState.destination.route == Routes.TripMap.route) {
                    EnterTransition.None
                } else {
                    if (isReducedMotion) fadeIn(tween(150)) else {
                        slideInVertically(
                            initialOffsetY = { fullHeight -> (fullHeight * 0.05f).roundToInt() },
                            animationSpec = tween(MotionTokens.PAGE_TRANSITION_DURATION, easing = MotionTokens.StandardEasing)
                        ) + fadeIn(tween(MotionTokens.PAGE_TRANSITION_DURATION))
                    }
                }
            },
            exitTransition = {
                val fromTab = tabIndexOf(initialState.destination.route)
                val toTab = tabIndexOf(targetState.destination.route)
                if (fromTab >= 0 && toTab >= 0) {
                    val forward = toTab > fromTab
                    if (isReducedMotion) {
                        fadeOut(tween(150))
                    } else {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> if (forward) (-fullWidth * 0.05f).roundToInt() else (fullWidth * 0.05f).roundToInt() },
                            animationSpec = tween(MotionTokens.PAGE_TRANSITION_DURATION, easing = MotionTokens.StandardEasing)
                        ) + fadeOut(tween(MotionTokens.PAGE_TRANSITION_DURATION))
                    }
                } else if (targetState.destination.route == Routes.TripMap.route || initialState.destination.route == Routes.TripMap.route) {
                    ExitTransition.None
                } else {
                    if (isReducedMotion) fadeOut(tween(150)) else {
                        fadeOut(tween(MotionTokens.PAGE_TRANSITION_DURATION - 40))
                    }
                }
            },
            popEnterTransition = {
                if (targetState.destination.route == Routes.TripMap.route || initialState.destination.route == Routes.TripMap.route) {
                    EnterTransition.None
                } else {
                    fadeIn(tween(if (isReducedMotion) 150 else MotionTokens.PAGE_TRANSITION_DURATION))
                }
            },
            popExitTransition = {
                if (targetState.destination.route == Routes.TripMap.route || initialState.destination.route == Routes.TripMap.route) {
                    ExitTransition.None
                } else {
                    if (isReducedMotion) fadeOut(tween(150)) else {
                        slideOutVertically(
                            targetOffsetY = { fullHeight -> (fullHeight * 0.05f).roundToInt() },
                            animationSpec = tween(MotionTokens.PAGE_TRANSITION_DURATION, easing = MotionTokens.StandardEasing)
                        ) + fadeOut(tween(MotionTokens.PAGE_TRANSITION_DURATION))
                    }
                }
            }
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
                    onNavigateToAddEvent = { tripId, date ->
                        navController.navigate(Routes.EventEditor.createRoute(tripId = tripId, date = date))
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

            val showBottomBar = tabIndexOf(currentRoute) >= 0
            if (showBottomBar) {
                AppBottomNavigation(
                    currentRoute = currentRoute,
                    onTabSelected = { route ->
                        bottomBarController.isVisible = true
                        navController.navigateToTopLevel(route)
                    },
                    visible = bottomBarController.isVisible,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
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
    val startDest = graph.findStartDestination()
    val isNavigatingHome = route == startDest.route || route == Routes.Home.route
    if (isNavigatingHome) {
        popBackStack(startDest.id, inclusive = false)
    } else {
        navigate(route) {
            popUpTo(startDest.id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}
