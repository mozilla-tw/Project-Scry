/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.collectionview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.FileProvider
import android.support.v7.app.ActionBar
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.TextView
import androidx.navigation.Navigation
import kotlinx.android.synthetic.main.dialog_collection_info.view.*
import kotlinx.android.synthetic.main.dialog_screenshot_info.view.*
import kotlinx.android.synthetic.main.fragment_collection.*
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.mozilla.scryer.*
import org.mozilla.scryer.Observer
import org.mozilla.scryer.detailpage.DetailPageActivity
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.sortingpanel.SortingPanelActivity
import org.mozilla.scryer.telemetry.TelemetryWrapper
import org.mozilla.scryer.ui.CollectionNameDialog
import org.mozilla.scryer.ui.ConfirmationDialog
import org.mozilla.scryer.ui.InnerSpaceDecoration
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

    private lateinit var screenshotAdapter: ScreenshotAdapter

    private val selectActionModeCallback: ActionMode.Callback = object : ActionMode.Callback {
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val activity = activity ?: run {
                mode.finish()
                return false
            }

            when (item.itemId) {
                R.id.action_move -> {
                    val intent = SortingPanelActivity.sortScreenshots(activity, selector.selected)
                    startActivity(intent)
                    mode.finish()
                }

                R.id.action_delete -> {
                    showDeleteScreenshotDialog(activity, selector.selected.toList(), object : OnDeleteScreenshotListener {
                        override fun onDeleteScreenshot() {
                            mode.finish()
                        }
                    })
                }

                R.id.action_share -> {
                    showShareScreenshotDialog(activity, selector.selected.toList())
                }
            }

            return true
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val activity = activity ?: return false
            activity.menuInflater.inflate(R.menu.menu_collection_view_select_action_mode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            screenshotAdapter.exitSelectionMode()
        }
    }

    private var selector = object : ListSelector<ScreenshotModel>() {
        private var actionMode: ActionMode? = null

        override fun onSelectChanged() {
            if (selected.isEmpty()) {
                screenshotAdapter.exitSelectionMode()
                return
            }
            actionMode?.title = "${selected.size} selected (TBD)"
        }

        override fun onEnterSelectMode() {
            val activity = (activity as? AppCompatActivity) ?: return
            actionMode = activity.startSupportActionMode(selectActionModeCallback)
        }

        override fun onExitSelectMode() {
            actionMode?.finish()
        }
    }
    private var screenshotList = listOf<ScreenshotModel>()

    private val collectionId: String? by lazy {
        arguments?.getString(ARG_COLLECTION_ID)
    }

    private val collectionName: String? by lazy {
        arguments?.getString(ARG_COLLECTION_NAME)
    }

    private var sortMenuItem: MenuItem? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
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
        screenshotAdapter = ScreenshotAdapter(context, selector) { item, view ->
            val context = context ?: return@ScreenshotAdapter
            DetailPageActivity.showDetailPage(context, item, view, collectionId)

            TelemetryWrapper.clickCollectionItem()
        }

        setHasOptionsMenu(true)
        setupActionBar()
        initScreenshotList(view.context)

        TelemetryWrapper.showCollectionPage()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_collection, menu)
        sortMenuItem = if (collectionId == CollectionModel.CATEGORY_NONE) {
            menu.findItem(R.id.action_sort).apply { updateSortMenuItem(this) }
        } else {
            null
        }

        val renameItem = menu.findItem(R.id.action_collection_rename)
        if (collectionId == null || collectionId == CollectionModel.CATEGORY_NONE) {
            renameItem.isVisible = false
        }

        val infoItem = menu.findItem(R.id.action_collection_info)
        if (collectionId == null) {
            infoItem.isVisible = false
        }

        val deleteItem = menu.findItem(R.id.action_collection_delete)
        if (collectionId == null || collectionId == CollectionModel.CATEGORY_NONE) {
            deleteItem.isVisible = false
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Navigation.findNavController(view).navigateUp()
            }

            R.id.action_sort -> {
                collectionId?.takeIf {
                    screenshotAdapter.getScreenshotList().isNotEmpty()
                }?.let {
                    startSortingActivity(it)
                    TelemetryWrapper.clickSortingInCollectionPage()
                }
            }

            R.id.action_search -> {
                context?.let {
                    Navigation.findNavController(view).navigate(R.id.action_navigate_to_search, Bundle())
                }
            }

            R.id.action_collection_rename -> {
                context?.let {
                    CollectionNameDialog.renameCollection(it, ScreenshotViewModel.get(this), collectionId)
                }
            }

            R.id.action_collection_info -> {
                context?.let {
                    showCollectionInfo(it, ScreenshotViewModel.get(this), collectionId)
                }
            }

            R.id.action_collection_delete -> {
                context?.let {
                    showDeleteCollectionDialog(it, ScreenshotViewModel.get(this), collectionId,
                            object : OnDeleteCollectionListener {
                                override fun onDeleteCollection() {
                                    Navigation.findNavController(view).navigateUp()
                                }
                            })
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
        actionBar.title = collectionName?.let { it } ?: getString(R.string.collection_header_viewall_all)
    }

    private fun updateSortMenuItem(item: MenuItem?) {
        item?.isVisible = screenshotList.isNotEmpty()
    }

    private fun initScreenshotList(context: Context) {
        val manager = GridLayoutManager(context, SPAN_COUNT, GridLayoutManager.VERTICAL, false)
        screenshotListView.itemAnimator = null
        screenshotListView.layoutManager = manager
        screenshotListView.adapter = screenshotAdapter

        val itemSpace = context.resources.getDimensionPixelSize(R.dimen.collection_item_space)

        screenshotListView.addItemDecoration(InnerSpaceDecoration(itemSpace) {
            SPAN_COUNT
        })

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
            screenshotList = screenshots
            updateSortMenuItem(sortMenuItem)

            if (screenshots.isNotEmpty()) {
                subtitleView.visibility = View.VISIBLE
                subtitleView.text = getString(R.string.collection_separator_shots, screenshots.size)
                empty_view.visibility = View.GONE
            } else {
                subtitleView.visibility = View.INVISIBLE
                empty_view.visibility = View.VISIBLE
            }

            screenshots.sortedByDescending { it.lastModified }.let { sorted ->
                screenshotAdapter.setScreenshotList(sorted)
                screenshotAdapter.notifyDataSetChanged()
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

interface OnContextMenuActionListener {
    fun onContextMenuAction(item: MenuItem?, itemPosition: Int)
}

interface OnDeleteScreenshotListener {
    fun onDeleteScreenshot()
}

interface OnDeleteCollectionListener {
    fun onDeleteCollection()
}

fun showScreenshotInfoDialog(context: Context, screenshotModel: ScreenshotModel) {
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_screenshot_info, null as ViewGroup?)
    dialogView.screenshot_info_name_content.text = getFileNameText(screenshotModel.absolutePath)
    dialogView.screenshot_info_file_size_amount.text = getFileSizeText(File(screenshotModel.absolutePath).length())
    dialogView.screenshot_info_last_edit_time.text = getFileDateText(File(screenshotModel.absolutePath).lastModified())

    AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.info_info))
            .setView(dialogView)
            .setPositiveButton(context.getString(android.R.string.ok)) {
                dialog: DialogInterface?, _: Int -> dialog?.dismiss()
            }
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

fun showDeleteScreenshotDialog(
        context: Context,
        screenshotModel: ScreenshotModel,
        listener: OnDeleteScreenshotListener? = null
) {
    showDeleteScreenshotDialog(context, listOf(screenshotModel), listener)
}

fun showDeleteScreenshotDialog(
        context: Context,
        screenshotModels: List<ScreenshotModel>,
        listener: OnDeleteScreenshotListener? = null
) {
    val dialog = ConfirmationDialog.build(context,
            context.getString(R.string.dialogue_deleteshot_title_delete),
            context.getString(R.string.action_delete),
            DialogInterface.OnClickListener { dialog, _ ->
                launch {
                    screenshotModels.forEach {
                        ScryerApplication.getScreenshotRepository().deleteScreenshot(it)
                        File(it.absolutePath).delete()
                    }
                }
                dialog?.dismiss()
                listener?.onDeleteScreenshot()
            },
            context.getString(android.R.string.cancel),
            DialogInterface.OnClickListener { dialog, _ ->
                dialog.dismiss()
            })
    dialog.viewHolder.message?.text = context.getString(R.string.dialogue_deleteshot_content_delete)
    dialog.viewHolder.subMessage?.visibility = View.VISIBLE

    launch(UI) {
        val size = withContext(DefaultDispatcher) {
            var totalSize = 0L
            screenshotModels.forEach {
                totalSize += File(it.absolutePath).length()
            }
            totalSize
        }

        dialog.viewHolder.subMessage?.text = getFileSizeText(size)
    }
    dialog.asAlertDialog().show()
}

fun showShareScreenshotDialog(context: Context, screenshotModel: ScreenshotModel) {
    showShareScreenshotDialog(context, listOf(screenshotModel))
}

fun showShareScreenshotDialog(context: Context, screenshotModels: List<ScreenshotModel>) {
    if (screenshotModels.isEmpty()) {
        return
    }

    launch {
        val authorities = BuildConfig.APPLICATION_ID + ".provider.fileprovider"
        val share = Intent()
        if (screenshotModels.size == 1) {
            val file = File(screenshotModels[0].absolutePath)
            val fileUri = FileProvider.getUriForFile(context, authorities, file)
            share.action = Intent.ACTION_SEND
            share.putExtra(Intent.EXTRA_STREAM, fileUri)
        } else {
            val uriList = ArrayList<Uri>()
            screenshotModels.forEach {
                val file = File(it.absolutePath)
                uriList.add(FileProvider.getUriForFile(context, authorities, file))
            }
            share.action = Intent.ACTION_SEND_MULTIPLE
            share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
        }
        share.type = "image/*"
        share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(share, null))
        } catch (e: ActivityNotFoundException) {
        }
    }
}

