/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import org.mozilla.scryer.permission.PermissionViewModel
import org.mozilla.scryer.preference.PreferenceWrapper
import org.mozilla.scryer.telemetry.TelemetryWrapper

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_CODE_OVERLAY_PERMISSION = 1000
        const val REQUEST_CODE_WRITE_EXTERNAL_PERMISSION = 1001
    }

    private val prefs: PreferenceWrapper by lazy {
        PreferenceWrapper(this)
    }

    var isFirstTimeLaunched: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        isFirstTimeLaunched = prefs.isFirstTimeLaunch()
        if (isFirstTimeLaunched) {
            prefs.setFirstTimeLaunched()
        }
    }

    override fun onResume() {
        super.onResume()

        TelemetryWrapper.startSession()
    }

    override fun onPause() {
        super.onPause()

        TelemetryWrapper.stopSession()
    }

    override fun onStop() {
        super.onStop()

        TelemetryWrapper.stopMainActivity()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        ViewModelProviders.of(this).get(PermissionViewModel::class.java)
                .permissionRequest.notify(grantResults)
    }
}

fun setSupportActionBar(activity: FragmentActivity?, toolbar: Toolbar) {
    (activity as AppCompatActivity).setSupportActionBar(toolbar)
}

fun getSupportActionBar(activity: FragmentActivity?): ActionBar {
    val actionBar = (activity as AppCompatActivity).supportActionBar
    return actionBar ?: throw RuntimeException("no action bar set")
}

