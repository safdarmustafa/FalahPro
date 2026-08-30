package com.falahpro.app.dua

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falahpro.app.dua.repository.DuaRepository
import com.falahpro.app.ui.theme.FalahArabicTextStyle
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahSpacing
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DuaDetailScreen(
    fileName: String,
    duaId: Int,
    onBack: () -> Unit
) {
    // ── ALL LOGIC UNCHANGED ──────────────────────────────────────────────────
    val context = LocalContext.current
    val repository = remember { DuaRepository() }

    val dua = remember(fileName, duaId) {
        repository.loadDuas(context, fileName).firstOrNull { it.id == duaId }
    }

    val categoryName = remember(fileName) {
        fileName
            .removeSuffix(".json")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
    // ── END LOGIC ────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FalahColors.Ivory)
    ) {
        if (dua == null) {
            HeroHeader(
                title = "Dua",
                showBack = true,
                onBack = onBack,
                breadcrumb = categoryName
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "No duas available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FalahColors.WarmBrown,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            HeroHeader(
                title = dua.title,
                showBack = true,
                onBack = onBack,
                breadcrumb = categoryName,
                arabicCard = null
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = FalahSpacing.screenRegular,
                        vertical = FalahSpacing.md
                    ),
                verticalArrangement = Arrangement.spacedBy(FalahSpacing.sm)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FalahColors.Forest)
                        .padding(FalahSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Arabic",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp
                        ),
                        color = FalahColors.Brass,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = FalahSpacing.sm)
                    )
                    Text(
                        text = dua.arabic,
                        style = FalahArabicTextStyle.copy(
                            fontSize = 26.sp,
                            lineHeight = 52.sp,
                            color = FalahColors.SoftBrass,
                            textAlign = TextAlign.Center
                        ),
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                DetailSectionCard(label = "Transliteration") {
                    Text(
                        text = dua.transliteration,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 24.sp
                        ),
                        color = FalahColors.InkBrown
                    )
                }

                DetailSectionCard(label = "Translation") {
                    Text(
                        text = dua.translation,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 24.sp
                        ),
                        color = FalahColors.WarmBrown
                    )
                }

                DetailSectionCard(label = "When to recite") {
                    Text(
                        text = dua.whenToRecite,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 24.sp
                        ),
                        color = FalahColors.InkBrown
                    )
                }

                DetailSectionCard(label = "Reference") {
                    listOf(
                        "Book" to dua.reference.book,
                        "Hadith" to dua.reference.hadith,
                        "Number" to dua.reference.number
                    ).forEachIndexed { i, (key, value) ->
                        if (i > 0) {
                            HorizontalDivider(
                                color = FalahColors.WarmSand,
                                modifier = Modifier.padding(vertical = FalahSpacing.xs)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodyMedium,
                                color = FalahColors.WarmBrown,
                                modifier = Modifier.weight(0.35f)
                            )
                            Text(
                                text = value.ifBlank { "—" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FalahColors.InkBrown,
                                textAlign = TextAlign.End,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(0.65f)
                                    .padding(start = FalahSpacing.sm)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FalahColors.ButterCream)
                        .border(1.dp, FalahColors.WarmSand, RoundedCornerShape(12.dp))
                        .padding(FalahSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "REPEAT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp
                            ),
                            color = FalahColors.WarmBrown
                        )
                        Spacer(Modifier.height(FalahSpacing.xxs))
                        Text(
                            text = if (dua.repeat == 1) "1 time"
                            else "${dua.repeat} times",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FalahColors.Forest
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(FalahColors.Brass)
                            .padding(
                                horizontal = FalahSpacing.md,
                                vertical = FalahSpacing.sm
                            )
                    ) {
                        Text(
                            text = "Recite",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = FalahColors.Forest
                        )
                    }
                }

                Spacer(Modifier.height(FalahSpacing.xl))
            }
        }
    }
}

@Composable
private fun DetailSectionCard(
    label: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, FalahColors.WarmSand, RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(FalahColors.Forest)
                .padding(horizontal = FalahSpacing.md, vertical = FalahSpacing.xs)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp
                ),
                fontWeight = FontWeight.Bold,
                color = FalahColors.Brass,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FalahColors.ButterCream)
                .padding(FalahSpacing.md),
            content = content
        )
    }
}

@Composable
private fun HeroHeader(
    title: String,
    subtitle: String? = null,
    showBack: Boolean = false,
    breadcrumb: String? = null,
    onBack: (() -> Unit)? = null,
    arabicCard: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(FalahColors.Forest)
            .statusBarsPadding()
    ) {
        Canvas(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.TopEnd)
        ) {
            try {
                if (size.width <= 0f || size.height <= 0f) return@Canvas
                val cx = size.width * 0.55f
                val cy = size.height * 0.45f
                val paint = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1.dp.toPx()
                    color = android.graphics.Color.argb(35, 196, 163, 90)
                    isAntiAlias = true
                }
                listOf(42f, 30f, 18f).forEach { r ->
                    val path = android.graphics.Path()
                    val radiusPx = r.dp.toPx().coerceAtLeast(0f)
                    for (i in 0 until 6) {
                        val angle = Math.toRadians(i * 60.0 - 30.0)
                        val x = cx + radiusPx * cos(angle).toFloat()
                        val y = cy + radiusPx * sin(angle).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawContext.canvas.nativeCanvas.drawPath(path, paint)
                }
                listOf(0, 60, 120).forEach { deg ->
                    val angle = Math.toRadians(deg.toDouble())
                    val r = 42.dp.toPx().coerceAtLeast(0f)
                    drawContext.canvas.nativeCanvas.drawLine(
                        cx - (r * cos(angle)).toFloat(),
                        cy - (r * sin(angle)).toFloat(),
                        cx + (r * cos(angle)).toFloat(),
                        cy + (r * sin(angle)).toFloat(),
                        paint
                    )
                }
            } catch (_: Exception) {
                // Decorative only — never crash the screen
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FalahSpacing.screenRegular,
                    vertical = FalahSpacing.md
                )
        ) {
            if (showBack && onBack != null) {
                Text(
                    text = "‹  Back",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = FalahColors.Brass,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onBack
                        )
                        .padding(vertical = FalahSpacing.xxs)
                )
                Spacer(Modifier.height(FalahSpacing.xs))
            }

            if (breadcrumb != null) {
                Text(
                    text = breadcrumb.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp
                    ),
                    color = FalahColors.Brass,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(FalahSpacing.xxs))
            }

            Text(
                text = title,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FalahColors.Ivory,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Spacer(Modifier.height(FalahSpacing.xxs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = FalahColors.Ivory.copy(alpha = 0.80f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (arabicCard != null) {
                Spacer(Modifier.height(FalahSpacing.md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FalahColors.Brass.copy(alpha = 0.12f))
                        .border(
                            1.dp,
                            FalahColors.Brass.copy(alpha = 0.22f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(FalahSpacing.md)
                ) {
                    arabicCard()
                }
                Spacer(Modifier.height(FalahSpacing.xs))
            }
        }
    }
}
