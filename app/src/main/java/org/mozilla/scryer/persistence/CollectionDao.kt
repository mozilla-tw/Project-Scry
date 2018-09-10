/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.persistence

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import android.arch.persistence.room.OnConflictStrategy.REPLACE

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

    @Transaction
    open fun updateCollectionId(collection: CollectionModel, id: String) {
        deleteCollection(collection)
        collection.id = id
        // This method is used mainly to alter a suggest collection(special id) to a normal
        // collection(random UUID), this happens when user decides to put an image into a suggest collection,
        // and this is the time the suggest collection becomes a real collection, thus update the
        // timestamp
        collection.date = System.currentTimeMillis()
        addCollection(collection)
    }
}
