// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 lilyzhaun
package dev.quicktransfer.app.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val WidthSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f)
private val HeightSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f)
private val PressureSpring = spring<Float>(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)
private val MembraneSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)

/** Material deformation only. Translation remains owned by the finger or Pager. */
@Stable
internal class NavigationBubbleShape(val horizontal: State<Float>, val vertical: State<Float>,
    val pressure: State<Float>, val tension: State<Float>) {
    fun scaleX(liquid: Boolean) = horizontal.value / (1f - if (liquid) tension.value.coerceIn(-0.2f, 0.2f) else 0f)
    fun scaleY(liquid: Boolean) = vertical.value * (1f - if (liquid) (tension.value / 3f).coerceIn(-0.2f, 0.2f) else 0f)
}

/** Measure the visible, clamped displacement. Stop sampling after a stationary velocity window. */
@Composable
internal fun rememberNavigationVelocity(enabled: Boolean, position: () -> Float): State<Float> {
    val velocity = remember { mutableFloatStateOf(0f) }
    val latestPosition by rememberUpdatedState(position)
    LaunchedEffect(enabled) {
        velocity.floatValue = 0f
        if (!enabled) return@LaunchedEffect
        var sampling: Job? = null
        snapshotFlow { latestPosition() }.collect { origin ->
            if (sampling?.isActive == true) return@collect
            sampling = launch {
                val tracker = VelocityTracker()
                var previous = origin
                var changedAt = withFrameNanos { it }
                tracker.addPosition(changedAt / 1_000_000, Offset(origin, 0f))
                do {
                    val frame = withFrameNanos { it }
                    val current = latestPosition()
                    if (current != previous) changedAt = frame
                    tracker.addPosition(frame / 1_000_000, Offset(current, 0f))
                    velocity.floatValue = tracker.calculateVelocity().x
                    previous = current
                } while (isActive && frame - changedAt < 100_000_000L)
                velocity.floatValue = 0f
            }
        }
    }
    return velocity
}

@Composable
internal fun rememberNavigationBubbleShape(pressed: Boolean, enabled: Boolean,
    velocity: () -> Float): NavigationBubbleShape {
    val expanded = pressed && enabled
    val width = animateFloatAsState(if (expanded) 78f / 56f else 1f,
        if (enabled) WidthSpring else snap(), label = "bubble-width")
    val height = animateFloatAsState(if (expanded) 78f / 56f else 1f,
        if (enabled) HeightSpring else snap(), label = "bubble-height")
    val pressure = animateFloatAsState(if (expanded) 1f else 0f,
        if (enabled) PressureSpring else snap(), label = "bubble-pressure")
    val membrane = remember(enabled) { AnimationState(0f) }
    val latestVelocity by rememberUpdatedState(velocity)
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var continuing = false
        suspend fun approach(target: Float) {
            val interrupted = continuing
            continuing = true
            // Retain the last frame time and velocity when a newer sample interrupts this spring.
            membrane.animateTo(target, MembraneSpring, sequentialAnimation = interrupted)
            continuing = false
        }
        // Conflate material samples; position updates never wait for this spring.
        snapshotFlow { latestVelocity() }.collectLatest { speed ->
            val tension = speed / 3f * 0.075f
            if (tension == 0f && membrane.value == 0f && membrane.velocity == 0f) return@collectLatest
            approach(tension)
        }
    }
    return remember(width, height, pressure, membrane) { NavigationBubbleShape(width, height, pressure, membrane) }
}
