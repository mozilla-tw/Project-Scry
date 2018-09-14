/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.preference

import android.content.Context
import android.content.SharedPreferences

// TODO: Figure out a better api design for this class
class PreferenceWrapper(context: Context) {
    companion object {
        private const val PREF_NAME = "secondary_pref"
    }

    // TODO: What about letting caller be responsible for providing PrefConfig instead of
    // hard-coding it here in the PreferenceManager
    private val prefEnableService = PrefConfig("prompt_service_enable", false)

    private val pref: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun shouldPromptEnableService(): Boolean {
        return pref.getBoolean(prefEnableService.key, prefEnableService.defaultValue)
    }

    fun setShouldPromptEnableService(shouldPrompt: Boolean) {
        pref.edit().putBoolean(prefEnableService.key, shouldPrompt).apply()
    }
}

open class PrefConfig<T>(val key: String, val defaultValue: T)
