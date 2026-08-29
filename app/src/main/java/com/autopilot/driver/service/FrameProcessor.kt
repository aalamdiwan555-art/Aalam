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
        val prepared = try {
            preprocessor.prepare(source)
        } catch (error: Throwable) {
            if (!source.isRecycled) source.recycle()
            return failedResult(snapshot, "Image preprocessing failed", DiagnosticState.ERROR)
        }
        val recognized = try {
            textDetector.detect(prepared, frameReceived)
        } catch (error: Throwable) {
            return failedResult(
                snapshot,
                "OCR failed; monitoring is still safe",
                DiagnosticState.ERROR,
                openCvState = if (preprocessor.isReady) DiagnosticState.READY else DiagnosticState.ERROR,
            )
        } finally {
            if (prepared !== source) {
                prepared.recycle()
            }
            if (!source.isRecycled) {
                source.recycle()
            }
        }

        val target = targetDetector.detect(recognized, screenWidth, screenHeight, frameReceived)
        val price = priceParser.parse(recognized, frameReceived, target.bounds)
        val match = priceMatcher.match(price?.price, config)
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

    private fun failedResult(
        snapshot: RuntimeSnapshot,
        message: String,
        ocrState: DiagnosticState,
        openCvState: DiagnosticState = if (preprocessor.isReady) DiagnosticState.READY else DiagnosticState.ERROR,
    ): FrameResult = FrameResult(
        detectedPrice = null,
        snapshot = snapshot.copy(
            diagnostics = snapshot.diagnostics.copy(
                capture = DiagnosticState.ON,
                frame = DiagnosticState.ERROR,
                ocr = ocrState,
                openCv = openCvState,
                price = DiagnosticState.NOT_DETECTED,
                priceMatch = DiagnosticState.NO_MATCH,
                target = DiagnosticState.NOT_DETECTED,
                confidence = 0f,
                latencyMs = null,
                lastMessage = message,
            ),
        ),
        price = null,
        match = com.autopilot.driver.model.PriceMatchResult(
            com.autopilot.driver.model.MatchStatus.INVALID,
            message,
        ),
        target = com.autopilot.driver.model.TargetDetection(
            detected = false,
            bounds = null,
            centerX = null,
            centerY = null,
            safeRegion = null,
            confidence = 0f,
            recognizedText = null,
            timestampMs = System.currentTimeMillis(),
        ),
    )
}

data class FrameResult(
    val detectedPrice: Double?,
    val snapshot: RuntimeSnapshot,
    val price: com.autopilot.driver.model.PriceDetection?,
    val match: com.autopilot.driver.model.PriceMatchResult,
    val target: com.autopilot.driver.model.TargetDetection,
)