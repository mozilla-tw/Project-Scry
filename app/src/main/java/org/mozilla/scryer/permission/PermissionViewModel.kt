/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.permission

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.content.pm.PackageManager
import org.mozilla.scryer.Event

class PermissionViewModel : ViewModel() {
    val permissionRequest = PermissionLiveData()
}

class PermissionLiveData: MutableLiveData<Event<BooleanArray>>() {
    fun notify(results: IntArray) {
        if (!results.isEmpty()) {
            value = Event(results.map { it == PackageManager.PERMISSION_GRANTED }.toBooleanArray())
        }
    }
}
