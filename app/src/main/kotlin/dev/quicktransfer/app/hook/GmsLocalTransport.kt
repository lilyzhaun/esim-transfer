// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.hook

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import dev.quicktransfer.app.core.awaitNativeReply
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** In-process transport for GMS's native target service and source message handler. */
internal class GmsLocalTransport(private val context: Context, private val gms: GmsReflection,
    private val captureTaskContext: () -> ((() -> Unit) -> Unit) = { { action -> action() } }) {
    private val current = ThreadLocal<Session>()
    private val sourceRole = ThreadLocal<Boolean>()
    private val endpoints = Collections.synchronizedMap(WeakHashMap<Any, (Any) -> Any>())
    private val closedEndpoint: (Any) -> Any = { error("原转移会话已结束") }
    fun ensureActive() { check(current.get()?.closed != true) { "原转移会话已结束" } }
    fun isActive() = current.get()?.closed != true

    fun installHooks(module: XposedModule) {
        module.hook(gms.declared("cdvd", "a")).intercept { chain -> current.get()?.state ?: chain.proceed() }
        module.hook(gms.declared("cdut", "h", Context::class.java)).intercept { chain -> current.get()?.d2d ?: chain.proceed() }
        module.hook(gms.declared("cehp", "e", gms.type("com.google.android.gms.esim.ProfileTransferData"))).intercept { chain ->
            // This profile already contains the user's real target identity, not this host's eUICC.
            if (current.get() != null) chain.args[0] else chain.proceed()
        }
        module.hook(gms.declared("cdwe", "a", gms.type("com.google.android.gms.esim.internal.MessagePayload"))).intercept { chain ->
            val endpoint = requireNotNull(chain.thisObject)
            val exchange = endpoints[endpoint]
            if (exchange == null) chain.proceed() else exchange(requireNotNull(chain.args[0]))
        }
    }

    fun fetch(profile: Any, confirmed: Boolean): Any {
        check(confirmed) { "请先完成源设备身份验证" }
        val session = Session()
        current.set(session)
        var returned = false
        try {
            session.initialize()
            // The full native service owns dispatch, credentials, reauthentication and WebSheet results.
            return requireNotNull(gms.call(gms.create("cdzm", context), "c", profile)).also { returned = true }
        } finally {
            current.remove()
            sourceRole.remove()
            session.close(interrupt = !returned)
        }
    }

    private inner class Session {
        private val sourceState = gms.create("cdvd")
        private val targetState = gms.create("cdvd")
        val state: Any get() = if (sourceRole.get() == true) sourceState else targetState
        @Volatile var closed = false
        var d2d: Any? = null
        private var source: Any? = null
        private var target: Any? = null
        private val executors = mutableListOf<ExecutorService>()
        private val sourceExecutor = Executors.newSingleThreadExecutor { Thread(it, "EsimTransfer-native-source") }
        private val runTaskContext = captureTaskContext()
        @Volatile private var pending: CompletableFuture<Any>? = null

        fun initialize() {
            gms.call(sourceState, "n", 2); gms.set(sourceState, "g", 1)
            gms.call(targetState, "n", 1); gms.set(targetState, "g", 1)
            gms.set(sourceState, "c", "quicktransfer-local-target")
            gms.set(targetState, "c", "quicktransfer-local-source")
            gms.call(sourceState, "l", gms.call(targetState, "d"))
            gms.call(targetState, "l", gms.call(sourceState, "d"))
            val connector = gms.create("cdzc", context, null)
            val targetEndpoint = requireNotNull(gms.field(connector, "e"))
            target = targetEndpoint
            executors += gms.field(connector, "g") as ExecutorService
            executors += gms.field(targetEndpoint, "i") as ExecutorService
            d2d = gms.create("cdut", context).also { gms.set(it, "d", 1); gms.set(it, "c", connector) }
            val sourceEndpoint = gms.create("cdwe", null, context)
            source = sourceEndpoint
            executors += gms.field(sourceEndpoint, "i") as ExecutorService
            endpoints[sourceEndpoint] = { exchange(sourceEndpoint, it) }
            endpoints[targetEndpoint] = { exchange(targetEndpoint, it) }
            sourceRole.set(true)
            // Forward the actual native source confirmation to GMS's native PIN-response handler.
            gms.call(sourceEndpoint, "h", message(10), true)
            sourceRole.remove()
        }

        fun exchange(endpoint: Any, request: Any): Any {
            if (endpoint === source) {
                // Native cdwe.q normally negotiates this on receipt, including the PIN reply.
                synchronized(this) {
                    val receiver = requireNotNull(target)
                    gms.set(receiver, "k", maxOf(gms.number(receiver, "k"), gms.number(request, "a")).toInt())
                }
                pending?.complete(request)
                return message(20)
            }
            val reply = CompletableFuture<Any>()
            pending = reply
            try {
                val timeout = gms.call(requireNotNull(target), "E", request) as Long
                return awaitNativeReply(reply, timeout, sourceExecutor) { runTaskContext {
                    current.set(this); sourceRole.set(true)
                    val parcel = Parcel.obtain()
                    try {
                        ensureActive()
                        (request as Parcelable).writeToParcel(parcel, 0)
                        val payload = gms.static("eclc", "b", parcel.marshall())
                        gms.call(requireNotNull(source), "q", "quicktransfer-local-target", payload)
                    } finally { parcel.recycle(); sourceRole.remove(); current.remove() }
                } }
            } finally { pending = null }
        }

        private fun message(type: Int): Any = gms.create("cebe").let { builder ->
            gms.call(builder, "c"); gms.set(builder, "a", type); requireNotNull(gms.call(builder, "a"))
        }

        fun close(interrupt: Boolean) {
            closed = true
            (executors + sourceExecutor).forEach { if (interrupt) it.shutdownNow() else it.shutdown() }
            // A late native callback must not fall through into the real Nearby sender.
            // Weak keys release these closed-endpoint markers when GMS releases its callbacks.
            source?.let { endpoints[it] = closedEndpoint }; target?.let { endpoints[it] = closedEndpoint }
        }
    }
}
