package com.autopilot.driver.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.hardware.display.DisplayManager
import android.view.accessibility.AccessibilityEvent
import com.autopilot.driver.capture.CaptureGeometry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AutopilotAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: AutopilotAccessibilityService? = null
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val gestureInFlight = AtomicBoolean(false)
    private val nextGestureId = AtomicLong(0L)
    @Volatile
    private var activeGestureId = 0L
    @Volatile
    private var pendingCallback: ((Boolean) -> Unit)? = null
    @Volatile
    private var connected = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        pendingCallback?.let { finishGesture(activeGestureId, it, false) }
    }

    fun isGeometryCurrent(geometry: CaptureGeometry): Boolean {
        val display = (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(geometry.displayId)
            ?: return false
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return display.rotation == geometry.rotation &&
            metrics.widthPixels == geometry.width &&
            metrics.heightPixels == geometry.height &&
            metrics.densityDpi == geometry.densityDpi
    }

    fun tap(x: Float, y: Float, callback: (Boolean) -> Unit) {
        if (!connected) {
            callback(false)
            return
        }
        if (!gestureInFlight.compareAndSet(false, true)) {
            callback(false)
            return
        }

        val gestureId = nextGestureId.incrementAndGet()
        activeGestureId = gestureId
        pendingCallback = callback
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 120L))
            .build()
        val dispatched = try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    finishGesture(gestureId, callback, true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    finishGesture(gestureId, callback, false)
                }
            }, null)
        } catch (_: Throwable) {
            false
        }
        if (!dispatched) {
            finishGesture(gestureId, callback, false)
        }
    }

    fun cancelPendingGestures() {
        // Android owns cancellation. Keep the guard until Android invokes the
        // callback so an old callback cannot unlock a newer gesture.
    }

    private fun finishGesture(gestureId: Long, callback: (Boolean) -> Unit, success: Boolean) {
        if (activeGestureId != gestureId) return
        gestureInFlight.set(false)
        activeGestureId = 0L
        pendingCallback = null
        mainHandler.post { callback(success) }
    }

    override fun onDestroy() {
        connected = false
        val callback = pendingCallback
        pendingCallback = null
        activeGestureId = 0L
        gestureInFlight.set(false)
        instance = null
        callback?.let { mainHandler.post { it(false) } }
        super.onDestroy()
    }
}