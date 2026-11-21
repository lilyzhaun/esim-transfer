// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.data

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import dev.quicktransfer.app.core.*
import dev.quicktransfer.app.ipc.HostRegistry
import dev.quicktransfer.app.ipc.ITransferHost
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

/** Canonical user settings and complete history belong to the app, not an execution mode. */
class SharedUserData(context: Context, private val library: LocalStore, private val summaries: TaskLogStore) {
    val archives = TaskArchiveStore(context)
    private val noticeMutable = MutableStateFlow("")
    val notice = noticeMutable.asStateFlow()
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    fun start() {
        scope.launch { HostRegistry.snapshots.collect { snapshots ->
            snapshots.forEach { (mode, snapshot) ->
                library.merge(snapshot.records)
                val host = HostRegistry.hostFor(mode) ?: return@forEach
                snapshot.records.filterNot { library.isRecordDeleted(it.id) }.forEach { record ->
                    try { if (archives.needsUpdate(record)) syncRecord(record, host, snapshot.supportsDetailedLogs) }
                    catch (error: Exception) { failed(error) }
                }
            }
        } }
    }
    fun owner(record: TransferRecord) = HostRegistry.hostFor(ExecutionMode.parse(record.executionMode))
    fun syncRecord(record: TransferRecord, host: ITransferHost, detailed: Boolean = true) {
        if (library.isRecordDeleted(record.id)) return
        val log = TaskLog.parse(JSONObject(host.getTaskLog(record.id)))
        check(log.taskId == record.id) { "日志与任务不一致" }
        summaries.save(log)
        if (detailed) host.exportTaskLog(record.id)?.let { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { archives.save(record, it) }
        }
        if (library.isRecordDeleted(record.id)) deleteLogs(record.id)
    }
    fun deleteLogs(id: String) { summaries.delete(id); archives.delete(id) }
    private fun failed(error: Exception) {
        Log.e("QuickTransferData", "共享数据同步未完成", error)
        noticeMutable.value = "部分记录暂未更新，请检查服务连接后重试"
    }
}
