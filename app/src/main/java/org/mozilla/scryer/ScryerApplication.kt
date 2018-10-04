/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer

import android.app.Application
import org.mozilla.scryer.repository.ScreenshotRepository
import org.mozilla.scryer.setting.PreferenceSettingsRepository
import org.mozilla.scryer.setting.SettingsRepository
import org.mozilla.scryer.telemetry.TelemetryWrapper

class ScryerApplication : Application() {
    companion object {
        private val instance: ScryerApplication by lazy {
            ApplicationHolder.instance
        }

        fun getScreenshotRepository(): ScreenshotRepository {
            return instance.screenshotRepository
        }

        fun getSettingsRepository(): SettingsRepository {
            return instance.settingsRepository
        }
    }

    private object ApplicationHolder {
        lateinit var instance: ScryerApplication
    }

    lateinit var screenshotRepository: ScreenshotRepository
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        ApplicationHolder.instance = this
        TelemetryWrapper.init(this)
        AdjustHelper.init(this)

        screenshotRepository = ScreenshotRepository.createRepository(this) {
            screenshotRepository.setupDefaultContent(this)
        }
        settingsRepository = PreferenceSettingsRepository.getInstance(this)
    }
}
