/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.mozilla.scryer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.navigation.fragment.NavHostFragment

class NavHostInsetAwareFragment : NavHostFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val frameLayout = object : FrameLayout(inflater.context) {
            var lastInsets: WindowInsets? = null

            override fun onApplyWindowInsets(insets: WindowInsets?): WindowInsets {
                if (lastInsets != insets) {
                    lastInsets = insets
                    requestLayout()
                }
                return insets?.consumeSystemWindowInsets() ?: super.onApplyWindowInsets(insets)
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                lastInsets?.let { insets ->
                    (0 until childCount).map { getChildAt(it) }.forEach { child ->
                        child.dispatchApplyWindowInsets(insets)
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        frameLayout.id = this.id
        return frameLayout
    }
}