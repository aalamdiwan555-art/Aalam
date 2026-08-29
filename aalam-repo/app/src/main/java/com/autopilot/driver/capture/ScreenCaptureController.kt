package com.autopilot.driver.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import java.nio.ByteBuffer

class ScreenCaptureController(
    private val context: Context,
    private val onFrame: (Bitmap, Int, Int) -> Unit,
    private val onStopped: () -> Unit,
) {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var stopping = false
    private var callback: MediaProjection.Callback? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    fun start(projection: MediaProjection) {
        stop()
        stopping = false
        this.projection = projection
        val thread = HandlerThread("AutopilotCapture").also { it.start() }
        handlerThread = thread
        val callbackHandler = Handler(thread.looper)
        handler = callbackHandler
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        try {
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            reader.setOnImageAvailableListener({ availableReader ->
                availableReader.acquireLatestImage()?.use { image ->
                    val plane = image.planes.firstOrNull() ?: return@use
                    val buffer: ByteBuffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val paddedWidth = width + rowPadding / pixelStride
                    var raw: Bitmap? = null
                    var frame: Bitmap? = null
                    var handedOff = false
                    try {
                        buffer.rewind()
                        val rawBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                        raw = rawBitmap
                        rawBitmap.copyPixelsFromBuffer(buffer)
                        val frameBitmap = if (paddedWidth == width) {
                            rawBitmap
                        } else {
                            Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
                        }
                        frame = frameBitmap
                        onFrame(frameBitmap, width, height)
                        handedOff = true
                    } catch (_: Throwable) {
                        if (!stopping) onStopped()
                    } finally {
                        if (!handedOff) frame?.let { if (!it.isRecycled) it.recycle() }
                        if (frame !== raw) raw?.let { if (!it.isRecycled) it.recycle() }
                    }
                }
            }, callbackHandler)
            virtualDisplay = projection.createVirtualDisplay(
                "AutopilotCapture",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                callbackHandler,
            )
            callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    if (!stopping) onStopped()
                    stop()
                }
            }
            projection.registerCallback(callback!!, callbackHandler)
        } catch (error: Throwable) {
            stop()
            throw error
        }
    }

    fun stop() {
        if (stopping && projection == null && virtualDisplay == null && imageReader == null) {
            return
        }
        stopping = true
        val display = virtualDisplay
        virtualDisplay = null
        val reader = imageReader
        imageReader = null
        val activeProjection = projection
        projection = null
        callback?.let { activeProjection?.unregisterCallback(it) }
        callback = null
        display?.release()
        reader?.close()
        activeProjection?.stop()
        handler?.removeCallbacksAndMessages(null)
        handler = null
        handlerThread?.quitSafely()
        handlerThread = null
    }
}