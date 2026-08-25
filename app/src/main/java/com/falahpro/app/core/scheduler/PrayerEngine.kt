package com.falahpro.app.core.scheduler

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.falahpro.app.core.alarm.ExpectedAlarm
import com.falahpro.app.core.alarm.PrayerAlarmRegistry
import com.falahpro.app.core.alarm.PrayerAlarmScheduler
import com.falahpro.app.core.prayer.PrayerCalculator
import com.falahpro.app.core.prayer.PrayerRepository
import com.falahpro.app.core.util.PrayerConstants
import com.falahpro.app.core.util.PrayerLog
import com.falahpro.app.core.util.PrayerRuntimeState
import com.falahpro.app.data.AzanMode
import com.falahpro.app.data.DataStoreManager
import com.falahpro.app.location.getUserLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/**
 * Central orchestrator — all alarm mutations are mutex-serialized and diff-based.
 */
object PrayerEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rescheduleMutex = Mutex()
    private val fireHandlerMutex = Mutex()

    /**
     * Process-local accept set for today's prayers. Written as soon as a delivery is
     * accepted so a concurrent reschedule cannot re-arm the same slot, and so a second
     * receiver cannot start Azan. Persisted fired flags are written only after notify
     * and optional Azan start have been attempted.
     */
    private val acceptedLock = Any()
    private var acceptedDate: String = ""
    private val acceptedPrayers = mutableSetOf<String>()

    private fun isAcceptedInMemory(prayerName: String): Boolean {
        val today = LocalDate.now().toString()
        synchronized(acceptedLock) {
            if (acceptedDate != today) return false
            return prayerName in acceptedPrayers
        }
    }

    private fun markAcceptedInMemory(prayerName: String) {
        val today = LocalDate.now().toString()
        synchronized(acceptedLock) {
            if (acceptedDate != today) {
                acceptedPrayers.clear()
                acceptedDate = today
            }
            acceptedPrayers.add(prayerName)
        }
    }

    fun bootstrap(context: Context) {
        engineScope.launch {
            bootstrapSync(context)
            // After base alarms are set from cached/default location, refresh from live GPS
            // (if permitted). Runs outside the bootstrap mutex to avoid re-entrant locking.
            syncLocationIfPermittedSync(context)
        }
    }

    suspend fun bootstrapSync(context: Context) {
        rescheduleMutex.withLock {
            val appContext = context.applicationContext
            PrayerLog.engineBoot()
            DataStoreManager.checkAndResetIfNewDay(appContext)
            PrayerRepository.getInstance(appContext).ensureFiredPrayersDateCurrent()
            PrayerRepository.getInstance(appContext).ensureTodayTimesCalculated()
            syncAlarmsInternal(appContext, reason = "application_onCreate")
        }
    }

    /** Re-verify alarms when returning from system settings (e.g. exact-alarm grant). */
    fun verifyOnResume(context: Context) {
        engineScope.launch {
            rescheduleMutex.withLock {
                syncAlarmsInternal(context.applicationContext, reason = "resume_verify")
            }
        }
    }

    fun rescheduleAll(context: Context, reason: String) {
        engineScope.launch { rescheduleAllSync(context, reason) }
    }

    suspend fun rescheduleAllSync(context: Context, reason: String) {
        rescheduleMutex.withLock {
            syncAlarmsInternal(context.applicationContext, reason)
        }
    }

    /**
     * Diff-based alarm sync: upsert expected alarms, cancel only obsolete slots.
     * Never cancels all alarms before scheduling — no zero-alarm window.
     * Always re-registers expected alarms (PendingIntent existence ≠ live AlarmManager registration).
     */
    private suspend fun syncAlarmsInternal(context: Context, reason: String) {
        PrayerLog.event("SYNC_ALARMS_STARTED", "reason=$reason")
        PrayerLog.rescheduleStarted(reason)

        val repository = PrayerRepository.getInstance(context)
        val scheduler = PrayerAlarmScheduler.getInstance(context)
        val registry = PrayerAlarmRegistry.getInstance(context)
        val notificationManager = com.falahpro.app.core.notification.PrayerNotificationManager
            .getInstance(context)

        if (!scheduler.canScheduleExactAlarms()) {
            PrayerLog.exactAlarmDenied()
            repository.recordReschedule(reason, 0, 0, 0, null, null, emptyList())
            return
        }

        val azanMode = DataStoreManager.getAzanMode(context).first()
        notificationManager.updateChannelsForMode(azanMode)

        if (azanMode == AzanMode.SILENT) {
            PrayerLog.warn("AZAN_MODE_SILENT", "all alarms cancelled")
            scheduler.cancelAlarmsExcept(emptySet())
            registry.clear()
            PrayerLog.rescheduleCompleted(0, 0, 0)
            repository.recordReschedule(reason, 0, 0, 0, null, null, emptyList())
            return
        }

        repository.ensureTodayTimesCalculated()
        val expected = buildExpectedAlarms(repository)
        val stored = registry.load()
        val keepCodes = expected.map { it.requestCode }.toSet()

        var scheduledCount = 0
        var verifiedCount = 0
        var repairedCount = 0
        var nextPrayerName: String? = null
        var nextAlarmAtMillis: Long? = null
        val now = System.currentTimeMillis()

        for (alarm in expected) {
            val hadPendingIntent = scheduler.isAlarmPending(alarm.prayerName, alarm.dayOffset)
            val storedTrigger = stored[alarm.requestCode]?.triggerAtMillis
            val triggerDrift = storedTrigger?.let { abs(it - alarm.triggerAtMillis) } ?: Long.MAX_VALUE

            if (scheduler.scheduleExactAlarm(alarm.prayerName, alarm.triggerAtMillis, alarm.dayOffset)) {
                scheduledCount++
                if (!hadPendingIntent ||
                    storedTrigger == null ||
                    triggerDrift > PrayerConstants.ALARM_TRIGGER_TOLERANCE_MS
                ) {
                    repairedCount++
                    PrayerLog.alarmRepaired(
                        alarm.prayerName,
                        alarm.requestCode,
                        when {
                            !hadPendingIntent -> "pending_intent_missing"
                            storedTrigger == null -> "registry_missing"
                            else -> "trigger_drift"
                        }
                    )
                } else {
                    verifiedCount++
                    PrayerLog.alarmVerified(alarm.prayerName, alarm.requestCode)
                }
            }

            if (alarm.triggerAtMillis > now) {
                if (nextAlarmAtMillis == null || alarm.triggerAtMillis < nextAlarmAtMillis!!) {
                    nextAlarmAtMillis = alarm.triggerAtMillis
                    nextPrayerName = alarm.prayerName
                }
            }
        }

        scheduler.cancelAlarmsExcept(keepCodes)
        registry.save(expected)

        if (expected.any { it.dayOffset == 1 }) {
            PrayerLog.tomorrowScheduled()
        }

        val requestCodes = expected.map { "${it.requestCode}" }
        PrayerLog.rescheduleCompleted(scheduledCount, verifiedCount, repairedCount)
        repository.recordReschedule(
            reason,
            expected.size,
            verifiedCount,
            repairedCount,
            nextPrayerName,
            nextAlarmAtMillis,
            requestCodes
        )
    }

    private suspend fun buildExpectedAlarms(repository: PrayerRepository): List<ExpectedAlarm> {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val now = System.currentTimeMillis()
        val result = mutableListOf<ExpectedAlarm>()

        listOf(today to 0, tomorrow to 1).forEach { (date, dayOffset) ->
            val times = repository.getPrayerTimesForDate(date)
            PrayerConstants.PRAYER_NAMES.forEach { prayerName ->
                val prayerTime = times[prayerName] ?: return@forEach
                val triggerAtMillis = PrayerCalculator
                    .toDateTime(date, prayerTime)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                if (triggerAtMillis > now) {
                    if (dayOffset == 0 &&
                        (repository.isPrayerFiredToday(prayerName) || isAcceptedInMemory(prayerName))
                    ) {
                        return@forEach
                    }
                    result.add(
                        ExpectedAlarm(
                            prayerName = prayerName,
                            dayOffset = dayOffset,
                            requestCode = PrayerConstants.requestCodeFor(prayerName, dayOffset),
                            triggerAtMillis = triggerAtMillis
                        )
                    )
                }
            }
        }
        return result
    }

    fun syncLocationIfPermitted(context: Context) {
        engineScope.launch { syncLocationIfPermittedSync(context) }
    }

    suspend fun syncLocationIfPermittedSync(context: Context) {
        val appContext = context.applicationContext
        if (!hasLocationPermission(appContext)) return
        val location = getUserLocation(appContext) ?: return
        val repository = PrayerRepository.getInstance(appContext)
        val changed = repository.updateLocationIfChanged(location.first, location.second)
        // Always ensure the city name is resolved for display, even on the very first fix
        // where the stored default happened to match (so the UI stops showing "—").
        resolveCityName(appContext, location.first, location.second)
        if (changed) {
            repository.ensureTodayTimesCalculated()
            rescheduleAllSync(appContext, reason = "location_updated")
        }
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    fun onLocaleChanged(context: Context) {
        engineScope.launch {
            val repository = PrayerRepository.getInstance(context.applicationContext)
            val (lat, lng) = repository.getLocation()
            resolveCityName(context.applicationContext, lat, lng)
        }
    }

    private suspend fun resolveCityName(context: Context, lat: Double, lng: Double) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val cityName = if (!addresses.isNullOrEmpty()) {
                val locality = addresses[0].locality
                val country = addresses[0].countryName
                when {
                    locality != null && country != null -> "$locality, $country"
                    locality != null -> locality
                    else -> "Unknown City"
                }
            } else {
                "Unknown City"
            }
            PrayerRepository.getInstance(context).saveCityName(cityName)
        } catch (e: Exception) {
            PrayerLog.error("GEOCODE_FAILED", e.message ?: "", e)
        }
    }

    suspend fun onPrayerAlarmFiredSync(
        context: Context,
        prayerName: String,
        triggerAtMillis: Long,
        dayOffset: Int
    ) {
        val appContext = context.applicationContext
        var rescheduleReason: String? = null

        fireHandlerMutex.withLock {
            val repository = PrayerRepository.getInstance(appContext)

            PrayerRuntimeState.lastReceiverPrayer = prayerName
            PrayerRuntimeState.lastReceiverAtMillis = System.currentTimeMillis()
            repository.recordReceiverEvent(prayerName)

            if (triggerAtMillis > 0L) {
                val now = System.currentTimeMillis()
                val drift = abs(now - triggerAtMillis)
                if (drift > PrayerConstants.STALE_ALARM_SKIP_MS) {
                    PrayerLog.warn("STALE_ALARM_SKIPPED", "prayer=$prayerName driftMs=$drift")
                    rescheduleReason = "stale_alarm_$prayerName"
                    return@withLock
                }
                if (drift > PrayerConstants.STALE_ALARM_WARN_MS) {
                    PrayerLog.warn("ALARM_LATE", "prayer=$prayerName driftMs=$drift")
                }

                val earlyByMs = triggerAtMillis - now
                if (earlyByMs > PrayerConstants.EARLY_ALARM_TOLERANCE_MS) {
                    PrayerLog.earlyAlarmDetected(prayerName, earlyByMs)
                    PrayerLog.event(
                        "EARLY_ALARM_ACCEPTED",
                        "prayer=$prayerName dayOffset=$dayOffset"
                    )
                }
            }

            if (repository.isPrayerFiredToday(prayerName) || isAcceptedInMemory(prayerName)) {
                PrayerLog.duplicateAlarmIgnored(prayerName)
                return@withLock
            }

            markAcceptedInMemory(prayerName)
            PrayerLog.prayerAlarmAccepted(prayerName)

            try {
                val azanMode = DataStoreManager.getAzanMode(appContext).first()
                val notificationManager = com.falahpro.app.core.notification.PrayerNotificationManager
                    .getInstance(appContext)

                notificationManager.showPrayerNotification(prayerName, azanMode)
                repository.recordNotificationEvent(prayerName)

                if (azanMode == AzanMode.FULL_SOUND) {
                    com.falahpro.app.core.audio.AzanPlaybackService.start(appContext, prayerName)
                }
            } finally {
                repository.tryMarkPrayerFiredToday(prayerName)
                rescheduleReason = "alarm_fired_$prayerName"
            }
        }

        rescheduleReason?.let { reason ->
            rescheduleAllSync(appContext, reason)
        }
    }
}
