// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import io.github.leostumpf.gpstools.MainActivity
import io.github.leostumpf.gpstools.R

/**
 * Keeps the app's location access alive while it is not on screen.
 *
 * It does no work of its own: the tracking session in the ViewModel carries on as before.
 * The service exists so Android keeps the process running and treats location use as
 * "while in use" — started from the visible app, announced by a notification the user can
 * always see and stop from.
 */
class BackgroundTrackingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            BackgroundMode.setActive(false)
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        BackgroundMode.setActive(true)
        // Not sticky: if Android kills the process, the session it served is gone too.
        return START_NOT_STICKY
    }

    /** Swiping the app away from recents ends it, background mode included. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        BackgroundMode.setActive(false)
        stopSelf()
    }

    override fun onDestroy() {
        BackgroundMode.setActive(false)
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Background mode", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while GPS Tools keeps the GNSS receiver running in the background."
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("GPS Tools is running in the background")
        .setContentText("The GNSS receiver stays on. Tap Stop to release it.")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            0,
            "Stop",
            PendingIntent.getService(
                this,
                1,
                Intent(this, BackgroundTrackingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private companion object {
        const val CHANNEL_ID = "background_mode"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "io.github.leostumpf.gpstools.STOP_BACKGROUND"
    }
}
