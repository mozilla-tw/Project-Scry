package org.mozilla.scryer.persistence

import android.graphics.Color

class SuggestCollectionHelper {
    companion object {
        val SUGGEST_COLOR = Color.parseColor("#00C8D7")
        val suggestCollections = listOf(
                CollectionModel("default1", "", Long.MAX_VALUE - 5, SUGGEST_COLOR),
                CollectionModel("default2", "", Long.MAX_VALUE - 4, SUGGEST_COLOR),
                CollectionModel("default3", "", Long.MAX_VALUE - 3, SUGGEST_COLOR),
                CollectionModel("default4", "", Long.MAX_VALUE - 2, SUGGEST_COLOR),
                CollectionModel("default5", "", Long.MAX_VALUE - 1, SUGGEST_COLOR))

        fun isSuggestCollection(collection: CollectionModel): Boolean {
            return suggestCollections.any { it.id == collection.id }
        }
    }
}
