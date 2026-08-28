package com.autopilot.driver.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.autopilot.driver.model.RecognizedText
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TextDetector {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun detect(bitmap: Bitmap, timestampMs: Long): List<RecognizedText> =
        withContext(Dispatchers.IO) {
            val result: Text = Tasks.await(
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
            )
            result.textBlocks.flatMap { block ->
                block.lines.map { line ->
                    RecognizedText(
                        text = line.text,
                        normalizedText = normalize(line.text),
                        bounds = line.boundingBox ?: Rect(),
                        confidence = line.confidence ?: 0f,
                        timestampMs = timestampMs,
                    )
                }
            }
        }

    fun close() {
        recognizer.close()
    }

    private fun normalize(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()
}