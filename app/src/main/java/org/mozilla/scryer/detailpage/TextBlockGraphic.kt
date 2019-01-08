/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.detailpage

import android.graphics.*
import com.google.firebase.ml.vision.text.FirebaseVisionText

class TextBlockGraphic internal constructor(
        overlay: GraphicOverlay,
        private val block: FirebaseVisionText.Block?
) : GraphicOverlay.Graphic(overlay) {

    companion object {
        private val COLOR_NORMAL = Color.parseColor("#52000000")
    }

    private val rectPaint: Paint = Paint()

    init {
        rectPaint.apply {
            color = COLOR_NORMAL
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        postInvalidate()
    }

    override fun draw(canvas: Canvas) {
        if (block == null) {
            throw IllegalStateException("Attempting to draw a null text.")
        }

        // Draws the bounding box around the TextBlock.
        val rect = RectF(block.boundingBox)
        canvas.drawRect(rect, rectPaint)
    }
}
