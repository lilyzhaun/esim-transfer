// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quicktransfer.app.core.*
import dev.quicktransfer.app.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun TransferPage(state: TransferUiState, source: SourceLine?, records: List<TransferRecord>, targetName: String,
    eid: String, imei: String, onEid: (String) -> Unit, onImei: (String) -> Unit, connect: () -> Unit, manager: () -> Unit,
    chooseSource: () -> Unit, chooseTarget: () -> Unit, viewRecord: (String) -> Unit,
    details: TargetDetails, onDetails: (TargetDetails) -> Unit, start: () -> Unit, modes: () -> Unit = {},
    back: (() -> Unit)? = null) {
    val active = LocalRouteActive.current
    val running = records.firstOrNull { it.blocksNewTransfer }
    val editable = active && running == null && !state.busy
    val detailFields = source?.targetFields.orEmpty()
    val optionalFields = (if (details.error() != null) targetDetailLabels.keys.toList() else emptyList()) - detailFields.toSet()
    var expanded by rememberSaveable(source?.subId) { mutableStateOf(false) }
    val blocker = transferBlocker(state, source, records, eid, imei, details)
    val canSubmit = editable && blocker == null
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    PageFrame(if (back == null) "转移" else "从本机转出", onBack = back, message = state.message,
        actions = { RefreshAction("刷新连接和源线路", active && !state.busy, connect, state.busy) }) {
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            ArrowPreference(title = state.mode.title, summary = if (state.host.connected && state.host.supported) "服务已连接" else "查看连接状态",
                onClick = modes.takeIf { active && !state.busy })
        } }
        if (!state.host.connected || !state.host.supported || state.host.notice.isNotBlank()) item {
            val connected = state.host.connected && state.host.supported && state.host.notice.isBlank()
            StatusPanel(1,
                 when { connected -> "服务已连接"; state.host.connected -> "服务暂不可用"; state.busy -> "正在连接服务"; else -> "准备连接模块" },
                when {
                    state.host.notice.isNotEmpty() -> state.host.notice
                     connected -> "选择源线路，填写目标身份后获取激活码。"
                    state.scopeGranted -> "已完成授权，连接服务后即可读取本机线路。"
                     else -> stringResource(R.string.enable_module, stringResource(R.string.app_name))
                 }, if (!connected) if (state.frameworkConnected && state.scopeGranted) "连接服务" else "打开 LSPosed" else null,
                 if (state.frameworkConnected && state.scopeGranted) connect else manager, enabled = active && !state.busy)
        }
        if (running != null) item {
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                ArrowPreference(title = running.phase.label, summary = running.message, onClick = ({ viewRecord(running.id) }).takeIf { active },
                    startAction = { Icon(MiuixIcons.Timer, null, Modifier.padding(end = 12.dp).size(24.dp)) })
            }
        }
        item { SmallTitle("源线路", textColor = uiColors().secondary) }
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            ArrowPreference(title = source?.displayTitle ?: "选择本机 SIM",
                summary = source?.displaySummary
                    ?: if (state.host.connected) "请选择要转移的线路" else "连接服务后读取可用线路",
                onClick = chooseSource.takeIf { editable && state.host.connected }, startAction = {
                    Icon(MiuixIcons.Phone, null, Modifier.padding(end = 12.dp).size(24.dp))
                })
        } }
        item { SmallTitle("目标设备", textColor = uiColors().secondary) }
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            ArrowPreference(title = targetName.ifBlank { "选择已保存的设备" }, summary = "也可直接填写目标设备的 EID 和 IMEI", onClick = chooseTarget.takeIf { editable })
        } }
        item { IdentityFields(eid, imei, onEid, onImei, editable) }
        if (detailFields.isNotEmpty()) item { TargetDetailFields(details, detailFields, onDetails, editable) }
        if (optionalFields.isNotEmpty()) item {
            InlineAction(if (expanded) "收起更多设备信息" else "更多设备信息（可选）", editable, { expanded = !expanded }, Modifier.fillMaxWidth())
        }
        if (expanded && optionalFields.isNotEmpty()) {
            item { TargetDetailFields(details, optionalFields, onDetails, editable) }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (blocker != null) {
                    Text(blocker.message, color = uiColors().secondary, fontSize = 14.sp,
                        modifier = Modifier.statusLiveRegion())
                    when (blocker.action) {
                        SetupAction.CONNECT -> InlineAction("设置连接方式", active && !state.busy, modes, Modifier.fillMaxWidth())
                        SetupAction.SOURCE -> InlineAction("选择源线路", editable && state.host.connected, chooseSource, Modifier.fillMaxWidth())
                        SetupAction.RECORD -> InlineAction("查看未完成任务", active, { running?.let { viewRecord(it.id) } }, Modifier.fillMaxWidth())
                        SetupAction.DETAILS -> if (!expanded) InlineAction("检查设备信息", editable, { expanded = true }, Modifier.fillMaxWidth())
                        SetupAction.TARGET -> Unit
                    }
                }
                PrimaryAction(canSubmit, {
                    keyboard?.hide(); focus.clearFocus()
                    start()
                },
                    Modifier.fillMaxWidth()) {
                    Text(if (state.preparingTransfer) "正在准备转移…" else "获取转移激活码")
                }
                Text("源设备完成身份验证后发起转移。运营商可能停用源卡，激活码用于上述目标设备。",
                    color = uiColors().secondary, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
internal fun IdentityFields(eid: String, imei: String, onEid: (String) -> Unit, onImei: (String) -> Unit, enabled: Boolean = true) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextField(value = eid, onValueChange = { onEid(it.filterNot(Char::isWhitespace)) }, label = "目标 EID", enabled = enabled,
            singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }))
        Text(if (eid.length >= 32 && IdentityRules.eidError(eid) != null) IdentityRules.eidError(eid)!! else "32 位数字 · ${eid.length}/32",
            fontSize = 13.sp, color = if (eid.length >= 32 && IdentityRules.eidError(eid) != null) MiuixTheme.colorScheme.error else uiColors().secondary,
            modifier = Modifier.padding(horizontal = 12.dp))
        TextField(value = imei, onValueChange = { onImei(it.filterNot(Char::isWhitespace)) }, label = "目标 IMEI", enabled = enabled,
            singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }))
        Text(if (imei.length >= 15 && IdentityRules.imeiError(imei) != null) IdentityRules.imeiError(imei)!! else "目标设备的 15 位 IMEI",
            fontSize = 13.sp, color = if (imei.length >= 15 && IdentityRules.imeiError(imei) != null) MiuixTheme.colorScheme.error else uiColors().secondary,
            modifier = Modifier.padding(horizontal = 12.dp))
    }
}

