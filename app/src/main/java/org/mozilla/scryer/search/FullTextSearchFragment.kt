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
import androidx.core.view.ViewCompat
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
import org.mozilla.scryer.persistence.LoadingViewModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.setSupportActionBar
import org.mozilla.scryer.ui.InnerSpaceDecoration
import org.mozilla.scryer.viewmodel.ScreenshotViewModel

class FullTextSearchFragment : androidx.fragment.app.Fragment() {

    companion object {
        private const val SPAN_COUNT = 3
    }

    private lateinit var screenshotAdapter: SearchAdapter
    private lateinit var liveData: LiveData<List<ScreenshotModel>>
    private lateinit var viewModel: ScreenshotViewModel

    private var actionModeMenu: Menu? = null

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
                }

                R.id.action_delete -> {
                    showDeleteScreenshotDialog(activity, selector.selected.toList(),
                            object : OnDeleteScreenshotListener {
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
                val queryText = s.toString().split(" ").joinToString(" AND ", "*", "*")
                liveData = viewModel.searchScreenshots(queryText)
            }

            override fun afterTextChanged(s: Editable?) {
                liveData.observe(this@FullTextSearchFragment, Observer { screenshots ->
                    if (screenshots.isNotEmpty()) {
                        subtitleLayout.visibility = View.VISIBLE
                        emptyView.visibility = View.GONE
                    } else {
                        subtitleLayout.visibility = View.GONE
                        emptyView.visibility = View.VISIBLE
                    }

                    subtitleTextView.text = getString(R.string.search_separator_results, screenshots.size)

                    screenshots.sortedByDescending { it.lastModified }.let { sorted ->
                        ScryerApplication.getContentScanner().getProgress().observe(this@FullTextSearchFragment, org.mozilla.scryer.Observer {
                            if (it.first != it.second) {
                                screenshotAdapter.showLoadingView(LoadingViewModel(getString(R.string.search_transition_progress, (it.second - it.first)),
                                        getString(R.string.search_transition_content_searchable)))
                            } else {
                                screenshotAdapter.showLoadingView(null)
                            }
                        })
                        screenshotAdapter.screenshotList = sorted
                        screenshotAdapter.notifyDataSetChanged()
                    }
                })
            }
        })

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

        screenshotAdapter = SearchAdapter(context, selector) { item, view ->
            val context = context ?: return@SearchAdapter
            DetailPageActivity.showDetailPage(context, item, view)
        }

        setHasOptionsMenu(true)
        setupActionBar()
        initScreenshotList(activity)
        setupWindowInsets()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
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

        liveData.observe(this, Observer { screenshots ->
            if (screenshots.isNotEmpty()) {
                subtitleLayout.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
            } else {
                subtitleLayout.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            }

            screenshots.sortedByDescending { it.lastModified }.let { sorted ->
                screenshotAdapter.screenshotList = sorted
                screenshotAdapter.notifyDataSetChanged()
            }
        })
    }

    private fun setupWindowInsets() {
        val rootView = view ?: return
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            toolbar_holder.setPadding(toolbar_holder.paddingLeft,
                    insets.systemWindowInsetTop,
                    toolbar_holder.paddingRight,
                    toolbar_holder.paddingBottom)
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight,
                    insets.systemWindowInsetBottom)
            insets
        }
    }
}