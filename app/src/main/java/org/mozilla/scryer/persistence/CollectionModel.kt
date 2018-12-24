/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "collection")
data class CollectionModel constructor(
        @PrimaryKey(autoGenerate = false) var id: String,
        @ColumnInfo(name = "name") var name: String,
        @ColumnInfo(name = "date") var createdDate: Long,
        @ColumnInfo(name = "color") var color: Int) {

    @Ignore
    constructor(name: String, date: Long, color: Int)
            : this(UUID.randomUUID().toString(), name, date, color)

    companion object {
        /** Screenshots that has not yet been reviewed by the users */
        const val UNCATEGORIZED = "uncategorized"

        /** Screenshots that had been reviewed by the users without categorizing it */
        const val CATEGORY_NONE = "category_none"
    }
}

