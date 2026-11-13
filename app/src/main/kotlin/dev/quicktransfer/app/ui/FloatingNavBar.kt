// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 lilyzhaun
package dev.quicktransfer.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.blur.highlight.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal val rootNavigationIcons = listOf(MiuixIcons.Home, MiuixIcons.SearchDevice, MiuixIcons.Recent, MiuixIcons.Settings)

@Composable
internal fun FloatingNavBar(position: () -> Float, selected: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, backdrop: LayerBackdrop? = null, pageInteractions: InteractionSource? = null,
    onGestureStart: () -> Unit = {}, onDragCommit: (Int) -> Unit = onSelect) {
    val colors = uiColors()
    val appearance = LocalAppearance.current
    val dark = LocalDarkTheme.current
    val liquid = appearance.liquid && LocalShaderSupported.current && backdrop != null
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val motionScale = scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val active = LocalRouteActive.current
    val motionEnabled = active && motionScale > 0f && LocalInputModeManager.current.inputMode != InputMode.Keyboard
    val height = maxOf(56.dp, 42.dp + with(density) { 14.sp.toDp() })
    val width = maxOf(76.dp, with(density) { 44.sp.toDp() }) * 4 + 8.dp
    val indicator = remember { Animatable(position().coerceIn(0f, 3f)) }
    var followingPage by remember { mutableStateOf(true) }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var settling by remember { mutableStateOf<Job?>(null) }
    var held by remember { mutableStateOf(false) }
    var landing by remember { mutableStateOf(false) }
    val travel = remember { mutableFloatStateOf(0f) }
    val panelWidth = remember { mutableFloatStateOf(1f) }
    val panelReturn = remember { Animatable(0f) }
    var panelJob by remember { mutableStateOf<Job?>(null) }
    val latestPosition by rememberUpdatedState(position)
    val latestSelected by rememberUpdatedState(selected)
    val start by rememberUpdatedState(onGestureStart)
    val commit by rememberUpdatedState(onDragCommit)
    val select by rememberUpdatedState(onSelect)
    fun visualPosition(): Float = dragPosition ?: if (followingPage) latestPosition().coerceIn(0f, 3f) else indicator.value
    fun panelOffset(): Float {
        if (!motionEnabled) return 0f
        val raw = if (held) travel.floatValue else panelReturn.value
        val fraction = (raw / panelWidth.floatValue).coerceIn(-1f, 1f)
        return sign(fraction) * EaseOut.transform(abs(fraction)) * with(density) { 4.dp.toPx() }
    }
    fun settleTo(index: Int) {
        val origin = visualPosition()
        settling?.cancel()
        followingPage = false
        dragPosition = origin
        landing = motionEnabled && abs(origin - index) > 0.075f
        settling = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            indicator.snapTo(origin)
            dragPosition = null
            if (!motionEnabled) indicator.snapTo(index.toFloat())
            else indicator.animateTo(index.toFloat(), spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)) {
                if (abs(value - index) <= 0.075f) landing = false
            }
            landing = false
        }
    }
    fun releasePress() {
        val origin = travel.floatValue
        panelJob?.cancel()
        panelJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            panelReturn.snapTo(origin)
            if (!motionEnabled) panelReturn.snapTo(0f)
            else panelReturn.animateTo(0f, spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.5f))
        }
        held = false
        travel.floatValue = 0f
    }
    LaunchedEffect(pageInteractions) {
        pageInteractions?.interactions?.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                settling?.cancel()
                dragPosition = null
                landing = false
                followingPage = true
            }
        }
    }
    LaunchedEffect(selected, followingPage) {
        if (!followingPage && dragPosition == null && indicator.targetValue != selected.toFloat()) settleTo(selected)
    }
    val velocity = rememberNavigationVelocity(motionEnabled && liquid && !followingPage && (held || indicator.isRunning), ::visualPosition)
    val bubble = rememberNavigationBubbleShape(held || landing, motionEnabled) { velocity.value }
    val navigation = rememberLayerBackdrop()
    val lensSource = remember(backdrop, navigation) { backdrop?.let { NavigationBackdrop(it, navigation) } }
    val interactions = remember { List(4) { MutableInteractionSource() } }
    val light = remember { Highlight(width = 1.dp, alpha = 0.75f, style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f), innerBlurRadius = 2.dp,
        primaryLight = LightSource(LightPosition(0.5f, -0.3f, -0.05f), Color.White, 1f),
        secondaryLight = LightSource(LightPosition(0.5f, 0.8f, -0.5f), Color.White, 0.4f), dualPeak = true)) }
    BoxWithConstraints(modifier.widthIn(max = width).fillMaxWidth().height(height + 8.dp)
        .onSizeChanged { panelWidth.floatValue = it.width.toFloat() }) {
        val cell = (maxWidth - 8.dp) / 4
        val cellPx = with(density) { cell.toPx() }
        val direction = if (rtl) -1f else 1f
        val pressGesture = Modifier.pointerInput(cellPx, rtl, active) {
            if (!active) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val origin = visualPosition()
                val originIndex = latestSelected
                val originPanel = panelOffset()
                val originTravel = panelReturn.value
                val visualIndex = if (rtl) 3f - origin else origin
                val bubbleLeft = 4.dp.toPx() + visualIndex * cellPx + originPanel
                val overhang = (bubble.scaleX(liquid) - 1f) * cellPx / 2f
                val grabbed = down.position.x in (bubbleLeft - overhang)..(bubbleLeft + cellPx + overhang)
                var dragging = false
                var cancelled = false
                var finished = false
                var releasedNormally = false
                var distance = 0f
                held = grabbed
                travel.floatValue = originTravel
                if (grabbed) {
                    down.consume()
                    settling?.cancel()
                    panelJob?.cancel()
                    landing = false
                    dragPosition = origin
                    followingPage = false
                    start()
                }
                fun finish(cancelled: Boolean) {
                    val target = if (cancelled) originIndex else visualPosition().roundToInt().coerceIn(0, 3)
                    settleTo(target)
                    if (!cancelled && !dragging && target == originIndex) select(target) else commit(target)
                }
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (event.changes.count { it.pressed } > 1 || pointer == null) {
                            event.changes.forEach { it.consume() }
                            cancelled = true; break
                        }
                        // Compose's synthetic ACTION_CANCEL is an already-consumed release.
                        if (pointer.isConsumed) { cancelled = true; break }
                        if (!pointer.pressed) { releasedNormally = true; break }
                        val delta = pointer.position.x - pointer.previousPosition.x
                        distance += delta
                        if (grabbed && !dragging && abs(distance) > viewConfiguration.touchSlop) {
                            dragging = true
                        }
                        if (grabbed) {
                            // Direct pointer ownership: no coroutine, spring, or pager update per MOVE.
                            travel.floatValue = originTravel + distance
                            dragPosition = (origin + (distance - (panelOffset() - originPanel)) * direction / cellPx).coerceIn(0f, 3f)
                            pointer.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    if (grabbed) {
                        finished = true
                        finish(cancelled)
                    }
                } finally {
                    if (grabbed && !finished && !releasedNormally) finish(true)
                    releasePress()
                }
            }
        }
        val body = Modifier.layerBackdrop(navigation)
            .graphicsLayer { translationX = panelOffset() }
            .dropShadow(CircleShape, Shadow(radius = 10.dp, color = Color.Black.copy(alpha = if (dark) 0.20f else 0.10f)))
            .then(if (liquid) Modifier.drawBackdrop(backdrop!!, shape = { CircleShape }, effects = {
                colorControls(saturation = 1.25f)
                blur(4.dp.toPx())
                navigationLens(24.dp.toPx(), 24.dp.toPx())
            }, highlight = { light }, onDrawSurface = { drawRect(colors.surface.copy(alpha = 0.40f)) })
            else Modifier.background(colors.surface, CircleShape))
        Row(Modifier.fillMaxSize().then(pressGesture).then(body).padding(4.dp).selectableGroup()) {
            RootPage.entries.forEachIndexed { index, page ->
                val focused by interactions[index].collectIsFocusedAsState()
                val tint by animateColorAsState(if (selected == index) colors.accent else colors.text, label = "tab-tint")
                Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .then(if (focused) Modifier.border(2.dp, colors.accent, CircleShape) else Modifier)
                    .selectable(selected == index, enabled = active, role = Role.Tab,
                        interactionSource = interactions[index], indication = null, onClick = {
                            settleTo(index); onSelect(index)
                        }),
                    verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(rootNavigationIcons[index], null, Modifier.size(24.dp), tint = tint)
                    Text(page.title, color = tint, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
                }
            }
        }
        val deformation: GraphicsLayerScope.() -> Unit = {
            scaleX = bubble.scaleX(liquid)
            scaleY = bubble.scaleY(liquid)
        }
        Box(Modifier.padding(4.dp).align(Alignment.TopStart)
            .graphicsLayer { translationX = direction * visualPosition() * cellPx + panelOffset() }
            .width(cell).height(height).clearAndSetSemantics {}
            .then(if (liquid) Modifier.drawBackdrop(lensSource!!, shape = { CircleShape }, effects = {
                val pressure = bubble.pressure.value
                navigationLens(14.dp.toPx() * pressure, 10.dp.toPx(), dispersion = 0.035f * pressure)
            }, highlight = { light.copy(alpha = bubble.pressure.value * 0.75f) }, layerBlock = deformation,
                onDrawSurface = {
                    val pressure = bubble.pressure.value
                    drawRect(colors.text.copy(alpha = 0.10f * (1f - pressure)))
                    drawRect(colors.accent.copy(alpha = 0.10f * pressure))
                })
            else Modifier.graphicsLayer(deformation).background(colors.accent.copy(alpha = 0.15f), CircleShape)))
    }
}
