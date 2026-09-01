package com.falahpro.app.prayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.falahpro.app.core.prayer.PrayerRepository
import com.falahpro.app.core.scheduler.PrayerEngine
import com.falahpro.app.core.util.PrayerDiagnostics
import com.falahpro.app.core.util.PrayerReliabilityHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@Composable
fun PrayerDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var diagnostics by remember { mutableStateOf<PrayerDiagnostics?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        diagnostics = PrayerRepository.getInstance(context).getDiagnostics()
    }

    LaunchedEffect(Unit) { refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PrayerEngine.verifyOnResume(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        // Re-read diagnostics shortly after resume verify kicks in
        kotlinx.coroutines.delay(500)
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF140C09))
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Prayer Engine Diagnostics", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        diagnostics?.let { d ->
            Section("Time")
            DiagnosticRow("Current epoch", d.currentEpochMillis.toString())
            DiagnosticRow("Date", d.currentDate)
            DiagnosticRow("Next alarm epoch", d.nextAlarmEpochMillis?.toString() ?: "—")

            Section("Location & cache")
            DiagnosticRow("Location", "${d.latitude}, ${d.longitude}")
            DiagnosticRow("City", d.cityName ?: "—")
            DiagnosticRow("Method", d.calculationMethod)
            DiagnosticRow("Cache status", d.cacheStatus)
            DiagnosticRow("Cache timestamp", d.cacheTimestamp ?: "—")
            DiagnosticRow("Last calculation", d.lastCalculationTime ?: "—")

            Section("Prayers")
            DiagnosticRow("Current prayer", d.currentPrayerName ?: "—")
            DiagnosticRow("Next prayer", d.nextPrayerName ?: "—")
            DiagnosticRow("Next alarm", d.nextAlarmTime ?: "—")
            val formatter = DateTimeFormatter.ofPattern("hh:mm a")
            d.prayerTimes.forEach { (name, time) ->
                DiagnosticRow(name, time.format(formatter))
            }

            Section("Alarms")
            DiagnosticRow("Scheduled count", d.scheduledAlarmCount.toString())
            DiagnosticRow("Verified count", d.verifiedAlarmCount.toString())
            DiagnosticRow("Repaired count", d.repairedAlarmCount.toString())
            DiagnosticRow("Request codes", d.alarmRequestCodes)
            DiagnosticRow("Last reschedule", d.lastRescheduleTime ?: "—")
            DiagnosticRow("Reschedule reason", d.lastRescheduleReason ?: "—")

            Section("Events")
            DiagnosticRow("Last notification", d.lastNotificationTime ?: "—")
            DiagnosticRow("Notification prayer", d.lastNotificationPrayer ?: "—")
            DiagnosticRow("Last azan", d.lastAzanTime ?: "—")
            DiagnosticRow("Last receiver", d.lastReceiverTime ?: "—")
            DiagnosticRow("Receiver prayer", d.lastReceiverPrayer ?: "—")
            DiagnosticRow("Last boot", d.lastBootTime ?: "—")

            Section("Permissions")
            PermissionDot("Exact alarms", d.exactAlarmPermissionGranted)
            PermissionDot("Notifications", d.notificationPermissionGranted)
            PermissionDot("Battery unrestricted", d.batteryOptimizationIgnored)
            if (PrayerReliabilityHelper.isXiaomiDevice()) {
                PermissionDot(
                    "Xiaomi Autostart (best-effort)",
                    PrayerReliabilityHelper.isXiaomiAutostartLikelyEnabled(context)
                )
            }
            DiagnosticRow("OEM", d.oemManufacturer)
            DiagnosticRow("OEM guidance", d.oemGuidance ?: "—")

            Section("Runtime")
            DiagnosticRow("Foreground service", d.foregroundServiceRunning.toString())
            DiagnosticRow("WakeLock held", d.wakeLockHeld.toString())
            DiagnosticRow("MediaPlayer active", d.mediaPlayerActive.toString())
            DiagnosticRow("Audio focus held", d.audioFocusHeld.toString())
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                PrayerReliabilityHelper.openExactAlarmSettings(context)
            }, modifier = Modifier.weight(1f)) { Text("Exact alarms") }
            Button(onClick = {
                PrayerReliabilityHelper.openNotificationSettings(context)
            }, modifier = Modifier.weight(1f)) { Text("Notifications") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { PrayerReliabilityHelper.requestIgnoreBatteryOptimizations(context) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Battery optimization") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                PrayerEngine.rescheduleAll(context, reason = "diagnostics_manual")
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Force reschedule") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { scope.launch { refresh() } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Refresh") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(12.dp))
    Text(title, color = Color(0xFFE2C07A), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun PermissionDot(label: String, ok: Boolean) {
    // AZAN-FIX-7: Live green/red status for Azan delivery permissions.
    DiagnosticRow(label, if (ok) "● OK" else "● MISSING")
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 14.sp)
    }
}
