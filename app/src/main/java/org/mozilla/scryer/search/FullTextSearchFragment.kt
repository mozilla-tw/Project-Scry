package org.mozilla.scryer.search

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_full_text_search.*
import org.mozilla.scryer.R
import org.mozilla.scryer.ScryerApplication
import org.mozilla.scryer.collectionview.*
import org.mozilla.scryer.detailpage.DetailPageActivity
import org.mozilla.scryer.extension.getNavController
import org.mozilla.scryer.getSupportActionBar
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.LoadingViewModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.persistence.SuggestCollectionHelper
import org.mozilla.scryer.scan.ContentScanner
import org.mozilla.scryer.setSupportActionBar
import org.mozilla.scryer.telemetry.TelemetryWrapper
import org.mozilla.scryer.ui.InnerSpaceDecoration
import org.mozilla.scryer.util.hideKeyboard
import org.mozilla.scryer.util.showKeyboard
import org.mozilla.scryer.viewmodel.ScreenshotViewModel

class FullTextSearchFragment : androidx.fragment.app.Fragment() {

    companion object {
        private const val SPAN_COUNT = 3
    }

    private lateinit var screenshotAdapter: SearchAdapter
    private lateinit var liveData: LiveData<List<ScreenshotModel>>
    private lateinit var collectionList: List<CollectionModel>
    private lateinit var viewModel: ScreenshotViewModel

    private var actionModeMenu: Menu? = null
    private var isIndexing: Boolean = false
    private var enterTimeMillis: Long = 0

