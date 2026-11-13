// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.data

import android.content.Context
import android.util.AtomicFile
import dev.quicktransfer.app.core.TaskLog
import org.json.JSONObject
import java.io.File

class TaskLogStore(private val directory: File) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "task-logs"))
    private fun file(id: String): AtomicFile {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "任务编号无效" }
        return AtomicFile(File(directory, "$id.json"))
    }
    @Synchronized fun read(id: String): TaskLog? {
        val file = file(id)
        return if (file.baseFile.exists()) TaskLog.parse(JSONObject(file.readFully().decodeToString())) else null
    }
    @Synchronized fun delete(id: String) { file(id).delete() }
    @Synchronized fun save(log: TaskLog): TaskLog {
        directory.mkdirs()
        val current = read(log.taskId)
        if (current != null && current.revision > log.revision) return current
        val file = file(log.taskId)
        val output = file.startWrite()
        try { output.write(log.json().toString().toByteArray()); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
        return log
    }
}
