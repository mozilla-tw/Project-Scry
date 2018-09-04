/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.landingpage

import android.Manifest
import android.app.ActionBar
import android.app.Activity
import android.app.SearchManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.*
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.support.annotation.RequiresApi
import android.support.constraint.Group
import android.support.design.widget.BottomSheetDialog
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatDialog
import android.support.v7.preference.PreferenceManager
import android.support.v7.widget.*
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.Navigation
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.mozilla.scryer.*
import org.mozilla.scryer.capture.SortingPanelActivity
import org.mozilla.scryer.detailpage.DetailPageActivity
import org.mozilla.scryer.extension.dpToPx
import org.mozilla.scryer.filemonitor.ScreenshotFetcher
import org.mozilla.scryer.permission.PermissionFlow
import org.mozilla.scryer.permission.PermissionHelper
import org.mozilla.scryer.permission.PermissionViewModel
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.setting.SettingsActivity
import org.mozilla.scryer.ui.BottomDialogFactory
import org.mozilla.scryer.ui.GridItemDecoration
import org.mozilla.scryer.ui.ScryerToast
import org.mozilla.scryer.viewmodel.ScreenshotViewModel
import java.io.File
import java.util.*

class HomeFragment : Fragment(), PermissionFlow.ViewDelegate {

    companion object {
        private const val LOG_TAG = "HomeFragment"

        const val COLLECTION_COLUMN_COUNT = 2
        const val QUICK_ACCESS_ITEM_COUNT = 5

        private const val PREF_SHOW_NEW_SCREENSHOT_DIALOG = "show_new_screenshot_dialog"
    }

    private lateinit var quickAccessContainer: ViewGroup
    private lateinit var quickAccessEmptyView: Group
    private val quickAccessAdapter: QuickAccessAdapter by lazy {
        QuickAccessAdapter(context)
    }

    private lateinit var mainListView: RecyclerView
    private val mainAdapter: MainAdapter = MainAdapter(this)

    private lateinit var searchListView: RecyclerView
    private val searchListAdapter: SearchAdapter by lazy {
        SearchAdapter(context)
    }

    private lateinit var permissionFlow: PermissionFlow
    private var storagePermissionView: View? = null

    private var permissionDialog: BottomSheetDialog? = null

    private val viewModel: ScreenshotViewModel by lazy {
        ScreenshotViewModel.get(this)
    }

