package com.falahpro.app.tasbih

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falahpro.app.R
import com.falahpro.app.data.DataStoreManager
import com.falahpro.app.ui.theme.FalahArabicTextStyle
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahShapes
import com.falahpro.app.ui.theme.FalahSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TasbihScreen(
    onProfileClick: () -> Unit = {},
    onReady: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isMuted by remember { mutableStateOf(false) }
    var hasSignaledReady by remember { mutableStateOf(false) }
    var hasLaidOut by remember { mutableStateOf(false) }
    var hasCountLoaded by remember { mutableStateOf(false) }

    fun signalReady() {
        if (!hasSignaledReady && hasLaidOut && hasCountLoaded) {
            hasSignaledReady = true
            onReady()
        }
    }

    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }

    val clickSoundId = remember {
        soundPool.load(context, R.raw.tasbihclick, 1)
    }

    fun playClick() {
        if (!isMuted) {
            soundPool.play(clickSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Assalamu Alaikum, Good Morning"
        in 12..16 -> "Assalamu Alaikum, Good Afternoon"
        in 17..20 -> "Assalamu Alaikum, Good Evening"
        else -> "Peaceful Night"
    }

    val quotes = listOf(
        "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ" to
                "Surely in the remembrance of Allah do hearts find peace",
        "إِنَّ مَعَ الْعُسْرِ يُسْرًا" to
                "Indeed, with hardship comes ease"
    )

    var quoteIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(6000)
            quoteIndex = (quoteIndex + 1) % quotes.size
        }
    }

    val dhikrList = listOf(
        "سُبْحَانَ اللَّهِ",
        "الْحَمْدُ لِلَّهِ",
        "اللَّهُ أَكْبَرُ"
    )

    var selectedDhikr by remember { mutableStateOf(dhikrList[0]) }
    var count by remember { mutableIntStateOf(0) }
    var target by remember { mutableIntStateOf(33) }

    LaunchedEffect(selectedDhikr) {
        DataStoreManager.getCount(context, selectedDhikr)
            .collect {
                count = it
                hasCountLoaded = true
                signalReady()
            }
    }

    LaunchedEffect(Unit) {
        delay(450)
        hasCountLoaded = true
        signalReady()
    }

    val safeTarget = if (target <= 0) 33 else target
    val filledCount = when {
        count == 0 -> 0
        count % safeTarget == 0 -> safeTarget
        else -> count % safeTarget
    }
    val progress = filledCount.toFloat() / safeTarget
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "tasbih_progress"
    )
    val milestone = when {
        count > 0 && count % safeTarget == 0 -> "✦ ${count / safeTarget}× complete ✦"
        count == 33 -> "SubhanAllah ×33"
        count == 66 -> "Alhamdulillah ×33"
        count == 99 -> "Allahu Akbar ×33"
        else -> ""
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(FalahColors.Ivory)
            .onGloballyPositioned {
                hasLaidOut = true
                signalReady()
            }
    ) {
        val isCompact = maxWidth < 360.dp
        val hPad = if (isCompact) FalahSpacing.screenCompact else FalahSpacing.screenRegular

        Column(modifier = Modifier.fillMaxSize()) {
            TasbihHeader(
                onProfileClick = onProfileClick,
                hPad = hPad
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = hPad),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(FalahSpacing.xs))

                Text(
                    text = "بِسْمِ ٱللَّٰهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                    style = FalahArabicTextStyle.copy(
                        fontSize = 24.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = FalahColors.Brass
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodySmall,
                    color = FalahColors.WarmBrown,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(FalahSpacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FalahSpacing.xs)
                ) {
                    dhikrList.forEach { dhikr ->
                        val selected = dhikr == selectedDhikr
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) FalahColors.Forest else FalahColors.WarmSand
                                )
                                .border(
                                    1.dp,
                                    if (selected) FalahColors.Brass else FalahColors.WarmSand,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    selectedDhikr = dhikr
                                    playClick()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dhikr,
                                style = FalahArabicTextStyle.copy(
                                    fontSize = 16.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) FalahColors.SoftBrass
                                    else FalahColors.InkBrown
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    BoxWithConstraints {
                        val ringFit = minOf(maxWidth, maxHeight) * 0.94f
                        TasbihBeadRing(
                            count = count,
                            filledCount = filledCount,
                            size = ringFit.coerceIn(150.dp, 230.dp),
                            onClick = {
                                playClick()
                                count++
                                scope.launch {
                                    DataStoreManager.saveCount(context, selectedDhikr, count)
                                }
                            }
                        )
                    }
                }

                if (milestone.isNotBlank()) {
                    Text(
                        text = milestone,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FalahColors.Brass,
                            textAlign = TextAlign.Center,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FalahSpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(FalahColors.WarmSand)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animProgress.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(999.dp))
                                .background(FalahColors.Brass)
                        )
                    }
                    Text(
                        text = "$filledCount / $safeTarget",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FalahColors.Forest
                        )
                    )
                }

                Spacer(Modifier.height(FalahSpacing.xs))

                Row(horizontalArrangement = Arrangement.spacedBy(FalahSpacing.xs)) {
                    listOf(33, 99, 100).forEach { t ->
                        val selected = target == t
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (selected) FalahColors.Brass else FalahColors.WarmSand
                                )
                                .border(
                                    1.dp,
                                    if (selected) FalahColors.Brass else FalahColors.WarmSand,
                                    RoundedCornerShape(999.dp)
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { target = t }
                                .padding(horizontal = 20.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$t",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) FalahColors.Forest
                                    else FalahColors.InkBrown
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(FalahSpacing.xs))

                TasbihQuoteCard(
                    arabic = quotes[quoteIndex].first,
                    translation = quotes[quoteIndex].second
                )

                Spacer(Modifier.height(FalahSpacing.xs))
            }

            TasbihActions(
                isMuted = isMuted,
                onReset = {
                    playClick()
                    count = 0
                    scope.launch {
                        DataStoreManager.saveCount(context, selectedDhikr, 0)
                    }
                },
                onMuteToggle = { isMuted = !isMuted },
                hPad = hPad
            )
        }
    }
}

