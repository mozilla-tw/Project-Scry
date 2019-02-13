package org.mozilla.scryer.telemetry

import android.app.ActivityManager
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.mozilla.scryer.ScryerService


class CaptureServiceHeartbeatWorker(private val context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val TAG = "CaptureServiceHeartbeatWorker"
    }

    override fun doWork(): Result {

        if (isCaptureServiceRunning()) {
            TelemetryWrapper.logActiveBackgroundService()
        }
        return Result.success()
    }

    private fun isCaptureServiceRunning(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (ScryerService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}