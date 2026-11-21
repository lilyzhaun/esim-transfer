// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 lilyzhaun
package dev.quicktransfer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
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
internal fun LoadingRing(diameter: Dp, modifier: Modifier = Modifier, color: Color = MiuixTheme.colorScheme.primary) {
    // miuix draws from its size parameter, not the size imposed by a modifier.
    CircularProgressIndicator(modifier = modifier, size = diameter, strokeWidth = 2.5.dp,
        colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color, backgroundColor = color.copy(alpha = .18f)))
}

@Composable
internal fun LoadingLine(label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LoadingRing(20.dp, Modifier.clearAndSetSemantics {})
        Text(label, Modifier.statusLiveRegion(), color = uiColors().secondary, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
internal fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, busy: Boolean = false, tint: Color = uiColors().text) {
    val clickable = enabled && !busy
    IconButton(onClick, enabled = clickable, modifier = modifier.size(48.dp).semantics(mergeDescendants = true) {
        contentDescription = label
        role = Role.Button
        if (busy) stateDescription = "正在刷新"
        if (!clickable) disabled()
    }) {
        if (busy) LoadingRing(24.dp, Modifier.clearAndSetSemantics {})
        else Icon(icon, null, Modifier.size(24.dp), tint = if (enabled) tint else MiuixTheme.colorScheme.disabledOnSecondaryVariant)
    }
}

@Composable
internal fun RefreshAction(label: String, enabled: Boolean, onClick: () -> Unit, busy: Boolean = false) =
    IconAction(MiuixIcons.Refresh, label, onClick, enabled = enabled, busy = busy)

@Composable
internal fun ChoiceRow(title: String, checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    summary: String? = null, enabled: Boolean = true, startAction: (@Composable () -> Unit)? = null) {
    BasicComponent(title = title, summary = summary, enabled = enabled, role = Role.RadioButton, onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.RadioButton
            selected = checked
            if (!enabled) disabled()
        }, startAction = startAction, endActions = {
            Box(Modifier.size(26.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                RadioButton(checked, onClick = null, enabled = enabled)
            }
        })
}

@Composable
internal fun DisclosureRow(title: String, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    summary: String? = null, enabled: Boolean = true) {
    BasicComponent(title = title, summary = summary, enabled = enabled, role = Role.Button, onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) {
            stateDescription = if (expanded) "已展开" else "已收起"
            if (!enabled) disabled()
        }, endActions = {
            Icon(MiuixIcons.ChevronForward, null, Modifier.size(20.dp).rotate(if (expanded) -90f else 90f),
                tint = uiColors().secondary)
        })
}

@Composable
internal fun AppDialog(show: Boolean, title: String, dismiss: () -> Unit, afterDismiss: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val shortWindow = LocalWindowInfo.current.containerDpSize.height < 480.dp
    OverlayDialog(show = show, modifier = Modifier.semantics { paneTitle = title },
        insideMargin = if (shortWindow) DpSize(20.dp, 16.dp) else DialogDefaults.insideMargin,
        onDismissRequest = { if (show) dismiss() }, onDismissFinished = afterDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (footer == null) {
                DialogTitle(title)
                content()
            } else {
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DialogTitle(title)
                    content()
                }
                footer()
            }
        }
    }
}

@Composable
private fun DialogTitle(title: String) {
    Text(title, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() })
}

@Composable
internal fun DialogText(text: String, color: Color = uiColors().text) {
    Text(text, fontSize = 15.sp, lineHeight = 22.sp, color = color)
}

@Composable
internal fun ConfirmationDialog(show: Boolean, title: String, confirmLabel: String, dismiss: () -> Unit,
    confirm: () -> Unit, cancelLabel: String = "取消", enabled: Boolean = true, afterDismiss: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit) {
    AppDialog(show, title, dismiss, afterDismiss, footer = {
        ConfirmationActions(cancelLabel, confirmLabel, { if (show) dismiss() }, { if (show && enabled) confirm() }, show, show && enabled)
    }, content = body)
}

@Composable
private fun ConfirmationActions(cancelLabel: String, confirmLabel: String, onCancel: () -> Unit, onConfirm: () -> Unit,
    cancelEnabled: Boolean, confirmEnabled: Boolean) {
    val style = MiuixTheme.textStyles.button.copy(textAlign = TextAlign.Center)
    val measure = rememberTextMeasurer()
    val gap = 8.dp
    val density = LocalDensity.current
    val insideMargin = if (LocalWindowInfo.current.containerDpSize.height < 480.dp) PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        else ButtonDefaults.InsideMargin
    val labelWidth = maxOf(measure.measure(cancelLabel, style, softWrap = false).size.width,
        measure.measure(confirmLabel, style, softWrap = false).size.width)
    val cancel: @Composable (Modifier) -> Unit = {
        TextButton(cancelLabel, onCancel, modifier = it.heightIn(min = 48.dp), enabled = cancelEnabled,
            insideMargin = insideMargin, textStyle = style)
    }
    val confirm: @Composable (Modifier) -> Unit = {
        TextButton(confirmLabel, onConfirm, modifier = it.heightIn(min = 48.dp), enabled = confirmEnabled,
            colors = ButtonDefaults.textButtonColorsPrimary(), insideMargin = insideMargin, textStyle = style)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val paddingPx = with(density) { 2 * 16.dp.roundToPx() }
        if (constraints.maxWidth < 2 * (labelWidth + paddingPx) + with(density) { gap.roundToPx() }) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                cancel(Modifier.fillMaxWidth())
                confirm(Modifier.fillMaxWidth())
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            cancel(Modifier.weight(1f))
            confirm(Modifier.weight(1f))
        }
    }
}
