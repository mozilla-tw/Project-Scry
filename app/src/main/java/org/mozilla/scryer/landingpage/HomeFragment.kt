/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.landingpage

import android.Manifest
import android.app.Activity
import android.app.SearchManager
import android.content.*
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.Navigation
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.fragment_home.view.*
import kotlinx.android.synthetic.main.view_quick_access.view.*
import kotlinx.coroutines.experimental.*
import mozilla.components.support.base.log.Log
import org.mozilla.scryer.*
import org.mozilla.scryer.capture.ScreenCaptureManager
import org.mozilla.scryer.collectionview.ScreenshotItemHolder
import org.mozilla.scryer.detailpage.DetailPageActivity
import org.mozilla.scryer.extension.navigateSafely
import org.mozilla.scryer.filemonitor.ScreenshotFetcher
import org.mozilla.scryer.permission.PermissionFlow
import org.mozilla.scryer.permission.PermissionHelper
import org.mozilla.scryer.permission.PermissionViewModel
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.persistence.SuggestCollectionHelper
import org.mozilla.scryer.preference.PreferenceWrapper
import org.mozilla.scryer.promote.PromoteRatingHelper
import org.mozilla.scryer.promote.PromoteShareHelper
import org.mozilla.scryer.setting.SettingsActivity
import org.mozilla.scryer.sortingpanel.SortingPanelActivity
import org.mozilla.scryer.telemetry.TelemetryWrapper
import org.mozilla.scryer.ui.BottomDialogFactory
import org.mozilla.scryer.util.launchIO
import org.mozilla.scryer.viewmodel.ScreenshotViewModel
import java.io.File
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext

class HomeFragment : Fragment(), PermissionFlow.ViewDelegate, CoroutineScope {

    companion object {
        private const val LOG_TAG = "HomeFragment"

        const val COLLECTION_COLUMN_COUNT = 2
        const val QUICK_ACCESS_ITEM_COUNT = 5

        private const val PREF_SHOW_NEW_SCREENSHOT_DIALOG = "show_new_screenshot_dialog"
        private const val PREF_SHOW_ENABLE_SERVICE_DIALOG = "show_enable_service_dialog"
    }

    private val fragmentJob = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + fragmentJob

    private var quickAccessContainer: ViewGroup? = null
    private var quickAccessAdapter: QuickAccessAdapter? = null

    private var mainAdapter: MainAdapter? = null

    private lateinit var permissionFlow: PermissionFlow
    private var storagePermissionView: View? = null
    private var welcomeView: View? = null

    private var permissionDialog: BottomSheetDialog? = null

    private val viewModel: ScreenshotViewModel by lazy {
        ScreenshotViewModel.get(this)
    }

