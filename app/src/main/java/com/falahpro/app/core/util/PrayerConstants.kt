package com.falahpro.app.core.util

object PrayerConstants {

    val PRAYER_NAMES = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    val CACHED_PRAYER_NAMES = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")

    const val ACTION_PRAYER_ALARM = "com.falahpro.app.action.PRAYER_ALARM"
    const val EXTRA_PRAYER_NAME = "extra_prayer_name"
    const val EXTRA_TRIGGER_AT_MILLIS = "extra_trigger_at_millis"
    const val EXTRA_DAY_OFFSET = "extra_day_offset"

    const val NOTIFICATION_CHANNEL_ID = "prayer_notifications"
    const val AZAN_PLAYBACK_CHANNEL_ID = "azan_playback"

    const val DEFAULT_LATITUDE = 25.5941
    const val DEFAULT_LONGITUDE = 85.1376

    /** Fajr=1001 … Isha=1005; tomorrow Fajr=2001 … Isha=2005 */
    const val REQUEST_CODE_BASE_TODAY = 1001
    const val REQUEST_CODE_BASE_TOMORROW = 2001

    const val STALE_ALARM_WARN_MS = 15 * 60 * 1000L
    const val STALE_ALARM_SKIP_MS = 60 * 60 * 1000L

    /** Trigger drift beyond this re-schedules an existing PendingIntent. */
    const val ALARM_TRIGGER_TOLERANCE_MS = 60_000L

    /** Alarm delivered more than this many ms before [triggerAtMillis] is deferred, not played. */
    const val EARLY_ALARM_TOLERANCE_MS = 30_000L

    fun requestCodeFor(prayerName: String, dayOffset: Int): Int {
        val prayerIndex = PRAYER_NAMES.indexOf(prayerName)
        require(prayerIndex >= 0) { "Unknown prayer: $prayerName" }
        val base = if (dayOffset == 0) REQUEST_CODE_BASE_TODAY else REQUEST_CODE_BASE_TOMORROW
        return base + prayerIndex
    }

    fun prayerForRequestCode(requestCode: Int): Pair<String, Int>? {
        return when {
            requestCode in REQUEST_CODE_BASE_TODAY..(REQUEST_CODE_BASE_TODAY + PRAYER_NAMES.lastIndex) -> {
                val index = requestCode - REQUEST_CODE_BASE_TODAY
                PRAYER_NAMES[index] to 0
            }
            requestCode in REQUEST_CODE_BASE_TOMORROW..(REQUEST_CODE_BASE_TOMORROW + PRAYER_NAMES.lastIndex) -> {
                val index = requestCode - REQUEST_CODE_BASE_TOMORROW
                PRAYER_NAMES[index] to 1
            }
            else -> null
        }
    }

    fun allAlarmSlots(): List<Pair<String, Int>> =
        listOf(0, 1).flatMap { dayOffset ->
            PRAYER_NAMES.map { prayer -> prayer to dayOffset }
        }
}
