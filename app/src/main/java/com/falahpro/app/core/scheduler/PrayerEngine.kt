package com.falahpro.app.core.scheduler

import android.Manifest
import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.falahpro.app.core.receiver.PrayerAlarmReceiver
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
import com.falahpro.app.location.getLastKnownLocationOrAwait
import com.falahpro.app.location.requestFreshUserLocation
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

/*
 * AZAN RELIABILITY QA MATRIX
 * Run before every release on physical device (Xiaomi preferred)
 *
 * 1. Fresh install, all permissions granted → Azan plays within ±2 min of prayer time
 * 2. App swiped from recents → Azan still fires
 * 3. Normal reboot → Azan fires after boot
 * 4. MIUI fast reboot → Azan fires after boot
 * 5. Exact alarm denied → Azan plays (late ok), "Delayed" shown in notification
 * 6. SILENT mode confirm → no Azan; switch back Full → Azan returns next prayer
 * 7. Xiaomi "recommended" battery → OEM wizard shown once, Azan still fires
 * 8. Doze 90 min, alarm fires late → Azan plays IF still within prayer window
 * 9. Call during Azan → Azan pauses, resumes after call ends (max 5 min gap)
 * 10. FGS blocked → fallback notification shown + retry alarm scheduled
 */

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

    /** AZAN-FIX-3B: Exact-alarm revoked — keep inexact slots so Azan is not dropped. */
    fun fallbackToInexact(context: Context) {
        rescheduleAll(context, reason = "exact_alarm_revoked_inexact")
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
            // Still arm inexact alarms so Azan is not dropped if the user
            // backed out of the one-time exact-alarm settings screen.
        }

        val azanMode = DataStoreManager.getAzanMode(context).first()
        notificationManager.updateChannelsForMode(azanMode)

        // AZAN-FIX-2: SILENT must not cancel AlarmManager slots — skip audio at fire time only.

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
        // AZAN-FIX-1: Only the soonest upcoming slot uses setAlarmClock (status bar + Doze).
        val nextUpcoming = expected
            .filter { it.triggerAtMillis > now }
            .minByOrNull { it.triggerAtMillis }

        for (alarm in expected) {
            val hadPendingIntent = scheduler.isAlarmPending(alarm.prayerName, alarm.dayOffset)
            val storedTrigger = stored[alarm.requestCode]?.triggerAtMillis
            val triggerDrift = storedTrigger?.let { abs(it - alarm.triggerAtMillis) } ?: Long.MAX_VALUE

            val scheduled = if (nextUpcoming != null &&
                alarm.requestCode == nextUpcoming.requestCode
            ) {
                scheduler.scheduleNextPrayerAsAlarmClock(
                    alarm.prayerName,
                    alarm.triggerAtMillis,
                    alarm.dayOffset
                )
            } else {
                scheduler.scheduleExactAlarm(alarm.prayerName, alarm.triggerAtMillis, alarm.dayOffset)
            }

            if (scheduled) {
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
        val repository = PrayerRepository.getInstance(appContext)

        suspend fun applyLocation(lat: Double, lng: Double) {
            val changed = repository.updateLocationIfChanged(lat, lng)
            // Always ensure the city name is resolved for display, even on the very first fix
            // where the stored default happened to match (so the UI stops showing "—").
            resolveCityName(appContext, lat, lng)
            if (changed) {
                repository.ensureTodayTimesCalculated()
                rescheduleAllSync(appContext, reason = "location_updated")
            }
        }

        val lastKnown = getLastKnownLocationOrAwait(appContext)
        if (lastKnown != null) {
            applyLocation(lastKnown.first, lastKnown.second)
        }
        val fresh = requestFreshUserLocation(appContext)
        if (fresh != null) {
            applyLocation(fresh.first, fresh.second)
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
        dayOffset: Int,
        isFgsRetry: Boolean = false // AZAN-FIX-4
    ) {
        val appContext = context.applicationContext
        var rescheduleReason: String? = null

        fireHandlerMutex.withLock {
            val repository = PrayerRepository.getInstance(appContext)

            // AZAN-FIX-4: If this is an FGS retry, skip stale/in-memory checks and go to audio
            if (isFgsRetry) {
                if (repository.isPrayerFiredToday(prayerName)) {
                    // AZAN-FIX-RETRY: Already fired in previous process — skip
                    PrayerLog.warn("FGS_RETRY_ALREADY_FIRED", prayerName)
                    rescheduleReason = "fgs_retry_already_fired_$prayerName"
                    return@withLock
                }
                val notificationManager = com.falahpro.app.core.notification.PrayerNotificationManager
                    .getInstance(appContext)
                startAzanServiceOrFallback(appContext, prayerName, notificationManager)
                return@withLock
            }

            PrayerRuntimeState.lastReceiverPrayer = prayerName
            PrayerRuntimeState.lastReceiverAtMillis = System.currentTimeMillis()
            repository.recordReceiverEvent(prayerName)

            if (triggerAtMillis > 0L) {
                val now = System.currentTimeMillis()
                val drift = abs(now - triggerAtMillis)
                if (drift > PrayerConstants.STALE_LOG_THRESHOLD_MS) {
                    PrayerLog.warn("ALARM_LATE", "prayer=$prayerName driftMs=$drift")
                }

                val nextPrayerStartMs = nextPrayerStartAfter(repository, prayerName)
                // AZAN-FIX-5: Drop only if we are already in the next salah's window.
                if (nextPrayerStartMs != null && now > nextPrayerStartMs) {
                    PrayerLog.warn("STALE_ALARM_SKIPPED", "prayer=$prayerName past_next=$nextPrayerStartMs")
                    rescheduleReason = "stale_alarm_$prayerName"
                    return@withLock
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

                if (azanMode == AzanMode.SILENT) {
                    // AZAN-FIX-2: Silent = skip audio, keep schedule
                    PrayerLog.warn("AZAN_MODE_SILENT", "playback skipped, alarms kept")
                    rescheduleReason = "silent_skip_$prayerName"
                    return@withLock
                }

                val delayMinutes = if (triggerAtMillis > 0L) {
                    val late = System.currentTimeMillis() - triggerAtMillis
                    if (late > 60_000L) (late / 60_000L).toInt() else 0
                } else 0

                notificationManager.showPrayerNotification(
                    prayerName,
                    azanMode,
                    delayMinutes = delayMinutes
                )
                repository.recordNotificationEvent(prayerName)

                // AZAN-FIX-2: NOTIFICATION_ONLY keeps alarms, no AzanPlaybackService.
                if (azanMode == AzanMode.FULL_SOUND) {
                    startAzanServiceOrFallback(appContext, prayerName, notificationManager)
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

    // AZAN-FIX-4: FGS from a cached process can throw; notify + retry once.
    private fun startAzanServiceOrFallback(
        appContext: Context,
        prayerName: String,
        notificationManager: com.falahpro.app.core.notification.PrayerNotificationManager
    ) {
        try {
            com.falahpro.app.core.audio.AzanPlaybackService.start(appContext, prayerName)
        } catch (e: Exception) {
            val fgsBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            if (fgsBlocked) {
                PrayerLog.error("FGS_START_BLOCKED", e.message ?: "", e)
                notificationManager.showMissedAzanFallback(prayerName)
                // AZAN-FIX-4: Handler retry unreliable if process dies.
                // Schedule a one-shot exact alarm 60s later so system re-wakes us.
                val retryMs = System.currentTimeMillis() + 60_000L
                val retryIntent = Intent(appContext, PrayerAlarmReceiver::class.java).apply {
                    action = PrayerConstants.ACTION_PRAYER_ALARM
                    putExtra(PrayerConstants.EXTRA_PRAYER_NAME, prayerName)
                    putExtra(PrayerConstants.EXTRA_TRIGGER_AT_MILLIS, retryMs)
                    putExtra(PrayerConstants.EXTRA_DAY_OFFSET, 0)
                    putExtra("is_fgs_retry", true)
                }
                val retryPendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    9999,
                    retryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                // AZAN-FIX-4: Use setAndAllowWhileIdle so it fires even in Doze
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    retryMs,
                    retryPendingIntent
                )
                PrayerLog.event("FGS_RETRY_SCHEDULED", "prayer=$prayerName retryMs=$retryMs")
            } else {
                throw e
            }
        }
    }

    /** AZAN-FIX-5: Next fard start after [prayerName] (tomorrow Fajr after Isha). */
    private suspend fun nextPrayerStartAfter(
        repository: PrayerRepository,
        prayerName: String
    ): Long? {
        val names = PrayerConstants.PRAYER_NAMES
        val idx = names.indexOf(prayerName)
        if (idx < 0) return null
        val today = LocalDate.now()
        val (nextName, date) = if (idx < names.lastIndex) {
            names[idx + 1] to today
        } else {
            names[0] to today.plusDays(1)
        }
        val time = repository.getPrayerTimesForDate(date)[nextName] ?: return null
        return PrayerCalculator
            .toDateTime(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
