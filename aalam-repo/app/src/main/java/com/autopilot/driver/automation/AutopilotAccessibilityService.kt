package com.autopilot.driver.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean

class AutopilotAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: AutopilotAccessibilityService? = null
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val gestureInFlight = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun tap(x: Float, y: Float, callback: (Boolean) -> Unit) {
        if (!gestureInFlight.compareAndSet(false, true)) {
            callback(false)
            return
        }

        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 120L))
            .build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                finishGesture(callback, true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                finishGesture(callback, false)
            }
        }, null)

        if (!dispatched) {
            finishGesture(callback, false)
        }
    }

    fun cancelPendingGestures() {
        // Android owns gesture cancellation. We only release our guard; we do
        // not cancel an unrelated user gesture.
        gestureInFlight.set(false)
    }

    private fun finishGesture(callback: (Boolean) -> Unit, success: Boolean) {
        gestureInFlight.set(false)
        mainHandler.post { callback(success) }
    }

    override fun onDestroy() {
        gestureInFlight.set(false)
        instance = null
        super.onDestroy()
    }
}