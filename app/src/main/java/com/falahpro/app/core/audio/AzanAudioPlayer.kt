package com.falahpro.app.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.falahpro.app.R
import com.falahpro.app.core.prayer.PrayerRepository
import com.falahpro.app.core.util.PrayerLog
import com.falahpro.app.core.util.PrayerRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Plays azan with guaranteed WakeLock / MediaPlayer cleanup on every exit path.
 */
class AzanAudioPlayer(
    private val context: Context,
    private val onComplete: () -> Unit = {}
) {

    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var isPausedForFocus = false
    private var pausedForFocusAtMs = 0L
    private var ducked = false
    private var normalVolume = 1f
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        handleAudioFocusChange(change)
    }
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun play() {
        stop()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (!requestAudioFocus()) {
            PrayerLog.warn("AUDIO_FOCUS_DENIED")
            // Android 15 / HyperOS HardeningEnforcer can deny focus for a
            // background FGS (procState=4). Azan must still play on STREAM_ALARM.
        }

        acquireWakeLock()
        try {
            val player = createAzanMediaPlayer()
            if (player == null) {
                PrayerLog.error("MEDIAPLAYER_CREATE_NULL")
                releaseAll()
                mainHandler.post(onComplete)
                return
            }
            mediaPlayer = player
            PrayerRuntimeState.mediaPlayerActive = true
            player.setOnCompletionListener {
                PrayerLog.audioCompleted()
                scope.launch { PrayerRepository.getInstance(context).recordAzanEvent() }
                releaseAll()
                mainHandler.post(onComplete)
            }
            player.setOnErrorListener { _, what, extra ->
                PrayerLog.error("MEDIAPLAYER_ERROR", "what=$what extra=$extra")
                releaseAll()
                mainHandler.post(onComplete)
                true
            }
            PrayerLog.event("PLAYING_AZAN")
            player.start()
            PrayerLog.audioStarted()
        } catch (e: Exception) {
            PrayerLog.error("AUDIO_START_FAILED", e.message ?: "", e)
            releaseAll()
            mainHandler.post(onComplete)
        }
    }

    fun stop() {
        releaseAll()
    }

    private fun releaseAll() {
        isPausedForFocus = false
        ducked = false
        try {
            releasePlayer()
        } finally {
            try {
                abandonAudioFocus()
            } finally {
                releaseWakeLock()
            }
        }
        PrayerRuntimeState.mediaPlayerActive = false
        PrayerRuntimeState.audioFocusHeld = false
    }

    private fun releasePlayer() {
        mediaPlayer?.run {
            try {
                if (isPlaying) stop()
            } catch (_: Exception) {
            }
            try {
                release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FalahPro:AzanPlayback"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
        PrayerRuntimeState.wakeLockHeld = true
        PrayerLog.wakeLockAcquired()
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            try {
                if (it.isHeld) it.release()
            } catch (_: Exception) {
            }
        }
        wakeLock = null
        if (PrayerRuntimeState.wakeLockHeld) {
            PrayerRuntimeState.wakeLockHeld = false
            PrayerLog.wakeLockReleased()
        }
    }

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        PrayerLog.event("AUDIO_FOCUS_REQUEST", "usage=USAGE_ALARM gain=AUDIOFOCUS_GAIN_TRANSIENT")
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(azanAudioAttributes())
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        PrayerRuntimeState.audioFocusHeld = granted
        if (granted) {
            PrayerLog.event("AUDIO_FOCUS_GRANTED")
        }
        return granted
    }

    // AZAN-FIX-8: Pause/resume instead of permanently stopping Azan.
    private fun handleAudioFocusChange(change: Int) {
        val manager = audioManager
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseForFocus()
            AudioManager.AUDIOFOCUS_LOSS -> {
                val inCall = manager?.mode == AudioManager.MODE_IN_CALL ||
                    manager?.mode == AudioManager.MODE_IN_COMMUNICATION
                if (inCall) {
                    stop()
                    mainHandler.post(onComplete)
                } else {
                    pauseForFocus()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val player = mediaPlayer ?: return
                if (!ducked) {
                    normalVolume = 1f
                    ducked = true
                    player.setVolume(0.6f, 0.6f)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (ducked) {
                    mediaPlayer?.setVolume(normalVolume, normalVolume)
                    ducked = false
                }
                if (isPausedForFocus) {
                    val pausedFor = System.currentTimeMillis() - pausedForFocusAtMs
                    if (pausedFor > 5 * 60 * 1000L) {
                        stop()
                        mainHandler.post(onComplete)
                    } else {
                        try {
                            mediaPlayer?.start()
                            isPausedForFocus = false
                        } catch (_: Exception) {
                            stop()
                            mainHandler.post(onComplete)
                        }
                    }
                }
            }
        }
    }

    private fun pauseForFocus() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
                isPausedForFocus = true
                pausedForFocusAtMs = System.currentTimeMillis()
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
        focusRequest = null
    }

    private fun azanAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private fun createAzanMediaPlayer(): MediaPlayer? {
        val afd = context.resources.openRawResourceFd(R.raw.azan) ?: return null
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(azanAudioAttributes())
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            player.prepare()
            player
        } catch (_: Exception) {
            player.release()
            null
        } finally {
            afd.close()
        }
    }
}
