/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.scan

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations

class ContentScanner {
    companion object {
        internal const val TAG = "ContentScanner"
    }

    private lateinit var plan: Plan

    fun onCreate(plan: Plan) {
        this.plan = plan
        plan.onCreate()
    }

    fun onDestroy() {
        plan.onDestroy()
    }

    fun getProgress(): LiveData<Pair<Int, Int>> {
        return Transformations.map(plan.getProgressState()) {
            if (it is ProgressState.Progress) {
                Pair(it.current, it.total)
            } else {
                Pair(0, 0)
            }
        }
    }

    fun getProgressState(): LiveData<ProgressState> {
        return plan.getProgressState()
    }

    fun isScanning(): Boolean {
        return plan.isScanning()
    }

    interface Plan {
        fun onCreate()
        fun onDestroy()
        fun getProgressState(): LiveData<ProgressState>
        fun isScanning(): Boolean
    }

    sealed class ProgressState {
        object Unavailable: ProgressState()
        class Progress(var current: Int, var total: Int): ProgressState()
    }
}
