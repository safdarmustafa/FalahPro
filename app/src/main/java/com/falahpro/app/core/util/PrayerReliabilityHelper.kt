package com.falahpro.app.core.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.falahpro.app.core.alarm.PrayerAlarmScheduler

object PrayerReliabilityHelper {

    fun canScheduleExactAlarms(context: Context): Boolean =
        PrayerAlarmScheduler.getInstance(context).canScheduleExactAlarms()

    fun exactAlarmSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun openExactAlarmSettings(context: Context) {
        val intent = exactAlarmSettingsIntent(context) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun ignoreBatteryOptimizationsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = ignoreBatteryOptimizationsIntent(context)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun detectOem(): OemManufacturer {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("samsung") -> OemManufacturer.SAMSUNG
            manufacturer.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ->
                OemManufacturer.XIAOMI
            manufacturer.contains("oppo") -> OemManufacturer.OPPO
            manufacturer.contains("vivo") -> OemManufacturer.VIVO
            manufacturer.contains("realme") -> OemManufacturer.REALME
            manufacturer.contains("oneplus") -> OemManufacturer.ONEPLUS
            manufacturer.contains("honor") -> OemManufacturer.HONOR
            manufacturer.contains("huawei") -> OemManufacturer.HUAWEI
            else -> OemManufacturer.OTHER
        }
    }

    fun getOemBatteryGuidance(manufacturer: OemManufacturer): String = when (manufacturer) {
        OemManufacturer.SAMSUNG ->
            "Samsung: Settings → Apps → Falah Pro → Battery → Unrestricted. " +
                "Also disable 'Put unused apps to sleep' for Falah Pro."
        OemManufacturer.XIAOMI ->
            "Xiaomi/Redmi/Poco: Security → Battery → App battery saver → Falah Pro → No restrictions. " +
                "Enable Autostart for Falah Pro."
        OemManufacturer.OPPO ->
            "Oppo: Settings → Battery → More battery settings → Optimize battery use → Falah Pro → Don't optimize."
        OemManufacturer.VIVO ->
            "Vivo: Settings → Battery → Background power consumption management → Falah Pro → Allow high background power."
        OemManufacturer.REALME ->
            "Realme: Settings → Battery → App battery management → Falah Pro → Don't optimize. Enable Autostart."
        OemManufacturer.ONEPLUS ->
            "OnePlus: Settings → Battery → Battery optimization → Falah Pro → Don't optimize."
        OemManufacturer.HUAWEI ->
            "Huawei: Settings → Battery → App launch → Falah Pro → Manage manually (enable all switches)."
        OemManufacturer.HONOR ->
            "Honor: Settings → Battery → App launch → Falah Pro → Manage manually (enable all switches)."
        OemManufacturer.OTHER ->
            "Disable battery optimization for Falah Pro in system battery settings."
    }

    enum class OemManufacturer {
        SAMSUNG, XIAOMI, OPPO, VIVO, REALME, ONEPLUS, HUAWEI, HONOR, OTHER
    }
}
