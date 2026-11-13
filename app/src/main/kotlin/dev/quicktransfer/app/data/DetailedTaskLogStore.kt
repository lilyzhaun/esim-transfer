// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Export-only, append-only evidence. Never passed to the summary log or a Binder String. */
class DetailedTaskLogStore(private val root: File) {
    private fun directory(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "任务编号无效" }
        return File(root, id)
    }
    @Synchronized fun append(id: String, kind: String, value: JSONObject) {
        val entry = JSONObject().put("time", System.currentTimeMillis()).put("kind", kind)
            .put("thread", Thread.currentThread().name).put("data", value)
        File(directory(id).apply { mkdirs() }, "events.jsonl").appendText(entry.toString() + "\n", Charsets.UTF_8)
    }
    /** Consume the entire decoded HTTP body to disk, keeping only the protocol parser's limit in RAM. */
    fun captureBody(id: String, metadata: JSONObject, input: InputStream): ByteArray {
        val file = File(directory(id).apply { mkdirs() }, "http-${UUID.randomUUID()}.body")
        metadata.put("responseBodyFile", file.name).put("responseBodyEncoding", "HTTP entity after content decoding")
        append(id, "http-body-start", JSONObject().put("requestId", metadata.getString("traceId")).put("file", file.name))
        val preview = ByteArrayOutputStream()
        var size = 0L
        var complete = false
        try {
            file.outputStream().buffered().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    if (preview.size() < 262145) preview.write(buffer, 0, minOf(count, 262145 - preview.size()))
                    size += count
                }
            }
            complete = true
            return preview.toByteArray()
        } finally {
            metadata.put("responseBodyBytes", size).put("responseBodyComplete", complete)
        }
    }
    data class Part(val file: File, val length: Long)
    @Synchronized fun snapshot(id: String): List<Part> = directory(id).listFiles().orEmpty()
        .filter { it.isFile }.sortedBy { if (it.name == "events.jsonl") "" else it.name }.map { Part(it, it.length()) }

    companion object {
        /** Fixed-length snapshot: an active task may append while an export is being copied. */
        fun copy(part: Part, output: OutputStream) {
            part.file.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                var remaining = part.length
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count < 0) throw java.io.EOFException("日志文件在导出期间发生变化")
                    output.write(buffer, 0, count); remaining -= count
                }
            }
        }
    }
}
