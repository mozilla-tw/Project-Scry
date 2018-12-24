/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer

import androidx.lifecycle.ViewModelProviders
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.view.View
import android.view.WindowManager
import org.mozilla.scryer.notification.ScryerMessagingService
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

        window?.let {
            it.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            it.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            it.decorView.systemUiVisibility = it.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            it.statusBarColor = Color.TRANSPARENT
        }

        intent.getStringExtra(ScryerMessagingService.PUSH_OPEN_URL)?.run {
            val openUrlIntent = Intent()
            openUrlIntent.data = Uri.parse(this)
            openUrlIntent.action = Intent.ACTION_VIEW
            startActivity(openUrlIntent)
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

fun setSupportActionBar(activity: androidx.fragment.app.FragmentActivity?, toolbar: Toolbar) {
    (activity as AppCompatActivity).setSupportActionBar(toolbar)
}

fun getSupportActionBar(activity: androidx.fragment.app.FragmentActivity?): ActionBar {
    val actionBar = (activity as AppCompatActivity).supportActionBar
    return actionBar ?: throw RuntimeException("no action bar set")
}

