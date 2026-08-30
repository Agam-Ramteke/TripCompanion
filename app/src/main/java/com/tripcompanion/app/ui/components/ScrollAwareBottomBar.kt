package com.tripcompanion.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Manages visibility of the floating bottom navigation bar across screens.
 */
class BottomBarController {
    var isVisible by mutableStateOf(true)
}

val LocalBottomBarController = compositionLocalOf { BottomBarController() }

/**
 * Creates a [NestedScrollConnection] that tracks accumulated vertical scroll distance
 * to show or hide the floating bottom navigation bar and trigger optional header collapse/expand callbacks.
 *
 * @param controller The [BottomBarController] instance to update.
 * @param hideThresholdDp Distance (dp) required when scrolling down before hiding (default 32dp).
 * @param showThresholdDp Distance (dp) required when scrolling up before showing (default 20dp).
 * @param onScrollDown Callback invoked when downward scroll crosses threshold.
 * @param onScrollUp Callback invoked when upward scroll crosses threshold.
 */
@Composable
fun rememberScrollAwareNestedScrollConnection(
    controller: BottomBarController = LocalBottomBarController.current,
    hideThresholdDp: Dp = 32.dp,
    showThresholdDp: Dp = 20.dp,
    onScrollDown: (() -> Unit)? = null,
    onScrollUp: (() -> Unit)? = null
): NestedScrollConnection {
    val density = LocalDensity.current
    val hideThresholdPx = remember(density, hideThresholdDp) { with(density) { hideThresholdDp.toPx() } }
    val showThresholdPx = remember(density, showThresholdDp) { with(density) { showThresholdDp.toPx() } }

    var accumulatedDelta by remember { mutableFloatStateOf(0f) }

    return remember(controller, hideThresholdPx, showThresholdPx, onScrollDown, onScrollUp) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y // Negative when scrolling DOWN (content moving UP)

                if (delta < 0f) {
                    // User is scrolling down
                    if (accumulatedDelta > 0f) accumulatedDelta = 0f
                    accumulatedDelta += delta
                    if (accumulatedDelta <= -hideThresholdPx) {
                        controller.isVisible = false
                        onScrollDown?.invoke()
                    }
                } else if (delta > 0f) {
                    // User is scrolling up
                    if (accumulatedDelta < 0f) accumulatedDelta = 0f
                    accumulatedDelta += delta
                    if (accumulatedDelta >= showThresholdPx) {
                        controller.isVisible = true
                        onScrollUp?.invoke()
                    }
                }

                return Offset.Zero
            }
        }
    }
}
