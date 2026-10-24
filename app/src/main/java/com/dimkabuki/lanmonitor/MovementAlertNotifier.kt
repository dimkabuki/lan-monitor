package com.dimkabuki.lanmonitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

internal enum class MovementNotificationOutcome {
    POSTED,
    PERMISSION_DENIED,
    APP_NOTIFICATIONS_DISABLED,
    CHANNEL_DISABLED,
    FAILED,
}

/** Posts generic movement alerts; it never includes frame contents or captured media. */
internal object MovementAlertNotifier {
    private const val CHANNEL_ID = "movement_alerts"
    private const val NOTIFICATION_ID = 8766

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Movement alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Alerts when the Monitor detects movement in the paired Camera video."
                    setShowBadge(false)
                },
            )
        }
    }

    fun post(context: Context): MovementNotificationOutcome {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return MovementNotificationOutcome.PERMISSION_DENIED
        }

        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return MovementNotificationOutcome.FAILED
        if (!manager.areNotificationsEnabled()) {
            return MovementNotificationOutcome.APP_NOTIFICATIONS_DISABLED
        }

        return try {
            ensureChannel(context)
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
            ) {
                return MovementNotificationOutcome.CHANNEL_DISABLED
            }

            val openAppIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Movement detected")
                .setContentText("Movement was detected in the paired Camera video.")
                .setCategory(Notification.CATEGORY_EVENT)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setContentIntent(openAppIntent)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
            MovementNotificationOutcome.POSTED
        } catch (_: SecurityException) {
            MovementNotificationOutcome.PERMISSION_DENIED
        } catch (_: RuntimeException) {
            MovementNotificationOutcome.FAILED
        }
    }
}
