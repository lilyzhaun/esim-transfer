// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quicktransfer.app.core.carrierListings
import top.yukonga.miuix.kmp.basic.*

@Composable
internal fun CarriersPage(state: TransferUiState, back: () -> Unit, refresh: () -> Unit) {
    val catalog = state.host.catalog
    val busy = state.busy || catalog.loading
    var query by rememberSaveable { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
    val focus = LocalFocusManager.current
    val listing = remember(catalog.entries, locale) { carrierListings(catalog.entries, locale) }
    val entries = remember(listing, query, locale) { listing.filter { it.matches(query, locale) } }
    PageFrame("支持的运营商", onBack = back, message = state.message,
        actions = { RefreshAction("刷新运营商列表", !busy, refresh, busy) }) {
        if (catalog.entries.isNotEmpty()) {
            item {
                TextField(query, { query = it }, label = "搜索运营商或国家地区", singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth())
            }
            item { Text(if (query.isBlank()) "${entries.size} 个运营商" else "找到 ${entries.size} 个运营商",
                color = uiColors().secondary, fontSize = 14.sp, modifier = Modifier.statusLiveRegion()) }
        }
        if (catalog.message.isNotBlank()) item { Text(catalog.message, color = uiColors().secondary,
            fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.statusLiveRegion()) }
        if (!state.host.connected && catalog.entries.isNotEmpty()) item {
            Text("服务已断开，当前显示上次读取的列表。", color = uiColors().secondary, fontSize = 14.sp)
        }
        when {
            busy && catalog.entries.isEmpty() -> item { EmptyState("正在读取运营商", "正在更新列表，请稍候") }
            !state.host.connected && catalog.entries.isEmpty() -> item {
                EmptyState("连接后查看运营商", "连接服务以获取运营商列表。", "连接服务", refresh)
            }
            catalog.updatedAt == 0L && catalog.entries.isEmpty() -> item {
                EmptyState("暂时无法读取列表", "请检查服务连接后重试。", "重新读取", refresh)
            }
            catalog.entries.isEmpty() -> item { EmptyState("暂无可用运营商", "请稍后刷新列表。") }
            entries.isEmpty() -> item { EmptyState("没有匹配的运营商", "试试运营商名称或国家地区名称。", "清除搜索") { query = "" } }
            else -> items(entries, key = { it.key }) { entry ->
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    BasicComponent(title = entry.carrier.title, summary = entry.summary(locale))
                }
            }
        }
    }
}
