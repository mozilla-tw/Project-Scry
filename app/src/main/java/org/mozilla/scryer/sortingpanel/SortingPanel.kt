/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.sortingpanel

import android.arch.lifecycle.DefaultLifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.content.Context
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.support.design.widget.BottomSheetBehavior
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import org.mozilla.scryer.R
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import java.io.File

class SortingPanel : FrameLayout, DefaultLifecycleObserver {
    companion object {
        private const val COLUMN_PORTRAIT = 2
        private const val COLUMN_LANDSCAPE = 3
    }

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

    private var collectionSourceObserver = Observer<List<CollectionModel>> {
        it?.filter {
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

    constructor(context: Context): super(context) {
        initView()
    }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
        initView()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        this.collectionSource?.observe(owner, this.collectionSourceObserver)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        this.collectionSource?.removeObserver(this.collectionSourceObserver)
    }

    private fun initView() {
        View.inflate(this.context, R.layout.view_sorting_panel, this)

        initRecyclerView()
        initPanel()
    }

    private fun getColumnCount(): Int {
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val rotation = display.rotation
        return when (rotation) {
            Surface.ROTATION_0 -> COLUMN_PORTRAIT
            Surface.ROTATION_180 -> COLUMN_PORTRAIT
            else -> COLUMN_LANDSCAPE
        }
    }

    private fun initRecyclerView() {
        val columnCount = getColumnCount()
        this.recyclerView.layoutManager = GridLayoutManager(context, columnCount,
                GridLayoutManager.VERTICAL,
                false)

        val space = resources.getDimensionPixelSize(R.dimen.sorting_panel_item_spacing)
        this.recyclerView.addItemDecoration(SortingPanelDecoration(columnCount, 0, 0, 0, space, space))
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

    class SortingPanelDecoration(private val columnCount: Int,
                                 private val left: Int,
                                 private val top: Int,
                                 private val right: Int,
                                 private val vSpace: Int,
                                 private val hSpace: Int) : RecyclerView.ItemDecoration() {

        @Suppress("unused")
        constructor(columnCount: Int, space: Int) : this(columnCount, space, space, space, space, space)

        override fun getItemOffsets(outRect: Rect, view: View,
                                    parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildViewHolder(view).adapterPosition
            if (position < 0) {
                return
            }

            setSpaces(outRect, position)
        }

        private fun setSpaces(outRect: Rect, position: Int) {
            outRect.left = if (position % this.columnCount == 0) left else 0
            outRect.top = if (position < this.columnCount) top else 0
            outRect.right = if (position % this.columnCount == this.columnCount - 1) right else this.hSpace
            outRect.bottom = this.vSpace
        }
    }

    interface Callback {
        fun onClick(collection: CollectionModel)
        fun onNewCollectionClick()
    }
}