@Composable
internal fun StatusPanel(tone: Int, title: String, summary: String, action: String? = null, click: () -> Unit = {}, enabled: Boolean = true) {
    val dark = LocalDarkTheme.current
    val (background, ink) = when (tone) {
        0 -> if (dark) Color(0xFF133B2A) to Color(0xFFC0EDD2) else Color(0xFFE0F4E7) to Color(0xFF16472E)
        2 -> if (dark) Color(0xFF4C2025) to Color(0xFFFFD9DE) else Color(0xFFFBE6E6) to Color(0xFF742B34)
        else -> if (dark) Color(0xFF3D310F) to Color(0xFFF9E5AD) else Color(0xFFFAF0D9) to Color(0xFF644B19)
    }
    Card(Modifier.fillMaxWidth().statusLiveRegion(), insideMargin = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
                    if (tone == 0) CompletionMark(Modifier.size(22.dp), ink)
                    else Icon(MiuixIcons.Info, null, tint = ink, modifier = Modifier.size(22.dp))
                }
                Text(title, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(summary, color = uiColors().secondary, fontSize = 14.sp, lineHeight = 22.sp)
            if (action != null) TextButton(action, onClick = click, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(color = Color.Transparent, disabledColor = Color.Transparent, textColor = MiuixTheme.colorScheme.primary))
        }
    }
}

@Composable
internal fun EmptyState(title: String, summary: String, action: String? = null, click: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(MiuixIcons.File, null, tint = uiColors().secondary, modifier = Modifier.size(40.dp))
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Text(summary, color = uiColors().secondary, fontSize = 15.sp, lineHeight = 23.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) PrimaryAction(LocalRouteActive.current, click) { Text(action) }
    }
}

@Composable
internal fun SourcePicker(sources: List<SourceLine>, selected: Int?, back: () -> Unit, carriers: () -> Unit, select: (Int) -> Unit,
    refresh: (() -> Unit)? = null, busy: Boolean = false) {
    PageFrame("选择源线路", onBack = back, actions = {
        if (refresh != null) RefreshAction("刷新 SIM 列表", !busy, refresh, busy)
    }) {
        if (sources.isEmpty()) item { EmptyState(if (busy) "正在读取线路" else "没有可用线路",
            "确认 SIM 已启用，并连接 Google Play 服务后刷新。", if (refresh != null && !busy) "刷新线路" else null, { refresh?.invoke() }) }
        if (sources.isNotEmpty()) item { Card(Modifier.fillMaxWidth().selectableGroup(), insideMargin = PaddingValues(0.dp)) {
            sources.forEach { source ->
                BasicComponent(title = source.displayTitle, summary = source.displaySummary,
                    enabled = source.supported, role = Role.RadioButton,
                    modifier = Modifier.semantics { this.selected = selected == source.subId },
                    onClick = ({ select(source.subId) }).takeIf { source.supported },
                    endActions = { RadioButton(selected == source.subId, onClick = null, modifier = Modifier.clearAndSetSemantics {}) })
            }
        } }
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            ArrowPreference(title = "支持的运营商", summary = "按国家地区查看运营商", onClick = carriers)
        } }
    }
}

