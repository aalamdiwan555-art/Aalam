package com.autopilot.driver

import android.graphics.Rect
import com.autopilot.driver.detector.TargetDetector
import com.autopilot.driver.matcher.TargetPhrases
import com.autopilot.driver.model.RecognizedText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetDetectorTest {
    private val detector = TargetDetector(TargetPhrases.defaults)

    @Test fun findsHighConfidenceTargetInsideScreen() {
        val result = detector.detect(listOf(text("Accept")), 1080, 1920)
        assertTrue(result.detected)
        assertTrue(result.safeRegion != null)
    }

    @Test fun rejectsLowConfidenceTarget() {
        val result = detector.detect(listOf(text("Accept", confidence = 0.2f)), 1080, 1920)
        assertFalse(result.detected)
    }

    private fun text(value: String, confidence: Float = 0.9f) =
        RecognizedText(value, value.lowercase(), Rect(100, 100, 260, 180), confidence, 1L)
}