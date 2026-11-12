// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 lilyzhaun
package dev.quicktransfer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

internal enum class RootPage(val title: String) { HOME("Transfer"), DEVICES("Devices"), HISTORY("History"), SETTINGS("Settings") }

@Composable
internal fun RootShell(covered: Boolean, rootRequest: RootPage? = null, consumeRootRequest: () -> Unit = {},
    content: @Composable (RootPage) -> Unit) {
    val pager = rememberPagerState { RootPage.entries.size }
    val scope = rememberCoroutineScope()
    val appearance = LocalAppearance.current
    val colors = uiColors()
    val density = LocalDensity.current
    var barPixels by remember { mutableIntStateOf(0) }
    var refreshEpoch by remember { mutableStateOf(List(RootPage.entries.size) { 0 }) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val material = appearance.blur && LocalShaderSupported.current
    val surface = rememberLayerBackdrop()
    val icons = listOf(MiuixIcons.Home, MiuixIcons.Phone, MiuixIcons.Timer, MiuixIcons.Settings)
    LaunchedEffect(rootRequest) {
        rootRequest?.let { pager.scrollToPage(it.ordinal); consumeRootRequest() }
    }
    BackHandler(!covered && pager.currentPage != 0) { scope.launch { pager.animateScrollToPage(0) } }
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
            val outline = RoundedCornerShape(if (appearance.floating) 28.dp else 0.dp)
            val effects = if (material) Modifier.drawBackdrop(surface, shape = { outline }, effects = {
                blur(20.dp.toPx())
                if (appearance.liquid && appearance.floating) runtimeShaderEffect("TransferGlass", GLASS, "content") {
                    setFloatUniform("extent", size.width, size.height)
                }
            }) else Modifier.background(colors.surface, outline)
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { barPixels = it.height }
                .padding(horizontal = if (appearance.floating) 16.dp else 0.dp)
                .padding(bottom = bottom + if (appearance.floating) 12.dp else 0.dp)) {
                NavigationBar(modifier = Modifier.clip(outline).then(effects),
                    color = if (material) colors.surface.copy(alpha = 0.72f) else colors.surface,
                    mode = NavigationBarDisplayMode.IconAndText) {
                    RootPage.entries.forEachIndexed { index, page ->
                        NavigationBarItem(selected = pager.currentPage == index, enabled = !pager.isScrollInProgress,
                            icon = icons[index], label = page.title, onClick = {
                                if (pager.currentPage == index) refreshEpoch = refreshEpoch.mapIndexed { i, n -> if (i == index) n + 1 else n }
                                else scope.launch { pager.animateScrollToPage(index) }
                            })
                    }
                }
            }
        }
    }
}

// A small, original edge displacement. No third-party lens or gesture implementation is used.
private const val GLASS = """
uniform shader content;
uniform float2 extent;
half4 main(float2 point) {
    float2 uv = point / max(extent, float2(1.0));
    float edge = abs(uv.y - 0.5) * 2.0;
    float bend = edge * edge * edge * 1.5;
    float2 samplePoint = point + float2(0.0, (0.5 - uv.y) * bend);
    half4 base = content.eval(samplePoint);
    return half4(min(base.rgb + half3(edge * 0.018), half3(1.0)), base.a);
}
"""
