package com.falahpro.app.prayer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.falahpro.app.FalahPro
import com.falahpro.app.core.notification.PrayerNotificationManager
import com.falahpro.app.core.scheduler.PrayerEngine
import com.falahpro.app.core.util.PrayerConstants
import com.falahpro.app.data.AzanMode
import com.falahpro.app.data.DataStoreManager
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahShapes
import com.falahpro.app.ui.theme.FalahSpacing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
            PrayerScreenHeader(hPad = hPad)

            Spacer(Modifier.height(FalahSpacing.sm))

            // Reliability banner — renders nothing when all permissions are OK
            PrayerReliabilityBanner()

            Spacer(Modifier.height(FalahSpacing.lg))

            PrayerHeroSection(
                progressDiameter = progressDiameter,
                innerDiameter = innerDiameter,
                animatedProgress = animatedProgress,
                pulse = pulse,
                nextPrayerName = uiState.nextPrayerName,
                displayHours = displayHours,
                displayMinutes = displayMinutes,
                displaySecs = displaySecs,
                currentTime = uiState.currentTime,
                cityName = uiState.cityName,
                sunriseTime = uiState.sunriseTime,
                hasLocationPermission = hasLocationPermission,
                formatter = formatter,
                hPad = hPad
            )

            Spacer(Modifier.height(FalahSpacing.xl))

            // Azan mode — all scheduling calls unchanged
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

            // Prayer schedule — toggle/state calls unchanged
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
private fun PrayerScreenHeader(hPad: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = hPad, vertical = FalahSpacing.sm)
    ) {
        Text(
            text = "Prayer Times",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = FalahColors.Forest
        )
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
private fun PrayerHeroSection(
    progressDiameter: Dp,
    innerDiameter: Dp,
    animatedProgress: Float,
    pulse: Float,
    nextPrayerName: String,
    displayHours: Int,
    displayMinutes: Int,
    displaySecs: Int,
    currentTime: LocalTime,
    cityName: String,
    sunriseTime: LocalTime?,
    hasLocationPermission: Boolean,
    formatter: DateTimeFormatter,
    hPad: Dp
) {
    // Pulse ring stays inside the outer progress circle
    val pulseDiameter = (innerDiameter * pulse.coerceIn(0.7f, 1.1f))
        .coerceAtMost(progressDiameter - 8.dp)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        Box(
            modifier = Modifier.size(progressDiameter),
            contentAlignment = Alignment.Center
        ) {
            // Subtle breathing ring — uses pulseScale from activity-scoped effects
            Box(
                modifier = Modifier
                    .size(pulseDiameter)
                    .background(FalahColors.Forest.copy(alpha = 0.07f), CircleShape)
            )

            // Progress arc — existing progress calculation unchanged
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(progressDiameter),
                strokeWidth = 5.dp,
                color = FalahColors.Brass,
                trackColor = FalahColors.WarmSand.copy(alpha = 0.45f)
            )

            // Inner circle with next prayer + countdown
            Box(
                modifier = Modifier
                    .size(innerDiameter)
                    .shadow(4.dp, CircleShape, ambientColor = FalahColors.Forest.copy(0.14f))
                    .clip(CircleShape)
                    .background(FalahColors.Forest),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = FalahSpacing.sm)
                ) {
                    Text(
                        text = nextPrayerName,
                        style = MaterialTheme.typography.titleMedium,
                        color = FalahColors.SoftBrass,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(FalahSpacing.xxs))
                    Text(
                        text = "%02d:%02d:%02d".format(displayHours, displayMinutes, displaySecs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FalahColors.Ivory.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(FalahSpacing.md))

        // Current time
        Text(
            text = currentTime.format(formatter),
            style = MaterialTheme.typography.titleMedium,
            color = FalahColors.InkBrown,
            fontWeight = FontWeight.Light
        )

        Spacer(Modifier.height(FalahSpacing.xxs))

        // City name
        Text(
            text = cityName,
            style = MaterialTheme.typography.bodyMedium,
            color = FalahColors.WarmBrown,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!hasLocationPermission) {
            Spacer(Modifier.height(FalahSpacing.xxs))
            Text(
                text = "Allow location for accurate prayer times",
                style = MaterialTheme.typography.labelSmall,
                color = FalahColors.WarmBrown.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = hPad)
            )
        }

        sunriseTime?.let { sunrise ->
            Spacer(Modifier.height(FalahSpacing.xxs))
            Text(
                text = "Sunrise  ${sunrise.format(formatter)}",
                style = MaterialTheme.typography.labelSmall,
                color = FalahColors.WarmBrown.copy(alpha = 0.60f)
            )
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
            .clip(FalahShapes.Card)
            .background(FalahColors.ButterCream)
            .border(1.dp, FalahColors.WarmSand, FalahShapes.Card)
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
                    .background(FalahColors.SoftBrass.copy(alpha = 0.55f), FalahShapes.Pill)
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = hPad)
            .clip(FalahShapes.Card)
            .background(FalahColors.ButterCream)
            .border(1.dp, FalahColors.WarmSand, FalahShapes.Card)
    ) {
        Column {
            prayers.forEachIndexed { index, prayer ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(FalahColors.WarmSand.copy(alpha = 0.6f))
                    )
                }
                PrayerRow(
                    prayer = prayer,
                    time = uiState.prayerTimes[prayer]?.format(formatter) ?: "--:--",
                    isNext = prayer == uiState.nextPrayerName,
                    isCompleted = uiState.prayerStates[prayer] == true,
                    onClick = { onPrayerClick(prayer) }
                )
            }
        }
    }
}

@Composable
private fun PrayerRow(
    prayer: String,
    time: String,
    isNext: Boolean,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isNext) FalahColors.Forest.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = FalahSpacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prayer,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isNext) FalahColors.Forest else FalahColors.InkBrown,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium,
                color = if (isNext) FalahColors.OldMoneyGreen else FalahColors.WarmBrown
            )
        }

        if (isNext) {
            Box(
                modifier = Modifier
                    .background(FalahColors.SoftBrass.copy(alpha = 0.6f), FalahShapes.Pill)
                    .padding(horizontal = FalahSpacing.sm, vertical = FalahSpacing.xxs)
            ) {
                Text(
                    text = "Next",
                    style = MaterialTheme.typography.labelSmall,
                    color = FalahColors.WarmBrown
                )
            }
            Spacer(Modifier.width(FalahSpacing.sm))
        }

        // Completion dot — toggles via existing setPrayerCompleted / DataStore call
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    if (isCompleted) FalahColors.Forest else Color.Transparent,
                    CircleShape
                )
                .then(
                    if (!isCompleted)
                        Modifier.border(1.5.dp, FalahColors.WarmSand, CircleShape)
                    else
                        Modifier
                )
        )
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
