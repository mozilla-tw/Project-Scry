/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.mozilla.scryer.collectionview

import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.android.synthetic.main.view_sorting_panel.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.scryer.R
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.persistence.SuggestCollectionHelper
import org.mozilla.scryer.promote.Promoter
import org.mozilla.scryer.sortingpanel.SortingPanel
import org.mozilla.scryer.sortingpanel.SortingPanelAdapter
import org.mozilla.scryer.telemetry.TelemetryWrapper
import org.mozilla.scryer.telemetry.TelemetryWrapper.ExtraValue.MULTIPLE
import org.mozilla.scryer.telemetry.TelemetryWrapper.ExtraValue.SINGLE
import org.mozilla.scryer.ui.CollectionNameDialog
import org.mozilla.scryer.util.CollectionListHelper
import org.mozilla.scryer.viewmodel.ScreenshotViewModel
import java.util.*

class SortingPanelDialog(
        private val activity: FragmentActivity,
        val screenshots: List<ScreenshotModel>
) {
    private val suggestCollectionCreateTime = mutableListOf<Pair<CollectionModel, Long>>()
    private var onDismissListener: (() -> Unit)? = null
    //private var dialog: AlertDialog? = null

    private val panelCallback = object : SortingPanelAdapter.Callback {
        override fun onClickStart(collection: CollectionModel) {
            GlobalScope.launch(Dispatchers.Main.immediate) {
                val model = ScreenshotViewModel.get(activity)

                if (SuggestCollectionHelper.isSuggestCollection(collection)) {
                    collection.color = CollectionListHelper.nextCollectionColor(
                            activity, true)
                    withContext(Dispatchers.Default) {
                        model.updateCollectionId(collection, UUID.randomUUID().toString())
                    }
                    suggestCollectionCreateTime.add(Pair(collection, System.currentTimeMillis()))
                }

                screenshots.forEach { it.collectionId = collection.id }
                withContext(Dispatchers.Default) {
                    model.updateScreenshots(screenshots)
                }

                if (screenshots.size > 1) {
                    TelemetryWrapper.sortScreenshot(SuggestCollectionHelper.getSuggestCollectionNameForTelemetry(activity, collection.name), MULTIPLE)
                } else {
                    TelemetryWrapper.sortScreenshot(SuggestCollectionHelper.getSuggestCollectionNameForTelemetry(activity, collection.name), SINGLE)
                }
            }
        }

        override fun onClickFinish(collection: CollectionModel) {
            dismiss()
            Promoter.onScreenshotSorted(activity)
        }

        override fun onNewCollectionClick() {
            CollectionNameDialog.createNewCollection(activity, ScreenshotViewModel.get(activity), false) {
                onClickStart(it)
                onClickFinish(it)
            }

            TelemetryWrapper.createCollectionWhenSorting()
        }
    }

    private val panelView = object : SortingPanel(activity) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            return if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                dismiss()

                if (screenshots.size > 1) {
                    TelemetryWrapper.cancelSorting(MULTIPLE)
                } else {
                    TelemetryWrapper.cancelSorting(SINGLE)
                }
                true

            } else {
                super.dispatchKeyEvent(event)
            }
        }
    }

    fun setOnDismissListener(listener: () -> Unit) {
        onDismissListener = listener
    }

    fun show() {
        GlobalScope.launch(Dispatchers.Main.immediate) {
            panelView.apply {
                initPanelUI(this)
                collectionSource = withContext(Dispatchers.Default) {
                    ScreenshotViewModel.get(activity).getCollections()
                }
                callback = panelCallback
                setActionCallback {
                    dismiss()

                    if (screenshots.size > 1) {
                        TelemetryWrapper.cancelSorting(MULTIPLE)
                    } else {
                        TelemetryWrapper.cancelSorting(SINGLE)
                    }
                }
            }
            panelView.onStart(activity)
            activity.windowManager.addView(panelView, WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_APPLICATION,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    PixelFormat.TRANSLUCENT))

            if (screenshots.size > 1) {
                TelemetryWrapper.promptSortingPage(MULTIPLE)
            } else {
                TelemetryWrapper.promptSortingPage(SINGLE)
            }
        }
    }

    private fun dismiss() {
        activity.windowManager.removeViewImmediate(panelView)
        panelView.onStop(activity)
        onDismissListener?.invoke()
        updateCollectionOrderToRepository()
    }

    private fun updateCollectionOrderToRepository() {
        for ((suggestCollection, createTime) in suggestCollectionCreateTime) {
            suggestCollection.createdDate = createTime
            GlobalScope.launch(Dispatchers.IO) {
                ScreenshotViewModel.get(activity).updateCollection(suggestCollection)
            }
        }
    }

    private fun initPanelUI(panel: SortingPanel) {
        val actionText = panel.findViewById<TextView>(R.id.panel_title_action_button)
        actionText.text = activity.getText(android.R.string.cancel)

        panel.fake_layer.visibility = View.INVISIBLE
        panel.image_view.visibility = View.INVISIBLE

        panel.setBackgroundColor(ContextCompat.getColor(panel.context, R.color.dialogScrim))
    }
}