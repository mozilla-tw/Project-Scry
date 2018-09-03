/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.landingpage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.FileProvider
import android.support.v7.app.ActionBar
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.Navigation
import com.bumptech.glide.Glide
import kotlinx.coroutines.experimental.launch
import org.mozilla.scryer.*
import org.mozilla.scryer.Observer
import org.mozilla.scryer.capture.SortingPanelActivity
import org.mozilla.scryer.detailpage.DetailPageActivity
import org.mozilla.scryer.extension.getValidPosition
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.ui.CollectionNameDialog
import org.mozilla.scryer.ui.ScryerToast
import org.mozilla.scryer.viewmodel.ScreenshotViewModel
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class CollectionFragment : Fragment() {
    companion object {
        const val ARG_COLLECTION_ID = "collection_id"
        const val ARG_COLLECTION_NAME = "collection_name"

        private const val SPAN_COUNT = 3
    }

    private lateinit var screenshotListView: RecyclerView
    private lateinit var subtitleView: TextView

    private val screenshotAdapter by lazy {
        ScreenshotAdapter(context)
    }

    private val collectionId: String? by lazy {
        arguments?.getString(ARG_COLLECTION_ID)
    }

    private val collectionName: String? by lazy {
        arguments?.getString(ARG_COLLECTION_NAME)
    }

    private var sortMenuItem: MenuItem? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = inflater.inflate(R.layout.fragment_collection, container, false)
        screenshotListView = layout.findViewById(R.id.screenshot_list)
        subtitleView = layout.findViewById(R.id.subtitle)
        return layout
    }

    override fun getView(): View {
        return super.getView()!!
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
        setupActionBar()
        initScreenshotList(view.context)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_collection, menu)
        sortMenuItem = if (collectionId == CollectionModel.CATEGORY_NONE) menu.findItem(R.id.action_sort) else null

        val renameItem = menu.findItem(R.id.action_collection_rename)
        if (collectionId == null || collectionId == CollectionModel.CATEGORY_NONE) {
            renameItem.isVisible = false
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> Navigation.findNavController(view).navigateUp()
            R.id.action_sort -> {
                collectionId?.takeIf {
                    screenshotAdapter.getScreenshotList().isNotEmpty()
                }?.let {
                    startSortingActivity(it)
                }
            }

            R.id.action_search -> {
                context?.let {
                    ScryerToast.makeText(it, "Not implement", Toast.LENGTH_SHORT).show()
                }
            }

            R.id.action_collection_rename -> {
                context?.let {
                    CollectionNameDialog.renameCollection(it, ScreenshotViewModel.get(this), collectionId)
                }
            }
            else -> return super.onOptionsItemSelected(item)
        }

        return super.onOptionsItemSelected(item)
    }

    private fun startSortingActivity(collectionId: String) {
        context?.let {
            startActivity(SortingPanelActivity.sortCollection(it, collectionId))
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(activity, view.findViewById(R.id.toolbar))
        getSupportActionBar(activity).apply {
            setDisplayHomeAsUpEnabled(true)
            updateActionBarTitle(this)
        }
    }

    private fun updateActionBarTitle(actionBar: ActionBar) {
        actionBar.title = collectionName?.let { it } ?: "All"
    }

    private fun initScreenshotList(context: Context) {
        val manager = GridLayoutManager(context, SPAN_COUNT, GridLayoutManager.VERTICAL, false)
        screenshotListView.layoutManager = manager
        screenshotListView.adapter = screenshotAdapter

        val itemSpace = context.resources.getDimensionPixelSize(R.dimen.collection_item_space)

        screenshotListView.addItemDecoration(InnerItemDecoration(SPAN_COUNT, itemSpace))

        val viewModel = ScreenshotViewModel.get(this)
        val liveData = collectionId?.let {
            val idList = if (it == CollectionModel.CATEGORY_NONE) {
                listOf(CollectionModel.UNCATEGORIZED, CollectionModel.CATEGORY_NONE)
            } else {
                listOf(it)
            }
            viewModel.getScreenshots(idList)

        } ?: viewModel.getScreenshots()

        liveData.observe(this, Observer { screenshots ->
            sortMenuItem?.isVisible = screenshots.isNotEmpty()

            screenshots.sortedByDescending { it.lastModified }.let { sorted ->
                screenshotAdapter.setScreenshotList(sorted)
                screenshotAdapter.notifyDataSetChanged()
                val shotCount: Int = sorted.size
                subtitleView.text = getString(R.string.collection_separator_shots, shotCount)
            }
        })

        viewModel.getCollections().observe(this, Observer { collections ->
            collections.find { it.id == collectionId }?.let {
                getSupportActionBar(activity).apply {
                    setDisplayHomeAsUpEnabled(true)
                    this.title = it.name
                }
            }
        })
    }
}

