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
                feedback = "Complete log exported"
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                feedback = "Export failed. Check the connection and destination, then try again"
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
            readError = "New log data could not be synced. The local copy is retained; sync and retry"
        } finally { loading = false }
    }
    val format = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    PageFrame("Task log", onBack = back, message = state.message.ifEmpty { feedback }.ifEmpty { readError },
        dismissMessage = { feedback = ""; readError = ""; dismissOperation() },
        actions = { RefreshAction("Refresh task log", !loading && !state.busy, { refresh++ }, loading) }) {
        item { Text("This page shows a summary. Exported files include complete identities, credentials, and captured requests and responses.",
            color = uiColors().secondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(12.dp)) }
        if (!connected) item {
            Text(if (archived) "The complete log is stored locally and can be exported." else if (log == null)
                "This older log is not synced. Connect the original service or authorized Shizuku to retrieve it." else "Showing the local copy. Switching execution mode does not remove it.", color = uiColors().secondary)
            InlineAction(if (state.busy) "Syncing…" else "Sync existing logs", !loading && !state.busy, connect, Modifier.fillMaxWidth())
        }
        if (state.dataNotice.isNotEmpty()) item { Text(state.dataNotice, color = uiColors().secondary, modifier = Modifier.statusLiveRegion()) }
        if (log != null || canExport) item { PrimaryAction(record != null && canExport && !exporting && exportId == null, {
            feedback = ""; dismissOperation()
            exportId = record?.id; export.launch("eSIM-log-${record?.createdAt ?: System.currentTimeMillis()}.txt")
        }, Modifier.fillMaxWidth()) { Text(if (exporting) "Exporting…" else "Export complete log") } }
        val current = log
        if (current != null) {
            if (!current.complete) item { Text("Only part of this older task was retained. Earlier events cannot be recovered.", color = uiColors().secondary, fontSize = 14.sp) }
            if (current.dropped > 0) item { Text("${current.dropped} earlier entries are omitted", color = uiColors().secondary, fontSize = 14.sp) }
            if (connected && !canExport) item { Text("Update the module and reconnect the original service to sync complete logs.", color = uiColors().secondary) }
            item { InlineAction("Copy log", current.entries.isNotEmpty(), {
                dismissOperation()
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Task log", current.text()))
                feedback = "Log copied"
            }, Modifier.fillMaxWidth()) }
            items(current.entries) { entry ->
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${format.format(Date(entry.time))} · ${entry.title}", fontSize = 16.sp)
                        SelectionContainer { Text(entry.detail, color = uiColors().secondary, fontSize = 14.sp, lineHeight = 21.sp) }
                    }
                }
            }
        } else if (loading) item { Text("Loading log…", modifier = Modifier.padding(12.dp).statusLiveRegion()) }
        else if (readError.isEmpty() && connected) item { EmptyState("No log available", "New transfers automatically record their progress.") }
    }
}
