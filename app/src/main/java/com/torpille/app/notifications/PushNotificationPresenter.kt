package com.torpille.app.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.torpille.app.MainActivity
import com.torpille.app.R

object PushNotificationPresenter {
    fun show(context: Context, title: String, body: String, communityId: String?, destination: String?, notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()) {
        if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val channelId = NotificationChannels.ALERTS
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        NotificationChannels.create(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("notificationCommunityId", communityId)
            putExtra("destination", destination ?: "communityDiscussion")
            data = android.net.Uri.Builder().scheme("torpille").authority("notification")
                .appendQueryParameter("cid", communityId).appendQueryParameter("destination", destination).build()
        }
        val pi = PendingIntent.getActivity(
            context,
            communityId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(notificationId, notif)
    }
}
