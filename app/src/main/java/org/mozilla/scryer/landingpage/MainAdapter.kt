/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.landingpage

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.mozilla.scryer.R
import org.mozilla.scryer.collectionview.CollectionFragment
import org.mozilla.scryer.collectionview.OnContextMenuActionListener
import org.mozilla.scryer.collectionview.showCollectionInfo
import org.mozilla.scryer.collectionview.showDeleteCollectionDialog
import org.mozilla.scryer.extension.getValidPosition
import org.mozilla.scryer.extension.navigateSafely
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.telemetry.TelemetryWrapper
import org.mozilla.scryer.ui.CollectionNameDialog
import org.mozilla.scryer.ui.GridItemDecoration
import org.mozilla.scryer.viewmodel.ScreenshotViewModel
import java.io.File

class MainAdapter(private val fragment: Fragment?): RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        OnContextMenuActionListener {
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

        const val CONTEXT_MENU_ID_RENAME = 0
        const val CONTEXT_MENU_ID_INFO = 1
        const val CONTEXT_MENU_ID_DELETE = 2
    }

    lateinit var quickAccessContainer: ViewGroup

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

    override fun onContextMenuAction(item: MenuItem?, itemPosition: Int) {
        val collectionModel: CollectionModel?
        val collectionItemPosition = itemPosition - PREFIX_ITEM_COUNT
        if (collectionItemPosition >= 0 && collectionItemPosition < collectionList.size) {
            collectionModel = collectionList[collectionItemPosition]
        } else {
            return
        }

        when (item?.itemId) {
            CONTEXT_MENU_ID_RENAME -> fragment?.context?.let {
                CollectionNameDialog.renameCollection(it, ScreenshotViewModel.get(fragment), collectionModel.id)
            }
            CONTEXT_MENU_ID_INFO -> fragment?.context?.let {
                showCollectionInfo(it, ScreenshotViewModel.get(fragment), collectionModel.id)
            }
            CONTEXT_MENU_ID_DELETE -> fragment?.context?.let {
                showDeleteCollectionDialog(it, ScreenshotViewModel.get(fragment), collectionModel.id, null)
            }
        }
    }

    private fun createCollectionHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val itemView = wrapInItemView(parent, R.layout.item_collection)

        val itemHolder = CollectionHolder(itemView, this)
        itemHolder.title = itemView.findViewById(R.id.title)
        itemHolder.image = itemView.findViewById(R.id.image)
        itemHolder.overlay = itemView.findViewById(R.id.overlay)

        itemView.setOnClickListener {
            itemHolder.getValidPosition { position: Int ->
                val itemIndex = position - PREFIX_ITEM_COUNT
                val bundle = Bundle().apply {
                    val model = collectionList[itemIndex]
                    putString(CollectionFragment.ARG_COLLECTION_ID, model.id)
                    putString(CollectionFragment.ARG_COLLECTION_NAME, model.name)
                }
                val navController = Navigation.findNavController(parent)
                navController.navigateSafely(R.id.MainFragment,
                        R.id.action_navigate_to_collection,
                        bundle)
                TelemetryWrapper.clickOnCollection()
            }
        }
        return itemHolder
    }

    private fun createNewCollectionHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val itemView = wrapInItemView(parent, R.layout.item_new_collection)
        itemView.elevation = 0f

        val holder = StaticHolder(itemView)
        itemView.setOnClickListener {
            holder.getValidPosition {
                val fragment = fragment ?: return@getValidPosition
                // since suggest collection is not visible on home view, it is confusing to show
                // name-conflict error msg when user input a name identical to suggest collections,
                // so here we just make that suggest collection become an user-created collection
                // since we don't want to show name conflict error msg on the dialog, set excludeSuggestion to true
                CollectionNameDialog.createNewCollection(parent.context, ScreenshotViewModel.get(fragment),
                        true)
                TelemetryWrapper.createCollectionFromHome()
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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_home_section_title, parent,
                false)

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
            }

            POS_COLLECTION_LIST_TITLE -> {
                holder.title?.text = holder.title?.context?.getString(R.string.home_separator_collection)
            }
        }
    }

    private fun bindCollectionHolder(holder: CollectionHolder, position: Int) {
        val model = collectionList[position - PREFIX_ITEM_COUNT]
        holder.title?.text = model.name

        val path: String?
        if (model.id == CollectionModel.CATEGORY_NONE) {
            path = getCoverPathForUnsortedCollection()
            holder.overlay?.background = ContextCompat.getDrawable(holder.itemView.context,
                    R.drawable.unsorted_collection_item_bkg)
        } else {
            path = coverList[model.id]?.absolutePath
            holder.overlay?.background = ContextCompat.getDrawable(holder.itemView.context,
                    R.drawable.sorted_collection_item_bkg)
        }

        if (!path.isNullOrEmpty() && File(path).exists()) {
            Glide.with(holder.itemView).load(File(path)).into(holder.image!!)
        } else {
            holder.image?.setImageDrawable(ContextCompat.getDrawable(holder.itemView.context,
                    R.drawable.image_emptyfolder))
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

    class CollectionHolder(itemView: View,
                           private val onContextMenuActionListener: OnContextMenuActionListener)
        : RecyclerView.ViewHolder(itemView), View.OnCreateContextMenuListener, MenuItem.OnMenuItemClickListener {
        var image: ImageView? = null
        var title: TextView? = null
        var overlay: View? = null

        init {
            itemView.setOnCreateContextMenuListener(this)
        }

        override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
            v?.context?.let {
                val isUnsortedItem = (adapterPosition - PREFIX_ITEM_COUNT) == 0
                if (!isUnsortedItem) {
                    menu?.add(0, CONTEXT_MENU_ID_RENAME, 0, it.getString(R.string.menu_action_rename))?.setOnMenuItemClickListener(this)
                }
                menu?.add(0, CONTEXT_MENU_ID_INFO, 0, it.getString(R.string.dialogue_collecitioninfo_title_info))?.setOnMenuItemClickListener(this)
                if (!isUnsortedItem) {
                    menu?.add(0, CONTEXT_MENU_ID_DELETE, 0, it.getString(R.string.action_delete))?.setOnMenuItemClickListener(this)
                }
            }

        }

        override fun onMenuItemClick(item: MenuItem?): Boolean {
            onContextMenuActionListener.onContextMenuAction(item, adapterPosition)
            return false
        }
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

    // TODO: Make event decoration configurable
    class ItemDecoration(context: Context, columnCount: Int, space: Int, top: Int)
        : GridItemDecoration(columnCount, space, top, space, space, space) {

//        private val shoppingDude = ContextCompat.getDrawable(context, R.drawable.ornaments)
//        private val shoppingDudePattern = ContextCompat.getDrawable(context,
//                R.drawable.santadeer)
//        private val shoppingBag = ContextCompat.getDrawable(context, R.drawable.gifts)
//
//        private val blackFridayLogo = ContextCompat.getDrawable(context, R.drawable.christmastree)

        private val quickAccessBkg = ColorDrawable(ContextCompat.getColor(context,
                R.color.quick_access_background))
//        private val collectionBkg = ColorDrawable(ContextCompat.getColor(context,
//                R.color.home_background))

        override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view) - PREFIX_ITEM_COUNT
            if (position < 0) {
                return
            }
            setSpaces(outRect, position)
        }

        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val childCount = parent.childCount
            for (child in (0 until childCount).map { parent.getChildAt(it) }) {
                val pos = parent.getChildAdapterPosition(child)
                when (pos) {
                    POS_QUICK_ACCESS_TITLE -> {
                        drawQuickAccessTitle(c, child)
                    }

//                    POS_COLLECTION_LIST_TITLE -> {
//                        drawCollectionTitle(c, child)
//                        decorateCollectionTitle(c, child)
//                    }
                }
            }
            super.onDraw(c, parent, state)
        }

