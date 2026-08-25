package com.falahpro.app.core.prayer

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.falahpro.app.core.util.PrayerConstants
import com.falahpro.app.core.util.PrayerDiagnostics
import com.falahpro.app.core.util.PrayerLog
import com.falahpro.app.core.util.PrayerReliabilityHelper
import com.falahpro.app.core.util.PrayerRuntimeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Context.prayerDataStore by preferencesDataStore(name = "prayer_cache")

data class PrayerTimesCache(
    val date: LocalDate,
    val latitude: Double,
    val longitude: Double,
    val calculationMethod: String,
    val times: Map<String, LocalTime>,
    val cityName: String?,
    val calculatedAtMillis: Long
)

/** Lightweight snapshot the UI observes; refreshes automatically when the cache changes. */
data class PrayerDisplayData(
    val times: Map<String, LocalTime>,
    val sunrise: LocalTime?,
    val cityName: String
)

/**
 * Persists location and cached prayer times. Only the Prayer Engine writes calculations.
 */
class PrayerRepository(private val context: Context) {

    private val latitudeKey = doublePreferencesKey("latitude")
    private val longitudeKey = doublePreferencesKey("longitude")
    private val cachedDateKey = stringPreferencesKey("cached_date")
    private val cachedLatitudeKey = doublePreferencesKey("cached_latitude")
    private val cachedLongitudeKey = doublePreferencesKey("cached_longitude")
    private val cachedMethodKey = stringPreferencesKey("cached_calculation_method")
    private val cachedCityNameKey = stringPreferencesKey("cached_city_name")
    private val calculatedAtKey = longPreferencesKey("calculated_at")
    private val calculationMethodKey = stringPreferencesKey("calculation_method")
    private val lastRescheduleAtKey = longPreferencesKey("last_reschedule_at")
    private val lastRescheduleReasonKey = stringPreferencesKey("last_reschedule_reason")
    private val scheduledAlarmCountKey = intPreferencesKey("scheduled_alarm_count")
    private val nextPrayerNameKey = stringPreferencesKey("next_prayer_name")
    private val nextAlarmAtKey = longPreferencesKey("next_alarm_at")
    private val lastNotificationAtKey = longPreferencesKey("last_notification_at")
    private val lastNotificationPrayerKey = stringPreferencesKey("last_notification_prayer")
    private val lastAzanAtKey = longPreferencesKey("last_azan_at")
    private val lastReceiverAtKey = longPreferencesKey("last_receiver_at")
    private val lastReceiverPrayerKey = stringPreferencesKey("last_receiver_prayer")
    private val lastBootAtKey = longPreferencesKey("last_boot_at")
    private val verifiedAlarmCountKey = intPreferencesKey("verified_alarm_count")
    private val repairedAlarmCountKey = intPreferencesKey("repaired_alarm_count")
    private val alarmRequestCodesKey = stringPreferencesKey("alarm_request_codes")
    private val firedPrayersDateKey = stringPreferencesKey("fired_prayers_date")

    private fun timeKey(prayer: String) = stringPreferencesKey("time_$prayer")

    private fun firedPrayerKey(prayerName: String) = booleanPreferencesKey("fired_prayer_$prayerName")

    private fun resetFiredPrayersForDateIfNeeded(
        prefs: MutablePreferences,
        today: String,
        logReset: Boolean
    ) {
        val previousDate = prefs[firedPrayersDateKey]
        if (previousDate != null && previousDate != today) {
            PrayerConstants.PRAYER_NAMES.forEach { prefs.remove(firedPrayerKey(it)) }
            if (logReset) {
                PrayerLog.firedPrayersReset(previousDate, today)
            }
        }
        prefs[firedPrayersDateKey] = today
    }

    /** Clears stale fired-prayer flags when the local calendar date has changed. */
    suspend fun ensureFiredPrayersDateCurrent() {
        context.prayerDataStore.edit { prefs ->
            resetFiredPrayersForDateIfNeeded(prefs, LocalDate.now().toString(), logReset = true)
        }
    }

