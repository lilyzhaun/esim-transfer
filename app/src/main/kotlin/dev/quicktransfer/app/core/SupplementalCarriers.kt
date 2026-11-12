// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.core

/** Factual subset of SamsungOdaService 5.0.00.28 production 2.0.1_26.250807.
 * See docs/research/flip2-264330035/SAMSUNG-CARRIER-SUPPLEMENTS.md for provenance.
 * Selection uses Android's resolved carrierId, not a guess from a shared MCC/MNC.
 */
data class SupplementalCarrier(
    val id: Int, val name: String, val country: String, val server: String,
    val version: String = "8.0", val temporaryToken: Boolean = false, val psim: Boolean = true,
    val legacyUserAgent: Boolean = false, val provisioningVersion: String = "",
    val usePost: Boolean = false, val readTimeoutSeconds: Int = 30,
    val httpTargetDetails: Boolean = false, val webTargetDetails: Boolean = false, val lguDetails: Boolean = false,
    val androidClient: Boolean = false, val equivalentId: Int? = null,
) {
    val transferType: Int get() = if (temporaryToken) 2 else 6
    val targetFields: List<String> get() = if (!httpTargetDetails && !webTargetDetails && !lguDetails) emptyList()
        else listOf("model", "serial", "imei2") + if (lguDetails) listOf("barcode", "activatedId") else emptyList()
    fun catalog() = SupportedCarrier(id, name, listOf(country), psim, true)
}

object SupplementalCarriers {
    val entries = listOf(
        SupplementalCarrier(1098, "AIS", "TH", "https://ss-es.ais.co.th", legacyUserAgent = true),
        SupplementalCarrier(1891, "SK Telecom", "KR", "https://entitlements-prod.sktdns.com:9901/DEGDROIDInterfaceTS43",
            legacyUserAgent = true, provisioningVersion = "5.0", webTargetDetails = true),
        SupplementalCarrier(1890, "KT", "KR", "https://common.api.kt.com/device/rest/getEapAKAToODSA",
            legacyUserAgent = true, provisioningVersion = "5.0", httpTargetDetails = true),
        SupplementalCarrier(1892, "LG U+", "KR", "https://ts43-es.lguplus.com",
            legacyUserAgent = true, provisioningVersion = "5.0", webTargetDetails = true, lguDetails = true),
        SupplementalCarrier(2287, "Halebop", "SE", "https://es.mnc001.mcc240.pub.3gppnetwork.org:10024/generic_devices/common",
            legacyUserAgent = true),
        // PARENT_ID=959: inherit Samsung's Telia NO settings, retain OneCall's identity and display name.
        SupplementalCarrier(1900, "OneCall", "NO", "https://es.mnc002.mcc242.pub.3gppnetwork.org:10024/generic_devices/common",
            legacyUserAgent = true, provisioningVersion = "5.0"),
        SupplementalCarrier(2548, "ice", "NO", "https://esim-be-prod.ice.no:443/executeGSMAActions",
            version = "9.0", temporaryToken = true, androidClient = true),
        SupplementalCarrier(2014, "Zain", "BH", "https://es.bh.zain.com:10024",
            legacyUserAgent = true, provisioningVersion = "5.0"),
        SupplementalCarrier(2604, "spusu", "GB", "https://sip.massresponse.com/livemvnowebservice/rest/samsunges/10248"),
        SupplementalCarrier(2600, "spusu", "AT", "https://sip.massresponse.com/livemvnowebservice/rest/samsunges/10241"),
        SupplementalCarrier(1992, "stc", "KW", "https://esmart.stc.com.kw/ts43", version = "9.0"),
        SupplementalCarrier(1585, "Zain", "KW", "https://es.kw.zain.com:10024"),
        SupplementalCarrier(892, "Telia", "LT", "https://es.mnc001.mcc246.pub.3gppnetwork.org:10024/generic_devices/common",
            provisioningVersion = "5.0"),
        SupplementalCarrier(797, "Cellcom", "IL", "https://esim-es-open-prod-cellcom.esimcloud.net/cellcom/inbound/executeGSMAActions",
            version = "9.0", temporaryToken = true, psim = false),
        SupplementalCarrier(2397, "Vodafone", "DE", "https://es-de.vodafone.com:5445/DEGDROIDInterfaceTS43",
            version = "9.0", temporaryToken = true, usePost = true, readTimeoutSeconds = 60, equivalentId = 25),
        SupplementalCarrier(2387, "Vodafone", "IE", "https://es-ie.vodafone.com:5449/DEGDROIDInterfaceTS43",
            version = "9.0", temporaryToken = true, usePost = true, readTimeoutSeconds = 60, equivalentId = 19),
        SupplementalCarrier(2398, "Vodafone", "CZ", "https://es-cz.multiopco.entitlement.vodafone.com:5452/DEGDROIDInterfaceTS43",
            version = "9.0", temporaryToken = true, usePost = true, readTimeoutSeconds = 60, equivalentId = 1452),
    )
    private val byId = entries.associateBy { it.id }
    fun find(id: Int): SupplementalCarrier? = byId[id]
    fun catalogAdditions(nativeIds: Set<Int>, displayedIds: Set<Int>): List<SupportedCarrier> = entries
        .filter { it.id !in nativeIds && it.equivalentId !in displayedIds }.map { it.catalog() }
}
