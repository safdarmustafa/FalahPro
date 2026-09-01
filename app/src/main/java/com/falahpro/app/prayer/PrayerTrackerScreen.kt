package com.falahpro.app.prayer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.falahpro.app.FalahPro
import com.falahpro.app.core.notification.PrayerNotificationManager
import com.falahpro.app.core.scheduler.PrayerEngine
import com.falahpro.app.core.util.PrayerConstants
import com.falahpro.app.data.AzanMode
import com.falahpro.app.data.DataStoreManager
import com.falahpro.app.ui.theme.FalahArabicTextStyle
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahSpacing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

private val prayerArabicNames = mapOf(
    "Fajr" to "الفجر",
    "Dhuhr" to "الظهر",
    "Asr" to "العصر",
    "Maghrib" to "المغرب",
    "Isha" to "العشاء"
)

private val prayerOrderList = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

/** Fajr ends at sunrise, not Dhuhr. Other prayers end at the next salah. */
private fun prayerEndTime(
    prayer: String,
    prayerTimes: Map<String, LocalTime>,
    sunriseTime: LocalTime?
): LocalTime? {
    if (prayer == "Fajr") return sunriseTime
    val idx = prayerOrderList.indexOf(prayer)
    if (idx < 0) return null
    val next = if (idx + 1 < prayerOrderList.size) prayerOrderList[idx + 1] else "Fajr"
    return prayerTimes[next]
}

/** Fajr is current only until sunrise; after that until Dhuhr there is no fard window. */
private fun resolveCurrentPrayer(
    prayerTimes: Map<String, LocalTime>,
    currentTime: LocalTime,
    sunriseTime: LocalTime?
): String? {
    val started = prayerOrderList.lastOrNull { name ->
        prayerTimes[name]?.let { currentTime.isAfter(it) } == true
    } ?: return null
    if (started == "Fajr") {
        val sunrise = sunriseTime ?: return started
        if (!currentTime.isBefore(sunrise)) return null
    }
    return started
}

private fun secondsUntil(end: LocalTime, now: LocalTime): Int {
    val nowSec = now.toSecondOfDay()
    val endSec = end.toSecondOfDay()
    return if (endSec > nowSec) endSec - nowSec else (86400 - nowSec) + endSec
}

private fun getHijriDateString(): String {
    return ""
}

// ─────────────────────────────────────────────────────────────────────────────
// Visual effects state — UNCHANGED (activity-scoped, signature must match FalahPro.kt)
// ─────────────────────────────────────────────────────────────────────────────

@Stable
class PrayerVisualEffectsState {
    var bgOffset by mutableFloatStateOf(0f)
        private set
    var pulseScale by mutableFloatStateOf(0.7f)
        private set

    internal fun setBgOffset(value: Float) { bgOffset = value }
    internal fun setPulseScale(value: Float) { pulseScale = value }
}

