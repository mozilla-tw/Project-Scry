/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.detailpage

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.DrawableCompat
import org.mozilla.scryer.R
import org.mozilla.scryer.telemetry.TelemetryWrapper
import org.mozilla.scryer.ui.ScryerToast

class TextSelectionCallback(
        private val view: TextView,
        private val searchEngineDelegate: SearchEngineDelegate
) : android.view.ActionMode.Callback {

    override fun onCreateActionMode(mode: android.view.ActionMode, menu: Menu): Boolean {
        TelemetryWrapper.promptExtractedTextMenu()
        return true
    }

    override fun onPrepareActionMode(mode: android.view.ActionMode, menu: Menu): Boolean {
        menu.clear()
        MenuInflater(view.context).inflate(R.menu.menu_detailpage_text_selection, menu)

        (0 until menu.size()).map {
            menu.getItem(it)
        }.forEach { item ->
            item.icon = DrawableCompat.wrap(item.icon).mutate().apply {
                DrawableCompat.setTint(this, Color.WHITE)
            }
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        mode.title = ""

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

    override fun onDestroyActionMode(mode: android.view.ActionMode) {
    }

    private fun searchText(text: String) {
        val uri = searchEngineDelegate.buildSearchUrl(text)
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            view.context.startActivity(this)
        }

        TelemetryWrapper.searchFromExtractedText()
    }

    private fun copyText(text: String) {
        val manager = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.primaryClip = ClipData.newPlainText("selected text", text)
        ScryerToast.makeText(view.context, view.context.getString(R.string.snackbar_copied),
                Toast.LENGTH_SHORT).show()

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

    interface SearchEngineDelegate {
        val name: String
        fun buildSearchUrl(text: String): String
    }
}
