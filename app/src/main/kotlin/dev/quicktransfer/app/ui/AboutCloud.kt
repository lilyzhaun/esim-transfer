// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 lilyzhaun
package dev.quicktransfer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.quicktransfer.app.ui.about.MiuixCloudPainter
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import kotlin.math.floor

internal fun cloudShouldAnimate(active: Boolean, resumed: Boolean, visible: Boolean, supported: Boolean, scale: Float) =
    active && resumed && visible && supported && scale > 0f

@Composable
internal fun AboutCloud(active: Boolean, visible: Boolean, backdrop: LayerBackdrop, opacity: () -> Float) {
    val colors = uiColors()
    val dark = LocalDarkTheme.current
    val supported = LocalShaderSupported.current
    val renderer = remember(supported, dark) { if (supported) MiuixCloudPainter(dark) else null }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val scale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val playing = cloudShouldAnimate(active, resumed, visible, supported, scale)
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, scale) {
        if (!playing) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frame ->
                if (frame - previous >= 16_666_667L) {
                    seconds.floatValue += (frame - previous).coerceAtMost(100_000_000L) / 1_000_000_000f / scale
                    previous = frame
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)
        .then(if (playing) Modifier else Modifier)) {
        drawRect(colors.canvas)
        if (size.width > 0f && size.height > 0f && renderer != null) {
            val time = if (scale > 0f) seconds.floatValue else 0f
            val cycle = time / if (dark) 4f else 2.5f
            val transition = ((cycle - floor(cycle) - 0.7f) / 0.3f).coerceIn(0f, 1f)
            val stage = floor(cycle) + transition * transition * (3f - 2f * transition)
            renderer.update(size.width, size.height, time, stage)
            drawRect(renderer.brush, alpha = opacity().coerceIn(0f, 1f))
        }
    }
}