//        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
//            super.onDrawOver(c, parent, state)
//            val childCount = parent.childCount
//            for (child in (0 until childCount).map { parent.getChildAt(it) }) {
//                val pos = parent.getChildAdapterPosition(child)
//                val lastIndex = (parent.adapter?.itemCount ?: 0) - 1
//                if (lastIndex > POS_COLLECTION_LIST_TITLE && pos == lastIndex) {
//                    decorateLastItem(c, child)
//                } else if (pos == POS_QUICK_ACCESS_TITLE) {
//                    decorateQuickAccessTitle(c, child)
//                }
//            }
//        }

        private fun drawQuickAccessTitle(canvas: Canvas, view: View) {
            quickAccessBkg.setBounds(view.left, view.top, view.right, view.bottom)
            quickAccessBkg.draw(canvas)
        }

//        private fun decorateQuickAccessTitle(canvas: Canvas, view: View) {
//            val logo = blackFridayLogo ?: return
//
//            val verticalSpace = view.height
//
//            val logoWidth = logo.intrinsicWidth
//            val logoHeight = logo.intrinsicHeight
//
//            val targetHeight = (verticalSpace * 1f).toInt()
//            val targetWidth = (logoWidth * (targetHeight / logoHeight.toFloat())).toInt()
//
//            val vPadding = view.resources.getDimensionPixelSize(R.dimen.quick_access_item_margin_top)
//            val hPadding = view.paddingEnd + 48f.dpToPx(view.resources.displayMetrics)
//
//            val bottom = view.bottom + vPadding
//            val top = bottom - targetHeight
//            val right = view.right - hPadding
//            val left = right - targetWidth
//
//            logo.setBounds(left, top, right, bottom)
//            logo.draw(canvas)
//        }
//
//        private fun drawCollectionTitle(canvas: Canvas, view: View) {
//            collectionBkg.setBounds(view.left, view.top, view.right, view.bottom)
//            collectionBkg.draw(canvas)
//        }
//
//        private fun decorateCollectionTitle(canvas: Canvas, view: View) {
//            val dude = shoppingDude ?: return
//            val pattern = shoppingDudePattern ?: return
//
//            val verticalSpace = view.height
//
//            val logoWidth = dude.intrinsicWidth
//            val logoHeight = dude.intrinsicHeight
//
//            val targetHeight = (verticalSpace * 0.9f).toInt()
//
//            val ornamentHeight = (verticalSpace * 0.55f).toInt()
//            val ornamentWidth = (logoWidth * (ornamentHeight / logoHeight.toFloat())).toInt()
//
//            val hPadding = view.resources.getDimensionPixelSize(R.dimen.home_horizontal_padding)
//
//            var top = view.top
//            var bottom = top + ornamentHeight
//            var right = view.right - hPadding
//            var left = right - ornamentWidth
//
//            dude.setBounds(left, top, right, bottom)
//
//            bottom = view.bottom
//            top = bottom - targetHeight
//            right = dude.bounds.left - 4f.dpToPx(view.resources.displayMetrics)
//            left = right - (pattern.intrinsicWidth * ((bottom - top) / pattern.intrinsicHeight.toFloat())).toInt()
//            pattern.setBounds(left, top, right, bottom)
//
//            view.invalidate()
//            pattern.draw(canvas)
//            dude.draw(canvas)
//        }
//
//        private fun decorateLastItem(canvas: Canvas, view: View) {
//            val bag = shoppingBag ?: return
//
//            val bagWidth = (bag.intrinsicWidth * 0.5f).toInt()
//            val bagHeight = (bag.intrinsicHeight * 0.5f).toInt()
//
//            val bottom = view.bottom
//            val right = view.right
//            val left = right - bagWidth
//            val top = bottom - bagHeight
//            bag.setBounds(left, top, right, bottom)
//            bag.draw(canvas)
//        }
    }
}