@Composable
fun rememberPrayerVisualEffects(): PrayerVisualEffectsState {
    val state = remember { PrayerVisualEffectsState() }
    LaunchedEffect(state) {
        coroutineScope {
            launch {
                val anim = Animatable(state.bgOffset)
                while (true) {
                    anim.animateTo(700f, tween(20_000, easing = LinearEasing)) {
                        state.setBgOffset(value)
                    }
                    anim.animateTo(0f, tween(20_000, easing = LinearEasing)) {
                        state.setBgOffset(value)
                    }
                }
            }
            launch {
                val anim = Animatable(state.pulseScale)
                while (true) {
                    anim.animateTo(1.2f, tween(2_000, easing = LinearEasing)) {
                        state.setPulseScale(value)
                    }
                    anim.animateTo(0.7f, tween(2_000, easing = LinearEasing)) {
                        state.setPulseScale(value)
                    }
                }
            }
        }
    }
    return state
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Passive observer of [PrayerViewModel.uiState].
 * The ViewModel is activity-scoped so tab switches do not recreate state or restart loaders.
 */
@Composable
fun PrayerTrackerScreen(
    viewModel: PrayerViewModel,
    visualEffects: PrayerVisualEffectsState
) {
    // ── ALL EXISTING LOGIC UNCHANGED ──────────────────────────────────────────
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val permissionLaunched by viewModel.permissionRequestLaunched.collectAsState()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission =
            perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    if (!hasLocationPermission && !permissionLaunched) {
        LaunchedEffect(Unit) {
            viewModel.markPermissionRequestLaunched()
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Refresh prayer times/city from live GPS whenever we have permission.
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            PrayerEngine.syncLocationIfPermitted(context)
        }
    }

    // Upcoming salah cannot stay ticked — azan has not happened yet.
    LaunchedEffect(uiState.prayerStates, uiState.prayerTimes, uiState.currentTime) {
        if (uiState.prayerTimes.isEmpty()) return@LaunchedEffect
        val now = uiState.currentTime
        uiState.prayerStates.forEach { (name, marked) ->
            if (!marked) return@forEach
            val start = uiState.prayerTimes[name] ?: return@forEach
            if (now.isBefore(start)) {
                viewModel.setPrayerCompleted(name, false)
                DataStoreManager.savePrayerState(context, name, false)
            }
        }
    }

    val prayers = PrayerConstants.PRAYER_NAMES
    val formatter = DateTimeFormatter.ofPattern("hh:mm a")

    val displayHours = uiState.remainingSeconds / 3600
    val displayMinutes = (uiState.remainingSeconds % 3600) / 60
    val displaySecs = uiState.remainingSeconds % 60

    val progress = uiState.completedCount / 5f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600),
        label = "progress"
    )

    val azanMode by DataStoreManager
        .getAzanMode(context)
        .collectAsState(initial = AzanMode.FULL_SOUND)

    val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    val pulse = visualEffects.pulseScale
    // ── END LOGIC ─────────────────────────────────────────────────────────────

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(FalahColors.Ivory)
    ) {
        val isCompact = maxWidth < 360.dp
        val hPad = if (isCompact) FalahSpacing.screenCompact else FalahSpacing.screenRegular

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PrayerScreenHeader(cityName = uiState.cityName)
            Spacer(Modifier.height(FalahSpacing.sm))
            PrayerHeroCard(
                uiState = uiState,
                displayHours = displayHours,
                displayMinutes = displayMinutes,
                displaySecs = displaySecs,
                animatedProgress = animatedProgress,
                formatter = formatter,
                hPad = hPad
            )
            Spacer(Modifier.height(FalahSpacing.md))
            AzanModeCard(
                azanMode = azanMode,
                onToggle = {
                    scope.launch {
                        val nextMode = when (azanMode) {
                            AzanMode.SILENT -> AzanMode.FULL_SOUND
                            AzanMode.FULL_SOUND -> AzanMode.NOTIFICATION_ONLY
                            AzanMode.NOTIFICATION_ONLY -> AzanMode.SILENT
                        }
                        DataStoreManager.saveAzanMode(context, nextMode)
                        PrayerNotificationManager.getInstance(context)
                            .updateChannelsForMode(nextMode)
                        PrayerEngine.rescheduleAll(context, reason = "azan_mode_changed")
                    }
                },
                modifier = Modifier.padding(horizontal = hPad)
            )
            Spacer(Modifier.height(FalahSpacing.md))
            PrayerScheduleCard(
                prayers = prayers,
                uiState = uiState,
                formatter = formatter,
                hPad = hPad,
                onPrayerClick = { prayer ->
                    val newValue = !(uiState.prayerStates[prayer] ?: false)
                    viewModel.setPrayerCompleted(prayer, newValue)
                    scope.launch {
                        DataStoreManager.savePrayerState(context, prayer, newValue)
                    }
                }
            )
            Spacer(Modifier.height(FalahSpacing.xl))
        }
    }
}

@Composable
private fun PrayerScreenHeader(cityName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                horizontal = FalahSpacing.screenRegular,
                vertical = FalahSpacing.sm
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Prayer Times",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = FalahColors.Forest
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = FalahColors.Forest,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = cityName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = FalahColors.InkBrown,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(FalahColors.WarmSand)
    )
}

