// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import dev.quicktransfer.app.core.Contract

class HostRegistrationProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val pm = requireNotNull(context).packageManager
        val uid = Binder.getCallingUid()
        val expected = pm.getPackageUid(Contract.GMS, 0)
        if (uid != expected || uid == Process.myUid() || uid / 100_000 != 0 || pm.getPackagesForUid(uid)?.contains(Contract.GMS) != true) throw SecurityException("GMS owner only")
        require(method == "register" && arg == null && extras?.getInt("protocol") == 1)
        HostRegistry.register(requireNotNull(extras?.getBinder("host")))
        return Bundle().apply { putBoolean("registered", true) }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor = throw SecurityException("No data access")
    override fun getType(uri: Uri): String = throw SecurityException("No data access")
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw SecurityException("No writes")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw SecurityException("No writes")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw SecurityException("No writes")
}
