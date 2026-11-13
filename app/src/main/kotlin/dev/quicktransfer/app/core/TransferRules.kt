// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.core

object IdentityRules {
    fun eidError(eid: String): String? = if (eid.matches(Regex("[0-9]{32}"))) null else "EID 需要 32 位数字"
    fun imeiError(imei: String): String? {
        if (!imei.matches(Regex("[0-9]{15}"))) return "IMEI 需要 15 位数字"
        val sum = imei.reversed().mapIndexed { index, c ->
            val n = c.digitToInt() * if (index % 2 == 1) 2 else 1
            n / 10 + n % 10
        }.sum()
        return if (sum % 10 == 0) null else "IMEI 校验位不正确，请核对后重试"
    }
    fun valid(eid: String, imei: String) = eidError(eid) == null && imeiError(imei) == null
}

/** A source confirmation starts one GMS flow; native retries stay inside it. Polling cannot start another. */
class SubmissionGate(alreadySubmitted: Boolean = false) {
    private var authenticated = false
    private var submitted = alreadySubmitted
    @Synchronized fun authenticate() { authenticated = true }
    @Synchronized fun take(): Boolean {
        if (!authenticated || submitted) return false
        submitted = true
        return true
    }
}

object ActivationCodes {
    fun standard(raw: String) = if (raw.startsWith("1$")) "LPA:$raw" else raw
    fun server(raw: String): String = standard(raw).removePrefix("LPA:").split('$').let { if (it.size >= 3 && it[0] == "1") it[1] else "" }
}
