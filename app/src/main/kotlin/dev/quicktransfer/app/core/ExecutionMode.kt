// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.core

enum class ExecutionMode(val title: String, val description: String) {
    GMS("LSPosed / GMS", "在 LSPosed 中勾选 Google Play 服务");
    companion object { fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: GMS }
}
