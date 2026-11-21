// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.core

import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.UUID

/** Private task data: never included in HostSnapshot or logs. GMS has already decoded userData. */
data class WebSheetRequest(val id: String, val url: String, val userData: String, val contentType: String) {
    val origin: String get() = httpsOrigin(url)
    val host: String get() = URI(url).host
    val mimeType: String get() = when (contentType.lowercase(Locale.ROOT).substringBefore(';').trim()) {
        "" -> ""
        "json", "application/json" -> "application/json"
        "xml", "application/xml", "text/xml" -> "application/xml"
        "application/x-www-form-urlencoded" -> "application/x-www-form-urlencoded"
        else -> throw IllegalArgumentException("运营商网页使用了暂不支持的内容类型")
    }
    val navigationUrl: String get() {
        if (mimeType.isNotEmpty() || userData.isEmpty()) return url
        val base = url.substringBefore('#')
        return base + (if ('?' in base) "&" else "?") + userData
    }
    fun json() = JSONObject().put("id", id).put("url", url).put("userData", userData).put("contentType", contentType).put("bridge", "odsa")
    companion object {
        fun create(url: String, data: String, contentType: String): WebSheetRequest {
            require(url.length <= 16_384 && data.length <= 131_072) { "运营商网页参数过长" }
            val origin = httpsOrigin(url)
            val uri = URI(url)
            val normalized = origin + uri.rawPath.orEmpty().ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
            return WebSheetRequest(UUID.randomUUID().toString(), normalized, data, contentType).also { it.mimeType }
        }
        fun parse(j: JSONObject): WebSheetRequest {
            require(j.optString("bridge", "odsa") == "odsa") { "运营商网页接口不受支持" }
            return WebSheetRequest(j.getString("id"), j.getString("url"), j.getString("userData"), j.getString("contentType"))
        }
    }
}

fun httpsOrigin(url: String): String {
    val uri = try { URI(url) } catch (_: java.net.URISyntaxException) { throw IllegalArgumentException("运营商网页地址格式无效") }
    require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.port in -1..65535) {
        "运营商网页地址必须是有效的 HTTPS 地址"
    }
    return "https://${uri.host.lowercase(Locale.ROOT)}" + if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
}

enum class WebAction { ACTIVATION, ADDRESS, FINISH, DISMISS, DELETE_TOKEN, CHECK_STATUS, SELECTION, DELETE_PROFILE }

/** TS.43 §6.7. The bridge accepts structured callbacks, never executable callback strings. */
data class WebSheetEvent(val action: WebAction, val value: String = "", val iccid: String = "", val imei: String = "") {
    companion object {
        fun parse(raw: String): WebSheetEvent {
            require(raw.length <= 16_384) { "网页回调数据过长" }
            val j = JSONObject(raw)
            val args = j.optJSONArray("args").strings()
            fun arg(i: Int) = args.getOrElse(i) { "" }.trim()
            return when (j.getString("event")) {
                "profileReadyWithActivationCode" -> {
                    val code = ActivationCodes.standard(arg(0))
                    val parts = code.removePrefix("LPA:").split('$')
                    require(code.length <= 4096 && parts.size >= 3 && parts[0] == "1" && parts[1].isNotBlank() && parts[2].isNotBlank()) { "网页返回的激活码不完整" }
                    WebSheetEvent(WebAction.ACTIVATION, code)
                }
                "profileReadyWithDefaultSmdp" -> {
                    require(arg(0).matches(Regex("[A-Za-z0-9.-]+")) && arg(0).contains('.')) { "网页返回的下载服务器地址无效" }
                    WebSheetEvent(WebAction.ADDRESS, arg(0), arg(1))
                }
                "finishFlow" -> {
                    val operation = arg(0).ifEmpty { "AcquireConfiguration" }
                    require(operation in setOf("AcquireConfiguration", "ManageSubscription")) { "运营商请求了暂不支持的后续操作" }
                    WebSheetEvent(WebAction.FINISH, operation)
                }
                "dismissFlow" -> WebSheetEvent(WebAction.DISMISS)
                "deleteToken" -> WebSheetEvent(WebAction.DELETE_TOKEN)
                "checkProfileServiceStatus" -> WebSheetEvent(WebAction.CHECK_STATUS)
                "SelectionCompleted" -> WebSheetEvent(WebAction.SELECTION, iccid = arg(0), imei = arg(1))
                "deleteProfileInUse" -> WebSheetEvent(WebAction.DELETE_PROFILE, iccid = arg(0))
                else -> throw IllegalArgumentException("无法识别运营商网页的回调")
            }
        }
    }
}

/** Shared by UI and host: callbacks must refer to the saved page for this exact task. */
fun TransferRecord.acceptsWebSheet(id: String): Boolean = webSheetId.isNotEmpty() && webSheetId == id &&
    submitted && phase in setOf(Phase.NEEDS_INPUT, Phase.ISSUED, Phase.INTERRUPTED, Phase.UNCERTAIN)

/** A portal may emit its code first and its default-SM-DP+ data afterwards. Keep both. */
fun TransferRecord.withWebDownloadAddress(address: String, iccid: String): TransferRecord = copy(
    phase = Phase.ISSUED,
    message = if (activationCode.isEmpty()) "已获取服务器地址，尚无完整激活码" else "已获取下载信息，请在目标设备下载安装",
    smdpAddresses = (smdpAddresses + address).distinct(), downloadIccid = iccid.ifBlank { downloadIccid },
    canResume = false, nextPollAt = 0,
)
