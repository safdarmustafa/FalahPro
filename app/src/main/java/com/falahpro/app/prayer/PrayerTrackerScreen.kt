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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
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
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahSpacing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

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

        // Responsive hero circle: 52% of width, clamped 160–220dp
        val progressDiameter = (maxWidth * 0.52f).coerceIn(160.dp, 220.dp)
        val innerDiameter = progressDiameter * 0.72f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PrayerScreenHeader(cityName = uiState.cityName)

            PrayerReliabilityBanner()

            Spacer(Modifier.height(FalahSpacing.sm))

            PrayerHeroCard(
                nextPrayerName = uiState.nextPrayerName,
                prayerTimes = uiState.prayerTimes,
                currentTime = uiState.currentTime,
                displayHours = displayHours,
                displayMinutes = displayMinutes,
                displaySecs = displaySecs,
                animatedProgress = animatedProgress,
                completedCount = uiState.completedCount,
                cityName = uiState.cityName,
                sunriseTime = uiState.sunriseTime,
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

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

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
                tint = FalahColors.WarmBrown,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = cityName,
                style = MaterialTheme.typography.labelSmall,
                color = FalahColors.WarmBrown,
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

// ─────────────────────────────────────────────────────────────────────────────
// Hero
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PrayerHeroCard(
    nextPrayerName: String,
    prayerTimes: Map<String, LocalTime>,
    currentTime: LocalTime,
    displayHours: Int,
    displayMinutes: Int,
    displaySecs: Int,
    animatedProgress: Float,
    completedCount: Int,
    cityName: String,
    sunriseTime: LocalTime?,
    formatter: DateTimeFormatter,
    hPad: Dp
) {
    val isTomorrowFajr = nextPrayerName == "Fajr" &&
        prayerTimes["Isha"]?.let { currentTime.isAfter(it) } == true
    val prayerLabel = if (isTomorrowFajr) "Fajr · Tomorrow" else nextPrayerName

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = hPad)
            .clip(RoundedCornerShape(16.dp))
            .background(FalahColors.Forest)
    ) {
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            val paint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 0.8.dp.toPx()
                color = android.graphics.Color.argb(18, 196, 163, 90)
                isAntiAlias = true
            }
            val stepX = 40.dp.toPx()
            val stepY = 40.dp.toPx()
            val cols = (size.width / stepX + 2).toInt()
            val rows = (size.height / stepY + 2).toInt()
            for (row in 0..rows) {
                for (col in 0..cols) {
                    val cx = col * stepX
                    val cy = row * stepY
                    val r1 = 18.dp.toPx()
                    val r2 = 12.dp.toPx()
                    val path = android.graphics.Path()
                    for (i in 0 until 6) {
                        val angle = Math.toRadians(i * 60.0 - 30.0)
                        val x = cx + (r1 * cos(angle)).toFloat()
                        val y = cy + (r1 * sin(angle)).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawContext.canvas.nativeCanvas.drawPath(path, paint)
                    val path2 = android.graphics.Path()
                    for (i in 0 until 6) {
                        val angle = Math.toRadians(i * 60.0 - 30.0)
                        val x = cx + (r2 * cos(angle)).toFloat()
                        val y = cy + (r2 * sin(angle)).toFloat()
                        if (i == 0) path2.moveTo(x, y) else path2.lineTo(x, y)
                    }
                    path2.close()
                    drawContext.canvas.nativeCanvas.drawPath(path2, paint)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FalahSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "NEXT PRAYER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.sp
                        ),
                        color = FalahColors.Brass
                    )
                    Spacer(Modifier.height(FalahSpacing.xxs))
                    Text(
                        text = prayerLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = FalahColors.SoftBrass,
                        lineHeight = 30.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Starts at ${prayerTimes[nextPrayerName]
                            ?.format(formatter) ?: "--:--"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FalahColors.Ivory
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "TIME LEFT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.sp
                        ),
                        color = FalahColors.Brass
                    )
                    Spacer(Modifier.height(FalahSpacing.xxs))
                    Text(
                        text = "%02d:%02d:%02d".format(
                            displayHours, displayMinutes, displaySecs
                        ),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            fontFeatureSettings = "\"tnum\""
                        ),
                        color = FalahColors.Ivory
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "hrs · min · sec",
                        style = MaterialTheme.typography.labelSmall,
                        color = FalahColors.Ivory
                    )
                }
            }

            Spacer(Modifier.height(FalahSpacing.md))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(FalahColors.Brass.copy(alpha = 0.15f))
            )
            Spacer(Modifier.height(FalahSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = currentTime.format(formatter),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Light,
                        color = FalahColors.Ivory
                    )
                    Text(
                        text = LocalDate.now().format(
                            DateTimeFormatter.ofPattern("EEEE, d MMM")
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = FalahColors.Ivory
                    )
                }
                sunriseTime?.let { sunrise ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.WbSunny,
                            contentDescription = null,
                            tint = FalahColors.Brass,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = sunrise.format(formatter),
                            style = MaterialTheme.typography.labelSmall,
                            color = FalahColors.Brass
                        )
                    }
                }
            }

            Spacer(Modifier.height(FalahSpacing.md))
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
                    text = "$completedCount of 5",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = FalahColors.Brass
                )
            }
            Spacer(Modifier.height(FalahSpacing.xxs))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(FalahColors.Ivory.copy(alpha = 0.1f))
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Azan mode card
// ─────────────────────────────────────────────────────────────────────────────

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FalahColors.ButterCream)
            .border(1.dp, FalahColors.WarmSand, RoundedCornerShape(14.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle
            )
            .padding(FalahSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Prayer Alert",
                    style = MaterialTheme.typography.labelMedium,
                    color = FalahColors.WarmBrown
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
                    color = FalahColors.WarmBrown
                )
            }
            Spacer(Modifier.width(FalahSpacing.sm))
            Box(
                modifier = Modifier
                    .background(FalahColors.SoftBrass.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                    .padding(horizontal = FalahSpacing.sm, vertical = FalahSpacing.xxs)
            ) {
                Text(
                    text = "Tap to change",
                    style = MaterialTheme.typography.labelSmall,
                    color = FalahColors.WarmBrown
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Prayer schedule
// ─────────────────────────────────────────────────────────────────────────────

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
                        FalahColors.Brass.copy(alpha = 0.2f),
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

        val inWindow = currentWindowPrayer(
            prayerTimes = uiState.prayerTimes,
            sunriseTime = uiState.sunriseTime,
            now = uiState.currentTime
        )
        val endTimePrayer = inWindow ?: uiState.nextPrayerName.takeIf { it != "—" }

        prayers.forEachIndexed { index, prayer ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(FalahColors.WarmSand.copy(alpha = 0.5f))
                )
            }
            PrayerRow(
                prayer = prayer,
                prayerTimes = uiState.prayerTimes,
                sunriseTime = uiState.sunriseTime,
                isNext = prayer == uiState.nextPrayerName,
                isCompleted = uiState.prayerStates[prayer] == true,
                currentTime = uiState.currentTime,
                showEndTime = prayer == endTimePrayer,
                formatter = formatter,
                onClick = { onPrayerClick(prayer) }
            )
        }
    }
}