    private val searchObserver = Observer<List<ScreenshotModel>> { screenshots ->
        screenshots?.let { newData ->
            searchListAdapter.setScreenshotList(newData)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = inflater.inflate(R.layout.fragment_home, container, false)
        mainListView = layout.findViewById(R.id.main_list)
        quickAccessContainer = View.inflate(inflater.context, R.layout.view_quick_access, null) as ViewGroup
        quickAccessEmptyView = quickAccessContainer.findViewById(R.id.empty_view_group)
        searchListView = layout.findViewById(R.id.search_list)
        return layout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initQuickAccessList(view.context)
        initCollectionList(view.context)
        initSearchList(view.context)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        initActionBar()
        initPermissionFlow()

        view!!.findViewById<View>(R.id.root_view).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        permissionFlow.start()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        activity?.let {
            inflater.inflate(R.menu.menu_main, menu)

            menu.findItem(R.id.action_settings).setOnMenuItemClickListener { _ ->
                startActivity(Intent(it, SettingsActivity::class.java))
                true
            }

            menu.findItem(R.id.action_svg_viewer).apply {
                setOnMenuItemClickListener { _ ->
                    startActivity(Intent(it, SvgViewerActivity::class.java))
                    true
                }
                isVisible = BuildConfig.DEBUG
            }

            createOptionsMenuSearchView(it)
        }
    }

    override fun showWelcomePage(action: Runnable) {
        showStoragePermissionView("welcome!!", action)
    }

    override fun showStoragePermissionView(title: String, action: Runnable) {
        val activity = activity?: return

        val model = ViewModelProviders.of(activity).get(PermissionViewModel::class.java)
        model.permissionRequest.observe(this, EventObserver {
            permissionFlow.onPermissionResult(MainActivity.REQUEST_CODE_WRITE_EXTERNAL_PERMISSION, it)
        })

        storagePermissionView?.let {
            it.visibility = View.VISIBLE

        }?: apply {
            val stub = view!!.findViewById<ViewStub>(R.id.storage_permission_stub)
            val permissionView = stub.inflate()
            storagePermissionView = permissionView
        }

        storagePermissionView?.findViewById<TextView>(R.id.title)?.text = title
        storagePermissionView?.findViewById<View>(R.id.action_button)?.setOnClickListener {
            action.run()
        }
    }

    private val dialogQueue = DialogQueue()

    @RequiresApi(Build.VERSION_CODES.M)
    override fun showOverlayPermissionView(action: Runnable, negativeAction: Runnable) {
        val context = context?: return

        val dialog = BottomDialogFactory.create(context, R.layout.dialog_bottom)

        dialog.findViewById<TextView>(R.id.title)?.setText(R.string.onboarding_fab_title_fab)
        dialog.findViewById<TextView>(R.id.subtitle)?.setText(R.string.onboarding_fab_content_permission)
        dialog.findViewById<View>(R.id.dont_ask_again_checkbox)?.visibility = View.GONE

        dialog.findViewById<TextView>(R.id.positive_button)?.apply {
            setText(R.string.onboarding_fab_action_grant)
            setOnClickListener {
                action.run()
                dialog.dismiss()
            }
        }

        dialog.findViewById<TextView>(R.id.negative_button)?.apply {
            setText(R.string.onboarding_fab_action_later)
            setOnClickListener {
                negativeAction.run()
                dialog.dismiss()
            }
        }

        dialog.setOnCancelListener {
            negativeAction.run()
        }

        permissionDialog = dialog
        dialogQueue.show(dialog, null)
    }

    override fun showCapturePermissionView(action: Runnable, negativeAction: Runnable) {
        val context = context?: return

        val dialog = BottomDialogFactory.create(context, R.layout.dialog_bottom)

        dialog.findViewById<TextView>(R.id.title)?.setText(R.string.onboarding_autogrant_overlay_title)
        dialog.findViewById<View>(R.id.dont_ask_again_checkbox)?.visibility = View.GONE
        dialog.findViewById<View>(R.id.positive_button)?.setOnClickListener {
            action.run()
            dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.negative_button)?.visibility = View.GONE
        dialog.setOnCancelListener {
            negativeAction.run()
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(this)
                dialog.dismiss()
            }
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(receiver,
                IntentFilter(ScryerService.EVENT_TAKE_SCREENSHOT))

        permissionDialog = dialog
        dialogQueue.show(dialog, DialogInterface.OnDismissListener {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        })
    }

    override fun onStorageGranted() {
        log(LOG_TAG, "onStorageGranted")
        storagePermissionView?.visibility = View.GONE

        syncScreenshotsFromExternalStorage { newScreenshots ->
            if (newScreenshots.isNotEmpty()) {
                showNewScreenshotsDialog(newScreenshots)
            }
        }
    }

    override fun onOverlayGranted() {
        log(LOG_TAG, "onOverlayGranted")
        dismissPermissionDialog()
    }

    override fun onOverlayDenied() {
        log(LOG_TAG, "onOverlayDenied")
        ScryerApplication.getSettingsRepository().floatingEnable = false
    }

    override fun onPermissionFlowFinish() {
        log(LOG_TAG, "onPermissionFlowFinish")
        if (ScryerApplication.getSettingsRepository().serviceEnabled) {
            context?.startService(Intent(context, ScryerService::class.java))
        }
    }

    override fun requestStoragePermission() {
        activity?.let {
            ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    MainActivity.REQUEST_CODE_WRITE_EXTERNAL_PERMISSION)
        }
    }

    override fun requestOverlayPermission() {
        PermissionHelper.requestOverlayPermission(activity, MainActivity.REQUEST_CODE_OVERLAY_PERMISSION)
    }

