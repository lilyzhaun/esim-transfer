// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import dev.quicktransfer.app.core.Contract
import dev.quicktransfer.app.core.httpsOrigin
import java.io.File
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog

internal fun mediaPermissions(resources: Array<String>): List<String>? {
    val mapping = mapOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE to Manifest.permission.CAMERA,
        PermissionRequest.RESOURCE_AUDIO_CAPTURE to Manifest.permission.RECORD_AUDIO)
    return if (resources.isEmpty() || resources.any { it !in mapping }) null else resources.map { mapping.getValue(it) }.distinct()
}

@Composable
internal fun rememberCarrierWebChrome(origin: () -> String?, error: (String) -> Unit, settings: () -> Unit): CarrierWebChrome {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val latestOrigin by rememberUpdatedState(origin)
    val latestError by rememberUpdatedState(error)
    val latestSettings by rememberUpdatedState(settings)
    val client = remember(context) { CarrierWebChrome(context, activity, { latestOrigin() }, { latestError(it) }, { latestSettings() }) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { client.permissionsReturned() }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { client.fileReturned(it.resultCode, it.data) }
    client.requestPermissions = { permissions.launch(it) }
    client.chooseFile = { files.launch(it) }
    DisposableEffect(client) { onDispose { client.close() } }
    OverlayDialog(show = client.prompt.isNotEmpty(), title = "Website permissions", onDismissRequest = client::deny) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(client.prompt)
            ConfirmationActions("Continue", client::deny, client::allow)
        }
    }
    return client
}

internal class CarrierWebChrome(private val context: Context, private val activity: Activity?,
    private val currentOrigin: () -> String?, private val error: (String) -> Unit, private val settings: () -> Unit) : WebChromeClient() {
    private data class Pending(val origin: String, val permissions: List<String>, val accept: () -> Unit,
        val reject: () -> Unit, val web: PermissionRequest? = null)
    var prompt by mutableStateOf("")
        private set
    var requestPermissions: (Array<String>) -> Unit = {}
    var chooseFile: (Intent) -> Unit = {}
    private var pending: Pending? = null
    private var permissionFlight: Pending? = null
    private data class FileFlight(val callback: ValueCallback<Array<Uri>>, val origin: String, val capture: Pair<File, Uri>?)
    private var fileFlight: FileFlight? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var fileOrigin: String? = null
    private var capture: Pair<File, Uri>? = null
    private val temporaryFiles = mutableListOf<File>()
    private val permittedSites = mutableMapOf<String, Set<String>>()
    private var closed = false
    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun active(origin: String) = !closed && currentOrigin() == origin

    override fun onPermissionRequest(request: PermissionRequest) {
        val origin = try { httpsOrigin(request.origin.toString()) } catch (_: IllegalArgumentException) { request.deny(); return }
        val permissions = mediaPermissions(request.resources)
        if (!active(origin) || permissions == null || pending != null || permissionFlight != null) { request.deny(); return }
        val accept = {
            if (active(origin) && permissions.all(::granted)) {
                permittedSites[origin] = permittedSites[origin].orEmpty() + permissions
                request.grant(request.resources)
            } else request.deny()
        }
        if (permissions.all(::granted) && permittedSites[origin].orEmpty().containsAll(permissions)) accept()
        else ask(Pending(origin, permissions, accept, { request.deny() }, request))
    }
    override fun onPermissionRequestCanceled(request: PermissionRequest) {
        if (pending?.web === request) { pending = null; prompt = "" }
    }
    private fun ask(value: Pending) {
        pending = value
        val names = value.permissions.joinToString(" and ") { if (it == Manifest.permission.CAMERA) "camera" else "microphone" }
        val rationale = activity != null && value.permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        prompt = "${Uri.parse(value.origin).host} needs $names to complete carrier verification." +
            if (rationale) "Permission was not granted. You can try again or return to the page." else "Continue to grant access, or return to the page."
    }
    fun allow() {
        prompt = ""
        val value = pending ?: return
        if (!active(value.origin)) { deny(); return }
        val missing = value.permissions.filterNot(::granted)
        if (missing.isEmpty()) { pending = null; value.accept() }
        else { permissionFlight = value; requestPermissions(missing.toTypedArray()) }
    }
    fun deny() { val value = pending; pending = null; prompt = ""; value?.reject?.invoke() }
    fun permissionsReturned() {
        val value = permissionFlight ?: return
        permissionFlight = null
        if (pending !== value) return // The old webpage cancelled while the system dialog was open.
        pending = null; prompt = ""
        if (!active(value.origin)) { value.reject(); return }
        val denied = value.permissions.filterNot(::granted)
        if (denied.isEmpty()) value.accept() else {
            value.reject()
            error("Required permissions were not granted. Enable them in app settings and retry; the page is retained.")
            if (activity != null && denied.any { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }) settings()
        }
    }
    override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
        val origin = currentOrigin()
        if (closed || origin == null || fileCallback != null || fileFlight != null || pending != null || permissionFlight != null) { callback.onReceiveValue(null); return true }
        fileCallback = callback; fileOrigin = origin
        val video = params.acceptTypes.any { it.startsWith("video/") }
        if (params.isCaptureEnabled && params.acceptTypes.any { it.startsWith("image/") || it.startsWith("video/") }) {
            val start = { launchCapture(video) }
            if (granted(Manifest.permission.CAMERA)) start()
            else ask(Pending(origin, listOf(Manifest.permission.CAMERA), start, { completeFile(null) }))
        } else launchFile(params.createIntent())
        return true
    }
    private fun launchCapture(video: Boolean) {
        val directory = File(context.cacheDir, "carrier-capture").apply { mkdirs() }
        val file = File.createTempFile("capture-", if (video) ".mp4" else ".jpg", directory)
        val uri = FileProvider.getUriForFile(context, "${Contract.APP}.carrier-files", file)
        capture = file to uri; temporaryFiles += file
        launchFile(Intent(if (video) MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri("Verification file", uri) })
    }
    private fun launchFile(intent: Intent) {
        val callback = fileCallback ?: return
        fileFlight = FileFlight(callback, requireNotNull(fileOrigin), capture)
        try { chooseFile(intent) }
        catch (_: ActivityNotFoundException) { fileFlight = null; completeFile(null); error("No camera or file picker is available. Install a suitable app and try again") }
        catch (_: SecurityException) { fileFlight = null; completeFile(null); error("Camera or file access changed. Grant permission and try again"); settings() }
    }
    fun fileReturned(code: Int, data: Intent?) {
        val flight = fileFlight ?: return
        fileFlight = null
        if (fileCallback !== flight.callback || fileOrigin != flight.origin) return
        val captured = flight.capture
        val uris = if (code != Activity.RESULT_OK) null else FileChooserParams.parseResult(code, data)
            ?: captured?.takeIf { it.first.length() > 0 }?.let { arrayOf(it.second) }
        completeFile(uris?.filter { it.scheme == "content" }?.toTypedArray()?.takeIf { it.isNotEmpty() })
    }
    private fun completeFile(uris: Array<Uri>?) {
        val callback = fileCallback; fileCallback = null
        val allowed = fileOrigin?.let(::active) == true
        fileOrigin = null; capture = null
        callback?.onReceiveValue(uris.takeIf { allowed })
    }
    fun navigation(origin: String?) {
        if (pending?.origin?.let { it != origin } == true) deny()
        if (fileOrigin?.let { it != origin } == true) completeFile(null)
    }
    fun close() {
        closed = true; deny(); completeFile(null)
        temporaryFiles.forEach(File::delete); temporaryFiles.clear()
    }
}
