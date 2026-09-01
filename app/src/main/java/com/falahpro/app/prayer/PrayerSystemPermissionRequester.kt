package com.falahpro.app.prayer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.falahpro.app.core.scheduler.PrayerEngine
import com.falahpro.app.core.util.PrayerReliabilityHelper

/**
 * Asks for azan-related grants the same way location is asked:
 * Android system dialogs, in sequence, without a custom setup screen.
 */
@Composable
fun RequestPrayerSystemPermissions() {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var showOemWizard by rememberSaveable { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // AZAN-FIX-3A: Notification grant must reschedule immediately, not wait for ON_RESUME.
        if (granted) {
            PrayerEngine.rescheduleAll(context, reason = "notifications_granted")
        } else {
            PrayerEngine.verifyOnResume(context)
        }
        step = 1
    }

    val nextStepLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // AZAN-FIX-3A: Returning from exact-alarm / battery settings.
        if (PrayerReliabilityHelper.canScheduleExactAlarms(context)) {
            PrayerEngine.rescheduleAll(context, reason = "exact_alarm_granted")
        } else {
            PrayerEngine.fallbackToInexact(context)
        }
        step += 1
    }

    LaunchedEffect(step) {
        when (step) {
            0 -> {
                val needNotifications =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !PrayerReliabilityHelper.areNotificationsEnabled(context)
                if (needNotifications) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    step = 1
                }
            }
            1 -> {
                // AZAN-FIX-7: OEM wizard once per install — never loop Xiaomi settings.
                if (PrayerReliabilityHelper.shouldShowOemWizard(context)) {
                    showOemWizard = true
                } else {
                    step = 2
                }
            }
            2 -> {
                val exactIntent = PrayerReliabilityHelper.exactAlarmSettingsIntent(context)
                if (exactIntent != null &&
                    PrayerReliabilityHelper.shouldPromptExactAlarmSettings(context)
                ) {
                    runCatching {
                        nextStepLauncher.launch(exactIntent)
                    }.onFailure { step = 3 }
                } else {
                    step = 3
                }
            }
        }
    }

    if (showOemWizard) {
        val xiaomi = PrayerReliabilityHelper.isXiaomiDevice()
        val samsung = PrayerReliabilityHelper.isSamsungDevice()
        AlertDialog(
            onDismissRequest = {
                showOemWizard = false
                step = 2
            },
            title = { Text("Keep Azan on time") },
            text = {
                Text(
                    "FalahPro needs these to deliver Azan on time. " +
                        "Without them, Azan may be delayed or missed."
                )
            },
            confirmButton = {
                if (xiaomi) {
                    TextButton(
                        onClick = {
                            PrayerReliabilityHelper.openXiaomiAutostart(context)
                            showOemWizard = false  // AZAN-FIX-2
                            step = 2               // AZAN-FIX-2
                        }
                    ) { Text("Open Autostart settings") }
                } else if (samsung) {
                    TextButton(
                        onClick = {
                            PrayerReliabilityHelper.openIgnoreBatteryOptimizationSettings(context)
                            showOemWizard = false
                            step = 2
                        }
                    ) { Text("Battery settings") }
                } else {
                    TextButton(
                        onClick = {
                            runCatching {
                                nextStepLauncher.launch(
                                    PrayerReliabilityHelper.ignoreBatteryOptimizationsIntent(context)
                                )
                            }
                            showOemWizard = false
                        }
                    ) { Text("Allow") }
                }
            },
            dismissButton = {
                if (xiaomi) {
                    TextButton(
                        onClick = {
                            PrayerReliabilityHelper.openIgnoreBatteryOptimizationSettings(context)
                            showOemWizard = false
                            step = 2
                        }
                    ) { Text("Set Battery to No Restrictions") }
                } else {
                    TextButton(
                        onClick = {
                            showOemWizard = false
                            step = 2
                        }
                    ) { Text("Not now") }
                }
            }
        )
    }
}
