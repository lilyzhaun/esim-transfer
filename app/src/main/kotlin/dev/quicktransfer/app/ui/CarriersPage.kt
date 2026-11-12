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
    PageFrame("Supported carriers", onBack = back, message = state.message,
        actions = { RefreshAction("Refresh carriers", !busy, refresh, busy) }) {
        if (catalog.entries.isNotEmpty()) {
            item {
                TextField(query, { query = it }, label = "Search carrier or country", singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth())
            }
            item { Text(if (query.isBlank()) "${entries.size} carriers" else "${entries.size} carriers found",
                color = uiColors().secondary, fontSize = 14.sp, modifier = Modifier.statusLiveRegion()) }
        }
        if (catalog.message.isNotBlank()) item { Text(catalog.message, color = uiColors().secondary,
            fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.statusLiveRegion()) }
        if (!state.host.connected && catalog.entries.isNotEmpty()) item {
            Text("Disconnected. Showing the previously loaded list.", color = uiColors().secondary, fontSize = 14.sp)
        }
        when {
            busy && catalog.entries.isEmpty() -> item { EmptyState("Loading carriers", "Updating the list. Please wait") }
            !state.host.connected && catalog.entries.isEmpty() -> item {
                EmptyState("Connect to view carriers", "Connect the service to load the carrier list.", "Connect service", refresh)
            }
            catalog.updatedAt == 0L && catalog.entries.isEmpty() -> item {
                EmptyState("Could not load the list", "Check the service connection and try again.", "Reload", refresh)
            }
            catalog.entries.isEmpty() -> item { EmptyState("No carriers available", "Refresh the list later.") }
            entries.isEmpty() -> item { EmptyState("No matching carriers", "Try a carrier or country name.", "Clear search") { query = "" } }
            else -> items(entries, key = { it.key }) { entry ->
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    BasicComponent(title = entry.carrier.title, summary = entry.summary(locale))
                }
            }
        }
    }
}
