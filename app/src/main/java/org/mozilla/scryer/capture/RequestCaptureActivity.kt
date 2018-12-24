package org.mozilla.scryer.capture

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.appcompat.app.AppCompatActivity
import android.view.WindowManager

class RequestCaptureActivity : AppCompatActivity() {

    private var requestStartTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw transparent status and navigation bar
        val window = this.window
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.transparent)

        requestScreenCapturePermission()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE_PERMISSION) {
            val intent = Intent(getResultBroadcastAction(this))
            intent.putExtra(RESULT_EXTRA_CODE, resultCode)
            intent.putExtra(RESULT_EXTRA_DATA, data)
            intent.putExtra(RESULT_EXTRA_PROMPT_SHOWN, promptShown())
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            finish()
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun requestScreenCapturePermission() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()

        requestStartTime = System.currentTimeMillis()
        startActivityForResult(intent, REQUEST_CODE_SCREEN_CAPTURE_PERMISSION)
    }

    private fun promptShown(): Boolean {
        // Assume that the prompt was shown if the response took 200ms or more to return.
        return System.currentTimeMillis() - requestStartTime > 200
    }

    companion object {
        const val RESULT_EXTRA_CODE = "code"
        const val RESULT_EXTRA_DATA = "data"
        const val RESULT_EXTRA_PROMPT_SHOWN = "prompt-shown"

        private const val REQUEST_CODE_SCREEN_CAPTURE_PERMISSION = 1

        fun getResultBroadcastAction(context: Context): String {
            return context.packageName + ".CAPTURE"
        }
    }
}
