// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 lilyzhaun
package dev.quicktransfer.app.ipc

import android.os.IBinder
import dev.quicktransfer.app.core.ExecutionMode
import dev.quicktransfer.app.core.HostSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object HostRegistry {
    private class Endpoint(val binder: IBinder, val api: ITransferHost) { lateinit var death: IBinder.DeathRecipient }
    private val endpoints = mutableMapOf<ExecutionMode, Endpoint>()
    private var mode = ExecutionMode.GMS
    private val services = MutableStateFlow<ITransferHost?>(null)
    private val states = MutableStateFlow<Map<ExecutionMode, HostSnapshot>>(emptyMap())
    private val visible = MutableStateFlow(HostSnapshot())
    private val calls = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val connection = services.asStateFlow()
    val snapshots = states.asStateFlow()
    val snapshot = visible.asStateFlow()

    @Synchronized fun selectLocal(local: Boolean) {
        mode = if (local) ExecutionMode.LOCAL else ExecutionMode.GMS
        updateSelection()
    }
    @Synchronized fun hostFor(mode: ExecutionMode): ITransferHost? = endpoints[mode]?.let {
        if (it.binder.isBinderAlive) it.api else null
    }
    private fun updateSelection() {
        services.value = endpoints[mode]?.api
        visible.value = states.value[mode] ?: HostSnapshot(executionMode = mode.name)
    }
    @Synchronized private fun detach(key: ExecutionMode, expected: Endpoint) {
        if (endpoints[key] !== expected) return
        endpoints.remove(key)
        states.value = states.value + (key to (states.value[key] ?: HostSnapshot()).copy(connected = false, executionMode = key.name))
        updateSelection()
    }
    @Synchronized fun register(binder: IBinder, local: Boolean = false) {
        val key = if (local) ExecutionMode.LOCAL else ExecutionMode.GMS
        val previous = endpoints[key]
        if (previous?.binder === binder) return
        previous?.binder?.unlinkToDeath(previous.death, 0)
        val next = Endpoint(binder, ITransferHost.Stub.asInterface(binder))
        next.death = IBinder.DeathRecipient { detach(key, next) }
        binder.linkToDeath(next.death, 0)
        endpoints[key] = next
        updateSelection()
        calls.launch {
            try {
                next.api.observe(object : IHostCallback.Stub() {
                    override fun onSnapshot(snapshot: String) {
                        val decoded = HostSnapshot.parse(snapshot).copy(executionMode = key.name)
                        synchronized(this@HostRegistry) {
                            if (endpoints[key] === next) { states.value = states.value + (key to decoded); updateSelection() }
                        }
                    }
                })
                if (synchronized(this@HostRegistry) { mode == key && endpoints[key] === next }) next.api.refresh()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                detach(key, next)
            }
        }
    }
}
