package eu.kanade.tachiyomi.data.translation

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.math.abs

data class TranslatedLine(
    val originalText: String,
    val translatedText: String,
    val boundingBox: Rect
)

class TranslationEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    private var sourceLang = TranslateLanguage.ENGLISH
    private var targetLang = TranslateLanguage.ITALIAN
    
    private var translator: Translator? = null

    init {
        initializeTranslator()
    }

    private fun initializeTranslator() {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        translator = Translation.getClient(options)
    }

    suspend fun translate(bitmap: Bitmap, cropBorders: Boolean = false): List<TranslatedLine> {
        val imageToProcess = if (cropBorders) {
            autoCrop(bitmap)
        } else {
            bitmap
        }
        
        val image = InputImage.fromBitmap(imageToProcess, 0)
        
        // 1. OCR
        val result = try {
            recognizer.process(image).await()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "OCR failed" }
            if (imageToProcess != bitmap) imageToProcess.recycle()
            return emptyList()
        }
        
        // 2. Merge Blocks
        // ML Kit TextBlock usually represents a paragraph/block of text.
        // We will use these blocks directly to avoid breaking sentences.
        val blocks = result.textBlocks

        // 3. Translate
        val translatedLines = mutableListOf<TranslatedLine>()
        val currentTranslator = translator ?: run {
            if (imageToProcess != bitmap) imageToProcess.recycle()
            return emptyList()
        }

        // Ensure model is downloaded
        val conditions = DownloadConditions.Builder()
            .build() // Allow cellular data for now, or make it configurable
            
        try {
            currentTranslator.downloadModelIfNeeded(conditions).await()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to download translation model" }
            if (imageToProcess != bitmap) imageToProcess.recycle()
            return emptyList()
        }

        for (block in blocks) {
            val box = block.boundingBox ?: continue
            // Replace newlines with spaces to treat the block as a single sentence
            val text = block.text.replace("\n", " ")
            
            try {
                val translatedText = currentTranslator.translate(text).await()
                translatedLines.add(TranslatedLine(text, translatedText, box))
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Translation failed for block: $text" }
            }
        }
        
        if (imageToProcess != bitmap) imageToProcess.recycle()
        
        return translatedLines
    }
    
    private fun autoCrop(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width == 0 || height == 0) return bitmap
        
        var top = 0
        var bottom = height
        var left = 0
        var right = width
        
        // Determine background color from top-left pixel
        val bgColor = bitmap.getPixel(0, 0)
        
        // Threshold for difference
        val threshold = 40 
        
        fun isBackground(x: Int, y: Int): Boolean {
            val pixel = bitmap.getPixel(x, y)
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val br = Color.red(bgColor)
            val bg = Color.green(bgColor)
            val bb = Color.blue(bgColor)
            return abs(r - br) < threshold && abs(g - bg) < threshold && abs(b - bb) < threshold
        }
        
        // Scan top
        for (y in 0 until height) {
            var rowIsBg = true
            for (x in 0 until width step 10) { 
                if (!isBackground(x, y)) {
                    rowIsBg = false
                    break
                }
            }
            if (!rowIsBg) {
                top = y
                break
            }
        }
        
        // Scan bottom
        for (y in height - 1 downTo top) {
            var rowIsBg = true
            for (x in 0 until width step 10) {
                if (!isBackground(x, y)) {
                    rowIsBg = false
                    break
                }
            }
            if (!rowIsBg) {
                bottom = y + 1
                break
            }
        }
        
        // Scan left
        for (x in 0 until width) {
            var colIsBg = true
            for (y in top until bottom step 10) {
                if (!isBackground(x, y)) {
                    colIsBg = false
                    break
                }
            }
            if (!colIsBg) {
                left = x
                break
            }
        }
        
        // Scan right
        for (x in width - 1 downTo left) {
            var colIsBg = true
            for (y in top until bottom step 10) {
                if (!isBackground(x, y)) {
                    colIsBg = false
                    break
                }
            }
            if (!colIsBg) {
                right = x + 1
                break
            }
        }
        
        if (left >= right || top >= bottom) return bitmap
        
        // If crop is minimal, return original
        if (left == 0 && top == 0 && right == width && bottom == height) return bitmap
        
        return try {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } catch (e: Exception) {
            bitmap
        }
    }
    
    fun setTargetLanguage(lang: String) {
        if (targetLang == lang) return
        targetLang = lang
        translator?.close()
        initializeTranslator()
    }

    fun setSourceLanguage(lang: String) {
        if (sourceLang == lang) return
        sourceLang = lang
        translator?.close()
        initializeTranslator()
    }
}
