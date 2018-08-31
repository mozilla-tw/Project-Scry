/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.persistence

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.PrimaryKey
import android.graphics.Color
import java.util.*

@Entity(tableName = "collection")
data class CollectionModel constructor(
        @PrimaryKey(autoGenerate = false) var id: String,
        @ColumnInfo(name = "name") var name: String,
        @ColumnInfo(name = "date") val date: Long,
        @ColumnInfo(name = "color") val color: Int) {

    @Ignore
    constructor(name: String, date: Long, color: Int)
            : this(UUID.randomUUID().toString(), name, date, color)

    companion object {
        /** Screenshots that has not yet been reviewed by the users */
        const val UNCATEGORIZED = "uncategorized"

        /** Screenshots that had been reviewed by the users without categorizing it */
        const val CATEGORY_NONE = "category_none"

        val suggestCollections = listOf(
                CollectionModel("default1", "default1", 1, Color.parseColor("#235dff")),
                CollectionModel("default2", "default2", 2, Color.parseColor("#10c1b6")),
                CollectionModel("default3", "default3", 3, Color.parseColor("#ffa6a8")))

        fun isSuggestCollection(collection: CollectionModel): Boolean {
            return suggestCollections.any { it.id == collection.id }
        }
    }
}
