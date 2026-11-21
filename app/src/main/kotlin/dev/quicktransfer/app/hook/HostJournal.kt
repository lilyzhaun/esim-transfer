// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.hook

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import dev.quicktransfer.app.core.*
import dev.quicktransfer.app.BuildConfig
import dev.quicktransfer.app.data.TaskLogStore
import dev.quicktransfer.app.data.DetailedTaskLogStore
import org.json.JSONObject
import java.io.File

internal class HostJournal(private val directory: File, private val logs: TaskLogStore, private val exports: File) {
    companion object {
        fun forGms(context: Context) = HostJournal(
            File(context.noBackupFilesDir, Contract.hostDirectory("esim-transfer")).apply { mkdirs() },
            TaskLogStore(File(context.noBackupFilesDir, Contract.hostDirectory("task-logs"))),
            File(context.cacheDir, Contract.hostDirectory("task-log-exports")).apply { mkdirs() })
    }
    private val details = DetailedTaskLogStore(File(directory, "details"))
    fun detail(id: String, kind: String, value: JSONObject) = details.append(id, kind, value)
    fun failure(id: String, error: Throwable) = detail(id, "exception", JSONObject()
        .put("type", error.javaClass.name).put("message", error.message).put("stackTrace", error.stackTraceToString()))
    /** The unlinked read-only descriptor survives until the app closes it; no large Binder payload. */
    fun export(record: TransferRecord): ParcelFileDescriptor {
        val parts = details.snapshot(record.id)
        val file = File.createTempFile("export-", ".txt", exports)
        try {
            file.outputStream().buffered().use { output ->
                fun text(value: String) { output.write(value.toByteArray(Charsets.UTF_8)) }
                text("eSIM 转移详细日志 · 格式 1\n导出时间：${System.currentTimeMillis()}\n宿主模块：${BuildConfig.VERSION_NAME}\n")
                text("身份、凭据、请求与响应按原值保存，未脱敏。时间为 Unix 毫秒，耗时为单调时钟毫秒。\n")
                text("范围：本任务的 Google Play 服务请求与响应、任务快照、网页入口及回调；WebView 内部网络不在宿主 HTTP 采集范围内。\n")
                text("这是导出时点的快照；进行中的请求可能只有开始事件或部分正文。旧版本未留存的数据无法补回。\n")
                text("\n===== 当前任务 =====\n${record.json().toString(2)}\n\n===== 简略时间线 =====\n${taskLog(record).text()}\n")
                if (parts.isEmpty()) text("\n详细事件：本任务没有新版详细采集记录，仅导出现存资料。\n")
                parts.forEach { part ->
                    text("\n===== ${part.file.name} · ${part.length} 字节 =====\n")
                    DetailedTaskLogStore.copy(part, output); text("\n")
                }
                // Legacy tasks only retained the latest response/context; do not invent earlier events.
                for (suffix in listOf("web", "parcel", "response.parcel")) {
                    val source = File(directory, "${record.id}.$suffix")
                    if (source.exists()) {
                        val bytes = AtomicFile(source).readFully()
                        text("\n===== 现存 $suffix${if (suffix.endsWith("parcel")) " · Base64" else ""} =====\n")
                        if (suffix.endsWith("parcel")) text(java.util.Base64.getEncoder().encodeToString(bytes)) else output.write(bytes)
                        text("\n")
                    } else text("\n现存 $suffix：未留存或已清理。\n")
                }
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally { file.delete() }
    }
    fun records(): List<TransferRecord> = directory.listFiles().orEmpty().filter { it.extension == "json" }
        .map { JSONObject(AtomicFile(it).readFully().decodeToString()) }.filter(JSONObject::isGmsRecord)
        .map(TransferRecord::parse).sortedByDescending { it.createdAt }.take(50)
    @Synchronized fun save(record: TransferRecord) {
        val file = File(directory, "${record.id}.json")
        val previous = if (file.exists()) JSONObject(AtomicFile(file).readFully().decodeToString()) else null
        val changed = previous == null || previous.optString("phase") != record.phase.name ||
            previous.optString("message") != record.message || previous.optBoolean("submitted") != record.submitted
        if (changed) {
            detail(record.id, if (previous == null) "task-created" else "task-state", record.json())
            val log = logs.read(record.id) ?: TaskLog(record.id, BuildConfig.VERSION_NAME, complete = previous == null && record.phase == Phase.CONFIRM)
            logs.save(log.append(TaskLogEvents.phase(record)))
        }
        write(file, record.json().toString().toByteArray())
    }
    @Synchronized fun appendLog(id: String, entry: TaskLogEntry) {
        detail(id, "summary-event", entry.json())
        val log = logs.read(id) ?: TaskLog(id, BuildConfig.VERSION_NAME)
        logs.save(log.append(entry))
    }
    fun taskLog(record: TransferRecord): TaskLog {
        logs.read(record.id)?.let { return it }
        return TaskLog(record.id, "旧版本").append(TaskLogEvents.phase(record))
    }
    fun saveProfile(id: String, profile: Parcelable) {
        saveParcelable("$id.parcel", profile)
    }
    fun saveResponse(id: String, response: Parcelable) { saveParcelable("$id.response.parcel", response) }
    fun saveWebSheet(id: String, request: WebSheetRequest) {
        detail(id, "web-sheet", request.json())
        write(File(directory, "$id.web"), request.json().toString().toByteArray())
    }
    fun readWebSheet(id: String) = WebSheetRequest.parse(JSONObject(AtomicFile(File(directory, "$id.web")).readFully().decodeToString()))
    fun deleteWebSheet(id: String) { AtomicFile(File(directory, "$id.web")).delete() }
    @Synchronized fun endLocalTask(record: TransferRecord): TransferRecord {
        val ended = record.endLocally()
        save(ended)
        appendLog(record.id, TaskLogEntry(ended.updatedAt, "已结束本地任务", "用户主动结束本地跟踪，运营商申请状态未更改"))
        deleteWebSheet(record.id)
        deleteCredential(record.id)
        return ended
    }
    private fun saveParcelable(name: String, profile: Parcelable) {
        val parcel = Parcel.obtain()
        try {
            profile.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            detail(name.substringBefore('.'), "gms-parcel", JSONObject().put("file", name).put("class", profile.javaClass.name)
                .put("base64", java.util.Base64.getEncoder().encodeToString(bytes)))
            write(File(directory, name), bytes)
        }
        finally { parcel.recycle() }
    }
    fun readProfile(id: String, gms: GmsReflection): Any {
        val bytes = AtomicFile(File(directory, "$id.parcel")).readFully()
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(bytes, 0, bytes.size); parcel.setDataPosition(0)
            val creator = gms.type("com.google.android.gms.esim.ProfileTransferData").getField("CREATOR").get(null) as Parcelable.Creator<*>
            return requireNotNull(creator.createFromParcel(parcel))
        } finally { parcel.recycle() }
    }
    fun deleteCredential(id: String) {
        File(directory, "$id.parcel").delete(); File(directory, "$id.response.parcel").delete()
    }
    private fun write(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
}
