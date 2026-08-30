package com.tripcompanion.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Standard motion duration & easing tokens for a cohesive, understated Android motion system.
 * Matches Material motion guidelines: fast, calm, responsive, never flashy.
 */
object MotionTokens {
    const val PAGE_TRANSITION_DURATION = 240
    const val CONTENT_ENTER_DURATION = 280
    const val CONTENT_STAGGER_DELAY = 25
    const val BUTTON_PRESS_DURATION = 120
    const val NAV_PILL_DURATION = 220
    const val EMPTY_STATE_ENTER_DURATION = 300

    val StandardEasing: Easing = FastOutSlowInEasing
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
}

/**
 * CompositionLocal indicating whether the user prefers reduced motion
 * (e.g. "Remove animations" accessibility setting enabled).
 */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * Checks if Android Accessibility / Animator settings have disabled animations.
 */
fun Context.isReducedMotionEnabled(): Boolean {
    return try {
        val durationScale = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        val transitionScale = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1.0f
        )
        durationScale == 0f || transitionScale == 0f
    } catch (_: Exception) {
        false
    }
}

/**
 * Provides [LocalReducedMotion] based on the current context accessibility settings.
 */
@Composable
fun ProvideMotionEnvironment(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isReducedMotion = remember(context) { context.isReducedMotionEnabled() }
    CompositionLocalProvider(LocalReducedMotion provides isReducedMotion) {
        content()
    }
}

/**
 * Subtle tactile press feedback for primary buttons (1.0 -> 0.97 scale on press, 100-150ms).
 */
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    scaleOnPress: Float = 0.97f
): Modifier = composed {
    val isReducedMotion = LocalReducedMotion.current
    if (isReducedMotion) return@composed this

    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleOnPress else 1.0f,
        animationSpec = tween(
            durationMillis = MotionTokens.BUTTON_PRESS_DURATION,
            easing = MotionTokens.StandardEasing
        ),
        label = "buttonPressScale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Tactile press feedback for circular Floating Action Buttons (1.0 -> 0.92 scale on press).
 */
fun Modifier.fabPressFeedback(
    interactionSource: MutableInteractionSource,
    scaleOnPress: Float = 0.92f
): Modifier = composed {
    val isReducedMotion = LocalReducedMotion.current
    if (isReducedMotion) return@composed this

    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleOnPress else 1.0f,
        animationSpec = tween(
            durationMillis = MotionTokens.BUTTON_PRESS_DURATION,
            easing = MotionTokens.StandardEasing
        ),
        label = "fabPressScale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Subtle staggered entrance for independent elements in a screen.
 * Fades 0 -> 1 and slides Y 8dp -> 0 with a 25ms stagger per index.
 */
fun Modifier.staggeredEntrance(
    index: Int = 0,
    offsetDp: Float = 8f
): Modifier = composed {
    val isReducedMotion = LocalReducedMotion.current
    if (isReducedMotion) return@composed this

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay((index * MotionTokens.CONTENT_STAGGER_DELAY).toLong())
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = MotionTokens.CONTENT_ENTER_DURATION,
            easing = MotionTokens.StandardEasing
        ),
        label = "staggerAlpha"
    )

    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else offsetDp,
        animationSpec = tween(
            durationMillis = MotionTokens.CONTENT_ENTER_DURATION,
            easing = MotionTokens.StandardEasing
        ),
        label = "staggerOffset"
    )

    this.graphicsLayer {
        this.alpha = alpha
        this.translationY = offsetY * density
    }
}
