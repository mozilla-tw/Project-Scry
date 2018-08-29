/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import org.mozilla.scryer.Observer
import org.mozilla.scryer.R
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.sortingpanel.SortingPanel
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
            intent.putExtra(SortingPanelActivity.EXTRA_COLLECTION_ID, collectionId)
            return intent
        }

        fun sortNewScreenshot(context: Context, path: String, showAddToCollection: Boolean): Intent {
            val intent = Intent(context, SortingPanelActivity::class.java)
            intent.putExtra(SortingPanelActivity.EXTRA_PATH, path)
            intent.putExtra(SortingPanelActivity.EXTRA_SHOW_ADD_TO_COLLECTION, showAddToCollection)
            return intent
        }

        fun sortOldScreenshot(context: Context, screenshotId: String): Intent {
            val intent = Intent(context, SortingPanelActivity::class.java)
            intent.putExtra(SortingPanelActivity.EXTRA_SCREENSHOT_ID, screenshotId)
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

    private val collections = mutableListOf<CollectionModel>()
    private var unsortedCollection: CollectionModel? = null

    private val collectionColors = mutableListOf<Int>()

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
        if (unsortedScreenshots.isNotEmpty()) {
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
            screenshotViewModel.updateScreenshot(model)
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
            collections.clear()
            collections.addAll(it)
            unsortedCollection = collections.find { it.id == CollectionModel.CATEGORY_NONE }
        })

        sortingPanel.collectionSource = collectionData
        sortingPanel.showAddToCollection = intent.getBooleanExtra(EXTRA_SHOW_ADD_TO_COLLECTION, true)
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
        if (screenshot.collectionId == CollectionModel.UNCATEGORIZED) {
            screenshot.collectionId = CollectionModel.CATEGORY_NONE
            screenshotViewModel.updateScreenshot(screenshot)
        }
    }

    private fun loadScreenshots(intent: Intent?, onFinished: (List<ScreenshotModel>) -> Unit) {
        intent?: run {
            onLoadScreenshotsFailed()
            return
        }

        val viewModel = this.screenshotViewModel

        when {
            intent.hasExtra(EXTRA_PATH) -> {
                createNewScreenshot(intent)?.apply {
                    val result = listOf(this)
                    viewModel.addScreenshot(result)
                    onFinished.invoke(result)
                }?: onLoadScreenshotsFailed()
            }

            intent.hasExtra(EXTRA_SCREENSHOT_ID) -> {
                val id = intent.getStringExtra(EXTRA_SCREENSHOT_ID)
                viewModel.getScreenshot(id) {
                    onFinished.invoke(listOf(it))
                }
            }

            intent.hasExtra(EXTRA_COLLECTION_ID) -> {
                val id = intent.getStringExtra(EXTRA_COLLECTION_ID)
                val idList = if (id == CollectionModel.CATEGORY_NONE) {
                    listOf(CollectionModel.UNCATEGORIZED, CollectionModel.CATEGORY_NONE)
                } else {
                    listOf(id)
                }
                viewModel.getScreenshotList(idList) {
                    onFinished.invoke(it)
                }
            }

            else -> onLoadScreenshotsFailed()
        }
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

        if (intent.hasExtra(EXTRA_SHOW_ADD_TO_COLLECTION)
                && !intent.getBooleanExtra(EXTRA_SHOW_ADD_TO_COLLECTION, true)) {
            Handler().postDelayed({ finishAndRemoveTask() }, 1000)
        }

        onNewModelAvailable()
    }

    private fun showAddedToast(model: CollectionModel?) {
        model?.let {
            toast.show(getString(R.string.snackbar_addto, it.name), Toast.LENGTH_SHORT)
        }
    }

    private fun onLoadScreenshotsFailed() {
        finishAndRemoveTask()
    }

    private fun onCollectionClicked(collection: CollectionModel) {
        currentScreenshot?.let {
            it.collectionId = collection.id
            screenshotViewModel.addScreenshot(listOf(it))

            if (screenshotCount == 1) {
                showAddedToast(collection)
            }
        }
        onNewModelAvailable()
    }

    private fun onNewCollectionClicked() {
        CollectionNameDialog.createNewCollection(this, screenshotViewModel)
    }

    private fun createNewScreenshot(intent: Intent): ScreenshotModel? {
        val path = getFilePath(intent)
        if (path.isNotEmpty()) {
            return ScreenshotModel(path, System.currentTimeMillis(), CollectionModel.CATEGORY_NONE)
        }
        return null
    }

    private fun getFilePath(intent: Intent): String {
        val path = intent.getStringExtra(EXTRA_PATH)
        val file = File(path)
        return if (file.exists()) file.absolutePath else ""
    }
}
