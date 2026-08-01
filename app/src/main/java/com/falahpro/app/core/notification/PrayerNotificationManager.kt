package com.falahpro.app.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import com.falahpro.app.FalahPro
import com.falahpro.app.R
import com.falahpro.app.core.util.PrayerConstants
import com.falahpro.app.core.util.PrayerLog
import com.falahpro.app.core.util.PrayerReliabilityHelper
import com.falahpro.app.data.AzanMode

/**
 * Creates notification channels once and shows high-priority prayer notifications.
 */
class PrayerNotificationManager(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannelsCreated()
    }

    fun updateChannelsForMode(azanMode: AzanMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            PrayerConstants.NOTIFICATION_CHANNEL_ID,
            "Prayer Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Prayer time alerts"
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            // Full azan is played by AzanPlaybackService — avoid double audio from channel sound.
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun ensureChannelsCreated() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        if (notificationManager.getNotificationChannel(PrayerConstants.NOTIFICATION_CHANNEL_ID) == null) {
            updateChannelsForMode(AzanMode.FULL_SOUND)
        }

        if (notificationManager.getNotificationChannel(PrayerConstants.AZAN_PLAYBACK_CHANNEL_ID) == null) {
            val playbackChannel = NotificationChannel(
                PrayerConstants.AZAN_PLAYBACK_CHANNEL_ID,
                "Azan Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background azan audio playback"
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(playbackChannel)
        }
    }

    fun showPrayerNotification(prayerName: String, azanMode: AzanMode) {
        if (azanMode == AzanMode.SILENT) return

        if (!PrayerReliabilityHelper.areNotificationsEnabled(context)) {
            PrayerLog.warn("NOTIFICATIONS_DISABLED")
            return
        }

        updateChannelsForMode(azanMode)

        val launchIntent = Intent(context, FalahPro::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            prayerName.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = buildString {
            append("It's time for $prayerName prayer.\n")
            append("Tap to open Falah Pro.")
        }

        val builder = NotificationCompat.Builder(context, PrayerConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("🕌 $prayerName")
            .setContentText("It's time for $prayerName prayer")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)

        when (azanMode) {
            AzanMode.FULL_SOUND -> {
                builder.setSilent(true)
                // Full-screen intent only for Full Azan (heads-up / lock-screen wake).
                // Notify Only must not auto-launch the Activity.
                builder.setFullScreenIntent(contentPendingIntent, true)
            }
            AzanMode.NOTIFICATION_ONLY -> {
                // Short alert via defaults only; no full azan service.
            }
            AzanMode.SILENT -> return
        }

        notificationManager.notify(prayerName.hashCode(), builder.build())
        PrayerLog.event("NOTIFICATION_SHOWN", "prayer=$prayerName")
        PrayerLog.notificationPosted(prayerName)
    }

    companion object {
        @Volatile
        private var instance: PrayerNotificationManager? = null

        fun getInstance(context: Context): PrayerNotificationManager {
            return instance ?: synchronized(this) {
                instance ?: PrayerNotificationManager(context.applicationContext)
                    .also { instance = it }
            }
        }
    }
}
