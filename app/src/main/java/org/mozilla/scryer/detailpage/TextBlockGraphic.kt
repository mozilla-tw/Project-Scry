/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.detailpage

import android.graphics.*
import com.google.firebase.ml.vision.text.FirebaseVisionText
import org.mozilla.scryer.BuildConfig
import org.mozilla.scryer.extension.dpToPx

class TextBlockGraphic internal constructor(
        overlay: GraphicOverlay,
        val block: FirebaseVisionText.TextBlock
) : GraphicOverlay.Graphic(overlay) {

    companion object {
        private val COLOR_NORMAL = Color.parseColor("#52000000")
        private const val CORNER_RADIUS_DP = 2f
    }

    private val cornerRadius = CORNER_RADIUS_DP.dpToPx(overlay.context.resources.displayMetrics).toFloat()
    private val boundingBox = RectF()

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

    var scale = 1f
    var translationX = 0f
    var translationY = 0f

    init {
        rectPaint.apply {
            color = COLOR_NORMAL
            style = Paint.Style.FILL
        }
        postInvalidate()
    }

    override fun draw(canvas: Canvas) {
        boundingBox.set(block.boundingBox)
        rectPaint.xfermode = if (isSelected) {
            PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        } else {
            PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }

        val tX = +translationX
        val tY = +translationY

        val left = boundingBox.left * scale + tX
        val top = boundingBox.top * scale + tY
        val width = (boundingBox.right - boundingBox.left) * scale
        val height = (boundingBox.bottom - boundingBox.top) * scale

        boundingBox.set(left, top, left + width, top + height)
        canvas.drawRoundRect(boundingBox, cornerRadius, cornerRadius, rectPaint)
        debugPaint?.takeIf { isSelected }?.let {
            canvas.drawText("($left, $top, ${left + width}, ${top + height})", left, top, it)
        }
    }
}
