// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quicktransfer.app.core.TaskLog
import dev.quicktransfer.app.core.TransferRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TaskLogPage(record: TransferRecord?, state: TransferUiState, back: () -> Unit, connect: () -> Unit,
    load: suspend (String, Boolean) -> TaskLog?, exportLog: suspend (String, android.net.Uri) -> Unit) {
    val connected = record != null && (record.executionMode in state.logModes ||
        state.host.connected && record.executionMode == state.host.executionMode)
    val archived = record?.id in state.archivedTaskIds
    val canExport = archived || connected && (record?.executionMode in state.logModes || state.host.supportsDetailedLogs)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissOperation = LocalDismissOperationMessage.current
    var log by remember(record?.id) { mutableStateOf<TaskLog?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf("") }
    var readError by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val id = exportId; exportId = null
        if (uri != null && id != null) scope.launch {
            exporting = true
            try {
                exportLog(id, uri)
                feedback = "详细日志已导出"
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                feedback = "导出未完成，请检查服务连接与保存位置后重试"
            } finally { exporting = false }
        }
    }
    LaunchedEffect(record?.id, record?.updatedAt, connected, refresh, archived, state.dataRevision) {
        val id = record?.id ?: return@LaunchedEffect
        loading = true; readError = ""
        try {
            log = load(id, false)
            if (connected || refresh > 0) log = load(id, true) ?: log
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            readError = "暂时无法更新日志，当前显示已保存的内容"
        } finally { loading = false }
    }
    val format = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    PageFrame("任务日志", onBack = back, message = state.message.ifEmpty { feedback }.ifEmpty { readError },
        dismissMessage = { feedback = ""; readError = ""; dismissOperation() },
        actions = { RefreshAction("刷新任务日志", !loading && !state.busy, { refresh++ }, loading) }) {
        item { Text("这里显示和复制简略记录。导出文件包含完整身份、凭据及已采集的请求与响应。",
            color = uiColors().secondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(12.dp)) }
        if (!connected) item {
            Text(if (archived) "完整日志已保存在本应用中，可直接导出。" else if (log == null)
                "尚未取得完整日志，请连接服务后重试。" else "可刷新日志查看最新进度。", color = uiColors().secondary)
            InlineAction(if (state.busy) "正在刷新…" else "刷新日志", !loading && !state.busy, connect, Modifier.fillMaxWidth())
        }
        if (log != null || canExport) item { PrimaryAction(record != null && canExport && !exporting && exportId == null, {
            feedback = ""; dismissOperation()
            exportId = record?.id; export.launch("eSIM-log-${record?.createdAt ?: System.currentTimeMillis()}.txt")
        }, Modifier.fillMaxWidth()) { Text(if (exporting) "正在导出…" else "导出详细日志") } }
        val current = log
        if (current != null) {
            if (!current.complete) item { Text("这项旧任务仅保留部分记录，早期过程无法补回。", color = uiColors().secondary, fontSize = 14.sp) }
            if (current.dropped > 0) item { Text("已省略较早的 ${current.dropped} 条日志", color = uiColors().secondary, fontSize = 14.sp) }
            if (connected && !canExport) item { Text("更新模块并重新连接原服务后，可同步详细日志。", color = uiColors().secondary) }
            item { InlineAction("复制日志", current.entries.isNotEmpty(), {
                dismissOperation()
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("任务日志", current.text()))
                feedback = "日志已复制"
            }, Modifier.fillMaxWidth()) }
            items(current.entries) { entry ->
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${format.format(Date(entry.time))} · ${entry.title}", fontSize = 16.sp)
                        SelectionContainer { Text(entry.detail, color = uiColors().secondary, fontSize = 14.sp, lineHeight = 21.sp) }
                    }
                }
            }
        } else if (loading) item { Text("正在读取日志…", modifier = Modifier.padding(12.dp).statusLiveRegion()) }
        else if (readError.isEmpty() && connected) item { EmptyState("暂无日志", "新的转移任务会自动记录过程。") }
    }
}
