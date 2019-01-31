/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(contentEntity = ScreenshotModel::class)
@Entity(tableName = "fts")
data class FtsEntity(
        @PrimaryKey(autoGenerate = true) var rowid: Int = 0,
        @ColumnInfo(name = "content_text") var contentText: String
)
