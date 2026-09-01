package com.falahpro.app.core.audio

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.falahpro.app.R
import com.falahpro.app.core.util.PrayerConstants
import com.falahpro.app.core.util.PrayerLog
import com.falahpro.app.core.util.PrayerRuntimeState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service for reliable full-length azan playback.
 */
class AzanPlaybackService : Service() {

    private var audioPlayer: AzanAudioPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prayerName = intent?.getStringExtra(EXTRA_PRAYER_NAME) ?: "Prayer"
        // AZAN-FIX-4: startForeground must run in the first seconds, before any other work.
        val notification = buildPlaybackNotification(prayerName)
        // AZAN-FIX-3: Specify mediaPlayback type for Android 10+ compliance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!isPlaying.compareAndSet(false, true)) {
            PrayerLog.warn("SERVICE_DUPLICATE_START")
            return START_NOT_STICKY
        }

        PrayerRuntimeState.foregroundServiceRunning = true
        PrayerLog.serviceStarted(prayerName)

        audioPlayer = AzanAudioPlayer(applicationContext) {
            isPlaying.set(false)
            PrayerRuntimeState.foregroundServiceRunning = false
            PrayerLog.serviceStopped()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        audioPlayer?.play()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isPlaying.set(false)
        PrayerRuntimeState.foregroundServiceRunning = false
        audioPlayer?.stop()
        audioPlayer = null
        PrayerLog.serviceStopped()
        super.onDestroy()
    }

    private fun buildPlaybackNotification(prayerName: String) =
        NotificationCompat.Builder(this, PrayerConstants.AZAN_PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Playing Azan")
            .setContentText("$prayerName prayer")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    companion object {
        private const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        private const val NOTIFICATION_ID = 9001
        private val isPlaying = AtomicBoolean(false)

        fun start(context: Context, prayerName: String) {
            val intent = Intent(context, AzanPlaybackService::class.java).apply {
                putExtra(EXTRA_PRAYER_NAME, prayerName)
            }
            // AZAN-FIX-4: Caller must catch ForegroundServiceStartNotAllowedException.
            context.startForegroundService(intent)
        }
    }
}
