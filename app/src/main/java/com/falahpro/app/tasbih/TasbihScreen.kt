package com.falahpro.app.tasbih

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahShapes
import com.falahpro.app.ui.theme.FalahSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TasbihScreen(
    onProfileClick: () -> Unit = {},
    onReady: () -> Unit = {}
) {
    // ── ALL EXISTING LOGIC UNCHANGED ──────────────────────────────────────────
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

    LaunchedEffect(selectedDhikr) {
        DataStoreManager.getCount(context, selectedDhikr)
            .collect {
                count = it
                hasCountLoaded = true
                signalReady()
            }
    }

    // Fallback if DataStore is slow — still release splash.
    LaunchedEffect(Unit) {
        delay(450)
        hasCountLoaded = true
        signalReady()
    }
    // ── END LOGIC ─────────────────────────────────────────────────────────────

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(FalahColors.Ivory)
            .onGloballyPositioned {
                hasLaidOut = true
                signalReady()
            }
    ) {
        // Responsive breakpoints
        val isShortScreen = maxHeight < 600.dp
        val isCompactWidth = maxWidth < 360.dp
        val hPad = if (isCompactWidth) FalahSpacing.screenCompact else FalahSpacing.screenRegular

        // Counter diameter: 42% of screen width, clamped 120–190dp
        val counterSize: Dp = (maxWidth * 0.42f).coerceIn(120.dp, 190.dp)

        // Vertical spacing: reduced on short screens
        val spaceAfterGreeting = if (isShortScreen) FalahSpacing.xs else FalahSpacing.md
        val spaceAfterQuote = if (isShortScreen) FalahSpacing.xs else FalahSpacing.md
        val spaceAfterChips = if (isShortScreen) FalahSpacing.sm else FalahSpacing.lg
        val spaceAboveActions = if (isShortScreen) FalahSpacing.xs else FalahSpacing.sm

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            TasbihHeader(
                onProfileClick = onProfileClick,
                hPad = hPad
            )

            // ── Scrollable content ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = hPad),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(FalahSpacing.md))

                // Bismillah
                Text(
                    text = "بِسْمِ ٱللَّٰهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                    style = TextStyle(
                        fontSize = 22.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDirection = TextDirection.Rtl,
                        textAlign = TextAlign.Center,
                        color = FalahColors.Brass
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(FalahSpacing.xs))

                // Greeting
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FalahColors.WarmBrown,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(spaceAfterGreeting))

                // Quote card
                TasbihQuoteCard(
                    quotes = quotes,
                    quoteIndex = quoteIndex
                )

                Spacer(Modifier.height(spaceAfterQuote))

                // Dhikr selector
                DhikrSelector(
                    dhikrList = dhikrList,
                    selectedDhikr = selectedDhikr,
                    onSelect = { dhikr ->
                        selectedDhikr = dhikr
                        playClick()
                    }
                )

                Spacer(Modifier.height(spaceAfterChips))

                // Counter
                TasbihCounter(
                    count = count,
                    size = counterSize,
                    onClick = {
                        playClick()
                        count++
                        scope.launch {
                            DataStoreManager.saveCount(context, selectedDhikr, count)
                        }
                    }
                )

                Spacer(Modifier.height(spaceAboveActions))
            }

            // ── Actions — always visible, never scrolled away ─────────────────
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

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TasbihHeader(
    onProfileClick: () -> Unit,
    hPad: Dp
) {
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

        // Title is bounded so it cannot overlap the profile button
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
                // Start after the 44dp button + its gap
                .padding(horizontal = 56.dp)
                .fillMaxWidth()
        )
    }

    // Thin separator
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FalahElevationLine)
            .background(FalahColors.WarmSand)
    )
}

private val FalahElevationLine = 1.dp

// ─────────────────────────────────────────────────────────────────────────────
// Quote card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TasbihQuoteCard(
    quotes: List<Pair<String, String>>,
    quoteIndex: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FalahShapes.Card)
            .background(FalahColors.ButterCream)
            .border(1.dp, FalahColors.WarmSand, FalahShapes.Card)
            .padding(horizontal = FalahSpacing.md, vertical = FalahSpacing.sm)
    ) {
        Crossfade(targetState = quoteIndex, label = "quoteAnim") { index ->
            val quote = quotes[index]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Arabic — RTL with generous lineHeight; never clipped
                Text(
                    text = quote.first,
                    style = TextStyle(
                        fontSize = 22.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDirection = TextDirection.Rtl,
                        textAlign = TextAlign.Center,
                        color = FalahColors.Brass
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(FalahSpacing.xs))

                // Translation
                Text(
                    text = quote.second,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FalahColors.WarmBrown,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dhikr selector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DhikrSelector(
    dhikrList: List<String>,
    selectedDhikr: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FalahSpacing.xs)
    ) {
        dhikrList.forEach { dhikr ->
            DhikrChip(
                dhikr = dhikr,
                selected = dhikr == selectedDhikr,
                onClick = { onSelect(dhikr) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DhikrChip(
    dhikr: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(FalahShapes.Pill)
            .background(
                if (selected) FalahColors.Forest else FalahColors.WarmSand
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = FalahSpacing.sm, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dhikr,
            style = TextStyle(
                fontSize = 14.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textDirection = TextDirection.Rtl,
                textAlign = TextAlign.Center,
                color = if (selected) FalahColors.SoftBrass else FalahColors.WarmBrown
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Counter
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TasbihCounter(
    count: Int,
    size: Dp,
    onClick: () -> Unit
) {
    // Font scales with circle: ~32% of diameter, clamped so "9999" still fits
    val baseFontSp = (size.value * 0.28f).coerceIn(22f, 40f)
    // Shrink text for larger numbers so it stays inside
    val countStr = count.toString()
    val fontSp = when (countStr.length) {
        in 0..3 -> baseFontSp
        4 -> baseFontSp * 0.82f
        else -> baseFontSp * 0.68f
    }

    Box(
        modifier = Modifier
            .size(size)
            .shadow(4.dp, CircleShape, ambientColor = FalahColors.Forest.copy(0.12f))
            .clip(CircleShape)
            .background(FalahColors.Forest)
            .border(2.dp, FalahColors.Brass.copy(alpha = 0.45f), CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .semantics { contentDescription = "Tasbih counter, tap to increment" },
        contentAlignment = Alignment.Center
    ) {
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Actions footer (always pinned, never scrolled away)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TasbihActions(
    isMuted: Boolean,
    onReset: () -> Unit,
    onMuteToggle: () -> Unit,
    hPad: Dp
) {
    // Hairline separator above actions
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
        // Reset
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

        // Mute / UnMute
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
                text = if (isMuted) "🔇 UnMute" else "🔊 Mute",
                style = MaterialTheme.typography.labelLarge,
                color = FalahColors.WarmBrown,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile button — restyled for Falah ivory palette
// ─────────────────────────────────────────────────────────────────────────────

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
            val iconColor = Color(0xFFE8D9A8) // SoftBrass

            // Head circle
            drawCircle(
                color = iconColor,
                radius = w * 0.22f,
                center = Offset(w * 0.5f, h * 0.30f)
            )

            // Shoulders
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
