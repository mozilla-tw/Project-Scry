package org.mozilla.scryer.telemetry

import android.content.Context
import android.preference.PreferenceManager
import android.support.annotation.Nullable
import org.mozilla.scryer.BuildConfig
import org.mozilla.scryer.R
import org.mozilla.scryer.ScryerApplication
import org.mozilla.telemetry.Telemetry
import org.mozilla.telemetry.TelemetryHolder
import org.mozilla.telemetry.annotation.TelemetryDoc
import org.mozilla.telemetry.annotation.TelemetryExtra
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
        const val CLOSE_FAB = "Close FAB"
        const val STOP_CAPTURE_SERVICE = "Stop capture service"
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
        const val NOTIFICATION = "notification"
        const val SETTINGS = "settings"
    }

    private object Extra {
        const val ON = "on"
        const val MODE = "mode"
        const val TIMES = "times"
        const val MESSAGE = "message"
    }

    object ExtraValue {
        const val SINGLE = "single"
        const val MULTIPLE = "multiple"
    }

    companion object {

        private const val TELEMETRY_APP_NAME = "Scryer"

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

        @TelemetryDoc(
                name = Category.START_SESSION,
                category = Category.START_SESSION,
                method = Method.V1,
                `object` = Object.GO,
                value = Value.APP,
                extras = [])
        fun startSession() {
            TelemetryHolder.get().recordSessionStart()
            EventBuilder(Category.START_SESSION, Method.V1, Object.GO, Value.APP).queue()
        }

        @TelemetryDoc(
                name = Category.STOP_SESSION,
                category = Category.STOP_SESSION,
                method = Method.V1,
                `object` = Object.GO,
                value = Value.APP,
                extras = [])
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

        @TelemetryDoc(
                name = Category.VISIT_WELCOME_PAGE,
                category = Category.VISIT_WELCOME_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun visitWelcomePage() {
            EventBuilder(Category.VISIT_WELCOME_PAGE, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.GRANT_STORAGE_PERMISSION,
                category = Category.GRANT_STORAGE_PERMISSION,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [TelemetryExtra(name = Extra.TIMES, value = "times")])
        fun grantStoragePermission(times: Int) {
            EventBuilder(Category.GRANT_STORAGE_PERMISSION, Method.V1, Object.GO).extra(Extra.TIMES, times.toString()).queue()
        }

        @TelemetryDoc(
                name = Category.PROMPT_OVERLAY_PERMISSION,
                category = Category.PROMPT_OVERLAY_PERMISSION,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun promptOverlayPermission() {
            EventBuilder(Category.PROMPT_OVERLAY_PERMISSION, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.GRANT_OVERLAY_PERMISSION,
                category = Category.GRANT_OVERLAY_PERMISSION,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun grantOverlayPermission() {
            EventBuilder(Category.GRANT_OVERLAY_PERMISSION, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.NOT_GRANT_OVERLAY_PERMISSION,
                category = Category.NOT_GRANT_OVERLAY_PERMISSION,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun notGrantOverlayPermission() {
            EventBuilder(Category.NOT_GRANT_OVERLAY_PERMISSION, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.VISIT_PERMISSION_ERROR_PAGE,
                category = Category.VISIT_PERMISSION_ERROR_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun visitPermissionErrorPage() {
            EventBuilder(Category.VISIT_PERMISSION_ERROR_PAGE, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.VISIT_HOME_PAGE,
                category = Category.VISIT_HOME_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun visitHomePage() {
            EventBuilder(Category.VISIT_HOME_PAGE, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.START_SEARCH,
                category = Category.START_SEARCH,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun startSearch() {
            EventBuilder(Category.START_SEARCH, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.CLICK_ON_QUICK_ACCESS,
                category = Category.CLICK_ON_QUICK_ACCESS,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [TelemetryExtra(name = Extra.ON, value = "index")])
        fun clickOnQuickAccess(index: Int) {
            EventBuilder(Category.CLICK_ON_QUICK_ACCESS, Method.V1, Object.GO).extra(Extra.ON, index.toString()).queue()
        }

        @TelemetryDoc(
                name = Category.CLICK_MORE_ON_QUICK_ACCESS,
                category = Category.CLICK_MORE_ON_QUICK_ACCESS,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickMoreOnQuickAccess() {
            EventBuilder(Category.CLICK_MORE_ON_QUICK_ACCESS, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.CLICK_ON_COLLECTION,
                category = Category.CLICK_ON_COLLECTION,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickOnCollection() {
            EventBuilder(Category.CLICK_ON_COLLECTION, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.CREATE_COLLECTION_FROM_HOME,
                category = Category.CREATE_COLLECTION_FROM_HOME,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun createCollectionFromHome() {
            EventBuilder(Category.CREATE_COLLECTION_FROM_HOME, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.ENTER_SETTINGS,
                category = Category.ENTER_SETTINGS,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun enterSettings() {
            EventBuilder(Category.ENTER_SETTINGS, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.VISIT_COLLECTION_PAGE,
                category = Category.VISIT_COLLECTION_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [TelemetryExtra(name = Extra.ON, value = "collection_name")])
        fun visitCollectionPage(name: String) {
            EventBuilder(Category.VISIT_COLLECTION_PAGE, Method.V1, Object.GO).extra(Extra.ON, name).queue()
        }

        @TelemetryDoc(
                name = Category.COLLECTION_ITEM,
                category = Category.COLLECTION_ITEM,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [TelemetryExtra(name = Extra.ON, value = "collection_name")])
        fun collectionItem(name: String) {
            EventBuilder(Category.COLLECTION_ITEM, Method.V1, Object.GO).extra(Extra.ON, name).queue()
        }

        @TelemetryDoc(
                name = Category.CLICK_ON_SORTING_BUTTON,
                category = Category.CLICK_ON_SORTING_BUTTON,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickOnSortingButton() {
            EventBuilder(Category.CLICK_ON_SORTING_BUTTON, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.CREATE_COLLECTION_WHEN_SORTING,
                category = Category.CREATE_COLLECTION_WHEN_SORTING,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun createCollectionWhenSorting() {
            EventBuilder(Category.CREATE_COLLECTION_WHEN_SORTING, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.SORT_SCREENSHOT,
                category = Category.SORT_SCREENSHOT,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [TelemetryExtra(name = Extra.ON, value = "collection_name"),
                    TelemetryExtra(name = Extra.MODE, value = ExtraValue.SINGLE + "," + ExtraValue.MULTIPLE)])
        fun sortScreenshot(name: String, mode: String) {
            EventBuilder(Category.SORT_SCREENSHOT, Method.V1, Object.GO)
                    .extra(Extra.ON, name)
                    .extra(Extra.MODE, ExtraValue.SINGLE)
                    .queue()
        }

        @TelemetryDoc(
                name = Category.CANCEL_SORTING,
                category = Category.CANCEL_SORTING,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [TelemetryExtra(name = Extra.MODE, value = ExtraValue.SINGLE + "," + ExtraValue.MULTIPLE)])
        fun cancelSorting(mode: String) {
            EventBuilder(Category.CANCEL_SORTING, Method.V1, Object.GO)
                    .extra(Extra.MODE, mode).queue()
        }


        @TelemetryDoc(
                name = Category.PROMPT_SORTING_PAGE,
                category = Category.PROMPT_SORTING_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [TelemetryExtra(name = Extra.MODE, value = ExtraValue.SINGLE + "," + ExtraValue.MULTIPLE)])
        fun promptSortingPage(mode: String) {
            EventBuilder(Category.PROMPT_SORTING_PAGE, Method.V1, Object.GO).extra(Extra.MODE, mode).queue()
        }

        @TelemetryDoc(
                name = Category.CAPTURE_VIA_FAB,
                category = Category.CAPTURE_VIA_FAB,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun captureViaFab() {
            EventBuilder(Category.CAPTURE_VIA_FAB, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.CAPTURE_VIA_NOTIFICATION,
                category = Category.CAPTURE_VIA_NOTIFICATION,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun captureViaNotification() {
            EventBuilder(Category.CAPTURE_VIA_NOTIFICATION, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.CAPTURE_VIA_EXTERNAL,
                category = Category.CAPTURE_VIA_EXTERNAL,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun captureViaExternal() {
            EventBuilder(Category.CAPTURE_VIA_EXTERNAL, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.VIEW_SCREENSHOT,
                category = Category.VIEW_SCREENSHOT,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun viewScreenshot() {
            EventBuilder(Category.VIEW_SCREENSHOT, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.SHARE_SCREENSHOT,
                category = Category.SHARE_SCREENSHOT,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun shareScreenshot() {
            EventBuilder(Category.SHARE_SCREENSHOT, Method.V1, Object.GO).queue()
        }


        @TelemetryDoc(
                name = Category.EXTRACT_TEXT_FROM_SCREENSHOT,
                category = Category.EXTRACT_TEXT_FROM_SCREENSHOT,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun extractTextFromScreenshot() {
            EventBuilder(Category.EXTRACT_TEXT_FROM_SCREENSHOT, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.VIEW_TEXT_IN_SCREENSHOT,
                category = Category.VIEW_TEXT_IN_SCREENSHOT,
                method = Method.V1,
                `object` = Object.GO,
                value = "success,weird_size,fail",
                extras = [TelemetryExtra(name = Extra.MESSAGE, value = "empty or failing reason")])
        fun viewTextInScreenshot(value: String, message: String = "") {
            EventBuilder(Category.VIEW_TEXT_IN_SCREENSHOT, Method.V1, Object.GO, value).extra(Extra.MESSAGE, message).queue()
        }

        @TelemetryDoc(
                name = Category.VISIT_SEARCH_PAGE,
                category = Category.VISIT_SEARCH_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun visitSearchPage() {
            EventBuilder(Category.VISIT_SEARCH_PAGE, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.INTERESTED_IN_SEARCH,
                category = Category.INTERESTED_IN_SEARCH,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun interestedInSearch() {
            EventBuilder(Category.INTERESTED_IN_SEARCH, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.NOT_INTERESTED_IN_SEARCH,
                category = Category.NOT_INTERESTED_IN_SEARCH,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun notInterestedInSearch() {
            EventBuilder(Category.NOT_INTERESTED_IN_SEARCH, Method.V1, Object.GO).queue()
        }

        fun closeFAB() {
            EventBuilder(Category.CLOSE_FAB, Method.V1, Object.GO).queue()
        }

        fun stopCaptureService(value: String) {
            EventBuilder(Category.STOP_CAPTURE_SERVICE, Method.V1, Object.GO, value).queue()
        }
    }

    internal class EventBuilder @JvmOverloads constructor(category: String, method: String, @Nullable `object`: String, value: String? = null) {
        var telemetryEvent: TelemetryEvent = TelemetryEvent.create(category, method, `object`, value)
        var firebaseEvent: FirebaseEvent = FirebaseEvent.create(category, method, `object`, value)


        fun extra(key: String, value: String): EventBuilder {
            telemetryEvent.extra(key, value)
            firebaseEvent.param(key, value)
            return this
        }

        fun queue() {
            val context = TelemetryHolder.get().configuration.context
            if (context != null) {
                telemetryEvent.queue()
                firebaseEvent.event(context)
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
