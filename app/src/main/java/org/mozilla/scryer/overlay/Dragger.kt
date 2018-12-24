/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.overlay

import android.content.Context
import android.view.View
import org.mozilla.scryer.extension.dpToPx

class Dragger(private val context: Context,
              private val targetView: View,
              width: Int,
              height: Int,
              private val windowCtrl: WindowController) {

    private lateinit var dragView: View
    private lateinit var dragHelper: DragHelper

    private val minTouchAreaSize = 48f.dpToPx(context.resources.displayMetrics)
    private val dragAreaWidth = Math.max(width, minTouchAreaSize)
    private val dragAreaHeight = Math.max(height, minTouchAreaSize)

    var dragListener: DragHelper.DragListener? = null

    init {
        initDragView()
    }

    fun updatePosition() {
        dragView.parent?.let {
            val offsetX = (dragAreaWidth - targetView.width) / 2
            val offsetY = (dragAreaHeight - targetView.height) / 2
            windowCtrl.moveViewTo(dragView, targetView.x.toInt() - offsetX, targetView.y.toInt() - offsetY)
        }
    }

    private fun initDragView() {
        this.dragView = View(context)
        //this.dragView.setBackgroundColor(Color.parseColor("#88ff0000"))

        this.dragHelper = DragHelper(dragView, windowCtrl)
        this.dragHelper.dragListener = object : DragHelper.DragListener {
            override fun onTouch() {
                dragListener?.onTouch()
            }

            override fun onTap() {
                dragListener?.onTap()
            }

            override fun onLongPress() {
                dragListener?.onLongPress()
            }

            override fun onRelease(x: Float, y: Float) {
                dragListener?.onRelease(x, y)
            }

            override fun onDrag(x: Float, y: Float) {
                dragListener?.onDrag(x, y)
            }
        }

        this.dragView.setOnTouchListener(dragHelper)
    }

    fun attachToWindow() {
        windowCtrl.addView(dragAreaWidth, dragAreaWidth, true, dragView)
        val radiusX = dragAreaWidth / 2
        val radiusY = dragAreaHeight / 2
        windowCtrl.moveViewTo(dragView, targetView.x.toInt() - radiusX, targetView.y.toInt() - radiusY)
    }

    fun detachFromWindow() {
        windowCtrl.removeView(dragView)
    }
}