@Composable
private fun PrayerHeroCard(
    uiState: PrayerUiState,
    displayHours: Int,
    displayMinutes: Int,
    displaySecs: Int,
    animatedProgress: Float,
    formatter: DateTimeFormatter,
    hPad: Dp
) {
    val prayerOrder = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    val currentTime = uiState.currentTime
    val prayerTimes = uiState.prayerTimes

    val currentPrayer = resolveCurrentPrayer(
        prayerTimes = prayerTimes,
        currentTime = currentTime,
        sunriseTime = uiState.sunriseTime
    )

    val nextPrayer = prayerOrder.firstOrNull { name ->
        prayerTimes[name]?.let { currentTime.isBefore(it) } == true
    } ?: "Fajr"

    val isTomorrowFajr = nextPrayer == "Fajr" &&
        prayerTimes["Isha"]?.let { currentTime.isAfter(it) } == true

    val heroIsCurrent = currentPrayer != null
    val heroPrayer = currentPrayer ?: nextPrayer
    val heroArabic = prayerArabicNames[heroPrayer] ?: ""
    val heroEyebrow = if (heroIsCurrent) "Current Prayer" else "Next Prayer"
    val heroCdLabel = if (heroIsCurrent) "Ends In" else "Starts In"
    val heroTime = prayerTimes[heroPrayer]?.format(formatter) ?: "--:--"
    val endForHero = currentPrayer?.let {
        prayerEndTime(it, prayerTimes, uiState.sunriseTime)
    }
    val endTimeFormatted = endForHero?.format(formatter)
    val heroSubText = when {
        heroIsCurrent && currentPrayer == "Fajr" && endTimeFormatted != null ->
            "Started at $heroTime · Ends at Sunrise $endTimeFormatted"
        heroIsCurrent && endTimeFormatted != null ->
            "Started at $heroTime · Ends at $endTimeFormatted"
        heroIsCurrent ->
            "Started at $heroTime"
        isTomorrowFajr ->
            "Starts tomorrow · $heroTime"
        else ->
            "Starts at $heroTime"
    }

    val nextPrayerTime = prayerTimes[nextPrayer]?.format(formatter) ?: "--:--"
    val nextLabel = if (isTomorrowFajr) "Fajr Tomorrow · $nextPrayerTime"
    else "$nextPrayer · $nextPrayerTime"
    val heroEndStripLabel = if (currentPrayer == "Fajr") "ENDS AT" else "NEXT UP"
    val heroEndStripValue = if (currentPrayer == "Fajr" && endTimeFormatted != null)
        "Sunrise · $endTimeFormatted"
    else nextLabel
    val heroRemainingSeconds = if (heroIsCurrent && endForHero != null) {
        secondsUntil(endForHero, currentTime)
    } else {
        displayHours * 3600 + displayMinutes * 60 + displaySecs
    }
    val cdHours = heroRemainingSeconds / 3600
    val cdMinutes = (heroRemainingSeconds % 3600) / 60
    val cdSecs = heroRemainingSeconds % 60

    val hijriDate = getHijriDateString()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = hPad)
            .clip(RoundedCornerShape(16.dp))
            .background(FalahColors.Forest)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            fun stroke(alpha: Int, widthDp: Float) = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = widthDp.dp.toPx()
                color = android.graphics.Color.argb(alpha, 196, 163, 90)
                isAntiAlias = true
                strokeJoin = android.graphics.Paint.Join.MITER
            }
            val lattice = stroke(11, 0.6f)
            val starPaint = stroke(26, 1.0f)
            val rosette = stroke(16, 0.75f)
            val native = drawContext.canvas.nativeCanvas
            val step = 56.dp.toPx()

            fun polygon(
                cx: Float,
                cy: Float,
                r: Float,
                sides: Int,
                rotDeg: Double,
                p: android.graphics.Paint
            ) {
                val path = android.graphics.Path()
                for (i in 0 until sides) {
                    val a = Math.toRadians(i * (360.0 / sides) + rotDeg)
                    val x = cx + (r * cos(a)).toFloat()
                    val y = cy + (r * sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                native.drawPath(path, p)
            }

            fun khatam(cx: Float, cy: Float, r: Float, p: android.graphics.Paint) {
                val path = android.graphics.Path()
                for (rot in listOf(0.0, 45.0)) {
                    for (i in 0 until 4) {
                        val a = Math.toRadians(i * 90.0 + rot)
                        val x = cx + (r * cos(a)).toFloat()
                        val y = cy + (r * sin(a)).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                }
                native.drawPath(path, p)
            }

            fun star8(cx: Float, cy: Float, outer: Float, inner: Float, p: android.graphics.Paint) {
                val path = android.graphics.Path()
                for (i in 0 until 16) {
                    val r = if (i % 2 == 0) outer else inner
                    val a = Math.toRadians(i * 22.5 - 90.0)
                    val x = cx + (r * cos(a)).toFloat()
                    val y = cy + (r * sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                native.drawPath(path, p)
            }

            val cols = (size.width / step + 3).toInt()
            val rows = (size.height / step + 3).toInt()

            for (row in -1..rows) {
                val y = row * step
                native.drawLine(-step, y, size.width + step, y, lattice)
            }
            for (col in -1..cols) {
                val x = col * step
                native.drawLine(x, -step, x, size.height + step, lattice)
            }
            for (i in -rows..cols + rows) {
                val x0 = i * step
                native.drawLine(x0, -step, x0 + size.height + step * 2, size.height + step, lattice)
                native.drawLine(x0, -step, x0 - size.height - step * 2, size.height + step, lattice)
            }

            for (row in 0..rows) {
                val xOff = if (row % 2 == 0) 0f else step / 2f
                for (col in 0..cols) {
                    val cx = col * step + xOff
                    val cy = row * step
                    polygon(cx, cy, 20.dp.toPx(), 8, 22.5, rosette)
                    khatam(cx, cy, 14.dp.toPx(), starPaint)
                    star8(cx, cy, 8.dp.toPx(), 3.6.dp.toPx(), rosette)
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(
                    horizontal = FalahSpacing.md,
                    vertical = FalahSpacing.sm
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = heroEyebrow,
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = FalahColors.Brass
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = heroPrayer,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = FalahColors.SoftBrass,
                                maxLines = 1
                            )
                            Text(
                                text = heroArabic,
                                style = FalahArabicTextStyle.copy(
                                    fontSize = 18.sp,
                                    lineHeight = 28.sp,
                                    color = FalahColors.SoftBrass.copy(alpha = 0.8f)
                                ),
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = heroSubText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = FalahColors.Ivory,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(FalahSpacing.sm))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = heroCdLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = FalahColors.Brass
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "%02d:%02d:%02d".format(
                                cdHours,
                                cdMinutes,
                                cdSecs
                            ),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                fontFeatureSettings = "\"tnum\""
                            ),
                            color = FalahColors.Ivory
                        )
                        Text(
                            text = "hrs · min · sec",
                            style = MaterialTheme.typography.labelSmall,
                            color = FalahColors.Ivory
                        )
                    }
                }

                Spacer(Modifier.height(FalahSpacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(FalahColors.Brass.copy(alpha = 0.15f))
                )
                Spacer(Modifier.height(FalahSpacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = currentTime.format(formatter),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Light,
                                color = FalahColors.Ivory
                            )
                            Text(
                                text = LocalDate.now().format(
                                    DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = FalahColors.Ivory
                            )
                        }
                        if (hijriDate.isNotBlank()) {
                            Text(
                                text = hijriDate,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = FalahColors.Brass
                            )
                        }
                    }

                    uiState.sunriseTime?.let { sunrise ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(FalahColors.Brass.copy(alpha = 0.1f))
                                .border(
                                    1.dp,
                                    FalahColors.Brass.copy(alpha = 0.18f),
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(
                                    horizontal = FalahSpacing.sm,
                                    vertical = 5.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WbSunny,
                                contentDescription = null,
                                tint = FalahColors.Brass,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Sunrise · ${sunrise.format(formatter)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = FalahColors.Brass
                            )
                        }
                    }
                }

                Spacer(Modifier.height(FalahSpacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Daily prayers",
                        style = MaterialTheme.typography.labelSmall,
                        color = FalahColors.Ivory
                    )
                    Text(
                        text = "${uiState.completedCount} of 5",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = FalahColors.Brass
                    )
                }
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(FalahColors.Ivory.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .clip(RoundedCornerShape(999.dp))
                            .background(FalahColors.Brass)
                    )
                }
            }

            if (heroIsCurrent) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FalahColors.Brass.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(FalahColors.Brass.copy(alpha = 0.28f))
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = FalahSpacing.md,
                                vertical = FalahSpacing.xs
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = heroEndStripLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = FalahColors.Brass
                        )
                        Text(
                            text = heroEndStripValue,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = FalahColors.SoftBrass
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AzanModeCard(
    azanMode: AzanMode,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (azanMode) {
        AzanMode.FULL_SOUND -> "Full Azan"
        AzanMode.NOTIFICATION_ONLY -> "Notifications Only"
        AzanMode.SILENT -> "Silent"
    }
    val description = when (azanMode) {
        AzanMode.FULL_SOUND -> "Full Azan audio at every prayer"
        AzanMode.NOTIFICATION_ONLY -> "Alert without Azan audio"
        AzanMode.SILENT -> "No sound or notification"
    }
    var confirmSilent by remember { mutableStateOf(false) }

    if (confirmSilent) {
        AlertDialog(
            onDismissRequest = { confirmSilent = false },
            title = { Text("Azan will be silenced") },
            text = {
                Text(
                    "You will receive no Azan sound or notification. " +
                        "Prayer times will still be tracked."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSilent = false
                        onToggle()
                    }
                ) { Text("Silence Azan") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSilent = false }) { Text("Cancel") }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FalahColors.ButterCream)
            .border(1.dp, FalahColors.WarmSand, RoundedCornerShape(14.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    // AZAN-FIX-2: Confirm before Silent so alarms are not "cancelled" by accident.
                    if (azanMode == AzanMode.NOTIFICATION_ONLY) {
                        confirmSilent = true
                    } else {
                        onToggle()
                    }
                }
            )
            .padding(FalahSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Prayer Alert",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FalahColors.InkBrown
                )
                Spacer(Modifier.height(FalahSpacing.xxs))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = FalahColors.Forest,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FalahColors.InkBrown
                )
            }
            Spacer(Modifier.width(FalahSpacing.sm))
            Box(
                modifier = Modifier
                    .background(FalahColors.SoftBrass, RoundedCornerShape(999.dp))
                    .padding(horizontal = FalahSpacing.sm, vertical = FalahSpacing.xxs)
            ) {
                Text(
                    text = "Tap to change",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = FalahColors.Forest
                )
            }
        }
    }
}

