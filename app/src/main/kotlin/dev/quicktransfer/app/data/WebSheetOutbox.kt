// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.data

import android.content.Context
import dev.quicktransfer.app.core.WebSheetEvent
import dev.quicktransfer.app.core.objects
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A carrier can finish while the GMS binder is disconnected. Retain that callback before IPC. */
internal class WebSheetOutbox(context: Context) {
    private val prefs = context.getSharedPreferences("websheet_results", Context.MODE_PRIVATE)
    data class Entry(val id: String, val task: String, val sheet: String, val event: String) {
        fun json() = JSONObject().put("id", id).put("task", task).put("sheet", sheet).put("event", event)
    }
    fun pending(task: String): List<Entry> = read().filter { it.task == task }
    @Synchronized fun add(task: String, sheet: String, event: String) {
        WebSheetEvent.parse(event)
        val entries = read()
        if (entries.any { it.task == task && it.sheet == sheet && it.event == event }) return
        check(entries.size < 32) { "待保存的验证结果过多，请先连接服务完成同步" }
        write(entries + Entry(UUID.randomUUID().toString(), task, sheet, event))
    }
    @Synchronized fun remove(id: String) = write(read().filterNot { it.id == id })
    @Synchronized fun acknowledge(id: String, reply: JSONObject) {
        if (!reply.optBoolean("rejected")) { remove(id); return }
        val entries = read()
        val rejected = entries.singleOrNull { it.id == id } ?: return
        val archive = JSONArray(prefs.getString("rejected", "[]")).objects().takeLast(31) +
            rejected.json().put("reason", reply.optString("message"))
        check(prefs.edit().putString("pending", JSONArray(entries.filterNot { it.id == id }.map(Entry::json)).toString())
            .putString("rejected", JSONArray(archive).toString()).commit()) { "无法单独保存被拒绝的网页回调，请检查存储空间" }
    }
    private fun read() = JSONArray(prefs.getString("pending", "[]")).objects().map {
        Entry(it.getString("id"), it.getString("task"), it.getString("sheet"), it.getString("event"))
    }
    private fun write(entries: List<Entry>) {
        check(prefs.edit().putString("pending", JSONArray(entries.map(Entry::json)).toString()).commit()) { "无法保存网页验证结果，请检查存储空间" }
    }
}