const val CONTEXT_MENU_ID_MOVE_TO = 0
const val CONTEXT_MENU_ID_INFO = 1
const val CONTEXT_MENU_ID_SHARE = 2
const val CONTEXT_MENU_ID_DELETE = 3

open class ScreenshotAdapter(val context: Context?) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), OnContextMenuActionListener {
    private var screenshotList: List<ScreenshotModel> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_screenshot, parent, false)

        val holder = ScreenshotItemHolder(view, this)
        holder.title = view.findViewById(R.id.title)
        holder.image = view.findViewById(R.id.image_view)
        holder.itemView.setOnClickListener { _ ->
            holder.getValidPosition{ position: Int ->
                DetailPageActivity.showDetailPage(parent.context, screenshotList[position].absolutePath, holder.image)
            }
        }
        return holder
    }

    override fun getItemCount(): Int {
        return screenshotList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as? ScreenshotItemHolder)?.apply {
            title?.text = screenshotList[position].collectionId
            image?.let {
                Glide.with(holder.itemView.context)
                        .load(File(screenshotList[position].absolutePath))
                        .into(it)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? ScreenshotItemHolder)?.apply {
            image?.let {
                Glide.with(holder.itemView.context)
                        .clear(it)
            }
        }
    }

    override fun onContextMenuAction(item: MenuItem?, itemPosition: Int) {
        val screenshotModel = getItem(itemPosition)
        when (item?.itemId) {
            CONTEXT_MENU_ID_MOVE_TO -> context?.let { it.startActivity(SortingPanelActivity.sortOldScreenshot(it, screenshotModel.id)) }
            CONTEXT_MENU_ID_INFO -> context?.let { showScreenshotInfoDialog(it, screenshotModel) }
            CONTEXT_MENU_ID_SHARE -> context?.let { showShareScreenshotDialog(it, screenshotModel) }
            CONTEXT_MENU_ID_DELETE -> context?.let { showDeleteScreenshotDialog(it, screenshotModel) }
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
}

class ScreenshotItemHolder(itemView: View, private val onContextMenuActionListener: OnContextMenuActionListener) : RecyclerView.ViewHolder(itemView), View.OnCreateContextMenuListener, MenuItem.OnMenuItemClickListener {
    var title: TextView? = null
    var image: ImageView? = null

    init {
        itemView.setOnCreateContextMenuListener(this)
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        menu?.setHeaderTitle(v?.context?.getString(R.string.menu_title_action))

        menu?.add(0, CONTEXT_MENU_ID_MOVE_TO, 0, v?.context?.getString(R.string.menu_shot_action_move))?.setOnMenuItemClickListener(this)
        menu?.add(0, CONTEXT_MENU_ID_INFO, 0, v?.context?.getString(R.string.info_info))?.setOnMenuItemClickListener(this)
        menu?.add(0, CONTEXT_MENU_ID_SHARE, 0, v?.context?.getString(R.string.menu_action_share))?.setOnMenuItemClickListener(this)
        menu?.add(0, CONTEXT_MENU_ID_DELETE, 0, v?.context?.getString(R.string.action_delete))?.setOnMenuItemClickListener(this)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        onContextMenuActionListener.onContextMenuAction(item, adapterPosition)
        return false
    }
}

class InnerItemDecoration(private val span: Int, private val innerSpace: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildViewHolder(view).adapterPosition
        if (position < 0) {
            return
        }

        val spaceUnit = innerSpace / 3

        when {
            position % span == 0 -> {
                outRect.left = 0
                outRect.right = spaceUnit * 2
            }

            position % span == 2 -> {
                outRect.left = spaceUnit * 2
                outRect.right = 0
            }

            else -> {
                outRect.left = spaceUnit
                outRect.right = spaceUnit
            }
        }
        outRect.bottom = innerSpace
    }
}

