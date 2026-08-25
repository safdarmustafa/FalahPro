package com.falahpro.app.qibla

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.falahpro.app.R
import com.falahpro.app.core.prayer.PrayerRepository
import com.falahpro.app.location.getUserLocation
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahShapes
import com.falahpro.app.ui.theme.FalahSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun QiblaScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // ── ALL PERMISSION LOGIC UNCHANGED ───────────────────────────────────────
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    // ── END LOGIC ─────────────────────────────────────────────────────────────

    if (!hasPermission) {
        // Permission denied state — premium presentation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FalahColors.Ivory),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(FalahSpacing.screenRegular)
            ) {
                Text(
                    text = "📍",
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(Modifier.height(FalahSpacing.md))
                Text(
                    text = "Location Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = FalahColors.Forest,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(FalahSpacing.xs))
                Text(
                    text = "Allow location access to find the Qibla direction from your current position.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FalahColors.WarmBrown
                )
            }
        }
    } else {
        PremiumCompass(onBack = onBack)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compass screen — all sensor/calculation logic UNCHANGED
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@Composable
fun PremiumCompass(onBack: () -> Unit) {
    val context = LocalContext.current
    val sensorManager =
        context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager

    // ── ALL LOGIC UNCHANGED ──────────────────────────────────────────────────
    var azimuth by remember { mutableFloatStateOf(0f) }
    var qiblaBearing by remember { mutableFloatStateOf(0f) }
    var hasLocation by remember { mutableStateOf(false) }
    var isLoadingLocation by remember { mutableStateOf(true) }
    var hasCompassSensor by remember { mutableStateOf(true) }
    var sensorAccuracy by remember { mutableStateOf<Int?>(null) }
    var cityName by remember { mutableStateOf("Locating…") }
    var declination by remember { mutableFloatStateOf(0f) }

    val kaabaLat = 21.4225
    val kaabaLng = 39.8262

    LaunchedEffect(Unit) {
        isLoadingLocation = true
        val loc = getUserLocation(context)
        if (loc != null) {
            val (lat, lng) = loc
            qiblaBearing = calculateQiblaDirection(lat, lng, kaabaLat, kaabaLng)
            hasLocation = true
            declination = GeomagneticField(
                lat.toFloat(),
                lng.toFloat(),
                0f,
                System.currentTimeMillis()
            ).declination

            val cached = PrayerRepository.getInstance(context).getCachedCityName()
            cityName = cached ?: withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, java.util.Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    if (!addresses.isNullOrEmpty()) {
                        addresses[0].locality
                            ?: addresses[0].subAdminArea
                            ?: addresses[0].adminArea
                            ?: "Unknown"
                    } else {
                        "Unknown"
                    }
                } catch (_: Exception) {
                    "Unknown"
                }
            }
        } else {
            hasLocation = false
            cityName = "GPS unavailable"
        }
        isLoadingLocation = false
    }

    DisposableEffect(declination) {
        val rotationVector: Sensor? =
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer: Sensor? =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer: Sensor? =
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var haveGravity = false
        var haveGeomagnetic = false

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        fun publish(rawMagneticDeg: Float) {
            val trueDeg = (rawMagneticDeg + declination + 360f) % 360f
            azimuth = lowPassAngle(azimuth, trueDeg)
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        publish((deg + 360f) % 360f)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        haveGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        haveGeomagnetic = true
                    }
                }

                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR &&
                    haveGravity && haveGeomagnetic
                ) {
                    if (SensorManager.getRotationMatrix(
                            rotationMatrix, null, gravity, geomagnetic
                        )
                    ) {
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        publish((deg + 360f) % 360f)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR ||
                    sensor?.type == Sensor.TYPE_MAGNETIC_FIELD
                ) {
                    sensorAccuracy = accuracy
                }
            }
        }

        when {
            rotationVector != null -> {
                sensorManager.registerListener(
                    listener, rotationVector, SensorManager.SENSOR_DELAY_GAME
                )
                hasCompassSensor = true
            }
            accelerometer != null && magnetometer != null -> {
                sensorManager.registerListener(
                    listener, accelerometer, SensorManager.SENSOR_DELAY_GAME
                )
                sensorManager.registerListener(
                    listener, magnetometer, SensorManager.SENSOR_DELAY_GAME
                )
                hasCompassSensor = true
            }
            else -> {
                hasCompassSensor = false
            }
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val animatedCompassRotation by animateFloatAsState(
        targetValue = -azimuth,
        animationSpec = tween(200),
        label = "compassRotation"
    )

    val angleToQibla = normalizeAngle(qiblaBearing - azimuth)
    val isAligned = abs(angleToQibla) < 5

    val accuracy = sensorAccuracy
    val accuracyLabel = when {
        !hasCompassSensor -> "No compass"
        accuracy == null -> "Checking…"
        accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Good accuracy"
        accuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Fair accuracy"
        accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low accuracy"
        else -> "Calibrate device"
    }

    val accuracyIcon = when {
        !hasCompassSensor -> "⚠"
        accuracy == null -> "…"
        accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "✓"
        else -> "!"
    }
    // ── END LOGIC ─────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FalahColors.Ivory)
            .statusBarsPadding()
            .padding(horizontal = FalahSpacing.screenRegular)
            .padding(top = FalahSpacing.xs, bottom = FalahSpacing.md)
    ) {
        QiblaTopBar(onBack = onBack)

        Row(
            horizontalArrangement = Arrangement.spacedBy(FalahSpacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            StatusChip(icon = "📍", label = cityName)
            StatusChip(icon = accuracyIcon, label = accuracyLabel)
            if (hasLocation) {
                StatusChip(icon = "🧭", label = "${qiblaBearing.roundToInt()}°")
            }
        }

        Spacer(Modifier.height(FalahSpacing.lg))

        // Responsive compass — fills available space, clamped to a safe range
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Derive compass size from available constraints
            val rawSize = if (maxWidth < maxHeight * 0.9f) maxWidth else maxHeight * 0.9f
            val compassSize = rawSize.coerceIn(220.dp, 300.dp)

            QiblaCompass(
                compassRotation = animatedCompassRotation,
                qiblaBearing = qiblaBearing,
                isAligned = isAligned,
                compassSize = compassSize,
                modifier = Modifier.size(compassSize)
            )
        }

        // Direction instruction
        Text(
            text = when {
                isLoadingLocation -> "Getting your location…"
                !hasLocation -> "Enable GPS to find Qibla direction"
                !hasCompassSensor -> "Compass sensor not available"
                isAligned -> "Facing Qibla ✦"
                else -> "Rotate ${abs(angleToQibla).toInt()}° ${
                    if (angleToQibla > 0) "clockwise" else "counter-clockwise"
                }"
            },
            style = if (isAligned) {
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = when {
                isLoadingLocation -> FalahColors.WarmBrown
                !hasLocation -> FalahColors.Danger
                !hasCompassSensor -> FalahColors.Danger
                isAligned -> FalahColors.Forest
                else -> FalahColors.WarmBrown
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = FalahSpacing.sm)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QiblaTopBar(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Back button — Forest circle + Brass border
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .shadow(2.dp, CircleShape, ambientColor = FalahColors.Forest.copy(0.15f))
                    .clip(CircleShape)
                    .background(FalahColors.Forest)
                    .border(1.5.dp, FalahColors.Brass.copy(alpha = 0.5f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack
                    )
            ) {
                BackChevronIcon(
                    modifier = Modifier.size(20.dp),
                    color = FalahColors.SoftBrass
                )
            }

            Spacer(Modifier.width(FalahSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Qibla",
                    style = MaterialTheme.typography.titleLarge,
                    color = FalahColors.Forest,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Direction to the Kaaba",
                    style = MaterialTheme.typography.labelMedium,
                    color = FalahColors.WarmBrown,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }

        Spacer(Modifier.height(FalahSpacing.md))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(FalahColors.WarmSand)
        )

        Spacer(Modifier.height(FalahSpacing.md))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Back chevron icon — drawing logic unchanged
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BackChevronIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = 2.2.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.62f, size.height * 0.18f)
            lineTo(size.width * 0.32f, size.height * 0.5f)
            lineTo(size.width * 0.62f, size.height * 0.82f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusChip(icon: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(FalahShapes.Pill)
            .background(FalahColors.ButterCream)
            .border(1.dp, FalahColors.WarmSand, FalahShapes.Pill)
            .padding(horizontal = FalahSpacing.sm, vertical = FalahSpacing.xs)
    ) {
        Text(icon, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(FalahSpacing.xxs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FalahColors.InkBrown,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compass — size is now passed as parameter, derived from BoxWithConstraints
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QiblaCompass(
    compassRotation: Float,
    qiblaBearing: Float,
    isAligned: Boolean,
    compassSize: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    // Kaaba radius: 39.3% of compass diameter (was hardcoded 118dp / 300dp ≈ 0.393)
    val kaabaRadiusPx = with(density) { (compassSize * 0.393f).toPx() }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = compassRotation }
        ) {
            CompassDial(modifier = Modifier.fillMaxSize())

            CompassCardinals(
                compassSize = compassSize,
                modifier = Modifier.fillMaxSize()
            )

            Image(
                painter = painterResource(R.drawable.kaaba),
                contentDescription = "Kaaba",
                modifier = Modifier
                    .size(if (isAligned) 44.dp else 36.dp)
                    .align(Alignment.Center)
                    .offset {
                        val rad = Math.toRadians(qiblaBearing.toDouble())
                        IntOffset(
                            (kaabaRadiusPx * sin(rad)).roundToInt(),
                            (-kaabaRadiusPx * cos(rad)).roundToInt()
                        )
                    }
            )
        }

        FixedCompassNeedle(modifier = Modifier.fillMaxSize())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compass dial — geometry unchanged, only colors updated to Falah palette
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompassDial(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension / 2f
        val ringWidth = 14.dp.toPx()
        val innerRadius = outerRadius - ringWidth - 4.dp.toPx()

        // Outer ring
        drawCircle(
            color = FalahColors.WarmSand,
            radius = outerRadius - ringWidth / 2f,
            style = Stroke(ringWidth)
        )

        // Tick marks — geometry unchanged
        for (degree in 0 until 360 step 30) {
            val rad = Math.toRadians(degree.toDouble())
            val isMajor = degree % 90 == 0
            val tickStart = outerRadius - ringWidth - (if (isMajor) 10.dp.toPx() else 6.dp.toPx())
            val tickEnd = outerRadius - ringWidth - 2.dp.toPx()
            val start = Offset(
                center.x + tickStart * sin(rad).toFloat(),
                center.y - tickStart * cos(rad).toFloat()
            )
            val end = Offset(
                center.x + tickEnd * sin(rad).toFloat(),
                center.y - tickEnd * cos(rad).toFloat()
            )
            drawLine(
                color = FalahColors.WarmBrown,
                start = start,
                end = end,
                strokeWidth = if (isMajor) 2.5f else 1.5f
            )
        }

        // Compass face — ButterCream instead of gold
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(FalahColors.ButterCream, FalahColors.WarmSand),
                center = center,
                radius = innerRadius
            ),
            radius = innerRadius,
            center = center
        )

        // Concentric ring pattern
        val patternStep = innerRadius / 5f
        for (i in 1..4) {
            drawCircle(
                color = FalahColors.WarmBrown.copy(alpha = 0.12f),
                radius = patternStep * i,
                center = center,
                style = Stroke(1.2f)
            )
        }
        // Diagonal pattern lines
        for (angle in 0 until 360 step 45) {
            rotate(angle.toFloat(), center) {
                drawLine(
                    color = FalahColors.WarmBrown.copy(alpha = 0.08f),
                    start = Offset(center.x, center.y - innerRadius * 0.85f),
                    end = Offset(center.x, center.y + innerRadius * 0.85f),
                    strokeWidth = 1f
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cardinal letters — offsets derived proportionally from compassSize
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompassCardinals(compassSize: Dp, modifier: Modifier = Modifier) {
    // 52dp / 300dp ≈ 0.173 — maintains identical visual position at any size
    val cardinalOffset = compassSize * 0.173f

    Box(modifier = modifier) {
        Text(
            "N",
            color = FalahColors.Danger,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = cardinalOffset)
        )
        Text(
            "S",
            color = FalahColors.WarmBrown,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -cardinalOffset)
        )
        Text(
            "E",
            color = FalahColors.WarmBrown,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = -cardinalOffset)
        )
        Text(
            "W",
            color = FalahColors.WarmBrown,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = cardinalOffset)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fixed needle — geometry unchanged, colors updated to Falah palette
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FixedCompassNeedle(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val needleLength = size.minDimension * 0.28f
        val needleWidth = 18.dp.toPx()

        val needlePath = Path().apply {
            moveTo(center.x, center.y - needleLength)
            lineTo(center.x + needleWidth / 2f, center.y)
            lineTo(center.x, center.y + needleLength * 0.55f)
            lineTo(center.x - needleWidth / 2f, center.y)
            close()
        }
        drawPath(needlePath, color = FalahColors.Brass.copy(alpha = 0.45f))

        val northPointer = Path().apply {
            moveTo(center.x, center.y - needleLength - 6.dp.toPx())
            lineTo(center.x + 7.dp.toPx(), center.y - needleLength + 4.dp.toPx())
            lineTo(center.x - 7.dp.toPx(), center.y - needleLength + 4.dp.toPx())
            close()
        }
        drawPath(northPointer, color = FalahColors.Danger)

        drawCircle(color = FalahColors.Ivory, radius = 6.dp.toPx(), center = center)
        drawCircle(color = FalahColors.Danger, radius = 3.dp.toPx(), center = center)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Math functions — UNCHANGED
// ─────────────────────────────────────────────────────────────────────────────

fun calculateQiblaDirection(
    userLat: Double,
    userLng: Double,
    kaabaLat: Double,
    kaabaLng: Double
): Float {
    val lat1 = Math.toRadians(userLat)
    val lat2 = Math.toRadians(kaabaLat)
    val deltaLng = Math.toRadians(kaabaLng - userLng)

    val y = sin(deltaLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLng)

    val bearing = Math.toDegrees(atan2(y, x))
    return ((bearing + 360) % 360).toFloat()
}

fun normalizeAngle(angle: Float): Float {
    var a = angle % 360
    if (a > 180) a -= 360
    if (a < -180) a += 360
    return a
}

/** Smooths azimuth updates while correctly handling the 0/360 wrap-around. */
fun lowPassAngle(prev: Float, next: Float, alpha: Float = 0.15f): Float {
    var diff = next - prev
    while (diff > 180f) diff -= 360f
    while (diff < -180f) diff += 360f
    return ((prev + alpha * diff) + 360f) % 360f
}
