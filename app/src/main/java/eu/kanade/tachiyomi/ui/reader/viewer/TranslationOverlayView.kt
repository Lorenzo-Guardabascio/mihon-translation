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
import eu.kanade.tachiyomi.data.translation.TranslatedLine
import kotlin.math.max
import kotlin.math.min

class TranslationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

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
            drawMultilineText(canvas, line.translatedText, viewRect)
        }
    }
    
    private fun drawMultilineText(canvas: Canvas, text: String, rect: RectF) {
        val width = rect.width().toInt()
        if (width <= 0) return

        // Dynamic text size calculation
        var textSize = 50f
        textPaint.textSize = textSize
        
        // Simple heuristic: try to fit text area into rect area
        // Area = width * height. Text Area approx = char_count * char_area
        // This is rough. Better to just measure.
        
        // Binary search or iterative reduction for font size
        // We want the text to fit within rect.height() when wrapped at rect.width()
        
        var layout: StaticLayout
        while (textSize > 10f) {
            textPaint.textSize = textSize
            layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.0f)
                .setIncludePad(false)
                .build()
            
            if (layout.height <= rect.height()) {
                break
            }
            textSize -= 2f
        }
        
        // Final layout
        textPaint.textSize = textSize
        layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(false)
            .build()

        canvas.withSave {
            translate(rect.left, rect.top + (rect.height() - layout.height) / 2)
            layout.draw(canvas)
        }
    }
}
