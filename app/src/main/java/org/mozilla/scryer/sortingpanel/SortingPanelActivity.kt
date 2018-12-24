/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.sortingpanel

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
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
import org.mozilla.scryer.persistence.SuggestCollectionHelper
import org.mozilla.scryer.promote.Promoter
import org.mozilla.scryer.telemetry.TelemetryWrapper
import org.mozilla.scryer.telemetry.TelemetryWrapper.ExtraValue.MULTIPLE
import org.mozilla.scryer.telemetry.TelemetryWrapper.ExtraValue.SINGLE
import org.mozilla.scryer.ui.CollectionNameDialog
import org.mozilla.scryer.ui.ConfirmationDialog
import org.mozilla.scryer.ui.ScryerToast
import org.mozilla.scryer.util.CollectionListHelper
import org.mozilla.scryer.viewmodel.ScreenshotViewModel
import java.io.File
import java.util.*

class SortingPanelActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_SCREENSHOT_ID = "screenshot_id"
        const val EXTRA_SCREENSHOT_IDS = "screenshot_ids"
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

        fun sortOldScreenshot(context: Context, screenshot: ScreenshotModel): Intent {
            val intent = Intent(context, SortingPanelActivity::class.java)
            intent.putExtra(EXTRA_SCREENSHOT_ID, screenshot.id)
            return intent
        }

        fun sortScreenshots(context: Context, screenshots: List<ScreenshotModel>): Intent {
            val intent = Intent(context, SortingPanelActivity::class.java)
            val list = ArrayList<String>()
            list.addAll(screenshots.map { it.id })
            intent.putStringArrayListExtra(EXTRA_SCREENSHOT_IDS, list)
            return intent
        }

        private fun isSortingNewScreenshot(intent: Intent?): Boolean {
            return intent?.getStringExtra(EXTRA_PATH)?.let {
                true
            } ?: false
        }
    }

    private val sortingPanel: SortingPanel by lazy { findViewById<SortingPanel>(R.id.sorting_panel) }

    private val screenshotViewModel: ScreenshotViewModel by lazy {
        ScreenshotViewModel.get(this)
    }

    private val unsortedScreenshots = LinkedList<ScreenshotModel>()
    private val sortedScreenshots = LinkedList<ScreenshotModel>()
    private var currentScreenshot: ScreenshotModel? = null

    private var unsortedCollection: CollectionModel? = null

    private val collectionColors = mutableListOf<Int>()

    /** This will only become non-null if we are sorting a whole collection */
    private var collectionId: String? = null

    /** True when we are sorting screenshots that haven't been reviewed by the user before */
    private val isSortingUncategorized: Boolean
        get() = (collectionId == CollectionModel.UNCATEGORIZED)

    /** True when: 1. Capture and save 2. Move screenshot to another collection  */
    private val isSortingSingleScreenshot: Boolean
        get() = (collectionId == null)

    /*  Update the timestamp of each suggest collection at once in onStop(), so
        the order of collections will keep static during multiple-sorting */
    private val suggestCollectionCreateTime = mutableListOf<Pair<CollectionModel, Long>>()

    /*  Notify promoter the first time one of the screenshots is sorted to any collection */
    private var hasNotifiedPromoter = false

    private val toast: ScryerToast by lazy {
        ScryerToast(this)
    }

    private val persistModel: PersistModel by lazy {
        ViewModelProviders.of(this)[PersistModel::class.java]
    }

    private val shouldShowCollectionPanel: Boolean
        get() = if (intent.hasExtra(EXTRA_SHOW_ADD_TO_COLLECTION)) {
            intent.getBooleanExtra(EXTRA_SHOW_ADD_TO_COLLECTION, true)

        } else {
            true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sorting_panel)

        loadCollectionColorList()
        loadScreenshots(intent, this::onLoadScreenshotsSuccess)
        initSortingPanel()
    }

    override fun onStart() {
        super.onStart()
        this.lifecycle.addObserver(this.sortingPanel)
    }

    override fun onStop() {
        this.lifecycle.removeObserver(this.sortingPanel)

        for ((suggestCollection, createTime) in suggestCollectionCreateTime) {
            suggestCollection.createdDate = createTime
            launch {
                screenshotViewModel.updateCollection(suggestCollection)
            }
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        ViewModelProviders.of(this).get(PersistModel::class.java).reset()
        loadScreenshots(intent, this::onLoadScreenshotsSuccess)
    }

    override fun onBackPressed() {
        if (isSortingUncategorized) {
            val dialog = ConfirmationDialog.build(this,
                    getString(R.string.dialogue_skipsorting_title_skip),
                    getString(R.string.dialogue_skipsorting_action_skip),
                    DialogInterface.OnClickListener { _, _ ->
                        flushToUnsortedCollection()
                        finishAndRemoveTask()
                        TelemetryWrapper.cancelSorting(MULTIPLE)
                    },
                    getString(android.R.string.cancel),
                    DialogInterface.OnClickListener { _, _ ->
                    })
            dialog.viewHolder.message?.text = getString(R.string.dialogue_skipsorting_content_moveto)
            dialog.viewHolder.subMessage?.visibility = View.VISIBLE
            dialog.viewHolder.subMessage?.text = getString(R.string.dialogue_skipsorting_content_count,
                    unsortedScreenshots.size + 1)
            dialog.asAlertDialog().show()
        } else {
            super.onBackPressed()

            if (isSortingSingleScreenshot) {
                TelemetryWrapper.cancelSorting(SINGLE)
            } else {
                TelemetryWrapper.cancelSorting(MULTIPLE)
            }
        }
    }

    private fun flushToUnsortedCollection() {
        showAddedToast(unsortedCollection, false)
        launch {
            screenshotViewModel.batchMove(unsortedScreenshots, CollectionModel.CATEGORY_NONE)
        }
    }

    private fun loadCollectionColorList() {
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
        collectionData.observe(this, Observer { collection ->
            unsortedCollection = collection.find { it.id == CollectionModel.CATEGORY_NONE }
        })

        sortingPanel.collectionSource = collectionData
        sortingPanel.showCollectionPanel = shouldShowCollectionPanel
        sortingPanel.callback = object : SortingPanelAdapter.Callback {
            override fun onClickStart(collection: CollectionModel) {
                onCollectionClickStart(collection)
            }

            override fun onClickFinish(collection: CollectionModel) {
                onCollectionClickFinish(collection)
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

            if (!isSortingNewScreenshot(intent) && unsortedScreenshots.isEmpty()) {
                sortingPanel.setActionText(getString(android.R.string.cancel))
            }
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

    private fun loadScreenshots(intent: Intent?, onFinished: (list: List<ScreenshotModel>) -> Unit) {
        if (persistModel.isLoaded()) {
            onFinished.invoke(persistModel.getScreenshots())
            return
        }

        intent ?: run {
            onLoadScreenshotsFailed()
            return
        }
        collectionId = null

        launch(UI) {
            when {
                intent.hasExtra(EXTRA_PATH) -> {
                    TelemetryWrapper.promptSortingPage(SINGLE)
                    loadNewScreenshot(getFilePath(intent))
                }

                intent.hasExtra(EXTRA_SCREENSHOT_ID) -> {
                    TelemetryWrapper.promptSortingPage(SINGLE)
                    loadOldScreenshot(intent.getStringExtra(EXTRA_SCREENSHOT_ID))
                }

                intent.hasExtra(EXTRA_COLLECTION_ID) -> {
                    TelemetryWrapper.promptSortingPage(MULTIPLE)
                    collectionId = intent.getStringExtra(EXTRA_COLLECTION_ID)
                    collectionId?.let {
                        loadCollection(it)
                    }
                }

                intent.hasExtra(EXTRA_SCREENSHOT_IDS) -> {
                    val list: List<String>? = intent.getStringArrayListExtra(EXTRA_SCREENSHOT_IDS)
                    list?.let {
                        loadScreenshots(list)
                    }
                }

                else -> {
                    null
                }

            }?.let {
                persistModel.onScreenshotLoaded(it)
                onFinished.invoke(it)

            }?: onLoadScreenshotsFailed()
        }
    }

    private suspend fun loadScreenshots(
            ids: List<String>
    ): List<ScreenshotModel> = withContext(DefaultDispatcher) {
        ids.mapNotNull {
            screenshotViewModel.getScreenshot(it)
        }
    }

    private suspend fun loadNewScreenshot(
            path: String
    ): List<ScreenshotModel>? = withContext(DefaultDispatcher) {
        createNewScreenshot(path)?.let {
            val result = listOf(it)
            screenshotViewModel.addScreenshot(result)
            result
        }
    }

    private suspend fun loadOldScreenshot(
            screenshotId: String
    ): List<ScreenshotModel>? = withContext(DefaultDispatcher) {
        screenshotViewModel.getScreenshot(screenshotId)?.let {
            listOf(it)
        }
    }

    private suspend fun loadCollection(
            collectionId: String
    ): List<ScreenshotModel>? = withContext(DefaultDispatcher) {
        val idList = if (collectionId == CollectionModel.CATEGORY_NONE) {
            listOf(CollectionModel.UNCATEGORIZED, CollectionModel.CATEGORY_NONE)
        } else {
            listOf(collectionId)
        }

        screenshotViewModel.getScreenshotList(idList)
    }

    private fun onLoadScreenshotsSuccess(screenshots: List<ScreenshotModel>) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
            return
        }

        this.sortedScreenshots.clear()
        this.unsortedScreenshots.clear()

        val panelModel = ViewModelProviders.of(this).get(PersistModel::class.java)
        val currentIndex = panelModel.getCurrentIndex()

        val sorted = screenshots.sortedByDescending { it.lastModified }
        this.sortedScreenshots.addAll(sorted.subList(0, currentIndex))
        this.unsortedScreenshots.addAll(sorted.subList(currentIndex, screenshots.size))

        if (screenshots.size == 1) {
            sortingPanel.setActionText(getString(if (isSortingNewScreenshot(intent)) {
                R.string.action_later
            } else {
                android.R.string.cancel
            }))
            sortingPanel.setProgressVisibility(View.INVISIBLE)
            sortingPanel.setFakeLayerVisibility(View.INVISIBLE)
        } else {
            sortingPanel.setActionText(getString(R.string.multisorting_action_next))
            sortingPanel.setProgressVisibility(View.VISIBLE)
            sortingPanel.setFakeLayerVisibility(View.VISIBLE)
        }

        sortingPanel.setActionCallback {
            if (isSortingUncategorized || isSortingNewScreenshot(intent)) {
                showAddedToast(unsortedCollection, unsortedScreenshots.isNotEmpty())
            }
            onNewModelAvailable()
            panelModel.onNextScreenshot()

            if (screenshots.size == 1) {
                TelemetryWrapper.cancelSorting(SINGLE)
            }
        }

        if (!shouldShowCollectionPanel) {
            Handler().postDelayed({ finishAndRemoveTask() }, 1000)
        }

        onNewModelAvailable()
    }

    private fun showAddedToast(model: CollectionModel?, stickToCollapsedPanel: Boolean) {
        model?.let {
            val yOffset = if (sortingPanel.isCollapse() && stickToCollapsedPanel) {
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

    private fun onCollectionClickStart(collection: CollectionModel) {
        launch(UI.immediate) {
            val screenshot = currentScreenshot ?: return@launch

            if (SuggestCollectionHelper.isSuggestCollection(collection)) {
                // Once the user selects a suggest collection, we update its id to
                // a random UUID, so that it will be treated as a normal collection from then on

                collection.color = CollectionListHelper.nextCollectionColor(
                        this@SortingPanelActivity, true)
                withContext(DefaultDispatcher) {
                    screenshotViewModel.updateCollectionId(collection, UUID.randomUUID().toString())
                }
                suggestCollectionCreateTime.add(Pair(collection, System.currentTimeMillis()))
            }

            screenshot.collectionId = collection.id
            withContext(DefaultDispatcher) {
                screenshotViewModel.addScreenshot(listOf(screenshot))
            }

            if (isSortingSingleScreenshot) {
                showAddedToast(collection, false)
            }

            if (isSortingSingleScreenshot) {
                TelemetryWrapper.sortScreenshot(SuggestCollectionHelper.getSuggestCollectionNameForTelemetry(this@SortingPanelActivity, collection.name), SINGLE)
            } else {
                TelemetryWrapper.sortScreenshot(SuggestCollectionHelper.getSuggestCollectionNameForTelemetry(this@SortingPanelActivity, collection.name), MULTIPLE)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onCollectionClickFinish(collection: CollectionModel) {
        onNewModelAvailable()
        persistModel.onNextScreenshot()

        onScreenshotSorted()
    }

    private fun onScreenshotSorted() {
        if (hasNotifiedPromoter) {
            return
        }
        hasNotifiedPromoter = true
        Promoter.onScreenshotSorted(this)
    }

    private fun onNewCollectionClicked() {
        // Since suggest collection is visible on sorting panel, it's reasonable to show error msg
        // when user input a name identical to suggest collection, there's no need to exclude
        // suggest collection when matching for conflict name, set excludeSuggestion to false
        CollectionNameDialog.createNewCollection(this, screenshotViewModel, false) {
            showAddedToast(it, true)
            onCollectionClickStart(it)
            onCollectionClickFinish(it)
        }

        TelemetryWrapper.createCollectionWhenSorting()
    }

    private fun createNewScreenshot(path: String): ScreenshotModel? {
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

class PersistModel : ViewModel() {
    private var screenshots = mutableListOf<ScreenshotModel>()
    private var isLoaded = false
    private var currentIdx = 0

    fun onScreenshotLoaded(screenshots: List<ScreenshotModel>) {
        this.screenshots.clear()
        this.screenshots.addAll(screenshots)
        isLoaded = true
    }

    fun getScreenshots(): List<ScreenshotModel> {
        return screenshots
    }

    fun isLoaded(): Boolean {
        return isLoaded
    }

    fun getCurrentIndex(): Int {
        return currentIdx
    }

    fun onNextScreenshot() {
        currentIdx++
    }

    fun reset() {
        if (isLoaded) {
            isLoaded = false
            screenshots.clear()
            currentIdx = 0
        }
    }
}
