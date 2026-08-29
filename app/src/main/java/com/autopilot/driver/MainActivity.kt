package com.autopilot.driver

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.autopilot.driver.automation.AutopilotAccessibilityService
import com.autopilot.driver.service.AutopilotService
import com.autopilot.driver.ui.AutopilotScreen
import com.autopilot.driver.ui.AutopilotViewModel
import com.autopilot.driver.ui.theme.AutopilotTheme
import kotlinx.coroutines.flow.MutableStateFlow

private enum class PermissionPrompt {
    ACCESSIBILITY,
    OVERLAY,
    START_ACCESSIBILITY,
    START_OVERLAY,
}

class MainActivity : ComponentActivity() {
    private val viewModel: AutopilotViewModel by viewModels {
        AutopilotViewModel.Factory(applicationContext)
    }
    private val permissionPrompt = MutableStateFlow<PermissionPrompt?>(null)
    private val projectionRequest = MutableStateFlow(false)
    private val preferences by lazy {
        getSharedPreferences("autopilot_onboarding", MODE_PRIVATE)
    }
    private var receiverRegistered = false
    private var pendingStart = false

    private val runtimeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AutopilotService.ACTION_UPDATE) {
                viewModel.onRuntimeUpdate(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(AutopilotService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(runtimeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(runtimeReceiver, filter)
        }
        receiverRegistered = true

        setContent {
            AutopilotTheme {
                val state by viewModel.uiState.collectAsState()
                val prompt by permissionPrompt.collectAsState()
                val shouldLaunchProjection by projectionRequest.collectAsState()
                val projectionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                        pendingStart = false
                        viewModel.startWithProjection(result.resultCode, result.data!!)
                    } else {
                        pendingStart = false
                        viewModel.onStartCancelled()
                    }
                }

                LaunchedEffect(Unit) {
                    continueFirstRunPermissions()
                }
                LaunchedEffect(shouldLaunchProjection) {
                    if (shouldLaunchProjection) {
                        projectionRequest.value = false
                        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        projectionLauncher.launch(manager.createScreenCaptureIntent())
                    }
                }

                AutopilotScreen(
                    state = state,
                    onMinimumChanged = viewModel::onMinimumChanged,
                    onMaximumChanged = viewModel::onMaximumChanged,
                    onStart = {
                        if (viewModel.validateAndSave()) {
                            pendingStart = true
                            continuePendingStart()
                        }
                    },
                    onStop = viewModel::stop,
                )

                prompt?.let { current ->
                    PermissionDialog(
                        prompt = current,
                        onDismiss = { permissionPrompt.value = null },
                        onConfirm = {
                            permissionPrompt.value = null
                            when (current) {
                                PermissionPrompt.ACCESSIBILITY,
                                PermissionPrompt.START_ACCESSIBILITY -> {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }

                                PermissionPrompt.OVERLAY,
                                PermissionPrompt.START_OVERLAY -> {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:$packageName"),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionPrompt.value == null) {
            if (pendingStart) {
                window.decorView.post {
                    continuePendingStart()
                }
            } else if (!preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)) {
                window.decorView.post { continueFirstRunPermissions() }
            }
        }
    }

    private fun continuePendingStart() {
        if (!isAutopilotAccessibilityEnabled()) {
            permissionPrompt.value = PermissionPrompt.START_ACCESSIBILITY
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            permissionPrompt.value = PermissionPrompt.START_OVERLAY
            return
        }
        projectionRequest.value = true
    }

    private fun continueFirstRunPermissions() {
        if (preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)) return
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        if (!isAutopilotAccessibilityEnabled()) {
            permissionPrompt.value = PermissionPrompt.ACCESSIBILITY
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            permissionPrompt.value = PermissionPrompt.OVERLAY
            return
        }
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            continueFirstRunPermissions()
        }
    }

    private fun isAutopilotAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = "$packageName/${AutopilotAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(runtimeReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    companion object {
        private const val KEY_ONBOARDING_COMPLETE = "complete"
        private const val REQUEST_NOTIFICATIONS = 401
    }
}

@androidx.compose.runtime.Composable
private fun PermissionDialog(
    prompt: PermissionPrompt,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isStartFlow = prompt == PermissionPrompt.START_ACCESSIBILITY ||
        prompt == PermissionPrompt.START_OVERLAY
    val title = when (prompt) {
        PermissionPrompt.ACCESSIBILITY,
        PermissionPrompt.START_ACCESSIBILITY -> R.string.permission_accessibility_title

        PermissionPrompt.OVERLAY,
        PermissionPrompt.START_OVERLAY -> R.string.permission_overlay_title
    }
    val body = when (prompt) {
        PermissionPrompt.ACCESSIBILITY,
        PermissionPrompt.START_ACCESSIBILITY ->
            R.string.permission_accessibility_body

        PermissionPrompt.OVERLAY,
        PermissionPrompt.START_OVERLAY ->
            R.string.permission_overlay_body
    }
    AlertDialog(
        onDismissRequest = onDismiss,
         title = { Text(stringResource(title)) },
         text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (isStartFlow) R.string.open_settings else R.string.continue_label))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.not_now))
            }
        },
    )
}