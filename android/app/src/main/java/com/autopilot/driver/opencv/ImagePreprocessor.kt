package com.autopilot.driver.opencv

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class ImagePreprocessor {
    val isReady: Boolean = OpenCVLoader.initLocal()

    fun prepare(source: Bitmap): Bitmap {
        if (!isReady) return source
        val rgba = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(gray, rgba, Imgproc.COLOR_GRAY2RGBA)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, output)
            output
        } finally {
            rgba.release()
            gray.release()
        }
    }
}