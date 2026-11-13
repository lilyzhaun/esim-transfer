// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.data

import android.content.Context
import android.util.AtomicFile
import dev.quicktransfer.app.core.TransferRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.FilterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** App-owned complete exports, independent of the currently selected execution host. */
class TaskArchiveStore(context: Context) {
    private val root = File(context.noBackupFilesDir, "shared-task-archives")
    private val revisions = context.getSharedPreferences("shared_task_archives", 0)
    private val mutable = MutableStateFlow(revisions.all.keys.filter { file(it).baseFile.exists() }.toSet())
    val ids = mutable.asStateFlow()
    private fun file(id: String): AtomicFile {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "任务编号无效" }
        return AtomicFile(File(root, "$id.txt.gz"))
    }
    fun contains(id: String) = id in mutable.value
    fun needsUpdate(record: TransferRecord) = !contains(record.id) || revisions.getLong(record.id, -1) < record.updatedAt
    @Synchronized fun save(record: TransferRecord, input: InputStream) {
        if (contains(record.id) && revisions.getLong(record.id, -1) > record.updatedAt) return
        root.mkdirs()
        val atomic = file(record.id); val output = atomic.startWrite()
        try {
            GZIPOutputStream(object : FilterOutputStream(output) { override fun close() { flush() } }).use { compressed ->
                input.copyTo(compressed)
            }
            atomic.finishWrite(output)
        } catch (error: Exception) { atomic.failWrite(output); throw error }
        revisions.edit().putLong(record.id, record.updatedAt).apply()
        mutable.value = mutable.value + record.id
    }
    @Synchronized fun copyTo(id: String, output: OutputStream) {
        check(contains(id)) { "详细日志尚未同步，请连接原服务或 Shizuku 后重试" }
        GZIPInputStream(file(id).openRead()).use { it.copyTo(output) }
    }
    @Synchronized fun delete(id: String) {
        file(id).delete(); revisions.edit().remove(id).apply(); mutable.value = mutable.value - id
    }
}
