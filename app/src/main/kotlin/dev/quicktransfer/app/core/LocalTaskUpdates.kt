// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.core

val TransferRecord.canEndLocally: Boolean get() = phase in setOf(Phase.NEEDS_INPUT, Phase.INTERRUPTED, Phase.UNCERTAIN)

/** Ends only local tracking; submission and carrier acceptance remain historical facts. */
fun TransferRecord.endLocally(now: Long = System.currentTimeMillis()): TransferRecord {
    check(canEndLocally) { "The task state changed. Refresh and try again" }
    return copy(phase = Phase.CANCELLED, message = "Local task ended. The carrier request is unchanged",
        canResume = false, nextPollAt = 0, webSheetId = "", webSheetHost = "", updatedAt = maxOf(now, updatedAt + 1))
}
