// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.core

val TransferRecord.canEndLocally: Boolean get() = phase in setOf(Phase.NEEDS_INPUT, Phase.INTERRUPTED, Phase.UNCERTAIN)

/** Ends only local tracking; submission and carrier acceptance remain historical facts. */
fun TransferRecord.endLocally(now: Long = System.currentTimeMillis()): TransferRecord {
    check(canEndLocally) { "任务状态已变化，请刷新后重试" }
    return copy(phase = Phase.CANCELLED, message = "已结束本地任务，运营商申请状态未更改",
        canResume = false, nextPollAt = 0, webSheetId = "", webSheetHost = "", updatedAt = maxOf(now, updatedAt + 1))
}