fun showCollectionInfo(context: Context, viewModel: ScreenshotViewModel, collectionId: String?) {
    launch(UI) {
        collectionId ?: return@launch

        withContext(DefaultDispatcher) {
            viewModel.getCollection(collectionId)

        }?.let {
            val screenshots = withContext(DefaultDispatcher) {
                viewModel.getScreenshotList(listOf(it.id))
            }
            val totalFileSize = withContext(DefaultDispatcher) {
                var totalFileSize = 0L
                for (screenshot in screenshots) {
                    val file = File(screenshot.absolutePath)
                    totalFileSize += file.length()
                }
                totalFileSize
            }
            showCollectionInfoDialog(context, it, screenshots, totalFileSize)
        }
    }
}

private fun showCollectionInfoDialog(
        context: Context,
        collection: CollectionModel,
        screenshots: List<ScreenshotModel>,
        totalFileSize: Long
) {
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_collection_info, null as ViewGroup?)
    dialogView.collection_info_name_content.text = getFileNameText(collection.name)
    dialogView.collection_info_total_screenshots_count.text = screenshots.size.toString()
    dialogView.collection_info_storage_used_amount.text = getFileSizeText(totalFileSize)

    AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.dialogue_collecitioninfo_title_info))
            .setView(dialogView)
            .setPositiveButton(context.getString(android.R.string.ok)) {
                dialog: DialogInterface?, _: Int -> dialog?.dismiss()
            }
            .show()
}