@Composable
private fun PrayerScheduleCard(
    prayers: List<String>,
    uiState: PrayerUiState,
    formatter: DateTimeFormatter,
    hPad: Dp,
    onPrayerClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = hPad)
            .clip(RoundedCornerShape(14.dp))
            .background(FalahColors.ButterCream)
            .border(1.dp, FalahColors.WarmSand, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FalahSpacing.md,
                    vertical = FalahSpacing.sm
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Schedule",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = FalahColors.Forest
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(FalahColors.Brass.copy(alpha = 0.12f))
                    .border(
                        1.dp,
                        FalahColors.Brass.copy(alpha = 0.22f),
                        RoundedCornerShape(999.dp)
                    )
                    .padding(
                        horizontal = FalahSpacing.sm,
                        vertical = FalahSpacing.xxs
                    )
            ) {
                Text(
                    text = "${uiState.completedCount} / 5 prayed",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = FalahColors.Brass
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(FalahColors.WarmSand.copy(alpha = 0.7f))
        )

        val prayerOrder = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        val currentTime = uiState.currentTime

        val currentPrayer = resolveCurrentPrayer(
            prayerTimes = uiState.prayerTimes,
            currentTime = currentTime,
            sunriseTime = uiState.sunriseTime
        )

        prayers.forEachIndexed { index, prayer ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(FalahColors.WarmSand.copy(alpha = 0.5f))
                )
            }
            val isCurrentPrayer = prayer == currentPrayer
            val isNextPrayer = prayer == uiState.nextPrayerName && !isCurrentPrayer
            val isPassed = uiState.prayerTimes[prayer]
                ?.let { currentTime.isAfter(it) } == true &&
                !isCurrentPrayer

            val endTime = prayerEndTime(
                prayer = prayer,
                prayerTimes = uiState.prayerTimes,
                sunriseTime = uiState.sunriseTime
            )

            val minutesToEnd = if (endTime != null && isCurrentPrayer) {
                secondsUntil(endTime, currentTime) / 60
            } else Int.MAX_VALUE

            val isUrgent = minutesToEnd < 60

            PrayerRow(
                prayer = prayer,
                prayerTime = uiState.prayerTimes[prayer]?.format(formatter) ?: "--:--",
                isCurrentPrayer = isCurrentPrayer,
                isNextPrayer = isNextPrayer,
                isPassed = isPassed,
                isCompleted = (isCurrentPrayer || isPassed) &&
                    uiState.prayerStates[prayer] == true,
                endTimeStr = endTime?.format(formatter),
                minutesToEnd = minutesToEnd,
                isUrgent = isUrgent,
                onClick = { onPrayerClick(prayer) }
            )
        }
    }
}

