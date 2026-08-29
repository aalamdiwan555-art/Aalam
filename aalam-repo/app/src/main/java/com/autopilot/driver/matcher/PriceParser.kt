package com.autopilot.driver.matcher

import com.autopilot.driver.model.PriceDetection
import com.autopilot.driver.model.RecognizedText
import java.text.Normalizer
import java.util.Locale

class PriceParser {
    private val pricePattern = Regex(
        """(?iu)(?:₹|(?<!\p{L})rs\.?|(?<!\p{L})inr\b)\s*([0-9][0-9,\s]*(?:\.[0-9]{1,2})?)|([0-9][0-9,\s]*(?:\.[0-9]{1,2})?)\s*(?:rupees?|रुपये)"""
    )

    fun parse(text: List<RecognizedText>, @Suppress("UNUSED_PARAMETER") nowMs: Long = System.currentTimeMillis()): PriceDetection? {
        val candidates = text.flatMap { block ->
            pricePattern.findAll(block.text).mapNotNull { match ->
                val number = parseAmount(match.groups[1]?.value ?: match.groups[2]?.value)
                    ?: return@mapNotNull null
                val contextualBoost = if (match.value.contains("₹") || match.value.contains("rs", true) ||
                    match.value.contains("inr", true) || match.value.contains("रुपये")
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
            ?.replace(Regex("\\s+"), "")
            ?: return null
        if (normalized.isEmpty() || normalized.count { it == '.' } > 1) return null

        val amountPattern = Regex(
            """(?:[0-9]+(?:\.[0-9]{1,2})?|[0-9]{1,3}(?:,[0-9]{3})+(?:\.[0-9]{1,2})?|[0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?)"""
        )
        if (!amountPattern.matches(normalized)) return null
        return normalized.replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun normalizeForMatching(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\p{Punct}\\s]+"), " ")
            .trim()
}