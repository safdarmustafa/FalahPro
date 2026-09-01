package com.falahpro.app.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.falahpro.app.core.scheduler.PrayerEngine
import com.falahpro.app.core.util.PrayerConstants
import com.falahpro.app.core.util.PrayerLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        PrayerLog.event(
            "RECEIVER_TRIGGERED",
            "prayer=${intent?.getStringExtra(PrayerConstants.EXTRA_PRAYER_NAME)}"
        )

        if (intent?.action != PrayerConstants.ACTION_PRAYER_ALARM) return

        val prayerName = intent.getStringExtra(PrayerConstants.EXTRA_PRAYER_NAME) ?: return
        val triggerAt = intent.getLongExtra(PrayerConstants.EXTRA_TRIGGER_AT_MILLIS, 0L)
        val dayOffset = intent.getIntExtra(PrayerConstants.EXTRA_DAY_OFFSET, 0)
        val isFgsRetry = intent.getBooleanExtra("is_fgs_retry", false) // AZAN-FIX-4

        PrayerLog.receiverEntered(prayerName)
        PrayerLog.alarmFired(prayerName, triggerAt)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                PrayerEngine.onPrayerAlarmFiredSync(
                    context,
                    prayerName,
                    triggerAt,
                    dayOffset,
                    isFgsRetry = isFgsRetry // AZAN-FIX-4
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
