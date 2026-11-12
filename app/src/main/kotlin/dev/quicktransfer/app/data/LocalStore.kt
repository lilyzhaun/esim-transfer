// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.data

import android.content.Context
import dev.quicktransfer.app.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("local_library", Context.MODE_PRIVATE)
    private val targetsMutable = MutableStateFlow(readTargets())
    private val deletedRecords = prefs.getStringSet("deleted_records", emptySet()).orEmpty().toMutableSet()
    private val recordsMutable = MutableStateFlow(readRecords())
    val targets = targetsMutable.asStateFlow()
    val records = recordsMutable.asStateFlow()
    private fun readTargets() = JSONArray(prefs.getString("targets", "[]")).objects().map(TargetDevice::parse)
    private fun readRecords() = JSONArray(prefs.getString("records", "[]")).objects().map(TransferRecord::parse)
    fun save(device: TargetDevice) {
        require(IdentityRules.valid(device.eid, device.imei))
        val existing = targetsMutable.value.firstOrNull { it.eid == device.eid }
        val updated = device.copy(id = existing?.id ?: device.id, name = device.name.trim().take(80).ifBlank { "Target device" })
        targetsMutable.value = (targetsMutable.value.filterNot { it.id == updated.id || it.id == device.id } + updated).takeLast(100)
        prefs.edit().putString("targets", JSONArray(targetsMutable.value.map { it.json() }).toString()).apply()
    }
    fun delete(id: String) {
        targetsMutable.value = targetsMutable.value.filterNot { it.id == id }
        prefs.edit().putString("targets", JSONArray(targetsMutable.value.map { it.json() }).toString()).apply()
    }
    @Synchronized fun deleteRecord(id: String) {
        val record = recordsMutable.value.firstOrNull { it.id == id } ?: return
        check(record.canDeleteFromHistory) { "Complete or end the task before deleting its record" }
        deletedRecords += id
        recordsMutable.value = recordsMutable.value.filterNot { it.id == id }
        prefs.edit().putStringSet("deleted_records", deletedRecords.toSet())
            .putString("records", JSONArray(recordsMutable.value.map { it.json() }).toString()).apply()
    }
    @Synchronized fun isRecordDeleted(id: String) = id in deletedRecords
    @Synchronized fun merge(records: List<TransferRecord>) {
        if (records.isEmpty()) return
        val merged = recordsMutable.value.associateBy { it.id }.toMutableMap()
        records.forEach { incoming ->
            if (incoming.id in deletedRecords) return@forEach
            val previous = merged[incoming.id]
            if (previous?.phase == Phase.ISSUED && incoming.phase != Phase.ISSUED) return@forEach
            if (previous == null || incoming.updatedAt >= previous.updatedAt) merged[incoming.id] = incoming
        }
        recordsMutable.value = merged.values.sortedByDescending { it.createdAt }.take(50)
        prefs.edit().putString("records", JSONArray(recordsMutable.value.map { it.json() }).toString()).apply()
    }
}
