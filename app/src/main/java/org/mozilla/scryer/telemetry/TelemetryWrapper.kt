package org.mozilla.scryer.telemetry

import android.annotation.SuppressLint
import android.content.Context
import android.preference.PreferenceManager
import android.support.annotation.Nullable
import android.util.Log
import org.mozilla.scryer.BuildConfig
import org.mozilla.scryer.R
import org.mozilla.scryer.ScryerApplication
import org.mozilla.telemetry.Telemetry
import org.mozilla.telemetry.TelemetryHolder
import org.mozilla.telemetry.config.TelemetryConfiguration
import org.mozilla.telemetry.event.TelemetryEvent
import org.mozilla.telemetry.measurement.SettingsMeasurement
import org.mozilla.telemetry.measurement.TelemetryMeasurement
import org.mozilla.telemetry.net.HttpURLConnectionTelemetryClient
import org.mozilla.telemetry.ping.TelemetryCorePingBuilder
import org.mozilla.telemetry.ping.TelemetryEventPingBuilder
import org.mozilla.telemetry.schedule.jobscheduler.JobSchedulerTelemetryScheduler
import org.mozilla.telemetry.serialize.JSONPingSerializer
import org.mozilla.telemetry.storage.FileTelemetryStorage


class TelemetryWrapper {

    private object Category {
        const val START_SESSION = "Start session"
        const val STOP_SESSION = "Stop session"
        const val VISIT_WELCOME_PAGE = "Visit welcome page"
        const val GRANT_STORAGE_PERMISSION = "Grant storage permission"
        const val PROMPT_OVERLAY_PERMISSION = "Prompt overlay permission"
        const val GRANT_OVERLAY_PERMISSION = "Grant overlay permission"
        const val NOT_GRANT_OVERLAY_PERMISSION = "Not grant overlay permission"
        const val VISIT_PERMISSION_ERROR_PAGE = "Visit permission error page"
        const val VISIT_HOME_PAGE = "Visit home page"
        const val START_SEARCH = "Start search"
        const val CLICK_ON_QUICK_ACCESS = "Click on quick access"
        const val CLICK_MORE_ON_QUICK_ACCESS = "Click more on quick access"
        const val CLICK_ON_COLLECTION = "Click on collection"
        const val CREATE_COLLECTION_FROM_HOME = "Create collection from home"
        const val ENTER_SETTINGS = "Enter settings"
        const val VISIT_COLLECTION_PAGE = "Visit collection page"
        const val CLICK_ON_SORTING_BUTTON = "Click on sorting button"
        const val COLLECTION_ITEM = "Click on collection item"
        const val CREATE_COLLECTION_WHEN_SORTING = "Create collection when sorting"
        const val PROMPT_SORTING_PAGE = "Prompt sorting page"
        const val SORT_SCREENSHOT = "Sort screenshot"
        const val CANCEL_SORTING = "Cancel sorting"
        const val CAPTURE_VIA_FAB = "Capture via FAB"
        const val CAPTURE_VIA_NOTIFICATION = "Capture via notification"
        const val CAPTURE_VIA_EXTERNAL = "Capture via external"
        const val VIEW_SCREENSHOT = "View screenshot"
        const val SHARE_SCREENSHOT = "Share screenshot"
        const val EXTRACT_TEXT_FROM_SCREENSHOT = "Extract text from screenshot"
        const val VIEW_TEXT_IN_SCREENSHOT = "View text in screenshot"
        const val VISIT_SEARCH_PAGE = "Visit search page"
        const val INTERESTED_IN_SEARCH = "Interested in search"
        const val NOT_INTERESTED_IN_SEARCH = "Not interested in search"
    }

    private object Method {
        const val V1 = "1"
    }

    private object Object {
        const val GO = "go"
    }

    object Value {
        const val APP = "app"
        const val SUCCESS = "success"
        const val WEIRD_SIZE = "weird_size"
        const val FAIL = "fail"
    }

    private object Extra {
        const val ON = "on"
        const val MODE = "mode"
        const val TIMES = "times"
    }

    private object ExtraValue {
        const val SINGLE = "single"
        const val MULTIPLE = "multiple"
    }

