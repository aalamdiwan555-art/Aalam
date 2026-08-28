package com.autopilot.driver

import com.autopilot.driver.matcher.PriceMatcher
import com.autopilot.driver.model.MatchStatus
import com.autopilot.driver.model.PriceConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceMatcherTest {
    private val matcher = PriceMatcher()
    private val range = PriceConfiguration(100.0, 150.0)

    @Test fun minimumAndMaximumAreInclusive() {
        assertEquals(MatchStatus.MATCH, matcher.match(100.0, range).status)
        assertEquals(MatchStatus.MATCH, matcher.match(150.0, range).status)
    }

    @Test fun valuesOutsideRangeDoNotMatch() {
        assertEquals(MatchStatus.NO_MATCH, matcher.match(99.0, range).status)
        assertEquals(MatchStatus.NO_MATCH, matcher.match(151.0, range).status)
    }

    @Test fun invalidValuesAreRejected() {
        assertEquals(MatchStatus.INVALID, matcher.match(null, range).status)
        assertEquals(MatchStatus.INVALID, matcher.match(100.0, PriceConfiguration(200.0, 100.0)).status)
        assertEquals(MatchStatus.INVALID, matcher.match(100.0, PriceConfiguration(-1.0, 100.0)).status)
    }

    @Test fun decimalValuesMatch() {
        assertEquals(MatchStatus.MATCH, matcher.match(125.50, range).status)
    }
}