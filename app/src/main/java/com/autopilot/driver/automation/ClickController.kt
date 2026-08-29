package com.autopilot.driver.automation

import android.graphics.Rect
import android.os.SystemClock
import com.autopilot.driver.capture.CaptureGeometry
import com.autopilot.driver.model.TargetDetection
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ClickResult(val success: Boolean, val message: String)

class ClickController {
    private var lastActionKey: String? = null
    private var lastActionAt: Long = 0L
    private val cooldownMs = 900L

    suspend fun click(target: TargetDetection, geometry: CaptureGeometry): ClickResult {
        val safe = target.safeRegion ?: return ClickResult(false, "Target has no safe region")
        val x = safe.centerX()
        val y = safe.centerY()
        if (!isValidCoordinate(x, y, safe, geometry)) {
            return ClickResult(false, "Target coordinates are outside the screen")
        }
        val key = "${safe.left}:${safe.top}:${safe.right}:${safe.bottom}"
        val now = SystemClock.uptimeMillis()
        if (key == lastActionKey && now - lastActionAt < cooldownMs) {
            return ClickResult(false, "Duplicate target is in cooldown")
        }

        val service = AutopilotAccessibilityService.instance
            ?: return ClickResult(false, "Accessibility service is not enabled")
        if (!service.isGeometryCurrent(geometry)) {
            return ClickResult(false, "Screen changed before the gesture was dispatched")
        }
        return suspendCancellableCoroutine { continuation ->
            service.tap(x.toFloat(), y.toFloat()) { success ->
                if (continuation.isActive) {
                    if (success) {
                        lastActionKey = key
                        lastActionAt = SystemClock.uptimeMillis()
                    }
                    continuation.resume(
                        if (success) ClickResult(true, "Target tapped")
                        else ClickResult(false, "Android rejected the gesture")
                    )
                }
            }
            continuation.invokeOnCancellation { service.cancelPendingGestures() }
        }
    }

    fun reset() {
        lastActionKey = null
        lastActionAt = 0L
    }

    private fun isValidCoordinate(x: Int, y: Int, region: Rect, geometry: CaptureGeometry): Boolean =
        geometry.width > 0 && geometry.height > 0 &&
            x in 0 until geometry.width && y in 0 until geometry.height && region.contains(x, y)
}