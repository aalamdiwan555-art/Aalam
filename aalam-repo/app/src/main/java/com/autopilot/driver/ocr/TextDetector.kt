package com.autopilot.driver.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.autopilot.driver.model.RecognizedText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class TextDetector {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val recognizerMutex = Mutex()
    private val closed = AtomicBoolean(false)

    suspend fun detect(bitmap: Bitmap, timestampMs: Long): List<RecognizedText> =
        withContext(Dispatchers.IO) {
            if (closed.get()) return@withContext emptyList()
            recognizerMutex.withLock {
                if (closed.get()) return@withLock emptyList()
                val result: Text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                result.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        RecognizedText(
                            text = line.text,
                            normalizedText = normalize(line.text),
                            bounds = line.boundingBox ?: Rect(),
                            confidence = (line.confidence ?: 0f).coerceIn(0f, 1f),
                            timestampMs = timestampMs,
                        )
                    }
                }
            }
        }

    fun close() {
        if (closed.compareAndSet(false, true)) recognizer.close()
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase(Locale.ROOT)
}
