/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.PointF
import android.view.View
import android.widget.FrameLayout

class FloatingView(context: Context) : FrameLayout(context) {

    companion object {
        fun create(view: View, dock: Dock, width: Int, height: Int, draggable: Boolean,
                   windowCtrl: WindowController): FloatingView {
            val floatingView = FloatingView(view.context)
            floatingView.contentView = view
            floatingView.dock = dock
            floatingView.contentWidth = width
            floatingView.contentHeight = height
            floatingView.draggable = draggable
            floatingView.windowCtrl = windowCtrl
            return floatingView
        }
    }

    private lateinit var contentView: View
    private lateinit var dock: Dock
    private var contentWidth: Int = 0
    private var contentHeight: Int = 0
    private var draggable: Boolean = false
    private lateinit var windowCtrl: WindowController

    private val dragger: Dragger by lazy {
        Dragger(context, this, contentWidth, contentHeight, windowCtrl)
    }

    private val point = PointF()

    var dragListener: DragHelper.DragListener? = null
    var stickToCurrentPosition = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addView(this.contentView, this.contentWidth, this.contentHeight)
        if (this.draggable) {
            initDragger()
        }
    }

    fun moveTo(dock: Dock) {
        val self = this@FloatingView
        self.x = dock.resolveX(this.contentWidth) - this.contentWidth / 2
        self.y = dock.resolveY(this.contentHeight) - this.contentHeight / 2
        this.dragger.updatePosition()
    }

    fun animateTo(dock: Dock, interpolator: TimeInterpolator, duration: Long) {
        val self = this@FloatingView

        val resolveX = dock.resolveX(self.width)
        val resolveY = dock.resolveY(self.height)
        convertToOrigin(point.apply { set(resolveX, resolveY) }, self)

        val animator = self.animate().x(point.x).y(point.y)
                .setUpdateListener {
                    if (draggable) {
                        dragger.updatePosition()
                    }
                }
        animator.setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                dock.updatePosition(self.x.toInt(), self.y.toInt())
            }
        })
        animator.duration = duration
        animator.interpolator = interpolator
    }

    fun detachFromWindow() {
        this.dragger.detachFromWindow()
    }

    private fun initDragger() {
        this.dragger.dragListener = object : DragHelper.DragListener {
            override fun onTouch() {
                dragListener?.onTouch()
            }

            override fun onTap() {
                dragListener?.onTap()
            }

            override fun onLongPress() {
                dragListener?.onLongPress()
            }

            override fun onDrag(x: Float, y: Float) {
                val restrictedY = restrictY(y)
                if (!stickToCurrentPosition) {
                    val self = this@FloatingView
                    point.set(x, restrictedY)
                    convertToOrigin(point, self)

                    self.x = point.x
                    self.y = point.y
                }
                dragListener?.onDrag(x, restrictedY)
            }

            override fun onRelease(x: Float, y: Float) {
                dragListener?.onRelease(x, restrictY(y))
            }

            private fun restrictY(centerY: Float): Float {
                val radius = height / 2f
                return Math.min(Math.max(radius, centerY), ((parent as View).height - radius))
            }
        }
        this.dragger.attachToWindow()
    }
}

fun convertToOrigin(center: PointF, view: View) {
    val x = center.x
    val y = center.y
    val width = view.measuredWidth
    val height = view.measuredHeight
    center.set(x - width / 2f, y - height / 2f)
}

fun convertToCenter(origin: PointF, view: View) {
    val x = origin.x
    val y = origin.y
    val width = view.measuredWidth
    val height = view.measuredHeight
    origin.set(x + width / 2f, y + height / 2f)
}