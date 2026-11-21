// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TaskLogEntry(val time: Long, val title: String, val detail: String) {
    fun json() = JSONObject().put("time", time).put("title", title).put("detail", detail)
    companion object {
        fun parse(j: JSONObject) = TaskLogEntry(j.getLong("time"), j.getString("title"), j.getString("detail"))
    }
}

data class TaskLog(val taskId: String, val appVersion: String, val entries: List<TaskLogEntry> = emptyList(),
    val complete: Boolean = false, val dropped: Int = 0, val revision: Long = 0) {
    fun append(entry: TaskLogEntry): TaskLog {
        val all = entries + entry
        return copy(entries = all.takeLast(100), dropped = dropped + (all.size - 100).coerceAtLeast(0),
            revision = maxOf(revision + 1, entry.time))
    }
    fun json() = JSONObject().put("taskId", taskId).put("appVersion", appVersion).put("complete", complete)
        .put("dropped", dropped).put("revision", revision).put("entries", JSONArray(entries.map { it.json() }))
    fun text(): String = buildString {
        appendLine("eSIM transfer · 任务日志")
        appendLine("任务：$taskId"); appendLine("记录版本：$appVersion")
        appendLine("不含完整设备身份、鉴权凭据或激活码")
        if (!complete) appendLine("旧任务仅有保留记录，早期过程未记录")
        if (dropped > 0) appendLine("已省略较早的 $dropped 条日志")
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        entries.forEach { appendLine(); appendLine("${format.format(Date(it.time))}  ${it.title}"); appendLine(it.detail) }
    }
    companion object {
        fun parse(j: JSONObject) = TaskLog(j.getString("taskId"), j.getString("appVersion"),
            j.optJSONArray("entries").objects().map(TaskLogEntry::parse), j.optBoolean("complete"), j.optInt("dropped"), j.optLong("revision"))
    }
}

/** Export summaries are allowlisted, never raw HTTP bodies, exception messages or credential values. */
object TaskLogEvents {
    fun phase(record: TransferRecord) = TaskLogEntry(record.updatedAt, record.phase.label,
        "${record.source.displayTitle} · ${record.source.slotTitle} · SIM 卡 · GMS\n" +
            if (record.submitted) "已进入转移申请阶段" else "尚未提交转移申请")
    fun failure(error: Throwable, time: Long = System.currentTimeMillis()) = TaskLogEntry(time, "操作未完成", "异常类型：${error.javaClass.simpleName.take(80)}")
    fun native(operation: String, values: Map<String, String>) = TaskLogEntry(System.currentTimeMillis(), "GMS · 运营商响应",
        (listOf(operation.takeIf { it in setOf("CheckEligibility", "AcquireTemporaryToken", "ManageSubscription", "AcquireConfiguration") } ?: "其他请求") +
            listOf("OperationResult", "SubscriptionResult", "ServiceStatus", "PollingInterval").mapNotNull { key ->
                values[key]?.takeIf { it.matches(Regex("[0-9]{1,6}")) }?.let { "$key=$it" }
            }).joinToString("\n"))
}
