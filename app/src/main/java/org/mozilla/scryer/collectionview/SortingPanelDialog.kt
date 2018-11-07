/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.mozilla.scryer.collectionview

import android.support.v4.app.FragmentActivity
import android.support.v7.app.AlertDialog
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.mozilla.scryer.R
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.persistence.SuggestCollectionHelper
import org.mozilla.scryer.sortingpanel.SortingPanel
import org.mozilla.scryer.sortingpanel.SortingPanelAdapter
import org.mozilla.scryer.ui.CollectionNameDialog
import org.mozilla.scryer.util.CollectionListHelper
import org.mozilla.scryer.viewmodel.ScreenshotViewModel
import java.util.*

class SortingPanelDialog(private val activity: FragmentActivity, val screenshots: List<ScreenshotModel>) {
    private val suggestCollectionCreateTime = mutableListOf<Pair<CollectionModel, Long>>()
    private var onDismissListener: (() -> Unit)? = null
    private var dialog: AlertDialog? = null

    fun setOnDismissListener(listener: () -> Unit) {
        onDismissListener = listener
    }

    private val panelCallback = object : SortingPanelAdapter.Callback {
        override fun onClickStart(collection: CollectionModel) {
            launch(UI.immediate) {
                val model = ScreenshotViewModel.get(activity)

                if (SuggestCollectionHelper.isSuggestCollection(collection)) {
                    collection.color = CollectionListHelper.nextCollectionColor(
                            activity, true)
                    withContext(DefaultDispatcher) {
                        model.updateCollectionId(collection, UUID.randomUUID().toString())
                    }
                    suggestCollectionCreateTime.add(Pair(collection, System.currentTimeMillis()))
                }

                screenshots.forEach { it.collectionId = collection.id }
                withContext(DefaultDispatcher) {
                    model.addScreenshot(screenshots)
                }
            }
        }

        override fun onClickFinish(collection: CollectionModel) {
            dialog?.dismiss()
        }

        override fun onNewCollectionClick() {
            CollectionNameDialog.createNewCollection(activity, ScreenshotViewModel.get(activity), false) {
                //showAddedToast(it, true)
//                            onCollectionClickStart(it)
//                            onCollectionClickFinish(it)
            }
        }
    }

    fun show() {
        dialog?.let {
            return
        }

        launch(UI.immediate) {
            val panel = View.inflate(activity, R.layout.activity_sorting_panel, null) as SortingPanel

            val dialog = AlertDialog.Builder(activity, R.style.sorting_dialog)
                    .setView(panel)
                    .create()
                    .apply {
                        setOnShowListener {
                            panel.onStart(activity)
                        }

                        setOnDismissListener {
                            panel.onStop(activity)
                            updateCollectionOrderToRepository()
                            onDismissListener?.invoke()
                        }
                    }

            panel.apply {
                initPanelUI(this)
                collectionSource = withContext(DefaultDispatcher) {
                    ScreenshotViewModel.get(activity).getCollections()
                }
                callback = panelCallback
                setActionCallback {
                    dialog.dismiss()
                }
            }

            dialog.show()

            val params = WindowManager.LayoutParams()
            params.copyFrom(dialog.window.attributes)
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            dialog.window.attributes = params
            panel.requestLayout()

            this@SortingPanelDialog.dialog = dialog
        }
    }

    private fun updateCollectionOrderToRepository() {
        for ((suggestCollection, createTime) in suggestCollectionCreateTime) {
            suggestCollection.createdDate = createTime
            launch {
                ScreenshotViewModel.get(activity).updateCollection(suggestCollection)
            }
        }
    }

    private fun initPanelUI(panel: SortingPanel) {
        val actionText = panel.findViewById<TextView>(R.id.panel_title_action_button)
        actionText.text = activity.getText(android.R.string.cancel)
    }
}