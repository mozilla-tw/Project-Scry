package org.mozilla.scryer.persistence

import android.content.Context
import android.graphics.Color
import org.mozilla.scryer.R

class SuggestCollectionHelper {
    companion object {
        val SUGGEST_COLOR = Color.parseColor("#00C8D7")
        val suggestCollections = listOf(
                CollectionModel("default1", "", Long.MAX_VALUE - 5, SUGGEST_COLOR),
                CollectionModel("default2", "", Long.MAX_VALUE - 4, SUGGEST_COLOR),
                CollectionModel("default3", "", Long.MAX_VALUE - 3, SUGGEST_COLOR),
                CollectionModel("default4", "", Long.MAX_VALUE - 2, SUGGEST_COLOR),
                CollectionModel("default5", "", Long.MAX_VALUE - 1, SUGGEST_COLOR))

        private val suggestCollectionsIds = listOf(
                R.string.sorting_suggestion_1st,
                R.string.sorting_suggestion_2nd,
                R.string.sorting_suggestion_3rd,
                R.string.sorting_suggestion_4th,
                R.string.sorting_suggestion_5th)

        fun isSuggestCollection(collection: CollectionModel): Boolean {
            return suggestCollections.any { it.id == collection.id }
        }

        fun getSuggestCollectionNameForTelemetry(context: Context?, name: String?): String {
            return if (SuggestCollectionHelper.isSuggestCollectionName(context, name)) {
                name ?: ""
            } else {
                "user-defined"
            }
        }

        private fun isSuggestCollectionName(context: Context?, name: String?): Boolean {
            return name != null && suggestCollectionsIds.any { context?.getString(it) == name }
        }
    }
}
