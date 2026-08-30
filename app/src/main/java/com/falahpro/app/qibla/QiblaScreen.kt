package com.falahpro.app.qibla

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.falahpro.app.core.prayer.PrayerRepository
import com.falahpro.app.location.getLastKnownLocationOrAwait
import com.falahpro.app.location.requestFreshUserLocation
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun QiblaScreen(@Suppress("UNUSED_PARAMETER") onBack: () -> Unit) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FalahColors.Ivory),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Location Required",
                    style = FalahTypography.titleMedium,
                    color = FalahColors.Forest,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Allow location access to find the Qibla direction from your current position.",
                    style = FalahTypography.bodyMedium,
                    color = FalahColors.WarmBrown
                )
            }
        }
    } else {
        PremiumCompass(onBack = onBack)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun PremiumCompass(@Suppress("UNUSED_PARAMETER") onBack: () -> Unit) {
    val context = LocalContext.current
    val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    @Suppress("DEPRECATION")
    val windowManager = remember {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    val haptic = LocalHapticFeedback.current

    var azimuth by remember { mutableFloatStateOf(0f) }
    var qiblaBearing by remember { mutableFloatStateOf(0f) }
    var hasLocation by remember { mutableStateOf(false) }
    var isLoadingLocation by remember { mutableStateOf(true) }
    var hasCompassSensor by remember { mutableStateOf(true) }
    var sensorAccuracy by remember { mutableStateOf<Int?>(null) }
    var cityName by remember { mutableStateOf("Locating…") }
    var declination by remember { mutableFloatStateOf(0f) }
    val declinationRef = remember { FloatArray(1) }
    val azimuthRef = remember { FloatArray(1) }
    var userLat by remember { mutableDoubleStateOf(0.0) }
    var userLng by remember { mutableDoubleStateOf(0.0) }
    var isAligned by remember { mutableStateOf(false) }

    val kaabaLat = 21.4225
    val kaabaLng = 39.8262

    LaunchedEffect(Unit) {
        isLoadingLocation = true

        suspend fun applyCoordinates(lat: Double, lng: Double) {
            qiblaBearing = calculateQiblaDirection(lat, lng, kaabaLat, kaabaLng)
            hasLocation = true
            userLat = lat
            userLng = lng
            val newDeclination = GeomagneticField(
                lat.toFloat(),
                lng.toFloat(),
                0f,
                System.currentTimeMillis()
            ).declination
            declinationRef[0] = newDeclination
            declination = newDeclination

            val cached = PrayerRepository.getInstance(context).getCachedCityName()
            cityName = cached ?: withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, java.util.Locale.getDefault())
                    @Suppress("DEPRECATION")
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
        }

        val lastKnown = getLastKnownLocationOrAwait(context)
        if (lastKnown != null) {
            applyCoordinates(lastKnown.first, lastKnown.second)
            isLoadingLocation = false
        }

        val fresh = requestFreshUserLocation(context)
        if (fresh != null) {
            applyCoordinates(fresh.first, fresh.second)
            isLoadingLocation = false
        } else if (lastKnown == null) {
            hasLocation = false
            cityName = "GPS unavailable"
            isLoadingLocation = false
        }
    }

    LaunchedEffect(azimuth, qiblaBearing) {
        val diff = abs(((qiblaBearing - azimuth + 180f + 360f) % 360f) - 180f)
        val wasAligned = isAligned
        isAligned = diff < 5f
        if (isAligned && !wasAligned) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    DisposableEffect(Unit) {
        val geomagneticRotationSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val usingGeoRotVec = geomagneticRotationSensor != null

        val listener = object : SensorEventListener {
            val accelValues = FloatArray(3)
            val magValues = FloatArray(3)
            val rotationMatrix = FloatArray(9)
            val remappedMatrix = FloatArray(9)
            val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        computeAzimuth()
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, accelValues, 0, 3)
                        if (!usingGeoRotVec) computeFromAccelMag()
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, magValues, 0, 3)
                        if (!usingGeoRotVec) computeFromAccelMag()
                    }
                }
            }

            fun computeFromAccelMag() {
                val success = SensorManager.getRotationMatrix(
                    rotationMatrix, null, accelValues, magValues
                )
                if (success) computeAzimuth()
            }

            @Suppress("DEPRECATION")
            fun computeAzimuth() {
                val display = windowManager.defaultDisplay
                val (axisX, axisY) = when (display.rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                if (!SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)) {
                    return
                }
                SensorManager.getOrientation(remappedMatrix, orientation)

                val rawDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                val normalizedDeg = (rawDeg + 360f) % 360f
                val trueDeg = (normalizedDeg + declinationRef[0] + 360f) % 360f

                val delta = abs(trueDeg - azimuthRef[0])
                val wrapDelta = if (delta > 180f) 360f - delta else delta
                val alpha = if (wrapDelta > 10f) 0.35f else 0.12f
                azimuthRef[0] = lowPassAngle(azimuthRef[0], trueDeg, alpha)
                azimuth = azimuthRef[0]
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR ||
                    sensor?.type == Sensor.TYPE_MAGNETIC_FIELD
                ) {
                    sensorAccuracy = accuracy
                }
            }
        }

        if (usingGeoRotVec) {
            sensorManager.registerListener(
                listener,
                geomagneticRotationSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            hasCompassSensor = true
        } else if (accelerometerSensor != null && magneticSensor != null) {
            sensorManager.registerListener(
                listener,
                accelerometerSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            sensorManager.registerListener(
                listener,
                magneticSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            hasCompassSensor = true
        } else {
            hasCompassSensor = false
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FalahColors.Ivory)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Qibla Direction",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = FalahColors.Forest
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(FalahColors.WarmSand))

            Spacer(Modifier.height(16.dp))

            TopBarRow(
                cityName = cityName,
                sensorAccuracy = sensorAccuracy,
                hasCompassSensor = hasCompassSensor,
                qiblaBearing = qiblaBearing,
                hasLocation = hasLocation
            )

            Spacer(Modifier.height(20.dp))

            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.weight(1f)
            ) {
                FalahQiblaCompass(
                    azimuth = azimuth,
                    modifier = Modifier
                        .size(300.dp)
                        .padding(top = 26.dp)
                )
                KaabaBadge(modifier = Modifier.zIndex(1f))
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.height(52.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isAligned && hasLocation && hasCompassSensor,
                    enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.9f),
                    exit = fadeOut(tween(300))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(FalahColors.SoftBrass.copy(alpha = 0.5f))
                            .border(1.dp, FalahColors.Brass, RoundedCornerShape(100.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "✦  Facing Qibla",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FalahColors.Forest
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (hasLocation) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(FalahColors.ButterCream)
                        .border(1.dp, FalahColors.WarmSand, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "📍 $cityName",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = FalahColors.InkBrown
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Makkah Al-Mukarramah",
                                fontSize = 11.sp,
                                color = FalahColors.Brass
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${qiblaBearing.roundToInt()}°",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = FalahColors.Forest,
                                lineHeight = 26.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${calculateDistanceKm(userLat, userLng, kaabaLat, kaabaLng).roundToInt()} km",
                                fontSize = 11.sp,
                                color = FalahColors.WarmBrown
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusPill(
    leading: String,
    text: String,
    textColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(FalahColors.ButterCream)
            .border(1.dp, FalahColors.WarmSand, RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = leading, fontSize = 12.sp, color = textColor)
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopBarRow(
    cityName: String,
    sensorAccuracy: Int?,
    hasCompassSensor: Boolean,
    qiblaBearing: Float,
    hasLocation: Boolean
) {
    val (accLabel, accColor) = when {
        !hasCompassSensor ->
            "No sensor" to FalahColors.Danger
        sensorAccuracy == null ->
            "Reading…" to FalahColors.WarmBrown
        sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH ->
            "High accuracy" to FalahColors.OldMoneyGreen
        sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM ->
            "Move in figure‑8" to Color(0xFF8B6000)
        else ->
            "Low accuracy" to FalahColors.Danger
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPill(
            leading = "📍",
            text = cityName,
            textColor = FalahColors.InkBrown
        )
        StatusPill(
            leading = if (sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH) "✓" else "◌",
            text = accLabel,
            textColor = accColor
        )
        StatusPill(
            leading = "🧭",
            text = if (hasLocation) "${qiblaBearing.roundToInt()}°" else "—",
            textColor = FalahColors.Forest
        )
    }
}

@Composable
private fun KaabaBadge(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(52.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = FalahColors.Brass.copy(alpha = 0.3f),
                spotColor = FalahColors.Brass.copy(alpha = 0.3f)
            )
            .clip(CircleShape)
            .background(FalahColors.ButterCream)
            .border(2.dp, FalahColors.Brass, CircleShape)
    ) {
        Text(text = "🕋", fontSize = 24.sp)
    }
}

@Composable
fun FalahQiblaCompass(
    azimuth: Float,
    modifier: Modifier = Modifier
) {
    val animatedAzimuth by animateFloatAsState(
        targetValue = -azimuth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "compass"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = size.minDimension / 2f - 2.dp.toPx()
        val dialR = outerR * 0.72f

        rotate(animatedAzimuth, Offset(cx, cy)) {
            drawCircle(
                color = Color(0xFFF3EDE1),
                radius = outerR
            )

            for (i in 0 until 360 step 5) {
                val isMajor = i % 30 == 0
                val isMed = i % 10 == 0
                val tickLen = when {
                    isMajor -> 13.dp.toPx()
                    isMed -> 7.dp.toPx()
                    else -> 4.dp.toPx()
                }
                val tickW = when {
                    isMajor -> 2.dp.toPx()
                    isMed -> 1.dp.toPx()
                    else -> 0.7.dp.toPx()
                }
                val angle = Math.toRadians(i.toDouble())
                val startR = outerR - 2.dp.toPx()
                val endR = startR - tickLen
                drawLine(
                    color = if (isMajor)
                        Color(0xFFC4A35A)
                    else
                        Color(0x556B4E3D),
                    start = Offset(
                        cx + (startR * sin(angle)).toFloat(),
                        cy - (startR * cos(angle)).toFloat()
                    ),
                    end = Offset(
                        cx + (endR * sin(angle)).toFloat(),
                        cy - (endR * cos(angle)).toFloat()
                    ),
                    strokeWidth = tickW
                )
            }

            val degPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#C4A35A")
                textSize = 11.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            val numberR = outerR * 0.855f
            for (deg in 0 until 360 step 30) {
                val a = Math.toRadians(deg.toDouble())
                val x = cx + (numberR * sin(a)).toFloat()
                val y = cy - (numberR * cos(a)).toFloat() + 4.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(deg.toString(), x, y, degPaint)
            }

            drawCircle(
                color = Color(0xFFF8F4EC),
                radius = dialR
            )

            val starPath = Path()
            val starOuter = dialR * 0.38f
            val starInner = dialR * 0.16f
            for (i in 0 until 16) {
                val r = if (i % 2 == 0) starOuter else starInner
                val a = Math.toRadians(i * 22.5 - 90.0)
                val x = cx + (r * sin(a)).toFloat()
                val y = cy - (r * cos(a)).toFloat()
                if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
            }
            starPath.close()
            drawPath(starPath, color = Color(0x18C4A35A))

            val cardinalPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#2C211C")
                textSize = 18.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            val northPaint = android.graphics.Paint(cardinalPaint).apply {
                color = android.graphics.Color.parseColor("#0E4032")
                textSize = 20.sp.toPx()
            }
            val cardinalR = dialR * 0.62f
            listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W").forEach { (deg, label) ->
                val a = Math.toRadians(deg.toDouble())
                val x = cx + (cardinalR * sin(a)).toFloat()
                val y = cy - (cardinalR * cos(a)).toFloat() + 6.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(
                    label, x, y,
                    if (deg == 0) northPaint else cardinalPaint
                )
            }

            drawCircle(
                color = Color(0xFFE6D9C6),
                radius = dialR,
                style = Stroke(1.dp.toPx())
            )
        }

        val nLen = dialR * 0.56f
        val nTail = dialR * 0.33f
        val hw = dialR * 0.10f

        val goldNeedle = Path().apply {
            moveTo(cx, cy - nLen)
            lineTo(cx + hw, cy)
            lineTo(cx - hw, cy)
            close()
        }
        drawPath(goldNeedle, color = Color(0xFFC4A35A))

        val greenTail = Path().apply {
            moveTo(cx, cy + nTail)
            lineTo(cx + hw * 0.75f, cy)
            lineTo(cx - hw * 0.75f, cy)
            close()
        }
        drawPath(greenTail, color = Color(0xFF145A45))

        drawLine(
            color = Color(0x80FFFFFF),
            start = Offset(cx, cy - nLen * 0.9f),
            end = Offset(cx, cy + nTail * 0.9f),
            strokeWidth = 1.dp.toPx()
        )

        drawCircle(
            color = Color(0xFFF3EDE1),
            radius = 8.dp.toPx(),
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color(0xFFC4A35A),
            radius = 8.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(2.dp.toPx())
        )
        drawCircle(
            color = Color(0xFFC4A35A),
            radius = 4.dp.toPx(),
            center = Offset(cx, cy)
        )

        drawCircle(
            color = Color(0xFFC4A35A),
            radius = outerR,
            style = Stroke(1.5.dp.toPx())
        )
    }
}

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

@Suppress("unused")
fun normalizeAngle(angle: Float): Float {
    var a = angle % 360
    if (a > 180) a -= 360
    if (a < -180) a += 360
    return a
}

fun lowPassAngle(prev: Float, next: Float, alpha: Float): Float {
    var diff = next - prev
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return (prev + alpha * diff + 360f) % 360f
}

@Suppress("unused")
fun calculateDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}
