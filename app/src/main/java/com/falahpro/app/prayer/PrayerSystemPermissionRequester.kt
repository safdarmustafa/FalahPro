package com.falahpro.app.prayer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.falahpro.app.core.scheduler.PrayerEngine
import com.falahpro.app.core.util.PrayerReliabilityHelper

/**
 * Asks for azan-related grants the same way location is asked:
 * Android system dialogs, in sequence, without a custom setup screen.
 *
 * Notifications = runtime permission popup.
 * Battery = system "Allow ignore battery optimizations" popup.
 * Exact alarms (Android 12+) = Android's own alarm-access screen (Google does not
 * provide a runtime Allow/Deny popup for that permission).
 */
@Composable
fun RequestPrayerSystemPermissions() {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        PrayerEngine.verifyOnResume(context)
        step = 1
    }

    val nextStepLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        PrayerEngine.verifyOnResume(context)
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
                if (PrayerReliabilityHelper.shouldPromptBatterySettings(context)) {
                    runCatching {
                        nextStepLauncher.launch(
                            PrayerReliabilityHelper.ignoreBatteryOptimizationsIntent(context)
                        )
                    }.onFailure { step = 2 }
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
}
