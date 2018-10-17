/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.collectionview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.widget.AppCompatCheckBox
import android.support.v7.widget.CardView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import org.mozilla.scryer.BuildConfig
import org.mozilla.scryer.R
import org.mozilla.scryer.extension.getValidPosition
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.sortingpanel.SortingPanelActivity
import java.io.File

open class ScreenshotAdapter(
        val context: Context?,
        private val selector: ListSelector<ScreenshotModel>? = null,
        private val onItemClickListener: ((item: ScreenshotModel, view: View?) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), OnContextMenuActionListener {

    private var screenshotList: List<ScreenshotModel> = emptyList()
    private var recyclerView: RecyclerView? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_screenshot, parent, false)

        val holder = ScreenshotItemHolder(view, this)
        holder.cardView = view.findViewById(R.id.card_view)
        holder.title = view.findViewById(R.id.title)
        holder.image = view.findViewById(R.id.image_view)
        holder.checkbox = view.findViewById(R.id.check_box)
        holder.itemView.setOnClickListener { _ ->
            holder.getValidPosition { position: Int ->
                if (selector?.isSelectMode == true) {
                    val screenshot = screenshotList[position]
                    selector.toggleSelection(screenshot)
                    updateSelectionUI(holder, screenshot)
                } else {
                    onItemClickListener?.invoke(screenshotList[position], holder.image)
                }
            }
        }

        holder.itemView.setOnLongClickListener { _ ->
            holder.getValidPosition { position ->
                if (selector?.isSelectMode == true) {
                    return@getValidPosition
                }
                enterSelectionMode()
                selector?.toggleSelection(screenshotList[position])
            }
        }

        return holder
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    override fun getItemCount(): Int {
        return screenshotList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as? ScreenshotItemHolder) ?: return
        val screenshot = screenshotList[position]

        holder.title?.text = screenshot.collectionId
        holder.image?.let {
            Glide.with(holder.itemView.context)
                    .load(File(screenshot.absolutePath))
                    .into(it)
        }

        updateSelectionUI(holder, screenshot)

        updateNonDataRelatedUI(holder)
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        (holder as? ScreenshotItemHolder) ?: return
        updateNonDataRelatedUI(holder)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? ScreenshotItemHolder) ?: return

        holder.image?.let {
            Glide.with(holder.itemView.context)
                    .clear(it)
        }
    }

    override fun onContextMenuAction(item: MenuItem?, itemPosition: Int) {
        val screenshotModel = getItem(itemPosition)
        when (item?.itemId) {
            CONTEXT_MENU_ID_MOVE_TO -> context?.let {
                it.startActivity(SortingPanelActivity.sortOldScreenshot(it, screenshotModel.id))
            }

            CONTEXT_MENU_ID_INFO -> context?.let {
                showScreenshotInfoDialog(it, screenshotModel)
            }

            CONTEXT_MENU_ID_SHARE -> context?.let {
                showShareScreenshotDialog(it, screenshotModel)
            }

            CONTEXT_MENU_ID_DELETE -> context?.let {
                showDeleteScreenshotDialog(it, screenshotModel)
            }
        }
    }

    fun getItemFileName(position: Int): String {
        val item = screenshotList[position]
        return item.absolutePath.substring(item.absolutePath.lastIndexOf(File.separator) + 1)
    }

    fun getItem(position: Int): ScreenshotModel {
        return screenshotList[position]
    }

    open fun setScreenshotList(list: List<ScreenshotModel>) {
        screenshotList = list
    }

    fun getScreenshotList(): List<ScreenshotModel> {
        return screenshotList
    }

    private fun isSelected(screenshot: ScreenshotModel): Boolean {
        selector ?: return false
        return selector.isSelected(screenshot)
    }

    private fun updateSelectionUI(holder: ScreenshotItemHolder, screenshot: ScreenshotModel) {
        selector ?: return
        val targetView = holder.cardView ?: return

        val isSelected = isSelected(screenshot)

        val scale = if (isSelected) { 0.8f } else { 1f }
        val selectedColor = Color.TRANSPARENT
        val unselectedColor = ContextCompat.getColor(holder.itemView.context,
                R.color.collection_view_screenshot_item_border)

        if (isSelected(screenshot)) {
            holder.checkbox?.isChecked = true

            selector.processSelection(screenshot) { stateChanged ->
                targetView.animate().cancel()
                DrawableCompat.setTint(holder.itemView.background, selectedColor)
                if (stateChanged) {
                    playSelectAnimation(holder, scale) {
                        DrawableCompat.setTint(holder.itemView.background, selectedColor)
                    }
                } else {
                    targetView.scaleX = scale
                    targetView.scaleY = scale
                }
            }

        } else {
            holder.checkbox?.isChecked = false
            selector.processSelection(screenshot) { stateChanged ->
                targetView.animate().cancel()
                if (stateChanged) {
                    playSelectAnimation(holder, scale) {
                        DrawableCompat.setTint(holder.itemView.background, unselectedColor)
                    }
                } else {

                    targetView.scaleX = 1f
                    targetView.scaleY = 1f
                    DrawableCompat.setTint(holder.itemView.background, unselectedColor)
                }
            }
        }
    }

    private fun updateNonDataRelatedUI(holder: ScreenshotItemHolder) {
        holder.checkbox?.visibility = if (selector?.isSelectMode == true) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
    }

    private fun playSelectAnimation(
            holder: ScreenshotItemHolder,
            scale: Float,
            onAnimationEnd: (() -> Unit)? = null
    ) {
        val targetView = holder.cardView ?: return
        val duration = 150L

        targetView.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        onAnimationEnd?.invoke()
                    }
                })
                .duration = duration
    }

    private fun notifyVisibleItemRangeChanged() {
        val recyclerView = recyclerView ?: return

        (recyclerView.layoutManager as? LinearLayoutManager)?.apply {
            val first = findFirstVisibleItemPosition()
            val last = findLastVisibleItemPosition()
            notifyItemRangeChanged(first, last - first + 1)

        } ?: run {
            if (BuildConfig.DEBUG) {
                throw IllegalStateException("layoutManager should be the child of LinearLayoutManager ")
            } else {
                notifyDataSetChanged()
            }
        }
    }

    fun enterSelectionMode() {
        selector?.enterSelectionMode()
        notifyVisibleItemRangeChanged()
    }

    fun exitSelectionMode() {
        selector?.exitSelectionMode()
        notifyVisibleItemRangeChanged()
    }
}

class ScreenshotItemHolder(
        itemView: View,
        private val onContextMenuActionListener: OnContextMenuActionListener
) : RecyclerView.ViewHolder(itemView), View.OnCreateContextMenuListener,
        MenuItem.OnMenuItemClickListener {
    var cardView: CardView? = null
    var title: TextView? = null
    var image: ImageView? = null
    var checkbox: AppCompatCheckBox? = null

    init {
        itemView.setOnCreateContextMenuListener(this)
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        v?.context?.let {
            menu?.add(0, CONTEXT_MENU_ID_MOVE_TO, 0, it.getString(R.string.menu_shot_action_move))?.setOnMenuItemClickListener(this)
            menu?.add(0, CONTEXT_MENU_ID_INFO, 0, it.getString(R.string.info_info))?.setOnMenuItemClickListener(this)
            menu?.add(0, CONTEXT_MENU_ID_SHARE, 0, it.getString(R.string.menu_action_share))?.setOnMenuItemClickListener(this)
            menu?.add(0, CONTEXT_MENU_ID_DELETE, 0, it.getString(R.string.action_delete))?.setOnMenuItemClickListener(this)
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        onContextMenuActionListener.onContextMenuAction(item, adapterPosition)
        return false
    }
}