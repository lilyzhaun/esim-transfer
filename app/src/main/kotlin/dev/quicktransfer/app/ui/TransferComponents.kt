// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quicktransfer.app.core.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FlowHeading(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        if (description.isNotEmpty()) Text(description, color = uiColors().secondary, fontSize = 15.sp, lineHeight = 23.sp)
    }
}

@Composable
internal fun CompletionMark(modifier: Modifier = Modifier, color: Color = MiuixTheme.colorScheme.primary) {
    Canvas(modifier) {
        drawLine(color, Offset(size.width * .2f, size.height * .51f), Offset(size.width * .42f, size.height * .72f), 2.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(size.width * .42f, size.height * .72f), Offset(size.width * .8f, size.height * .28f), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
internal fun TaskRouteSummary(record: TransferRecord) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("源线路", color = uiColors().secondary, fontSize = 13.sp)
                Text(record.source.displayTitle, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(uiColors().secondary.copy(alpha = .10f)))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("接收设备", color = uiColors().secondary, fontSize = 13.sp)
                Text(record.target.name, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text("EID 尾号 ${record.target.eid.takeLast(6)}", color = uiColors().secondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun TaskOutcomeHeader(record: TransferRecord, compact: Boolean = false) {
    val success = record.phase == Phase.ISSUED
    val problem = record.phase in setOf(Phase.FAILED, Phase.UNCERTAIN, Phase.INTERRUPTED)
    val ink = when {
        success -> if (LocalDarkTheme.current) Color(0xFF79D7AE) else Color(0xFF19734C)
        problem -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.primary
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp).statusLiveRegion(),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!compact) Box(Modifier.size(56.dp).background(ink.copy(alpha = .10f), CircleShape), contentAlignment = Alignment.Center) {
            when {
                success -> CompletionMark(Modifier.size(30.dp), ink)
                record.phase.running -> LoadingRing(26.dp, Modifier, ink)
                else -> Icon(MiuixIcons.Info, null, Modifier.size(28.dp), tint = ink)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (compact && success) CompletionMark(Modifier.size(24.dp), ink)
            Text(record.statusTitle, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Text(if (success && record.activationCode.isNotBlank()) "在目标设备添加 eSIM，即可继续使用这条线路。" else record.message,
            color = uiColors().secondary, fontSize = 14.sp, lineHeight = 22.sp,
            textAlign = TextAlign.Center)
    }
}

@Composable
internal fun TaskSummaryCard(record: TransferRecord, click: () -> Unit, enabled: Boolean = true) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        ArrowPreference(title = record.statusTitle, summary = "${record.source.displayTitle} → ${record.target.name}", onClick = click.takeIf { enabled },
            startAction = {
                if (record.phase == Phase.ISSUED) CompletionMark(Modifier.size(24.dp))
                else Icon(MiuixIcons.Info, null, Modifier.size(24.dp), tint = MiuixTheme.colorScheme.primary)
            })
    }
}
