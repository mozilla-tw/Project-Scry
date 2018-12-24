/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.sortingpanel

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import org.mozilla.scryer.R
import org.mozilla.scryer.extension.dpToPx
import org.mozilla.scryer.extension.getValidPosition
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.SuggestCollectionHelper

class SortingPanelAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
    companion object {
        private const val TYPE_NEW_COLLECTION = 0
        private const val TYPE_COLLECTION_ITEM = 1

        private const val DURATION_SELECT_ANIMATION = 200L
    }

    var collections: List<CollectionModel>? = null
        set(value) {
            newCollectionItemPosition = value?.size ?: 0
            field = value
        }

    private var newCollectionItemPosition = 0

    var callback: Callback? = null

    private var selectedHolder: ItemHolder? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
        when (viewType) {
            TYPE_NEW_COLLECTION -> {
                return createNewCollectionHolder(parent)
            }

            TYPE_COLLECTION_ITEM -> {
                return createCollectionHolder(parent)
            }
        }
        throw IllegalStateException("unexpected item type $viewType")
    }

    override fun getItemCount(): Int {
        return (collections?.size ?: 0) + 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            newCollectionItemPosition -> TYPE_NEW_COLLECTION
            else -> TYPE_COLLECTION_ITEM
        }
    }

    override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
        if (position == newCollectionItemPosition) {
            bindNewCollectionItem(holder)
        } else {
            bindCollectionItem(holder, position)
        }
    }

    private fun bindNewCollectionItem(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
        (holder as? NewItemHolder)?.let {
            holder.title?.text = holder.itemView.resources.getString(R.string.action_create)
        }
    }

    private fun bindCollectionItem(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
        val itemHolder = (holder as? ItemHolder) ?: return
        val item = getItem(position) ?: return
        val context = itemHolder.itemView.context

        itemHolder.title?.text = item.name
        if (SuggestCollectionHelper.isSuggestCollection(item)) {
            itemHolder.itemView.background = ContextCompat.getDrawable(itemHolder.itemView.context,
                    R.drawable.sorting_panel_suggest_item_bkg)
            itemHolder.itemView.elevation = 0f
            itemHolder.title?.setTextColor(ContextCompat.getColor(context,
                    R.color.sorting_panel_suggest_item_background))
        } else {
            itemHolder.itemView.background = itemHolder.background
            DrawableCompat.setTint(holder.itemView.background, item.color)
            itemHolder.itemView.elevation = 1f.dpToPx(itemHolder.itemView.resources.displayMetrics).toFloat()
            itemHolder.title?.setTextColor(ContextCompat.getColor(context,
                    R.color.sorting_panel_collection_item_background))
        }
    }

    fun onNewScreenshotReady() {
        selectedHolder?.let {
            setSelectState(it, false)
        }
        selectedHolder = null
    }

    private fun getItem(position: Int): CollectionModel? {
        return collections?.get(position)
    }

    private fun createNewCollectionHolder(parent: ViewGroup): androidx.recyclerview.widget.RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_sorting_panel_item, parent, false)
        view.findViewById<View>(R.id.plus_icon).visibility = View.VISIBLE
        view.background = parent.context.getDrawable(R.drawable.sorting_panel_create_bkg)

        val holder = NewItemHolder(view)
        holder.title = view.findViewById(R.id.title)
        holder.title?.setTextColor(ContextCompat.getColor(parent.context, R.color.grey90))

        holder.itemView.setOnClickListener { _ ->
            holder.getValidPosition {
                callback?.onNewCollectionClick()
            }
        }
        holder.itemView.background = DrawableCompat.wrap(holder.itemView.background)
        return holder
    }

    private fun createCollectionHolder(parent: ViewGroup): androidx.recyclerview.widget.RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_sorting_panel_item, parent, false)
        view.findViewById<View>(R.id.plus_icon).visibility = View.GONE
        view.background = parent.context.getDrawable(R.drawable.rect_2dp)

        val holder = ItemHolder(view)
        holder.title = view.findViewById(R.id.title)
        holder.title?.setTextColor(ContextCompat.getColor(parent.context, R.color.white))
        holder.checkIcon = view.findViewById(R.id.check_icon)

        holder.itemView.setOnClickListener { _ ->
            holder.getValidPosition { position ->
                getItem(position)?.let {
                    onCollectionClicked(it, holder)
                }
            }
        }
        holder.background = DrawableCompat.wrap(holder.itemView.background)
        holder.itemView.background = holder.background

        return holder
    }

    private fun onCollectionClicked(model: CollectionModel, holder: ItemHolder) {
        selectedHolder = selectedHolder?.let { return }?: holder

        setSelectState(holder, true)
        callback?.onClickStart(model)

        holder.itemView.postDelayed({
            callback?.onClickFinish(model)

        }, DURATION_SELECT_ANIMATION)
    }

    private class ItemHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        var title: TextView? = null
        var checkIcon: View? = null
        var background: Drawable? = null
    }

    private class NewItemHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        var title: TextView? = null
    }

    private fun setSelectState(holder: ItemHolder, isSelected: Boolean) {
        if (isSelected) {
            holder.checkIcon?.visibility = View.VISIBLE
            holder.title?.visibility = View.INVISIBLE
        } else {
            holder.checkIcon?.visibility = View.INVISIBLE
            holder.title?.visibility = View.VISIBLE
        }
    }

    interface Callback {
        fun onClickStart(collection: CollectionModel)
        fun onClickFinish(collection: CollectionModel)
        fun onNewCollectionClick()
    }
}
