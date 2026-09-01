package com.falahpro.app.core.alarm

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.falahpro.app.core.util.PrayerConstants
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PrayerAlarmSchedulerTest {

    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: PrayerAlarmScheduler
    private val futureTimeMs = System.currentTimeMillis() + 60_000L

    @Before
    fun setUp() {
        alarmManager = mockk(relaxed = true)
        every { alarmManager.canScheduleExactAlarms() } returns true
        scheduler = PrayerAlarmScheduler(contextWithAlarmManager(alarmManager))
    }

    @After
    fun tearDown() {
        resetSchedulerSingleton()
    }

    @Test
    fun scheduleNextPrayerAsAlarmClock_usesSetAlarmClock_whenExactGranted() {
        // ARRANGE
        every { alarmManager.canScheduleExactAlarms() } returns true

        // ACT
        scheduler.scheduleNextPrayerAsAlarmClock("Fajr", futureTimeMs, dayOffset = 0)

        // ASSERT
        verify(exactly = 1) { alarmManager.setAlarmClock(any(), any()) }
        verify(exactly = 0) { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
    }

    @Test
    fun scheduleNextPrayerAsAlarmClock_fallsBackToInexact_whenExactDenied() {
        // ARRANGE
        every { alarmManager.canScheduleExactAlarms() } returns false

        // ACT
        val scheduled = scheduler.scheduleNextPrayerAsAlarmClock(
            "Fajr",
            futureTimeMs,
            dayOffset = 0
        )

        // ASSERT
        verify(exactly = 1) { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
        verify(exactly = 0) { alarmManager.setAlarmClock(any(), any()) }
        assertTrue(scheduled)
    }

    @Test
    fun scheduleExactAlarm_usesSetExactAndAllowWhileIdle_whenExactGranted() {
        // ARRANGE
        every { alarmManager.canScheduleExactAlarms() } returns true

        // ACT
        scheduler.scheduleExactAlarm("Dhuhr", futureTimeMs, dayOffset = 0)

        // ASSERT
        verify(exactly = 1) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any())
        }
    }

    @Test
    fun scheduleExactAlarm_usesInexactFallback_whenExactDenied() {
        // ARRANGE
        every { alarmManager.canScheduleExactAlarms() } returns false

        // ACT
        val scheduled = scheduler.scheduleExactAlarm("Asr", futureTimeMs, dayOffset = 0)

        // ASSERT
        verify(exactly = 1) { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
        assertTrue(scheduled)
    }

    @Test
    fun cancelAlarmsExcept_doesNotCancelAlarmsInKeepSet() {
        // ARRANGE
        val spyScheduler = spyk(scheduler)
        spyScheduler.scheduleExactAlarm("Fajr", futureTimeMs, dayOffset = 0)
        spyScheduler.scheduleExactAlarm("Dhuhr", futureTimeMs, dayOffset = 0)

        // ACT
        spyScheduler.cancelAlarmsExcept(keepRequestCodes = setOf(1001))

        // ASSERT
        verify(exactly = 0) { spyScheduler.cancelAlarm("Fajr", 0) }
        verify(exactly = 1) { spyScheduler.cancelAlarm("Dhuhr", 0) }
        assertTrue(PrayerConstants.requestCodeFor("Fajr", 0) == 1001)
        assertTrue(PrayerConstants.requestCodeFor("Dhuhr", 0) == 1002)
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

    private fun resetSchedulerSingleton() {
        try {
            val companion = Class.forName(
                "com.falahpro.app.core.alarm.PrayerAlarmScheduler\$Companion"
            )
            val field = companion.getDeclaredField("instance")
            field.isAccessible = true
            field.set(PrayerAlarmScheduler, null)
        } catch (_: Exception) {
            try {
                val field = PrayerAlarmScheduler::class.java.getDeclaredField("instance")
                field.isAccessible = true
                field.set(null, null)
            } catch (_: Exception) {
            }
        }
    }
}
