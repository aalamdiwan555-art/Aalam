package com.autopilot.driver.model

import android.graphics.Rect

enum class RunState {
    STOPPED,
    STARTING,
    RUNNING,
    PAUSED,
    ERROR,
    STOPPING,
}

enum class MatchStatus {
    MATCH,
    NO_MATCH,
    INVALID,
}

enum class ActionStatus {
    IDLE,
    EXECUTED,
    FAILED,
}

enum class DiagnosticState {
    READY,
    ERROR,
    ON,
    OFF,
    RECEIVING,
    STOPPED,
    DETECTED,
    NOT_DETECTED,
    MATCH,
    NO_MATCH,
}

data class PriceConfiguration(
    val minimumPrice: Double,
    val maximumPrice: Double,
)

data class PriceMatchResult(
    val status: MatchStatus,
    val reason: String,
)

data class RecognizedText(
    val text: String,
    val normalizedText: String,
    val bounds: Rect,
    val confidence: Float,
    val timestampMs: Long,
)

data class PriceDetection(
    val price: Double,
    val confidence: Float,
    val sourceText: String,
    val bounds: Rect?,
    val timestampMs: Long,
)

data class TargetDetection(
    val detected: Boolean,
    val bounds: Rect?,
    val centerX: Int?,
    val centerY: Int?,
    val safeRegion: Rect?,
    val confidence: Float,
    val recognizedText: String?,
    val timestampMs: Long,
)

data class Diagnostics(
    val capture: DiagnosticState = DiagnosticState.OFF,
    val frame: DiagnosticState = DiagnosticState.STOPPED,
    val ocr: DiagnosticState = DiagnosticState.READY,
    val openCv: DiagnosticState = DiagnosticState.READY,
    val price: DiagnosticState = DiagnosticState.NOT_DETECTED,
    val priceMatch: DiagnosticState = DiagnosticState.NO_MATCH,
    val target: DiagnosticState = DiagnosticState.NOT_DETECTED,
    val confidence: Float = 0f,
    val latencyMs: Long? = null,
    val action: ActionStatus = ActionStatus.IDLE,
    val lastMessage: String? = null,
)

data class RuntimeSnapshot(
    val state: RunState = RunState.STOPPED,
    val detectedPrice: Double? = null,
    val diagnostics: Diagnostics = Diagnostics(),
    val errorMessage: String? = null,
)