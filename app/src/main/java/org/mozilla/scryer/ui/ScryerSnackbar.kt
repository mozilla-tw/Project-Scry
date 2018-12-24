package org.mozilla.scryer.ui

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.snackbar.Snackbar
import org.mozilla.scryer.R

class ScryerSnackbar {
    companion object {

        fun make(view: View, text: String, duration: Int): Snackbar {
            val bar = Snackbar.make(view, text, duration)

            ContextCompat.getDrawable(view.context, R.drawable.rect_4dp)?.apply {
                val wrapped = DrawableCompat.wrap(this)
                DrawableCompat.setTint(wrapped, ContextCompat.getColor(view.context, R.color.primaryTeal))
                bar.view.background = wrapped

                val params = bar.view.layoutParams
                // TODO: Visual spec for margin
                val margin = view.resources.getDimensionPixelSize(R.dimen.toast_horizontal_margin)
                if (params is ViewGroup.MarginLayoutParams) {
                    params.marginStart = margin
                    params.marginEnd = margin
                    params.bottomMargin = margin
                }
                bar.view.layoutParams = params
                bar.setActionTextColor(Color.WHITE)
            }

            return bar
        }
    }
}