@Composable
private fun TasbihQuoteCard(
    arabic: String,
    translation: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FalahColors.ButterCream)
            .border(1.dp, FalahColors.WarmSand, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FalahSpacing.md, vertical = FalahSpacing.xs)
        ) {
            Text(
                text = arabic,
                style = FalahArabicTextStyle.copy(
                    fontSize = 20.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = FalahColors.Brass
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = translation,
                style = TextStyle(
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = FalahColors.InkBrown
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TasbihBeadRing(
    count: Int,
    filledCount: Int,
    size: Dp,
    onClick: () -> Unit
) {
    val beadCount = 33
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val rPx = this.size.minDimension * 0.42f
            val native = drawContext.canvas.nativeCanvas
            val lattice = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 0.7.dp.toPx()
                color = android.graphics.Color.argb(28, 196, 163, 90)
                isAntiAlias = true
            }
            val step = 18.dp.toPx()
            for (i in -2..8) {
                native.drawLine(-step, i * step, this.size.width + step, i * step, lattice)
                native.drawLine(i * step, -step, i * step, this.size.height + step, lattice)
            }

            drawCircle(
                color = Color(0xFFC4A35A).copy(alpha = 0.28f),
                radius = rPx,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx())
            )

            val shownFilled = filledCount.coerceAtMost(beadCount)
            for (i in 0 until beadCount) {
                val angle = (i.toFloat() / beadCount) * 2f * PI.toFloat() - PI.toFloat() / 2f
                val x = cx + rPx * cos(angle)
                val y = cy + rPx * sin(angle)
                val isFilled = i < shownFilled
                val beadR = if (isFilled) 8.dp.toPx() else 6.5.dp.toPx()
                drawCircle(
                    color = Color(0x28000000),
                    radius = beadR,
                    center = Offset(x + 1.2f, y + 1.4f)
                )
                drawCircle(
                    color = if (isFilled) Color(0xFFC4A35A) else Color(0xFFD8CBB4),
                    radius = beadR,
                    center = Offset(x, y)
                )
                drawCircle(
                    color = if (isFilled) Color(0xFF9A7B30) else Color(0xFFB9A88A),
                    radius = beadR,
                    center = Offset(x, y),
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = Color(0x66FFFFFF),
                    radius = beadR * 0.28f,
                    center = Offset(x - beadR * 0.28f, y - beadR * 0.28f)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(size * 0.46f)
                .shadow(8.dp, CircleShape, ambientColor = FalahColors.Forest.copy(alpha = 0.22f))
                .clip(CircleShape)
                .background(FalahColors.Forest)
                .border(2.dp, FalahColors.Brass, CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick
                )
                .semantics { contentDescription = "Tasbih counter, tap to increment" },
            contentAlignment = Alignment.Center
        ) {
            val countStr = count.toString()
            val fontSp = when (countStr.length) {
                in 0..2 -> 36f
                3 -> 30f
                4 -> 24f
                else -> 20f
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = countStr,
                    style = TextStyle(
                        fontSize = fontSp.sp,
                        fontWeight = FontWeight.Bold,
                        color = FalahColors.Ivory,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.5).sp
                    )
                )
                Text(
                    text = "TAP",
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = FalahColors.SoftBrass,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

@Composable
private fun TasbihHeader(
    onProfileClick: () -> Unit,
    hPad: Dp
) {
    Column(modifier = Modifier.fillMaxWidth().background(FalahColors.Ivory)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = hPad, vertical = FalahSpacing.sm)
        ) {
            TasbihProfileButton(
                onClick = onProfileClick,
                modifier = Modifier.align(Alignment.CenterStart)
            )

            Text(
                text = "Tasbih Counter",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = FalahColors.Forest,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp)
                    .fillMaxWidth()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FalahElevationLine)
                .background(FalahColors.WarmSand)
        )
    }
}

private val FalahElevationLine = 1.dp

@Composable
private fun TasbihActions(
    isMuted: Boolean,
    onReset: () -> Unit,
    onMuteToggle: () -> Unit,
    hPad: Dp
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FalahElevationLine)
                .background(FalahColors.WarmSand)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FalahColors.ButterCream)
                .padding(horizontal = hPad, vertical = FalahSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(FalahSpacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(FalahShapes.Input)
                    .background(FalahColors.WarmSand)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onReset
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelLarge,
                    color = FalahColors.WarmBrown
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(FalahShapes.Input)
                    .background(
                        if (isMuted) FalahColors.WarmBrown.copy(alpha = 0.10f)
                        else FalahColors.WarmSand
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onMuteToggle
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isMuted) "Unmute" else "Mute",
                    style = MaterialTheme.typography.labelLarge,
                    color = FalahColors.WarmBrown,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TasbihProfileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .shadow(2.dp, CircleShape, ambientColor = FalahColors.Forest.copy(0.15f))
            .clip(CircleShape)
            .background(FalahColors.Forest)
            .border(1.5.dp, FalahColors.Brass.copy(alpha = 0.50f), CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .semantics { contentDescription = "Profile" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val iconColor = Color(0xFFE8D9A8)

            drawCircle(
                color = iconColor,
                radius = w * 0.22f,
                center = Offset(w * 0.5f, h * 0.30f)
            )

            val torso = Path().apply {
                moveTo(w * 0.15f, h * 0.92f)
                cubicTo(w * 0.15f, h * 0.60f, w * 0.32f, h * 0.53f, w * 0.5f, h * 0.53f)
                cubicTo(w * 0.68f, h * 0.53f, w * 0.85f, h * 0.60f, w * 0.85f, h * 0.92f)
                close()
            }
            drawPath(torso, color = iconColor, style = Fill)
        }
    }
}
