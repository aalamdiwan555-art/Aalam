package com.autopilot.driver.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.autopilot.driver.model.ActionStatus
import com.autopilot.driver.model.DiagnosticState
import com.autopilot.driver.model.Diagnostics
import com.autopilot.driver.model.RunState
import com.autopilot.driver.model.RuntimeSnapshot
import com.autopilot.driver.service.AutopilotService
import com.autopilot.driver.storage.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class AutopilotViewModel(private val appContext: Context) : ViewModel() {
    private val settingsStore = SettingsStore(appContext)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { stored ->
                _uiState.update {
                    it.copy(
                        minimumPrice = stored.minimumPrice,
                        maximumPrice = stored.maximumPrice,
                        overlayEnabled = stored.showOverlay,
                    )
                }
            }
        }
    }

    fun onMinimumChanged(value: String) = _uiState.update { it.copy(minimumPrice = value, inputError = null) }
    fun onMaximumChanged(value: String) = _uiState.update { it.copy(maximumPrice = value, inputError = null) }

    fun validateAndSave(): Boolean {
        val min = _uiState.value.minimumPrice.toDoubleOrNull()
        val max = _uiState.value.maximumPrice.toDoubleOrNull()
        val error = when {
            min == null || max == null -> "Enter valid numbers for both prices"
            !min.isFinite() || !max.isFinite() -> "Prices must be finite numbers"
            min < 0 || max < 0 -> "Prices cannot be negative"
            min > max -> "Minimum price must not exceed maximum"
            else -> null
        }
        _uiState.update { it.copy(inputError = error) }
        if (error != null) return false
        viewModelScope.launch { settingsStore.savePriceRange(min!!, max!!) }
        return true
    }

    fun startWithProjection(resultCode: Int, data: Intent) {
        if (!validateAndSave()) return
        val state = _uiState.value
        val intent = Intent(appContext, AutopilotService::class.java).apply {
            action = AutopilotService.ACTION_START
            putExtra(AutopilotService.EXTRA_PROJECTION_CODE, resultCode)
            putExtra(AutopilotService.EXTRA_PROJECTION_DATA, data)
            putExtra(AutopilotService.EXTRA_MINIMUM, state.minimumPrice.toDouble())
            putExtra(AutopilotService.EXTRA_MAXIMUM, state.maximumPrice.toDouble())
        }
        androidx.core.content.ContextCompat.startForegroundService(appContext, intent)
        _uiState.update { it.copy(snapshot = it.snapshot.copy(state = RunState.STARTING), inputError = null) }
    }

    fun pause() {
        appContext.startService(Intent(appContext, AutopilotService::class.java).setAction(AutopilotService.ACTION_PAUSE))
    }

    fun stop() {
        appContext.startService(Intent(appContext, AutopilotService::class.java).setAction(AutopilotService.ACTION_STOP))
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setOverlayEnabled(enabled) }
        _uiState.update { it.copy(overlayEnabled = enabled) }
    }

    fun onRuntimeSnapshot(snapshot: RuntimeSnapshot) {
        _uiState.update { it.copy(snapshot = snapshot) }
    }

    fun onRuntimeUpdate(intent: Intent) {
        val state = runCatching { RunState.valueOf(intent.getStringExtra("state") ?: "STOPPED") }
            .getOrDefault(RunState.STOPPED)
        val diagnostics = Diagnostics(
            capture = if (intent.getBooleanExtra("captureOn", false)) DiagnosticState.ON else DiagnosticState.OFF,
            frame = if (state == RunState.RUNNING) DiagnosticState.RECEIVING else DiagnosticState.STOPPED,
            ocr = DiagnosticState.READY,
            openCv = DiagnosticState.READY,
            price = runCatching { DiagnosticState.valueOf(intent.getStringExtra("price") ?: "NOT_DETECTED") }.getOrDefault(DiagnosticState.NOT_DETECTED),
            priceMatch = runCatching { DiagnosticState.valueOf(intent.getStringExtra("priceMatch") ?: "NO_MATCH") }.getOrDefault(DiagnosticState.NO_MATCH),
            target = runCatching { DiagnosticState.valueOf(intent.getStringExtra("target") ?: "NOT_DETECTED") }.getOrDefault(DiagnosticState.NOT_DETECTED),
            confidence = intent.getFloatExtra("confidence", 0f),
            latencyMs = if (intent.hasExtra("latency")) intent.getLongExtra("latency", 0) else null,
            action = runCatching { ActionStatus.valueOf(intent.getStringExtra("action") ?: "IDLE") }.getOrDefault(ActionStatus.IDLE),
            lastMessage = intent.getStringExtra("message"),
        )
        _uiState.update {
            it.copy(
                snapshot = RuntimeSnapshot(
                    state = state,
                    detectedPrice = if (intent.hasExtra("detectedPrice")) intent.getDoubleExtra("detectedPrice", 0.0) else null,
                    diagnostics = diagnostics,
                    errorMessage = intent.getStringExtra("error"),
                ),
            )
        }
    }

    fun formatPrice(price: Double?): String = price?.let { "₹" + String.format(Locale.US, "%.2f", it) } ?: "Not detected"

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AutopilotViewModel(context.applicationContext) as T
    }
}

data class UiState(
    val minimumPrice: String = "100",
    val maximumPrice: String = "150",
    val overlayEnabled: Boolean = false,
    val inputError: String? = null,
    val snapshot: RuntimeSnapshot = RuntimeSnapshot(),
)