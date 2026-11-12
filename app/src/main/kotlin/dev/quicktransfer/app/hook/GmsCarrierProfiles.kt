// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.hook

import android.net.Uri
import android.os.Build
import dev.quicktransfer.app.core.*
import io.github.libxposed.api.XposedModule
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Carrier configuration and SIM presentation scoped to module-owned calls. */
internal class GmsCarrierProfiles(private val gms: GmsReflection) {
    private val carrier = ThreadLocal<Int>()
    private val supplement = ThreadLocal<SupplementalCarrier?>()
    val current: SupplementalCarrier? get() = supplement.get()

    fun <T> withCarrier(id: Int, block: () -> T): T {
        if (carrier.get() == id) return block()
        val previous = carrier.get(); val previousSupplement = supplement.get()
        val native = gms.call(gms.static("cejo", "d")!!, "b", id, false)
        carrier.set(id); supplement.set(SupplementalCarriers.find(id).takeIf { native == null })
        try { return block() } finally {
            if (previous == null) carrier.remove() else carrier.set(previous)
            if (previousSupplement == null) supplement.remove() else supplement.set(previousSupplement)
        }
    }

    fun select(native: Any?, id: Int, xos: Boolean): Any? = native ?: current?.takeIf { !xos && it.id == id }?.let(::create)

    fun presentSourceProfile(profile: Any): Any = profile.also {
        // Native ProfileInfo.c: 1 = SIM, 2 = embedded eUICC, 3 = removable eUICC.
        if (carrier.get() != null) gms.set(it, "c", 1)
    }
    fun presentSourceDevice(device: Any): Any = device.also {
        if (carrier.get() != null) gms.set(it, "c", "") // Source EID only; target identity is built separately.
    }

    fun create(config: SupplementalCarrier): Any = gms.create("com.google.android.gms.esim.CarrierInfo",
        config.id, config.name, "", if (config.psim) config.transferType else 0, config.transferType,
        "", 3600, config.server, "", "", 120, 1, 15, 2,
        config.usePost, config.psim, true, if (config.androidClient) "androidTransfer" else "", config.version,
        false, false, "", false, 0, false, false, false, false,
        false, !config.temporaryToken, 120_000L, false, emptyList<Int>(), emptyList<Int>(), false, !config.androidClient)

    fun installHooks(module: XposedModule) {
        module.hook(gms.declared("cehp", "c", android.telephony.SubscriptionInfo::class.java,
            gms.type("com.google.android.gms.esim.CarrierInfo"))).intercept { chain ->
            presentSourceProfile(requireNotNull(chain.proceed()))
        }
        module.hook(gms.declared("cehp", "b", android.telephony.SubscriptionInfo::class.java)).intercept { chain ->
            presentSourceDevice(requireNotNull(chain.proceed()))
        }
        module.hook(gms.declared("cejo", "b", Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)).intercept { chain ->
            select(chain.proceed(), chain.args[0] as Int, chain.args[1] as Boolean)
        }
        module.hook(gms.declared("wwv", "e", String::class.java, String::class.java, String::class.java, String::class.java)).intercept { chain ->
            val config = current
            when {
                config == null || config.androidClient -> chain.proceed()
                config.legacyUserAgent -> "${Build.MODEL} Android${Build.VERSION.RELEASE}"
                else -> "PRD-TS43 ${Build.MODEL} client-Primary-ODSA OS-Android/${Build.VERSION.RELEASE}"
            }
        }
        module.hook(gms.declared("wwv", "h", Uri.Builder::class.java, List::class.java, gms.type("wwn"))).intercept { chain ->
            chain.proceed().also { addProvisioning(chain.args[0] as Uri.Builder) }
        }
        module.hook(gms.declared("wwv", "i", JSONObject::class.java, List::class.java, gms.type("wwn"))).intercept { chain ->
            chain.proceed().also { addProvisioning(chain.args[0] as JSONObject) }
        }
    }

    fun addProvisioning(query: Uri.Builder) { current?.provisioningVersion?.takeIf(String::isNotEmpty)?.let { query.appendQueryParameter("provisioning_version", it) } }
    fun addProvisioning(body: JSONObject) { current?.provisioningVersion?.takeIf(String::isNotEmpty)?.let { body.put("provisioning_version", it) } }
    fun adaptClient(client: Any) { current?.let { gms.set(requireNotNull(gms.field(client, "a")), "d", it.readTimeoutSeconds) } }

    fun adaptRequest(request: Any, operation: Any? = null, sourceIccid: String = "", sourceEid: String = "",
        sourceImei: String = "", target: TargetDevice? = null) {
        val config = current ?: return
        if (config.androidClient) return // Standard Android entitlement request format is already owned by GMS.
        gms.set(request, "a", 1)
        gms.set(request, "h", Build.VERSION.RELEASE)
        if (operation == null) return
        val name = gms.text(operation, "a")
        if (name in setOf("CheckEligibility", "AcquireTemporaryToken")) {
            gms.set(operation, "n", sourceIccid)
            if (name == "CheckEligibility" && target != null) gms.set(operation, "p", target.imei)
        } else if (name == "ManageSubscription" || name == "AcquireConfiguration") {
            if (config.temporaryToken) {
                // The temporary token is spent at the target; old_* always identifies the source.
                gms.set(operation, "n", "")
                if (target != null) {
                    gms.set(operation, "o", target.eid); gms.set(operation, "p", target.imei); gms.set(operation, "s", "")
                }
            } else {
                gms.set(operation, "n", sourceIccid); gms.set(operation, "o", sourceEid)
            }
            if (name == "ManageSubscription") {
                val oldImei = sourceImei.ifBlank { if (config.temporaryToken) gms.text(operation, "w") else gms.text(request, "e") }
                gms.set(operation, "w", oldImei); gms.set(operation, "x", sourceIccid)
            }
        }
        if (config.httpTargetDetails && target != null && name != "AcquireTemporaryToken") {
            gms.set(operation, "q", target.imeis)
            gms.set(operation, "t", target.details.serial)
            gms.set(operation, "u", target.details.model)
        }
    }

    fun webUserData(data: String, target: TargetDevice): String {
        val config = current ?: return data
        if (!config.webTargetDetails) return data
        val details = target.details
        val params = linkedMapOf("IMEI1" to target.imei, "IMEI2" to details.imei2,
            "serial_number" to details.serial, "model_name" to details.model)
        if (config.lguDetails) { params["barcode_number"] = details.barcode; params["activated_id"] = details.activatedId }
        val suffix = params.filterValues(String::isNotEmpty).entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, StandardCharsets.UTF_8)}"
        }
        return data + (if (data.isEmpty() || data.endsWith('&')) "" else "&") + suffix
    }
}
