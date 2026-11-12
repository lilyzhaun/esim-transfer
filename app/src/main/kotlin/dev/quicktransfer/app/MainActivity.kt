// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.quicktransfer.app.ui.TransferUi
import dev.quicktransfer.app.local.LocalSimBridge

class MainActivity : ComponentActivity() {
    override fun onStart() { super.onStart(); LocalSimBridge.restoreAuthorized() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent { TransferUi() }
    }
}
