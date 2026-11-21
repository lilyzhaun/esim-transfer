// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quicktransfer.app.BuildConfig
import dev.quicktransfer.app.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun SettingsPage(state: TransferUiState, appearance: Appearance, style: () -> Unit, diagnostics: () -> Unit,
    manager: () -> Unit, scope: () -> Unit, about: () -> Unit, carriers: () -> Unit) {
    val active = LocalRouteActive.current
    PageFrame("设置") {
        item { SmallTitle("转移设置", textColor = uiColors().secondary) }
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            ArrowPreference(title = "支持的运营商", summary = "查看国家地区与运营商", onClick = carriers.takeIf { active })
        } }
        item { SmallTitle("管理应用", textColor = uiColors().secondary) }
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            ArrowPreference(title = "外观与交互", summary = "${appearance.theme.title} · ${appearance.accent.title} · ${if (appearance.floating) "悬浮底栏" else "标准底栏"}",
                onClick = style.takeIf { active })
            ArrowPreference(title = "连接状态", summary = if (state.host.connected) "${state.mode.title} 已连接" else "查看权限和服务状态", onClick = diagnostics.takeIf { active })
                ArrowPreference(title = "LSPosed 管理", summary = "作用域：Google Play 服务", onClick = manager.takeIf { active })
                if (!state.scopeGranted) ArrowPreference(title = "授权 Google Play 服务", summary = "在 LSPosed 中确认授权", onClick = scope.takeIf { active })
            ArrowPreference(title = stringResource(R.string.about_app, stringResource(R.string.app_name)),
                summary = BuildConfig.VERSION_NAME, onClick = about.takeIf { active })
        } }
        item { Text("目标设备和转移记录保存在本机。", color = uiColors().secondary,
            fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
    }
}

private enum class AppearanceChooser { THEME, ACCENT }

@Composable
internal fun AppearancePage(store: AppearanceStore, back: () -> Unit) {
    val state by store.state.collectAsState()
    val supported = LocalShaderSupported.current
    var chooser by remember { mutableStateOf(AppearanceChooser.THEME) }
    var showChooser by remember { mutableStateOf(false) }
    var pendingTheme by remember { mutableStateOf<ThemeMode?>(null) }
    var pendingAccent by remember { mutableStateOf<Accent?>(null) }
    fun openChooser(value: AppearanceChooser) {
        chooser = value; pendingTheme = null; pendingAccent = null; showChooser = true
    }
    PageFrame("外观与交互", onBack = back) {
        item { SmallTitle("主题", textColor = uiColors().secondary) }
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            ArrowPreference(title = "外观模式", summary = state.theme.title, onClick = { openChooser(AppearanceChooser.THEME) })
            ArrowPreference(title = "主题色", summary = state.accent.title, onClick = { openChooser(AppearanceChooser.ACCENT) }, startAction = {
                Box(Modifier.padding(end = 12.dp).size(22.dp).background(if (LocalDarkTheme.current) state.accent.dark else state.accent.light, CircleShape))
            })
        } }
        item { SmallTitle("导航与材质", textColor = uiColors().secondary) }
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            SwitchPreference(title = "悬浮底栏", summary = if (state.floating) "胶囊底栏与可拖动的选中气泡" else "使用标准底部导航", checked = state.floating, onCheckedChange = { store.set("floating_nav", it) })
            SwitchPreference(title = "模糊效果", summary = if (supported) "顶栏与关于页信息卡片使用磨砂材质" else "设备不支持，使用实色背景", checked = state.blur, enabled = supported, onCheckedChange = { store.set("blur_enabled", it) })
            SwitchPreference(title = "液态玻璃", summary = when {
                !supported -> "设备不支持，使用实色滑动气泡"
                !state.floating -> "开启悬浮底栏后显示效果"
                state.liquid -> "透明折射气泡 · 关闭后使用实色气泡"
                else -> "实色滑动气泡 · 开启后使用液态玻璃"
            }, checked = state.liquid, enabled = supported && state.floating, onCheckedChange = { store.set("liquid_glass_enabled", it) })
        } }
        item { SmallTitle("应用交互", textColor = uiColors().secondary) }
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            SwitchPreference(title = "预测性返回", summary = "返回手势跟手预览上一页", checked = state.predictiveBack, onCheckedChange = { store.set("predictive_back_enabled", it) })
            SwitchPreference(title = "触感反馈", summary = "跟随系统触感设置", checked = state.haptics, onCheckedChange = { store.set("haptics_enabled", it) })
        } }
        item { Text("设置即时生效并保存在本机。字体大小和动画速度跟随系统。", color = uiColors().secondary, fontSize = 14.sp, lineHeight = 21.sp) }
    }
    // Retain content through the exit; apply global palette changes only after the overlay is gone.
    OverlayDialog(show = showChooser, title = if (chooser == AppearanceChooser.ACCENT) "选择主题色" else "外观模式",
        onDismissRequest = { if (showChooser) { pendingTheme = null; pendingAccent = null; showChooser = false } },
        onDismissFinished = {
            val theme = pendingTheme; val accent = pendingAccent
            pendingTheme = null; pendingAccent = null
            theme?.let(store::theme); accent?.let(store::accent)
        }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            if (chooser == AppearanceChooser.THEME) ThemeMode.entries.forEach { mode ->
                BasicComponent(title = mode.title, onClick = { if (showChooser) { pendingTheme = mode; showChooser = false } },
                    endActions = { RadioButton((pendingTheme ?: state.theme) == mode, onClick = null) })
            } else Accent.entries.forEach { accent ->
                BasicComponent(title = accent.title, onClick = { if (showChooser) { pendingAccent = accent; showChooser = false } }, startAction = {
                    Box(Modifier.padding(end = 12.dp).size(22.dp).background(if (LocalDarkTheme.current) accent.dark else accent.light, CircleShape))
                }, endActions = { RadioButton((pendingAccent ?: state.accent) == accent, onClick = null) })
            }
        }
    }
}

@Composable
internal fun DiagnosticsPage(state: TransferUiState, back: () -> Unit, refresh: () -> Unit) {
    PageFrame("连接状态", onBack = back, message = state.message,
        actions = { RefreshAction("刷新模块连接", !state.busy, refresh, state.busy) }) {
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            BasicComponent(title = "运行模式", summary = state.mode.title)
                BasicComponent(title = "LSPosed", summary = state.framework)
                BasicComponent(title = "Google Play 服务授权", summary = if (state.scopeGranted) "已授权" else "等待授权")
            BasicComponent(title = "服务连接", summary = if (state.host.connected) "已连接" else "未连接")
            BasicComponent(title = "Google Play 服务", summary = state.host.version.ifBlank { "尚未读取" })
            BasicComponent(title = "服务状态", summary = if (state.host.supported) "可用" else "请检查服务和模块版本")
        } }
        if (state.host.notice.isNotEmpty() && state.host.notice != state.message) item { StatusPanel(1, "连接提示", state.host.notice) }
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            BasicComponent(title = "下载安装", summary = "在目标设备使用结果页的二维码或激活码")
        } }
    }
}
