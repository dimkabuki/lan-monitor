package com.dimkabuki.lanmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder

/**
 * Keeps the process in foreground priority for a user-started Camera or Monitor session.
 *
 * The actual media controller remains in the active UI session in this implementation.
 * Starting is only done in response to visible user interaction and granted runtime
 * permissions; stopping always requests session cleanup first.
 */
class SessionForegroundService : Service() {
    private var retainedController: LanWebRtcSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ActiveSessionRegistry.requestStop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val role = intent?.getStringExtra(EXTRA_ROLE)
            ?.let { name -> runCatching { AppRole.valueOf(name) }.getOrNull() }
        if (role == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val controller = SessionControllerStore.findForService(role)
        if (controller == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (retainedController !== controller) {
            if (!SessionControllerStore.retainService(controller)) {
                ActiveSessionRegistry.reportFailure(
                    "Another Lan Monitor session is already active. Stop it before starting a different role.",
                )
                stopSelf(startId)
                return START_NOT_STICKY
            }
            val previous = retainedController
            retainedController = controller
            previous?.let(SessionControllerStore::releaseService)
        }

        return try {
            startForeground(NOTIFICATION_ID, buildNotification(role), foregroundServiceType(role))
            START_NOT_STICKY
        } catch (error: RuntimeException) {
            ActiveSessionRegistry.reportFailure(
                "Could not keep the session active in Android background mode: " +
                    "${error.message ?: error.javaClass.simpleName}. Keep Lan Monitor open and check its permissions.",
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        ActiveSessionRegistry.requestStop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        retainedController?.let(SessionControllerStore::releaseService)
        retainedController = null
        super.onDestroy()
    }

    private fun foregroundServiceType(role: AppRole): Int = when (role) {
        AppRole.CAMERA ->
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        AppRole.MONITOR -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
    }

    private fun buildNotification(role: AppRole): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP_SESSION,
            Intent(this, SessionForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val description = when (role) {
            AppRole.CAMERA -> "Camera and microphone session is active on the local network."
            AppRole.MONITOR -> "Receiving media from the paired Camera on the local network."
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Lan Monitor — ${role.label} active")
            .setContentText(description)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Stop",
                    stopIntent,
                ).build(),
            )
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Active Lan Monitor session",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ongoing status and stop control for an active Camera or Monitor session."
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "active_session"
        private const val NOTIFICATION_ID = 8765
        private const val REQUEST_OPEN_APP = 1
        private const val REQUEST_STOP_SESSION = 2
        private const val EXTRA_ROLE = "com.dimkabuki.lanmonitor.extra.ROLE"
        private const val ACTION_STOP = "com.dimkabuki.lanmonitor.action.STOP_SESSION"

        fun start(context: Context, role: AppRole): String? = try {
            context.applicationContext.startForegroundService(
                Intent(context.applicationContext, SessionForegroundService::class.java)
                    .putExtra(EXTRA_ROLE, role.name),
            )
            null
        } catch (error: RuntimeException) {
            "Could not start the Android foreground service: " +
                "${error.message ?: error.javaClass.simpleName}. Keep the app visible and check permissions."
        }

        fun stop(context: Context) {
            runCatching {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, SessionForegroundService::class.java),
                )
            }
        }
    }
}
