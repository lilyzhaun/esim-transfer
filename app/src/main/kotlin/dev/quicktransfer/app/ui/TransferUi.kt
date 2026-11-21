// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.*
import androidx.navigation3.scene.*
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import dev.quicktransfer.app.core.*
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.*

private data object RootOverlay
private data object AppearanceRoute
private data object DeviceEditorRoute
private data object TargetPickerRoute
private data object SourcePickerRoute
private data object RecordRoute
private data object AboutRoute
private data object LicenseRoute
private data object DiagnosticsRoute
private data object CarriersRoute
private data object WebSheetRoute
private data object TaskLogRoute
private data object LocalTransferRoute

@Composable
fun TransferUi(model: TransferViewModel = viewModel()) {
    val context = LocalContext.current
    val appearanceStore = remember { AppearanceStore(context) }
    val appearance by appearanceStore.state.collectAsState()
    val state by model.state.collectAsState()
    val devices by model.library.targets.collectAsState()
    val records by model.library.records.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance.theme) { ThemeMode.SYSTEM -> systemDark; ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val controller = remember(dark, appearance.accent) { ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
        lightColors = appColors(false, appearance.accent), darkColors = appColors(true, appearance.accent)) }
    val shader = remember { isRuntimeShaderSupported() }
    var pages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedSource by rememberSaveable { mutableStateOf<Int?>(null) }
    var targetId by rememberSaveable { mutableStateOf<String?>(null) }
    var targetName by rememberSaveable { mutableStateOf("") }
    var eid by rememberSaveable { mutableStateOf("") }
    var imei by rememberSaveable { mutableStateOf("") }
    var targetDetails by rememberSaveable(stateSaver = TargetDetailsSaver) { mutableStateOf(TargetDetails()) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var useEditedTarget by rememberSaveable { mutableStateOf(false) }
    var rootRequest by remember { mutableStateOf<RootPage?>(null) }
    var recordId by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pages.lastOrNull()
    fun navigate(route: String) { if (pages.lastOrNull() != route) { model.clearMessage(); pages += route } }
    val back: () -> Unit = {
        if (pages.lastOrNull() == "websheet") model.leaveWebSheet(recordId)
        model.clearMessage(); pages = pages.dropLast(1)
    }
    LaunchedEffect(state.sources) {
        if (state.sources.isNotEmpty() && state.sources.none { it.subId == selectedSource })
            selectedSource = state.sources.filter { it.supported }.singleOrNull()?.subId
    }
    SideEffect {
        val activity = context as Activity
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
        }
    }
    val platformHaptic = LocalHapticFeedback.current
    val hapticEnabled by rememberUpdatedState(appearance.haptics)
    val haptic = remember(platformHaptic) { object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { if (hapticEnabled) platformHaptic.performHapticFeedback(hapticFeedbackType) }
    } }
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(LocalAppearance provides appearance, LocalShaderSupported provides shader,
            LocalOperationMessage provides state.message, LocalDismissOperationMessage provides model::clearMessage,
            LocalDarkTheme provides dark, LocalHapticFeedback provides haptic) {
            RootPopupHost { Box(Modifier.fillMaxSize()) {
                RootShell(covered = page != null, rootRequest = rootRequest, consumeRootRequest = { rootRequest = null }) { root ->
                    when (root) {
                        RootPage.HOME -> TransferHomePage(state, records,
                            if (IdentityRules.valid(eid, imei)) TargetDevice(targetId ?: "draft", targetName.ifBlank { "本次目标设备" }, eid, imei, targetDetails) else null,
                            { navigate("local_transfer") }, { navigate("pick_target") },
                             { navigate("diagnostics") }, { id -> recordId = id; navigate("record") })
                         RootPage.DEVICES -> DevicesPage(devices,
                             { editId = null; useEditedTarget = false; navigate("device") },
                             { editId = it; useEditedTarget = false; navigate("device") })
                         RootPage.HISTORY -> HistoryPage(records, model::deleteRecord, model::syncHistory, state.busy) { recordId = it; navigate("record") }
                        RootPage.SETTINGS -> SettingsPage(state, appearance, { navigate("appearance") }, { navigate("diagnostics") }, model::openManager, model::requestScope,
                             { navigate("about") }, { navigate("carriers") })
                    }
                }
                val destinations: List<Any> = pages.mapNotNull { when (it) {
                    "appearance" -> AppearanceRoute; "device" -> DeviceEditorRoute; "pick_target" -> TargetPickerRoute
                    "source" -> SourcePickerRoute; "record" -> RecordRoute; "about" -> AboutRoute; "diagnostics" -> DiagnosticsRoute
                    "carriers" -> CarriersRoute
                    "websheet" -> WebSheetRoute
                    "task_log" -> TaskLogRoute
                      "license" -> LicenseRoute
                        "local_transfer" -> LocalTransferRoute
                    else -> null
                } }
                LaunchedEffect(pages, destinations.size) { if (pages.size != destinations.size) pages = emptyList() }
                val entries = entryProvider<Any> {
                      entry<RootOverlay> { Box(Modifier.fillMaxSize()) }
                      entry<LocalTransferRoute> {
                          val liveState by model.state.collectAsState()
                          val liveRecords by model.library.records.collectAsState()
                          TransferPage(liveState, liveState.sources.firstOrNull { it.subId == selectedSource }, liveRecords,
                              targetName, eid, imei, { eid = it; targetId = null; targetName = ""; model.clearMessage() },
                              { imei = it; targetId = null; targetName = ""; model.clearMessage() },
                              model::connect, model::openManager, { navigate("source") }, { navigate("pick_target") },
                              { id -> recordId = id; navigate("record") }, targetDetails,
                              { targetDetails = it; targetId = null; targetName = ""; model.clearMessage() }, {
                                  liveState.sources.firstOrNull { it.subId == selectedSource }?.let { source ->
                                      model.start(source, TargetDevice(targetId ?: java.util.UUID.randomUUID().toString(), targetName.ifBlank { "本次目标设备" }, eid, imei, targetDetails)) {
                                          recordId = it; navigate("record")
                                      }
                                  }
                              }, { navigate("diagnostics") }, back = back)
                      }
                    entry<AppearanceRoute> { AppearancePage(appearanceStore, back) }
                     entry<DeviceEditorRoute> {
                         val liveDevices by model.library.targets.collectAsState()
                          val initial = liveDevices.firstOrNull { it.id == editId } ?: if (useEditedTarget && editId != null)
                              TargetDevice(requireNotNull(editId), targetName.ifBlank { "本次目标设备" }, eid, imei, targetDetails) else null
                          DeviceEditor(initial, back,
                             { device ->
                                 model.library.save(device)
                                 if (useEditedTarget || targetId == device.id) {
                                     targetId = device.id; targetName = device.name; eid = device.eid; imei = device.imei; targetDetails = device.details
                                 }
                                 if (useEditedTarget) {
                                      useEditedTarget = false
                                      val flowIndex = pages.indexOfLast { it == "local_transfer" }
                                      pages = if (flowIndex >= 0) pages.take(flowIndex + 1) else emptyList()
                                      if (flowIndex < 0) rootRequest = RootPage.HOME
                                 } else back()
                             }, {
                                 editId?.let(model.library::delete)
                                 if (targetId == editId) { targetId = null; targetName = "" }
                                 back()
                             })
                     }
                    entry<TargetPickerRoute> {
                        val liveDevices by model.library.targets.collectAsState()
                         TargetPicker(liveDevices, back, { editId = null; useEditedTarget = true; navigate("device") }) {
                        targetId = it.id; targetName = it.name; eid = it.eid; imei = it.imei; targetDetails = it.details; back()
                    } }
                    entry<SourcePickerRoute> {
                        val liveState by model.state.collectAsState()
                         SourcePicker(liveState.sources, selectedSource, back, { navigate("carriers") },
                             { selectedSource = it; model.clearMessage(); back() }, model::connect, liveState.busy)
                    }
                    entry<RecordRoute> { RecordDestination(recordId, model, back, { navigate("websheet") }, { navigate("task_log") }) }
                    entry<TaskLogRoute> {
                        val liveState by model.state.collectAsState()
                        val liveRecords by model.library.records.collectAsState()
                         TaskLogPage(liveRecords.firstOrNull { it.id == recordId }, liveState, back, model::syncHistory, model::taskLog, model::exportTaskLog)
                    }
                    entry<WebSheetRoute> {
                        val liveState by model.state.collectAsState()
                        val liveRecords by model.library.records.collectAsState()
                        WebSheetPage(liveRecords.firstOrNull { it.id == recordId }, liveState, back, model::connect)
                    }
                    entry<AboutRoute> { AboutPage(back, { navigate("license") }, active = page == "about") }
                    entry<LicenseRoute> { LicensePage(back) }
                    entry<DiagnosticsRoute> {
                        val liveState by model.state.collectAsState()
                        DiagnosticsPage(liveState, back, model::connect)
                    }
                    entry<CarriersRoute> {
                        val liveState by model.state.collectAsState()
                        CarriersPage(liveState, back, model::connect)
                    }
                }
                val decorated = rememberDecoratedNavEntries(backStack = listOf(RootOverlay) + destinations,
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()), entryProvider = entries)
                val scenes = rememberSceneState(decorated, sceneStrategies = listOf(SinglePaneSceneStrategy()), onBack = back)
                val gesture = rememberNavigationEventState(scenes)
                val resting = rememberNavigationEventState(scenes)
                NavigationBackHandler(scenes, gesture, back)
                key(appearance.predictiveBack) {
                    CompositionLocalProvider(LocalBottomInset provides WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), LocalRouteActive provides true) {
                        NavDisplay(sceneState = scenes, navigationEventState = if (appearance.predictiveBack) gesture else resting,
                            transitionEffects = NavDisplayTransitionEffects(enableCornerClip = false, dimAmount = 0f, blockInputDuringTransition = true))
                    }
                }
            } }
        }
    }
}

/** A retained navigation entry owns its subscriptions, not a parent composition's snapshots. */
@Composable
private fun RecordDestination(id: String?, model: TransferViewModel, back: () -> Unit, webSheet: () -> Unit, taskLog: () -> Unit) {
    val records by model.library.records.collectAsState()
    val state by model.state.collectAsState()
    RecordPage(records.firstOrNull { it.id == id }, state, back, model::connect, model::cancel, model::resume, webSheet, taskLog, model::endLocalTask)
}
