/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.experimental.*
import org.mozilla.scryer.ScryerApplication
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.repository.ScreenshotRepository

class ScreenshotViewModel(private val delegate: ScreenshotRepository) : ViewModel(),
        ScreenshotRepository by delegate {
    companion object {
        fun get(fragment: androidx.fragment.app.Fragment): ScreenshotViewModel {
            return ViewModelProviders.of(fragment, getFactory()).get(ScreenshotViewModel::class.java)
        }

        fun get(activity: androidx.fragment.app.FragmentActivity): ScreenshotViewModel {
            return ViewModelProviders.of(activity, getFactory()).get(ScreenshotViewModel::class.java)
        }

        private fun getFactory(): ScreenshotViewModelFactory {
            return ScreenshotViewModelFactory(ScryerApplication.getScreenshotRepository())
        }
    }

    suspend fun batchMove(screenshots: List<ScreenshotModel>, collectionId: String) = withContext(CommonPool) {
        screenshots.forEach {
            it.collectionId = collectionId
            updateScreenshot(it)
        }
    }
}

class ScreenshotViewModelFactory(private val repository: ScreenshotRepository)
    : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ScreenshotViewModel(repository) as T
    }
}