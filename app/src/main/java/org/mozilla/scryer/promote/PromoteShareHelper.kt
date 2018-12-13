package org.mozilla.scryer.promote

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.support.v7.preference.PreferenceManager
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

        private fun getPref(context: Context): SharedPreferences {
            return PreferenceManager.getDefaultSharedPreferences(context)
        }
    }
}
