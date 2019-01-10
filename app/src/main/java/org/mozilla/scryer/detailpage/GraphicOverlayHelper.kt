/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.detailpage

import com.google.firebase.ml.vision.text.FirebaseVisionText

class GraphicOverlayHelper {
    var blocks = listOf<TextBlockGraphic>()

    fun getSelectedText(): String {
        val selectedBlocks = blocks.filter { it.isSelected }.map { it.block }
        return buildFullTextString(selectedBlocks)
    }

    fun selectAllBlocks() {
        blocks.forEach { it.isSelected = true }
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
}
