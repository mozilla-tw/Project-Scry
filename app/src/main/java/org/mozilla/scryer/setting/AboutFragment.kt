package org.mozilla.scryer.setting

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.appcompat.app.ActionBar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.mozilla.scryer.R
import org.mozilla.scryer.getSupportActionBar
import java.util.*

class AboutFragment : androidx.fragment.app.Fragment() {
    companion object {
        const val TAG: String = "AboutFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view: View = inflater.inflate(R.layout.fragment_about, container, false)

        val versionText = view.findViewById<TextView>(R.id.about_text_version)
        versionText?.text = getString(R.string.about_content_version, getAppVersion(context!!))

        val supportButton = view.findViewById<Button>(R.id.about_btn_support)
        supportButton.setOnClickListener {
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse(getSupportPageUrl(context!!))
            startActivity(intent)
        }

        val yourRightsButton = view.findViewById<Button>(R.id.about_btn_your_rights)
        yourRightsButton.setOnClickListener {
            showYourRightsPage()
        }

        val privacyNoticeButton = view.findViewById<Button>(R.id.about_btn_privacy_notice)
        privacyNoticeButton.setOnClickListener {
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse(getString(R.string.about_privacy_notice_url))
            startActivity(intent)
        }

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
        actionBar.title = getString(R.string.settings_list_about)
    }

    private fun getSupportPageUrl(context: Context): String {
        return context.getString(R.string.about_support_url,
                getAppVersion(context),
                getLanguageTag(Locale.getDefault()))
    }

    private fun showYourRightsPage() {
        activity?.supportFragmentManager
                ?.beginTransaction()
                ?.replace(R.id.container, YourRightsFragment())
                ?.addToBackStack(YourRightsFragment.TAG)
                ?.commitAllowingStateLoss()
    }
}

fun getAppVersion(context: Context): String {
    var appVersion = ""
    try {
        appVersion = context.packageManager.getPackageInfo(context.packageName, 0)?.versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        // Nothing to do if we can't find the package name.
    }
    return appVersion
}

/**
 * Gecko uses locale codes like "es-ES", whereas a Java [Locale]
 * stringifies as "es_ES".
 *
 *
 * This method approximates the Java 7 method
 * `Locale#toLanguageTag()`.
 *
 * @return a locale string suitable for passing to Gecko.
 */
fun getLanguageTag(locale: Locale): String {
    // If this were Java 7:
    // return locale.toLanguageTag();

    val language = getLanguage(locale)
    val country = locale.country // Can be an empty string.
    return if (country == "") {
        language
    } else "$language-$country"
}

/**
 * Sometimes we want just the language for a locale, not the entire language
 * tag. But Java's .getLanguage method is wrong.
 *
 *
 * This method is equivalent to the first part of
 * [Locales.getLanguageTag].
 *
 * @return a language string, such as "he" for the Hebrew locales.
 */
private fun getLanguage(locale: Locale): String {
    // Can, but should never be, an empty string.
    val language = locale.language

    // Modernize certain language codes.
    if (language == "iw") {
        return "he"
    }

    if (language == "in") {
        return "id"
    }

    return if (language == "ji") {
        "yi"
    } else language

}