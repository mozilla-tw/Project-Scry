/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.scan

import android.content.Context
import androidx.concurrent.futures.ResolvableFuture
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.experimental.*
import kotlin.coroutines.experimental.CoroutineContext

class BackgroundScanner(
        context: Context,
        params: WorkerParameters
) : ListenableWorker(context, params), CoroutineScope {

    private val workerJob = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + workerJob + CoroutineExceptionHandler { _, _ -> }

    override fun startWork(): ListenableFuture<Result> {
        val future = ResolvableFuture.create<Result>()

        launch {
            FirebaseVisionTextHelper.scanAndSave()
        }.invokeOnCompletion {
            future.set(Result.success())
            workerJob.cancel()
        }

        return future
    }

    override fun onStopped() {
        workerJob.cancel()
    }
}
