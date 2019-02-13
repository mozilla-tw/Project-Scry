/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.scan

import androidx.lifecycle.LiveData

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
        return plan.getProgress()
    }

    fun isScanning(): Boolean {
        return plan.isScanning()
    }

    interface Plan {
        fun onCreate()
        fun onDestroy()
        fun getProgress(): LiveData<Pair<Int, Int>>
        fun isScanning(): Boolean
    }
}
