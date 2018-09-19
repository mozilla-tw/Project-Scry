/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.repository

import android.arch.lifecycle.LiveData
import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotDatabase
import org.mozilla.scryer.persistence.ScreenshotModel

interface ScreenshotRepository {
    companion object Factory {
        fun createRepository(context: Context, onCreated: () -> Unit): ScreenshotRepository {
            val callback = object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    onCreated()
                }
            }
            return ScreenshotDatabaseRepository(Room.databaseBuilder(context.applicationContext,
                    ScreenshotDatabase::class.java, "screenshot-db")
                    .addCallback(callback)
                    .build())
        }
    }

    fun addCollection(collection: CollectionModel)
    fun getCollections(): LiveData<List<CollectionModel>>
    fun getCollectionList(): List<CollectionModel>
    fun getCollection(id: String): CollectionModel?
    /** collection_id to model */
    fun getCollectionCovers(): LiveData<Map<String, ScreenshotModel>>
    fun updateCollection(collection: CollectionModel)
    fun updateCollectionId(collection: CollectionModel, id: String)
    fun deleteCollection(collection: CollectionModel)

    fun addScreenshot(screenshots: List<ScreenshotModel>)
    fun updateScreenshot(screenshot: ScreenshotModel)
    fun getScreenshot(screenshotId: String): ScreenshotModel?
    fun getScreenshots(): LiveData<List<ScreenshotModel>>
    fun getScreenshotList(): List<ScreenshotModel>
    fun getScreenshots(collectionIds: List<String>): LiveData<List<ScreenshotModel>>
    fun getScreenshotList(collectionIds: List<String>): List<ScreenshotModel>
    fun deleteScreenshot(screenshot: ScreenshotModel)

    fun setupDefaultContent(context: Context) {
        TODO("not implemented")
    }
}
