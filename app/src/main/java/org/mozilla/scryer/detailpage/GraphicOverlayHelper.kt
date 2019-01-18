/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.detailpage

import com.google.firebase.ml.vision.text.FirebaseVisionText

class GraphicOverlayHelper(private val overlay: GraphicOverlay) {
    var blocks = listOf<TextBlockGraphic>()

    fun getSelectedText(): String {
        val selectedBlocks = blocks.filter { it.isSelected }.map { it.block }
        return buildFullTextString(selectedBlocks)
    }

    fun selectAllBlocks() {
        blocks.forEach { it.isSelected = true }
    }

    fun unselectAllBlocks() {
        blocks.forEach { it.isSelected = false }
    }

    private fun buildFullTextString(blocks: List<FirebaseVisionText.TextBlock>): String {
        val builder = StringBuilder()
        blocks.forEach { block ->
            val lines = block.lines.toMutableList().apply {
                sortBy {
                    it.boundingBox?.centerY()
                }
            }
            lines.forEach { line ->
                builder.append(line.text).append("\n")
            }
            builder.append("\n")
        }
        return builder.toString()
    }

    fun convertToGraphicBlocks(
            firebaseText: FirebaseVisionText,
            pageView: DetailPageAdapter.PageView
    ): List<TextBlockGraphic> {
        return convertToGraphicBlocks(convertToTextBlocks(firebaseText), pageView, overlay)
    }

    private fun convertToTextBlocks(
            firebaseText: FirebaseVisionText
    ): List<FirebaseVisionText.TextBlock> {
        return getSortedTextBlocks(firebaseText)
    }

    private fun convertToGraphicBlocks(
            textBlocks: List<FirebaseVisionText.TextBlock>,
            pageView: DetailPageAdapter.PageView,
            overlay: GraphicOverlay
    ): List<TextBlockGraphic> {
        // Since the image will be displayed in the manner of center-inside, we need to
        // transform each block so its size and position can be rendered correctly
        val transform = getTransform(pageView.getWidth(), pageView.getHeight(),
                pageView.getSourceImageWidth(), pageView.getSourceImageHeight())

        return textBlocks.map { textBlock ->
            TextBlockGraphic(overlay, textBlock).apply { transform(this) }
        }
    }

    private fun getTransform(
            viewportWidth: Int,
            viewportHeight: Int,
            imageWidth: Int,
            imageHeight: Int
    ): (graphic: GraphicOverlay.Graphic) -> Unit {
        val imageRatio = imageWidth / imageHeight.toFloat()
        val viewportRatio = viewportWidth / viewportHeight.toFloat()

        val scale: Float
        var translationX = 0f
        var translationY = 0f
        if (imageRatio >= viewportRatio) {
            scale = viewportWidth / imageWidth.toFloat()
            translationY = (viewportHeight.toFloat() - imageHeight * scale) / 2f

        } else {
            scale = viewportHeight / imageHeight.toFloat()
            translationX = (viewportWidth.toFloat() - imageWidth * scale) / 2f
        }

        return {
            it.scale = scale
            it.translationX = translationX
            it.translationY = translationY
        }
    }

    private fun getSortedTextBlocks(result: FirebaseVisionText): List<FirebaseVisionText.TextBlock> {
        return result.textBlocks.toMutableList().apply {
            sortBy { it.boundingBox?.centerY() }
        }
    }
}
