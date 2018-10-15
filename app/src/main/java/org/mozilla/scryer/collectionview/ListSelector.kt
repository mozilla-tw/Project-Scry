/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.collectionview

abstract class ListSelector<T> {
    var isSelectMode = false
    val selected = mutableListOf<T>()
    private val pendingTransition = mutableListOf<T>()

    fun enterSelectionMode() {
        if (isSelectMode) {
            return
        }
        isSelectMode = true
        onEnterSelectMode()
    }

    fun exitSelectionMode() {
        isSelectMode = false
        pendingTransition.addAll(selected)
        selected.clear()
        onExitSelectMode()
    }

    fun toggleSelection(listItem: T) {
        if (selected.contains(listItem)) {
            selected.remove(listItem)
            pendingTransition.add(listItem)
        } else {
            selected.add(listItem)
            pendingTransition.add(listItem)
        }
        onSelectChanged()
    }

    fun isSelected(listItem: T): Boolean {
        return selected.contains(listItem)
    }

    fun processSelection(listItem: T, callback: (stateChanged: Boolean) -> Unit) {
        if (pendingTransition.contains(listItem)) {
            pendingTransition.remove(listItem)
            callback.invoke(true)
        } else {
            callback.invoke(false)
        }
    }

    abstract fun onEnterSelectMode()
    abstract fun onExitSelectMode()
    abstract fun onSelectChanged()
}