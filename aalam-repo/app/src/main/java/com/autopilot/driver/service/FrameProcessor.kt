package com.autopilot.driver.service

import android.graphics.Bitmap
import com.autopilot.driver.detector.TargetDetector
import com.autopilot.driver.matcher.PriceMatcher
import com.autopilot.driver.matcher.PriceParser
import com.autopilot.driver.model.PriceConfiguration
import com.autopilot.driver.model.RuntimeSnapshot
import com.autopilot.driver.model.Diagnostics
import com.autopilot.driver.model.DiagnosticState
import com.autopilot.driver.model.MatchStatus
import com.autopilot.driver.model.ActionStatus
import com.autopilot.driver.ocr.TextDetector
import com.autopilot.driver.opencv.ImagePreprocessor

class FrameProcessor(
    private val textDetector: TextDetector,
    private val priceParser: PriceParser,
    private val priceMatcher: PriceMatcher,
    private val targetDetector: TargetDetector,
    private val preprocessor: ImagePreprocessor,
) {
    suspend fun process(
        source: Bitmap,
        config: PriceConfiguration,
        screenWidth: Int,
        screenHeight: Int,
        snapshot: RuntimeSnapshot,
    ): FrameResult {
        val frameReceived = System.currentTimeMillis()
        val prepared = preprocessor.prepare(source)
        val recognized = try {
            textDetector.detect(prepared, frameReceived)
        } finally {
            if (prepared !== source) {
                prepared.recycle()
            }
            if (!source.isRecycled) {
                source.recycle()
            }
        }

        val price = priceParser.parse(recognized, frameReceived)
        val match = priceMatcher.match(price?.price, config)
        val target = targetDetector.detect(recognized, screenWidth, screenHeight)
        val latency = System.currentTimeMillis() - frameReceived
        val confidence = listOfNotNull(price?.confidence, if (target.detected) target.confidence else null)
            .minOrNull() ?: 0f
        val previousAction = snapshot.diagnostics.action
        return FrameResult(
            detectedPrice = price?.price,
            snapshot = snapshot.copy(
                diagnostics = Diagnostics(
                    capture = DiagnosticState.ON,
                    frame = DiagnosticState.RECEIVING,
                    ocr = DiagnosticState.READY,
                    openCv = if (preprocessor.isReady) DiagnosticState.READY else DiagnosticState.ERROR,
                    price = if (price != null) DiagnosticState.DETECTED else DiagnosticState.NOT_DETECTED,
                    priceMatch = when (match.status) {
                        MatchStatus.MATCH -> DiagnosticState.MATCH
                        else -> DiagnosticState.NO_MATCH
                    },
                    target = if (target.detected) DiagnosticState.DETECTED else DiagnosticState.NOT_DETECTED,
                    confidence = confidence,
                    latencyMs = latency,
                    action = previousAction,
                    lastMessage = match.reason,
                ),
            ),
            price = price,
            match = match,
            target = target,
        )
    }
}

data class FrameResult(
    val detectedPrice: Double?,
    val snapshot: RuntimeSnapshot,
    val price: com.autopilot.driver.model.PriceDetection?,
    val match: com.autopilot.driver.model.PriceMatchResult,
    val target: com.autopilot.driver.model.TargetDetection,
)