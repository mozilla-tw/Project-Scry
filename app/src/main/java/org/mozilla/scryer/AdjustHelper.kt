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
import com.adjust.sdk.AdjustEvent
import com.adjust.sdk.LogLevel

const val ADJUST_EVENT_CAPTURE_VIA_FAB = "ltd7wr"
const val ADJUST_EVENT_FEEDBACK_POSITIVE = "i7wmh5"
const val ADJUST_EVENT_SHARE_APP = "er82lg"
const val ADJUST_EVENT_SORT_SCREENSHOT = "3odfiz"
const val ADJUST_EVENT_START_SEARCH = "g6icdf"
const val ADJUST_EVENT_VIEW_TEXT_IN_SCREENSHOT = "t7ubav"

class AdjustHelper {
    companion object {
        fun init(application: Application) {
            val token = BuildConfig.ADJUST_TOKEN.takeIf { it.isNotEmpty() } ?: return

            val config = AdjustConfig(application, token, BuildConfig.ADJUST_ENVIRONMENT, true)
            config.setSendInBackground(true)
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

        fun trackEvent(eventToken: String) {
            Adjust.trackEvent(AdjustEvent(eventToken))
        }
    }
}
