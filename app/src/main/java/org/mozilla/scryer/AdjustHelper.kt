/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.LogLevel

class AdjustHelper {
    companion object {
        fun init(application: Application) {
            val token = BuildConfig.ADJUST_TOKEN.takeIf { it.isNotEmpty() } ?: return

            val config = AdjustConfig(application, token, BuildConfig.ADJUST_ENVIRONMENT, true)
            config.setLogLevel(LogLevel.DEBUG)
            Adjust.onCreate(config)

            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityPaused(activity: Activity?) {
                    Adjust.onPause()
                }

                override fun onActivityResumed(activity: Activity?) {
                    Adjust.onResume()
                }

                override fun onActivityStarted(activity: Activity?) {
                }

                override fun onActivityDestroyed(activity: Activity?) {
                }

                override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
                }

                override fun onActivityStopped(activity: Activity?) {
                }

                override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
                }
            })
        }
    }
}
