/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.RelativeLayout
import org.mozilla.scryer.R
import org.mozilla.scryer.extension.dpToPx

class CaptureButtonController(private val context: Context) {
    companion object {
        const val BUTTON_SIZE_DP = 40f
        const val EXIT_SCALE_RATIO = 76 / 52f

        const val ALPHA_ACTIVE = 1f
        const val ALPHA_INACTIVE = 0.50f
    }

    private lateinit var screen: Screen
    private lateinit var sideDock: Dock
    private lateinit var exitDock: Dock

    private lateinit var buttonView: FloatingView
    private lateinit var exitViewContainer: FloatingView
    private lateinit var exitAnchor: View
    private lateinit var exitCircleView: View

    private var clickListener: ClickListener? = null

    private var windowController: WindowController = WindowController(
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)

    private val metrics = context.resources.displayMetrics
    private val buttonSize = BUTTON_SIZE_DP.dpToPx(metrics)

    fun init() {
        screen = Screen(context, windowController)
        sideDock = Dock(screen)
        initExitView()
        buttonView = initCaptureButtonView(screen, sideDock)

        screen.onBoundaryUpdateListener = Runnable {
            buttonView.moveTo(sideDock)
        }
    }

    fun setOnClickListener(listener: ClickListener) {
        this.clickListener = listener
    }

    fun show() {
        screen.containerView.visibility = View.VISIBLE
    }

    fun hide() {
        screen.containerView.visibility = View.INVISIBLE
    }

    fun destroy() {
        buttonView.detachFromWindow()
        screen.detachFromWindow()
    }

    private fun initCaptureButtonView(screen: Screen, dock: Dock): FloatingView {
        val buttonView = FloatingView.create(createCaptureButtonView(context), dock, buttonSize, buttonSize,
                true, windowController)
        buttonView.alpha = ALPHA_INACTIVE

        buttonView.dragListener = object : DragHelper.DragListener {
            var isCollided = false
            val interpolator = FastOutSlowInInterpolator()

            override fun onTouch() {
                buttonView.alpha = ALPHA_ACTIVE
            }

            override fun onTap() {
                buttonView.alpha = ALPHA_INACTIVE
                clickListener?.onScreenshotButtonClicked()
            }

            override fun onLongPress() {

            }

            override fun onDrag(x: Float, y: Float) {
                exitViewContainer.visibility = View.VISIBLE

                val isCollide = isCollideWithExitView(x, y)
                if (isCollide && !isCollided) {
                    exitCircleView.animate()
                            .scaleX(EXIT_SCALE_RATIO)
                            .scaleY(EXIT_SCALE_RATIO)
                            .interpolator = interpolator
                    isCollided = true
                } else if (!isCollide && isCollided) {
                    exitCircleView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .interpolator = interpolator
                    isCollided = false
                }

//                exitCircleView.scaleX = if (isCollide) 1.5f else 1f
//                exitCircleView.scaleY = if (isCollide) 1.5f else 1f
//
//                if (isCollide(x, y)) {
//                    if (!buttonView.stickToCurrentPosition) {
//                        buttonView.stickToCurrentPosition = true
//                        buttonView.animateTo(exitDock, OvershootInterpolator(), 300)
//                    }
//                    exitBorder.scaleX = 1.5f
//                    exitBorder.scaleY = 1.5f
//                } else {
//                    buttonView.stickToCurrentPosition = false
//                }
            }

            override fun onRelease(x: Float, y: Float) {
                buttonView.alpha = ALPHA_INACTIVE

                if (isCollideWithExitView(x, y)) {
                    val animator = buttonView.animate().scaleX(0f).scaleY(0f)
                    animator.duration = 200
                    animator.interpolator = AccelerateInterpolator()
                    animator.setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            clickListener?.onScreenshotButtonDismissed()
                        }
                    })

                } else {
                    dock.updatePosition(x.toInt(), y.toInt())
                    buttonView.animateTo(dock, OvershootInterpolator(), 500)
                }
                exitViewContainer.visibility = View.INVISIBLE
            }
        }

        screen.addView(buttonView, buttonSize, buttonSize)
        buttonView.post {
            buttonView.moveTo(sideDock)
        }
        return buttonView
    }

    private fun createCaptureButtonView(context: Context): View {
        return View.inflate(context, R.layout.view_capture_button, null)
    }

    private fun initExitView()  {
        val exitViewContent = createExitView(context)
        exitViewContainer = FloatingView.create(exitViewContent, sideDock, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, false, windowController)
        val trashParams = RelativeLayout.LayoutParams(ViewGroup.MarginLayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
        trashParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        exitViewContainer.visibility = View.INVISIBLE
        screen.containerView.addView(exitViewContainer, trashParams)

        exitAnchor = exitViewContent.findViewById<View>(R.id.exit_button_anchor_view)
        exitCircleView = exitViewContent.findViewById<View>(R.id.border)

        exitDock = object : Dock(screen) {
            override fun resolveX(targetSize: Int): Float {
                return getCenterX(exitAnchor)
            }

            override fun resolveY(targetSize: Int): Float {
                return getCenterY(exitAnchor)
            }

            override fun updatePosition(x: Int, y: Int) {
            }
        }
    }

    private fun createExitView(context: Context): View {
        return View.inflate(context, R.layout.view_capture_button_exit, null)
    }

    private fun isCollideWithExitView(dragX: Float, dragY: Float): Boolean {
        val snapRadius = buttonSize
        val dstX = getCenterX(exitAnchor).toDouble()
        val dstY = getCenterY(exitAnchor).toDouble()
        val dist = Math.hypot(dstX - dragX, dstY - dragY)
        return dist <= snapRadius
    }

    private fun getCenterX(view: View): Float {
        return (view.left + view.right) / 2f
    }

    private fun getCenterY(view: View): Float {
        return (view.top + view.bottom) / 2f
    }

    interface ClickListener {
        fun onScreenshotButtonClicked()
        //fun onScreenshotButtonLongClicked() {}
        fun onScreenshotButtonDismissed() {}
    }
}
