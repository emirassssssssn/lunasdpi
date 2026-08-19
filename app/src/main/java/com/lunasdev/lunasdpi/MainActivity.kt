package com.lunasdev.lunasdpi

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lunasdev.lunasdpi.service.AppLaunchWatcher
import com.lunasdev.lunasdpi.service.ProtectionStartRequest
import com.lunasdev.lunasdpi.ui.LunaApp
import com.lunasdev.lunasdpi.ui.theme.LunaDpiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureStartRequest(intent)
        enableEdgeToEdge()
        setContent {
            LunaDpiTheme {
                LunaApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppLaunchWatcher.setUiVisible(true)
        applyA11yPerf()
    }

    override fun onPause() {
        AppLaunchWatcher.setUiVisible(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureStartRequest(intent)
    }

    private fun applyA11yPerf() {
        window.decorView.importantForAccessibility =
            if (AppLaunchWatcher.isEnabled(this)) {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            }
    }

    private fun captureStartRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(AppLaunchWatcher.EXTRA_START_PROTECTION, false) == true) {
            ProtectionStartRequest.arm()
        }
    }
}
