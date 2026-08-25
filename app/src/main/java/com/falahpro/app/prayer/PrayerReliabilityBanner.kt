package com.falahpro.app.prayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.falahpro.app.core.util.PrayerReliabilityHelper
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahShapes
import com.falahpro.app.ui.theme.FalahSpacing

/**
 * Shows a compact attention card when any prayer-reliability permission is missing.
 * Returns nothing when Exact Alarms + Notifications + Battery are all OK.
 *
 * ALL logic calls (PrayerReliabilityHelper.*) are UNCHANGED.
 * Only visual styling has been updated to the Falah Pro design system.
 */
@Composable
fun PrayerReliabilityBanner() {
    val context = LocalContext.current

    // ── ALL EXISTING LOGIC UNCHANGED ─────────────────────────────────────────
    val exactAlarms = PrayerReliabilityHelper.canScheduleExactAlarms(context)
    val notifications = PrayerReliabilityHelper.areNotificationsEnabled(context)
    val batteryOk = PrayerReliabilityHelper.isIgnoringBatteryOptimizations(context)
    val oem = PrayerReliabilityHelper.detectOem()
    val oemGuidance = PrayerReliabilityHelper.getOemBatteryGuidance(oem)

    if (exactAlarms && notifications && batteryOk) return
    // ── END LOGIC ─────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FalahSpacing.screenRegular)
            .clip(FalahShapes.Card)
            .background(FalahColors.ButterCream)
            .border(1.dp, FalahColors.Danger.copy(alpha = 0.30f), FalahShapes.Card)
            .padding(FalahSpacing.md)
    ) {
        Text(
            text = "Prayer alerts need attention",
            style = MaterialTheme.typography.labelLarge,
            color = FalahColors.Danger
        )

        Spacer(Modifier.height(FalahSpacing.xs))

        if (!exactAlarms) {
            Text(
                text = "• Allow Alarms & reminders for exact prayer times",
                style = MaterialTheme.typography.bodyMedium,
                color = FalahColors.WarmBrown,
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { PrayerReliabilityHelper.openExactAlarmSettings(context) }
                    .padding(vertical = FalahSpacing.xxs)
            )
        }

        if (!notifications) {
            Text(
                text = "• Enable notifications for prayer alerts",
                style = MaterialTheme.typography.bodyMedium,
                color = FalahColors.WarmBrown,
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { PrayerReliabilityHelper.openNotificationSettings(context) }
                    .padding(vertical = FalahSpacing.xxs)
            )
        }

        if (!batteryOk) {
            Text(
                text = "• Disable battery optimization for Falah Pro",
                style = MaterialTheme.typography.bodyMedium,
                color = FalahColors.WarmBrown,
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { PrayerReliabilityHelper.requestIgnoreBatteryOptimizations(context) }
                    .padding(vertical = FalahSpacing.xxs)
            )

            if (oemGuidance.isNotBlank()) {
                Spacer(Modifier.height(FalahSpacing.xxs))
                Text(
                    text = oemGuidance,
                    style = MaterialTheme.typography.labelSmall,
                    color = FalahColors.WarmBrown.copy(alpha = 0.65f)
                )
            }
        }
    }
}