@Composable
internal fun DevicesPage(devices: List<TargetDevice>, add: () -> Unit, edit: (String) -> Unit) {
    val active = LocalRouteActive.current
    PageFrame("设备", actions = { if (devices.isNotEmpty()) IconButton(add, enabled = active, modifier = Modifier.size(48.dp)) { Icon(MiuixIcons.Add, "添加目标设备") } }) {
        if (devices.isEmpty()) item { EmptyState("保存你的目标设备", "填写一次 EID 和 IMEI，下一次转移时直接选择。", "添加设备", add) }
        else items(devices, key = { it.id }) { device ->
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                ArrowPreference(title = device.name, summary = "EID 尾号 ${device.eid.takeLast(6)}\nIMEI 尾号 ${device.imei.takeLast(4)}",
                    onClick = ({ edit(device.id) }).takeIf { active }, startAction = { Icon(MiuixIcons.Phone, null, Modifier.padding(end = 12.dp).size(24.dp)) })
            }
        }
    }
}

@Composable
internal fun TargetPicker(devices: List<TargetDevice>, back: () -> Unit, add: () -> Unit, select: (TargetDevice) -> Unit) {
    PageFrame("选择目标设备", onBack = back) {
        if (devices.isEmpty()) item { EmptyState("还没有保存的设备", "添加目标设备后，可直接用于本次转移。", "添加设备", add) }
        items(devices, key = { it.id }) { device -> Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            ArrowPreference(title = device.name, summary = "EID 尾号 ${device.eid.takeLast(6)}", onClick = { select(device) })
        } }
    }
}

@Composable
internal fun DeviceEditor(device: TargetDevice?, back: () -> Unit, save: (TargetDevice) -> Unit, delete: () -> Unit) {
    val active = LocalRouteActive.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var name by rememberSaveable(device?.id) { mutableStateOf(device?.name.orEmpty()) }
    var eid by rememberSaveable(device?.id) { mutableStateOf(device?.eid.orEmpty()) }
    var imei by rememberSaveable(device?.id) { mutableStateOf(device?.imei.orEmpty()) }
    var details by rememberSaveable(device?.id, stateSaver = TargetDetailsSaver) { mutableStateOf(device?.details ?: TargetDetails()) }
    var expanded by rememberSaveable(device?.id) { mutableStateOf(details.fields().values.any(String::isNotEmpty)) }
    var showDelete by remember { mutableStateOf(false) }
    var deletePending by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val valid = IdentityRules.valid(eid, imei) && details.error() == null
    PageFrame(if (device == null) "添加设备" else "编辑设备", onBack = back,
        message = feedback, dismissMessage = { feedback = "" }) {
        item { TextField(name, { name = it.take(80) }, label = "设备名称", singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { IdentityFields(eid, imei, { eid = it }, { imei = it }) }
        item { InlineAction(if (expanded) "收起补充信息" else "更多设备信息", true, { expanded = !expanded }, Modifier.fillMaxWidth()) }
        if (expanded) item { TargetDetailFields(details, targetDetailLabels.keys.toList(), { details = it }) }
        else details.error()?.let { message -> item { Text(message, color = MiuixTheme.colorScheme.error, fontSize = 13.sp) } }
        item { Text("填写最终下载 eSIM 的设备身份。信息保存在本机，可随时编辑。", color = uiColors().secondary, fontSize = 14.sp, lineHeight = 21.sp) }
        item {
            if (!valid) Text(IdentityRules.eidError(eid) ?: IdentityRules.imeiError(imei) ?: details.error().orEmpty(),
                color = uiColors().secondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            PrimaryAction(active && !saving && valid, {
                saving = true; feedback = ""; keyboard?.hide(); focus.clearFocus()
                try { save(TargetDevice(device?.id ?: java.util.UUID.randomUUID().toString(), name.ifBlank { "目标设备" }, eid, imei, details)) }
                catch (error: Exception) {
                    Log.e("QuickTransferUi", "目标设备保存失败", error)
                    feedback = "设备未能保存，请检查存储空间后重试；填写内容已保留"
                    saving = false
                }
            }, Modifier.fillMaxWidth()) { Text(if (saving) "正在保存…" else "保存设备") }
        }
        if (device != null) item { InlineAction("删除此设备", active && !saving, { showDelete = true }, Modifier.fillMaxWidth()) }
    }
    OverlayDialog(show = showDelete, title = "删除目标设备？", onDismissRequest = { showDelete = false; deletePending = false },
        onDismissFinished = { if (deletePending) {
            deletePending = false
            try { delete() } catch (error: Exception) {
                Log.e("QuickTransferUi", "目标设备删除失败", error); feedback = "设备未能删除，请重试"
            }
        } }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("仅移除本机保存的设备信息，已有转移记录仍然保留。")
            ConfirmationActions("删除设备", { showDelete = false }, { deletePending = true; showDelete = false })
        }
    }
}

internal fun dateText(time: Long): String = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
