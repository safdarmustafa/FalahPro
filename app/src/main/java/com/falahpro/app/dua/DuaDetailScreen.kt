package com.falahpro.app.dua

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falahpro.app.dua.repository.DuaRepository
import com.falahpro.app.ui.theme.FalahArabicTextStyle
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahShapes
import com.falahpro.app.ui.theme.FalahSpacing

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
        // ── Fixed header — sits above the scroll area ─────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = FalahSpacing.screenRegular, vertical = FalahSpacing.sm)
        ) {
            // Back button
            Text(
                text = "← Back",
                style = MaterialTheme.typography.labelLarge,
                color = FalahColors.OldMoneyGreen,
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onBack
                    )
                    .padding(vertical = FalahSpacing.xxs)
            )

            Spacer(Modifier.height(FalahSpacing.xs))

            // Category breadcrumb
            Text(
                text = categoryName,
                style = MaterialTheme.typography.labelMedium,
                color = FalahColors.WarmBrown,
                letterSpacing = 0.4.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(FalahColors.WarmSand)
        )

        // ── Content ───────────────────────────────────────────────────────────
        if (dua == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "No duas available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FalahColors.WarmBrown
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FalahSpacing.screenRegular, vertical = FalahSpacing.md)
            ) {
                // Dua title
                Text(
                    text = dua.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = FalahColors.Forest,
                    lineHeight = 36.sp
                )

                Spacer(Modifier.height(FalahSpacing.md))

                // Arabic — premium reading surface
                DetailSectionCard {
                    DetailLabel("Arabic")
                    Spacer(Modifier.height(FalahSpacing.md))
                    Text(
                        text = dua.arabic,
                        style = FalahArabicTextStyle.copy(
                            fontSize = 24.sp,
                            lineHeight = 46.sp,
                            textAlign = TextAlign.Center,
                            color = FalahColors.InkBrown
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = FalahSpacing.xs)
                    )
                }

                Spacer(Modifier.height(FalahSpacing.md))

                // Transliteration
                DetailSectionCard {
                    DetailLabel("Transliteration")
                    Spacer(Modifier.height(FalahSpacing.sm))
                    Text(
                        text = dua.transliteration,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = FalahColors.InkBrown
                    )
                }

                Spacer(Modifier.height(FalahSpacing.md))

                // Translation
                DetailSectionCard {
                    DetailLabel("Translation")
                    Spacer(Modifier.height(FalahSpacing.sm))
                    Text(
                        text = dua.translation,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 26.sp
                        ),
                        color = FalahColors.WarmBrown
                    )
                }

                Spacer(Modifier.height(FalahSpacing.md))

                // When to recite
                DetailSectionCard {
                    DetailLabel("When To Recite")
                    Spacer(Modifier.height(FalahSpacing.sm))
                    Text(
                        text = dua.whenToRecite,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 26.sp
                        ),
                        color = FalahColors.InkBrown
                    )
                }

                Spacer(Modifier.height(FalahSpacing.md))

                // Reference — fixed weighted layout
                DetailSectionCard {
                    DetailLabel("Reference")
                    Spacer(Modifier.height(FalahSpacing.md))

                    ReferenceRow(label = "Book", value = dua.reference.book)
                    Spacer(Modifier.height(FalahSpacing.sm))
                    HorizontalDivider(color = FalahColors.WarmSand)
                    Spacer(Modifier.height(FalahSpacing.sm))
                    ReferenceRow(label = "Hadith", value = dua.reference.hadith)
                    Spacer(Modifier.height(FalahSpacing.sm))
                    HorizontalDivider(color = FalahColors.WarmSand)
                    Spacer(Modifier.height(FalahSpacing.sm))
                    ReferenceRow(label = "Number", value = dua.reference.number)
                }

                Spacer(Modifier.height(FalahSpacing.md))

                // Repeat count
                DetailSectionCard {
                    DetailLabel("Repeat Count")
                    Spacer(Modifier.height(FalahSpacing.sm))
                    Text(
                        text = if (dua.repeat == 1) "1 time" else "${dua.repeat} times",
                        style = MaterialTheme.typography.titleMedium,
                        color = FalahColors.Forest,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(FalahSpacing.xl))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailSectionCard(
    content: @Composable () -> Unit
) {
    // Column, not Box — Box stacks children at the same position (collision bug)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FalahShapes.Card)
            .background(FalahColors.ButterCream)
            .border(1.dp, FalahColors.WarmSand, FalahShapes.Card)
            .padding(FalahSpacing.md)
    ) {
        content()
    }
}

@Composable
private fun DetailLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = FalahColors.WarmBrown.copy(alpha = 0.70f)
    )
}

/**
 * Fixed layout: label gets 32% of the row width, value gets 68%.
 * Long book/hadith names wrap rather than overflowing.
 */
@Composable
private fun ReferenceRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = FalahColors.WarmBrown.copy(alpha = 0.65f),
            modifier = Modifier.weight(0.32f)
        )
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = FalahColors.InkBrown,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(0.68f)
                .padding(start = FalahSpacing.sm)
        )
    }
}
