/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.ui

import android.graphics.Rect
import androidx.recyclerview.widget.RecyclerView
import android.view.View

open class GridItemDecoration(private val columnCount: Int,
                              private val left: Int,
                              private val top: Int,
                              private val right: Int,
                              private val vSpace: Int,
                              private val hSpace: Int) : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {

    constructor(columnCount: Int, space: Int) : this(columnCount, space, space, space, space, space)

    override fun getItemOffsets(outRect: Rect, view: View,
                                parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
        val position = parent.getChildViewHolder(view).adapterPosition
        if (position < 0) {
            return
        }

        setSpaces(outRect, position)
    }

    open fun setSpaces(outRect: Rect, position: Int) {
        outRect.left = if (position % columnCount == 0) left else hSpace / 2
        outRect.top = if (position < columnCount) top else 0
        outRect.right = if (position % columnCount == columnCount - 1) right else hSpace / 2
        outRect.bottom = vSpace
    }
}