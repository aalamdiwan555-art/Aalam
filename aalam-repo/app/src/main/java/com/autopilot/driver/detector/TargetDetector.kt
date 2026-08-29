package com.autopilot.driver.detector

import android.graphics.Rect
import com.autopilot.driver.matcher.TargetPhrase
import com.autopilot.driver.model.RecognizedText
import com.autopilot.driver.model.TargetDetection
import java.text.Normalizer
import java.util.Locale

class TargetDetector(
    private val phrases: List<TargetPhrase>,
    private val minimumConfidence: Float = 0.72f,
) {
    fun detect(text: List<RecognizedText>, screenWidth: Int, screenHeight: Int): TargetDetection {
        val match = text.asSequence()
            .filter { it.confidence >= minimumConfidence }
            .mapNotNull { block ->
                val phrase = phrases
                    .map { target -> target to matchScore(block.text, target.phrase) }
                    .filter { (_, score) -> score > 0 }
                    .maxByOrNull { (_, score) -> score }
                    ?.first
                    ?: return@mapNotNull null
                block to phrase
            }
            .maxByOrNull { (block, phrase) ->
                block.confidence + matchScore(block.text, phrase.phrase) * 0.1f
            }
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
        if (width <= 0 || height <= 0 || bounds.width() <= 0 || bounds.height() <= 0) return null
        val originalArea = bounds.width().toLong() * bounds.height().toLong()
        val clamped = Rect(
            bounds.left.coerceIn(0, width),
            bounds.top.coerceIn(0, height),
            bounds.right.coerceIn(0, width),
            bounds.bottom.coerceIn(0, height),
        )
        if (clamped.width() < 12 || clamped.height() < 12 || clamped.left >= clamped.right || clamped.top >= clamped.bottom) {
            return null
        }
        val visibleArea = clamped.width().toLong() * clamped.height().toLong()
        if (visibleArea * 4 < originalArea * 3) return null
        val insetX = (clamped.width() * 0.18f).toInt().coerceAtLeast(2)
        val insetY = (clamped.height() * 0.18f).toInt().coerceAtLeast(2)
        val safe = Rect(clamped)
        safe.inset(insetX, insetY)
        return if (safe.left >= 0 && safe.top >= 0 && safe.right <= width && safe.bottom <= height &&
            safe.width() > 0 && safe.height() > 0
        ) safe else null
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun matchScore(value: String, phrase: String): Float {
        val normalizedValue = normalize(value)
        val normalizedPhrase = normalize(phrase)
        if (normalizedValue == normalizedPhrase) return 1f
        if (Regex("(^|\\s)${Regex.escape(normalizedPhrase)}(\\s|$)").containsMatchIn(normalizedValue)) {
            return 1f
        }

        // OCR occasionally changes one character in a short button label
        // (for example, "Accept" -> "Accepl"). Accept only a very small
        // edit distance so unrelated text cannot become a tap target.
        val compactValue = normalizedValue.replace(" ", "")
        val compactPhrase = normalizedPhrase.replace(" ", "")
        if (compactPhrase.length < 5 || compactValue.length < compactPhrase.length ||
            compactValue.length > compactPhrase.length + 2
        ) return 0f
        return if (levenshtein(compactValue, compactPhrase) <= 1) 0.8f else 0f
    }

    private fun levenshtein(left: String, right: String): Int {
        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1,
                )
            }
            previous.indices.forEach { previous[it] = current[it] }
        }
        return previous[right.length]
    }
}