    /**
     * Atomically marks [prayerName] as fired for today.
     * @return true if this invocation newly accepted the fire; false if already fired today.
     */
    suspend fun tryMarkPrayerFiredToday(prayerName: String): Boolean {
        var accepted = false
        context.prayerDataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            resetFiredPrayersForDateIfNeeded(prefs, today, logReset = true)
            val key = firedPrayerKey(prayerName)
            if (prefs[key] == true) {
                accepted = false
            } else {
                prefs[key] = true
                accepted = true
            }
        }
        return accepted
    }

    suspend fun isPrayerFiredToday(prayerName: String): Boolean {
        val prefs = context.prayerDataStore.data.first()
        val today = LocalDate.now().toString()
        if (prefs[firedPrayersDateKey] != today) return false
        return prefs[firedPrayerKey(prayerName)] == true
    }

    suspend fun recordNotificationEvent(prayerName: String) {
        val now = System.currentTimeMillis()
        PrayerRuntimeState.lastNotificationPrayer = prayerName
        PrayerRuntimeState.lastNotificationAtMillis = now
        context.prayerDataStore.edit { prefs ->
            prefs[lastNotificationAtKey] = now
            prefs[lastNotificationPrayerKey] = prayerName
        }
    }

    suspend fun recordAzanEvent() {
        val now = System.currentTimeMillis()
        PrayerRuntimeState.lastAzanAtMillis = now
        context.prayerDataStore.edit { prefs ->
            prefs[lastAzanAtKey] = now
        }
    }

    suspend fun recordReceiverEvent(prayerName: String) {
        val now = System.currentTimeMillis()
        context.prayerDataStore.edit { prefs ->
            prefs[lastReceiverAtKey] = now
            prefs[lastReceiverPrayerKey] = prayerName
        }
    }

    suspend fun recordBootEvent() {
        context.prayerDataStore.edit { prefs ->
            prefs[lastBootAtKey] = System.currentTimeMillis()
        }
    }

    suspend fun saveLocation(latitude: Double, longitude: Double) {
        context.prayerDataStore.edit { prefs ->
            prefs[latitudeKey] = latitude
            prefs[longitudeKey] = longitude
        }
    }

    suspend fun getLocation(): Pair<Double, Double> {
        val prefs = context.prayerDataStore.data.first()
        val lat = prefs[latitudeKey] ?: PrayerConstants.DEFAULT_LATITUDE
        val lng = prefs[longitudeKey] ?: PrayerConstants.DEFAULT_LONGITUDE
        return lat to lng
    }

    suspend fun hasStoredLocation(): Boolean {
        val prefs = context.prayerDataStore.data.first()
        return prefs[latitudeKey] != null && prefs[longitudeKey] != null
    }

    fun getCalculationMethod(): String = DEFAULT_CALCULATION_METHOD

    suspend fun saveCachedTimes(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        times: Map<String, LocalTime>,
        cityName: String? = null
    ) {
        val now = System.currentTimeMillis()
        context.prayerDataStore.edit { prefs ->
            prefs[cachedDateKey] = date.toString()
            prefs[cachedLatitudeKey] = latitude
            prefs[cachedLongitudeKey] = longitude
            prefs[cachedMethodKey] = getCalculationMethod()
            prefs[calculatedAtKey] = now
            times.forEach { (prayer, time) ->
                prefs[timeKey(prayer)] = time.format(TIME_FORMATTER)
            }
            if (cityName != null) {
                prefs[cachedCityNameKey] = cityName
            }
        }
    }

    suspend fun getTodayCache(): PrayerTimesCache? {
        val prefs = context.prayerDataStore.data.first()
        val dateStr = prefs[cachedDateKey] ?: return null
        val date = LocalDate.parse(dateStr)
        val cachedLat = prefs[cachedLatitudeKey] ?: return null
        val cachedLng = prefs[cachedLongitudeKey] ?: return null
        val method = prefs[cachedMethodKey] ?: DEFAULT_CALCULATION_METHOD
        val calculatedAt = prefs[calculatedAtKey] ?: 0L
        val times = buildMap {
            PrayerConstants.PRAYER_NAMES.forEach { prayer ->
                val timeStr = prefs[timeKey(prayer)] ?: return null
                put(prayer, LocalTime.parse(timeStr, TIME_FORMATTER))
            }
            prefs[timeKey("Sunrise")]?.let { put("Sunrise", LocalTime.parse(it, TIME_FORMATTER)) }
        }
        val cityName = prefs[cachedCityNameKey]
        return PrayerTimesCache(date, cachedLat, cachedLng, method, times, cityName, calculatedAt)
    }

    /** Read-only — never invokes [PrayerCalculator]. */
    suspend fun getTodayCachedTimes(): Map<String, LocalTime>? {
        val cache = getTodayCache() ?: return null
        return if (isCacheValid(cache)) cache.times else null
    }

    suspend fun getCachedCityName(): String? = getTodayCache()?.cityName

    /**
     * Reactive display data for the prayer screen. Emits whenever the cache or city name
     * changes (e.g. after a location update recalculates times), so the UI stays in sync.
     */
    fun observeDisplayData(): kotlinx.coroutines.flow.Flow<PrayerDisplayData> =
        context.prayerDataStore.data.map { prefs ->
            val times = buildMap {
                PrayerConstants.PRAYER_NAMES.forEach { prayer ->
                    prefs[timeKey(prayer)]?.let {
                        put(prayer, LocalTime.parse(it, TIME_FORMATTER))
                    }
                }
            }
            val sunrise = prefs[timeKey("Sunrise")]?.let { LocalTime.parse(it, TIME_FORMATTER) }
            val city = prefs[cachedCityNameKey] ?: "—"
            PrayerDisplayData(times, sunrise, city)
        }

    suspend fun isCacheValidForToday(): Boolean {
        val cache = getTodayCache() ?: return false
        return isCacheValid(cache)
    }

    private suspend fun isCacheValid(cache: PrayerTimesCache): Boolean {
        if (cache.date != LocalDate.now()) return false
        if (cache.calculationMethod != getCalculationMethod()) return false
        val (currentLat, currentLng) = getLocation()
        return locationsMatch(cache.latitude, cache.longitude, currentLat, currentLng)
    }

    /** Engine-only: returns times for scheduling, calculating when needed. */
    suspend fun getPrayerTimesForDate(date: LocalDate): Map<String, LocalTime> {
        if (date == LocalDate.now()) {
            return ensureTodayTimesCalculated()
        }
        val (lat, lng) = getLocation()
        return PrayerCalculator.calculateForDate(lat, lng, date)
    }

    /** Engine-only: ensures today's times exist in cache. */
    suspend fun ensureTodayTimesCalculated(): Map<String, LocalTime> {
        val cache = getTodayCache()
        if (cache != null && isCacheValid(cache)) {
            PrayerLog.cacheHit(LocalDate.now().toString())
            return cache.times
        }
        PrayerLog.calculationStarted(LocalDate.now().toString())
        val (lat, lng) = getLocation()
        val times = PrayerCalculator.calculateForDate(lat, lng, LocalDate.now())
        saveCachedTimes(LocalDate.now(), lat, lng, times, cache?.cityName)
        return times
    }

    suspend fun updateLocationIfChanged(latitude: Double, longitude: Double): Boolean {
        val (storedLat, storedLng) = getLocation()
        if (locationsMatch(storedLat, storedLng, latitude, longitude)) {
            return false
        }
        saveLocation(latitude, longitude)
        invalidateTimesCache()
        PrayerLog.locationChanged(storedLat, storedLng, latitude, longitude)
        return true
    }

    suspend fun saveCityName(cityName: String) {
        context.prayerDataStore.edit { prefs ->
            prefs[cachedCityNameKey] = cityName
        }
    }

    suspend fun invalidateTimesCache() {
        context.prayerDataStore.edit { prefs ->
            prefs.remove(cachedDateKey)
            prefs.remove(cachedLatitudeKey)
            prefs.remove(cachedLongitudeKey)
            prefs.remove(cachedMethodKey)
            prefs.remove(calculatedAtKey)
            PrayerConstants.CACHED_PRAYER_NAMES.forEach { prefs.remove(timeKey(it)) }
        }
        PrayerLog.cacheInvalidated()
    }

    suspend fun invalidateCache() = invalidateTimesCache()

    suspend fun recordReschedule(
        reason: String,
        scheduledCount: Int,
        verifiedCount: Int,
        repairedCount: Int,
        nextPrayerName: String?,
        nextAlarmAtMillis: Long?,
        requestCodes: List<String>
    ) {
        context.prayerDataStore.edit { prefs ->
            prefs[lastRescheduleAtKey] = System.currentTimeMillis()
            prefs[lastRescheduleReasonKey] = reason
            prefs[scheduledAlarmCountKey] = scheduledCount
            prefs[verifiedAlarmCountKey] = verifiedCount
            prefs[repairedAlarmCountKey] = repairedCount
            prefs[alarmRequestCodesKey] = requestCodes.joinToString(",")
            if (nextPrayerName != null) {
                prefs[nextPrayerNameKey] = nextPrayerName
            } else {
                prefs.remove(nextPrayerNameKey)
            }
            if (nextAlarmAtMillis != null) {
                prefs[nextAlarmAtKey] = nextAlarmAtMillis
            } else {
                prefs.remove(nextAlarmAtKey)
            }
        }
    }

    suspend fun getDiagnostics(): PrayerDiagnostics {
        val (lat, lng) = getLocation()
        val prefs = context.prayerDataStore.data.first()
        val cache = getTodayCache()
        val cacheStatus = when {
            cache == null -> "MISSING"
            isCacheValid(cache) -> "VALID"
            else -> "STALE"
        }
        val times = cache?.times ?: emptyMap()
        val next = PrayerCalculator.findNextPrayer(
            times.filterKeys { PrayerConstants.PRAYER_NAMES.contains(it) }
        )
        val oem = PrayerReliabilityHelper.detectOem()
        val runtime = PrayerRuntimeState.snapshot()
        val currentPrayer = PrayerCalculator.findNextPrayer(
            times.filterKeys { PrayerConstants.PRAYER_NAMES.contains(it) }
        )?.first
        return PrayerDiagnostics(
            currentEpochMillis = System.currentTimeMillis(),
            currentDate = LocalDate.now().toString(),
            latitude = lat,
            longitude = lng,
            cityName = cache?.cityName,
            calculationMethod = getCalculationMethod(),
            cacheStatus = cacheStatus,
            cacheTimestamp = formatMillis(cache?.calculatedAtMillis),
            prayerTimes = times,
            currentPrayerName = currentPrayer,
            lastCalculationTime = formatMillis(prefs[calculatedAtKey]),
            lastRescheduleTime = formatMillis(prefs[lastRescheduleAtKey]),
            lastRescheduleReason = prefs[lastRescheduleReasonKey],
            nextPrayerName = next?.first ?: prefs[nextPrayerNameKey],
            nextAlarmTime = formatMillis(prefs[nextAlarmAtKey]),
            nextAlarmEpochMillis = prefs[nextAlarmAtKey],
            scheduledAlarmCount = prefs[scheduledAlarmCountKey] ?: 0,
            verifiedAlarmCount = prefs[verifiedAlarmCountKey] ?: 0,
            repairedAlarmCount = prefs[repairedAlarmCountKey] ?: 0,
            alarmRequestCodes = prefs[alarmRequestCodesKey] ?: "—",
            lastNotificationTime = formatMillis(prefs[lastNotificationAtKey]),
            lastNotificationPrayer = prefs[lastNotificationPrayerKey],
            lastAzanTime = formatMillis(prefs[lastAzanAtKey]),
            lastReceiverTime = formatMillis(prefs[lastReceiverAtKey]),
            lastReceiverPrayer = prefs[lastReceiverPrayerKey],
            lastBootTime = formatMillis(prefs[lastBootAtKey]),
            exactAlarmPermissionGranted = PrayerReliabilityHelper.canScheduleExactAlarms(context),
            notificationPermissionGranted = PrayerReliabilityHelper.areNotificationsEnabled(context),
            batteryOptimizationIgnored = PrayerReliabilityHelper.isIgnoringBatteryOptimizations(context),
            oemManufacturer = oem.name,
            oemGuidance = PrayerReliabilityHelper.getOemBatteryGuidance(oem),
            foregroundServiceRunning = runtime.foregroundServiceRunning,
            wakeLockHeld = runtime.wakeLockHeld,
            mediaPlayerActive = runtime.mediaPlayerActive,
            audioFocusHeld = runtime.audioFocusHeld
        )
    }

    private fun formatMillis(millis: Long?): String? {
        if (millis == null || millis <= 0L) return null
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(DIAGNOSTICS_FORMATTER)
    }

    fun observeCalculationMethod() = context.prayerDataStore.data.map { prefs ->
        prefs[calculationMethodKey] ?: DEFAULT_CALCULATION_METHOD
    }

    companion object {
        private const val DEFAULT_CALCULATION_METHOD = "KARACHI_HANAFI"
        private const val LOCATION_EPSILON = 0.005

        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
        private val DIAGNOSTICS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun locationsMatch(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Boolean =
            kotlin.math.abs(lat1 - lat2) < LOCATION_EPSILON &&
                kotlin.math.abs(lng1 - lng2) < LOCATION_EPSILON

        @Volatile
        private var instance: PrayerRepository? = null

        fun getInstance(context: Context): PrayerRepository {
            return instance ?: synchronized(this) {
                instance ?: PrayerRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
