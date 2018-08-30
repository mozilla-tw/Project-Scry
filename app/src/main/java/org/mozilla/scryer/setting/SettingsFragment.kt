package org.mozilla.scryer.setting

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.ActionBar
import android.support.v7.app.AlertDialog
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.SwitchPreferenceCompat
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import org.mozilla.scryer.*
import org.mozilla.scryer.permission.PermissionHelper

class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private val enableCaptureService: SwitchPreferenceCompat by lazy { findPreference(getString(R.string.pref_key_enable_capture_service)) as SwitchPreferenceCompat }
    private val enableFloatingScreenshotButton: SwitchPreferenceCompat by lazy { findPreference(getString(R.string.pref_key_enable_floating_screenshot_button)) as SwitchPreferenceCompat }
    private val enableAddToCollectionButton: SwitchPreferenceCompat by lazy { findPreference(getString(R.string.pref_key_enable_add_to_collection)) as SwitchPreferenceCompat }
    private val giveFeedbackPreference: Preference by lazy { findPreference(getString(R.string.pref_key_give_feedback)) }
    private val shareWithFriendsPreference: Preference by lazy { findPreference(getString(R.string.pref_key_share_with_friends)) }
    private val aboutPreference: Preference by lazy { findPreference(getString(R.string.pref_key_about)) }

    private var overlayPermissionRequested = false

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
        actionBar.title = getString(R.string.menu_home_action_settings)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        enableCaptureService.onPreferenceChangeListener = this
        ScryerApplication.getSettingsRepository().serviceEnabledObserver.observe(this, Observer {
            enableCaptureService.isChecked = it
        })

        enableFloatingScreenshotButton.onPreferenceChangeListener = this
        ScryerApplication.getSettingsRepository().floatingEnableObservable.observe(this, Observer { enabled ->
            enableFloatingScreenshotButton.isChecked = enabled
            onFloatingEnableStateChanged(enabled)
        })

        enableAddToCollectionButton.onPreferenceChangeListener = this
        ScryerApplication.getSettingsRepository().addToCollectionEnableObservable.observe(this, Observer {
            enableAddToCollectionButton.isChecked = it
        })

        giveFeedbackPreference.onPreferenceClickListener = this
        shareWithFriendsPreference.onPreferenceClickListener = this
        aboutPreference.onPreferenceClickListener = this
    }

    override fun onResume() {
        super.onResume()
        checkOverlayPermission()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val repository = ScryerApplication.getSettingsRepository()

        if (preference == enableCaptureService) {
            val enable = newValue as Boolean
            val intent = Intent(activity, ScryerService::class.java)
            intent.action = if (enable) {
                ScryerService.ACTION_ENABLE_SERVICE
            } else {
                ScryerService.ACTION_STOP
            }
            activity?.startService(intent)

            repository.serviceEnabled = enable

            return true
        } else if (preference == enableFloatingScreenshotButton) {
            repository.floatingEnable = newValue as Boolean

            return true
        } else if (preference == enableAddToCollectionButton) {
            repository.addToCollectionEnable = newValue as Boolean

            return true
        }

        return false
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        when (preference) {
            giveFeedbackPreference -> context?.let { showFeedbackDialog(it); return true }
            shareWithFriendsPreference -> context?.let { showShareAppDialog(it); return true }
            aboutPreference -> context?.let { showAboutPage(it); return true }
        }

        return false
    }

    private fun showFeedbackDialog(context: Context) {
        val dialog = AlertDialog.Builder(context).create()
        dialog?.setOnCancelListener {
            // TODO: telemetry
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_give_feedback, null as ViewGroup?)
        dialogView.findViewById<Button>(R.id.dialog_give_feedback_btn_go_rate).setOnClickListener {
            goToPlayStore(context)
            dialog?.dismiss()
        }
        dialogView.findViewById<Button>(R.id.dialog_give_feedback_btn_feedback).setOnClickListener {
            // TODO: go to feedback web page
            dialog?.dismiss()
        }
        dialog.setView(dialogView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    private fun showShareAppDialog(context: Context) {
        val sendIntent = Intent(Intent.ACTION_SEND)
        sendIntent.type = "text/plain"
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
        sendIntent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_intro))
        context.startActivity(Intent.createChooser(sendIntent, null))
    }

    private fun showAboutPage(context: Context) {
        activity?.supportFragmentManager
                ?.beginTransaction()
                ?.replace(R.id.container, AboutFragment())
                ?.addToBackStack(AboutFragment.TAG)
                ?.commitAllowingStateLoss()
    }

    private fun goToPlayStore(context: Context) {
        val appPackageName = context.packageName
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            // No google play install
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun checkOverlayPermission() {
        val context = context?: return
        val hasPermission = PermissionHelper.hasOverlayPermission(context)

        if (hasPermission) {
            if (overlayPermissionRequested) {
                overlayPermissionRequested = false
                // Since overlayPermissionRequested will only be set to true after user toggles on
                // the switch without overlay permission, in which case the preference value has already
                // been set to "true", no need to set it again here
                enableCaptureButton()
            }
        } else {
            // Permission disabled after resume => set false to repo
            ScryerApplication.getSettingsRepository().floatingEnable = false
        }
    }

    private fun onFloatingEnableStateChanged(enabled: Boolean) {
        val activity = activity?: return

        if (enabled) {
            if (PermissionHelper.hasOverlayPermission(activity)) {
                enableCaptureButton()
            } else {
                overlayPermissionRequested = true
                PermissionHelper.requestOverlayPermission(activity, MainActivity.REQUEST_CODE_OVERLAY_PERMISSION)
            }
        } else {
            val intent = Intent(activity, ScryerService::class.java)
            intent.action = ScryerService.ACTION_DISABLE_CAPTURE_BUTTON
            activity.startService(intent)
        }
    }

    private fun enableCaptureButton() {
        val intent = Intent(activity, ScryerService::class.java)
        intent.action = ScryerService.ACTION_ENABLE_CAPTURE_BUTTON
        activity?.startService(intent)
    }
}
