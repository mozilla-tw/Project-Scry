/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.permission

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.mozilla.scryer.overlay.OverlayPermission

class PermissionHelper {
    companion object {
        fun hasStoragePermission(context: Context) = ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        @TargetApi(Build.VERSION_CODES.M)
        fun shouldShowStorageRational(activity: androidx.fragment.app.FragmentActivity): Boolean {
            return activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        fun hasOverlayPermission(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    || OverlayPermission.hasPermission(context)
        }

        fun requestOverlayPermission(activity: Activity?, requestCode: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return
            }
            activity?.let {
                val intent = OverlayPermission.createPermissionIntent(it)
                it.startActivityForResult(intent, requestCode)
            }
        }
    }
}
