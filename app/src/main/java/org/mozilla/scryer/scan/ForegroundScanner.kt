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
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.launch
import org.mozilla.scryer.ScryerApplication
import org.mozilla.scryer.persistence.ScreenshotModel
import kotlin.coroutines.experimental.CoroutineContext

class ForegroundScanner : CoroutineScope {

    private val screenshotLiveData =
            ScryerApplication.getScreenshotRepository().getScreenshots()
    private val screenshotObserver = Observer<List<ScreenshotModel>> {
        scheduleForegroundScan()
    }

    private val progressLiveData = MutableLiveData<Pair<Int, Int>>()

    private val parentJob = Job()
    private var scanJob: Job? = null
    private var scanActor: SendChannel<Unit>? = null

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + parentJob

    fun onCreate() {}

    fun onStart() {
        prepareScan()
        screenshotLiveData.observeForever(screenshotObserver)
    }

    fun onStop() {
        screenshotLiveData.removeObserver(screenshotObserver)
        cancelScan()
    }

    fun onDestroy() {
        parentJob.cancel()
    }

    fun getProgress(): LiveData<Pair<Int, Int>> {
        return Transformations.map(progressLiveData) { it }
    }

    fun isScanning(): Boolean {
        return scanJob?.isActive == true
    }

    private fun prepareScan() {
        val scanJob = Job(parentJob).apply {
            scanJob = this
        }

        scanActor = actor(context = scanJob, capacity = Channel.CONFLATED) {
            for (msg in channel) {
                FirebaseVisionTextHelper.scanAndSave { a, b ->
                    launch (Dispatchers.Main) {
                        progressLiveData.value = Pair(a, b)
                    }
                }
            }
        }
    }

    private fun cancelScan() {
        scanActor?.close()
        scanJob?.cancel()
    }

    private fun scheduleForegroundScan() {
        launch {
            scanActor?.send(Unit)
        }
    }
}
