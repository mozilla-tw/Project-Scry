package org.mozilla.scryer.detailpage

import android.graphics.*
import android.util.Log

import com.google.firebase.ml.vision.text.FirebaseVisionText

class TextGraphic internal constructor(overlay: GraphicOverlay, private val block: FirebaseVisionText.Block?) : GraphicOverlay.Graphic(overlay) {

    private val rectPaint: Paint = Paint()
    private val textPaint: Paint = Paint()

    init {
        rectPaint.color = Color.parseColor("#ccffcc00")
        rectPaint.style = Paint.Style.FILL_AND_STROKE
        rectPaint.strokeWidth = STROKE_WIDTH

        textPaint.color = Color.BLACK
        textPaint.textSize = TEXT_SIZE
        // Redraw the overlay, as this graphic has been added.
        postInvalidate()
    }

    /**
     * Draws the text block annotations for position, size, and raw value on the supplied canvas.
     */
    override fun draw(canvas: Canvas) {
        Log.d(TAG, "on draw text graphic")
        if (block == null) {
            throw IllegalStateException("Attempting to draw a null text.")
        }

        // Draws the bounding box around the TextBlock.
        val rect = RectF(block.boundingBox)
        canvas.drawRect(rect, rectPaint)

        for (line in block.lines.toMutableList().apply{ sortBy{ it.boundingBox?.centerY() } }) {
            line.boundingBox?.let {
                estimateSize(line.text, it)
                canvas.drawText(line.text, it.left.toFloat(), it.bottom.toFloat() /*- textPaint.fontMetrics.bottom*/, textPaint)
            }
        }
    }

    private fun estimateSize(text: String, boundingBox: Rect) {
        val width = boundingBox.width()

        var start = 1
        var end = 300
        val bound = Rect()
        while (start < end) {
            val size = start + (end - start) / 2
            textPaint.textSize = size.toFloat()
            textPaint.getTextBounds(text, 0, text.length, bound)

            val measureWidth = bound.right - bound.left
            val diffWidth = width - measureWidth
            if (diffWidth == 0) {
                return
            } else {
                if (diffWidth > 0) {
                    start = size + 1
                } else {
                    end = size - 1
                }
            }
        }
    }

    companion object {

        private const val TAG = "TextGraphic"
        private const val TEXT_COLOR = Color.RED
        private const val TEXT_SIZE = 54.0f
        private const val STROKE_WIDTH = 4.0f
    }
}
