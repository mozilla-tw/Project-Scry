/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.sortingpanel

import android.graphics.Color
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.mozilla.scryer.R
import org.mozilla.scryer.persistence.CollectionModel

class SortingPanelAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val TYPE_NEW_COLLECTION = 0
        private const val TYPE_COLLECTION_ITEM = 1

        private const val POSITION_NEW_COLLECTION = 0

        private const val DURATION_SELECT_ANIMATION = 200L
    }

    var collections: List<CollectionModel>? = null
    var callback: SortingPanel.Callback? = null

    private var selectedHolder: ItemHolder? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
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
        return 1 + (collections?.size ?: 0)
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            POSITION_NEW_COLLECTION -> TYPE_NEW_COLLECTION
            else -> TYPE_COLLECTION_ITEM
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (position) {
            POSITION_NEW_COLLECTION -> {
                (holder as NewItemHolder).title?.text = holder.itemView.resources.getString(R.string.action_create)
            }
            else -> {
                (holder as ItemHolder).title?.text = collections?.get(position - 1)?.name
                val color = collections?.get(position - 1)?.color ?: Color.WHITE
                DrawableCompat.setTint(holder.itemView.background, color)
            }
        }
    }

    fun onNewScreenshotReady() {
        selectedHolder?.let {
            setSelectState(it, false)
        }
        selectedHolder = null
    }

    private fun createNewCollectionHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_sorting_panel_item, parent, false)
        view.findViewById<View>(R.id.plus_icon).visibility = View.VISIBLE
        view.background = parent.context.getDrawable(R.drawable.sorting_panel_create_bkg)

        val holder = NewItemHolder(view)
        holder.title = view.findViewById(R.id.title)
        holder.title?.setTextColor(ContextCompat.getColor(parent.context, R.color.grey90))

        holder.itemView.setOnClickListener { _ ->
            ensurePosition(holder) {
                callback?.onNewCollectionClick()
            }
        }
        holder.itemView.background = DrawableCompat.wrap(holder.itemView.background)
        return holder
    }

    private fun createCollectionHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_sorting_panel_item, parent, false)
        view.findViewById<View>(R.id.plus_icon).visibility = View.GONE
        view.background = parent.context.getDrawable(R.drawable.rect_2dp)

        val holder = ItemHolder(view)
        holder.title = view.findViewById(R.id.title)
        holder.title?.setTextColor(ContextCompat.getColor(parent.context, R.color.white))
        holder.checkIcon = view.findViewById(R.id.check_icon)

        holder.itemView.setOnClickListener { _ ->
            ensurePosition(holder) { position ->
                collections?.let {
                    onCollectionClicked(it[position - 1], holder)
                }
            }
        }
        holder.itemView.background = DrawableCompat.wrap(holder.itemView.background)
        return holder
    }

    private fun onCollectionClicked(model: CollectionModel, holder: ItemHolder) {
        selectedHolder = selectedHolder?.let { return }?: holder

        setSelectState(holder, true)

        holder.itemView.postDelayed({
            callback?.onClick(model)

        }, DURATION_SELECT_ANIMATION)
    }

    private class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var title: TextView? = null
        var checkIcon: View? = null
    }

    private class NewItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var title: TextView? = null
    }

    private fun ensurePosition(holder: RecyclerView.ViewHolder, action: (Int) -> Unit) {
        if (holder.adapterPosition != RecyclerView.NO_POSITION) {
            action(holder.adapterPosition)
        }
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
}
