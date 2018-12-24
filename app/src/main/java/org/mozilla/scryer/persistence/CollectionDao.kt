/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.persistence

import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.room.OnConflictStrategy.REPLACE

@Dao
abstract class CollectionDao {

    @Query("SELECT * FROM collection")
    abstract fun getCollections(): LiveData<List<CollectionModel>>

    @Query("SELECT * FROM collection")
    abstract fun getCollectionList(): List<CollectionModel>

    @Insert(onConflict = REPLACE)
    abstract fun addCollection(collection: CollectionModel)

    @Update(onConflict = REPLACE)
    abstract fun updateCollection(collection: CollectionModel)

    @Delete
    abstract fun deleteCollection(collection: CollectionModel)

    @Query("SELECT * FROM collection WHERE id = :id")
    abstract fun getCollection(id: String): CollectionModel?

    @Transaction
    open fun updateCollectionId(collection: CollectionModel, id: String) {
        deleteCollection(collection)
        collection.id = id
        addCollection(collection)
    }
}
