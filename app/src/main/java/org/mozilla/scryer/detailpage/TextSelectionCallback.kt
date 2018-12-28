/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.detailpage

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.TextView
import kotlinx.coroutines.experimental.launch
import mozilla.components.browser.search.SearchEngine
import mozilla.components.browser.search.SearchEngineManager
import mozilla.components.browser.search.provider.AssetsSearchEngineProvider
import mozilla.components.browser.search.provider.localization.LocaleSearchLocalizationProvider
import org.mozilla.scryer.R
import org.mozilla.scryer.telemetry.TelemetryWrapper

class TextSelectionCallback(private val view: TextView) : android.view.ActionMode.Callback {
    private val searchEngineManager = SearchEngineManager(listOf(
            AssetsSearchEngineProvider(LocaleSearchLocalizationProvider())
    ))
    private lateinit var searchEngine: SearchEngine

    override fun onCreateActionMode(mode: android.view.ActionMode, menu: Menu): Boolean {
        TelemetryWrapper.promptExtractedTextMenu()
        return true
    }

    override fun onPrepareActionMode(mode: android.view.ActionMode, menu: Menu): Boolean {
        menu.clear()
        MenuInflater(view.context).inflate(R.menu.menu_detailpage_text_selection, menu)
        return true
    }

    override fun onActionItemClicked(mode: android.view.ActionMode, item: MenuItem): Boolean {
        val selectedText = view.text.substring(
                view.selectionStart,
                view.selectionEnd)
        when (item.itemId) {
            R.id.action_search -> searchText(selectedText)
            R.id.action_copy -> copyText(selectedText)
            R.id.action_share -> shareText(selectedText)
        }
        return false
    }

    override fun onDestroyActionMode(mode: android.view.ActionMode) {}

    private fun searchText(text: String) {
        launch {
            // TODO: Refine coroutine usage
            if (!::searchEngine.isInitialized) {
                searchEngine = searchEngineManager.getDefaultSearchEngine(view.context)
            }

            val uri = searchEngine.buildSearchUrl(text)
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                view.context.startActivity(this)
            }

            TelemetryWrapper.searchFromExtractedText()
        }
    }

    private fun copyText(text: String) {
        val manager = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.primaryClip = ClipData.newPlainText("selected text", text)

        TelemetryWrapper.copyExtractedText()
    }

    private fun shareText(text: String) {
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            view.context.startActivity(this)
        }

        TelemetryWrapper.shareExtractedText()
    }
}
