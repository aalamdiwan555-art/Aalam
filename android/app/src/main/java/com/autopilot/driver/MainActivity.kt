package com.autopilot.driver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.autopilot.driver.service.AutopilotService
import com.autopilot.driver.service.FloatingPanelService
import com.autopilot.driver.ui.AutopilotScreen
import com.autopilot.driver.ui.AutopilotViewModel
import com.autopilot.driver.ui.theme.AutopilotTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AutopilotViewModel by viewModels {
        AutopilotViewModel.Factory(applicationContext)
    }
    private val runtimeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AutopilotService.ACTION_UPDATE) viewModel.onRuntimeUpdate(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(AutopilotService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(runtimeReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(runtimeReceiver, filter)
        setContent {
            AutopilotTheme {
                val state by viewModel.uiState.collectAsState()
                var showAccessibilityExplanation by remember { mutableStateOf(false) }
                val projectionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                        viewModel.startWithProjection(result.resultCode, result.data!!)
                    }
                }
                LaunchedEffect(state.overlayEnabled) {
                    if (state.overlayEnabled && Settings.canDrawOverlays(this@MainActivity)) {
                        startService(Intent(this@MainActivity, FloatingPanelService::class.java))
                    } else if (!state.overlayEnabled) {
                        stopService(Intent(this@MainActivity, FloatingPanelService::class.java))
                    }
                }
                AutopilotScreen(
                    state = state,
                    onMinimumChanged = viewModel::onMinimumChanged,
                    onMaximumChanged = viewModel::onMaximumChanged,
                    onStart = {
                        if (viewModel.validateAndSave()) {
                            if (isAutopilotAccessibilityEnabled()) {
                                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                projectionLauncher.launch(manager.createScreenCaptureIntent())
                            } else {
                                showAccessibilityExplanation = true
                            }
                        }
                    },
                    onPause = viewModel::pause,
                    onStop = viewModel::stop,
                    onOverlayChanged = { enabled ->
                        if (enabled && !Settings.canDrawOverlays(this@MainActivity)) {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        } else {
                            viewModel.setOverlayEnabled(enabled)
                        }
                    },
                )
                if (showAccessibilityExplanation) {
                    AlertDialog(
                        onDismissRequest = { showAccessibilityExplanation = false },
                        title = { androidx.compose.material3.Text("Enable authorized interaction") },
                        text = {
                            androidx.compose.material3.Text(
                                "Autopilot needs Android Accessibility permission to dispatch a tap only after a price and target match. No action runs without this permission."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showAccessibilityExplanation = false
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }) { androidx.compose.material3.Text("OPEN SETTINGS") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAccessibilityExplanation = false }) {
                                androidx.compose.material3.Text("CANCEL")
                            }
                        },
                    )
                }
            }
        }
    }

    private fun isAutopilotAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = "$packageName/${com.autopilot.driver.automation.AutopilotAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    override fun onDestroy() {
        unregisterReceiver(runtimeReceiver)
        super.onDestroy()
    }
}