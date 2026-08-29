package com.autopilot.driver

import android.graphics.Rect
import com.autopilot.driver.matcher.PriceParser
import com.autopilot.driver.model.RecognizedText
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceParserTest {
    private val parser = PriceParser()

    @Test fun parsesIndianCurrencyFormats() {
        val values = listOf("₹100", "₹ 100", "Rs 100", "Rs. 100", "INR 100", "100 रुपये")
        values.forEach { value ->
            val result = parser.parse(listOf(block(value)))
            assertEquals(100.0, result?.price ?: -1.0, 0.001)
        }
    }

    @Test fun prefersCurrencyContextWhenMultipleNumbersExist() {
        val result = parser.parse(listOf(block("Order 12, ₹125.50, item 7")))
        assertEquals(125.50, result?.price ?: -1.0, 0.001)
    }

    @Test fun normalizesText() {
        assertEquals("accept now", parser.normalizeForMatching("  ACCEPT,   now! "))
    }

    private fun block(text: String) = RecognizedText(text, text.lowercase(), Rect(0, 0, 100, 30), 0.9f, 1L)
}