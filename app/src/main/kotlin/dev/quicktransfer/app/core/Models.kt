// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import dev.quicktransfer.app.BuildConfig

object Contract {
    const val APP = BuildConfig.APPLICATION_ID
    const val GMS = "com.google.android.gms"
    const val PROCESS = "com.google.android.gms.ui"
    const val AUTHORITY = "$APP.host"
    const val REGISTRATION_PROTOCOL = 2
    const val PREFS = "host_connection"
    const val DISCOVERY = "discovery"
    const val APP_UID = "module_uid"
    const val TASK_EXTRA = "$APP.task"
    const val DISCOVER_EXTRA = "$APP.discover"
    const val SOURCE_ACTIVITY = "$GMS.esim.ui.EsimTransferSourceActivity"
    const val SOURCE_IMPL = "$GMS.esim.ui.EsimTransferSourceChimeraActivity"
    const val MAIN_ACTIVITY = "dev.quicktransfer.app.MainActivity"
    const val VERSION = 264330035L

    /** The host writes inside Google Play services, where application sandboxes do not separate modules. */
    fun hostDirectory(name: String, appId: String = APP): String =
        if (appId == "dev.quicktransfer.app") name else "$name-$appId"
}

data class TargetDetails(val model: String = "", val serial: String = "", val imei2: String = "", val barcode: String = "", val activatedId: String = "",
    ) {
    fun fields() = linkedMapOf("model" to model, "serial" to serial, "imei2" to imei2, "barcode" to barcode, "activatedId" to activatedId)
    fun json() = JSONObject(fields() as Map<*, *>)
    fun error(): String? = when {
        fields().values.any { it.length > 120 || it.any(Char::isISOControl) } -> "设备补充信息过长或含有无效字符"
        imei2.isNotEmpty() && IdentityRules.imeiError(imei2) != null -> "第二 IMEI 的格式或校验位不正确"
        else -> null
    }
    companion object { fun parse(j: JSONObject?) = if (j == null) TargetDetails() else TargetDetails(
        j.optString("model"), j.optString("serial"), j.optString("imei2"), j.optString("barcode"), j.optString("activatedId"))
    }
}

data class TargetDevice(val id: String = UUID.randomUUID().toString(), val name: String, val eid: String, val imei: String,
    val details: TargetDetails = TargetDetails()) {
    val imeis: List<String> get() = listOf(imei, details.imei2).filter(String::isNotBlank).distinct()
    fun json() = JSONObject().put("id", id).put("name", name).put("eid", eid).put("imei", imei).put("details", details.json())
    companion object { fun parse(j: JSONObject) = TargetDevice(j.getString("id"), j.getString("name"), j.getString("eid"), j.getString("imei"), TargetDetails.parse(j.optJSONObject("details"))) }
}

data class SourceLine(val subId: Int, val slot: Int, val carrierId: Int, val name: String, val embedded: Boolean,
    val configured: Boolean, val supported: Boolean, val transferType: Int, val reason: String = "", val targetFields: List<String> = emptyList(),
    val backend: String = "gms", val country: String = "") {
    val slotTitle: String get() = if (slot >= 0) "SIM ${slot + 1}" else "源 SIM"
    val displayTitle: String get() = countryFlag(country) + name.trim().ifBlank { "未识别运营商" }
    val displaySummary: String get() = "$slotTitle · SIM 卡" + if (supported) "" else
        "\n${reason.ifBlank { "请刷新 Google Play 服务的线路状态" }}"
    fun json() = JSONObject().put("subId", subId).put("slot", slot).put("carrierId", carrierId).put("name", name)
        .put("embedded", embedded).put("configured", configured).put("supported", supported).put("transferType", transferType).put("reason", reason)
        .put("targetFields", JSONArray(targetFields)).put("backend", backend).put("country", country)
    companion object { fun parse(j: JSONObject) = SourceLine(j.getInt("subId"), j.getInt("slot"), j.getInt("carrierId"), j.getString("name"),
        j.getBoolean("embedded"), j.getBoolean("configured"), j.getBoolean("supported"), j.getInt("transferType"), j.optString("reason"), j.optJSONArray("targetFields").strings(), j.optString("backend", "gms"), j.optString("country")) }
}

enum class Phase(val label: String, val running: Boolean = false) {
    CONFIRM("等待源端验证", true), AUTHENTICATING("验证源线路", true), ELIGIBILITY("检查线路资格", true),
    TOKEN("获取转移凭据", true), SUBMITTING("提交转移请求", true), WAITING("等待运营商发码", true),
    ISSUED("已获取激活码"), NEEDS_INPUT("运营商需要进一步操作"), FAILED("未完成"), CANCELLED("已取消"),
    INTERRUPTED("查询已中断"), UNCERTAIN("结果待确认")
}

