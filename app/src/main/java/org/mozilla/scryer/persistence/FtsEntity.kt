/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.persistence

import androidx.room.*

@Fts4(contentEntity = ScreenshotContentModel::class)
@Entity(tableName = "fts")
data class FtsEntity(
        @ColumnInfo(name = "content_text") var contentText: String
)