    companion object {

        private const val TELEMETRY_APP_NAME = "Scryer"

        private var DebugTelemetry = false

        //  static init block
        init {
            try {
                val enabled = getPropertiesFromSystem ("log.tag.mozdebug")
                DebugTelemetry = enabled.toBoolean()
            } catch (e : Exception) {
            }
        }

        //  TODO: move this to util class
        @SuppressLint("PrivateApi")
        @Throws(Exception::class)
        internal fun getPropertiesFromSystem(key: String): String {
            val c = Class.forName("android.os.SystemProperties")
            val method = c.getDeclaredMethod("get", String::class.java)
            return method.invoke(null, key) as String
        }

        fun init(context: Context) {
            try {
                val resources = context.resources
                val telemetryEnabled = isTelemetryEnabled(context)

                val configuration = TelemetryConfiguration(context)
                        .setServerEndpoint("https://incoming.telemetry.mozilla.org")
                        .setAppName(TELEMETRY_APP_NAME)
                        .setUpdateChannel(BuildConfig.BUILD_TYPE)
                        .setPreferencesImportantForTelemetry(
                                resources.getString(R.string.pref_key_enable_capture_service),
                                resources.getString(R.string.pref_key_enable_floating_screenshot_button),
                                resources.getString(R.string.pref_key_enable_add_to_collection))
                        .setSettingsProvider(CustomSettingsProvider())
                        .setCollectionEnabled(telemetryEnabled)
                        .setUploadEnabled(telemetryEnabled)

                val serializer = JSONPingSerializer()
                val storage = FileTelemetryStorage(configuration, serializer)
                val client = HttpURLConnectionTelemetryClient()
                val scheduler = JobSchedulerTelemetryScheduler()

                TelemetryHolder.set(Telemetry(configuration, storage, client, scheduler)
                        .addPingBuilder(TelemetryCorePingBuilder(configuration))
                        .addPingBuilder(TelemetryEventPingBuilder(configuration)))
            } finally {
            }
        }

        private fun isTelemetryEnabled(context: Context): Boolean {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val isEnabledByDefault = BuildConfig.BUILD_TYPE == "release"
            return preferences.getBoolean(context.resources.getString(R.string.pref_key_enable_send_usage_data), isEnabledByDefault)
        }

        fun startSession() {
            TelemetryHolder.get().recordSessionStart()
            EventBuilder(Category.START_SESSION, Method.V1, Object.GO, Value.APP).queue()
        }

        fun stopSession() {
            TelemetryHolder.get().recordSessionEnd()
            EventBuilder(Category.STOP_SESSION, Method.V1, Object.GO, Value.APP).queue()
        }

        fun stopMainActivity() {
            TelemetryHolder.get()
                    .queuePing(TelemetryCorePingBuilder.TYPE)
                    .queuePing(TelemetryEventPingBuilder.TYPE)
                    .scheduleUpload()
        }

        fun visitWelcomePage() {
            EventBuilder(Category.VISIT_WELCOME_PAGE, Method.V1, Object.GO).queue()
        }

        fun grantStoragePermission(times: Int) {
            EventBuilder(Category.GRANT_STORAGE_PERMISSION, Method.V1, Object.GO).extra(Extra.TIMES, times.toString()).queue()
        }

        fun promptOverlayPermission() {
            EventBuilder(Category.PROMPT_OVERLAY_PERMISSION, Method.V1, Object.GO).queue()
        }

        fun grantOverlayPermission() {
            EventBuilder(Category.GRANT_OVERLAY_PERMISSION, Method.V1, Object.GO).queue()
        }

        fun notGrantOverlayPermission() {
            EventBuilder(Category.NOT_GRANT_OVERLAY_PERMISSION, Method.V1, Object.GO).queue()
        }

        fun visitPermissionErrorPage() {
            EventBuilder(Category.VISIT_PERMISSION_ERROR_PAGE, Method.V1, Object.GO).queue()
        }

        fun visitHomePage() {
            EventBuilder(Category.VISIT_HOME_PAGE, Method.V1, Object.GO).queue()
        }

        fun startSearch() {
            EventBuilder(Category.START_SEARCH, Method.V1, Object.GO).queue()
        }

        fun clickOnQuickAccess(index: Int) {
            EventBuilder(Category.CLICK_ON_QUICK_ACCESS, Method.V1, Object.GO).extra(Extra.ON, index.toString()).queue()
        }

        fun clickMoreOnQuickAccess() {
            EventBuilder(Category.CLICK_MORE_ON_QUICK_ACCESS, Method.V1, Object.GO).queue()
        }

        fun clickOnCollection() {
            EventBuilder(Category.CLICK_ON_COLLECTION, Method.V1, Object.GO).queue()
        }

        fun createCollectionFromHome() {
            EventBuilder(Category.CREATE_COLLECTION_FROM_HOME, Method.V1, Object.GO).queue()
        }

        fun enterSettings() {
            EventBuilder(Category.ENTER_SETTINGS, Method.V1, Object.GO).queue()
        }

        fun visitCollectionPage(name: String) {
            EventBuilder(Category.VISIT_COLLECTION_PAGE, Method.V1, Object.GO).extra(Extra.ON, name).queue()
        }

        fun collectionItem(name: String) {
            EventBuilder(Category.COLLECTION_ITEM, Method.V1, Object.GO).extra(Extra.ON, name).queue()
        }

        fun clickOnSortingButton() {
            EventBuilder(Category.CLICK_ON_SORTING_BUTTON, Method.V1, Object.GO).queue()
        }

        fun createCollectionWhenSorting() {
            EventBuilder(Category.CREATE_COLLECTION_WHEN_SORTING, Method.V1, Object.GO).queue()
        }

        fun promptSingleSortingPage() {
            EventBuilder(Category.PROMPT_SORTING_PAGE, Method.V1, Object.GO).extra(Extra.MODE, ExtraValue.SINGLE).queue()
        }

        fun promptMultipleSortingPage() {
            EventBuilder(Category.PROMPT_SORTING_PAGE, Method.V1, Object.GO).extra(Extra.MODE, ExtraValue.MULTIPLE).queue()
        }

        fun sortScreenshot() {
            EventBuilder(Category.SORT_SCREENSHOT, Method.V1, Object.GO).queue()
        }

        fun cancelSorting() {
            EventBuilder(Category.CANCEL_SORTING, Method.V1, Object.GO).queue()
        }

        fun captureViaFab() {
            EventBuilder(Category.CAPTURE_VIA_FAB, Method.V1, Object.GO).queue()
        }

        fun captureViaNotification() {
            EventBuilder(Category.CAPTURE_VIA_NOTIFICATION, Method.V1, Object.GO).queue()
        }

        fun captureViaExternal() {
            EventBuilder(Category.CAPTURE_VIA_EXTERNAL, Method.V1, Object.GO).queue()
        }

        fun viewScreenshot() {
            EventBuilder(Category.VIEW_SCREENSHOT, Method.V1, Object.GO).queue()
        }

        fun shareScreenshot() {
            EventBuilder(Category.SHARE_SCREENSHOT, Method.V1, Object.GO).queue()
        }

        fun extractTextFromScreenshot() {
            EventBuilder(Category.EXTRACT_TEXT_FROM_SCREENSHOT, Method.V1, Object.GO).queue()
        }

        fun viewTextInScreenshot(value: String) {
            EventBuilder(Category.VIEW_TEXT_IN_SCREENSHOT, Method.V1, Object.GO, value).queue()
        }

        fun visitSearchPage() {
            EventBuilder(Category.VISIT_SEARCH_PAGE, Method.V1, Object.GO).queue()
        }

        fun interestedInSearch() {
            EventBuilder(Category.INTERESTED_IN_SEARCH, Method.V1, Object.GO).queue()
        }

        fun notInterestedInSearch() {
            EventBuilder(Category.NOT_INTERESTED_IN_SEARCH, Method.V1, Object.GO).queue()
        }
    }

