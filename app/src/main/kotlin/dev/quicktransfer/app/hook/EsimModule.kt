// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.hook

import android.app.Application
import android.content.Context
import android.os.Process
import android.util.Log
import dev.quicktransfer.app.core.Contract
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class EsimModule : XposedModule() {
    private var processName = ""
    private var installed = false
    override fun onModuleLoaded(param: ModuleLoadedParam) { processName = param.processName }
    override fun onPackageReady(param: PackageReadyParam) {
        if (installed || param.packageName != Contract.GMS || processName != Contract.PROCESS || Process.myUid() / 100_000 != 0) return
        installed = true
        hook(Application::class.java.getDeclaredMethod("attach", Context::class.java)).intercept { chain ->
            val result = chain.proceed()
            val app = chain.thisObject as Application
            try { GmsHost.start(app, this, apiVersion, getRemotePreferences(Contract.PREFS)) }
            catch (error: Exception) { log(Log.ERROR, "EsimTransfer", "host_init_failed ${error.javaClass.simpleName}") }
            result
        }
    }
}
