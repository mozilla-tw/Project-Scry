/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.search

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.mozilla.scryer.R
import org.mozilla.scryer.collectionview.ListSelector
import org.mozilla.scryer.collectionview.ScreenshotAdapter
import org.mozilla.scryer.persistence.LoadingViewModel
import org.mozilla.scryer.persistence.ScreenshotModel

class SearchAdapter(
        context: Context?,
        selector: ListSelector<ScreenshotModel>? = null,
        onItemClickListener: ((item: ScreenshotModel, view: View?, position: Int) -> Unit)? = null
) : ScreenshotAdapter(context, selector, onItemClickListener) {

    companion object {
        const val VIEW_TYPE_ITEM = 0
        const val VIEW_TYPE_LOADING = 1
    }

    private var loadingViewModel: LoadingViewModel? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        if (loadingViewModel != null && viewType == VIEW_TYPE_LOADING) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_loading, parent, false)

            val holder = LoadingViewHolder(view)
            holder.primaryTextView = view.findViewById(R.id.primaryTextView)
            holder.secondaryTextView = view.findViewById(R.id.secondaryTextView)

            return holder
        }

        return super.onCreateViewHolder(parent, viewType)
    }

    override fun getItemViewType(position: Int): Int {
        if (loadingViewModel == null) {
            return VIEW_TYPE_ITEM
        }

        if (position == itemCount - 1) {
            return VIEW_TYPE_LOADING
        }

        return VIEW_TYPE_ITEM
    }

    override fun getItemCount(): Int {
        return if (loadingViewModel == null) {
            screenshotList.size
        } else {
            screenshotList.size + 1
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (loadingViewModel != null && getItemViewType(position) == VIEW_TYPE_LOADING) {
            if (holder !is LoadingViewHolder) return

            holder.primaryTextView?.text = loadingViewModel?.primaryText
            holder.secondaryTextView?.text = loadingViewModel?.secondaryText

            return
        }

        super.onBindViewHolder(holder, position)
    }

    fun showLoadingView(loadingViewModel: LoadingViewModel?) {
        this.loadingViewModel = loadingViewModel
        notifyItemInserted(itemCount - 1)
    }
}

class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    var primaryTextView: TextView? = null
    var secondaryTextView: TextView? = null
}