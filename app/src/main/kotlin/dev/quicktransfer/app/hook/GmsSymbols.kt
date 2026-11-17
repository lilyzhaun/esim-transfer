// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.hook

import dev.quicktransfer.app.core.Contract

/** Audited symbol names for the installed GMS builds; callers use the 26.43.30 contract. */
internal object GmsSymbols {
    const val CURRENT = 264432035L
    fun supported(version: Long) = version == Contract.VERSION || version == CURRENT
    private val currentTypes = mapOf(
        "avf" to "atg", "cdut" to "cefm", "cdvd" to "cefx", "cdwe" to "cehb",
        "cdzc" to "cejz", "cdzl" to "ceki", "cdzm" to "cekj", "cebe" to "cemb",
        "cegv" to "ceti", "cegz" to "cetm", "cehf" to "cets", "cehp" to "ceuc",
        "ceiz" to "cevs", "cejo" to "cewh", "cejs" to "cewm", "eclc" to "ediv",
        "ifec" to "igjq", "kaqj" to "kcff", "wwj" to "wya", "wwk" to "wyb",
        "wwn" to "wye", "wwv" to "wym", "wxa" to "wyr", "wxd" to "wyu", "wxm" to "wzd")
    private val currentMethods = mapOf("cdut.h" to "j", "cejs.n" to "o", "cejs.B" to "D")
    private val currentFields = mapOf("cdvd.c" to "d", "cdvd.g" to "h")
    private val currentOwners = currentTypes.entries.associate { (old, current) -> current to old }
    fun type(version: Long, name: String) = if (version == CURRENT) currentTypes[name] ?: name else name
    fun method(version: Long, owner: String, name: String) =
        if (version == CURRENT) currentMethods["$owner.$name"] ?: name else name
    fun field(version: Long, runtimeOwner: String, name: String) =
        if (version == CURRENT) currentFields["${currentOwners[runtimeOwner] ?: runtimeOwner}.$name"] ?: name else name
}
