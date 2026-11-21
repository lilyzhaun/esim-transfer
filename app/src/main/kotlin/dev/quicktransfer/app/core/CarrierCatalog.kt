// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.text.Collator
import java.text.Normalizer

data class SupportedCarrier(val id: Int, val name: String, val countries: List<String>, val psim: Boolean, val esim: Boolean,
    val backend: String = "gms", val status: String = "") {
    val backendLabel: String get() = "GMS"
    val flag: String get() = countries.joinToString(" ", transform = ::countryFlag).ifBlank { "🌐" }
    val title: String get() = "$flag  $name"
    fun countryNames(locale: Locale = Locale.getDefault()): String = countries.joinToString("、") {
        Locale.Builder().setRegion(it).build().getDisplayCountry(locale)
    }.ifBlank { "地区未标注" }
    val simTypes: String get() = when { psim -> "SIM 卡"; esim -> "仅 eSIM 转出"; else -> "暂不支持转移" }
    fun matches(query: String, locale: Locale = Locale.getDefault()): Boolean = query.trim().let { text ->
        text.isEmpty() || name.contains(text, true) || countries.any { it.equals(text, true) } ||
            countryNames(locale).contains(text, true) || countryNames(Locale.ENGLISH).contains(text, true) || backendLabel.contains(text, true)
    }
    fun json() = JSONObject().put("id", id).put("name", name).put("countries", JSONArray(countries)).put("psim", psim).put("esim", esim).put("backend", backend).put("status", status)
    companion object {
        fun parse(j: JSONObject) = SupportedCarrier(j.getInt("id"), j.getString("name"),
            j.optJSONArray("countries").strings().map { it.uppercase(Locale.ROOT) }.filter { it.matches(Regex("[A-Z]{2}")) }.distinct().sorted(),
            j.optBoolean("psim"), j.optBoolean("esim"), j.optString("backend", "gms"), j.optString("status"))
    }
}

/** Presentation only: merging duplicate names never changes carrier IDs or execution routing. */
data class CarrierListing(val key: String, val carrier: SupportedCarrier, val backends: List<String>, val aliases: List<SupportedCarrier>) {
    fun matches(query: String, locale: Locale) = aliases.any { it.matches(query, locale) }
    fun summary(locale: Locale) = listOf(carrier.countryNames(locale), backends.joinToString(" / "),
        carrier.simTypes.takeUnless { carrier.psim }.orEmpty()).filter(String::isNotEmpty).joinToString(" · ")
}

fun carrierListings(entries: List<SupportedCarrier>, locale: Locale = Locale.getDefault()): List<CarrierListing> {
    fun nameKey(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
    val collator = Collator.getInstance(locale)
    return entries.groupBy { it.countries.sorted().joinToString(",") + ":" + nameKey(it.name) }.map { (key, aliases) ->
        val representative = aliases.sortedWith(compareBy<SupportedCarrier> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id }).first()
        CarrierListing(key, representative.copy(psim = aliases.any { it.psim }, esim = aliases.any { it.esim }, status = ""),
            aliases.map { it.backendLabel }.distinct().sorted(), aliases.sortedBy { it.id })
    }.sortedWith { a, b ->
        val country = collator.compare(a.carrier.countryNames(locale), b.carrier.countryNames(locale))
        if (country != 0) country else collator.compare(a.carrier.name, b.carrier.name).takeIf { it != 0 } ?: a.key.compareTo(b.key)
    }
}

fun countryFlag(country: String): String {
    val code = country.uppercase(Locale.ROOT)
    if (!code.matches(Regex("[A-Z]{2}"))) return "🌐"
    return code.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }.joinToString("")
}

data class CarrierCatalog(val entries: List<SupportedCarrier> = emptyList(), val updatedAt: Long = 0,
    val loading: Boolean = false, val message: String = "") {
    fun json() = JSONObject().put("entries", JSONArray(entries.map(SupportedCarrier::json))).put("updatedAt", updatedAt)
        .put("loading", loading).put("message", message)
    companion object {
        fun parse(j: JSONObject?) = if (j == null) CarrierCatalog() else CarrierCatalog(
            j.optJSONArray("entries").objects().map(SupportedCarrier::parse), j.optLong("updatedAt"), j.optBoolean("loading"), j.optString("message"))
    }
}
