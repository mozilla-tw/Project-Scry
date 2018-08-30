/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.landingpage

import android.graphics.Rect
import android.os.Bundle
import android.support.annotation.LayoutRes
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.Navigation
import com.bumptech.glide.Glide
import org.mozilla.scryer.R
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.ui.CollectionNameDialog
import org.mozilla.scryer.ui.GridItemDecoration
import org.mozilla.scryer.viewmodel.ScreenshotViewModel
import java.io.File

class MainAdapter(private val fragment: Fragment?): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        const val TYPE_SECTION_NAME = 0
        const val TYPE_QUICK_ACCESS = 1
        const val TYPE_COLLECTION_ITEM = 2
        const val TYPE_NEW_COLLECTION_ITEM = 3

        const val POS_QUICK_ACCESS_TITLE = 0
        const val POS_QUICK_ACCESS_LIST = 1
        const val POS_COLLECTION_LIST_TITLE = 2

        const val PREFIX_ITEM_COUNT = 3
        const val POSTFIX_ITEM_COUNT = 1
    }

    lateinit var quickAccessContainer: View

    var collectionList: List<CollectionModel> = emptyList()
    var coverList: Map<String, ScreenshotModel> = HashMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            TYPE_SECTION_NAME ->
                return createSectionNameHolder(parent)
            TYPE_QUICK_ACCESS ->
                return createQuickAccessHolder()
            TYPE_COLLECTION_ITEM ->
                return createCollectionHolder(parent)
            TYPE_NEW_COLLECTION_ITEM ->
                return createNewCollectionHolder(parent)
        }
        throw IllegalArgumentException("unexpected view type: $viewType")
    }

    override fun getItemCount(): Int {
        return PREFIX_ITEM_COUNT + collectionList.size + POSTFIX_ITEM_COUNT
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            POS_QUICK_ACCESS_TITLE -> TYPE_SECTION_NAME
            POS_QUICK_ACCESS_LIST -> TYPE_QUICK_ACCESS
            POS_COLLECTION_LIST_TITLE -> TYPE_SECTION_NAME
            else -> {
                val itemIndex = position - PREFIX_ITEM_COUNT
                if (collectionList.isEmpty() || itemIndex >= collectionList.size) {
                    TYPE_NEW_COLLECTION_ITEM
                } else {
                    TYPE_COLLECTION_ITEM
                }
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SectionNameHolder -> bindSectionNameHolder(holder, position)
            is StaticHolder -> return
            is CollectionHolder -> bindCollectionHolder(holder ,position)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is CollectionHolder -> holder.image?.setImageBitmap(null)
        }
    }

    private fun createCollectionHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val itemView = wrapInItemView(parent, R.layout.item_collection)

        val itemHolder = CollectionHolder(itemView)
        itemHolder.title = itemView.findViewById(R.id.title)
        itemHolder.image = itemView.findViewById(R.id.image)
        itemHolder.overlay = itemView.findViewById(R.id.overlay)

        itemView.setOnClickListener {_ ->
            itemHolder.adapterPosition.takeIf { position ->
                position != RecyclerView.NO_POSITION

            }?.let { position: Int ->
                val itemIndex = position - PREFIX_ITEM_COUNT
                val bundle = Bundle().apply {
                    val model = collectionList[itemIndex]
                    putString(CollectionFragment.ARG_COLLECTION_ID, model.id)
                    putString(CollectionFragment.ARG_COLLECTION_NAME, model.name)
                }
                Navigation.findNavController(parent).navigate(R.id.action_navigate_to_collection, bundle)
            }
        }
        return itemHolder
    }

    private fun createNewCollectionHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val itemView = wrapInItemView(parent, R.layout.item_new_collection)
        itemView.elevation = 0f

        val holder = StaticHolder(itemView)
        itemView.setOnClickListener {
            holder.adapterPosition.takeIf { position ->
                position != RecyclerView.NO_POSITION

            }?.let { _ ->
                fragment?.let {
                    CollectionNameDialog.createNewCollection(parent.context, ScreenshotViewModel.get(it)) {}
                }
            }
        }
        return holder
    }

    private fun wrapInItemView(parent: ViewGroup, @LayoutRes layoutId: Int): View {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.home_collection_item_container, parent, false)
        val slot = itemView.findViewById<ViewGroup>(R.id.slot)
        inflater.inflate(layoutId, slot, true)
        return itemView
    }

    private fun createSectionNameHolder(parent: ViewGroup): SectionNameHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_home_section_title, parent, false)

        val holder = SectionNameHolder(view)
        holder.title = view.findViewById(R.id.root_view)
        return holder
    }

    private fun createQuickAccessHolder(): RecyclerView.ViewHolder {
        return StaticHolder(quickAccessContainer)
    }

    private fun bindSectionNameHolder(holder: SectionNameHolder, position: Int) {
        when (position) {
            POS_QUICK_ACCESS_TITLE -> {
                holder.title?.text = holder.title?.context?.getString(R.string.home_separator_access)
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context,
                        R.color.quick_access_background))
            }

            POS_COLLECTION_LIST_TITLE -> {
                holder.title?.text = holder.title?.context?.getString(R.string.home_separator_collection)
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context,
                        R.color.home_background))
            }
        }
    }

    private fun bindCollectionHolder(holder: CollectionHolder, position: Int) {
        val model = collectionList[position - PREFIX_ITEM_COUNT]
        holder.title?.text = model.name

        val path: String?
        if (model.id == CollectionModel.CATEGORY_NONE) {
            path = getCoverPathForUnsortedCollection()
            holder.overlay?.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.unsorted_collection_item_bkg)
        } else {
            path = coverList[model.id]?.absolutePath
            holder.overlay?.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.sorted_collection_item_bkg)
        }

        if (!path.isNullOrEmpty() && File(path).exists()) {
            Glide.with(holder.itemView).load(File(path)).into(holder.image!!)
        } else {
            holder.image?.setImageBitmap(null)
        }
    }

    /**
     * Use the latest screenshot from [CollectionModel.UNCATEGORIZED] and [CollectionModel.CATEGORY_NONE]
     * as the cover image
     */
    private fun getCoverPathForUnsortedCollection(): String {
        val dummy = ScreenshotModel("", 0, "")
        val categoryNone = coverList[CollectionModel.CATEGORY_NONE]?: dummy
        val uncategorized = coverList[CollectionModel.UNCATEGORIZED]?: dummy

        return when {
            categoryNone.lastModified > uncategorized.lastModified -> categoryNone.absolutePath
            categoryNone.lastModified < uncategorized.lastModified -> uncategorized.absolutePath
            else -> ""
        }
    }


    class StaticHolder(itemView: View): RecyclerView.ViewHolder(itemView)

    class SectionNameHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        var title: TextView? = null
    }

    class CollectionHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        var image: ImageView? = null
        var title: TextView? = null
        var overlay: View? = null
    }

    class SpanSizeLookup(private val columnCount: Int) : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            return when (position) {
                POS_COLLECTION_LIST_TITLE -> columnCount
                POS_QUICK_ACCESS_TITLE -> columnCount
                POS_QUICK_ACCESS_LIST -> columnCount
                else -> 1
            }
        }
    }

    class ItemDecoration(columnCount: Int, space: Int, top: Int)
        : GridItemDecoration(columnCount, space, top, space, space, space) {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view) - PREFIX_ITEM_COUNT
            if (position < 0) {
                return
            }
            setSpaces(outRect, position)
        }
    }
}
