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
        const val HOME_QUICK_ACCESS_MORE = "home_quick_access_more"
        const val HOME_COLLECTIONS = "home_collections"
        const val HOME_CREATE_NEW_COLLECTION = "home_create_new_collection"
        const val HOME_SETTINGS = "home_settings"
        const val COLLECTION_PAGE = "collection_page"
        const val COLLECTION_SORTING_BUTTON = "collection_sorting_button"
        const val COLLECTION_ITEM = "collection_item"
        const val SORTING_PAGE = "sorting_page"
        const val SORTING_MOVE_TO_BUTTON = "sorting_move_to_button"
        const val SORTING_SORT_CANCEL = "sorting_sort_cancel"
        const val SORTING_CREATE_NEW_COLLECTION = "sorting_create_new_collection"
        const val CAPTURE_BUTTON = "capture_button"
        const val CAPTURE_VIA_NOTIFICATION = "capture_via_notification"
        const val CAPTURE_VIA_EXTERNAL = "capture_via_external"
        const val DETAIL_PAGE = "detail_page"
        const val DETAIL_SHARE_BUTTON = "detail_share_button"
        const val TEXT_MODE_BUTTON = "text_mode_button"
        const val TEXT_MODE_RESULT = "text_mode_result"
        const val SEARCH_PAGE = "search_page"
        const val SEARCH_INTERESTED = "search_interested"
        const val SEARCH_NOT_INTERESTED = "search_not_interested"
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
    }

    private object ExtraValue {
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
                action = Category.START_SESSION,
                method = Method.V1,
                `object` = Object.GO,
                value = Value.APP,
                extras = [])
        fun startSession() {
            TelemetryHolder.get().recordSessionStart()
            EventBuilder(Category.START_SESSION, Method.V1, Object.GO, Value.APP).queue()
        }

        @TelemetryDoc(
                name = Category.START_SESSION,
                action = Category.START_SESSION,
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
                action = Category.VISIT_WELCOME_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun visitWelcomePage() {
            EventBuilder(Category.VISIT_WELCOME_PAGE, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.GRANT_STORAGE_PERMISSION,
                action = Category.GRANT_STORAGE_PERMISSION,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun grantStoragePermission() {
            EventBuilder(Category.GRANT_STORAGE_PERMISSION, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.PROMPT_OVERLAY_PERMISSION,
                action = Category.PROMPT_OVERLAY_PERMISSION,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun promptOverlayPermission() {
            EventBuilder(Category.PROMPT_OVERLAY_PERMISSION, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.GRANT_OVERLAY_PERMISSION,
                action = Category.GRANT_OVERLAY_PERMISSION,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun grantOverlayPermission() {
            EventBuilder(Category.GRANT_OVERLAY_PERMISSION, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.NOT_GRANT_OVERLAY_PERMISSION,
                action = Category.NOT_GRANT_OVERLAY_PERMISSION,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun notGrantOverlayPermission() {
            EventBuilder(Category.NOT_GRANT_OVERLAY_PERMISSION, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.VISIT_PERMISSION_ERROR_PAGE,
                action = Category.VISIT_PERMISSION_ERROR_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun visitPermissionErrorPage() {
            EventBuilder(Category.VISIT_PERMISSION_ERROR_PAGE, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.VISIT_HOME_PAGE,
                action = Category.VISIT_HOME_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun showHomePage() {
            EventBuilder(Category.VISIT_HOME_PAGE, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.START_SEARCH,
                action = Category.START_SEARCH,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickHomeSearchBar() {
            EventBuilder(Category.START_SEARCH, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.CLICK_ON_QUICK_ACCESS,
                action = Category.CLICK_ON_QUICK_ACCESS,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [TelemetryExtra(name = Extra.ON, value = "position")])
        fun clickHomeQuickAccessItem(index: Int) {
            EventBuilder(Category.CLICK_ON_QUICK_ACCESS, Method.V1, Object.GO).extra(Extra.ON, index.toString()).queue()
        }

        @TelemetryDoc(
                name = Category.HOME_QUICK_ACCESS_MORE,
                action = Category.HOME_QUICK_ACCESS_MORE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickHomeQuickAccessMoreItem() {
            EventBuilder(Category.HOME_QUICK_ACCESS_MORE, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.HOME_COLLECTIONS,
                action = Category.HOME_COLLECTIONS,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickHomeCollectionItem() {
            EventBuilder(Category.HOME_COLLECTIONS, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.HOME_CREATE_NEW_COLLECTION,
                action = Category.HOME_CREATE_NEW_COLLECTION,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickHomeCreateNewCollectionItem() {
            EventBuilder(Category.HOME_CREATE_NEW_COLLECTION, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.HOME_SETTINGS,
                action = Category.HOME_SETTINGS,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickHomeSettings() {
            EventBuilder(Category.HOME_SETTINGS, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.COLLECTION_PAGE,
                action = Category.COLLECTION_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun showCollectionPage(name: String) {
            EventBuilder(Category.COLLECTION_PAGE, Method.V1, Object.GO).extra(Extra.ON, name).queue()
        }

        @TelemetryDoc(
                name = Category.COLLECTION_ITEM,
                action = Category.COLLECTION_ITEM,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [TelemetryExtra(name = Extra.ON, value = "collection name,see the doc for more details.")])
        fun clickCollectionItem(name: String) {
            EventBuilder(Category.COLLECTION_ITEM, Method.V1, Object.GO).extra(Extra.ON, name).queue()
        }

        @TelemetryDoc(
                name = Category.COLLECTION_SORTING_BUTTON,
                action = Category.COLLECTION_SORTING_BUTTON,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickSortingInCollectionPage() {
            EventBuilder(Category.COLLECTION_SORTING_BUTTON, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.SORTING_PAGE,
                action = Category.SORTING_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [TelemetryExtra(name = Extra.MODE, value = ExtraValue.SINGLE)])
        fun showSingleSortingPage() {
            EventBuilder(Category.SORTING_PAGE, Method.V1, Object.GO).extra(Extra.MODE, ExtraValue.SINGLE).queue()
        }

        @TelemetryDoc(
                name = Category.SORTING_PAGE,
                action = Category.SORTING_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = Value.APP,
                extras = [TelemetryExtra(name = Extra.MODE, value = ExtraValue.MULTIPLE)])
        fun showMultipleSortingPage() {
            EventBuilder(Category.SORTING_PAGE, Method.V1, Object.GO).extra(Extra.MODE, ExtraValue.MULTIPLE).queue()
        }

        @TelemetryDoc(
                name = Category.SORTING_MOVE_TO_BUTTON,
                action = Category.SORTING_MOVE_TO_BUTTON,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickMoveToInSortingPage() {
            EventBuilder(Category.SORTING_MOVE_TO_BUTTON, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.SORTING_SORT_CANCEL,
                action = Category.SORTING_SORT_CANCEL,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickCancelInSortingPage() {
            EventBuilder(Category.SORTING_SORT_CANCEL, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.SORTING_CREATE_NEW_COLLECTION,
                action = Category.SORTING_CREATE_NEW_COLLECTION,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickCreateNewCollectionItemInSortingPage() {
            EventBuilder(Category.SORTING_CREATE_NEW_COLLECTION, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.CAPTURE_BUTTON,
                action = Category.CAPTURE_BUTTON,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickCaptureButton() {
            EventBuilder(Category.CAPTURE_BUTTON, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.CAPTURE_VIA_NOTIFICATION,
                action = Category.CAPTURE_VIA_NOTIFICATION,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickCaptureViaNotification() {
            EventBuilder(Category.CAPTURE_VIA_NOTIFICATION, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.CAPTURE_VIA_EXTERNAL,
                action = Category.CAPTURE_VIA_EXTERNAL,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickCaptureViaExternal() {
            EventBuilder(Category.CAPTURE_VIA_EXTERNAL, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.DETAIL_PAGE,
                action = Category.DETAIL_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun showDetailPage() {
            EventBuilder(Category.DETAIL_PAGE, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.TEXT_MODE_BUTTON,
                action = Category.TEXT_MODE_BUTTON,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickTextModeButton() {
            EventBuilder(Category.TEXT_MODE_BUTTON, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.DETAIL_SHARE_BUTTON,
                action = Category.DETAIL_SHARE_BUTTON,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickShareButtonInDetailPage() {
            EventBuilder(Category.DETAIL_SHARE_BUTTON, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.TEXT_MODE_RESULT,
                action = Category.TEXT_MODE_RESULT,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun showTextModeResult(value: String) {
            EventBuilder(Category.TEXT_MODE_RESULT, Method.V1, Object.GO, value).queue()
        }

        @TelemetryDoc(
                name = Category.SEARCH_PAGE,
                action = Category.SEARCH_PAGE,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun showSearchPage() {
            EventBuilder(Category.SEARCH_PAGE, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.SEARCH_INTERESTED,
                action = Category.SEARCH_INTERESTED,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickSearchInterested() {
            EventBuilder(Category.SEARCH_INTERESTED, Method.V1, Object.GO).queue()
        }

        @TelemetryDoc(
                name = Category.SEARCH_NOT_INTERESTED,
                action = Category.SEARCH_NOT_INTERESTED,
                method = Method.V1,
                `object` = Object.GO,
                value = "",
                extras = [])
        fun clickSearchNotInterested() {
            EventBuilder(Category.SEARCH_NOT_INTERESTED, Method.V1, Object.GO).queue()
        }
    }

    internal class EventBuilder @JvmOverloads constructor(category: String, method: String, @Nullable `object`: String, value: String? = null) {
        var telemetryEvent: TelemetryEvent = TelemetryEvent.create(category, method, `object`, value)
        //TODO: Add firebase event

        fun extra(key: String, value: String): EventBuilder {
            telemetryEvent.extra(key, value)
            return this
        }

        fun queue() {
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
