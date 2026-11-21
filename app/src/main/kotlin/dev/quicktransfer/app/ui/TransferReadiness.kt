// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import dev.quicktransfer.app.core.*

internal enum class SetupAction { CONNECT, SOURCE, TARGET, DETAILS, RECORD }
internal data class TransferBlocker(val message: String, val action: SetupAction)

/** The form and the submit boundary use the same prerequisites, before any host preparation. */
internal fun transferBlocker(state: TransferUiState, source: SourceLine?, records: List<TransferRecord>,
    eid: String, imei: String, details: TargetDetails): TransferBlocker? {
    fun blocked(text: String, action: SetupAction) = TransferBlocker(text, action)
    if (records.any { it.blocksNewTransfer }) return blocked("请先处理尚未结束的转移任务", SetupAction.RECORD)
    if (!state.host.connected || !state.host.supported || state.host.executionMode != state.mode.name)
        return blocked("请先连接当前模式的服务", SetupAction.CONNECT)
    if (source == null) return blocked("请选择要转移的本机 SIM", SetupAction.SOURCE)
    if (!source.supported) return blocked(source.reason.ifBlank { "此线路暂不可转移，请选择其他 SIM 或刷新线路" }, SetupAction.SOURCE)
    IdentityRules.eidError(eid)?.let { return blocked(if (eid.isBlank()) "请填写目标设备的 32 位 EID" else it, SetupAction.TARGET) }
    IdentityRules.imeiError(imei)?.let { return blocked(if (imei.isBlank()) "请填写目标设备的 15 位 IMEI" else it, SetupAction.TARGET) }
    details.error()?.let { return blocked(it, SetupAction.DETAILS) }
    return null
}