    private val pref: PreferenceWrapper? by lazy {
        context?.let {
            PreferenceWrapper(it)
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        mainAdapter = MainAdapter(this)
        quickAccessAdapter = QuickAccessAdapter(context)

        val layout = inflater.inflate(R.layout.fragment_home, container, false)
        quickAccessContainer = View.inflate(
                inflater.context,
                R.layout.view_quick_access,
                null
        ) as ViewGroup
        return layout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initQuickAccessList(view.context)
        initCollectionList(view.context)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        initActionBar()
        initPermissionFlow()
    }

    override fun onResume() {
        super.onResume()
        permissionFlow.start()
    }

    override fun onStart() {
        super.onStart()
        promptPromotionIfNeeded()
    }

    override fun onDestroyView() {
        mainAdapter = null
        quickAccessAdapter = null
        quickAccessContainer = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        fragmentJob.cancel()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        activity?.let {
            inflater.inflate(R.menu.menu_main, menu)

            menu.findItem(R.id.action_settings).setOnMenuItemClickListener { _ ->
                startActivity(Intent(it, SettingsActivity::class.java))
                TelemetryWrapper.enterSettings()
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

    override fun showWelcomePage(action: Runnable, withStoragePermission: Boolean) {
        welcomeView = welcomeView?.let {
            it.visibility = View.VISIBLE
            it

        }?: run {
            val stub = view!!.findViewById<ViewStub>(R.id.welcome_stub)
            stub.inflate()
        }

        welcomeView?.apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.onboarding_storage_title_welcome,
                    getString(R.string.app_full_name))

            findViewById<TextView>(R.id.description).text = getString(if (withStoragePermission) {
                    R.string.onboarding_storage_content_permission

                } else {
                    R.string.onboarding_storage_content_autogrant

                }, getString(R.string.app_full_name))

            showStoragePermissionView(this, action)
        }

        TelemetryWrapper.visitWelcomePage()
    }

    override fun showStoragePermissionView(isRational: Boolean, action: Runnable) {
        storagePermissionView = (storagePermissionView?.let {
            it.visibility = View.VISIBLE
            it

        }?: run {
            val stub = view!!.findViewById<ViewStub>(R.id.storage_permission_stub)
            stub.inflate()

        })?.apply {
            val appName = getString(R.string.app_full_name)
            val description = findViewById<TextView>(R.id.description)
            val actionButton = findViewById<TextView>(R.id.action_button)
            if (isRational) {
                description.text = getString(R.string.onboarding_error_content_permission, appName)
                actionButton.setText(R.string.onboarding_error_action_allow)
            } else {
                description.text = getString(R.string.onboarding_rareerror_content_permission, appName)
                actionButton.setText(R.string.onboarding_rareerror_action_goto)
            }
            showStoragePermissionView(this, action)
        }

        TelemetryWrapper.visitPermissionErrorPage()
    }

    private fun showStoragePermissionView(view: View, action: Runnable) {
        val activity = activity?: return

        val model = ViewModelProviders.of(activity).get(PermissionViewModel::class.java)
        model.permissionRequest.observe(this, EventObserver {
            permissionFlow.onPermissionResult(MainActivity.REQUEST_CODE_WRITE_EXTERNAL_PERMISSION, it)
        })

        view.findViewById<View>(R.id.action_button)?.setOnClickListener {
            action.run()
            TelemetryWrapper.grantStoragePermission(pref?.getAndIncreaseGrantStoragePermissionCount() ?: 1)
        }
    }

    private val dialogQueue = DialogQueue()

    @RequiresApi(Build.VERSION_CODES.M)
    override fun showOverlayPermissionView(action: Runnable, negativeAction: Runnable) {
        val context = context?: return

        val dialog = BottomDialogFactory.create(context, R.layout.dialog_bottom)
        val appNameGo = getString(R.string.app_name_go)
        dialog.findViewById<View>(R.id.image)?.visibility = View.VISIBLE
        dialog.findViewById<TextView>(R.id.title)?.text = getString(R.string.onboarding_fab_title_fab, appNameGo)
        dialog.findViewById<TextView>(R.id.subtitle)?.text = getString(
                R.string.onboarding_fab_content_permission,
                appNameGo
        )
        dialog.findViewById<View>(R.id.dont_ask_again_checkbox)?.visibility = View.GONE

        dialog.findViewById<TextView>(R.id.positive_button)?.apply {
            setOnClickListener {
                action.run()
                dialog.dismiss()
                TelemetryWrapper.grantOverlayPermission()
            }
        }

        dialog.findViewById<TextView>(R.id.negative_button)?.apply {
            setText(R.string.action_later)
            setOnClickListener {
                negativeAction.run()
                dialog.dismiss()
                TelemetryWrapper.notGrantOverlayPermission()
            }
        }

        dialog.setOnCancelListener {
            negativeAction.run()
        }

        permissionDialog = dialog
        dialogQueue.show(dialog, null)

        TelemetryWrapper.promptOverlayPermission()
    }

    override fun showCapturePermissionView(action: Runnable, negativeAction: Runnable) {
        val context = context?: return

        val dialog = BottomDialogFactory.create(context, R.layout.dialog_bottom)
        dialog.findViewById<View>(R.id.image)?.visibility = View.VISIBLE
        dialog.findViewById<TextView>(R.id.title)?.visibility = View.GONE
        dialog.findViewById<TextView>(R.id.subtitle)?.text = getString(
                R.string.onboarding_autogrant_overlay_title,
                getString(R.string.app_name_go)
        )
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

                val activity = activity ?: return
                if (activity.isFinishing || activity.isDestroyed) {
                    return
                }

                if (dialog.isShowing) {
                    dialog.dismiss()
                }
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
        welcomeView?.visibility = View.GONE
        storagePermissionView?.visibility = View.GONE

        launch(Dispatchers.Main) {
            val newScreenshots = syncAndGetNewScreenshotsFromExternal()
            yield() // Skip the following UI work if the fragmentJob is already cancelled

            mainAdapter?.notifyDataSetChanged()

            val showNewScreenshotDialog = newScreenshots.isNotEmpty()
                    && isDialogAllowed(PREF_SHOW_NEW_SCREENSHOT_DIALOG)
                    && !isFirstTimeLaunched()
            val showEnableServiceDialog = shouldPromptEnableService()
                    && isDialogAllowed(PREF_SHOW_ENABLE_SERVICE_DIALOG)

            if (showNewScreenshotDialog) {
                showNewScreenshotsDialog(newScreenshots)

            } else if (showEnableServiceDialog) {
                showEnableServiceDialog()
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
        TelemetryWrapper.visitHomePage()
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
            if (this::permissionFlow.isInitialized && permissionFlow.isFinished()) {
                Navigation.findNavController(view!!).navigateSafely(R.id.MainFragment,
                        R.id.action_navigate_to_full_text_search,
                        Bundle())
                TelemetryWrapper.startSearch()
            }
        }
    }

    private fun initQuickAccessList(context: Context) {
        quickAccessAdapter?.clickListener = object : QuickAccessAdapter.ItemClickListener {
            override fun onItemClick(screenshotModel: ScreenshotModel, holder: ScreenshotItemHolder) {
                DetailPageActivity.showDetailPage(context, screenshotModel, holder.image)
                TelemetryWrapper.clickOnQuickAccess(holder.adapterPosition)
            }

            override fun onMoreClick(holder: RecyclerView.ViewHolder) {
                Navigation.findNavController(holder.itemView).navigate(
                        R.id.action_navigate_to_collection,
                        Bundle()
                )
                TelemetryWrapper.clickMoreOnQuickAccess()
            }
        }

        quickAccessContainer?.list_view?.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = quickAccessAdapter

            val spaceOuter = resources.getDimensionPixelSize(R.dimen.home_horizontal_padding)
            val spaceInner = resources.getDimensionPixelSize(R.dimen.quick_access_item_space)
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                        outRect: Rect,
                        view: View,
                        parent: RecyclerView,
                        state: RecyclerView.State
                ) {
                    val position = parent.getChildAdapterPosition(view)
                    if (position == 0) {
                        outRect.left = spaceOuter
                    }
                    if (position == quickAccessAdapter?.let { it.itemCount - 1 } ?: 0) {
                        outRect.right = spaceOuter
                    } else {
                        outRect.right = spaceInner
                    }
                }
            })
        }

