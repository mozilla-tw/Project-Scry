/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.promote

import android.content.Context
import android.content.SharedPreferences
import android.support.v7.preference.PreferenceManager

class Promoter {
    companion object {

        private const val KEY_TAKE_SCREENSHOT = "promote_cond_take_screenshot"
        private const val KEY_SORT_SCREENSHOT = "promote_cond_sort_screenshot"
        private const val KEY_TAP_OCR = "promote_cond_tap_ocr_button"

        fun onScreenshotTaken(context: Context) {
            incPref(context, KEY_TAKE_SCREENSHOT)
        }

        fun onScreenshotSorted(context: Context) {
            incPref(context, KEY_SORT_SCREENSHOT)
        }

        fun onOcrButtonClicked(context: Context) {
            incPref(context, KEY_TAP_OCR)
        }

        private fun getPref(context: Context): SharedPreferences {
            return PreferenceManager.getDefaultSharedPreferences(context)
        }

        private fun incPref(context: Context, key: String) {
            val pref = getPref(context)
            val old = pref.getInt(key, 0)
            pref.edit().putInt(key, old + 1).apply()
        }
    }
}
