package com.falahpro.app.core.audio

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AzanAudioPlayerTest {

    private lateinit var context: Context
    private val completed = AtomicBoolean(false)

    @Before
    fun setUp() {
        completed.set(false)
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun audioFocusLossTransient_pausesPlayer_doesNotStop() {
        // ARRANGE
        val mediaPlayer = startedMediaPlayer()
        val player = wiredPlayer(mediaPlayer)
        setField(player, "isPausedForFocus", false)

        // ACT
        focusListener(player).onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

        // ASSERT
        val shadow = Shadows.shadowOf(mediaPlayer)
        assertTrue(shadow.state == ShadowMediaPlayer.State.PAUSED)
        assertTrue(shadow.state != ShadowMediaPlayer.State.STOPPED)
        assertTrue(shadow.state != ShadowMediaPlayer.State.END)
        assertTrue(getField(player, "isPausedForFocus") as Boolean)
    }

    @Test
    fun audioFocusGain_resumesIfPausedUnderFiveMinutes() {
        // ARRANGE
        val mediaPlayer = startedMediaPlayer()
        val player = wiredPlayer(mediaPlayer)
        mediaPlayer.pause()
        setField(player, "isPausedForFocus", true)
        setField(player, "pausedForFocusAtMs", System.currentTimeMillis() - 2 * 60 * 1000L)

        // ACT
        focusListener(player).onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        // ASSERT
        assertTrue(Shadows.shadowOf(mediaPlayer).state == ShadowMediaPlayer.State.STARTED)
        assertFalse(getField(player, "isPausedForFocus") as Boolean)
    }

    @Test
    fun audioFocusGain_stopsIfPausedOverFiveMinutes() {
        // ARRANGE
        val mediaPlayer = startedMediaPlayer()
        val player = wiredPlayer(mediaPlayer)
        mediaPlayer.pause()
        setField(player, "isPausedForFocus", true)
        setField(player, "pausedForFocusAtMs", System.currentTimeMillis() - 6 * 60 * 1000L)

        // ACT
        focusListener(player).onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // ASSERT
        val shadow = Shadows.shadowOf(mediaPlayer)
        assertTrue(
            shadow.state == ShadowMediaPlayer.State.STOPPED ||
                shadow.state == ShadowMediaPlayer.State.END
        )
        assertTrue(completed.get())
        assertTrue(shadow.state != ShadowMediaPlayer.State.STARTED)
    }

    @Test
    fun audioFocusLoss_duringCall_stopsAzanPermanently() {
        // ARRANGE
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_CALL
        val mediaPlayer = startedMediaPlayer()
        val player = wiredPlayer(mediaPlayer)

        // ACT
        focusListener(player).onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // ASSERT
        val shadow = Shadows.shadowOf(mediaPlayer)
        assertTrue(
            shadow.state == ShadowMediaPlayer.State.STOPPED ||
                shadow.state == ShadowMediaPlayer.State.END
        )
        assertTrue(completed.get())
        assertFalse(getField(player, "isPausedForFocus") as Boolean)
    }

    @Test
    fun audioFocusLossCanDuck_lowersVolumeToSixtyPercent() {
        // ARRANGE
        val mediaPlayer = startedMediaPlayer()
        val player = wiredPlayer(mediaPlayer)
        setField(player, "ducked", false)

        // ACT
        focusListener(player).onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        // ASSERT
        val shadow = Shadows.shadowOf(mediaPlayer)
        assertTrue(kotlin.math.abs(shadow.leftVolume - 0.6f) < 0.01f)
        assertTrue(kotlin.math.abs(shadow.rightVolume - 0.6f) < 0.01f)
        assertTrue(shadow.state != ShadowMediaPlayer.State.STOPPED)
    }

    @Test
    fun wakeLock_acquiredOnPlay_releasedOnStop() {
        // ARRANGE
        val player = AzanAudioPlayer(context) { completed.set(true) }

        // ACT
        acquireWakeLock(player)
        val wakeLock = getField(player, "wakeLock") as PowerManager.WakeLock
        assertTrue(wakeLock.isHeld)
        player.stop()

        // ASSERT
        assertFalse(wakeLock.isHeld)
        assertTrue(getField(player, "wakeLock") == null)
    }

    private fun startedMediaPlayer(): MediaPlayer {
        val src = "http://falah.local/azan.mp3"
        ShadowMediaPlayer.addMediaInfo(
            org.robolectric.shadows.util.DataSource.toDataSource(src),
            ShadowMediaPlayer.MediaInfo(10_000, 1)
        )
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(src)
        mediaPlayer.prepare()
        mediaPlayer.start()
        return mediaPlayer
    }

    private fun wiredPlayer(mediaPlayer: MediaPlayer): AzanAudioPlayer {
        val player = AzanAudioPlayer(context) { completed.set(true) }
        setField(player, "mediaPlayer", mediaPlayer)
        setField(
            player,
            "audioManager",
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        )
        return player
    }

    private fun acquireWakeLock(player: AzanAudioPlayer) {
        val method = AzanAudioPlayer::class.java.getDeclaredMethod("acquireWakeLock")
        method.isAccessible = true
        method.invoke(player)
    }

    private fun focusListener(player: AzanAudioPlayer): AudioManager.OnAudioFocusChangeListener {
        val field = AzanAudioPlayer::class.java.getDeclaredField("focusListener")
        field.isAccessible = true
        return field.get(player) as AudioManager.OnAudioFocusChangeListener
    }

    private fun getField(player: AzanAudioPlayer, name: String): Any? {
        val field = AzanAudioPlayer::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(player)
    }

    private fun setField(player: AzanAudioPlayer, name: String, value: Any) {
        val field = AzanAudioPlayer::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(player, value)
    }
}
