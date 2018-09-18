/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.sortingpanel

import android.arch.lifecycle.*
import android.content.Context
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.support.design.widget.BottomSheetBehavior
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import org.mozilla.scryer.R
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.ui.GridItemDecoration
import java.io.File

class SortingPanel : FrameLayout, DefaultLifecycleObserver {

    private val recyclerView: RecyclerView by lazy { findViewById<RecyclerView>(R.id.panel_recycler_view) }
    private val coordinatorLayout: View by lazy { findViewById<View>(R.id.coordinator_layout) }
    private val panelView: View by lazy { findViewById<View>(R.id.panel_container) }
    private val overlay: View by lazy { findViewById<View>(R.id.background_overlay) }
    private val imageView: ImageView by lazy { findViewById<ImageView>(R.id.image_view) }
    private val hintBar: View by lazy { findViewById<View>(R.id.panel_hint_bar) }
    private val progressView: TextView by lazy { findViewById<TextView>(R.id.panel_title_progress_text) }
    private val actionButton: TextView by lazy { findViewById<TextView>(R.id.panel_title_action_button) }
    private val fakeLayer: View by lazy { findViewById<View>(R.id.fake_layer) }

    private val adapter = SortingPanelAdapter()

    private var actionCallback: (() -> Unit)? = null

    private var collectionSourceObserver = Observer<List<CollectionModel>> { collections ->
        collections?.filter {
            it.id != CollectionModel.CATEGORY_NONE

        }?.sortedBy {
            it.date

        }?.let {
            this.adapter.collections = it
            this.adapter.notifyDataSetChanged()
        }
    }

    var screenshot: ScreenshotModel? = null
        set(value) {
            value?.let {
                // TODO: Loading view
                Glide.with(this).load(File(it.absolutePath)).into(imageView)
                adapter.onNewScreenshotReady()
                field = value
            }
        }

    var collectionSource: LiveData<List<CollectionModel>>? = null

    var callback: Callback? = null
        set(value) {
            this.adapter.callback = value
        }

    var showCollectionPanel: Boolean = true
        set(value) {
            if (value) {
                coordinatorLayout.visibility = View.VISIBLE
            } else {
                coordinatorLayout.visibility = View.GONE
            }
        }

    private val columnCount: Int
        get() = context.resources.getInteger(R.integer.sorting_panel_column_count)

    constructor(context: Context): super(context) {
        initView()
    }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
        initView()
    }

    override fun onStart(owner: LifecycleOwner) {
        this.collectionSource?.observe(owner, this.collectionSourceObserver)
    }

    override fun onStop(owner: LifecycleOwner) {
        this.collectionSource?.removeObserver(this.collectionSourceObserver)
    }

    private fun initView() {
        View.inflate(this.context, R.layout.view_sorting_panel, this)

        initRecyclerView()
        initPanel()
    }

    private fun initRecyclerView() {
        this.recyclerView.layoutManager = GridLayoutManager(context, columnCount,
                GridLayoutManager.VERTICAL,
                false)

        val space = resources.getDimensionPixelSize(R.dimen.sorting_panel_item_spacing)
        this.recyclerView.addItemDecoration(SortingPanelDecoration(space, 0, this))
        this.recyclerView.adapter = this.adapter
    }

    override fun onSaveInstanceState(): Parcelable {
        val parcelable = super.onSaveInstanceState()
        val savedState = SavedState(parcelable)
        savedState.overlayAlpha = this.overlay.alpha
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            overlay.alpha = state.overlayAlpha
            hintBar.alpha = 1 - state.overlayAlpha
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private fun initPanel() {
        val behavior = BottomSheetBehavior.from(panelView)

        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.peekHeight = resources.getDimensionPixelSize(
                R.dimen.sorting_panel_title_height)

        hintBar.alpha = 0f

        val expandButton = this.panelView.findViewById<View>(R.id.panel_expend_button)
        expandButton.setOnClickListener {
            if (behavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        actionButton.setOnClickListener {
            actionCallback?.invoke()
        }

        val rootView = findViewById<View>(R.id.root_view)
        rootView.setOnClickListener {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        behavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                this@SortingPanel.overlay.alpha = slideOffset
                this@SortingPanel.hintBar.alpha = 1 - slideOffset
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {}
        })
    }

    fun getCollapseHeight(): Int {
        return BottomSheetBehavior.from(panelView).peekHeight
    }

    fun isCollapse(): Boolean {
        return BottomSheetBehavior.from(panelView).state == BottomSheetBehavior.STATE_COLLAPSED
    }

    fun setActionText(text: String) {
        actionButton.text = text
    }

    fun setActionCallback(callback: () -> Unit) {
        actionCallback = callback
    }

    fun setProgress(current: Int, total: Int) {
        if (progressView.visibility != View.VISIBLE) {
            return
        }
        progressView.visibility = if (total == 1) { View.INVISIBLE } else { View.VISIBLE }
        progressView.text = resources.getString(R.string.multisorting_count_number, current, total)
    }

    fun setProgressVisibility(visibility: Int) {
        progressView.visibility = visibility
    }

    fun setFakeLayerVisibility(visibility: Int) {
        fakeLayer.visibility = visibility
    }

    internal class SavedState : BaseSavedState {
        var overlayAlpha: Float = 0f

        constructor(source: Parcel): super(source) {
            this.overlayAlpha = source.readFloat()
        }

        constructor(superState: Parcelable): super(superState)

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeFloat(overlayAlpha)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState {
                return SavedState(parcel)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }

    class SortingPanelDecoration(private val space: Int, top: Int, private val panel: SortingPanel)
        : GridItemDecoration(panel.columnCount, 0, top, 0, space, space) {

        override fun setSpaces(outRect: Rect, position: Int) {
            val columnCount = panel.columnCount
            if (columnCount <= 2) {
                outRect.left = if (position % columnCount == 0) 0 else space / 2
                outRect.top = 0
                outRect.right = if (position % columnCount == columnCount - 1) 0 else space / 2
                outRect.bottom = space

            } else {
                /**
                 * x: padding for left/right-most items (left-most item only have right padding, vice versa)
                 * y: padding for middle items (padding at both left & right sides)
                 *
                 * size of left/right-most items must be equal to the size of middle items
                 *   x = 2y
                 *
                 * 2x + 2(middleItemCount)y = sum of all padding
                 * => 2x + 2(itemCount - 2)y = space * (itemCount - 1)
                 * => 4y + 2(itemCount - 2)y = space * (itemCount - 1)
                 * => y = space * (itemCount  - 1) / (4 + 2 * (itemCount - 2))y
                 */
                val y = space * (columnCount - 1) / (4 + 2 * (columnCount - 2))
                val x = y * 2

                outRect.top = 0
                outRect.bottom = space
                when {
                    position % columnCount == 0 -> {
                        outRect.left = 0
                        outRect.right = x
                    }
                    position % columnCount == columnCount - 1 -> {
                        outRect.left = x
                        outRect.right = 0
                    }
                    else -> {
                        outRect.left = y
                        outRect.right = y
                    }
                }
            }
        }
    }

    interface Callback {
        fun onClick(collection: CollectionModel)
        fun onNewCollectionClick()
    }
}