/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.scan

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import mozilla.components.support.base.log.Log
import org.mozilla.scryer.ScryerApplication
import org.mozilla.scryer.persistence.ScreenshotModel
import kotlin.coroutines.experimental.CoroutineContext

class ForegroundScanner : CoroutineScope {

    companion object {
        private const val TAG = ContentScanner.TAG + "Fg"
    }
    private var foregroundScanJob: Job? = null
    private var dirty = false
    private var isStart = false

    private val screenshotLiveData = ScryerApplication.getScreenshotRepository().getScreenshots()
    private val screenshotObserver = Observer<List<ScreenshotModel>> {
        scheduleForegroundScan()
    }

    private val progressLiveData = MutableLiveData<Pair<Int, Int>>()

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    fun onCreate() {}

    fun onStart() {
        isStart = true
        dirty = false
        screenshotLiveData.observeForever(screenshotObserver)
    }

    fun onStop() {
        isStart = false
        screenshotLiveData.removeObserver(screenshotObserver)
        foregroundScanJob?.cancel()
    }

    fun onDestroy() {
        job.cancel()
    }

    fun getProgress(): LiveData<Pair<Int, Int>> {
        return Transformations.map(progressLiveData) { it }
    }

    fun isScanning(): Boolean {
        return foregroundScanJob?.isActive == true
    }

    private fun scheduleForegroundScan() {
        foregroundScanJob?.let {
            dirty = true
            return
        }
        dirty = false
        foregroundScanJob = launch {
            FirebaseVisionTextHelper.scanAndSave { current, total ->
                launch(Dispatchers.Main) {
                    progressLiveData.value = Pair(current, total)
                }
            }
        }

        foregroundScanJob?.invokeOnCompletion {
            foregroundScanJob = null

            if (dirty && isStart) {
                Log.log(tag = TAG, message = "dirty, re-schedule scan")
                scheduleForegroundScan()
            }
        }
    }
}
