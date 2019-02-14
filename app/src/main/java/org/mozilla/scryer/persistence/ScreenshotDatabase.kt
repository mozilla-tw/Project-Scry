/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.persistence

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
        entities = [
            CollectionModel::class,
            ScreenshotModel::class,
            ScreenshotContentModel::class,
            FtsEntity::class
        ],
        version = 2
)
abstract class ScreenshotDatabase: RoomDatabase() {
    abstract fun screenshotDao(): ScreenshotDao
    abstract fun collectionDao(): CollectionDao
}