interface OnContextMenuActionListener {
    fun onContextMenuAction(item: MenuItem?, itemPosition: Int)
}

fun showScreenshotInfoDialog(context: Context, screenshotModel: ScreenshotModel) {
    val message = StringBuilder()
    message.apply {
        append(context.getString(R.string.dialogue_list_name)).append("\n")
        append(getFileNameText(screenshotModel.absolutePath)).append("\n").append("\n")
        append(context.getString(R.string.dialogue_shotinfo_list_size)).append("\n")
        append(getFileSizeText(File(screenshotModel.absolutePath).length())).append("\n").append("\n")
        append(context.getString(R.string.dialogue_list_edit)).append("\n")
        append(getFileDateText(File(screenshotModel.absolutePath).lastModified()))
    }

    AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.info_info))
            .setMessage(message)
            .setPositiveButton(context.getString(android.R.string.ok)) { dialog: DialogInterface?, _: Int -> dialog?.dismiss() }
            .show()
}

fun getFileNameText(fullPath: String): String {
    val lastSeparatorIndex = fullPath.lastIndexOf(File.separatorChar)
    return if (lastSeparatorIndex != -1) {
        fullPath.substring(lastSeparatorIndex + 1)
    } else {
        fullPath
    }
}

fun getFileSizeText(size: Long): String {
    val df = DecimalFormat("0.00")
    val sizeKb = 1024.0f
    val sizeMo = sizeKb * sizeKb
    val sizeGo = sizeMo * sizeKb
    val sizeTerra = sizeGo * sizeKb

    return when {
        size < sizeMo -> df.format(size / sizeKb) + " KB"
        size < sizeGo -> df.format(size / sizeMo) + " MB"
        size < sizeTerra -> df.format(size / sizeGo) + " GB"
        else -> ""
    }
}

fun getFileDateText(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp

    return dateFormat.format(cal.time)
}

fun showDeleteScreenshotDialog(context: Context, screenshotModel: ScreenshotModel) {
    AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.dialogue_deleteshot_title_delete))
            .setMessage(context.getString(R.string.dialogue_delete_content_cantundo))
            .setNegativeButton(context.getString(android.R.string.cancel)) { dialog: DialogInterface?, _: Int -> dialog?.dismiss() }
            .setPositiveButton(context.getString(R.string.action_delete)) { dialog: DialogInterface?, _: Int ->
                launch {
                    ScryerApplication.getScreenshotRepository().deleteScreenshot(screenshotModel)
                    File(screenshotModel.absolutePath).delete()
                }
                dialog?.dismiss()
            }
            .show()
}

fun showShareScreenshotDialog(context: Context, screenshotModel: ScreenshotModel) {
    launch {
        val authorities = BuildConfig.APPLICATION_ID + ".provider.fileprovider"
        val file = File(screenshotModel.absolutePath)
        val fileUri: Uri?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            fileUri = FileProvider.getUriForFile(context, authorities, file)
        } else {
            fileUri = Uri.fromFile(file)
        }
        val share = Intent(Intent.ACTION_SEND)
        share.putExtra(Intent.EXTRA_STREAM, fileUri)
        share.type = "image/*"
        share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(share, null))
        } catch (e: ActivityNotFoundException) {
        }
    }
}
