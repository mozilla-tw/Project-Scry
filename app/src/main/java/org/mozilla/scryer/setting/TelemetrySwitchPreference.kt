package org.mozilla.scryer.setting


import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import android.util.AttributeSet
import android.widget.TextView
import org.mozilla.scryer.R
import java.util.*

class TelemetrySwitchPreference : SwitchPreferenceCompat {

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val learnMore = holder.itemView.findViewById<TextView>(R.id.learnMore)
        learnMore.setOnClickListener { v ->
            // This is a hardcoded link: if we ever end up needing more of these links, we should
            // move the link into an xml parameter, but there's no advantage to making it configurable now.
            val url = getTelemetryDataUsageUrl(context)

            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse(url)
            context.startActivity(intent)
        }

        super.onBindViewHolder(holder)
    }

    private fun getTelemetryDataUsageUrl(context: Context): String {
        return context.getString(R.string.telemetry_data_usage_url,
                getAppVersion(context),
                getLanguageTag(Locale.getDefault()))
    }
}
