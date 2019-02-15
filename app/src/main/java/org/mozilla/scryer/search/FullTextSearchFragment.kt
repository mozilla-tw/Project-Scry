package org.mozilla.scryer.search

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ViewCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_full_text_search.*
import org.mozilla.scryer.R
import org.mozilla.scryer.ScryerApplication
import org.mozilla.scryer.collectionview.ScreenshotAdapter
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

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val layout = inflater.inflate(R.layout.fragment_full_text_search, container, false)

        val searchEditText = layout.findViewById<AppCompatEditText>(R.id.searchEditText)
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

        return layout
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val activity = activity ?: return

        screenshotAdapter = SearchAdapter(context) { item, view ->
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