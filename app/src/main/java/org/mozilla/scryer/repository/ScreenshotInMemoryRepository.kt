/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel

@Suppress("unused")
class ScreenshotInMemoryRepository : ScreenshotRepository {
    private val collectionData = MutableLiveData<List<CollectionModel>>()
    private val collectionList = mutableListOf<CollectionModel>()
    private val screenshotData = MutableLiveData<List<ScreenshotModel>>()
    private val screenshotList = mutableListOf<ScreenshotModel>()

    init {
        collectionData.value = collectionList
        screenshotData.value = screenshotList
    }

    override fun addCollection(collection: CollectionModel) {
        collectionList.add(collection)
        collectionData.value = collectionList
    }

    override fun getCollections(): LiveData<List<CollectionModel>> {
        return collectionData
    }

    override fun getCollectionList(): List<CollectionModel> {
        return collectionList
    }

    override fun getCollectionCovers(): LiveData<Map<String, ScreenshotModel>> {
        return MutableLiveData<Map<String, ScreenshotModel>>()
    }

    override fun addScreenshot(screenshots: List<ScreenshotModel>) {
        screenshotList.addAll(screenshots)
        screenshotData.value = screenshotList
    }

    override fun updateScreenshot(screenshot: ScreenshotModel) {
        screenshotData.value = screenshotList
    }

    override fun getScreenshot(screenshotId: String): ScreenshotModel? {
        return screenshotList.find { it.id == screenshotId }
    }

    override fun getScreenshots(): LiveData<List<ScreenshotModel>> {
        return screenshotData
    }

    override fun getScreenshots(collectionIds: List<String>): LiveData<List<ScreenshotModel>> {
        return screenshotData
    }

    override fun deleteScreenshot(screenshot: ScreenshotModel) {
        screenshotList.remove(screenshot)
        screenshotData.value = screenshotList
    }

    override fun getScreenshotList(): List<ScreenshotModel> {
        return screenshotList
    }

    override fun getScreenshotList(collectionIds: List<String>): List<ScreenshotModel> {
        return screenshotList
    }

    override fun updateCollection(collection: CollectionModel) {
    }

    override fun deleteCollection(collection: CollectionModel) {
    }

    override fun updateCollectionId(collection: CollectionModel, id: String) {
    }

    override fun getCollection(id: String): CollectionModel? {
        return null
    }
}
