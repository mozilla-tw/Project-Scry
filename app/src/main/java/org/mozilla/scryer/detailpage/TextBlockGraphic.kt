/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.detailpage

import android.graphics.*
import com.google.firebase.ml.vision.text.FirebaseVisionText

class TextBlockGraphic internal constructor(
        overlay: GraphicOverlay,
        val block: FirebaseVisionText.TextBlock?
) : GraphicOverlay.Graphic(overlay) {

    companion object {
        private val COLOR_NORMAL = Color.parseColor("#52000000")
    }

    private val rectPaint: Paint = Paint()
    var isSelected: Boolean = false
        set(value) {
            field = value
            postInvalidate()
        }

    init {
        rectPaint.apply {
            color = COLOR_NORMAL
            style = Paint.Style.FILL
        }
        postInvalidate()
    }

    override fun draw(canvas: Canvas) {
        if (block == null) {
            throw IllegalStateException("Attempting to draw a null text.")
        }

        // Draws the bounding box around the TextBlock.
        val rect = RectF(block.boundingBox)
        rectPaint.xfermode = if (isSelected) {
            PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        } else {
            PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }

        canvas.drawRect(rect, rectPaint)
    }
}
