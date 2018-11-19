package org.mozilla.scryer.notification

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.mozilla.scryer.MainActivity
import org.mozilla.scryer.R

class ScryerMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        // This happens when the app is running in foreground, and the user clicks on the push
        // notification with payload "PUSH_OPEN_URL"
        remoteMessage?.let {

            val intent = Intent()
            // check if message contains data payload
            if (it.data != null) {
                intent.putExtra(PUSH_OPEN_URL, it.data[PUSH_OPEN_URL])
            }
            val title = it.notification?.title
            val body = it.notification?.body

            // We have a remote message from gcm, let the child decides what to do with it.
            onRemoteMessage(intent, title, body)
        }
    }

    private fun onRemoteMessage(intent: Intent, title: String?, body: String?) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ID_FCM_NOTIFICATION, buildNotification(intent, title, body))
    }

    private fun buildNotification(intent: Intent, title: String?, body: String?): Notification {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createMessageChannel()
        } else {
            ""
        }

        intent.setClass(applicationContext, MainActivity::class.java)
        intent.flags = intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK
        val tapPendingIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_ONE_SHOT)

        return NotificationCompat.Builder(this, channelId)
                .setCategory(Notification.CATEGORY_PROMO)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setColor(ContextCompat.getColor(this, R.color.foreground_notification))
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(tapPendingIntent)
                .setAutoCancel(true)
                .build()
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createMessageChannel(): String {
        val channelId = "message_channel"
        val channelName = "ScreenshotPlus Message"
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
        return channelId
    }

    companion object {
        private const val ID_FCM_NOTIFICATION = 9489
        const val PUSH_OPEN_URL = "push_open_url"
    }
}