    private val selectActionModeCallback: ActionMode.Callback = object : ActionMode.Callback {
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val activity = activity ?: run {
                mode.finish()
                return false
            }

            when (item.itemId) {
                R.id.action_move -> {
                    val dialog = SortingPanelDialog(activity, selector.selected.toList())
                    dialog.setOnDismissListener {
                        mode.finish()
                    }
                    dialog.show()
                    TelemetryWrapper.moveScreenshot(TelemetryWrapper.ExtraValue.SEARCH, selector.selected.size)
                }

                R.id.action_delete -> {
                    showDeleteScreenshotDialog(activity, selector.selected.toList(),
                            object : OnDeleteScreenshotListener {
                                override fun onDeleteScreenshot() {
                                    mode.finish()
                                }
                            })
                    TelemetryWrapper.deleteScreenshot(TelemetryWrapper.ExtraValue.SEARCH, selector.selected.size)
                }

                R.id.action_share -> {
                    showShareScreenshotDialog(activity, selector.selected.toList())
                    TelemetryWrapper.shareScreenshot(TelemetryWrapper.ExtraValue.SEARCH, selector.selected.size)
                }
            }

            return true
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val activity = activity ?: return false
            activity.menuInflater.inflate(R.menu.menu_collection_view_select_action_mode, menu)
            actionModeMenu = menu

            (0 until menu.size()).map {
                menu.getItem(it)
            }.forEach { item ->
                item.icon = DrawableCompat.wrap(item.icon).mutate().apply {
                    DrawableCompat.setTint(this, Color.WHITE)
                }
                if (selector.selected.isEmpty()) {
                    item.isVisible = false
                }
            }

            activity.window?.let {
                it.statusBarColor = ContextCompat.getColor(activity, R.color.primaryTeal)
            }

            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            screenshotAdapter.exitSelectionMode()
            val activity = activity ?: return

            activity.findViewById<View>(R.id.action_mode_bar).visibility = View.INVISIBLE
            activity.window?.let {
                it.statusBarColor = ContextCompat.getColor(activity, R.color.statusBarColor)
            }
        }
    }

    private var selector = object : ListSelector<ScreenshotModel>() {
        private var actionMode: ActionMode? = null

        override fun onSelectChanged() {
            if (selected.isEmpty()) {
                screenshotAdapter.exitSelectionMode()
                return
            }

            actionMode?.title = if (selected.size == screenshotAdapter.itemCount) {
                getString(R.string.collection_header_select_all)
            } else {
                "${selected.size}"
            }

            selectAllCheckbox.isChecked = screenshotAdapter.screenshotList.all {
                isSelected(it)
            }
            selectAllCheckbox.invalidate()

            actionModeMenu?.let { menu ->
                (0 until menu.size()).map {
                    menu.getItem(it)
                }.forEach { item ->
                    if (selected.isNotEmpty()) {
                        item.isVisible = true
                    }
                }
            }
        }

        override fun onEnterSelectMode() {
            val activity = (activity as? AppCompatActivity) ?: return
            actionMode = activity.startSupportActionMode(selectActionModeCallback)
            selectAllCheckbox.visibility = View.VISIBLE
            actionMode?.title = getString(R.string.collection_header_select_none)
            selectAllCheckbox.isChecked = false

            TelemetryWrapper.longPressOnScreenshot(TelemetryWrapper.ExtraValue.SEARCH)
        }

        override fun onExitSelectMode() {
            actionMode?.finish()
            selectAllCheckbox.visibility = View.GONE
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_full_text_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                liveData.removeObservers(this@FullTextSearchFragment)
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                screenshotAdapter.showLoadingView(LoadingViewModel(getText(R.string.search_transition_searching)))
                liveData = viewModel.searchScreenshots(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {
                liveData.observe(this@FullTextSearchFragment, Observer { screenshots ->
                    subtitleLayout.visibility = if (screenshots.isEmpty()) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
                    emptyView.visibility = if (screenshots.isEmpty() && s?.isNotEmpty() == true && !isIndexing) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }

                    if (s?.isEmpty() == true || !isIndexing) {
                        screenshotAdapter.showLoadingView(null)
                    }

                    subtitleTextView.text = getString(R.string.search_separator_results, screenshots.size)

                    screenshots.sortedByDescending { it.lastModified }.let { sorted ->
                        screenshotAdapter.screenshotList = sorted
                        screenshotAdapter.notifyDataSetChanged()
                    }
                })

                clear.visibility = if (searchEditText.text?.isNotEmpty() == true) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }
            }
        })
        searchEditText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            // Avoid showing keyboard again when returning to the previous page by back key.
            if (hasFocus) {
                showKeyboard(searchEditText)
            } else {
                hideKeyboard(searchEditText)
            }
        }
        searchEditText.requestFocus()

        clear.setOnClickListener { searchEditText.setText("") }

        selectAllCheckbox.setOnClickListener { _ ->
            val isChecked = selectAllCheckbox.isChecked
            selectAllCheckbox.invalidate()
            screenshotAdapter.screenshotList.forEach {
                if (isChecked != selector.isSelected(it)) {
                    selector.toggleSelection(it)
                }
            }
            screenshotAdapter.notifyDataSetChanged()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val activity = activity ?: return

        screenshotAdapter = SearchAdapter(context, selector) { item, view, position ->
            val context = context ?: return@SearchAdapter
            DetailPageActivity.showDetailPage(context, item, view, searchKeyword = searchEditText.text.toString())

            TelemetryWrapper.clickSearchResult(
                    SuggestCollectionHelper.getSuggestCollectionNameForTelemetry(context,
                            collectionList.find { it.id == item.collectionId }?.name),
                    screenshotAdapter.itemCount,
                    position,
                    searchEditText.text?.length ?: 0,
                    System.currentTimeMillis() - enterTimeMillis)
        }
        enterTimeMillis = System.currentTimeMillis()

        if (ScryerApplication.getContentScanner().isScanning()) {
            ScryerApplication.getContentScanner().getProgressState().observe(
                    this@FullTextSearchFragment,
                    org.mozilla.scryer.Observer {
                        if (it is ContentScanner.ProgressState.Unavailable) {
                            // TODO: ML model is unavailable
                        } else if (it is ContentScanner.ProgressState.Progress) {
                            if (it.current != it.total) {
                                onIndexProgress(it.current, it.total)
                            } else {
                                onIndexEnd()
                            }
                        }
                    })
        }

        setHasOptionsMenu(true)
        setupActionBar()
        initScreenshotList(activity)
    }

    private fun onIndexProgress(current: Int, total: Int) {
        searchEditText.text?.let {
            if (it.isNotEmpty()) {
                screenshotAdapter.showLoadingView(LoadingViewModel(
                        getString(R.string.search_transition_progress, total - current),
                        getString(R.string.search_transition_content_searchable)))

                emptyView.visibility = View.GONE
            }
        }

        isIndexing = true
    }

    private fun onIndexEnd() {
        screenshotAdapter.showLoadingView(null)
        if (screenshotAdapter.screenshotList.isEmpty()
                && searchEditText.text?.isNotEmpty() == true) {
            emptyView.visibility = View.VISIBLE
        }
        isIndexing = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                hideKeyboard(searchEditText)
                getNavController()?.navigateUp()
            }

            else -> return super.onOptionsItemSelected(item)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupActionBar() {
        view?.let {
            setSupportActionBar(activity, it.findViewById(R.id.toolbar))
        }
        getSupportActionBar(activity).apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
    }

    private fun initScreenshotList(context: Context) {
        val manager = GridLayoutManager(context, SPAN_COUNT,
                RecyclerView.VERTICAL, false)
        manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (screenshotAdapter.getItemViewType(position)) {
                    SearchAdapter.VIEW_TYPE_ITEM -> 1
                    SearchAdapter.VIEW_TYPE_LOADING -> SPAN_COUNT
                    else -> -1
                }
            }
        }
        screenshotListView.itemAnimator = null
        screenshotListView.layoutManager = manager
        screenshotListView.adapter = screenshotAdapter

        val itemSpace = context.resources.getDimensionPixelSize(R.dimen.collection_item_space)
        screenshotListView.addItemDecoration(InnerSpaceDecoration(itemSpace) {
            SPAN_COUNT
        })

        viewModel = ScreenshotViewModel.get(this)
        liveData = viewModel.searchScreenshots("")

        viewModel.getCollections().observe(this.viewLifecycleOwner, Observer { collections ->
            collections?.asSequence()?.filter {
                !SuggestCollectionHelper.isSuggestCollection(it)
            }?.toList()?.let {
                collectionList = it
            }
        })
    }
}
