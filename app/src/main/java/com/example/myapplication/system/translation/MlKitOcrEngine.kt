package com.example.myapplication.system.translation

import android.content.Context
import android.graphics.Bitmap
import com.example.myapplication.model.ScreenTextBlock
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.util.UUID

interface OcrEngine : Closeable {
    fun recognize(bitmap: Bitmap): List<ScreenTextBlock>
}

class MlKitOcrEngine(
    context: Context,
) : OcrEngine {
    private val recognizers: List<TextRecognizer> = listOf(
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
    )

    override fun recognize(bitmap: Bitmap): List<ScreenTextBlock> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val mergedBlocks = linkedMapOf<String, ScreenTextBlock>()

        recognizers.forEach { recognizer ->
            val text = Tasks.await(recognizer.process(image))
            text.textBlocks.forEachIndexed { index, block ->
                val rawText = block.text.orEmpty().trim()
                val bounds = block.boundingBox ?: return@forEachIndexed
                if (rawText.isBlank()) {
                    return@forEachIndexed
                }
                val dedupeKey = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}:$rawText"
                if (dedupeKey !in mergedBlocks) {
                    mergedBlocks[dedupeKey] = ScreenTextBlock(
                        id = UUID.randomUUID().toString(),
                        text = rawText,
                        bounds = bounds,
                        confidence = 1f,
                        orderIndex = index,
                    )
                }
            }
        }

        return mergedBlocks.values
            .sortedWith(
                compareBy<ScreenTextBlock> { it.bounds.top / 24 }
                    .thenBy { it.bounds.left }
                    .thenBy { it.bounds.top },
            )
            .mapIndexed { index, block -> block.copy(orderIndex = index) }
    }

    override fun close() {
        recognizers.forEach(TextRecognizer::close)
    }
}
