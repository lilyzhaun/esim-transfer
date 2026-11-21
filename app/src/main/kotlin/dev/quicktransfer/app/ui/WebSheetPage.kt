// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebMessageCompat
import dev.quicktransfer.app.core.*
import dev.quicktransfer.app.data.WebSheetOutbox
import dev.quicktransfer.app.ipc.HostRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
internal fun WebSheetPage(record: TransferRecord?, state: TransferUiState, back: () -> Unit, connect: () -> Unit) {
    var sheet by remember(record?.id) { mutableStateOf<WebSheetRequest?>(null) }
    var error by remember(record?.id) { mutableStateOf("") }
    var feedback by remember(record?.id) { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var generation by rememberSaveable(record?.id) { mutableIntStateOf(0) }
    var currentHost by rememberSaveable(record?.webSheetId) { mutableStateOf(record?.webSheetHost.orEmpty()) }
    var pendingCount by remember { mutableIntStateOf(0) }
    var permissionSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current.applicationContext
    val outbox = remember(context) { WebSheetOutbox(context) }
    val scope = rememberCoroutineScope()
    val events = remember { Mutex() }
    val latestBack by rememberUpdatedState(back)
    val latestRecord by rememberUpdatedState(record)
    val recordId = record?.id
    suspend fun flush() {
        val id = recordId ?: return
        val host = HostRegistry.connection.value ?: return
        if (latestRecord?.phase?.running == true) return
        sending = true
        try {
            val pending = withContext(Dispatchers.IO) { outbox.pending(id) }
            for (entry in pending) {
                if (latestRecord?.webSheetId != entry.sheet) {
                    withContext(Dispatchers.IO) { outbox.remove(entry.id) }
                    continue
                }
                val result = withContext(Dispatchers.IO) { JSONObject(host.webSheetEvent(id, entry.sheet, entry.event)) }
                withContext(Dispatchers.IO) { outbox.acknowledge(entry.id, result) }
                feedback = result.optString("message")
                if (result.optBoolean("rejected")) continue
                if (result.optBoolean("close")) { latestBack(); break }
                if (WebSheetEvent.parse(entry.event).action == WebAction.CHECK_STATUS) break
            }
        } catch (failure: Exception) {
            feedback = "验证结果已暂存。${failure.message ?: "连接服务后重试保存"}"
        } finally {
            pendingCount = withContext(Dispatchers.IO) { outbox.pending(id).size }
            sending = false
        }
    }
    LaunchedEffect(recordId, state.host.connected, record?.phase, record?.webSheetId) {
        // Host publication can change phase before Binder returns. Do not cancel an in-flight acknowledgement.
        scope.launch { events.withLock {
        flush()
        if (sheet == null && recordId != null && latestRecord?.webSheetId?.isNotEmpty() == true && HostRegistry.connection.value != null) {
            try {
                sheet = withContext(Dispatchers.IO) {
                    val host = requireNotNull(HostRegistry.connection.value) { "服务已断开，请重新连接" }
                    WebSheetRequest.parse(JSONObject(host.openWebSheet(recordId)))
                }
                error = ""
            } catch (failure: Exception) { error = "无法读取验证页面，请返回任务刷新状态"; loading = false }
        } else if (sheet == null) {
            loading = false
        }
        } }
    }
    val colors = uiColors()
    val dismissOperation = LocalDismissOperationMessage.current
    Scaffold(containerColor = colors.canvas, topBar = {
        SmallTopAppBar(title = "运营商验证", navigationIcon = {
            IconAction(MiuixIcons.Back, "返回任务", back)
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            val request = sheet
            if (state.message.isNotBlank()) ActionMessage(state.message, dismissOperation,
                Modifier.fillMaxWidth().padding(12.dp))
            if (request != null) Text(currentHost.ifBlank { request.host }, fontSize = 14.sp, color = colors.secondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (!state.host.connected) {
                Column(Modifier.padding(16.dp)) { EmptyState("服务已断开", "重新连接后继续原任务的网页验证。", "连接服务", connect) }
            } else if (error.isNotEmpty()) {
                Column(Modifier.padding(16.dp)) {
                    Text(error, color = colors.secondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.statusLiveRegion())
                    if (request != null) InlineAction("重新加载网页", !sending, { error = ""; generation++; loading = true }, Modifier.fillMaxWidth())
                }
            }
            if (pendingCount > 0) InlineAction("重试保存验证结果", state.host.connected && !sending,
                { scope.launch { events.withLock { flush() } } }, Modifier.padding(horizontal = 16.dp).fillMaxWidth())
            if (permissionSettings) InlineAction("打开权限设置", true, {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${Contract.APP}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, Modifier.padding(horizontal = 16.dp).fillMaxWidth())
            if (request == null && !loading && error.isEmpty() && state.host.connected) {
                Column(Modifier.padding(16.dp)) { EmptyState("验证页面已结束", "返回任务查看最新结果。", "返回任务", back) }
            }
            if (feedback.isNotEmpty()) Text(feedback, color = colors.secondary, fontSize = 14.sp, lineHeight = 21.sp,
                modifier = Modifier.padding(16.dp).statusLiveRegion())
            if (loading || sending || record?.phase?.running == true) {
                LoadingLine(if (sending || record?.phase?.running == true) "正在处理验证结果" else "正在加载网页",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (request != null) key(request.id, generation) {
                CarrierWebView(request, Modifier.weight(1f).fillMaxWidth(),
                    onPermissionSettings = { permissionSettings = true },
                    onLocation = { currentHost = it }, onLoading = { loading = it }, onError = { error = it; loading = false }, onEvent = { raw ->
                        scope.launch {
                            events.withLock {
                                try {
                                    withContext(Dispatchers.IO) { outbox.add(requireNotNull(recordId), request.id, raw) }
                                    pendingCount = withContext(Dispatchers.IO) { outbox.pending(requireNotNull(recordId)).size }
                                    if (HostRegistry.connection.value == null) feedback = "验证结果已暂存，连接服务后将继续保存"
                                    else flush()
                                } catch (failure: Exception) { feedback = failure.message ?: "验证结果未能保存，请保持当前页面并重试" }
                            }
                        }
                    })
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CarrierWebView(sheet: WebSheetRequest, modifier: Modifier, onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit, onLocation: (String) -> Unit, onEvent: (String) -> Unit, onPermissionSettings: () -> Unit) {
    val loading by rememberUpdatedState(onLoading)
    val failure by rememberUpdatedState(onError)
    val event by rememberUpdatedState(onEvent)
    val location by rememberUpdatedState(onLocation)
    val history = rememberSaveable(sheet.id, saver = BrowserHistory.saver) { BrowserHistory() }
    val chrome = rememberCarrierWebChrome(origin = { history.view?.url?.let { url ->
        try { httpsOrigin(url) } catch (_: IllegalArgumentException) { null }
    } }, error = onError, settings = onPermissionSettings)
    AndroidView(modifier = modifier, factory = { context ->
        WebView(context).apply {
            // WebView treats WRAP_CONTENT as a zero-height CSS viewport for percentage-height pages.
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            history.view = this
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.allowFileAccess = false; settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(false)
            webChromeClient = chrome
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            if (!supported) {
                post { failure("请先更新 Android System WebView，再打开运营商网页") }
            } else {
                WebViewCompat.addWebMessageListener(this, "EsimTransferBridge", setOf(sheet.origin)) { _, message, origin, mainFrame, _ ->
                    if (mainFrame && message.type == WebMessageCompat.TYPE_STRING && httpsOrigin(origin.toString()) == sheet.origin) message.data?.let(event)
                }
                WebViewCompat.addDocumentStartJavaScript(this, ODSA_BRIDGE, setOf(sheet.origin))
                val initialPost = WebSheetPost(sheet, settings.userAgentString,
                    navigate = { url -> post { loadUrl(url) } }, error = { text -> post { failure(text) } },
                    initialPostAllowed = !history.started)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (request.url.scheme == "https") {
                            if (request.isForMainFrame) location(request.url.host.orEmpty())
                            return false
                        }
                        if (request.isForMainFrame) failure("验证网页请求打开外部应用，当前页面已保留")
                        return true
                    }
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        if (request.isForMainFrame) initialPost.intercept(request.url.toString()) else null
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        chrome.navigation(url?.let { try { httpsOrigin(it) } catch (_: IllegalArgumentException) { null } })
                        location(url?.let { android.net.Uri.parse(it).host }.orEmpty()); loading(true)
                    }
                    override fun onPageFinished(view: WebView, url: String?) {
                        location(url?.let { android.net.Uri.parse(it).host }.orEmpty()); loading(false)
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) failure("网页加载失败，请检查网络后重新加载")
                    }
                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if (request.isForMainFrame) failure("运营商网页暂不可用（${response.statusCode}），可稍后重新加载")
                    }
                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                        handler.cancel(); failure("无法验证运营商网页的 HTTPS 连接，请检查网络和系统时间")
                    }
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        failure("网页进程已结束，请重新加载网页"); return true
                    }
                }
                val restored = history.saved?.let { restoreState(it) }
                if (restored != null) {
                    location(restored.currentItem?.url?.let { android.net.Uri.parse(it).host }.orEmpty())
                    loading(false)
                } else if (history.started) {
                    post { failure("验证页面已中断，请重新加载网页；原转移任务仍保留") }
                } else {
                    history.started = true
                    loadUrl(sheet.navigationUrl)
                }
            }
        }
    }, onRelease = { view -> history.capture(); history.view = null; view.stopLoading(); view.destroy() })
}

/** Preserve WebView navigation across configuration changes; failed restoration never replays POST. */
private class BrowserHistory(var saved: Bundle? = null) {
    var view: WebView? = null
    var started = saved?.getBoolean("esim.started") == true
    fun capture(): Bundle = (view?.let { browser -> Bundle().also { browser.saveState(it) } } ?: saved ?: Bundle()).also {
        it.putBoolean("esim.started", started); saved = it
    }
    companion object { val saver = Saver<BrowserHistory, Bundle>(save = { it.capture() }, restore = { BrowserHistory(it) }) }
}

private val ODSA_BRIDGE = """
    (() => {
      const send = (event, args) => EsimTransferBridge.postMessage(JSON.stringify({event, args: args.map(v => v == null ? '' : String(v))}));
      const flow = {};
      ['profileReadyWithActivationCode', 'profileReadyWithDefaultSmdp', 'SelectionCompleted',
       'finishFlow', 'dismissFlow', 'deleteToken', 'checkProfileServiceStatus', 'deleteProfileInUse']
        .forEach(name => flow[name] = (...args) => send(name, args));
      Object.defineProperty(window, 'ODSAServiceFlow', {value: Object.freeze(flow), configurable: false});
    })();
""".trimIndent()
