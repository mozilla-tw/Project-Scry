package org.mozilla.scryer.util

import android.content.Context
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.scryer.R
import org.mozilla.scryer.ScryerApplication
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.SuggestCollectionHelper

class CollectionListHelper {

    companion object {

        suspend fun nextCollectionColor(context: Context, excludeSuggestion: Boolean = false): Int {
            val collections = withContext(Dispatchers.Default) {
                ScryerApplication.getScreenshotRepository().getCollectionList()
            }

            return nextCollectionColor(context, collections, excludeSuggestion)
        }

        fun nextCollectionColor(
                context: Context,
                collections: List<CollectionModel>,
                excludeSuggestion: Boolean
        ): Int {
            val list = if (excludeSuggestion) {
                collections.filterNot { SuggestCollectionHelper.isSuggestCollection(it) }
            } else {
                collections
            }

            val defaultColor = ContextCompat.getColor(context, R.color.primaryTeal)
            return nextCollectionColor(list, getColorPool(context), defaultColor)
        }

        fun isNameConflict(
                name: String,
                collections: List<CollectionModel>,
                excludeSuggestion: Boolean
        ): Boolean {
            val list = if (excludeSuggestion) {
                collections.filterNot { SuggestCollectionHelper.isSuggestCollection(it) }
            } else {
                collections
            }

            return list.any {
                name.compareTo(it.name, true) == 0
            }
        }
    }
}

fun nextCollectionColor(collections: List<CollectionModel>, colorPool: List<Int>, defaultColor: Int): Int {
    if (collections.isNotEmpty()) {
        val lastColor = collections.last().color

        colorPool.forEachIndexed { index, color ->
            if (color == lastColor) {
                return colorPool[(index + 1) % colorPool.size]
            }
        }
    }

    return if (colorPool.isEmpty()) {
        defaultColor
    } else {
        colorPool.first()
    }
}

fun getColorPool(context: Context): List<Int> {
    val defaultColor = ContextCompat.getColor(context, R.color.primaryTeal)
    val typedArray = context.resources.obtainTypedArray(R.array.collection_colors)

    val colorList = mutableListOf<Int>()
    for (i in 0 until typedArray.length()) {
        colorList.add(typedArray.getColor(i, defaultColor))
    }

    typedArray.recycle()
    return colorList
}
