// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.core

val TransferRecord.canDeleteFromHistory: Boolean get() = phase in setOf(Phase.ISSUED, Phase.FAILED, Phase.CANCELLED,
    Phase.EXTERNAL_CHANNEL, Phase.NO_TRANSFER, Phase.INELIGIBLE)
