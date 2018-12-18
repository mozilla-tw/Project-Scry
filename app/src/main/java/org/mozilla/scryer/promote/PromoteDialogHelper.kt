/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.promote

import android.content.Context
import android.graphics.drawable.Drawable
import android.support.v7.app.AlertDialog
import android.view.View
import android.widget.ImageView
import kotlinx.android.synthetic.main.dialog_promote.view.*
import org.mozilla.scryer.R

class PromoteDialogHelper {
    companion object {
        fun createPromoteDialog(
                context: Context,
                title: String,
                subtitle: String,
                drawable: Drawable?,
                positiveText: String,
                positiveListener: () -> Unit,
                negativeText: String,
                negativeListener: () -> Unit
        ): AlertDialog {
            val dialog = AlertDialog.Builder(context).create()
            val dialogView = View.inflate(context, R.layout.dialog_promote, null).let {
                it.title.text = title
                it.subtitle.text = subtitle
                drawable?.let { image ->
                    it.findViewById<ImageView>(R.id.image).setImageDrawable(image)
                }

                it.positive_button.text = positiveText
                it.positive_button.setOnClickListener { _ ->
                    dialog.dismiss()
                    positiveListener.invoke()
                }

                it.negative_button.text = negativeText
                it.negative_button.setOnClickListener { _ ->
                    dialog.dismiss()
                    negativeListener.invoke()
                }
                it
            }
            dialog.setView(dialogView)
            return dialog
        }
    }
}
