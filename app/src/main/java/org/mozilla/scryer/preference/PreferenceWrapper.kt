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
    // hard-coding it here in the PreferenceManager?
    private val shouldPromptEnableService = PrefConfig("prompt_service_enable", false)
    private val isFirstTimeLaunch = PrefConfig("first_time_launch", true)
    private val grantStoragePermissionCount = PrefConfig("grant_storage_permission_count", 1)
    private val isOcrOnboardingShown = PrefConfig("ocr_onboarding_shown", false)
    private val isSearchOnboardingShown = PrefConfig("search_onboarding_shown", false)

    private val pref: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isFirstTimeLaunch(): Boolean {
        return pref.getBoolean(isFirstTimeLaunch.key, isFirstTimeLaunch.defaultValue)
    }

    fun setFirstTimeLaunched() {
        pref.edit().putBoolean(isFirstTimeLaunch.key, false).apply()
    }

    fun shouldPromptEnableService(): Boolean {
        return pref.getBoolean(shouldPromptEnableService.key, shouldPromptEnableService.defaultValue)
    }

    fun setShouldPromptEnableService(shouldPrompt: Boolean) {
        pref.edit().putBoolean(shouldPromptEnableService.key, shouldPrompt).apply()
    }

    fun getAndIncreaseGrantStoragePermissionCount(): Int {
        val count = pref.getInt(grantStoragePermissionCount.key, 1)
        pref.edit().putInt(grantStoragePermissionCount.key, (count + 1)).apply()
        return count
    }

    fun isOcrOnboardingShown(): Boolean {
        return pref.getBoolean(isOcrOnboardingShown.key, isOcrOnboardingShown.defaultValue)
    }

    fun setOcrOnboardingShown() {
        pref.edit().putBoolean(isOcrOnboardingShown.key, true).apply()
    }

    fun isSearchOnboardingShown(): Boolean {
        return pref.getBoolean(isSearchOnboardingShown.key, isSearchOnboardingShown.defaultValue)
    }

    fun setSearchOnboardingShown() {
        pref.edit().putBoolean(isSearchOnboardingShown.key, true).apply()
    }
}

open class PrefConfig<T>(val key: String, val defaultValue: T)