    override fun launchSystemSettingPage() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", activity?.packageName, null)
        activity?.startActivity(intent)
    }

    private fun dismissPermissionDialog() = permissionDialog?.takeIf { it.isShowing }?.dismiss()

    private fun initActionBar() {
        setHasOptionsMenu(true)
        setSupportActionBar(activity, view!!.findViewById(R.id.toolbar))
        getSupportActionBar(activity).displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
        getSupportActionBar(activity).setCustomView(R.layout.view_home_toolbar)
    }

    private fun initPermissionFlow() {
        permissionFlow = PermissionFlow(PermissionFlow.createDefaultPermissionProvider(activity),
                PermissionFlow.createDefaultPageStateProvider(activity), this)
    }

    private fun createOptionsMenuSearchView(activity: Activity) {
        //val searchItem = menu.findItem(R.id.action_search)

        //val searchView = searchItem.actionView as SearchView
        val searchView = view!!.findViewById<SearchView>(R.id.search_view)//searchItem.actionView as SearchView
        searchView.setIconifiedByDefault(false)
        searchView.maxWidth = resources.displayMetrics.widthPixels
        searchView.findViewById<View>(R.id.search_plate)?.setBackgroundColor(Color.TRANSPARENT)
        searchView.isFocusableInTouchMode = false
        searchView.isClickable = false

        val searchManager = activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity.componentName))

        searchView.setOnSearchClickListener {
            Toast.makeText(context, "WIP!", Toast.LENGTH_SHORT).show()
        }

        searchView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewDetachedFromWindow(v: View?) {
//                searchListView.visibility = View.INVISIBLE
//                mainListView.visibility = View.VISIBLE
                viewModel.getScreenshots().removeObserver(searchObserver)
            }

            override fun onViewAttachedToWindow(v: View?) {
//                searchListView.visibility = View.VISIBLE
//                mainListView.visibility = View.INVISIBLE
                viewModel.getScreenshots().observe(this@HomeFragment, searchObserver)
            }
        })

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                //searchListAdapter.filter.filter(query)
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                //searchListAdapter.filter.filter(newText)
                return false
            }
        })

        view!!.findViewById<View>(R.id.intercept_view).setOnClickListener {
            if (permissionFlow.isFinished()) {
                ScryerToast.makeText(it.context, "Not implement", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initQuickAccessList(context: Context) {
        quickAccessAdapter.clickListener = object : QuickAccessAdapter.ItemClickListener {
            override fun onItemClick(screenshotModel: ScreenshotModel, holder: ScreenshotItemHolder) {
                DetailPageActivity.showDetailPage(context, screenshotModel.absolutePath, holder.image)
            }

            override fun onMoreClick(holder: RecyclerView.ViewHolder) {
                Navigation.findNavController(holder.itemView).navigate(R.id.action_navigate_to_collection, Bundle())
            }
        }

        with (quickAccessContainer.findViewById<RecyclerView>(R.id.list_view)) {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = quickAccessAdapter

            val spaceOuter = resources.getDimensionPixelSize(R.dimen.home_horizontal_padding)
            val spaceInner = resources.getDimensionPixelSize(R.dimen.quick_access_item_space)
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    val position = parent.getChildAdapterPosition(view)
                    if (position == 0) {
                        outRect.left = spaceOuter
                    }
                    if (position == quickAccessAdapter.itemCount - 1) {
                        outRect.right = spaceOuter
                    } else {
                        outRect.right = spaceInner
                    }
                }
            })
        }

        viewModel.getScreenshots().observe(this, Observer { screenshots ->
            screenshots?.let { newList ->
                val finalList = newList.sortedByDescending { it.lastModified }
                        .subList(0, Math.min(newList.size, QUICK_ACCESS_ITEM_COUNT + 1))
                updateQuickAccessListView(finalList)
            }
        })
    }

    private fun initCollectionList(context: Context) {
        val manager = GridLayoutManager(context, COLLECTION_COLUMN_COUNT, GridLayoutManager.VERTICAL, false)
        manager.spanSizeLookup = MainAdapter.SpanSizeLookup(COLLECTION_COLUMN_COUNT)
        mainListView.layoutManager = manager

        mainAdapter.quickAccessContainer = quickAccessContainer
        mainListView.adapter = mainAdapter

        val spaceOuter = resources.getDimensionPixelSize(R.dimen.home_horizontal_padding)
        val spaceTop = resources.getDimensionPixelSize(R.dimen.home_collection_padding_top)
        mainListView.addItemDecoration(MainAdapter.ItemDecoration(COLLECTION_COLUMN_COUNT, spaceOuter, spaceTop))

        viewModel.getCollections().observe(this, Observer { collections ->
            collections?.filter {
                !CollectionModel.isSuggestCollection(it)

            }?.sortedBy {
                it.date

            }?.let {
                updateCollectionListView(it)
            }
        })

        viewModel.getCollectionCovers().observe(this, Observer { coverMap ->
            coverMap?.let { newData ->
                mainAdapter.coverList = newData
                mainAdapter.notifyDataSetChanged()
            }
        })
    }

    private fun initSearchList(context: Context) {
        val manager = GridLayoutManager(context, COLLECTION_COLUMN_COUNT, GridLayoutManager.VERTICAL, false)
        searchListView.layoutManager = manager
        searchListView.adapter = searchListAdapter
        val space = 8f.dpToPx(context.resources.displayMetrics)
        searchListView.addItemDecoration(GridItemDecoration(COLLECTION_COLUMN_COUNT, space))
    }

    private fun updateQuickAccessListView(screenshots: List<ScreenshotModel>) {
        quickAccessEmptyView.visibility = if (screenshots.isEmpty()) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }

        quickAccessAdapter.list = screenshots
        quickAccessAdapter.notifyDataSetChanged()
    }

    private fun updateCollectionListView(collections: List<CollectionModel>) {
        mainAdapter.collectionList = collections
        mainAdapter.notifyDataSetChanged()
    }

    private fun syncScreenshotsFromExternalStorage(callback: (newScreenshots: List<ScreenshotModel>) -> Unit) {
        viewModel.getScreenshotList { list ->
            context?.let {
                ScreenshotFetcher().fetchScreenshots(it) { externalList ->
                    val resultList = mergeExternalToDatabase(externalList, list)
                    callback(resultList.filter { it.collectionId == CollectionModel.UNCATEGORIZED })
                }
            }
        }
    }

    private fun showNewScreenshotsDialog(newScreenshots: List<ScreenshotModel>) {
        val context = context?: return
        if (!isNewScreenshotDialogAllowed()) {
            return
        }

        val dialog = BottomDialogFactory.create(context, R.layout.dialog_bottom)

        dialog.findViewById<TextView>(R.id.title)?.setText(R.string.sheet_unsorted_title_unsorted)
        val subtitle = getString(R.string.sheet_unsorted_content_shots, newScreenshots.size)
        dialog.findViewById<TextView>(R.id.subtitle)?.text = subtitle

        dialog.findViewById<TextView>(R.id.positive_button)?.apply {
            setText(R.string.sheet_unsorted_action_sort)
            setOnClickListener {
                startActivity(SortingPanelActivity.sortCollection(context, CollectionModel.UNCATEGORIZED))
                dialog.dismiss()
            }
        }

        val checkbox = dialog.findViewById<AppCompatCheckBox>(R.id.dont_ask_again_checkbox)
        dialog.findViewById<TextView>(R.id.negative_button)?.apply {
            setText(R.string.sheet_action_no)
            setOnClickListener {
                if (checkbox?.isChecked == true) {
                    setDoNotShowNewScreenshotDialog()
                }
                dialog.cancel()
            }
        }

        dialog.setOnCancelListener {
            updateScreenshotCategory(newScreenshots, CollectionModel.CATEGORY_NONE)
        }

        dialogQueue.tryShow(dialog, null)
    }

    private fun updateScreenshotCategory(list: List<ScreenshotModel>, collectionId: String) {
        list.forEach {
            it.collectionId = collectionId
            viewModel.updateScreenshot(it)
        }
    }

    private fun mergeExternalToDatabase(externalList: List<ScreenshotModel>,
                                        dbList: List<ScreenshotModel>): List<ScreenshotModel> {
        // A lookup table consist of files recorded in the database, so we can quickly check whether each file
        // from external storage had already been recorded before
        val localModels = dbList.map { it.absolutePath to it }.toMap().toMutableMap()

        val results = mutableListOf<ScreenshotModel>()
        externalList.forEach { externalModel ->
            val localModel = localModels[externalModel.absolutePath]
            localModel?.let {
                // Already recorded before, sync id and collectionId from local record
                // TODO: Do we really need to save(rewrite) existed item to db again here(replace)?
                externalModel.id = localModel.id
                externalModel.collectionId = localModel.collectionId

                // Remove processed item from the lookup table
                localModels.remove(externalModel.absolutePath)

            }?: run {
                // No record found, make a new uncategorized item
                externalModel.id = UUID.randomUUID().toString()
                externalModel.collectionId = CollectionModel.UNCATEGORIZED
            }

            results.add(externalModel)
        }

        launch(UI) {
            withContext(DefaultDispatcher) {
                for (entry in localModels) {
                    val model = entry.value
                    val file = File(model.absolutePath)
                    if (!file.exists()) {
                        viewModel.deleteScreenshot(model)
                    }
                }
            }
            mainAdapter.notifyDataSetChanged()
        }

        launch {
            viewModel.addScreenshot(results)
        }
        return results
    }

    private fun log(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    private fun setDoNotShowNewScreenshotDialog() {
        context?.let {
            PreferenceManager.getDefaultSharedPreferences(it).edit()
                    .putBoolean(PREF_SHOW_NEW_SCREENSHOT_DIALOG, false)
                    .apply()
        }
    }

    private fun isNewScreenshotDialogAllowed(): Boolean {
        return context?.let {
            PreferenceManager.getDefaultSharedPreferences(it)
                    .getBoolean(PREF_SHOW_NEW_SCREENSHOT_DIALOG, true)
        }?: false
    }

    private class DialogQueue {
        private var current: AppCompatDialog? = null
        private val queue = LinkedList<AppCompatDialog>()
        private val listeners = HashMap<AppCompatDialog, DialogInterface.OnDismissListener?>()

        fun show(dialog: AppCompatDialog, dismissListener: DialogInterface.OnDismissListener?) {
            listeners[dialog] = dismissListener
            queue.offer(dialog)
            schedule()
        }

        fun tryShow(dialog: AppCompatDialog, dismissListener: DialogInterface.OnDismissListener?) {
            current?: run {
                show(dialog, dismissListener)
            }
        }

        private fun schedule() {
            current?: run {
                current = queue.poll()?.let { dialog ->
                    dialog.setOnDismissListener { _ ->
                        val listener = listeners[dialog]
                        listener?.let { targetInterface ->
                            targetInterface.onDismiss(dialog)
                            listeners.remove(dialog)
                        }
                        current = null
                        schedule()
                    }
                    dialog.show()

                    dialog
                }
            }
        }
    }
}
