// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app

import android.app.Application
import dev.quicktransfer.app.data.LocalStore
import dev.quicktransfer.app.data.TaskLogStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TransferApplication : Application(), XposedServiceHelper.OnServiceListener {
    val library by lazy { LocalStore(this) }
    val taskLogs by lazy { TaskLogStore(this) }
    val sharedData by lazy { dev.quicktransfer.app.data.SharedUserData(this, library, taskLogs) }
    companion object {
        private val mutable = MutableStateFlow<XposedService?>(null)
        val framework = mutable.asStateFlow()
    }
    override fun onCreate() {
        super.onCreate()
        sharedData.start(); XposedServiceHelper.registerListener(this)
    }
    override fun onServiceBind(service: XposedService) { mutable.value = service }
    override fun onServiceDied(service: XposedService) { if (mutable.value === service) mutable.value = null }
}
