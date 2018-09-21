package org.mozilla.scryer.util

import org.junit.Test

import org.junit.Assert.*
import org.mozilla.scryer.persistence.CollectionModel

class CollectionListHelperTest {

    @Test
    fun nextCollectionColor() {
        var collections = (0..3).map { model(it) }
        var colors = listOf(0, 1, 2, 3)
        assertEquals(0, nextCollectionColor(collections, colors, 100))


        collections = (0..1).map { model(it) }
        colors = listOf(0, 1, 2, 3)
        assertEquals(2, nextCollectionColor(collections, colors, 100))


        // Return the first color in the pool if the last collection color is an unknown color
        collections = listOf(
                model(0),
                model(1),
                model(2),
                model(5)
        )
        colors = listOf(0, 1, 2, 3)
        assertEquals(0, nextCollectionColor(collections, colors, 100))


        collections = listOf(
                model(0),
                model(1),
                model(2),
                model(5)
        )
        colors = listOf()
        assertEquals(100, nextCollectionColor(collections, colors, 100))


        collections = listOf()
        colors = listOf(1, 2, 3)
        assertEquals(1, nextCollectionColor(collections, colors, 100))


        collections = listOf()
        colors = listOf()
        assertEquals(100, nextCollectionColor(collections, colors, 100))
    }

    private fun model(color: Int): CollectionModel {
        return CollectionModel("", "", 0, color)
    }
}