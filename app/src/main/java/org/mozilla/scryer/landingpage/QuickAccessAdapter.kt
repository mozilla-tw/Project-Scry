/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.landingpage

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import com.bumptech.glide.Glide
import org.mozilla.scryer.R
import org.mozilla.scryer.collectionview.*
import org.mozilla.scryer.sortingpanel.SortingPanelActivity
import org.mozilla.scryer.extension.getValidPosition
import org.mozilla.scryer.persistence.ScreenshotModel
import java.io.File

class QuickAccessAdapter(val context: Context?) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), OnContextMenuActionListener {
    companion object {
        const val TYPE_ITEM = 0
        const val TYPE_MORE = 1
    }

    var list: List<ScreenshotModel> = emptyList()
    var clickListener: ItemClickListener? = null

    private val maxItemsToDisplay = HomeFragment.QUICK_ACCESS_ITEM_COUNT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_MORE -> createMoreHolder(parent)
            TYPE_ITEM -> createItemHolder(parent)
            else -> throw IllegalArgumentException("unrecognized view type")
        }
    }

    override fun getItemCount(): Int {
        return if (hasMoreItem()) {
            maxItemsToDisplay + 1
        } else {
            Math.min(list.size, maxItemsToDisplay)
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (isPositionForMoreButton(position)) {
            return TYPE_MORE
        }
        return TYPE_ITEM
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val viewType = getItemViewType(position)
        when (viewType) {
            TYPE_ITEM -> bindItemHolder(holder, position)
        }
    }

    override fun onContextMenuAction(item: MenuItem?, itemPosition: Int) {
        val screenshotModel: ScreenshotModel?
        if (itemPosition >= 0 && itemPosition < list.size) {
            screenshotModel = list[itemPosition]
        } else {
            return
        }

        when (item?.itemId) {
            CONTEXT_MENU_ID_MOVE_TO -> context?.let { it.startActivity(SortingPanelActivity.sortOldScreenshot(it, screenshotModel.id)) }
            CONTEXT_MENU_ID_INFO -> context?.let { showScreenshotInfoDialog(it, screenshotModel) }
            CONTEXT_MENU_ID_SHARE -> context?.let { showShareScreenshotDialog(it, screenshotModel) }
            CONTEXT_MENU_ID_DELETE -> context?.let { showDeleteScreenshotDialog(it, screenshotModel) }
        }
    }

    private fun createItemHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quick_access, parent, false)

        val holder = ScreenshotItemHolder(view, this)
        holder.image = view.findViewById(R.id.image_view)
        holder.itemView.setOnClickListener { _ ->
            holder.getValidPosition { position: Int ->
                clickListener?.onItemClick(list[position], holder)
            }
        }
        return holder
    }

    private fun createMoreHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quick_access_more, parent, false)
        val holder = object : RecyclerView.ViewHolder(view) {
        }
        view.setOnClickListener {
            clickListener?.onMoreClick(holder)
        }
        return holder
    }

    private fun bindItemHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val path = list[position].absolutePath
        val fileName = path.substring(path.lastIndexOf(File.separator) + 1)
        (holder as? ScreenshotItemHolder)?.apply {
            holder.title?.text = fileName
            holder.image?.let {
                Glide.with(holder.itemView.context)
                        .load(File(list[position].absolutePath)).into(it)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? ScreenshotItemHolder)?.image?.let {
            Glide.with(holder.itemView.context).clear(it)
        }
    }

    private fun isPositionForMoreButton(position: Int) = position >= maxItemsToDisplay

    private fun hasMoreItem() = list.size > maxItemsToDisplay

    interface ItemClickListener {
        fun onItemClick(screenshotModel: ScreenshotModel, holder: ScreenshotItemHolder)
        fun onMoreClick(holder: RecyclerView.ViewHolder)
    }
}
