// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.yukonga.miuix.kmp.theme.*

enum class ThemeMode(val title: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }
enum class Accent(val title: String, val light: Color, val dark: Color) {
    BLUE("Blue", Color(0xFF1765D1), Color(0xFF82B0FF)),
    TEAL("Teal", Color(0xFF006C67), Color(0xFF79D7CC)),
    VIOLET("Violet", Color(0xFF6941B3), Color(0xFFC5ABF8)),
    ROSE("Rose", Color(0xFFAC3F56), Color(0xFFF3AABD))
}
data class Appearance(val theme: ThemeMode = ThemeMode.SYSTEM, val accent: Accent = Accent.BLUE,
    val floating: Boolean = true, val blur: Boolean = true, val liquid: Boolean = true,
    val predictiveBack: Boolean = false, val haptics: Boolean = true)

class AppearanceStore(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(Appearance(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: ThemeMode.SYSTEM,
        Accent.entries.firstOrNull { it.name == prefs.getString("accent", null) } ?: Accent.BLUE,
        prefs.getBoolean("floating_nav", true), prefs.getBoolean("blur_enabled", true), prefs.getBoolean("liquid_glass_enabled", true),
        prefs.getBoolean("predictive_back_enabled", false), prefs.getBoolean("haptics_enabled", true)))
    val state = mutable.asStateFlow()
    fun theme(mode: ThemeMode) { mutable.value = mutable.value.copy(theme = mode); prefs.edit().putString("theme", mode.name).apply() }
    fun accent(accent: Accent) { mutable.value = mutable.value.copy(accent = accent); prefs.edit().putString("accent", accent.name).apply() }
    fun set(key: String, enabled: Boolean) {
        mutable.value = when (key) {
            "floating_nav" -> mutable.value.copy(floating = enabled)
            "blur_enabled" -> mutable.value.copy(blur = enabled)
            "liquid_glass_enabled" -> mutable.value.copy(liquid = enabled)
            "predictive_back_enabled" -> mutable.value.copy(predictiveBack = enabled)
            "haptics_enabled" -> mutable.value.copy(haptics = enabled)
            else -> error("Unknown appearance setting")
        }
        prefs.edit().putBoolean(key, enabled).apply()
    }
}
internal val LocalRouteActive = staticCompositionLocalOf { true }
internal val LocalBottomInset = staticCompositionLocalOf { androidx.compose.ui.unit.Dp(0f) }
internal val LocalDarkTheme = staticCompositionLocalOf { false }
internal fun appColors(dark: Boolean, accent: Accent) = if (dark) darkColorScheme(primary = accent.dark,
    onPrimary = Color(0xFF102340), onSecondaryContainer = Color(0xFFD0D0D0), error = Color(0xFFFFB4AB))
else lightColorScheme(primary = accent.light, onSecondaryContainer = Color(0xFF626262), error = Color(0xFFB3261E))
internal data class UiColors(val canvas: Color, val surface: Color, val text: Color, val secondary: Color, val accent: Color)
@Composable internal fun uiColors() = UiColors(MiuixTheme.colorScheme.surface, MiuixTheme.colorScheme.surfaceContainer,
    MiuixTheme.colorScheme.onSurface, MiuixTheme.colorScheme.onSurfaceVariantSummary, MiuixTheme.colorScheme.primary)
