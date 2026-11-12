// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.hook

import android.content.Context
import android.telephony.SubscriptionInfo
import dev.quicktransfer.app.core.SourceLine
import dev.quicktransfer.app.core.TargetDevice

/** Version-specific bridge; eligibility and transfer dispatch belong to GMS. */
internal class GmsTransferEngine(private val context: Context, private val gms: GmsReflection,
    private val profiles: GmsCarrierProfiles = GmsCarrierProfiles(gms)) {
    private val engine by lazy { requireNotNull(gms.static("cehp", "g", context)).also { gms.call(it, "q") } }

    fun source(sub: SubscriptionInfo): SourceLine = profiles.withCarrier(sub.carrierId) {
        val carrier = gms.call(requireNotNull(gms.static("cejo", "d")), "b", sub.carrierId, false)
        // Module-owned transfers always use GMS's physical-SIM eligibility branch.
        val metadata = requireNotNull(gms.call(engine, "p", sub.simSlotIndex, sub.subscriptionId,
            sub.carrierId.toLong(), false, carrier, false))
        SourceLine(sub.subscriptionId, sub.simSlotIndex, sub.carrierId, carrier?.let { gms.text(it, "b") }?.takeIf(String::isNotBlank) ?: sub.carrierName.toString(),
            false, carrier != null, gms.number(metadata, "c") == 0L,
            gms.number(metadata, "a").toInt(), failureReason(metadata), profiles.current?.targetFields.orEmpty(), country = sub.countryIso.orEmpty())
    }

    fun profile(sub: SubscriptionInfo, target: TargetDevice): Any = profiles.withCarrier(sub.carrierId) {
        val sourceDevice = requireNotNull(gms.call(engine, "b", sub))
        // Optional target details belong only to the carrier-specific HTTP/Web adapters.
        val device = gms.create("com.google.android.gms.esim.DeviceInfo",
            gms.text(sourceDevice, "a"), gms.text(sourceDevice, "b"), gms.text(sourceDevice, "c"),
            target.imei, target.eid, gms.number(sourceDevice, "f").toInt(), gms.number(sourceDevice, "g").toInt(),
            gms.call(sourceDevice, "a"), "", "", arrayListOf<String>(), "", "", 1, target.name)
        val sourceData = requireNotNull(gms.call(engine, "d", sub, "", gms.static("ifec", "G", arrayListOf<Int>()), device, false))
        val metadata = requireNotNull(gms.field(sourceData, "c"))
        check(gms.number(metadata, "c") == 0L) { failureReason(metadata) }
        // Keep GMS's selected type and credentials, including paths with no temporary token.
        gms.create("com.google.android.gms.esim.ProfileTransferData", device,
            gms.field(sourceData, "b"), metadata, gms.text(sourceData, "d"), 0L)
    }

    fun resume(profile: Any): Any {
        val fetcher = requireNotNull(gms.static("cegv", "a", context, profile, null, null, ""))
        return requireNotNull(gms.call(fetcher, "c", gms.number(requireNotNull(gms.field(profile, "c")), "a").toInt()))
    }

    fun bindTarget(fetcher: Any, profile: Any) {
        val device = requireNotNull(gms.field(profile, "a"))
        val type = gms.number(requireNotNull(gms.field(profile, "c")), "a").toInt()
        val imei = gms.text(device, "d")
        val eid = gms.text(device, "e")
        gms.set(fetcher, "q", imei)
        gms.set(fetcher, "w", eid)
        // GMS's own role predicates: only target-executed flows replace terminal_*.
        // Source-executed TS.43 flows retain their SIM's terminal identity and AKA.
        if (gms.static("cehf", "l", type) == true || gms.static("cehf", "j", type) == true) {
            gms.set(fetcher, "u", imei)
            gms.set(fetcher, "v", eid)
        }
    }

    fun checkTarget(request: Any, operation: Any, target: TargetDevice) {
        val targetEid = gms.text(operation, "s")
        val targetId = gms.text(operation, "p")
        val targetIds = gms.field(operation, "q") as List<*>
        val explicitTarget = targetEid.isNotEmpty() || targetId.isNotEmpty() || targetIds.isNotEmpty()
        val matches = if (explicitTarget) {
            val ids = (targetIds.map { it.toString() } + targetId).flatMap { it.split(',') }.filter { it.isNotBlank() }
            val correctEid = targetEid == target.eid || targetEid.isEmpty() &&
                gms.text(request, "e") == target.imei && gms.text(operation, "o") == target.eid
            correctEid && target.imei in ids && ids.all { it in target.imeis }
        } else gms.text(operation, "o") == target.eid && gms.text(request, "e") == target.imei
        check(matches) { "The target identity does not match the request" }
    }

    private fun failureReason(metadata: Any): String {
        val failure = gms.number(metadata, "c").toInt()
        if (failure == 0) return ""
        return gms.text(metadata, "m").ifBlank {
            when (failure) {
                1 -> "Enable this SIM line first"
                2 -> "Set a screen lock first"
                3 -> "Transfer credentials expired. Check again"
                4 -> "No transfer configuration is available for this line"
                5 -> "Google Play services could not authenticate this line"
                6 -> "Google Play services does not offer a transfer method for this line"
                7 -> "Connect to Wi-Fi and refresh"
                8 -> "Use the device owner profile"
                11 -> "Google Play services reported incompatible source and target device types"
                12, 13 -> "The device does not meet the transfer requirements"
                14 -> "Device management policy prevents transferring this line"
                else -> "Google Play services eligibility check failed($failure)"
            }
        }
    }
}