@Composable
private fun PrayerRow(
    prayer: String,
    prayerTimes: Map<String, LocalTime>,
    sunriseTime: LocalTime?,
    isNext: Boolean,
    isCompleted: Boolean,
    currentTime: LocalTime,
    showEndTime: Boolean,
    formatter: DateTimeFormatter,
    onClick: () -> Unit
) {
    val prayerTime = prayerTimes[prayer]
    val endTime = endTimeForPrayer(prayer, prayerTimes, sunriseTime)
    val isPassed = windowHasEnded(prayer, prayerTime, endTime, currentTime)

    val minutesToEnd = if (endTime != null) {
        val nowSec = currentTime.toSecondOfDay()
        val endSec = endTime.toSecondOfDay()
        val diff = if (endSec > nowSec) endSec - nowSec
        else (24 * 3600 - nowSec) + endSec
        diff / 60
    } else Int.MAX_VALUE
    val isUrgent = showEndTime && minutesToEnd < 60

    val iconVector = when (prayer) {
        "Fajr" -> Icons.Outlined.NightsStay
        "Dhuhr" -> Icons.Outlined.WbSunny
        "Asr" -> Icons.Outlined.Schedule
        "Maghrib" -> Icons.Outlined.WbTwilight
        "Isha" -> Icons.Outlined.Bedtime
        else -> Icons.Outlined.Schedule
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isNext -> FalahColors.Ivory
                    isPassed -> Color.Transparent
                    else -> Color.Transparent
                }
            )
            .then(
                if (isNext) Modifier.drawWithContent {
                    drawContent()
                    drawRect(
                        color = FalahColors.Brass,
                        topLeft = Offset.Zero,
                        size = Size(3.dp.toPx(), size.height)
                    )
                } else Modifier
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(
                horizontal = FalahSpacing.md,
                vertical = FalahSpacing.sm
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isNext) FalahColors.Forest
                    else FalahColors.WarmSand.copy(alpha = 0.45f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = if (isNext) FalahColors.SoftBrass
                else FalahColors.WarmBrown,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(FalahSpacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prayer,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isNext -> FalahColors.Forest
                    isPassed -> FalahColors.WarmBrown
                    else -> FalahColors.InkBrown
                },
                modifier = if (isPassed)
                    Modifier.alpha(0.55f) else Modifier
            )
            Text(
                text = prayerTime?.format(formatter) ?: "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = if (isNext) FalahColors.OldMoneyGreen
                else FalahColors.WarmBrown,
                modifier = if (isPassed)
                    Modifier.alpha(0.55f) else Modifier
            )

            if (showEndTime && endTime != null) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isUrgent) FalahColors.Danger
                                else FalahColors.Brass
                            )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isUrgent)
                            "${minutesToEnd}min remaining"
                        else
                            "Ends ${endTime.format(formatter)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUrgent) FalahColors.Danger
                        else FalahColors.WarmBrown,
                        fontWeight = if (isUrgent) FontWeight.Bold
                        else FontWeight.Normal
                    )
                }
            }
        }

        if (isNext) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(FalahColors.Brass)
                    .padding(
                        horizontal = FalahSpacing.sm,
                        vertical = FalahSpacing.xxs
                    )
            ) {
                Text(
                    text = "Next",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = FalahColors.Forest
                )
            }
            Spacer(Modifier.width(FalahSpacing.xs))
        }

        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (isCompleted) FalahColors.Forest
                    else Color.Transparent
                )
                .border(
                    width = 1.5.dp,
                    color = if (isCompleted) FalahColors.Forest
                    else FalahColors.WarmSand,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = FalahColors.Ivory,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

private fun endTimeForPrayer(
    prayer: String,
    prayerTimes: Map<String, LocalTime>,
    sunriseTime: LocalTime?
): LocalTime? = when (prayer) {
    "Fajr" -> sunriseTime
    "Dhuhr" -> prayerTimes["Asr"]
    "Asr" -> prayerTimes["Maghrib"]
    "Maghrib" -> prayerTimes["Isha"]
    "Isha" -> prayerTimes["Fajr"]
    else -> null
}

private fun isWithinWindow(
    now: LocalTime,
    start: LocalTime,
    end: LocalTime,
    wrapsMidnight: Boolean
): Boolean {
    return if (!wrapsMidnight) {
        !now.isBefore(start) && now.isBefore(end)
    } else {
        !now.isBefore(start) || now.isBefore(end)
    }
}

private fun currentWindowPrayer(
    prayerTimes: Map<String, LocalTime>,
    sunriseTime: LocalTime?,
    now: LocalTime
): String? {
    for (name in PrayerConstants.PRAYER_NAMES) {
        val start = prayerTimes[name] ?: continue
        val end = endTimeForPrayer(name, prayerTimes, sunriseTime) ?: continue
        if (isWithinWindow(now, start, end, wrapsMidnight = name == "Isha")) {
            return name
        }
    }
    return null
}

private fun windowHasEnded(
    prayer: String,
    start: LocalTime?,
    end: LocalTime?,
    now: LocalTime
): Boolean {
    if (start == null || end == null) return false
    return if (prayer == "Isha") {
        now.isBefore(start) && !now.isBefore(end)
    } else {
        !now.isBefore(end)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel helper — UNCHANGED
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun rememberPrayerViewModel(): PrayerViewModel {
    val activity = LocalContext.current as FalahPro
    return viewModel(viewModelStoreOwner = activity)
}
