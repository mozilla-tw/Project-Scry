package org.mozilla.scryer.detailpage

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log

import com.google.firebase.ml.vision.text.FirebaseVisionText

class TextGraphic internal constructor(overlay: GraphicOverlay, private val block: FirebaseVisionText.Block?) : GraphicOverlay.Graphic(overlay) {

    private val rectPaint: Paint = Paint()
    private val textPaint: Paint = Paint()

    init {
        rectPaint.color = TEXT_COLOR
        rectPaint.style = Paint.Style.STROKE
        rectPaint.strokeWidth = STROKE_WIDTH

        textPaint.color = TEXT_COLOR
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

        for (line in block.lines.toMutableList().apply{ sortBy{ it.boundingBox?.centerY() } }) {
            line.boundingBox?.let {
                canvas.drawText(line.text, it.left.toFloat(), it.bottom.toFloat(), textPaint)
            }
        }

        // Draws the bounding box around the TextBlock.
        val rect = RectF(block.boundingBox)
        canvas.drawRect(rect, rectPaint)

        // Renders the text at the bottom of the box.
        canvas.drawText(block.text, rect.left, rect.bottom, textPaint)
    }

    companion object {

        private const val TAG = "TextGraphic"
        private const val TEXT_COLOR = Color.RED
        private const val TEXT_SIZE = 54.0f
        private const val STROKE_WIDTH = 4.0f
    }
}
