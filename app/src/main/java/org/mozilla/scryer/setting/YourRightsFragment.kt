package org.mozilla.scryer.setting

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.appcompat.app.ActionBar
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.mozilla.scryer.R
import org.mozilla.scryer.getSupportActionBar

class YourRightsFragment : androidx.fragment.app.Fragment() {
    companion object {
        const val TAG: String = "YourRightsFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view: View = inflater.inflate(R.layout.fragment_your_rights, container, false)

        val content = StringBuilder()
        content.apply {
            append("<p>").append(getString(R.string.your_rights_content_1, getString(R.string.app_full_name))).append("</p>")
            append("<p>").append(getString(R.string.your_rights_content_2, getString(R.string.app_full_name))).append("</p>")
            append("<p>").append(getString(R.string.your_rights_content_3, getString(R.string.about_privacy_notice_url))).append("</p>")
            append("<p>").append(getString(R.string.your_rights_content_4, getString(R.string.app_full_name))).append("</p>")
        }

        val yourRightsTextView = view.findViewById<TextView>(R.id.your_rights_content)
        yourRightsTextView?.text = Html.fromHtml(content.toString())
        yourRightsTextView?.movementMethod = LinkMovementMethod.getInstance()

        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setupActionBar()
    }

    private fun setupActionBar() {
        getSupportActionBar(activity).apply {
            setDisplayHomeAsUpEnabled(true)
            updateActionBarTitle(this)
        }
    }

    private fun updateActionBarTitle(actionBar: ActionBar) {
        actionBar.title = getString(R.string.about_list_right)
    }
}