/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.ui

import android.graphics.Rect
import androidx.recyclerview.widget.RecyclerView
import android.view.View

class InnerSpaceDecoration(private val space: Int,
                           private val columnCountProvider: () -> Int) : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
        /**
         * x: padding for left/right-most items (left-most item only have right padding, vice versa)
         * y: padding for middle items (padding at both left & right sides)
         *
         * size of left/right-most items must be equal to the size of middle items
         *   x = 2y
         *
         * 2x + 2(middleItemCount)y = sum of all padding
         * => 2x + 2(itemCount - 2)y = space * (itemCount - 1)
         * => 4y + 2(itemCount - 2)y = space * (itemCount - 1)
         * => y = space * (itemCount  - 1) / (4 + 2 * (itemCount - 2))y
         */
        val position = parent.getChildAdapterPosition(view)
        val columnCount = columnCountProvider.invoke()
        val y = space * (columnCount - 1) / (4 + 2 * (columnCount - 2))
        val x = y * 2

        outRect.top = 0
        outRect.bottom = space
        when {
            position % columnCount == 0 -> {
                outRect.left = 0
                outRect.right = x
            }
            position % columnCount == columnCount - 1 -> {
                outRect.left = x
                outRect.right = 0
            }
            else -> {
                outRect.left = y
                outRect.right = y
            }
        }
    }
}