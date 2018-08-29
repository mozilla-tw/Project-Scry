/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.overlay

import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class WindowController internal constructor(private val windowManager: WindowManager) {
    private val tmp: Point = Point()

    fun addView(width: Int, height: Int, isTouchable: Boolean, view: View) {
        val touchableFlag = if (isTouchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(width, height, windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or touchableFlag,
                PixelFormat.TRANSLUCENT)

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0

        windowManager.addView(view, params)
    }

    fun getViewPositionX(view: View): Int {
        val params = view.layoutParams as WindowManager.LayoutParams
        return params.x
    }

    fun getViewPositionY(view: View): Int {
        val params = view.layoutParams as WindowManager.LayoutParams
        return params.y
    }

    fun removeView(view: View) {
        view.parent?.run {
            windowManager.removeView(view)
        }
    }

    fun moveViewTo(view: View, x: Int, y: Int) {
        val params = view.layoutParams as WindowManager.LayoutParams
        params.x = x
        params.y = y
        windowManager.updateViewLayout(view, params)
    }

    fun getWindowWidth(): Int {
        windowManager.defaultDisplay.getSize(tmp)
        return tmp.x
    }

    fun getWindowHeight(): Int {
        windowManager.defaultDisplay.getSize(tmp)
        return tmp.y
    }
}
