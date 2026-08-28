package com.autopilot.driver.matcher

import com.autopilot.driver.model.PriceDetection
import com.autopilot.driver.model.RecognizedText
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

class PriceParser {
    private val pricePattern = Regex(
        """(?i)(?:₹|rs\.?|inr)\s*([0-9]{1,3}(?:[,\s][0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)|([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)\s*(?:rupees?|रुपये)"""
    )

    fun parse(text: List<RecognizedText>, nowMs: Long = System.currentTimeMillis()): PriceDetection? {
        val candidates = text.flatMap { block ->
            pricePattern.findAll(block.text).mapNotNull { match ->
                val number = (match.groups[1]?.value ?: match.groups[2]?.value)
                    ?.replace(",", "")
                    ?.replace(" ", "")
                    ?.toDoubleOrNull()
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
            ?: parseStandaloneNumber(text, nowMs)
    }

    private fun parseStandaloneNumber(text: List<RecognizedText>, nowMs: Long): PriceDetection? {
        val numberPattern = Regex("""(?<![\w.])([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)(?![\w.])""")
        return text.flatMap { block ->
            numberPattern.findAll(block.text).mapNotNull { match ->
                val number = match.value.replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
                PriceDetection(number, max(0f, block.confidence - 0.2f), block.text, block.bounds, nowMs)
            }
        }.maxByOrNull { it.confidence }
    }

    fun normalizeForMatching(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\p{Punct}\\s]+"), " ")
            .trim()
}