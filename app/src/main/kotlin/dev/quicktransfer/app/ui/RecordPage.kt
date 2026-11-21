// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.PersistableBundle
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.quicktransfer.app.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun RecordPage(record: TransferRecord?, state: TransferUiState, back: () -> Unit, refresh: () -> Unit, cancel: (String) -> Unit, resume: (String) -> Unit,
    openWebSheet: () -> Unit = {}, openLog: () -> Unit = {}, endLocalTask: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissOperation = LocalDismissOperationMessage.current
    var feedback by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var showEnd by remember(record?.id) { mutableStateOf(false) }
    var showCode by rememberSaveable(record?.id) { mutableStateOf(false) }
    var showInfo by rememberSaveable(record?.id) { mutableStateOf(false) }
    val active = LocalRouteActive.current
    val connected = state.host.connected
    val endEnabled = record?.canEndLocally == true && connected && state.host.supportsLocalTaskEnd &&
        !state.busy && state.host.records.none { it.phase.running }
    val code = record?.activationCode.orEmpty()
    val qr by produceState<Bitmap?>(null, code) {
        value = null
        if (code.isNotEmpty()) value = withContext(Dispatchers.Default) {
            createDownloadQr(ActivationCodes.standard(code))
        }
    }
    fun copy(label: String, value: String) {
        dismissOperation()
        val data = ClipData.newPlainText(label, value)
        data.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(data)
        feedback = "已复制$label"
    }
    PageFrame("转移记录", onBack = back, message = state.message.ifEmpty { feedback },
        dismissMessage = { feedback = ""; dismissOperation() },
        actions = { RefreshAction("刷新任务状态", active && !state.busy, refresh, state.busy) },
        scrollKey = record?.phase == Phase.ISSUED, footer = if (code.isNotEmpty()) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryAction(active, { copy("激活码", ActivationCodes.standard(code)) }, Modifier.fillMaxWidth()) { Text("复制完整激活码") }
                    InlineAction(if (saving) "正在保存二维码…" else "保存二维码到相册", active && qr != null && !saving, {
                        val bitmap = qr ?: return@InlineAction
                        saving = true; feedback = ""; dismissOperation()
                        scope.launch {
                            try { withContext(Dispatchers.IO) { saveQr(context, bitmap) }; feedback = "二维码已保存到相册" }
                            catch (_: Exception) { feedback = "保存失败，请检查可用存储空间后重试" }
                            finally { saving = false }
                        }
                    }, Modifier.fillMaxWidth())
                }
            }
        } else null) {
        if (record == null) item { EmptyState("正在同步任务", "连接服务后可查看当前任务和已保存结果。") }
        else {
            if (!connected && (record.blocksNewTransfer || record.canEndLocally || record.webSheetId.isNotEmpty())) item {
                InlineAction(if (state.busy) "正在连接…" else "重新连接以处理任务", !state.busy, refresh, Modifier.fillMaxWidth())
            }
            item { TaskOutcomeHeader(if (!state.host.connected && record.phase.running) record.copy(message = "服务暂时断开，重新连接后核对当前任务") else record) }
            if (record.phase != Phase.ISSUED) item { TaskRouteSummary(record) }
            if (record.phase == Phase.CONFIRM) item { InlineAction("取消源端验证", connected && !state.busy, { cancel(record.id) }, Modifier.fillMaxWidth()) }
            if (record.canResume) item { PrimaryAction(connected && !state.busy, { resume(record.id) }, Modifier.fillMaxWidth()) { Text("继续查询原请求") } }
            if (record.phase != Phase.ISSUED && record.webSheetId.isNotEmpty()) item {
                PrimaryAction(connected && !state.busy && !record.phase.running, openWebSheet, Modifier.fillMaxWidth()) { Text("打开运营商网页") }
                Text(record.webSheetHost, color = uiColors().secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (record.canEndLocally) item {
                InlineAction("结束本地任务", endEnabled, { showEnd = true }, Modifier.fillMaxWidth())
                if (!state.host.connected || !state.host.supportsLocalTaskEnd) Text(
                    if (!state.host.connected) "连接服务后可结束此任务" else "请重载 GMS 并重新连接，以使用结束任务功能",
                    color = uiColors().secondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (code.isNotEmpty()) {
                item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        qr?.let { Image(it.asImageBitmap(), "目标设备下载 eSIM 的激活二维码", Modifier.widthIn(max = 252.dp).fillMaxWidth().aspectRatio(1f).background(Color.White)) }
                        Text("在目标设备扫描二维码", fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
                        Text("用于 ${record.target.name} · EID 尾号 ${record.target.eid.takeLast(6)}", color = uiColors().secondary,
                            fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
                    }
                } }
                item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    DisclosureRow(title = "完整激活码", expanded = showCode, summary = if (showCode) "收起激活码" else "手动添加 eSIM 时使用",
                        onClick = { showCode = !showCode })
                    if (showCode) Box(Modifier.padding(20.dp)) {
                        SelectionContainer { Text(ActivationCodes.standard(code), fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 22.sp) }
                    }
                } }
            }
            val server = ActivationCodes.server(code).ifBlank { record.smdpAddresses.joinToString("\n") }
            if (server.isNotBlank() && (code.isEmpty() || showInfo)) item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                BasicComponent(title = "SM-DP+ 地址", summary = server, onClick = { copy("SM-DP+ 地址", server) })
            } }
            if (record.downloadIccid.isNotBlank() && (code.isEmpty() || showInfo)) item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                BasicComponent(title = "下载配置 ICCID", summary = record.downloadIccid, onClick = { copy("ICCID", record.downloadIccid) })
            } }
            item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                ArrowPreference(title = "任务日志", summary = "查看过程或导出记录", onClick = openLog.takeIf { active })
                DisclosureRow(title = "设备与任务详情", expanded = showInfo, summary = if (showInfo) "收起详细信息" else "${record.target.name} · ${dateText(record.createdAt)}",
                    onClick = { showInfo = !showInfo }, modifier = Modifier)
            } }
            if (showInfo) item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                BasicComponent(title = "源线路", summary = "${record.source.displayTitle}\n${record.source.displaySummary}")
                BasicComponent(title = "目标设备", summary = record.target.name)
                BasicComponent(title = "目标 EID", summary = record.target.eid)
                BasicComponent(title = "目标 IMEI", summary = record.target.imei)
                record.target.details.fields().filterValues(String::isNotBlank).forEach { (key, value) ->
                    BasicComponent(title = targetDetailLabels.getValue(key), summary = value)
                }
                BasicComponent(title = "发起时间", summary = dateText(record.createdAt))
                BasicComponent(title = "运营商请求", summary = if (record.submitted) "已提交转移请求" else "尚未提交转移请求")
                if (record.phase == Phase.ISSUED && record.webSheetId.isNotEmpty())
                    ArrowPreference(title = "返回运营商网页", onClick = openWebSheet.takeIf { connected && !state.busy })
            } }
        }
    }
    ConfirmationDialog(showEnd, "结束本地任务？", "确认结束", dismiss = { showEnd = false },
        confirm = { if (record != null) { showEnd = false; endLocalTask(record.id) } }, enabled = endEnabled) {
        DialogText("结束后可发起新任务，记录和日志仍保留。此操作不会撤销运营商申请，原网页将无法继续。")
    }
}

internal fun createDownloadQr(code: String): Bitmap {
    val matrix = QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 640, 640, mapOf(EncodeHintType.MARGIN to 4))
    return Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).apply {
        val pixels = IntArray(640 * 640) { i -> if (matrix[i % 640, i / 640]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        setPixels(pixels, 0, 640, 0, 0, 640, 640)
    }
}

internal fun saveQr(context: Context, bitmap: Bitmap) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "eSIM-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/eSIM transfer")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
    try {
        requireNotNull(resolver.openOutputStream(uri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
}
