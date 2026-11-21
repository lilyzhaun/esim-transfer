// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quicktransfer.app.core.TargetDetails
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val TargetDetailsSaver = Saver<TargetDetails, String>(save = { it.json().toString() }, restore = { TargetDetails.parse(JSONObject(it)) })
internal val targetDetailLabels = linkedMapOf("model" to "目标设备型号", "serial" to "目标设备序列号",
    "imei2" to "第二 IMEI（如有）", "barcode" to "设备条码", "activatedId" to "设备激活标识")

@Composable
internal fun TargetDetailFields(details: TargetDetails, fields: List<String>, change: (TargetDetails) -> Unit, enabled: Boolean = true,
    required: Boolean = false) {
    val visibleFields = fields.filter { it in targetDetailLabels }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(if (required) "必填设备信息" else "补充设备信息")
        Text(if (required) "此运营商需要以下目标设备信息，填写后即可继续。" else "填写最终下载设备的真实信息，不适用的项目可以留空。",
            modifier = Modifier.padding(horizontal = 16.dp), fontSize = 14.sp, lineHeight = 21.sp, color = uiColors().secondary)
        visibleFields.forEachIndexed { index, key ->
            TextField(value = details.fields().getValue(key), onValueChange = { input ->
                val value = if (key == "imei2") input.filterNot(Char::isWhitespace) else input
                val bounded = value.take(120)
                change(when (key) {
                    "model" -> details.copy(model = bounded)
                    "serial" -> details.copy(serial = bounded)
                    "imei2" -> details.copy(imei2 = bounded)
                    "barcode" -> details.copy(barcode = bounded)
                    "activatedId" -> details.copy(activatedId = bounded)
                    else -> details
                })
            }, label = targetDetailLabels.getValue(key).let { if (required) it.replace("（如有）", "") else it }, enabled = enabled, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (key == "imei2") KeyboardType.Number else KeyboardType.Text,
                    imeAction = if (index == visibleFields.lastIndex) ImeAction.Done else ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { keyboard?.hide(); focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth())
        }
        details.error()?.let { Text(it, color = MiuixTheme.colorScheme.error, fontSize = 13.sp, lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 16.dp).statusLiveRegion()) }
    }
}
