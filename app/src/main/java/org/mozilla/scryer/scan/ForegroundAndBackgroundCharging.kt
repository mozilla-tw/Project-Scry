/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.scan

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.*
import mozilla.components.support.base.log.Log
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Scan when the app is in foreground, or in background while charging
 */
class ForegroundAndBackgroundCharging : ContentScanner.Plan {

    companion object {
        private const val TAG = ContentScanner.TAG + "Plan"
        private const val TAG_BACKGROUND = "background_scan"
    }

    private val foregroundScanner = ForegroundScanner()
    private var backgroundRequestId: UUID? = null

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            onEnterForeground()
        }

        override fun onStop(owner: LifecycleOwner) {
            onEnterBackground()
        }
    }

    override fun onCreate() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        foregroundScanner.onCreate()
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        foregroundScanner.onDestroy()
    }

    override fun getProgressState(): LiveData<ContentScanner.ProgressState> {
        return foregroundScanner.getProgressState()
    }

    override fun isScanning(): Boolean {
        backgroundRequestId?.let {
            return WorkManager.getInstance().getWorkInfoById(it).get().state == WorkInfo.State.RUNNING
        }
        return foregroundScanner.isScanning()
    }

    private fun onEnterForeground() {
        Log.log(tag = TAG, message = "enter foreground")
        cancelBackgroundScan()
        startForegroundScan()
    }

    private fun onEnterBackground() {
        Log.log(tag = TAG, message = "enter background")
        cancelForegroundScan()
        startBackgroundScan()
    }

    private fun startForegroundScan() {
        foregroundScanner.onStart()
    }

    private fun cancelForegroundScan() {
        foregroundScanner.onStop()
    }

    private fun startBackgroundScan() {
        val request = PeriodicWorkRequest.Builder(
                BackgroundScanner::class.java,
                15,
                TimeUnit.MINUTES
        ).setConstraints(
                Constraints.Builder().setRequiresCharging(true).build()
        ).build()
        backgroundRequestId = request.id

        WorkManager.getInstance().enqueueUniquePeriodicWork(
                TAG_BACKGROUND,
                ExistingPeriodicWorkPolicy.REPLACE,
                request
        )
    }

    private fun cancelBackgroundScan() {
        WorkManager.getInstance().cancelUniqueWork(TAG_BACKGROUND)
    }
}
