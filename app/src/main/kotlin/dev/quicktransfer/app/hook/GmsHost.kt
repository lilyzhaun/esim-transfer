// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.*
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Xml
import dev.quicktransfer.app.core.*
import dev.quicktransfer.app.ipc.IHostCallback
import dev.quicktransfer.app.ipc.ITransferHost
import io.github.libxposed.api.XposedModule
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.Executors

internal class GmsHost private constructor(private val context: Context, private val module: XposedModule,
    private val api: Int, private val preferences: SharedPreferences) {
    companion object {
        @Volatile private var instance: GmsHost? = null
        @Synchronized fun start(context: Context, module: XposedModule, api: Int, preferences: SharedPreferences) {
            if (instance == null) instance = GmsHost(context, module, api, preferences).also { it.initialize() }
        }
    }
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "EsimTransfer-worker") }
    private val discovery = Executors.newSingleThreadExecutor { r -> Thread(r, "EsimTransfer-discovery") }
    private val lock = Any()
    private val packageInfo = context.packageManager.getPackageInfo(Contract.GMS, 0)
    private val gms = GmsReflection(context.classLoader, packageInfo.longVersionCode)
    private val carrierProfiles = GmsCarrierProfiles(gms)
    private val transfer = GmsTransferEngine(context, gms, carrierProfiles)
    private val transferTask = ThreadLocal<String>()
    private val localTransport = GmsLocalTransport(context, gms) {
        val id = requireNotNull(transferTask.get())
        val carrierId = current().source.carrierId
        val work: (() -> Unit) -> Unit = { action -> transferTask.set(id)
            try { carrierProfiles.withCarrier(carrierId, action) } finally { transferTask.remove() } }
        work
    }
    private val journal = HostJournal.forGms(context)
    private var compatible = GmsSymbols.supported(packageInfo.longVersionCode)
    @Volatile private var notice = ""
    @Volatile private var sources = emptyList<SourceLine>()
    @Volatile private var catalog = CarrierCatalog()
    private var records = journal.records().map { old ->
        if (old.phase.running) old.afterHostRestart().also(journal::save) else old
    }.associateBy { it.id }.toMutableMap()
    @Volatile private var callback: IHostCallback? = null
    @Volatile private var taskId: String? = null
    private var sourceFingerprint = ""
    private var gate = SubmissionGate()
    private var sourceUi: WeakReference<Any>? = null
    private var promptStarted = false
    private var authenticated = false
    private var definiteRejection = false
    private var downloadResult: Any? = null
    private var pollingOnly = false
    private var activeProfile: Any? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == Contract.DISCOVERY) register() }

    private val binder = object : ITransferHost.Stub() {
        override fun getSnapshot(): String { enforceCaller(); return snapshot() }
        override fun getTaskLog(taskId: String): String {
            enforceCaller()
            val record = synchronized(lock) { requireNotNull(records[taskId]) { "任务不存在" } }
            return journal.taskLog(record).json().toString()
        }
        override fun exportTaskLog(taskId: String): ParcelFileDescriptor {
            enforceCaller()
            val record = synchronized(lock) { requireNotNull(records[taskId]) { "任务不存在" } }
            return journal.export(record)
        }
        override fun observe(client: IHostCallback) { enforceCaller(); callback = client; publish() }
        override fun refresh() { enforceCaller(); discovery.execute { readSources(); readCatalog(); recoverPollingState(); publish() } }
        override fun prepare(request: String): String {
            enforceCaller()
            val parsed = JSONObject(request)
            val target = TargetDevice.parse(parsed.getJSONObject("target"))
            require(target.name.length <= 80 && IdentityRules.valid(target.eid, target.imei)) { "目标身份格式不正确" }
            require(target.details.error() == null) { target.details.error().orEmpty() }
            check(compatible) { "当前 Google Play 服务版本无法连接模块" }
            val sub = subscription(parsed.getInt("subId"))
            val source = sourceFor(sub)
            check(source.supported) { source.reason }
            synchronized(lock) {
                check(records.values.none { it.blocksNewTransfer }) { "已有转移请求等待结果，请先处理当前任务" }
                sourceFingerprint = sub.iccId
                val now = System.currentTimeMillis()
                val record = TransferRecord(UUID.randomUUID().toString(), now, now, source, target)
                journal.save(record)
                journal.detail(record.id, "host-environment", JSONObject().put("gmsVersion", packageInfo.versionName)
                    .put("gmsVersionCode", packageInfo.longVersionCode).put("frameworkApi", api)
                    .put("androidSdk", Build.VERSION.SDK_INT).put("androidRelease", Build.VERSION.RELEASE)
                    .put("buildFingerprint", Build.FINGERPRINT).put("model", Build.MODEL).put("manufacturer", Build.MANUFACTURER)
                    .put("sourceFingerprint", sourceFingerprint).put("input", parsed))
                records[record.id] = record; taskId = record.id
                gate = SubmissionGate(); authenticated = false; promptStarted = false; definiteRejection = false
                publish()
                return record.id
            }
        }
        override fun cancelConfirmation(id: String) {
            enforceCaller()
            synchronized(lock) {
                if (taskId != id || authenticated || records[id]?.phase != Phase.CONFIRM) return
                update(Phase.CANCELLED, "已取消源端验证")
                taskId = null
                closeSourceUi()
            }
        }
        override fun resumePolling(id: String) {
            enforceCaller()
            synchronized(lock) {
                val old = requireNotNull(records[id])
                check(old.canResume && old.pendingAccepted && !old.phase.running) { "该请求当前不能继续查询" }
                check(records.values.none { it.phase.running }) { "已有任务正在进行" }
                taskId = id; gate = SubmissionGate(alreadySubmitted = true); definiteRejection = false
                update(Phase.WAITING, "继续查询原转移请求")
                worker.execute { runTransfer(resume = true) }
            }
        }
        override fun openWebSheet(id: String): String {
            enforceCaller()
            return synchronized(lock) {
                check(compatible) { "请更新模块并重新连接服务" }
                val record = requireNotNull(records[id])
                val sheet = journal.readWebSheet(id)
                check(record.acceptsWebSheet(sheet.id)) { "该验证页面已结束，请刷新任务状态" }
                sheet.json().toString()
            }
        }
        override fun webSheetEvent(id: String, sheetId: String, event: String): String {
            enforceCaller()
            synchronized(lock) { check(records[id]?.acceptsWebSheet(sheetId) == true) { "该验证页面已结束" } }
            journal.detail(id, "web-callback", JSONObject().put("sheetId", sheetId).put("rawEvent", event))
            return handleWebSheetEvent(id, sheetId, WebSheetEvent.parse(event)).also {
                journal.detail(id, "web-callback-result", JSONObject(it.toString()).put("sheetId", sheetId))
            }.toString()
        }
        override fun leaveWebSheet(id: String, sheetId: String) {
            enforceCaller()
            synchronized(lock) {
                val old = records[id] ?: return
                if (old.phase != Phase.ISSUED || old.webSheetId != sheetId) return
                val next = old.copy(webSheetId = "", webSheetHost = "", updatedAt = maxOf(System.currentTimeMillis(), old.updatedAt + 1))
                journal.save(next); records[id] = next
                journal.deleteWebSheet(id); journal.deleteCredential(id); publish()
            }
        }
        override fun endLocalTask(id: String): String {
            enforceCaller()
            synchronized(lock) {
                check(records.values.none { it.phase.running }) { "任务正在进行，请等待当前操作结束" }
                check(records[id]?.canEndLocally == true) { "任务状态已变化，请刷新后重试" }
            }
            // Serialize with the transfer worker, including its final cleanup after a web result.
            return worker.submit<String> {
                synchronized(lock) {
                    check(records.values.none { it.phase.running }) { "任务正在进行，请等待当前操作结束" }
                    val ended = journal.endLocalTask(requireNotNull(records[id]) { "任务不存在" })
                    records[id] = ended
                    if (taskId == id) taskId = null
                    publish()
                    ended.json().toString()
                }
            }.get()
        }
    }

    private fun initialize() {
        preferences.registerOnSharedPreferenceChangeListener(listener)
        if (compatible) {
            try { installHooks() }
            catch (error: ReflectiveOperationException) {
                compatible = false; notice = "当前 Google Play 服务接口暂不兼容，请更新模块后重试"
                module.log(android.util.Log.ERROR, "EsimTransfer", "gms_hooks_failed version=${packageInfo.longVersionCode} ${error.stackTraceToString()}")
            }
        } else notice = "当前 Google Play 服务 ${packageInfo.versionName.orEmpty().substringBefore(' ')} 尚未适配，请更新模块"
        discovery.execute { readSources(); readCatalog(); recoverPollingState(); register() }
    }
    private fun enforceCaller() {
        val expected = preferences.getInt(Contract.APP_UID, -1)
        check(expected >= 10_000 && expected / 100_000 == 0 && Binder.getCallingUid() == expected && expected != Process.myUid()) { "仅模块应用可以调用" }
    }
    private fun register() {
        discovery.execute {
            try {
                context.contentResolver.call(Uri.parse("content://${Contract.AUTHORITY}"), "register", null,
                     Bundle().apply { putInt("protocol", Contract.REGISTRATION_PROTOCOL); putBinder("host", binder) })
            } catch (error: RuntimeException) { module.log(android.util.Log.WARN, "EsimTransfer", "registration_failed ${error.javaClass.simpleName}") }
        }
    }
    private fun snapshot(): String = synchronized(lock) {
        HostSnapshot(true, packageInfo.versionName.orEmpty(), packageInfo.longVersionCode, api, compatible, sources,
            records.values.sortedByDescending { it.createdAt }.take(50), notice, catalog,
            supportsLocalTaskEnd = true, supportsDetailedLogs = true).json().toString()
    }
    private fun publish() {
        publishSnapshot(lock, ::snapshot) { payload ->
            val observer = callback
            if (observer != null) {
                try { observer.onSnapshot(payload) }
                catch (_: RemoteException) { if (callback === observer) callback = null }
            }
        }
    }
    // This call executes under GMS's UID, not the standalone app's permission set.
    @SuppressLint("MissingPermission")
    private fun subscription(id: Int): SubscriptionInfo = requireNotNull(context.getSystemService(SubscriptionManager::class.java).getActiveSubscriptionInfo(id)) { "源线路已不可用" }
    private fun sourceFor(sub: SubscriptionInfo) = transfer.source(sub)
    private fun readSources() {
        try {
            @Suppress("UNCHECKED_CAST")
            val available = (gms.call(context.getSystemService(SubscriptionManager::class.java),
                "getAvailableSubscriptionInfoList") as? List<SubscriptionInfo>).orEmpty()
            @Suppress("UNCHECKED_CAST")
            val visible = if (compatible) (gms.static("cejs", "n", available) as? List<SubscriptionInfo>).orEmpty() else available
            sources = visible.map { sub ->
                if (compatible) sourceFor(sub) else SourceLine(sub.subscriptionId, sub.simSlotIndex,
                    sub.carrierId, sub.carrierName.toString(), false, false, false, 0,
                    "当前 Google Play 服务版本无法连接模块", country = sub.countryIso.orEmpty())
            }
            if (compatible) notice = ""
        } catch (error: Exception) { notice = "无法读取本机线路，请重新连接服务后刷新" }
    }
    private fun readCatalog() {
        if (!compatible) { catalog = CarrierCatalog(message = notice); return }
        catalog = catalog.copy(loading = true, message = "")
        publish()
        catalog = try { GmsCarrierCatalog(context, gms).read() }
        catch (error: Exception) { catalog.copy(loading = false, message = "无法更新运营商列表，请重新连接服务后刷新") }
    }
    private fun recoverPollingState() = synchronized(lock) {
        records = records.mapValues { (_, record) ->
            if (!record.pendingAccepted || record.phase !in listOf(Phase.INTERRUPTED, Phase.UNCERTAIN) || !compatible) record
            else try {
                journal.readProfile(record.id, gms)
                record.copy(canResume = true, message = "可继续查询原请求，不会重新提交转移")
            } catch (error: Exception) { record.copy(canResume = false, message = "暂时无法继续查询，请向运营商核对转移结果") }
        }.toMutableMap()
    }
    private fun isTransferCall(): Boolean {
        val id = transferTask.get() ?: return false
        check(id == taskId) { "原转移任务已结束" }
        localTransport.ensureActive()
        return true
    }
    private fun current(): TransferRecord = synchronized(lock) { requireNotNull(records[transferTask.get() ?: taskId]) }
    private fun captureDownload(result: Any?, fetcher: Any) = synchronized(lock) {
        if (transferTask.get() != null && transferTask.get() == taskId && localTransport.isActive()) {
            downloadResult = result
            // Source-executed flows can rotate the auth token before returning their websheet.
            val profile = activeProfile
            val token = gms.text(fetcher, "x")
            if (profile != null && token.isNotBlank()) {
                gms.set(requireNotNull(gms.field(profile, "c")), "h", token)
                journal.saveProfile(current().id, profile as Parcelable)
            }
        }
    }
    private fun update(phase: Phase, message: String, change: (TransferRecord) -> TransferRecord = { it }) = synchronized(lock) {
        val id = transferTask.get() ?: taskId
        if (id != taskId || !localTransport.isActive()) return@synchronized
        val old = records[id] ?: return@synchronized
        val next = change(old).copy(phase = phase, message = message, updatedAt = maxOf(System.currentTimeMillis(), old.updatedAt + 1))
        journal.save(next); records[next.id] = next
        publish()
    }
    private fun owns(a: Any): Boolean {
        val intent = gms.call(a, "getIntent") as? Intent ?: return false
        return taskId != null && intent.getStringExtra(Contract.TASK_EXTRA) == taskId
    }
    private fun closeSourceUi() {
        val source = sourceUi?.get(); sourceUi = null
        if (source != null) main.post { gms.call(source, "finish") }
    }
    private fun returnToApp() {
        main.post {
             val intent = context.packageManager.getLaunchIntentForPackage(Contract.APP) ?: Intent().setClassName(Contract.APP, Contract.MAIN_ACTIVITY)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            sourceUi?.get()?.let { (gms.call(it, "getContainerActivity") as Activity).startActivity(intent) }
        }
    }
    private fun requestConfirmation(a: Any) {
        synchronized(lock) {
            if (!owns(a) || promptStarted || current().phase != Phase.CONFIRM) return
            promptStarted = true; sourceUi = WeakReference(a)
        }
        main.post {
            try { gms.call(a, "b") }
            catch (error: Exception) { fail(error) }
        }
    }

    private fun installHooks() {
        carrierProfiles.installHooks(module)
        localTransport.installHooks(module)
        GmsHttpTrace(gms, journal) { transferTask.get() }.install(module)
        val source = Contract.SOURCE_IMPL
        val onCreate = gms.declared(source, "onCreate", Bundle::class.java)
        val onResume = gms.declared(source, "onResume")
        val success = gms.declared("ceiz", "c", gms.type("avf"))
        val authError = gms.declared("ceiz", "a", Int::class.javaPrimitiveType!!, CharSequence::class.java)
        val transport = gms.declared("wwk", "b", String::class.java, gms.type("wwn"), gms.type("wwj"))
        val auth = gms.declared("wwk", "a", List::class.java, gms.type("wwn"))
        val fetcherFactory = gms.declared("cegv", "a", Context::class.java,
            gms.type("com.google.android.gms.esim.ProfileTransferData"), android.net.Network::class.java, gms.type("cdzl"), String::class.java)
        module.hook(fetcherFactory).intercept { chain ->
            chain.proceed().also { fetcher ->
                if (isTransferCall()) transfer.bindTarget(requireNotNull(fetcher), requireNotNull(chain.args[1]))
            }
        }
        module.hook(gms.declared("cehf", "h", Context::class.java, android.telephony.TelephonyManager::class.java,
            gms.type("com.google.android.gms.esim.ProfileTransferData"))).intercept { chain ->
            // This helper reads the receiving device's IMEI, including token refresh paths.
            if (isTransferCall()) current().target.imei else chain.proceed()
        }
        module.hook(gms.declared("cehf", "d")).intercept { chain ->
            chain.proceed().also { captureDownload(it, requireNotNull(chain.thisObject)) }
        }
        module.hook(gms.declared("cehf", "c", Int::class.javaPrimitiveType!!)).intercept { chain ->
            chain.proceed().also { captureDownload(it, requireNotNull(chain.thisObject)) }
        }
        module.hook(gms.declared("cegz", "a")).intercept { chain ->
            if (isTransferCall()) submitted()
            chain.proceed()
        }
        module.hook(onCreate).intercept { chain ->
            val result = chain.proceed()
            val a = requireNotNull(chain.thisObject)
            val intent = gms.call(a, "getIntent") as Intent
            if (intent.getBooleanExtra(Contract.DISCOVER_EXTRA, false)) { register(); gms.call(a, "finish") }
            result
        }
        module.hook(onResume).intercept { chain ->
            val result = chain.proceed()
            try { requestConfirmation(requireNotNull(chain.thisObject)) }
            catch (error: Exception) { if (taskId != null) fail(error) }
            result
        }
        module.hook(gms.inherited(source, "onNewIntent", Intent::class.java)).intercept { chain ->
            val result = chain.proceed()
            val a = requireNotNull(chain.thisObject)
            val intent = chain.args[0] as Intent
            if (gms.type(source).isInstance(a)) {
                if (intent.getBooleanExtra(Contract.DISCOVER_EXTRA, false)) register()
                else if (taskId != null && intent.getStringExtra(Contract.TASK_EXTRA) == taskId) {
                    if (gms.number(a, "b") == 3L) { gms.call(a, "setIntent", intent); requestConfirmation(a) }
                    else fail(IllegalStateException("GMS 已有其他转移页面，请结束后重试"))
                }
            }
            result
        }
        module.hook(gms.declared(source, "onUserLeaveHint")).intercept { chain ->
            if (authenticated && owns(requireNotNull(chain.thisObject))) null else chain.proceed()
        }
        module.hook(success).intercept { chain ->
            val a = requireNotNull(gms.field(requireNotNull(chain.thisObject), "a"))
            if (!owns(a)) chain.proceed() else {
                synchronized(lock) {
                    if (!authenticated && current().phase == Phase.CONFIRM) {
                        authenticated = true; gate.authenticate()
                        update(Phase.AUTHENTICATING, "源设备验证通过，正在鉴权")
                        worker.execute { runTransfer(false) }
                        returnToApp()
                    }
                }
                null
            }
        }
        module.hook(authError).intercept { chain ->
            val a = requireNotNull(gms.field(requireNotNull(chain.thisObject), "a"))
            if (!owns(a)) chain.proceed() else {
                synchronized(lock) { if (!authenticated) {
                    update(Phase.CANCELLED, "源设备验证未完成（${chain.args[0]}）")
                    closeSourceUi(); taskId = null
                } }
                null
            }
        }
        module.hook(auth).intercept { chain ->
            if (isTransferCall()) {
                carrierProfiles.adaptClient(requireNotNull(chain.thisObject))
                carrierProfiles.adaptRequest(requireNotNull(chain.args[1]))
                update(Phase.AUTHENTICATING, "正在验证本机源线路")
            }
            chain.proceed()
        }
        module.hook(transport).intercept { chain ->
            if (!isTransferCall()) return@intercept chain.proceed()
            val op = requireNotNull(chain.args[2])
            val sourceDevice = activeProfile?.let { gms.field(it, "a") }
            val sourceEid = sourceDevice?.let { gms.text(it, "c") }.orEmpty()
            val sourceImei = sourceDevice?.let { gms.text(it, "b") }.orEmpty()
            carrierProfiles.adaptClient(requireNotNull(chain.thisObject))
            carrierProfiles.adaptRequest(requireNotNull(chain.args[1]), op, sourceFingerprint, sourceEid, sourceImei, current().target)
            val name = gms.text(op, "a")
            when (name) {
                "CheckEligibility" -> update(Phase.ELIGIBILITY, "正在检查线路资格")
                "AcquireTemporaryToken" -> update(Phase.TOKEN, "正在获取本次转移凭据")
                "ManageSubscription" -> {
                    val record = current()
                    transfer.checkTarget(requireNotNull(chain.args[1]), op, record.target)
                    submitted()
                }
                "AcquireConfiguration" -> update(Phase.WAITING, "正在查询运营商下载信息")
            }
            val result = chain.proceed()
            isTransferCall()
            val params = parameters(result as String)
            journal.appendLog(current().id, TaskLogEvents.native(name, params))
            synchronized(lock) {
                if (transferTask.get() == taskId && localTransport.isActive()) {
                    if (name == "ManageSubscription") {
                        definiteRejection = params["OperationResult"]?.let { it != "1" } ?: false
                        if (params["OperationResult"] == "1" && params["SubscriptionResult"] == "4") update(Phase.WAITING, "请求已受理，等待运营商发码", { it.copy(pendingAccepted = true) })
                    }
                    if (name == "AcquireConfiguration" && params["ServiceStatus"] == "2") {
                        val minutes = params["PollingInterval"]?.toLongOrNull() ?: 1L
                        update(Phase.WAITING, "运营商处理中，约 $minutes 分钟后再次查询", { it.copy(nextPollAt = System.currentTimeMillis() + minutes * 60_000) })
                    }
                }
            }
            result
        }
    }
    private fun parameters(body: String): Map<String, String> {
        val parser = Xml.newPullParser(); parser.setInput(StringReader(body))
        val result = mutableMapOf<String, String>()
        val wanted = setOf("OperationResult", "SubscriptionResult", "ServiceStatus", "PollingInterval")
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "parm") {
                val name = parser.getAttributeValue(null, "name")
                if (name in wanted) result[name] = parser.getAttributeValue(null, "value").orEmpty()
            }
            parser.next()
        }
        return result
    }
    private fun runTransfer(resume: Boolean, webManage: Boolean = false, keepWebSheet: Boolean = false) {
        transferTask.set(requireNotNull(taskId))
        downloadResult = null
        pollingOnly = resume && !webManage
        try { carrierProfiles.withCarrier(current().source.carrierId) {
            val record = current()
            val profile = if (resume) journal.readProfile(record.id, gms) else {
                check(gate.take()) { "本次源端确认已经使用，请查看当前任务" }
                val sub = subscription(record.source.subId)
                check(sub.iccId == sourceFingerprint) { "源线路已变化" }
                transfer.profile(sub, record.target).also { journal.saveProfile(record.id, it as Parcelable) }
            }
            activeProfile = profile
            sourceFingerprint = gms.text(requireNotNull(gms.field(profile, "b")), "a")
            if (resume && !webManage) finishDownload(transfer.resume(profile))
            else {
                // webManage is an explicit TS.43 callback continuing a previously confirmed, saved task.
                val result = localTransport.fetch(profile, authenticated || webManage && record.submitted)
                journal.saveResponse(record.id, result as Parcelable)
                finishNativeResult(result)
            }
            if (keepWebSheet && current().phase == Phase.INTERRUPTED) update(Phase.NEEDS_INPUT, "请继续在运营商网页完成验证", { it.copy(canResume = false) })
        } } catch (error: Exception) { fail(error) }
        finally { activeProfile = null; downloadResult = null; pollingOnly = false; transferTask.remove(); closeSourceUi() }
    }
    private fun submitted() = synchronized(lock) {
        // One app invocation, with authentication/retries inside that invocation owned by GMS.
        // A restored query must never silently turn into a new transfer.
        check(!pollingOnly) { "原请求需要重新申请转移，已停止继续查询" }
        check(transferTask.get() == taskId && localTransport.isActive()) { "原转移任务已结束" }
        definiteRejection = false
        // Commit the in-flight marker before dispatch, even if the target times out immediately after this check.
        val old = current()
        val next = old.copy(phase = Phase.SUBMITTING, message = "正在申请目标设备的 eSIM", submitted = true,
            updatedAt = maxOf(System.currentTimeMillis(), old.updatedAt + 1))
        journal.save(next); records[next.id] = next; publish()
    }
    private fun finishNativeResult(result: Any) {
        val outcome = NativeCredential.read(gms, result, downloadResult)
        if (outcome.issued) {
            update(Phase.ISSUED, if (outcome.code.isBlank()) "已获取服务器地址，尚无完整激活码" else "已获取下载信息，请在目标设备下载安装",
                { it.copy(activationCode = outcome.code, smdpAddresses = outcome.addresses, canResume = false, nextPollAt = 0) })
            journal.deleteCredential(current().id)
            return
        }
        if (outcome.needsInput) {
            val sheet = gms.field(result, "e")
            if (sheet != null && gms.text(sheet, "a").isNotBlank()) saveWebSheet(gms.text(sheet, "a"), gms.text(sheet, "b"), gms.text(sheet, "c"))
            else update(Phase.NEEDS_INPUT, "运营商要求进一步确认，当前任务已保存")
            return
        }
        if (outcome.delayed && current().submitted) {
            update(Phase.INTERRUPTED, "运营商仍在处理，可继续查询原请求", { it.copy(pendingAccepted = true) })
            recoverPollingState(); publish()
            return
        }
        fail(IllegalStateException(outcome.error))
    }
    private fun finishDownload(result: Any) {
        val code = gms.text(result, "a")
        val addresses = (gms.field(result, "b") as List<*>).map { it.toString() }
        val needsInput = gms.field(result, "j") == true || gms.text(result, "g").isNotEmpty()
        val delayed = gms.field(result, "e") == true
        when {
            needsInput -> {
                if (gms.text(result, "g").isNotBlank()) saveWebSheet(gms.text(result, "g"), gms.text(result, "h"), gms.text(result, "i"))
                else update(Phase.NEEDS_INPUT, "运营商要求进一步确认，当前任务已保存")
                return
            }
            delayed && current().submitted -> {
                update(Phase.INTERRUPTED, "运营商仍在处理，可继续查询原请求", { it.copy(pendingAccepted = true) })
                recoverPollingState(); publish()
                return
            }
            code.isNotEmpty() || addresses.isNotEmpty() -> update(Phase.ISSUED,
                if (code.isNotEmpty()) "已获取下载信息，请在目标设备下载安装" else "已获取服务器地址，尚无完整激活码",
                { it.copy(activationCode = code, smdpAddresses = addresses, canResume = false, nextPollAt = 0) })
            else -> { fail(IllegalStateException("Google Play 服务未返回下载信息")); return }
        }
        if (current().webSheetId.isEmpty()) journal.deleteCredential(current().id)
    }
    private fun saveWebSheet(url: String, data: String, type: String) {
        val sheet = WebSheetRequest.create(url, carrierProfiles.webUserData(data, current().target), type)
        journal.saveWebSheet(current().id, sheet)
        update(Phase.NEEDS_INPUT, "请打开运营商网页完成验证", {
            it.copy(webSheetId = sheet.id, webSheetHost = sheet.host, canResume = false)
        })
    }

    private fun handleWebSheetEvent(id: String, sheetId: String, event: WebSheetEvent): JSONObject = synchronized(lock) {
        val record = requireNotNull(records[id])
        check(record.acceptsWebSheet(sheetId)) { "该验证页面已结束，请返回任务查看结果" }
        check(records.values.none { it.phase.running }) { "正在处理上一项操作，请稍后再试" }
        journal.appendLog(id, TaskLogEntry(System.currentTimeMillis(), "运营商网页回调", event.action.name))
        taskId = id
        fun response(close: Boolean = false, message: String = "") = JSONObject().put("close", close).put("message", message)
        fun closeSheet() {
            update(current().phase, current().message, { it.copy(webSheetId = "", webSheetHost = "") })
            journal.deleteWebSheet(id)
        }
        fun continueTask(manage: Boolean, keepOpen: Boolean): JSONObject {
            if (!keepOpen) closeSheet()
            definiteRejection = false
            update(Phase.WAITING, if (manage) "正在继续处理运营商请求" else "正在查询原请求的下载信息", { it.copy(pendingAccepted = true, canResume = false) })
            worker.execute { runTransfer(resume = true, webManage = manage, keepWebSheet = keepOpen) }
            return response(!keepOpen)
        }
        when (event.action) {
            WebAction.ACTIVATION -> {
                check(record.activationCode.isEmpty() || ActivationCodes.standard(record.activationCode) == event.value) { "运营商返回了不同的激活码，请核对原任务" }
                update(Phase.ISSUED, "已获取下载信息，请在目标设备下载安装", { it.copy(activationCode = event.value, canResume = false, nextPollAt = 0) })
                response(message = "激活码已保存，可返回任务查看")
            }
            WebAction.ADDRESS -> {
                val merged = record.withWebDownloadAddress(event.value, event.iccid)
                update(merged.phase, merged.message) { merged }
                response(message = "下载服务器地址已保存")
            }
            WebAction.FINISH -> {
                if (record.phase == Phase.ISSUED) { closeSheet(); journal.deleteCredential(id); response(true) }
                else continueTask(event.value == "ManageSubscription", false)
            }
            WebAction.DISMISS -> {
                if (record.phase != Phase.ISSUED) update(Phase.CANCELLED, "运营商网页已结束本次验证", { it.copy(canResume = false) })
                closeSheet(); journal.deleteCredential(id); response(true)
            }
            WebAction.CHECK_STATUS -> {
                if (record.phase == Phase.ISSUED) response(message = "下载信息已保存，请在目标设备上添加 eSIM")
                else continueTask(false, true)
            }
            WebAction.DELETE_TOKEN -> {
                val profile = journal.readProfile(id, gms)
                val metadata = requireNotNull(gms.field(profile, "c"))
                gms.set(metadata, "h", ""); gms.set(metadata, "i", 0L)
                journal.saveProfile(id, profile as Parcelable)
                response(message = "网页已退出鉴权，后续请求将重新验证源线路")
            }
            WebAction.SELECTION -> {
                if (record.phase == Phase.ISSUED) return@synchronized response(message = "本任务已取得下载信息，请返回任务查看")
                val profile = journal.readProfile(id, gms)
                val info = requireNotNull(gms.field(profile, "b")); val device = requireNotNull(gms.field(profile, "a"))
                require(event.iccid.isNotEmpty() || event.imei.isNotEmpty()) { "运营商没有返回所选线路" }
                require((event.iccid.isEmpty() || event.iccid == gms.text(info, "a")) && (event.imei.isEmpty() || event.imei == gms.text(device, "b"))) { "网页所选线路与当前任务不一致" }
                continueTask(true, false)
            }
            WebAction.DELETE_PROFILE -> response(message = "运营商要求释放源线路，当前任务已保留，请向运营商确认后续步骤")
        }
    }
    private fun fail(error: Exception) {
        val record = synchronized(lock) { records[taskId] } ?: return
        journal.failure(record.id, error)
        journal.appendLog(record.id, TaskLogEvents.failure(error))
        val phase = when {
            !record.submitted || definiteRejection -> Phase.FAILED
            record.pendingAccepted -> Phase.INTERRUPTED
            else -> Phase.UNCERTAIN
        }
        val message = when (phase) {
            Phase.INTERRUPTED -> "查询中断，可尝试继续原请求"
            Phase.UNCERTAIN -> "请求已提交但结果未确认，请先向运营商核对"
            else -> if (error is IllegalStateException || error is IllegalArgumentException) error.message.orEmpty()
                else "转移服务未完成操作，请刷新任务状态"
        }
        update(phase, message)
        if (phase == Phase.INTERRUPTED) { recoverPollingState(); publish() }
        else if (phase == Phase.FAILED) journal.deleteCredential(record.id)
        closeSourceUi()
    }
}
