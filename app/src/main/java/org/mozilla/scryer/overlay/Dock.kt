/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.overlay

open class Dock(val screen: Screen) {
    companion object {
        private const val DOCK_OFFSET_FACTOR = 0.1f
    }

    var side: Side = Side.Right
    private var yPositionPercentage: Float = 1 / 2f

    init {

    }

    open fun resolveX(targetSize: Int): Float {
        val offset = DOCK_OFFSET_FACTOR * targetSize
        return if (side == Side.Left) {
            (targetSize / 2) - offset
        } else {
            (screen.width - targetSize / 2) + offset
        }
    }

    open fun resolveY(targetSize: Int): Float {
        return screen.height * yPositionPercentage
    }

    open fun updatePosition(x: Int, y: Int) {
        yPositionPercentage = y / screen.height.toFloat()
        side = if (x < screen.width / 2) Dock.Side.Left else Dock.Side.Right
    }

    sealed class Side {
        object Left: Side()
        object Right: Side()
    }
}