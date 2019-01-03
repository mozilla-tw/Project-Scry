/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.util

import android.os.Looper
import kotlinx.coroutines.experimental.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

object ThreadUtils {
    private val backgroundExecutorService = Executors.newSingleThreadExecutor(ioPrioritisedFactory)
    private val uiThread = Looper.getMainLooper().thread

    private val ioPrioritisedFactory: ThreadFactory
        get() = CustomThreadFactory("pool-io-background", Thread.NORM_PRIORITY - 1)

    val singleThreadDispatcher = backgroundExecutorService.asCoroutineDispatcher()

    fun assertOnUiThread() {
        val currentThread = Thread.currentThread()
        val currentThreadId = currentThread.id
        val expectedThreadId = uiThread.id

        if (currentThreadId == expectedThreadId) {
            return
        }

        throw IllegalThreadStateException("Expected UI thread, but running on " + currentThread.name)
    }

    private class CustomThreadFactory(private val threadName: String, private val threadPriority: Int) : ThreadFactory {
        private val mNumber = AtomicInteger()

        override fun newThread(r: Runnable): Thread {
            val thread = Thread(r, threadName + "-" + mNumber.getAndIncrement())
            thread.priority = threadPriority
            return thread
        }
    }
}

fun launchIO(block: suspend CoroutineScope.() -> Unit) {
    GlobalScope.launch(Dispatchers.IO) {
        block(this)
    }
}