        viewModel.getScreenshots().observe(this.viewLifecycleOwner, Observer { screenshots ->
            screenshots?.let { newList ->
                val finalList = newList.sortedByDescending { it.lastModified }
                        .subList(0, Math.min(newList.size, QUICK_ACCESS_ITEM_COUNT + 1))
                updateQuickAccessListView(finalList)
            }
        })
    }

    private fun initCollectionList(context: Context) {
        val manager = GridLayoutManager(context, COLLECTION_COLUMN_COUNT,
                RecyclerView.VERTICAL, false)
        manager.spanSizeLookup = MainAdapter.SpanSizeLookup(COLLECTION_COLUMN_COUNT)
        root_view.main_list.layoutManager = manager

        quickAccessContainer?.let {
            mainAdapter?.quickAccessContainer = it
        }
        root_view.main_list.adapter = mainAdapter

        val spaceOuter = resources.getDimensionPixelSize(R.dimen.home_horizontal_padding)
        root_view.main_list.addItemDecoration(MainAdapter.ItemDecoration(context,
                COLLECTION_COLUMN_COUNT,
                spaceOuter,
                0))

        viewModel.getCollections().observe(this.viewLifecycleOwner, Observer { collections ->
            collections?.asSequence()?.filter {
                !SuggestCollectionHelper.isSuggestCollection(it)

            }?.sortedBy {
                it.createdDate

            }?.toList()?.let {
                updateCollectionListView(it)
            }
        })

        viewModel.getCollectionCovers().observe(this.viewLifecycleOwner, Observer { coverMap ->
            coverMap?.let { newData ->
                mainAdapter?.coverList = newData
                mainAdapter?.notifyDataSetChanged()
            }
        })
    }

    private fun updateQuickAccessListView(screenshots: List<ScreenshotModel>) {
        quickAccessContainer?.empty_view_group?.visibility = if (screenshots.isEmpty()) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }

        quickAccessAdapter?.list = screenshots
        quickAccessAdapter?.notifyDataSetChanged()
    }

    private fun updateCollectionListView(collections: List<CollectionModel>) {
        mainAdapter?.collectionList = collections
        mainAdapter?.notifyDataSetChanged()
    }

    private suspend fun syncAndGetNewScreenshotsFromExternal(): List<ScreenshotModel> {
        return withContext(Dispatchers.IO + NonCancellable) {
            context?.let {
                val externalList = ScreenshotFetcher().fetchScreenshots(it)
                val dbList = viewModel.getScreenshotList()
                mergeExternalToDatabase(externalList, dbList).filter { screenshot ->
                    screenshot.collectionId == CollectionModel.UNCATEGORIZED
                }
            } ?: emptyList()
        }
    }

    private fun showNewScreenshotsDialog(newScreenshots: List<ScreenshotModel>) {
        val context = context?: return
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
                dialog.cancel()
            }
        }

        dialog.setOnCancelListener {
            launchIO {
                viewModel.batchMove(newScreenshots, CollectionModel.CATEGORY_NONE)
            }
        }

        dialogQueue.tryShow(dialog, DialogInterface.OnDismissListener {
            if (checkbox?.isChecked == true) {
                setDoNotShowDialogAgain(PREF_SHOW_NEW_SCREENSHOT_DIALOG)
            }
        })
    }

    private fun showEnableServiceDialog() {
        val context = context?: return
        val dialog = BottomDialogFactory.create(context, R.layout.dialog_bottom)

        dialog.findViewById<TextView>(R.id.title)?.text = getString(R.string.sheet_enable_title_enable,
                getString(R.string.app_full_name))
        dialog.findViewById<TextView>(R.id.subtitle)?.text = getString(R.string.sheet_enable_content_enable)

        dialog.findViewById<TextView>(R.id.positive_button)?.apply {
            setText(R.string.sheet_enable_action_enable)
            setOnClickListener {
                ScryerApplication.getSettingsRepository().serviceEnabled = true
                val intent = Intent(activity, ScryerService::class.java)
                intent.action = ScryerService.ACTION_ENABLE_SERVICE
                activity?.startService(intent)
                dialog.dismiss()
            }
        }

        val checkbox = dialog.findViewById<AppCompatCheckBox>(R.id.dont_ask_again_checkbox)
        dialog.findViewById<TextView>(R.id.negative_button)?.apply {
            setText(R.string.sheet_action_no)
            setOnClickListener {
                dialog.cancel()
            }
        }

        val isShown = dialogQueue.tryShow(dialog, DialogInterface.OnDismissListener {
            if (checkbox?.isChecked == true) {
                setDoNotShowDialogAgain(PREF_SHOW_ENABLE_SERVICE_DIALOG)
            }
        })
        if (isShown) {
            pref?.setShouldPromptEnableService(false)
        }
    }

    private fun mergeExternalToDatabase(
            externalList: List<ScreenshotModel>,
            dbList: List<ScreenshotModel>
    ): List<ScreenshotModel> {
        // A lookup table consist of files recorded in the database, so we can quickly check whether each file
        // from external storage had already been recorded before
        val localModels = dbList.map { it.absolutePath to it }.toMap().toMutableMap()

        val results = mutableListOf<ScreenshotModel>()
        externalList.filterNot {
            // skip screenshots that were taken by our self
            File(it.absolutePath).parent.endsWith(File.separator + ScreenCaptureManager.SCREENSHOT_DIR)

        }.forEach { externalModel ->
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

                results.add(externalModel)
            }
        }

        for (entry in localModels) {
            val model = entry.value
            val file = File(model.absolutePath)
            if (!file.exists()) {
                viewModel.deleteScreenshot(model)
            }
        }

        viewModel.addScreenshot(results)
        return results
    }

    private fun log(tag: String, msg: String) {
        Log.log(Log.Priority.DEBUG, tag, null, msg)
    }

    private fun setDoNotShowDialogAgain(prefKey: String) {
        context?.let {
            PreferenceManager.getDefaultSharedPreferences(it).edit()
                    .putBoolean(prefKey, false)
                    .apply()
        }
    }

    private fun isDialogAllowed(prefKey: String): Boolean {
        return context?.let {
            PreferenceManager.getDefaultSharedPreferences(it)
                    .getBoolean(prefKey, true)
        }?: false
    }

    private fun shouldPromptEnableService(): Boolean {
        return pref?.shouldPromptEnableService() ?: false
    }

    private fun isFirstTimeLaunched(): Boolean {
        // TODO: Better way?
        return (activity as? MainActivity)?.isFirstTimeLaunched ?: false
    }

    private fun promptPromotionIfNeeded() {
        val context = context ?: return
        val shareReason = PromoteShareHelper.getShareReason(context)
        if (shareReason >= 0) {
            promptShareDialog(context, shareReason)

        } else if (PromoteRatingHelper.shouldPromote(context)) {
            promptRatingDialog(context)
        }
    }

    private fun promptRatingDialog(context: Context) {
        val from = TelemetryWrapper.ExtraValue.FROM_PROMPT

        val dialog = PromoteRatingHelper.getRatingDialog(context, {
            TelemetryWrapper.clickFeedback(TelemetryWrapper.Value.POSITIVE, from)
        }, {
            TelemetryWrapper.clickFeedback(TelemetryWrapper.Value.NEGATIVE, from)
        })

        if (dialogQueue.tryShow(dialog, null)) {
            PromoteRatingHelper.onRatingPromoted(context)
            TelemetryWrapper.promptFeedbackDialog(from)
        }
    }

    private fun promptShareDialog(context: Context, reason: Int) {
        val reasonForTelemetry = when (reason) {
            PromoteShareHelper.REASON_SHOT -> TelemetryWrapper.ExtraValue.TRIGGER_CAPTURE
            PromoteShareHelper.REASON_SORT -> TelemetryWrapper.ExtraValue.TRIGGER_SORT
            PromoteShareHelper.REASON_OCR -> TelemetryWrapper.ExtraValue.TRIGGER_OCR
            else -> return
        }

        PromoteShareHelper.getShareDialog(context, reason, {
            TelemetryWrapper.clickShareApp(TelemetryWrapper.ExtraValue.FROM_PROMPT,
                    reasonForTelemetry)
        })?.let {
            if (dialogQueue.tryShow(it, null)) {
                PromoteShareHelper.onSharingPromoted(context)
                TelemetryWrapper.promptShareDialog(TelemetryWrapper.ExtraValue.FROM_PROMPT,
                        reasonForTelemetry)
            }
        }
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

        fun tryShow(dialog: AppCompatDialog, dismissListener: DialogInterface.OnDismissListener?): Boolean {
            current?: run {
                show(dialog, dismissListener)
                return true
            }
            return false
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
