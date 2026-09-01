package com.falahpro.app.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.falahpro.app.core.alarm.PrayerAlarmScheduler
import com.falahpro.app.core.scheduler.PrayerEngine

class ExactAlarmRevocationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED") {
            return
        }
        val scheduler = PrayerAlarmScheduler.getInstance(context)
        if (scheduler.canScheduleExactAlarms()) {
            // AZAN-FIX-1: Exact alarm re-granted — reschedule with setAlarmClock
            PrayerEngine.rescheduleAll(context, reason = "exact_alarm_re_granted")
        } else {
            // AZAN-FIX-1: Exact alarm revoked — fallback to inexact, never drop alarms
            PrayerEngine.fallbackToInexact(context)
        }
    }
}
