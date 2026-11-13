// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 lilyzhaun
package dev.quicktransfer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.*
import kotlin.math.abs

internal enum class RootPage(val title: String) { HOME("转移"), DEVICES("设备"), HISTORY("记录"), SETTINGS("设置") }

@Composable
internal fun RootShell(covered: Boolean, rootRequest: RootPage? = null, consumeRootRequest: () -> Unit = {},
    content: @Composable (RootPage) -> Unit) {
    val pager = rememberPagerState { RootPage.entries.size }
    val scope = rememberCoroutineScope()
    val appearance = LocalAppearance.current
    val colors = uiColors()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val inputMode = LocalInputModeManager.current
    val motionScale = scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    val interactive by rememberUpdatedState(!covered)
    var barPixels by remember { mutableIntStateOf(0) }
    var refreshEpoch by remember { mutableStateOf(List(RootPage.entries.size) { 0 }) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val surface = rememberLayerBackdrop { drawRect(colors.canvas); drawContent() }
    fun select(index: Int, settling: Boolean = false) {
        val target = index.coerceIn(0, 3)
        navigationJob?.cancel()
        if (!settling && pager.currentPage == target && !pager.isScrollInProgress && abs(pager.currentPageOffsetFraction) < 0.001f) {
            refreshEpoch = refreshEpoch.mapIndexed { i, n -> if (i == target) n + 1 else n }
            return
        }
        if (appearance.haptics && inputMode.inputMode == InputMode.Touch) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        navigationJob = scope.launch {
            if (motionScale <= 0f || inputMode.inputMode == InputMode.Keyboard) pager.scrollToPage(target)
            else pager.animateScrollToPage(target, animationSpec = spring(dampingRatio = 1f, stiffness = 650f))
        }
    }
    LaunchedEffect(rootRequest) {
        rootRequest?.let { navigationJob?.cancel(); pager.scrollToPage(it.ordinal); consumeRootRequest() }
    }
    LaunchedEffect(covered) {
        if (covered) { navigationJob?.cancel(); pager.scrollToPage(pager.currentPage) }
    }
    BackHandler(!covered && pager.currentPage != 0) { select(0) }
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        HorizontalPager(pager, userScrollEnabled = !covered, beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize().layerBackdrop(surface)) { index ->
            val selected = !covered && pager.currentPage == index
            Box(Modifier.fillMaxSize().focusProperties { canFocus = selected; onEnter = { if (!selected) cancelFocusChange() } }
                .focusGroup().then(if (selected) Modifier else Modifier.clearAndSetSemantics {})) {
                CompositionLocalProvider(LocalRouteActive provides selected,
                    LocalBottomInset provides maxOf(with(density) { barPixels.toDp() }, bottom + 76.dp),
                    LocalReselectEpoch provides refreshEpoch[index]) { content(RootPage.entries[index]) }
            }
        }
        if (!covered) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { barPixels = it.height }
                .padding(horizontal = if (appearance.floating) 12.dp else 0.dp)
                .padding(bottom = bottom + if (appearance.floating) 12.dp else 0.dp), contentAlignment = Alignment.Center) {
                if (appearance.floating) FloatingNavBar(
                    position = { pager.currentPage + pager.currentPageOffsetFraction },
                    selected = pager.targetPage, onSelect = { select(it) }, backdrop = surface,
                    pageInteractions = pager.interactionSource, onGestureStart = { navigationJob?.cancel() },
                    onDragCommit = { if (interactive) select(it, settling = true) })
                else NavigationBar(color = colors.surface, mode = NavigationBarDisplayMode.IconAndText) {
                    RootPage.entries.forEachIndexed { index, page ->
                        NavigationBarItem(selected = pager.currentPage == index, enabled = true,
                            icon = rootNavigationIcons[index], label = page.title, onClick = { select(index) })
                    }
                }
            }
        }
    }
}
