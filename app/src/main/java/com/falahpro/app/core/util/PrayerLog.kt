package com.falahpro.app.core.util

import android.util.Log

/**
 * Structured prayer-engine logging. Filter Logcat with tag [TAG].
 */
object PrayerLog {

    const val TAG = "FalahProPrayer"

    fun event(code: String, detail: String = "") {
        if (detail.isEmpty()) {
            Log.i(TAG, code)
        } else {
            Log.i(TAG, "$code | $detail")
        }
    }

    fun warn(code: String, detail: String = "") {
        if (detail.isEmpty()) Log.w(TAG, code) else Log.w(TAG, "$code | $detail")
    }

    fun error(code: String, detail: String = "", throwable: Throwable? = null) {
        val message = if (detail.isEmpty()) code else "$code | $detail"
        if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
    }

    // --- Convenience wrappers (map to structured codes) ---

    fun engineBoot() = event("ENGINE_BOOT")
    fun prayerCalculated(prayer: String, time: String, date: String) =
        event("CALCULATION_FINISHED", "prayer=$prayer time=$time date=$date")
    fun calculationStarted(date: String) = event("CALCULATION_STARTED", "date=$date")
    fun alarmScheduled(prayer: String, triggerAtMillis: Long, dayOffset: Int, requestCode: Int) =
        event("ALARM_SCHEDULED", "prayer=$prayer code=$requestCode trigger=$triggerAtMillis day=$dayOffset")
    fun alarmCancelled(prayer: String, dayOffset: Int, requestCode: Int) =
        event("ALARM_CANCELLED", "prayer=$prayer code=$requestCode day=$dayOffset")
    fun alarmVerified(prayer: String, requestCode: Int) =
        event("ALARM_VERIFIED", "prayer=$prayer code=$requestCode")
    fun alarmRepaired(prayer: String, requestCode: Int, reason: String) =
        event("ALARM_REPAIRED", "prayer=$prayer code=$requestCode reason=$reason")
    fun alarmFired(prayer: String, triggerAtMillis: Long) =
        event("ALARM_FIRED", "prayer=$prayer trigger=$triggerAtMillis")
    fun notificationPosted(prayer: String) = event("NOTIFICATION_POSTED", "prayer=$prayer")
    fun audioStarted() = event("AUDIO_STARTED")
    fun audioCompleted() = event("AUDIO_COMPLETED")
    fun serviceStarted(prayer: String) = event("SERVICE_STARTED", "prayer=$prayer")
    fun serviceStopped() = event("SERVICE_STOPPED")
    fun wakeLockAcquired() = event("WAKELOCK_ACQUIRED")
    fun wakeLockReleased() = event("WAKELOCK_RELEASED")
    fun tomorrowScheduled() = event("TOMORROW_SCHEDULED")
    fun rescheduleStarted(reason: String) = event("RESCHEDULE_STARTED", "reason=$reason")
    fun rescheduleCompleted(scheduled: Int, verified: Int, repaired: Int) =
        event("RESCHEDULE_COMPLETED", "scheduled=$scheduled verified=$verified repaired=$repaired")
    fun bootCompleted() = event("BOOT_COMPLETED")
    fun timezoneChanged() = event("TIMEZONE_CHANGED")
    fun dateChanged() = event("DATE_CHANGED")
    fun timeChanged() = event("TIME_CHANGED")
    fun localeChanged() = event("LOCALE_CHANGED")
    fun cacheHit(date: String) = event("CACHE_HIT", "date=$date")
    fun cacheInvalidated() = event("CACHE_INVALIDATED")
    fun locationChanged(oldLat: Double, oldLng: Double, newLat: Double, newLng: Double) =
        event("LOCATION_CHANGED", "old=$oldLat,$oldLng new=$newLat,$newLng")
    fun receiverEntered(prayer: String) = event("RECEIVER_ENTERED", "prayer=$prayer")
    fun exactAlarmDenied() = warn("EXACT_ALARM_DENIED", "user must grant Alarms & reminders")
    fun prayerAlarmAccepted(prayer: String) = event("PRAYER_ALARM_ACCEPTED", "prayer=$prayer")
    fun duplicateAlarmIgnored(prayer: String) = warn("DUPLICATE_ALARM_IGNORED", "prayer=$prayer")
    fun earlyAlarmDetected(prayer: String, earlyByMs: Long) =
        warn("EARLY_ALARM_DETECTED", "prayer=$prayer earlyByMs=$earlyByMs")
    fun firedPrayersReset(previousDate: String, newDate: String) =
        event("FIRED_PRAYERS_RESET", "previousDate=$previousDate newDate=$newDate")
}
