package com.falahpro.app.dua

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falahpro.app.dua.json.JsonCategory
import com.falahpro.app.dua.repository.DuaRepository
import com.falahpro.app.ui.theme.FalahArabicTextStyle
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahSpacing
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DuaLibraryScreen(
    onCategoryClick: (JsonCategory) -> Unit
) {
    // ── ALL LOGIC UNCHANGED ──────────────────────────────────────────────────
    val context = LocalContext.current
    val repository = remember { DuaRepository() }
    val categories = remember { repository.loadCategories(context) }
    // ── END LOGIC ────────────────────────────────────────────────────────────

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FalahColors.Ivory)
    ) {
        item {
            HeroHeader(
                title = "Dua Library",
                subtitle = "Authentic supplications for every Muslim"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FalahColors.Forest)
                    .padding(horizontal = FalahSpacing.screenRegular)
                    .padding(bottom = FalahSpacing.sm)
            ) {
                Text(
                    text = "ادْعُوا رَبَّكُمْ تَضَرُّعًا وَخُفْيَةً",
                    style = FalahArabicTextStyle.copy(
                        fontSize = 13.sp,
                        color = FalahColors.Brass.copy(alpha = 0.75f),
                        textAlign = TextAlign.End
                    ),
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(FalahSpacing.md))
        }

        if (categories.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FalahSpacing.screenRegular)
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No duas available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FalahColors.WarmBrown,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            items(categories, key = { it.id }) { category ->
                val duaCount = remember(category.file) {
                    repository.loadDuas(context, category.file).size
                }

                CategoryCard(
                    category = category,
                    duaCount = duaCount,
                    onClick = { onCategoryClick(category) }
                )
            }
        }

        item { Spacer(Modifier.height(FalahSpacing.md)) }
    }
}

@Composable
private fun CategoryCard(
    category: JsonCategory,
    duaCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FalahSpacing.screenRegular)
            .padding(vertical = FalahSpacing.xxs)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = FalahColors.Forest.copy(alpha = 0.08f),
                spotColor = FalahColors.Forest.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(FalahColors.ButterCream)
            .border(1.dp, FalahColors.WarmSand, RoundedCornerShape(12.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(64.dp)
                .background(FalahColors.Brass)
        )

        Spacer(Modifier.width(FalahSpacing.sm))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(FalahColors.Forest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = category.icon,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }

        Spacer(Modifier.width(FalahSpacing.sm))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = FalahSpacing.md),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = FalahColors.Forest,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(FalahSpacing.xxs))
            Text(
                text = category.description,
                style = MaterialTheme.typography.bodySmall,
                color = FalahColors.WarmBrown,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.width(FalahSpacing.sm))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(FalahColors.Forest)
                .padding(horizontal = FalahSpacing.sm, vertical = FalahSpacing.xxs)
        ) {
            Text(
                text = "$duaCount",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = FalahColors.SoftBrass,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(FalahSpacing.md))
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