fun showDeleteCollectionDialog(
        context: Context,
        viewModel: ScreenshotViewModel,
        collectionId: String?,
        listener: OnDeleteCollectionListener?
) {
    launch(UI) {
        collectionId ?: return@launch

        withContext(DefaultDispatcher) {
            viewModel.getCollection(collectionId)

        }?.let { collection ->
            val screenshots = withContext(DefaultDispatcher) {
                viewModel.getScreenshotList(listOf(collection.id))
            }

            val totalFileSize = withContext(DefaultDispatcher) {
                var totalFileSize = 0L
                for (screenshot in screenshots) {
                    val file = File(screenshot.absolutePath)
                    totalFileSize += file.length()
                }
                totalFileSize
            }

            // fool the lint
            val screenshotCount: Int = screenshots.size

            val dialog = ConfirmationDialog.build(context,
                    context.getString(R.string.dialogue_deletecollection_title_delete),
                    context.getString(R.string.action_delete),
                    DialogInterface.OnClickListener { dialog, _ ->
                        launch {
                            viewModel.deleteCollection(collection)
                            screenshots.forEach { screenshot ->
                                File(screenshot.absolutePath).delete()
                                viewModel.deleteScreenshot(screenshot)
                            }
                        }
                        dialog?.dismiss()
                        listener?.onDeleteCollection()
                    },
                    context.getString(android.R.string.cancel),
                    DialogInterface.OnClickListener { dialog, _ ->
                        dialog.dismiss()
                    })
            dialog.viewHolder.message?.text = context.getString(R.string.dialogue_delete_content_cantundo)
            dialog.viewHolder.subMessage?.apply {
                visibility = View.VISIBLE
                text = context.getString(R.string.dialogue_deletecollection_content_shots, screenshotCount)
            }
            dialog.viewHolder.subMessage2?.apply {
                visibility = View.VISIBLE
                text = getFileSizeText(totalFileSize)
            }
            dialog.asAlertDialog().show()
        }
    }
}

