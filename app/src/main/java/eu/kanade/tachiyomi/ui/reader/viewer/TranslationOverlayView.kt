package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.withSave
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.chrisbanes.photoview.PhotoView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.data.translation.TranslatedLine
import uy.kohesive.injekt.injectLazy
import kotlin.math.max
import kotlin.math.min

class TranslationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val readerPreferences: ReaderPreferences by injectLazy()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return

        readerPreferences.translationBackgroundAlpha().changes()
            .onEach { invalidate() }
            .launchIn(scope)

        readerPreferences.translationFontSize().changes()
            .onEach { invalidate() }
            .launchIn(scope)
    }

    private var translatedLines: List<TranslatedLine> = emptyList()
    private var pageView: View? = null
    
    private val backgroundPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = 200
    }
    
    private val textPaint = TextPaint().apply {
        color = Color.BLACK
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    
    fun setTranslations(lines: List<TranslatedLine>) {
        translatedLines = lines
        invalidate()
    }
    
    fun attachToPageView(view: View) {
        pageView = view
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val view = pageView ?: return
        if (translatedLines.isEmpty()) return

        val bgAlpha = (readerPreferences.translationBackgroundAlpha().get() * 255).toInt().coerceIn(0, 255)
        backgroundPaint.alpha = bgAlpha
        val fontScale = readerPreferences.translationFontSize().get()

        for (line in translatedLines) {
            val imageRect = line.boundingBox
            val viewRect = RectF()
            
            if (view is SubsamplingScaleImageView && view.isReady) {
                val topLeft = view.sourceToViewCoord(imageRect.left.toFloat(), imageRect.top.toFloat())
                val bottomRight = view.sourceToViewCoord(imageRect.right.toFloat(), imageRect.bottom.toFloat())
                if (topLeft != null && bottomRight != null) {
                    viewRect.set(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
                }
            } else if (view is PhotoView) {
                val displayRect = view.displayRect ?: continue
                val drawable = view.drawable ?: continue
                
                if (drawable.intrinsicWidth == 0 || drawable.intrinsicHeight == 0) continue

                val scaleX = displayRect.width() / drawable.intrinsicWidth
                val scaleY = displayRect.height() / drawable.intrinsicHeight
                
                viewRect.left = displayRect.left + imageRect.left * scaleX
                viewRect.top = displayRect.top + imageRect.top * scaleY
                viewRect.right = displayRect.left + imageRect.right * scaleX
                viewRect.bottom = displayRect.top + imageRect.bottom * scaleY
            } else if (view is ImageView) {
                val drawable = view.drawable ?: continue
                if (drawable.intrinsicWidth == 0 || drawable.intrinsicHeight == 0) continue

                // Assuming FIT_CENTER or similar logic where image is centered or fills view
                // For Webtoon (adjustViewBounds=true), the view size matches the scaled image size
                val scaleX = view.width.toFloat() / drawable.intrinsicWidth
                val scaleY = view.height.toFloat() / drawable.intrinsicHeight
                
                // Use the smaller scale to fit inside (if aspect ratios differ)
                // But with adjustViewBounds, they should match.
                // Let's assume simple scaling for now.
                
                viewRect.left = imageRect.left * scaleX
                viewRect.top = imageRect.top * scaleY
                viewRect.right = imageRect.right * scaleX
                viewRect.bottom = imageRect.bottom * scaleY
            } else {
                continue
            }
            
            // Draw background
            canvas.drawRect(viewRect, backgroundPaint)
            
            // Text fitting with StaticLayout
            drawMultilineText(canvas, line.translatedText, viewRect, fontScale)
        }
    }
    
    private fun drawMultilineText(canvas: Canvas, text: String, rect: RectF, fontScale: Float) {
        val width = rect.width().toInt()
        if (width <= 0) return

        // 1. Find the optimal size that fits the box (without scale)
        var textSize = 100f // Start big
        val minSize = 10f
        
        var layout: StaticLayout
        
        // Iterative reduction to find "fit" size
        while (textSize > minSize) {
            textPaint.textSize = textSize
            layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.0f)
                .setIncludePad(false)
                .setEllipsize(null)
                .setMaxLines(Integer.MAX_VALUE)
                .build()
            
            if (layout.height <= rect.height()) {
                break
            }
            textSize -= 2f
        }
        
        // 2. Apply user preference scale to the "fitting" size
        // If fontScale is 1.0, it stays as "fitting size".
        // If 2.0, it doubles (and overflows).
        // If 0.5, it shrinks.
        textSize *= fontScale
        
        // Ensure we don't go below readable size
        textSize = max(textSize, minSize)

        // 3. Final Layout
        textPaint.textSize = textSize
        layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(false)
            .setEllipsize(null)
            .setMaxLines(Integer.MAX_VALUE)
            .build()

        canvas.withSave {
            // Center vertically if it fits, otherwise align top
            val yOffset = if (layout.height < rect.height()) {
                (rect.height() - layout.height) / 2
            } else {
                0f
            }
            translate(rect.left, rect.top + yOffset)
            
            // Clip to rect if it overflows? 
            // User might want to see the text even if it overflows the original bubble.
            // Let's NOT clip for now, as bubbles are often smaller than the text we want to read.
            
            layout.draw(canvas)
        }
    }
}
