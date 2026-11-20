// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.core

val TransferRecord.canEndLocally: Boolean get() = phase in setOf(Phase.NEEDS_INPUT, Phase.INTERRUPTED, Phase.UNCERTAIN, Phase.CARRIER_ONGOING)

/** Only a recorded carrier acceptance makes a lost worker a polling interruption. Never replay the application. */
internal fun TransferRecord.afterHostRestart(now: Long = System.currentTimeMillis()): TransferRecord {
    if (!phase.running) return this
    val (next, explanation) = when {
        submitted && !pendingAccepted -> Phase.UNCERTAIN to "服务已重启，无法确认运营商是否收到换卡申请，请先核对原任务"
        submitted -> Phase.INTERRUPTED to "运营商已受理，服务重启中断了查询，请核对原任务"
        phase == Phase.CARRIER_VERIFICATION -> Phase.CANCELLED to "验证会话已结束，未提交换卡申请"
        else -> Phase.CANCELLED to "服务已重启，原会话已结束，未提交换卡申请"
    }
    return copy(phase = next, message = explanation, canResume = false, nextPollAt = 0,
        updatedAt = maxOf(now, updatedAt + 1))
}

/** Ends only local tracking; submission and carrier acceptance remain historical facts. */
fun TransferRecord.endLocally(now: Long = System.currentTimeMillis()): TransferRecord {
    check(canEndLocally) { "任务状态已变化，请刷新后重试" }
    return copy(phase = Phase.CANCELLED, message = "已结束本地任务，运营商申请状态未更改",
        canResume = false, nextPollAt = 0, webSheetId = "", webSheetHost = "", updatedAt = maxOf(now, updatedAt + 1))
}
