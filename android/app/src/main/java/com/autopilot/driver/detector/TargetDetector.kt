package com.autopilot.driver.detector

import android.graphics.Rect
import com.autopilot.driver.matcher.TargetPhrase
import com.autopilot.driver.model.RecognizedText
import com.autopilot.driver.model.TargetDetection

class TargetDetector(
    private val phrases: List<TargetPhrase>,
    private val minimumConfidence: Float = 0.72f,
) {
    fun detect(text: List<RecognizedText>, screenWidth: Int, screenHeight: Int): TargetDetection {
        val match = text.asSequence()
            .filter { it.confidence >= minimumConfidence }
            .mapNotNull { block ->
                val phrase = phrases.firstOrNull { target ->
                    normalize(block.text).contains(normalize(target.phrase))
                } ?: return@mapNotNull null
                block to phrase
            }
            .maxByOrNull { it.first.confidence }
            ?: return TargetDetection(false, null, null, null, null, 0f, null, System.currentTimeMillis())

        val sourceBounds = Rect(match.first.bounds)
        val safe = safeRegion(sourceBounds, screenWidth, screenHeight) ?: return TargetDetection(
            false, null, null, null, null, match.first.confidence, match.first.text, match.first.timestampMs
        )
        return TargetDetection(
            detected = true,
            bounds = sourceBounds,
            centerX = safe.centerX(),
            centerY = safe.centerY(),
            safeRegion = safe,
            confidence = match.first.confidence,
            recognizedText = match.first.text,
            timestampMs = match.first.timestampMs,
        )
    }

    private fun safeRegion(bounds: Rect, width: Int, height: Int): Rect? {
        val clamped = Rect(
            bounds.left.coerceIn(0, width),
            bounds.top.coerceIn(0, height),
            bounds.right.coerceIn(0, width),
            bounds.bottom.coerceIn(0, height),
        )
        if (clamped.width() < 12 || clamped.height() < 12 || clamped.left >= clamped.right || clamped.top >= clamped.bottom) {
            return null
        }
        val insetX = (clamped.width() * 0.18f).toInt().coerceAtLeast(2)
        val insetY = (clamped.height() * 0.18f).toInt().coerceAtLeast(2)
        val safe = Rect(clamped)
        safe.inset(insetX, insetY)
        return if (safe.left >= 0 && safe.top >= 0 && safe.right <= width && safe.bottom <= height &&
            safe.width() > 0 && safe.height() > 0
        ) safe else null
    }

    private fun normalize(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()
}