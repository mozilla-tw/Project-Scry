/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.sortingpanel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.mozilla.scryer.Observer
import org.mozilla.scryer.R
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.ui.CollectionNameDialog
import org.mozilla.scryer.ui.ScryerToast
import org.mozilla.scryer.viewmodel.ScreenshotViewModel
import java.io.File
import java.util.*

class SortingPanelActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_SCREENSHOT_ID = "screenshot_id"
        const val EXTRA_COLLECTION_ID = "collection_id"
        const val EXTRA_SHOW_ADD_TO_COLLECTION = "collection_id"

        fun sortCollection(context: Context, collectionId: String): Intent {
            val intent = Intent(context, SortingPanelActivity::class.java)
            intent.putExtra(EXTRA_COLLECTION_ID, collectionId)
            return intent
        }

        fun sortNewScreenshot(context: Context, path: String, showAddToCollection: Boolean): Intent {
            val intent = Intent(context, SortingPanelActivity::class.java)
            intent.putExtra(EXTRA_PATH, path)
            intent.putExtra(EXTRA_SHOW_ADD_TO_COLLECTION, showAddToCollection)
            return intent
        }

        fun sortOldScreenshot(context: Context, screenshotId: String): Intent {
            val intent = Intent(context, SortingPanelActivity::class.java)
            intent.putExtra(EXTRA_SCREENSHOT_ID, screenshotId)
            return intent
        }
    }

    private val sortingPanel: SortingPanel by lazy { findViewById<SortingPanel>(R.id.sorting_panel) }

    private val screenshotViewModel: ScreenshotViewModel by lazy {
        ScreenshotViewModel.get(this)
    }

    private val unsortedScreenshots = LinkedList<ScreenshotModel>()
    private val sortedScreenshots = LinkedList<ScreenshotModel>()
    private var currentScreenshot: ScreenshotModel? = null
    private val screenshotCount: Int
        get() = unsortedScreenshots.size + sortedScreenshots.size

    private var unsortedCollection: CollectionModel? = null

    private val collectionColors = mutableListOf<Int>()

    private val isMultiSortMode: Boolean
        get() = intent.hasExtra(EXTRA_COLLECTION_ID)

    private val toast: ScryerToast by lazy {
        ScryerToast(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sorting_panel)

        loadColors()
        loadScreenshots(intent, this::onLoadScreenshotsSuccess)
        initSortingPanel()
    }

    override fun onStart() {
        super.onStart()
        this.lifecycle.addObserver(this.sortingPanel)
    }

    override fun onStop() {
        super.onStop()
        this.lifecycle.removeObserver(this.sortingPanel)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        loadScreenshots(intent, this::onLoadScreenshotsSuccess)
    }

    override fun onBackPressed() {
        if (isMultiSortMode) {
            AlertDialog.Builder(this)
                    .setTitle(R.string.dialogue_skipsorting_title_skip)
                    .setMessage(R.string.dialogue_skipsorting_content_moveto)
                    .setPositiveButton(R.string.dialogue_skipsorting_action_skip) { _, _ ->
                        flushToUnsortedCollection()
                        finishAndRemoveTask()
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                    }
                    .show()
        } else {
            super.onBackPressed()
        }
    }

    private fun flushToUnsortedCollection() {
        showAddedToast(unsortedCollection)
        for (model in unsortedScreenshots) {
            model.collectionId = CollectionModel.CATEGORY_NONE
            // TODO: Batch
            launch {
                screenshotViewModel.updateScreenshot(model)
            }
        }
    }

    private fun loadColors() {
        val typedArray = resources.obtainTypedArray(R.array.collection_colors)
        val length = typedArray.length()
        for (i in 0 until length) {
            val color = typedArray.getColor(i, ContextCompat.getColor(this, R.color.primaryTeal))
            collectionColors.add(color)
        }
        typedArray.recycle()
    }

    private fun initSortingPanel() {
        val collectionData = screenshotViewModel.getCollections()
        collectionData.observe(this, Observer {
            unsortedCollection = it.find { it.id == CollectionModel.CATEGORY_NONE }
        })

        sortingPanel.collectionSource = collectionData
        sortingPanel.showCollectionPanel = shouldShowCollectionPanel
        sortingPanel.callback = object : SortingPanel.Callback {
            override fun onClick(collection: CollectionModel) {
                onCollectionClicked(collection)
            }

            override fun onNewCollectionClick() {
                onNewCollectionClicked()
            }
        }
    }

    private fun onNewModelAvailable() {
        if (unsortedScreenshots.isEmpty()) {
            finishAndRemoveTask()
            return
        }

        currentScreenshot = this.unsortedScreenshots.removeFirst()?.apply {
            sortedScreenshots.addLast(this)
            onScreenshotViewed(this)

            sortingPanel.screenshot = this
            sortingPanel.setProgress(sortedScreenshots.size, sortedScreenshots.size + unsortedScreenshots.size)
        }
    }

    private fun onScreenshotViewed(screenshot: ScreenshotModel) {
        if (!shouldShowCollectionPanel) {
            return
        }

        if (screenshot.collectionId == CollectionModel.UNCATEGORIZED) {
            screenshot.collectionId = CollectionModel.CATEGORY_NONE
            launch {
                screenshotViewModel.updateScreenshot(screenshot)
            }
        }
    }

    private fun loadScreenshots(intent: Intent?, onFinished: (List<ScreenshotModel>) -> Unit) {
        intent?: run {
            onLoadScreenshotsFailed()
            return
        }

        val viewModel = this.screenshotViewModel

        when {
            // Sort a new screenshot
            intent.hasExtra(EXTRA_PATH) -> {
                createNewScreenshot(intent)?.apply {
                    val result = listOf(this)
                    launch(UI) {
                        withContext(DefaultDispatcher) {
                            viewModel.addScreenshot(result)
                        }
                        onFinished.invoke(result)
                    }
                }?: onLoadScreenshotsFailed()
            }

            // Sort an old screenshot
            intent.hasExtra(EXTRA_SCREENSHOT_ID) -> {
                val id = intent.getStringExtra(EXTRA_SCREENSHOT_ID)
                launch(UI) {
                    withContext(DefaultDispatcher) {
                        viewModel.getScreenshot(id)

                    }?.let {
                        onFinished.invoke(listOf(it))
                    }
                }
            }

            // Sort all screenshots in a collection
            intent.hasExtra(EXTRA_COLLECTION_ID) -> {
                val id = intent.getStringExtra(EXTRA_COLLECTION_ID)
                val idList = if (id == CollectionModel.CATEGORY_NONE) {
                    listOf(CollectionModel.UNCATEGORIZED, CollectionModel.CATEGORY_NONE)
                } else {
                    listOf(id)
                }
                launch(UI) {
                    val screenshots = withContext(DefaultDispatcher) {
                        viewModel.getScreenshotList(idList)
                    }
                    onFinished.invoke(screenshots)
                }
            }

            else -> onLoadScreenshotsFailed()
        }
    }

    private val shouldShowCollectionPanel: Boolean
        get() = if (intent.hasExtra(EXTRA_SHOW_ADD_TO_COLLECTION)) {
            intent.getBooleanExtra(EXTRA_SHOW_ADD_TO_COLLECTION, true)

        } else {
            true
        }

    private fun onLoadScreenshotsSuccess(screenshots: List<ScreenshotModel>) {
        this.sortedScreenshots.clear()

        this.unsortedScreenshots.clear()
        this.unsortedScreenshots.addAll(screenshots.sortedByDescending { it.lastModified })

        if (screenshots.size == 1) {
            sortingPanel.setActionText(getString(android.R.string.cancel))
            sortingPanel.setProgressVisibility(View.INVISIBLE)
            sortingPanel.setFakeLayerVisibility(View.INVISIBLE)
        } else {
            sortingPanel.setActionText(getString(R.string.multisorting_action_next))
            sortingPanel.setProgressVisibility(View.VISIBLE)
            sortingPanel.setFakeLayerVisibility(View.VISIBLE)
        }

        sortingPanel.setActionCallback {
            showAddedToast(unsortedCollection)
            onNewModelAvailable()
        }

        if (!shouldShowCollectionPanel) {
            Handler().postDelayed({ finishAndRemoveTask() }, 1000)
        }

        onNewModelAvailable()
    }

    private fun showAddedToast(model: CollectionModel?) {
        model?.let {
            val yOffset = if (sortingPanel.isCollapse()) {
                sortingPanel.getCollapseHeight()
            } else {
                0
            }
            toast.show(getString(R.string.snackbar_addto, it.name), Toast.LENGTH_SHORT, yOffset)
        }
    }

    private fun onLoadScreenshotsFailed() {
        finishAndRemoveTask()
    }

    private fun onCollectionClicked(collection: CollectionModel) {
        launch(UI) {
            currentScreenshot?.let { screenshot ->
                if (CollectionModel.isSuggestCollection(collection)) {
                    // Once the user selects a suggest collection, we update its id to
                    // a random UUID, so that it will be treated as a normal collection from then on
                    withContext(DefaultDispatcher) {
                        screenshotViewModel.updateCollectionId(collection, UUID.randomUUID().toString())
                    }
                }

                screenshot.collectionId = collection.id
                withContext(DefaultDispatcher) {
                    screenshotViewModel.addScreenshot(listOf(screenshot))
                }

                if (screenshotCount == 1) {
                    showAddedToast(collection)
                }
            }
            onNewModelAvailable()
        }
    }

    private fun onNewCollectionClicked() {
        // Since suggest collection is visible on sorting panel, it's reasonable to show error msg
        // when user input a name identical to suggest collection, there's no need to exclude
        // suggest collection when matching for conflict name, set excludeSuggestion to false
        CollectionNameDialog.createNewCollection(this, screenshotViewModel, false) {
            onCollectionClicked(it)
        }
    }

    private fun createNewScreenshot(intent: Intent): ScreenshotModel? {
        val path = getFilePath(intent)
        if (path.isNotEmpty()) {
            return ScreenshotModel(path, System.currentTimeMillis(), CollectionModel.UNCATEGORIZED)
        }
        return null
    }

    private fun getFilePath(intent: Intent): String {
        val path = intent.getStringExtra(EXTRA_PATH)
        val file = File(path)
        return if (file.exists()) file.absolutePath else ""
    }
}