    internal class EventBuilder @JvmOverloads constructor(val category: String, val method: String, @Nullable val `object`: String, val value: String? = null) {
        var telemetryEvent: TelemetryEvent = TelemetryEvent.create(category, method, `object`, value)
        //TODO: Add firebase event

        fun extra(key: String, value: String): EventBuilder {
            telemetryEvent.extra(key, value)
            return this
        }

        fun queue() {
            if(DebugTelemetry) {
                val str = StringBuilder()
                val append = str
                        .append("{\n\tcategory: ").append(category)
                        .append("\n\tmethod: ").append(method)
                        .append("\n\tobject: ").append(`object`)
                        .append("\n\tvalue: ").append(value)
                        .append("\n}")
                Log.d("DebugTelemetry˛", append.toString())
            }

            val context = TelemetryHolder.get().configuration.context
            if (context != null) {
                telemetryEvent.queue()
            }
        }
    }

    private class CustomSettingsProvider : SettingsMeasurement.SharedPreferenceSettingsProvider() {

        private val custom = HashMap<String, Any>(1)

        override fun update(configuration: TelemetryConfiguration) {
            super.update(configuration)

            addCustomPing(configuration, ScreenshotCountMeasurement())
        }


        internal fun addCustomPing(configuration: TelemetryConfiguration, measurement: TelemetryMeasurement) {
            var preferenceKeys: MutableSet<String>? = configuration.preferencesImportantForTelemetry
            if (preferenceKeys == null) {
                configuration.setPreferencesImportantForTelemetry()
                preferenceKeys = configuration.preferencesImportantForTelemetry
            }
            preferenceKeys!!.add(measurement.fieldName)
            custom[measurement.fieldName] = measurement.flush()
        }

        override fun containsKey(key: String): Boolean {
            return super.containsKey(key) or custom.containsKey(key)
        }

        override fun getValue(key: String): Any {
            return custom[key] ?: super.getValue(key)
        }
    }

    private class ScreenshotCountMeasurement : TelemetryMeasurement(MEASUREMENT_SCREENSHOT_COUNT) {

        override fun flush(): Any {
            if ("main" == Thread.currentThread().name) {
                throw RuntimeException("Call from main thread exception")
            }

            return try {
                ScryerApplication.getScreenshotRepository().getScreenshotList().size
            } catch (e: Exception) {
                -1
            }
        }

        companion object {
            private const val MEASUREMENT_SCREENSHOT_COUNT = "screenshot_count"
        }
    }
}
