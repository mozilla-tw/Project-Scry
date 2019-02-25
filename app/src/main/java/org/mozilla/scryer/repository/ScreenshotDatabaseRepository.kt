/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.withContext
import org.mozilla.scryer.R
import org.mozilla.scryer.persistence.*
import org.mozilla.scryer.util.launchIO

class ScreenshotDatabaseRepository(private val database: ScreenshotDatabase) : ScreenshotRepository {

    companion object {
        fun create(context: Context, onCreated: () -> Unit): ScreenshotDatabaseRepository {
            val callback = object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    onCreated()
                }
            }
            return ScreenshotDatabaseRepository(
                    Room.databaseBuilder(context.applicationContext, ScreenshotDatabase::class.java,
                            "screenshot-db")
                            .addMigrations(MIGRATION_1_2)
                            .addCallback(callback)
                            .build()
            )
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                        "CREATE TABLE IF NOT EXISTS `screenshot_content` (`id` TEXT NOT NULL, `content_text` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`id`) REFERENCES `screenshot`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                database.execSQL(
                        "CREATE VIRTUAL TABLE IF NOT EXISTS `fts` USING FTS4(" +
                        "`content_text`, " +
                        "content=`screenshot_content`)"
                )
            }
        }
    }

    private var collectionListData = database.collectionDao().getCollections()
    private val screenshotListData = database.screenshotDao().getScreenshots()

    override fun addScreenshot(screenshots: List<ScreenshotModel>) {
        database.screenshotDao().addScreenshot(screenshots)
    }

    override fun updateScreenshots(screenshots: List<ScreenshotModel>) {
        database.screenshotDao().updateScreenshot(screenshots)
    }

    override fun getScreenshot(screenshotId: String): ScreenshotModel? {
        return database.screenshotDao().getScreenshot(screenshotId)
    }

    override fun getScreenshots(collectionIds: List<String>): LiveData<List<ScreenshotModel>> {
        return database.screenshotDao().getScreenshots(collectionIds)
    }

    override fun getScreenshots(): LiveData<List<ScreenshotModel>> {
        return screenshotListData
    }

    override fun deleteScreenshot(screenshot: ScreenshotModel) {
        database.screenshotDao().deleteScreenshot(screenshot)
    }

    override fun getCollections(): LiveData<List<CollectionModel>> {
        return collectionListData
    }

    override fun getCollectionList(): List<CollectionModel> {
        return database.collectionDao().getCollectionList()
    }

    override fun addCollection(collection: CollectionModel) {
        database.collectionDao().addCollection(collection)
    }

    override fun getCollection(id: String): CollectionModel? {
        return database.collectionDao().getCollection(id)
    }

    override fun getCollectionCovers(): LiveData<Map<String, ScreenshotModel>> {
        return Transformations.switchMap(database.screenshotDao().getCollectionCovers()) { models ->
            MutableLiveData<Map<String, ScreenshotModel>>().apply {
                value = models.map { it.collectionId to it }.toMap()
            }
        }
    }

    override fun updateCollection(collection: CollectionModel) {
        database.collectionDao().updateCollection(collection)
    }

    override fun setupDefaultContent(context: Context) {
        launchIO {
            val none = CollectionModel(CollectionModel.CATEGORY_NONE,
                    context.getString(R.string.home_action_unsorted), 0, 0)
            addCollection(none)

            val nameList = listOf(R.string.sorting_suggestion_1st,
                    R.string.sorting_suggestion_2nd,
                    R.string.sorting_suggestion_3rd,
                    R.string.sorting_suggestion_4th,
                    R.string.sorting_suggestion_5th)

            if (nameList.size < SuggestCollectionHelper.suggestCollections.size) {
                throw RuntimeException("Not enough name for all suggestion collection")
            }

            SuggestCollectionHelper.suggestCollections.forEachIndexed { index, collection ->
                collection.name = context.getString(nameList[index])
                addCollection(collection)
            }
        }
    }

    override fun getScreenshotList(): List<ScreenshotModel> {
        return database.screenshotDao().getScreenshotList()
    }

    override fun getScreenshotList(collectionIds: List<String>): List<ScreenshotModel> {
        return database.screenshotDao().getScreenshotList(collectionIds)
    }

    override fun deleteCollection(collection: CollectionModel) {
        database.collectionDao().deleteCollection(collection)
    }

    override fun updateCollectionId(collection: CollectionModel, id: String) {
        database.collectionDao().updateCollectionId(collection, id)
    }

    override fun searchScreenshots(queryText: String): LiveData<List<ScreenshotModel>> {
        return MatchStrategy().search(queryText, database)
    }

    override fun getScreenshotContent(): LiveData<List<ScreenshotContentModel>> {
        return database.screenshotDao().getScreenshotContent()
    }

    override fun updateScreenshotContent(screenshotContent: ScreenshotContentModel) {
        return database.screenshotDao().updateContentText(screenshotContent)
    }

    override fun getContentText(screenshot: ScreenshotModel): String? {
        return database.screenshotDao().getContentText(screenshot.id)?.contentText
    }

    private interface SearchStrategy {
        fun search(
                queryText: String,
                database: ScreenshotDatabase
        ): LiveData<List<ScreenshotModel>>
    }

    private class MatchStrategy : SearchStrategy {
        override fun search(
                queryText: String,
                database: ScreenshotDatabase
        ): LiveData<List<ScreenshotModel>> {
            return database.screenshotDao().searchScreenshots(
                    queryText
                            .split("[ \"\\-*]".toRegex())
                            .joinToString(" ", "", "*")
            )
        }
    }

    private class TwoWayStrategy : SearchStrategy {
        override fun search(
                queryText: String,
                database: ScreenshotDatabase
        ): LiveData<List<ScreenshotModel>> {
            val useFts = queryText.all { Character.isLetterOrDigit(it) }
            if (useFts) {
                return database.screenshotDao().searchScreenshots(
                        queryText
                                .split(" ")
                                .joinToString(" ", "*", "*")
                )

            } else {
                val liveData = MutableLiveData<List<ScreenshotModel>>()
                return Transformations.switchMap(database.screenshotDao().getScreenshotContent()) {
                    launchIO {
                        val args = queryText.split(" ").map { term -> "%$term%" }
                        val whereClauseBuilder = StringBuilder()
                        for (index in 0 until args.size) {
                            whereClauseBuilder.append("${if (index > 0) {
                                " AND "
                            } else {
                                ""
                            }}content_text like ?")
                        }

                        val contentSql = "SELECT content.* " +
                                "FROM screenshot_content content " +
                                "WHERE $whereClauseBuilder"
                        val sql = "SELECT s.* " +
                                "FROM screenshot s " +
                                "INNER JOIN ($contentSql) result " +
                                "ON s.id = result.id"
                        val query = SimpleSQLiteQuery(sql, args.toTypedArray())
                        val result = database.screenshotDao().searchScreenshotsRaw(query)
                        withContext (Dispatchers.Main) {
                            liveData.value = result
                        }
                    }
                    liveData
                }
            }
        }
    }
}
