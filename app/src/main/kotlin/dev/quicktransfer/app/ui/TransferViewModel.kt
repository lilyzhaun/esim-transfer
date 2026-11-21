// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.quicktransfer.app.TransferApplication
import dev.quicktransfer.app.core.*
import dev.quicktransfer.app.ipc.HostRegistry
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.UUID

data class TransferUiState(val host: HostSnapshot = HostSnapshot(), val framework: String = "未连接 LSPosed",
    val frameworkConnected: Boolean = false, val scopeGranted: Boolean = false, val busy: Boolean = false,
    val message: String = "", val sources: List<SourceLine> = emptyList(),
    val mode: ExecutionMode = ExecutionMode.GMS,
    val archivedTaskIds: Set<String> = emptySet(), val logModes: Set<String> = emptySet(),
    val dataRevision: Long = 0,
    val preparingTransfer: Boolean = false, val supportsLocalSim: Boolean = true)

class TransferViewModel(application: Application) : AndroidViewModel(application) {
    val library = (application as TransferApplication).library
    private val taskLogs = (application as TransferApplication).taskLogs
    private val app = application as TransferApplication
    private val mutable = MutableStateFlow(TransferUiState(supportsLocalSim = app.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY)))
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch { TransferApplication.framework.collect { service ->
            if (service == null) mutable.update { it.copy(frameworkConnected = false, scopeGranted = false, framework = "未连接 LSPosed") }
            else try {
                mutable.update { it.copy(frameworkConnected = true, scopeGranted = Contract.GMS in service.scope,
                    framework = "${service.frameworkName} ${service.frameworkVersion} · API ${service.apiVersion}") }
                discover()
            } catch (_: XposedService.ServiceException) { mutable.update { it.copy(message = "框架连接已中断，请重新打开管理器") } }
        } }
        viewModelScope.launch { HostRegistry.snapshot.collect { snapshot ->
            library.merge(snapshot.records)
            mutable.update {
                val message = when {
                    snapshot.notice.isNotBlank() && snapshot.notice != it.host.notice -> snapshot.notice
                    snapshot.notice.isBlank() && it.message == it.host.notice -> ""
                    else -> it.message
                }
                it.copy(host = snapshot, sources = snapshot.sources, message = message)
            }
        } }
        viewModelScope.launch { app.sharedData.archives.ids.collect { ids -> mutable.update { it.copy(archivedTaskIds = ids) } } }
        viewModelScope.launch { HostRegistry.snapshots.collect { snapshots -> mutable.update { state ->
            state.copy(logModes = snapshots.filterValues { it.connected && it.supportsDetailedLogs }.keys.map { it.name }.toSet())
        } } }
    }
    private suspend fun discover() = withContext(Dispatchers.IO) {
        TransferApplication.framework.value?.getRemotePreferences(Contract.PREFS)?.edit()
            ?.putInt(Contract.APP_UID, Process.myUid())?.putString(Contract.DISCOVERY, UUID.randomUUID().toString())?.commit()
        Unit
    }
    fun requestScope() {
        val service = TransferApplication.framework.value ?: return openManager()
        service.requestScope(listOf(Contract.GMS), object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) {
                mutable.update { it.copy(scopeGranted = Contract.GMS in approved, message = "作用域已授权，重新加载 GMS 后连接") }
                viewModelScope.launch { discover() }
            }
            override fun onScopeRequestFailed(message: String) { mutable.update { it.copy(message = "作用域尚未授权，请在 LSPosed 中启用本模块") } }
        })
    }
    fun openManager() {
        val context = getApplication<Application>()
        val intent = context.packageManager.getLaunchIntentForPackage("org.lsposed.manager")
        if (intent == null) mutable.update { it.copy(message = "请从通知或系统入口打开 LSPosed 管理器") }
        else context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun connect() {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = "") }
        viewModelScope.launch {
            try {
                discover()
                if (HostRegistry.connection.value == null) {
                    val launch = sourceIntent().putExtra(Contract.DISCOVER_EXTRA, true)
                    getApplication<Application>().startActivity(launch)
                    withTimeout(6500) { HostRegistry.connection.filterNotNull().first() }
                }
                withContext(Dispatchers.IO) { HostRegistry.connection.value?.refresh() }
            } catch (_: TimeoutCancellationException) {
                mutable.update { it.copy(message = "尚未连接。请在 LSPosed 启用模块并勾选 Google Play 服务，重载 GMS 后重试") }
            } catch (error: Exception) {
                mutable.update { it.copy(message = "连接失败，请检查模块是否已启用后重试") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun start(source: SourceLine, target: TargetDevice, ready: (String) -> Unit) {
        if (mutable.value.busy) return
        val currentSource = mutable.value.sources.firstOrNull { it.subId == source.subId && it.backend == source.backend }
        val blocker = transferBlocker(mutable.value, currentSource, library.records.value, target.eid, target.imei, target.details)
        if (blocker != null) { mutable.update { it.copy(message = blocker.message) }; return }
        mutable.update { it.copy(busy = true, preparingTransfer = true, message = "") }
        viewModelScope.launch {
            var id: String? = null
            try {
                check(library.records.value.none { it.blocksNewTransfer }) { "已有转移任务等待结果，请先处理原任务" }
                val host = requireNotNull(HostRegistry.connection.value) { "服务尚未连接" }
                id = withContext(Dispatchers.IO) {
                    host.prepare(JSONObject().put("subId", source.subId).put("target", target.json()).toString())
                }
                ready(id)
                val intent = sourceIntent()
                app.startActivity(intent.putExtra(Contract.TASK_EXTRA, id))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val pending = id
                if (pending != null) withContext(Dispatchers.IO) { HostRegistry.connection.value?.cancelConfirmation(pending) }
                Log.e("QuickTransferUi", "转移准备失败", error)
                mutable.update { it.copy(message = if (error is IllegalStateException || error is IllegalArgumentException) error.message.orEmpty() else "无法开始转移，请重新连接服务后重试") }
            } finally { mutable.update { it.copy(busy = false, preparingTransfer = false) } }
        }
    }
    fun cancel(id: String) = action { checkRecordMode(id); it.cancelConfirmation(id) }
    fun deleteRecord(id: String): String = try {
        check(HostRegistry.snapshots.value.values.flatMap { it.records }.firstOrNull { it.id == id }?.canDeleteFromHistory != false) {
            "请先完成或结束任务，再删除记录"
        }
        library.deleteRecord(id); app.sharedData.deleteLogs(id); ""
    } catch (error: Exception) { error.message ?: "删除未完成，请重试" }
    suspend fun taskLog(id: String, refresh: Boolean): TaskLog? = withContext(Dispatchers.IO) {
        if (!refresh) return@withContext taskLogs.read(id)
        val record = library.records.value.firstOrNull { it.id == id } ?: return@withContext taskLogs.read(id)
        val host = app.sharedData.owner(record)
        if (host != null) {
            try { app.sharedData.syncRecord(record, host, record.executionMode in mutable.value.logModes ||
                record.executionMode == mutable.value.host.executionMode && mutable.value.host.supportsDetailedLogs) }
            catch (error: Exception) { if (error is CancellationException || taskLogs.read(id) == null) throw error }
        }
        taskLogs.read(id)
    }
    fun resume(id: String) = action { checkRecordMode(id); it.resumePolling(id) }
    suspend fun exportTaskLog(id: String, uri: Uri) = withContext(Dispatchers.IO) {
        val record = requireNotNull(library.records.value.firstOrNull { it.id == id }) { "任务不存在" }
        val archives = app.sharedData.archives
        val host = app.sharedData.owner(record)
        if (host != null) try { app.sharedData.syncRecord(record, host) }
            catch (error: Exception) { if (error is CancellationException || !archives.contains(id)) throw error }
        check(archives.contains(id)) { "详细日志尚未同步，请连接服务后重试" }
        requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")).use { archives.copyTo(id, it) }
    }
    fun syncHistory() {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = "") }
        viewModelScope.launch {
            try {
                discover()
                withContext(Dispatchers.IO) { HostRegistry.connection.value?.refresh() }
                mutable.update { it.copy(message = app.sharedData.notice.value.ifBlank { "记录已刷新" }) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(message = "暂时无法更新记录，请检查服务连接后重试") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun endLocalTask(id: String) = action { host ->
        checkRecordMode(id)
        val ended = TransferRecord.parse(JSONObject(host.endLocalTask(id)))
        check(ended.id == id && ended.phase == Phase.CANCELLED) { "任务状态尚未同步，请刷新后重试" }
        library.merge(listOf(ended))
    }
    fun leaveWebSheet(id: String?) {
        val record = library.records.value.firstOrNull { it.id == id && it.phase == Phase.ISSUED && it.webSheetId.isNotEmpty() } ?: return
        action { checkRecordMode(record.id); it.leaveWebSheet(record.id, record.webSheetId) }
    }
    private fun checkRecordMode(id: String): TransferRecord {
        val record = requireNotNull(library.records.value.firstOrNull { it.id == id }) { "任务不存在" }
        return record
    }
    fun clearMessage() { mutable.update { it.copy(message = "") } }
    private fun action(block: (dev.quicktransfer.app.ipc.ITransferHost) -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = "") }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block(requireNotNull(HostRegistry.connection.value)) } }
            catch (error: Exception) { mutable.update { it.copy(message = if (error is IllegalStateException) error.message.orEmpty() else "操作未完成，请刷新状态后重试") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private fun sourceIntent() = Intent(Intent.ACTION_VIEW, Uri.parse("https://simtransfer.goog/"))
        .setClassName(Contract.GMS, Contract.SOURCE_ACTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