data class TransferRecord(
    val id: String, val createdAt: Long, val updatedAt: Long, val source: SourceLine, val target: TargetDevice,
    val phase: Phase = Phase.CONFIRM, val message: String = "请在 GMS 界面验证源设备身份",
    val submitted: Boolean = false, val pendingAccepted: Boolean = false, val canResume: Boolean = false,
    val activationCode: String = "", val smdpAddresses: List<String> = emptyList(), val nextPollAt: Long = 0,
    val webSheetId: String = "", val webSheetHost: String = "",
    val downloadIccid: String = "",
    val executionMode: String = ExecutionMode.GMS.name,
) {
    val blocksNewTransfer: Boolean get() = phase.running || submitted && phase in setOf(Phase.NEEDS_INPUT, Phase.INTERRUPTED, Phase.UNCERTAIN)
    val statusTitle: String get() = when {
        phase == Phase.ISSUED && activationCode.isBlank() -> "已获取服务器地址"
        else -> phase.label
    }
    fun json(): JSONObject = JSONObject().put("id", id).put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("source", source.json()).put("target", target.json()).put("phase", phase.name).put("message", message)
        .put("submitted", submitted).put("pendingAccepted", pendingAccepted).put("canResume", canResume)
        .put("activationCode", activationCode).put("smdpAddresses", JSONArray(smdpAddresses)).put("nextPollAt", nextPollAt)
        .put("webSheetId", webSheetId).put("webSheetHost", webSheetHost)
        .put("downloadIccid", downloadIccid)
        .put("executionMode", executionMode)
    companion object {
        fun parse(j: JSONObject) = TransferRecord(j.getString("id"), j.getLong("createdAt"), j.getLong("updatedAt"),
            SourceLine.parse(j.getJSONObject("source")), TargetDevice.parse(j.getJSONObject("target")), Phase.valueOf(j.getString("phase")),
            j.optString("message"), j.optBoolean("submitted"), j.optBoolean("pendingAccepted"), j.optBoolean("canResume"),
            j.optString("activationCode"), j.optJSONArray("smdpAddresses").strings(), j.optLong("nextPollAt"),
            j.optString("webSheetId"), j.optString("webSheetHost"), j.optString("downloadIccid"),
            j.optString("executionMode", ExecutionMode.GMS.name))
    }
}

data class HostSnapshot(val connected: Boolean = false, val version: String = "", val versionCode: Long = 0,
    val api: Int = 0, val supported: Boolean = false, val sources: List<SourceLine> = emptyList(),
    val records: List<TransferRecord> = emptyList(), val notice: String = "", val catalog: CarrierCatalog = CarrierCatalog(),
    val supportsLocalTaskEnd: Boolean = false, val supportsDetailedLogs: Boolean = false,
    val executionMode: String = ExecutionMode.GMS.name) {
    fun json() = JSONObject().put("version", version).put("versionCode", versionCode).put("api", api).put("supported", supported)
        .put("sources", JSONArray(sources.map { it.json() })).put("records", JSONArray(records.map { it.json() })).put("notice", notice).put("catalog", catalog.json())
        .put("supportsLocalTaskEnd", supportsLocalTaskEnd)
        .put("supportsDetailedLogs", supportsDetailedLogs)
        .put("executionMode", executionMode)
    companion object {
        fun parse(raw: String): HostSnapshot { val j = JSONObject(raw); return HostSnapshot(true, j.optString("version"), j.optLong("versionCode"),
            j.optInt("api"), j.optBoolean("supported"), j.optJSONArray("sources").objects().map(SourceLine::parse),
            j.optJSONArray("records").objects().filter(JSONObject::isGmsRecord).map(TransferRecord::parse), j.optString("notice"), CarrierCatalog.parse(j.optJSONObject("catalog")),
            j.optBoolean("supportsLocalTaskEnd"), j.optBoolean("supportsDetailedLogs"), j.optString("executionMode", ExecutionMode.GMS.name)) }
    }
}

fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }

/** Older records without routing fields used the local SIM and Google Play services. */
internal fun JSONObject.isGmsRecord(): Boolean {
    val source = optJSONObject("source") ?: return false
    return source.optString("backend", "gms") == "gms" && source.optString("origin", "local") == "local" &&
        optString("executionMode", ExecutionMode.GMS.name) == ExecutionMode.GMS.name
}
