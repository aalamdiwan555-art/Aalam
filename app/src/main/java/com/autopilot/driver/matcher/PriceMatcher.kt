package com.autopilot.driver.matcher

import com.autopilot.driver.model.MatchStatus
import com.autopilot.driver.model.PriceConfiguration
import com.autopilot.driver.model.PriceMatchResult

class PriceMatcher {
    fun match(detectedPrice: Double?, configuration: PriceConfiguration): PriceMatchResult {
        if (detectedPrice == null || !detectedPrice.isFinite()) {
            return PriceMatchResult(MatchStatus.INVALID, "No valid price was detected")
        }
        if (!configuration.minimumPrice.isFinite() || !configuration.maximumPrice.isFinite()) {
            return PriceMatchResult(MatchStatus.INVALID, "Price range is not valid")
        }
        if (configuration.minimumPrice < 0 || configuration.maximumPrice < 0) {
            return PriceMatchResult(MatchStatus.INVALID, "Prices cannot be negative")
        }
        if (configuration.minimumPrice > configuration.maximumPrice) {
            return PriceMatchResult(MatchStatus.INVALID, "Minimum price must not exceed maximum")
        }
        return if (detectedPrice in configuration.minimumPrice..configuration.maximumPrice) {
            PriceMatchResult(MatchStatus.MATCH, "Price is inside the configured range")
        } else {
            PriceMatchResult(MatchStatus.NO_MATCH, "Price is outside the configured range")
        }
    }
}