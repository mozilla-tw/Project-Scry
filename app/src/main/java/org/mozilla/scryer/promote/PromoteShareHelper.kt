/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.promote

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import android.view.View
import org.mozilla.scryer.R

class PromoteShareHelper {
    companion object {
        private const val KEY_SHARING_PROMOTED = "promote_cond_sharing_promoted"

        const val REASON_SHOT = 0
        const val REASON_SORT = 1
        const val REASON_OCR = 2

        fun getShareReason(context: Context): Int {
            val pref = getPref(context)
            if (pref.getBoolean(KEY_SHARING_PROMOTED, false)) {
                return -1
            }
            if (pref.getInt(Promoter.KEY_TAKE_SCREENSHOT, 0) >= 10) {
                return REASON_SHOT
            }
            if (pref.getInt(Promoter.KEY_SORT_SCREENSHOT, 0) >= 10) {
                return REASON_SORT
            }
            if (pref.getInt(Promoter.KEY_TAP_OCR, 0) >= 5) {
                return REASON_OCR
            }
            return -1
        }

        fun onSharingPromoted(context: Context) {
            getPref(context).edit().putBoolean(KEY_SHARING_PROMOTED, true).apply()
        }

        fun showShareAppDialog(context: Context) {
            val sendIntent = Intent(Intent.ACTION_SEND)
            sendIntent.type = "text/plain"
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_full_name))
            sendIntent.putExtra(Intent.EXTRA_TEXT,
                    context.getString(R.string.share_intro,
                            context.getString(R.string.app_full_name),
                            context.getString(R.string.share_app_google_play_url)))
            context.startActivity(Intent.createChooser(sendIntent, null))
        }

        fun getShareDialog(
                context: Context,
                reason: Int,
                onPositive: (() -> Unit)? = null,
                onNegative: (() -> Unit)? = null
        ): AlertDialog? {
            val subtitleId = when (reason) {
                PromoteShareHelper.REASON_SHOT -> R.string.dialogue_take_share_description
                PromoteShareHelper.REASON_SORT -> R.string.dialogue_sort_share_description
                PromoteShareHelper.REASON_OCR -> R.string.dialogue_ocr_share_description
                else -> return null
            }

            val dialog = PromoteDialogHelper.createPromoteDialog(context,
                    context.getString(R.string.dialogue_share_title),
                    context.getString(subtitleId),
                    ContextCompat.getDrawable(context, R.drawable.image_share),
                    context.getString(R.string.action_share),
                    {
                        PromoteShareHelper.showShareAppDialog(context)
                        onPositive?.invoke()
                    },
                    context.getString(R.string.sheet_action_no),
                    {
                        onNegative?.invoke()
                    })
            dialog.setOnShowListener {
                dialog.findViewById<View>(R.id.button_divider)?.visibility = View.GONE
                dialog.findViewById<View>(R.id.negative_button)?.visibility = View.GONE
            }
            return dialog
        }

        private fun getPref(context: Context): SharedPreferences {
            return PreferenceManager.getDefaultSharedPreferences(context)
        }
    }
}
