// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import dev.quicktransfer.app.core.WebSheetRequest
import dev.quicktransfer.app.core.httpsOrigin
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

/** WebView.postUrl only sends form data. Intercept the initial navigation for TS.43 JSON/XML POSTs. */
internal class WebSheetPost(private val sheet: WebSheetRequest, private val userAgent: String,
    private val navigate: (String) -> Unit, private val error: (String) -> Unit,
    private val openConnection: (String) -> HttpURLConnection = { URI(it).toURL().openConnection() as HttpURLConnection },
    initialPostAllowed: Boolean = true) {
    private val pending = AtomicReference(sheet.url.takeIf { initialPostAllowed })
    private var redirects = 0
    fun intercept(url: String): WebResourceResponse? {
        val expected = pending.get()
        if (sheet.mimeType.isEmpty() || expected != url || !pending.compareAndSet(expected, null)) return null
        var connection: HttpURLConnection? = null
        try {
            val conn = openConnection(url)
            connection = conn
            conn.connectTimeout = 20_000; conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false; conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", sheet.mimeType + "; charset=UTF-8")
            conn.setRequestProperty("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
            conn.outputStream.use { it.write(sheet.userData.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            conn.headerFields.entries.filter { it.key.equals("Set-Cookie", true) }.flatMap { it.value }.forEach {
                CookieManager.getInstance().setCookie(url, it)
            }
            CookieManager.getInstance().flush()
            if (status in 300..399) {
                require(++redirects <= 5) { "运营商网页重定向次数过多，请稍后重试" }
                val location = conn.getHeaderField("Location") ?: throw IllegalArgumentException("网页重定向缺少地址")
                val next = URI(url).resolve(location).toString()
                httpsOrigin(next)
                if (status == 307 || status == 308) {
                    require(httpsOrigin(next) == sheet.origin) { "网页要求向其他网站重新发送验证数据" }
                    pending.set(next)
                }
                conn.disconnect()
                navigate(next)
                return emptyResponse()
            }
            val content = conn.contentType.orEmpty()
            val mime = content.substringBefore(';').ifBlank { "text/html" }
            val encoding = Regex("charset=([^; ]+)", RegexOption.IGNORE_CASE).find(content)?.groupValues?.get(1) ?: "UTF-8"
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val ownedConnection = conn
            val body = object : FilterInputStream(stream ?: ByteArrayInputStream(byteArrayOf())) {
                override fun close() { try { super.close() } finally { ownedConnection.disconnect() } }
            }
            val headers = conn.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(", ") }
            return WebResourceResponse(mime, encoding, status, conn.responseMessage?.ifBlank { "Response" } ?: "Response", headers, body)
        } catch (failure: Exception) {
            connection?.disconnect()
            error(if (failure is IllegalArgumentException) failure.message.orEmpty() else "验证网页加载失败，请检查网络后重试")
            return emptyResponse()
        }
    }
    private fun emptyResponse() = WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(byteArrayOf()))
}