@Composable
private fun PrayerRow(
    prayer: String,
    prayerTime: String,
    isCurrentPrayer: Boolean,
    isNextPrayer: Boolean,
    isPassed: Boolean,
    isCompleted: Boolean,
    endTimeStr: String?,
    minutesToEnd: Int,
    isUrgent: Boolean,
    onClick: () -> Unit
) {
    val arabicName = prayerArabicNames[prayer] ?: ""

    val rowBg = when {
        isCurrentPrayer -> FalahColors.Ivory
        else -> Color.Transparent
    }

    val iconVector = when (prayer) {
        "Fajr" -> Icons.Outlined.NightsStay
        "Dhuhr" -> Icons.Outlined.WbSunny
        "Asr" -> Icons.Outlined.Schedule
        "Maghrib" -> Icons.Outlined.WbTwilight
        "Isha" -> Icons.Outlined.Bedtime
        else -> Icons.Outlined.Schedule
    }

    val canMark = isCurrentPrayer || isPassed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .then(
                if (canMark) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = FalahSpacing.md, vertical = FalahSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    when {
                        isCurrentPrayer -> FalahColors.Brass
                        isPassed -> FalahColors.OldMoneyGreen
                        else -> Color.Transparent
                    }
                )
        )

        Spacer(Modifier.width(FalahSpacing.sm))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        isCurrentPrayer -> FalahColors.Forest
                        isPassed -> FalahColors.OldMoneyGreen.copy(alpha = 0.12f)
                        else -> FalahColors.WarmSand.copy(alpha = 0.45f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = when {
                    isCurrentPrayer -> FalahColors.SoftBrass
                    isPassed -> FalahColors.OldMoneyGreen
                    else -> FalahColors.InkBrown
                },
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(FalahSpacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prayer,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentPrayer) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrentPrayer) FalahColors.Forest else FalahColors.InkBrown,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = arabicName,
                style = FalahArabicTextStyle.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Start,
                    color = if (isCurrentPrayer) FalahColors.Forest else FalahColors.InkBrown
                ),
                maxLines = 1
            )
            Text(
                text = prayerTime,
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrentPrayer) FalahColors.OldMoneyGreen else FalahColors.InkBrown,
                fontWeight = if (isCurrentPrayer) FontWeight.SemiBold else FontWeight.Normal
            )

            if (isCurrentPrayer && endTimeStr != null) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isUrgent) FalahColors.Danger else FalahColors.Brass
                            )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isUrgent)
                            "${minutesToEnd}min remaining"
                        else
                            "Ends at $endTimeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUrgent) FalahColors.Danger else FalahColors.InkBrown,
                        fontWeight = if (isUrgent) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.width(FalahSpacing.xs))

        if (isCurrentPrayer) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(FalahColors.Forest)
                    .padding(horizontal = FalahSpacing.sm, vertical = 3.dp)
            ) {
                Text(
                    text = "Now",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = FalahColors.SoftBrass
                )
            }
            Spacer(Modifier.width(FalahSpacing.xs))
        } else if (isNextPrayer) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(FalahColors.Brass.copy(alpha = 0.15f))
                    .border(
                        1.dp,
                        FalahColors.Brass.copy(alpha = 0.3f),
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = FalahSpacing.sm, vertical = 3.dp)
            ) {
                Text(
                    text = "Next",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = FalahColors.Brass
                )
            }
            Spacer(Modifier.width(FalahSpacing.xs))
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .then(
                    if (canMark) Modifier.clickable(onClick = onClick)
                    else Modifier
                )
                .background(
                    when {
                        isCompleted && isPassed -> FalahColors.OldMoneyGreen
                        isCompleted -> FalahColors.Forest
                        else -> Color.Transparent
                    }
                )
                .border(
                    1.5.dp,
                    if (isCompleted) Color.Transparent
                    else if (canMark) FalahColors.WarmSand
                    else FalahColors.WarmSand,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Mark $prayer prayed",
                    tint = FalahColors.Ivory,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun rememberPrayerViewModel(): PrayerViewModel {
    val activity = LocalContext.current as FalahPro
    return viewModel(viewModelStoreOwner = activity)
}
