package com.falahpro.app.core.scheduler

import android.app.AlarmManager
import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.ContextWrapper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.falahpro.app.core.alarm.PrayerAlarmScheduler
import com.falahpro.app.core.audio.AzanPlaybackService
import com.falahpro.app.core.notification.PrayerNotificationManager
import com.falahpro.app.core.prayer.PrayerRepository
import com.falahpro.app.data.AzanMode
import com.falahpro.app.data.DataStoreManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PrayerEngineTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: PrayerRepository
    private lateinit var scheduler: PrayerAlarmScheduler
    private lateinit var notificationManager: PrayerNotificationManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clearAcceptedInMemory()
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        alarmManager = mockk(relaxed = true)
        context = contextWithAlarmManager(alarmManager)

        mockkObject(PrayerRepository)
        every { PrayerRepository.getInstance(any()) } returns repository
        mockkObject(PrayerAlarmScheduler)
        every { PrayerAlarmScheduler.getInstance(any()) } returns scheduler
        mockkObject(PrayerNotificationManager)
        every { PrayerNotificationManager.getInstance(any()) } returns notificationManager
        mockkObject(DataStoreManager)
        every { DataStoreManager.getAzanMode(any()) } returns flowOf(AzanMode.FULL_SOUND)
        mockkObject(AzanPlaybackService)
        every { AzanPlaybackService.start(any(), any()) } just runs
        mockkObject(PrayerEngine)
        coEvery { PrayerEngine.rescheduleAllSync(any(), any()) } just runs
        every { PrayerEngine.rescheduleAll(any(), any()) } just runs

        coEvery { repository.isPrayerFiredToday(any()) } returns false
        coEvery { repository.getPrayerTimesForDate(any()) } returns emptyMap()
        coEvery { repository.tryMarkPrayerFiredToday(any()) } returns true
        coEvery { repository.recordReceiverEvent(any()) } returns Unit
        coEvery { repository.recordNotificationEvent(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
        clearAcceptedInMemory()
        Dispatchers.resetMain()
    }

    @Test
    fun onPrayerAlarmFiredSync_silentMode_skipsAudio_keepsSchedule() = runTest(dispatcher) {
        // ARRANGE
        every { DataStoreManager.getAzanMode(any()) } returns flowOf(AzanMode.SILENT)
        coEvery { repository.isPrayerFiredToday("Fajr") } returns false
        val futureTimeMs = System.currentTimeMillis() + 60_000L

        // ACT
        PrayerEngine.onPrayerAlarmFiredSync(context, "Fajr", futureTimeMs, 0)

        // ASSERT
        verify(exactly = 0) { AzanPlaybackService.start(any(), any()) }
        coVerify { PrayerEngine.rescheduleAllSync(any(), any()) }
        // Kotlin still runs the try/finally, which persists the fired flag.
        coVerify { repository.tryMarkPrayerFiredToday("Fajr") }
        verify(exactly = 0) { scheduler.cancelAlarmsExcept(any()) }
    }

    @Test
    fun onPrayerAlarmFiredSync_duplicateAlarm_ignored() = runTest(dispatcher) {
        // ARRANGE
        markAcceptedInMemory("Fajr")
        val futureTimeMs = System.currentTimeMillis() + 60_000L

        // ACT
        PrayerEngine.onPrayerAlarmFiredSync(context, "Fajr", futureTimeMs, 0)

        // ASSERT
        verify(exactly = 0) { AzanPlaybackService.start(any(), any()) }
        verify(exactly = 0) { notificationManager.showPrayerNotification(any(), any(), any()) }
        coVerify(exactly = 0) { repository.tryMarkPrayerFiredToday(any()) }
    }

    @Test
    fun onPrayerAlarmFiredSync_staleAlarm_droppedWhenPastNextPrayer() = runTest(dispatcher) {
        // ARRANGE
        val staleTimeMs = System.currentTimeMillis() - 6L * 60L * 60L * 1000L
        coEvery { repository.getPrayerTimesForDate(LocalDate.now()) } returns mapOf(
            "Dhuhr" to LocalTime.MIDNIGHT
        )

        // ACT
        PrayerEngine.onPrayerAlarmFiredSync(context, "Fajr", staleTimeMs, 0)

        // ASSERT
        verify(exactly = 0) { AzanPlaybackService.start(any(), any()) }
        verify(exactly = 0) { notificationManager.showPrayerNotification(any(), any(), any()) }
        coVerify { PrayerEngine.rescheduleAllSync(any(), match { it.startsWith("stale_alarm_") }) }
    }

    @Test
    fun onPrayerAlarmFiredSync_lateAlarm_playsIfWithinPrayerWindow() = runTest(dispatcher) {
        // ARRANGE
        val lateTimeMs = System.currentTimeMillis() - 45L * 60L * 1000L
        val dhuhrTime = upcomingTimeToday(minutesAhead = 30)
        coEvery { repository.getPrayerTimesForDate(LocalDate.now()) } returns mapOf(
            "Dhuhr" to dhuhrTime
        )
        coEvery { repository.isPrayerFiredToday("Fajr") } returns false
        every { DataStoreManager.getAzanMode(any()) } returns flowOf(AzanMode.FULL_SOUND)

        // ACT
        PrayerEngine.onPrayerAlarmFiredSync(context, "Fajr", lateTimeMs, 0)

        // ASSERT
        verify { AzanPlaybackService.start(any(), eq("Fajr")) }
        verify {
            notificationManager.showPrayerNotification(
                "Fajr",
                AzanMode.FULL_SOUND,
                delayMinutes = 45
            )
        }
        coVerify { repository.tryMarkPrayerFiredToday("Fajr") }
    }

    @Test
    fun onPrayerAlarmFiredSync_fgsRetry_skipsWhenAlreadyFiredInDataStore() = runTest(dispatcher) {
        // ARRANGE
        coEvery { repository.isPrayerFiredToday("Maghrib") } returns true
        val timeMs = System.currentTimeMillis()

        // ACT
        PrayerEngine.onPrayerAlarmFiredSync(
            context,
            "Maghrib",
            timeMs,
            0,
            isFgsRetry = true
        )

        // ASSERT
        verify(exactly = 0) { AzanPlaybackService.start(any(), any()) }
        coVerify(exactly = 0) { repository.tryMarkPrayerFiredToday(any()) }
        coVerify {
            PrayerEngine.rescheduleAllSync(any(), eq("fgs_retry_already_fired_Maghrib"))
        }
    }

    @Test
    fun onPrayerAlarmFiredSync_fgsRetry_playsWhenNotFiredInDataStore() = runTest(dispatcher) {
        // ARRANGE
        coEvery { repository.isPrayerFiredToday("Maghrib") } returns false
        every { DataStoreManager.getAzanMode(any()) } returns flowOf(AzanMode.FULL_SOUND)
        val timeMs = System.currentTimeMillis()

        // ACT
        PrayerEngine.onPrayerAlarmFiredSync(
            context,
            "Maghrib",
            timeMs,
            0,
            isFgsRetry = true
        )

        // ASSERT
        verify { AzanPlaybackService.start(any(), eq("Maghrib")) }
        coVerify(exactly = 0) { repository.tryMarkPrayerFiredToday(any()) }
    }

    @Test
    fun onPrayerAlarmFiredSync_fgsBlocked_schedulesRetryAlarm() = runTest(dispatcher) {
        // ARRANGE
        every { DataStoreManager.getAzanMode(any()) } returns flowOf(AzanMode.FULL_SOUND)
        every { AzanPlaybackService.start(any(), any()) } throws
            ForegroundServiceStartNotAllowedException("blocked")
        val timeMs = System.currentTimeMillis()
        val triggerSlot = slot<Long>()
        every {
            alarmManager.setAndAllowWhileIdle(any(), capture(triggerSlot), any())
        } just runs

        // ACT
        PrayerEngine.onPrayerAlarmFiredSync(context, "Isha", timeMs, 0)

        // ASSERT
        verify { notificationManager.showMissedAzanFallback("Isha") }
        verify { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, any(), any()) }
        val expected = System.currentTimeMillis() + 60_000L
        assertTrue(abs(triggerSlot.captured - expected) <= 5_000L)
    }

    private fun contextWithAlarmManager(alarmManager: AlarmManager): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? {
                return if (name == Context.ALARM_SERVICE) alarmManager
                else super.getSystemService(name)
            }
        }
    }

    private fun upcomingTimeToday(minutesAhead: Int): LocalTime {
        val later = LocalDateTime.now().plusMinutes(minutesAhead.toLong())
        return if (later.toLocalDate() == LocalDate.now()) {
            later.toLocalTime()
        } else {
            LocalTime.MAX
        }
    }

    private fun markAcceptedInMemory(prayerName: String) {
        val today = LocalDate.now().toString()
        val dateField = PrayerEngine.javaClass.getDeclaredField("acceptedDate")
        dateField.isAccessible = true
        dateField.set(PrayerEngine, today)
        val setField = PrayerEngine.javaClass.getDeclaredField("acceptedPrayers")
        setField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (setField.get(PrayerEngine) as MutableSet<String>).add(prayerName)
    }

    private fun clearAcceptedInMemory() {
        val dateField = PrayerEngine.javaClass.getDeclaredField("acceptedDate")
        dateField.isAccessible = true
        dateField.set(PrayerEngine, "")
        val setField = PrayerEngine.javaClass.getDeclaredField("acceptedPrayers")
        setField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (setField.get(PrayerEngine) as MutableSet<String>).clear()
    }
}
