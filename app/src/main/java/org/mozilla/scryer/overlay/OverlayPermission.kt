/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.overlay

import android.annotation.TargetApi
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.support.annotation.RequiresApi

object OverlayPermission {

    fun hasPermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT == Build.VERSION_CODES.O -> {
                return Settings.canDrawOverlays(context) || hasPermissionV26Workaround(context)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                Settings.canDrawOverlays(context)
            }

            else -> true
        }
    }

    /**
     * On Android O, Settings.canDrawOverlays() will keep returning false after user grant and resume
     * back from system setting, it will start to return true after being paused again.
     * In this case we use AppOpsManager to check for the permission.
     *
     * Scenarios
     * 1. Not grant: canDrawOverlays is false, Op is 2 (MODE_ERRORED)
     * 2. Grant then resume: canDrawOverlay is false, Op is 1 (MODE_IGNORED)
     * 3. Grant then resume then pause: canDrawOverlay is true, Op is 0 (MODE_ALLOWED)
     *
     * Reference:
     * https://stackoverflow.com/questions/46173460/why-in-android-o-method-settings-candrawoverlays-returns-false-when-user-has
     */
    @TargetApi(Build.VERSION_CODES.O)
    private fun hasPermissionV26Workaround(context: Context): Boolean {
        return try {
            val opsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = opsManager.checkOpNoThrow("android:system_alert_window",
                    android.os.Process.myUid(),
                    context.packageName)
            mode == 0 || mode == 1

        } catch (e: Exception) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun createPermissionIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
    }
}
