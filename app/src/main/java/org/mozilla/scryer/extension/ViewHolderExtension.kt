/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.extension

import android.support.v7.widget.RecyclerView

fun RecyclerView.ViewHolder.getValidPosition(callback: (position: Int) -> Unit) {
    if (adapterPosition != RecyclerView.NO_POSITION) {
        callback(adapterPosition)
    }
}
