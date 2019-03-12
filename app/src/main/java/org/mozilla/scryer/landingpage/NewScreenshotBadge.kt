/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.landingpage

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import org.mozilla.scryer.R

class NewScreenshotBadge(context: Context, attrs: AttributeSet) : AppCompatTextView(context, attrs) {
    var count: Int = 0
        set(value) {
            val coerce = value.coerceAtMost(999)
            field = coerce
            val suffix = if (value > 999) { "+" } else { "" }
            "$coerce$suffix".apply { text = this }
        }

    init {
        ContextCompat.getDrawable(context, R.drawable.rect_3dp)?.let {
            DrawableCompat.wrap(it.mutate())
        }?.apply {
            DrawableCompat.setTint(this, ContextCompat.getColor(context, R.color.errorRed))
            background = this
        }
    }
}