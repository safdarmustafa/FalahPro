package com.falahpro.app.prayer

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.falahpro.app.core.alarm.PrayerAlarmScheduler
import com.falahpro.app.core.scheduler.PrayerEngine
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ExactAlarmRevocationReceiverTest {

    private lateinit var context: Context
    private lateinit var scheduler: PrayerAlarmScheduler
    private lateinit var receiver: ExactAlarmRevocationReceiver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scheduler = mockk(relaxed = true)
        receiver = ExactAlarmRevocationReceiver()

        mockkObject(PrayerAlarmScheduler)
        every { PrayerAlarmScheduler.getInstance(any()) } returns scheduler
        mockkObject(PrayerEngine)
        every { PrayerEngine.rescheduleAll(any(), any()) } just runs
        every { PrayerEngine.fallbackToInexact(any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun grantAction_callsRescheduleAll() {
        // ARRANGE
        every { scheduler.canScheduleExactAlarms() } returns true
        val intent = Intent("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")

        // ACT
        receiver.onReceive(context, intent)

        // ASSERT
        verify {
            PrayerEngine.rescheduleAll(any(), eq("exact_alarm_re_granted"))
        }
    }

    @Test
    fun revokeAction_callsFallbackToInexact() {
        // ARRANGE
        every { scheduler.canScheduleExactAlarms() } returns false
        val intent = Intent("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")

        // ACT
        receiver.onReceive(context, intent)

        // ASSERT
        verify { PrayerEngine.fallbackToInexact(any()) }
        verify(exactly = 0) { PrayerEngine.rescheduleAll(any(), any()) }
    }
}
