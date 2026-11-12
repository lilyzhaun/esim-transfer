// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quicktransfer.app.core.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import androidx.compose.ui.semantics.*

@Composable
internal fun HistoryPage(records: List<TransferRecord>, delete: (String) -> String = { "" },
    sync: (() -> Unit)? = null, syncing: Boolean = false, notice: String = "", select: (String) -> Unit) {
    val active = LocalRouteActive.current
    var query by rememberSaveable { mutableStateOf("") }
    var successfulOnly by rememberSaveable { mutableStateOf(false) }
    var deletionId by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var deletePending by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    val deleting = records.firstOrNull { it.id == deletionId }
    val visible = remember(records, query, successfulOnly) {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        records.filter { record ->
            (!successfulOnly || record.phase == Phase.ISSUED) && terms.all { term ->
                listOf(record.source.displayTitle, record.target.name, record.target.eid, record.target.imei,
                    record.id, record.statusTitle, dateText(record.createdAt), record.source.backend,
                    ExecutionMode.parse(record.executionMode).title).any { it.contains(term, ignoreCase = true) }
            }
        }
    }
    val operationMessage = LocalOperationMessage.current
    val dismissOperation = LocalDismissOperationMessage.current
    PageFrame("History", message = operationMessage.ifEmpty { feedback },
        dismissMessage = { feedback = ""; dismissOperation() }, actions = {
        if (sync != null) RefreshAction("Sync history and logs", active && !syncing, sync, syncing)
    }) {
        if (notice.isNotEmpty()) item { Text(notice, color = uiColors().secondary, fontSize = 13.sp, modifier = Modifier.statusLiveRegion()) }
        if (records.isEmpty()) item { EmptyState("No transfers yet", "Transfer progress and download results will appear here.") }
        else {
            item { TextField(query, { query = it }, label = "Search carrier, device, or identity suffix", singleLine = true,
                enabled = active, modifier = Modifier.fillMaxWidth()) }
            item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                SwitchPreference(title = "Successful results only", summary = "Activation code or download configuration received", checked = successfulOnly,
                    onCheckedChange = { successfulOnly = it }, enabled = active)
            } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${visible.size} records", color = uiColors().secondary, fontSize = 14.sp)
                if (query.isNotEmpty() || successfulOnly) TextButton(text = "Clear filters", enabled = active,
                    onClick = { query = ""; successfulOnly = false })
            } }
            if (visible.isEmpty()) item { EmptyState("No matching records", "Try another keyword or clear the filters.") }
            items(visible, key = { it.id }) { record -> Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        BasicComponent(title = "${record.source.displayTitle} → ${record.target.name}",
                            summary = "${record.statusTitle}\n${dateText(record.createdAt)}" +
                                if (record.canDeleteFromHistory) "" else "\nAvailable for deletion after the task ends",
                            onClick = ({ select(record.id) }).takeIf { active })
                    }
                    IconButton(enabled = active && record.canDeleteFromHistory, modifier = Modifier.size(48.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Delete record"; role = Role.Button
                            if (!active || !record.canDeleteFromHistory) disabled()
                        },
                        onClick = { deletionId = record.id; showDelete = true }) {
                        Icon(MiuixIcons.Delete, null, Modifier.size(22.dp), tint = uiColors().secondary)
                    }
                }
            } }
        }
    }
    OverlayDialog(show = showDelete, title = "Delete this record?", onDismissRequest = { showDelete = false; deletePending = false },
        onDismissFinished = {
            if (deletePending) {
                deletePending = false
                deletionId?.let { dismissOperation(); feedback = delete(it).ifEmpty { "Record deleted" } }
            }
            deletionId = null
        }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            deleting?.let { Text("${it.source.displayTitle} → ${it.target.name}\n${dateText(it.createdAt)}") }
            Text("This removes the local record. It does not cancel a request already sent to the carrier.")
            ConfirmationActions("Delete", { showDelete = false; deletePending = false },
                { deletePending = true; showDelete = false }, enabled = active && deleting?.canDeleteFromHistory == true)
        }
    }
}
