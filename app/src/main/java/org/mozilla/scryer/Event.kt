/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer

import androidx.lifecycle.Observer

open class Event<out T>(private val content: T) {
    private var handled = false

    fun getUnhandledContent(): T? {
        return if (handled) {
            null
        } else {
            handled = true
            content
        }
    }
}

class EventObserver<T>(private val onEvent: (T) -> Unit) : Observer<Event<T>> {
    override fun onChanged(t: Event<T>?) {
        t?.getUnhandledContent()?.let(onEvent)
    }
}

class Observer<T>(private val onEvent: (T) -> Unit) : Observer<T> {
    override fun onChanged(t: T?) {
        t?.let {
            onEvent(it)
        }
    }
}

abstract class NonNullObserver<T> : Observer<T> {
    override fun onChanged(t: T?) {
        t?.let {
            onValueChanged(it)
        }
    }

    abstract fun onValueChanged(newValue: T)
}