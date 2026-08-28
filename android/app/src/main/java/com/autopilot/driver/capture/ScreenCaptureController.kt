package com.autopilot.driver.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
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

    fun start(projection: MediaProjection) {
        this.projection = projection
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            reader.acquireLatestImage()?.use { image ->
                val plane = image.planes.firstOrNull() ?: return@use
                val buffer: ByteBuffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val paddedWidth = width + rowPadding / pixelStride
                val raw = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                raw.copyPixelsFromBuffer(buffer)
                val frame = if (paddedWidth == width) raw else Bitmap.createBitmap(raw, 0, 0, width, height)
                onFrame(frame, width, height)
                if (frame !== raw) raw.recycle()
                if (!raw.isRecycled) raw.recycle()
            }
        }, Handler(Looper.getMainLooper()))
        virtualDisplay = projection.createVirtualDisplay(
            "AutopilotCapture",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null,
        )
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                onStopped()
                stop()
            }
        }, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
    }
}