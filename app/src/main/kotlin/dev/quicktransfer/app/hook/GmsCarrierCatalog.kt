// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.hook

import android.content.Context
import android.net.Uri
import dev.quicktransfer.app.core.*

internal class GmsCarrierCatalog(private val context: Context, private val gms: GmsReflection) {
    fun read(): CarrierCatalog {
        val lookup = requireNotNull(gms.static("cejo", "d"))
        val table = gms.field(requireNotNull(gms.static("kaqj", "b")), "b") as Map<*, *>
        val overrides = gms.call(lookup, "e") as List<*>
        val ids = table.keys.map { (it as Number).toInt() }.toSet() + overrides.map { gms.number(requireNotNull(it), "a").toInt() }
        var warning = ""
        val countries = try { countries(ids) } catch (_: SecurityException) {
            warning = "Country information is restricted. Unknown locations use a globe icon"; emptyMap()
        } catch (_: IllegalArgumentException) {
            warning = "Country information is unavailable. Unknown locations use a globe icon"; emptyMap()
        }
        val entries = ids.mapNotNull { id ->
            val info = gms.call(lookup, "b", id, false) ?: return@mapNotNull null
            val psim = gms.static("cejs", "B", gms.number(info, "d").toInt()) == true
            val esim = gms.static("cejs", "B", gms.number(info, "e").toInt()) == true
            if (!psim && !esim) null else SupportedCarrier(id, gms.text(info, "b").ifBlank { "Carrier $id" },
                countries[id].orEmpty().sorted(), psim, esim)
        }.toMutableList()
        val nativeIds = ids + SupplementalCarriers.entries.filter { gms.call(lookup, "b", it.id, false) != null }.map { it.id }
        entries += SupplementalCarriers.catalogAdditions(nativeIds, entries.map { it.id }.toSet())
        entries.sortWith(compareBy<SupportedCarrier> { it.countries.firstOrNull() ?: "ZZ" }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })
        return CarrierCatalog(entries, System.currentTimeMillis(), message = warning)
    }

    private fun countries(ids: Set<Int>): Map<Int, Set<String>> {
        val result = mutableMapOf<Int, MutableSet<String>>()
        val cursor = context.contentResolver.query(Uri.parse("content://carrier_id/all"),
            arrayOf("carrier_id", "mccmnc"), null, null, null)
            ?: throw IllegalArgumentException("Carrier identification is unavailable")
        cursor.use {
            val idColumn = it.getColumnIndexOrThrow("carrier_id")
            val mccColumn = it.getColumnIndexOrThrow("mccmnc")
            while (it.moveToNext()) {
                val id = it.getInt(idColumn)
                if (id in ids) MccCountries.forMccMnc(it.getString(mccColumn).orEmpty())?.let { country ->
                    result.getOrPut(id) { mutableSetOf() }.add(country)
                }
            }
        }
        return result
    }
}
