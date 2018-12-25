/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.detailpage.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.view_detail_page_menu.view.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.cancelAndJoin
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import mozilla.components.browser.search.SearchEngine
import mozilla.components.browser.search.SearchEngineManager
import mozilla.components.browser.search.provider.AssetsSearchEngineProvider
import mozilla.components.browser.search.provider.localization.LocaleSearchLocalizationProvider
import org.mozilla.scryer.R

class TextSelectionMenuView : FrameLayout {
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context) : super(context)

    private var selectedText: String? = null
    private val searchEngineManager = SearchEngineManager(listOf(
            AssetsSearchEngineProvider(LocaleSearchLocalizationProvider())
    ))
    private lateinit var searchEngine: SearchEngine

    init {
        View.inflate(context, R.layout.view_detail_page_menu, this)

        listOf(R.id.action_search_root, R.id.action_copy, R.id.action_share).forEach { id ->
            findViewById<View>(id).apply {
                setOnLongClickListener(null)
                isLongClickable = false
                setOnClickListener {
                    dispatchClick(it.id)
                }
            }
        }
    }

    private var showJob: Job? = null

    fun show(selectedText: String) {
        launch {
            if (showJob?.isActive == true) {
                showJob?.cancelAndJoin()
            }

            showJob = launch {
                if (!::searchEngine.isInitialized) {
                    searchEngine = searchEngineManager.getDefaultSearchEngine(context)
                }
                withContext(UI) {
                    action_search_root.action_search_icon.setImageBitmap(searchEngine.icon)
                    action_search_root.action_search_icon.visibility = View.VISIBLE
                    this@TextSelectionMenuView.selectedText = selectedText
                    this@TextSelectionMenuView.visibility = View.VISIBLE
                }
            }
        }
    }

    fun hide() {
        launch {
            if (showJob?.isActive == true) {
                showJob?.cancelAndJoin()
                showJob = null
            }
            withContext(UI) {
                this@TextSelectionMenuView.selectedText = null
                this@TextSelectionMenuView.visibility = View.GONE
            }
        }
    }

    private fun dispatchClick(viewId: Int) {
        val selectedText = this.selectedText ?: return
        when (viewId) {
            R.id.action_search_root -> searchText(selectedText)
            R.id.action_copy -> copyText(selectedText)
            R.id.action_share -> shareText(selectedText)
        }
    }

    private fun searchText(text: String) {
        val uri = searchEngine.buildSearchUrl(text)
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            context.startActivity(this)
        }
    }

    private fun copyText(text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.primaryClip = ClipData.newPlainText("selected text", text)
    }

    private fun shareText(text: String) {
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            context.startActivity(this)
        }
    }
}
