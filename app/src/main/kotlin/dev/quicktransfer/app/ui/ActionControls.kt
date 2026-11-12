// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 lilyzhaun
package dev.quicktransfer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun Modifier.statusLiveRegion(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }

@Composable
internal fun PrimaryAction(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit) = Button(onClick = onClick, enabled = enabled,
    modifier = modifier.heightIn(min = 48.dp), colors = ButtonDefaults.buttonColorsPrimary(), content = content)

@Composable
internal fun InlineAction(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(label, onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(color = Color.Transparent, disabledColor = Color.Transparent,
            textColor = MiuixTheme.colorScheme.primary), textStyle = MiuixTheme.textStyles.button.copy(textAlign = TextAlign.Center))
}

@Composable
internal fun RefreshAction(label: String, enabled: Boolean, onClick: () -> Unit, busy: Boolean = false) {
    IconButton(onClick, enabled = enabled && !busy, modifier = Modifier.size(48.dp).semantics(mergeDescendants = true) {
        contentDescription = label; role = Role.Button
        stateDescription = if (busy) "Refreshing" else "Ready"
        if (!enabled || busy) disabled()
    }) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp)) else Icon(MiuixIcons.Refresh, null)
    }
}

@Composable
internal fun ConfirmationActions(confirmLabel: String, onCancel: () -> Unit, onConfirm: () -> Unit, enabled: Boolean = true) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.5f) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryAction(enabled, onConfirm, Modifier.fillMaxWidth()) { Text(confirmLabel) }
            InlineAction("Cancel", true, onCancel, Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InlineAction("Cancel", true, onCancel, Modifier.weight(1f))
            PrimaryAction(enabled, onConfirm, Modifier.weight(1f)) { Text(confirmLabel) }
        }
    }
}
