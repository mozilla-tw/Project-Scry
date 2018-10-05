/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.ui

import android.content.Context
import android.content.DialogInterface
import android.support.v7.app.AlertDialog
import android.view.View
import android.widget.TextView
import org.mozilla.scryer.R

class ConfirmationDialog private constructor(
        private val dialog: AlertDialog,
        val viewHolder: ViewHolder
) {
    companion object {
        fun build(
                context: Context,
                title: String,
                positiveButtonText: String,
                positiveButtonListener: DialogInterface.OnClickListener,
                negativeButtonText: String,
                negativeButtonListener: DialogInterface.OnClickListener
        ): ConfirmationDialog {
            val view = View.inflate(context, R.layout.dialog_confirmation, null)
            val dialog = AlertDialog.Builder(context)
                    .setView(view)
                    .setTitle(title)
                    .setPositiveButton(positiveButtonText, positiveButtonListener)
                    .setNegativeButton(negativeButtonText, negativeButtonListener)
                    .create()
            val holder = ViewHolder()
            holder.message = view.findViewById(R.id.confirmation_message)
            holder.subMessage = view.findViewById(R.id.confirmation_message_content_first_line)
            holder.subMessage2 = view.findViewById(R.id.confirmation_message_content_second_line)
            return ConfirmationDialog(dialog, holder)
        }
    }

    fun asAlertDialog(): AlertDialog {
        return dialog
    }

    class ViewHolder {
        var message: TextView? = null
        var subMessage: TextView? = null
        var subMessage2: TextView? = null
    }
}