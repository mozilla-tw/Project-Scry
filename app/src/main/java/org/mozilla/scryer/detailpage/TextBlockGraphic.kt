/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.detailpage

import android.graphics.*
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.text.FirebaseVisionText
import org.mozilla.scryer.BuildConfig
import org.mozilla.scryer.R
import org.mozilla.scryer.extension.dpToPx

class TextBlockGraphic internal constructor(
        val overlay: GraphicOverlay
) : GraphicOverlay.Graphic(overlay) {

    companion object {
        private const val colorNormalRes = android.R.color.black
        private const val alphaNormal = 0.32f

        private const val colorSelectedRes = R.color.primaryTeal
        private const val alphaSelected = 0.3f

        private const val colorHighlightRes = R.color.primaryTeal
        private const val alphaHighlight = 0.3f

        private const val CORNER_RADIUS_DP = 2f
    }

    private val cornerRadius = CORNER_RADIUS_DP.dpToPx(overlay.context.resources.displayMetrics).toFloat()
    val boundingBox = RectF()

    private val normalColor: Int = ContextCompat.getColor(overlay.context, colorNormalRes).let {
        Color.argb((255 * alphaNormal).toInt(), Color.red(it), Color.green(it), Color.blue(it))
    }

    private val selectedColor: Int = ContextCompat.getColor(overlay.context, colorSelectedRes).let {
        Color.argb((255 * alphaSelected).toInt(), Color.red(it), Color.green(it), Color.blue(it))
    }

    private val highlightColor: Int = ContextCompat.getColor(overlay.context, colorHighlightRes).let {
        Color.argb((255 * alphaHighlight).toInt(), Color.red(it), Color.green(it), Color.blue(it))
    }

    private val debugPaint: Paint? = if (BuildConfig.DEBUG) {
        Paint().apply {
            color = Color.RED
            textSize = 12f.dpToPx(overlay.context.resources.displayMetrics).toFloat()
            isFakeBoldText = true
        }
    } else {
        null
    }

    private val rectPaint: Paint = Paint()

    var isSelected: Boolean = false
        set(value) {
            field = value
            postInvalidate()
        }

    var textBlock: FirebaseVisionText.TextBlock? = null
    var lineBlock: FirebaseVisionText.Line? = null
    var elementBlock: FirebaseVisionText.Element? = null

    init {
        rectPaint.apply {
            style = Paint.Style.FILL
        }
        postInvalidate()
    }

    fun isTextBlock(): Boolean {
        return textBlock != null
    }

    fun isLineBlock(): Boolean {
        return lineBlock != null
    }

    @Suppress("unused")
    fun isElementBlock(): Boolean {
        return elementBlock != null
    }

    override fun draw(canvas: Canvas) {
        boundingBox.set(getBoundingBox() ?: return)

        if (overlay.overlayMode == GraphicOverlay.MODE_HIGHLIGHT) {
            rectPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            rectPaint.color = highlightColor
        } else {
            rectPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            rectPaint.color = if (isSelected) {
                selectedColor
            } else {
                normalColor
            }
        }

        val left = boundingBox.left * scale + translationX
        val top = boundingBox.top * scale + translationY
        val width = (boundingBox.right - boundingBox.left) * scale
        val height = (boundingBox.bottom - boundingBox.top) * scale

        boundingBox.set(left, top, left + width, top + height)
        canvas.drawRoundRect(boundingBox, cornerRadius, cornerRadius, rectPaint)
        debugPaint?.takeIf { isSelected }?.let {
            canvas.drawText("($left, $top, ${left + width}, ${top + height})", left, top, it)
        }
    }

    private fun getBoundingBox(): Rect? {
        return textBlock?.boundingBox ?: lineBlock?.boundingBox ?: elementBlock?.boundingBox
    }
}
