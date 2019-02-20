/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.persistence

import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.room.OnConflictStrategy.REPLACE
import androidx.sqlite.db.SimpleSQLiteQuery

@Dao
interface ScreenshotDao {

    @Query("SELECT * FROM screenshot WHERE id = :screenshotId")
    fun getScreenshot(screenshotId: String): ScreenshotModel

    @Query("SELECT * FROM screenshot")
    fun getScreenshots(): LiveData<List<ScreenshotModel>>
    @Query("SELECT * FROM screenshot")
    fun getScreenshotList(): List<ScreenshotModel>

    @Query("SELECT * FROM screenshot WHERE collection_id IN(:collectionIds)")
    fun getScreenshots(collectionIds: List<String>): LiveData<List<ScreenshotModel>>
    @Query("SELECT * FROM screenshot WHERE collection_id IN(:collectionIds)")
    fun getScreenshotList(collectionIds: List<String>): List<ScreenshotModel>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addScreenshot(screenshot: List<ScreenshotModel>)

    @Update(onConflict = REPLACE)
    fun updateScreenshot(screenshots: List<ScreenshotModel>)

    @Delete
    fun deleteScreenshot(screenshot: ScreenshotModel)

//    @Query("SELECT screenshot.* FROM screenshot JOIN fts ON " +
//            "screenshot.rowid = fts.docid WHERE fts.content_text MATCH :queryText")
//    fun searchScreenshots(queryText: String): LiveData<List<ScreenshotModel>>

    /**
     *  SELECT
     *      s.*
     *  FROM
     *      screenshot s
     *  INNER JOIN
     *      (SELECT
     *          content.*
     *      FROM
     *          screenshot_content content
     *      INNER JOIN
     *          fts
     *      ON
     *          content.`rowid` = fts.`rowid`
     *      WHERE
     *          fts.content_text
     *      MATCH
     *          :queryText) result
     *  ON
     *      s.id = result.id
     */
    @Query("SELECT s.* FROM screenshot s INNER JOIN (SELECT content.* FROM screenshot_content content INNER JOIN fts ON content.`rowid` = fts.`rowid` WHERE fts.content_text MATCH :queryText) result ON s.id = result.id")
    fun searchScreenshots(queryText: String): LiveData<List<ScreenshotModel>>

    @RawQuery
    fun searchScreenshotsRaw(query: SimpleSQLiteQuery) : List<ScreenshotModel>

    @Query("SELECT screenshot.* FROM (SELECT id, max(last_modified) AS max_date FROM screenshot GROUP BY collection_id) AS latest INNER JOIN screenshot ON latest.id = screenshot.id AND screenshot.last_modified = latest.max_date")
    fun getCollectionCovers(): LiveData<List<ScreenshotModel>>

    @Query("SELECT * FROM screenshot_content")
    fun getScreenshotContent(): LiveData<List<ScreenshotContentModel>>

    @Insert(onConflict = REPLACE)
    fun updateContentText(contentModel: ScreenshotContentModel)

    @Query("SELECT * FROM screenshot_content WHERE id = :screenshotId")
    fun getContentText(screenshotId: String): ScreenshotContentModel?
}
