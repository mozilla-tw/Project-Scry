/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.promote

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import org.mozilla.scryer.R

class PromoteRatingHelper {
    companion object {
        private const val KEY_RATING_PROMOTED = "promote_cond_rating_promoted"

        fun shouldPromote(context: Context): Boolean {
            val pref = getPref(context)
            return pref.getInt(Promoter.KEY_SORT_SCREENSHOT, 0) >= 3
                    && !pref.getBoolean(KEY_RATING_PROMOTED, false)
        }

        fun onRatingPromoted(context: Context) {
            getPref(context).edit().putBoolean(KEY_RATING_PROMOTED, true).apply()
        }

        fun goToPlayStore(context: Context) {
            val appPackageName = context.packageName
            try {
                val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$appPackageName"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (ex: ActivityNotFoundException) {
                // No google play install
                val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }

        fun goToFeedback(context: Context) {
            val intent = Intent(Intent.ACTION_VIEW,
                    Uri.parse(context.getString(R.string.give_us_feedback_url)))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        fun getRatingDialog(
                context: Context,
                onPositive: () -> Unit,
                onNegative: () -> Unit
        ): AlertDialog {
            return PromoteDialogHelper.createPromoteDialog(context,
                    context.getString(R.string.dialogue_feedback_title),
                    context.getString(R.string.feedback_detail_letusknow),
                    ContextCompat.getDrawable(context, R.drawable.image_feedback),
                    context.getString(R.string.dialogue_feedback_action_5stars),
                    {
                        PromoteRatingHelper.goToPlayStore(context)
                        onPositive.invoke()
                    },
                    context.getString(R.string.dialogue_feedback_action_send),
                    {
                        PromoteRatingHelper.goToFeedback(context)
                        onNegative.invoke()
                    })
        }

        private fun getPref(context: Context): SharedPreferences {
            return PreferenceManager.getDefaultSharedPreferences(context)
        }
    }
}
