package com.autopilot.driver.matcher

import android.graphics.Rect
import com.autopilot.driver.model.PriceDetection
import com.autopilot.driver.model.RecognizedText
import java.text.Normalizer
import java.util.Locale

class PriceParser {
    private val pricePattern = Regex(
        """(?iu)(?:\p{Sc}|(?<!\p{L})rs\.?|(?<!\p{L})inr\b)\s*([0-9][0-9,\s\u00A0\u202F]*(?:\.[0-9]{1,2})?)|([0-9][0-9,\s\u00A0\u202F]*(?:\.[0-9]{1,2})?)\s*(?:rupees?|रुपये)"""
    )

    fun parse(
        text: List<RecognizedText>,
        nowMs: Long = System.currentTimeMillis(),
        targetBounds: Rect? = null,
    ): PriceDetection? {
        val candidates = text.flatMap { block ->
            if (nowMs - block.timestampMs > MAX_FRAME_AGE_MS) return@flatMap emptyList()
            pricePattern.findAll(block.text).mapNotNull { match ->
                val number = parseAmount(match.groups[1]?.value ?: match.groups[2]?.value)
                    ?: return@mapNotNull null
                if (targetBounds != null && !isAssociated(block.bounds, targetBounds)) {
                    return@mapNotNull null
                }
                val contextualBoost = if (match.value.contains("₹") || match.value.contains("rs", true) ||
                    match.value.contains("inr", true) || match.value.contains("रुपये") ||
                    match.value.any { it == '$' || it == '€' || it == '£' }
                ) 0.15f else 0f
                PriceDetection(
                    price = number,
                    confidence = (block.confidence + contextualBoost).coerceIn(0f, 1f),
                    sourceText = block.text,
                    bounds = block.bounds,
                    timestampMs = block.timestampMs,
                )
            }
        }
        return candidates.maxByOrNull { it.confidence }
    }

    private fun parseAmount(raw: String?): Double? {
        val normalized = raw
            ?.replace('\u00A0', ' ')
            ?.replace('\u202F', ' ')
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?: return null
        if (normalized.isEmpty()) return null

        val decimal = """(?:\.[0-9]{1,2})?"""
        val valid = when {
            ',' in normalized && ' ' in normalized -> false
            ',' in normalized -> normalized.matches(
                Regex("""(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,3}(?:,[0-9]{2})+,[0-9]{3})$decimal"""),
            )
            ' ' in normalized -> normalized.matches(
                Regex("""(?:[0-9]+|[0-9]{1,3}(?: [0-9]{3})+|[0-9]{1,3}(?: [0-9]{2})+ [0-9]{3})$decimal"""),
            )
            else -> normalized.matches(Regex("""[0-9]+$decimal"""))
        }
        if (!valid) return null
        return normalized.replace(",", "").replace(" ", "").toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun normalizeForMatching(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\p{Punct}\\s]+"), " ")
            .trim()

    private fun isAssociated(priceBounds: Rect, targetBounds: Rect): Boolean {
        if (priceBounds.width() <= 0 || priceBounds.height() <= 0 ||
            targetBounds.width() <= 0 || targetBounds.height() <= 0
        ) return false
        val horizontalOverlap =
            maxOf(0, minOf(priceBounds.right, targetBounds.right) - maxOf(priceBounds.left, targetBounds.left))
        val verticalOverlap =
            maxOf(0, minOf(priceBounds.bottom, targetBounds.bottom) - maxOf(priceBounds.top, targetBounds.top))
        val verticalGap = when {
            priceBounds.bottom < targetBounds.top -> targetBounds.top - priceBounds.bottom
            targetBounds.bottom < priceBounds.top -> priceBounds.top - targetBounds.bottom
            else -> 0
        }
        val horizontalGap = when {
            priceBounds.right < targetBounds.left -> targetBounds.left - priceBounds.right
            targetBounds.right < priceBounds.left -> priceBounds.left - targetBounds.right
            else -> 0
        }
        return (horizontalOverlap > 0 && verticalGap <= maxOf(targetBounds.height() * 4, 240)) ||
            (verticalOverlap > 0 && horizontalGap <= maxOf(targetBounds.width() * 4, 240))
    }

    private companion object {
        const val MAX_FRAME_AGE_MS = 1_500L
    }
}