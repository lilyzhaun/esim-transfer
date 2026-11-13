// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.hook

/** Only the final native service response decides success; a fetcher's intermediate result cannot. */
internal data class NativeCredential(val code: String, val addresses: List<String>, val issued: Boolean,
    val needsInput: Boolean, val delayed: Boolean, val error: String) {
    companion object {
        fun read(gms: GmsReflection, result: Any, raw: Any?): NativeCredential {
            val finalCode = gms.text(result, "a")
            val rawAddresses = if (raw == null || finalCode.isBlank()) emptyList()
                else (gms.field(raw, "b") as List<*>).map { it.toString() }
            val addressOnly = raw != null && gms.text(raw, "a").isBlank() && rawAddresses.any {
                finalCode == "1\$${it.removePrefix("https://").removePrefix("http://")}\$"
            }
            val addresses = if (addressOnly || raw != null && finalCode == gms.text(raw, "a")) rawAddresses else emptyList()
            val web = gms.field(result, "e")
            val message = gms.field(result, "f")
            val needsInput = web != null && gms.text(web, "a").isNotBlank() || message != null && gms.text(message, "a").isNotBlank()
            return NativeCredential(if (addressOnly) "" else finalCode, addresses, finalCode.isNotBlank(), needsInput,
                gms.field(result, "d") == true, gms.text(result, "c").ifBlank { "Google Play 服务未返回下载信息（${gms.number(result, "g")}）" })
        }
    }
}
