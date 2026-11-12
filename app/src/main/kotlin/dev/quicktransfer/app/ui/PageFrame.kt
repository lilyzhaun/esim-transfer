// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 lilyzhaun
package dev.quicktransfer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

internal val LocalAppearance = staticCompositionLocalOf { Appearance() }
internal val LocalShaderSupported = staticCompositionLocalOf { false }
internal val LocalReselectEpoch = staticCompositionLocalOf { 0 }
internal val LocalOperationMessage = staticCompositionLocalOf { "" }
internal val LocalDismissOperationMessage = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
internal fun PageFrame(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {},
    itemSpacing: Dp = 12.dp, message: String = LocalOperationMessage.current,
    dismissMessage: () -> Unit = LocalDismissOperationMessage.current, content: LazyListScope.() -> Unit) {
    val list = rememberLazyListState()
    val header = MiuixScrollBehavior(rememberTopAppBarState())
    val epoch = LocalReselectEpoch.current
    val active = LocalRouteActive.current
    LaunchedEffect(epoch) { if (epoch > 0) { list.scrollToItem(0); header.state.heightOffset = 0f; header.state.contentOffset = 0f } }
    val bottom = maxOf(LocalBottomInset.current, WindowInsets.ime.asPaddingValues().calculateBottomPadding())
    Scaffold(containerColor = uiColors().canvas, contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        topBar = {
            if (onBack == null) TopAppBar(title, scrollBehavior = header, actions = actions)
            else SmallTopAppBar(title, navigationIcon = {
                IconButton(onBack, modifier = Modifier.size(48.dp)) { Icon(MiuixIcons.Back, "Back") }
            }, actions = actions)
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(start = padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            end = padding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr), top = padding.calculateTopPadding())
            .consumeWindowInsets(padding)) {
            LazyColumn(state = list, modifier = Modifier.weight(1f).widthIn(max = 960.dp).fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .then(if (onBack == null) Modifier.nestedScroll(header.nestedScrollConnection) else Modifier),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp,
                    bottom = 12.dp + if (active && message.isNotBlank()) 0.dp else bottom),
                verticalArrangement = Arrangement.spacedBy(itemSpacing), content = content)
            if (active && message.isNotBlank()) ActionMessage(message, dismissMessage,
                Modifier.widthIn(max = 960.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                    .padding(start = 12.dp, end = 12.dp, bottom = bottom + 12.dp))
        }
    }
}

@Composable
internal fun ActionMessage(message: String, dismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.statusLiveRegion(), insideMargin = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, Modifier.weight(1f).heightIn(max = 160.dp).verticalScroll(rememberScrollState()), fontSize = 14.sp, lineHeight = 21.sp)
            InlineAction("Dismiss", true, dismiss)
        }
    }
}

@Composable
internal fun ListFooterAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { InlineAction(label, enabled, onClick) }
}
