package com.falahpro.app.core.util

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.falahpro.app.core.alarm.PrayerAlarmScheduler
import com.falahpro.app.data.DataStoreManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PrayerReliabilityHelperTest {

    private lateinit var context: Context
    private lateinit var scheduler: PrayerAlarmScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scheduler = mockk(relaxed = true)
        mockkObject(PrayerAlarmScheduler)
        every { PrayerAlarmScheduler.getInstance(any()) } returns scheduler
        mockkObject(DataStoreManager)
        coEvery { DataStoreManager.getExactAlarmPromptLastShownMs(any()) } returns 0L
        coEvery { DataStoreManager.setExactAlarmPromptLastShownMs(any(), any()) } just runs
        coEvery { DataStoreManager.getOemWizardShownMs(any()) } returns 0L
        coEvery { DataStoreManager.setOemWizardShownMs(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
        ShadowBuild.reset()
    }

    @Test
    fun shouldPromptExactAlarmSettings_falseIfAlreadyGranted() = runTest {
        // ARRANGE
        every { scheduler.canScheduleExactAlarms() } returns true

        // ACT
        val result = PrayerReliabilityHelper.shouldPromptExactAlarmSettings(context)

        // ASSERT
        assertFalse(result)
    }

    @Test
    fun shouldPromptExactAlarmSettings_falseIfShownWithin24h() = runTest {
        // ARRANGE
        every { scheduler.canScheduleExactAlarms() } returns false
        val twoHoursAgo = System.currentTimeMillis() - 2L * 60L * 60L * 1000L
        coEvery { DataStoreManager.getExactAlarmPromptLastShownMs(any()) } returns twoHoursAgo

        // ACT
        val result = PrayerReliabilityHelper.shouldPromptExactAlarmSettings(context)

        // ASSERT
        assertFalse(result)
    }

    @Test
    fun shouldPromptExactAlarmSettings_trueIfDeniedAnd24hPassed() = runTest {
        // ARRANGE
        every { scheduler.canScheduleExactAlarms() } returns false
        val twentyFiveHoursAgo = System.currentTimeMillis() - 25L * 60L * 60L * 1000L
        coEvery { DataStoreManager.getExactAlarmPromptLastShownMs(any()) } returns twentyFiveHoursAgo

        // ACT
        val result = PrayerReliabilityHelper.shouldPromptExactAlarmSettings(context)

        // ASSERT
        assertTrue(result)
        coVerify { DataStoreManager.setExactAlarmPromptLastShownMs(any(), any()) }
    }

    @Test
    fun shouldShowOemWizard_falseIfAlreadyShown() = runTest {
        // ARRANGE
        val oneDayAgo = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        coEvery { DataStoreManager.getOemWizardShownMs(any()) } returns oneDayAgo

        // ACT
        val result = PrayerReliabilityHelper.shouldShowOemWizard(context)

        // ASSERT
        assertFalse(result)
    }

    @Test
    fun shouldShowOemWizard_trueOnFirstCall_falseOnSecond() = runTest {
        // ARRANGE
        var shownMs = 0L
        coEvery { DataStoreManager.getOemWizardShownMs(any()) } answers { shownMs }
        coEvery { DataStoreManager.setOemWizardShownMs(any(), any()) } answers {
            shownMs = args[1] as Long
        }

        // ACT
        val first = PrayerReliabilityHelper.shouldShowOemWizard(context)
        val second = PrayerReliabilityHelper.shouldShowOemWizard(context)

        // ASSERT
        assertTrue(first)
        assertFalse(second)
        coVerify(exactly = 1) { DataStoreManager.setOemWizardShownMs(any(), any()) }
    }

    @Test
    fun isXiaomiDevice_trueForXiaomiRedmiPoco_falseForSamsung() {
        // ARRANGE / ACT / ASSERT
        ShadowBuild.setManufacturer("Xiaomi")
        ShadowBuild.setBrand("xiaomi")
        assertTrue(PrayerReliabilityHelper.isXiaomiDevice())

        ShadowBuild.setManufacturer("unknown")
        ShadowBuild.setBrand("redmi")
        assertTrue(PrayerReliabilityHelper.isXiaomiDevice())

        ShadowBuild.setManufacturer("unknown")
        ShadowBuild.setBrand("poco")
        assertTrue(PrayerReliabilityHelper.isXiaomiDevice())

        ShadowBuild.setManufacturer("Samsung")
        ShadowBuild.setBrand("samsung")
        assertFalse(PrayerReliabilityHelper.isXiaomiDevice())
    }
}
