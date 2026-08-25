package com.falahpro.app.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.falahpro.app.core.prayer.PrayerRepository
import com.falahpro.app.core.scheduler.PrayerEngine
import com.falahpro.app.core.util.PrayerLog
import com.falahpro.app.data.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reschedules alarms at midnight and resets daily prayer tracker state.
 */
class DateChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_DATE_CHANGED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                PrayerLog.dateChanged()
                DataStoreManager.checkAndResetIfNewDay(context)
                PrayerRepository.getInstance(context).ensureFiredPrayersDateCurrent()
                PrayerRepository.getInstance(context).invalidateCache()
                PrayerEngine.rescheduleAllSync(context, reason = "date_changed")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
