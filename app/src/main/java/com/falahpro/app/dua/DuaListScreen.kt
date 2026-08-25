package com.falahpro.app.dua

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.falahpro.app.dua.json.JsonDua
import com.falahpro.app.dua.repository.DuaRepository
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahShapes
import com.falahpro.app.ui.theme.FalahSpacing

@Composable
fun DuaListScreen(
    fileName: String,
    onBack: () -> Unit,
    onDuaClick: (JsonDua) -> Unit
) {
    // ── ALL LOGIC UNCHANGED ──────────────────────────────────────────────────
    val context = LocalContext.current
    val repository = remember { DuaRepository() }

    val duas = remember(fileName) {
        repository.loadDuas(context = context, fileName = fileName)
    }

    val categoryTitle = remember(fileName) {
        fileName
            .removeSuffix(".json")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
    // ── END LOGIC ────────────────────────────────────────────────────────────

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FalahColors.Ivory)
    ) {
        item {
            // Status-bar safe header
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

                Spacer(Modifier.height(FalahSpacing.sm))

                Text(
                    text = categoryTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = FalahColors.Forest
                )
                Spacer(Modifier.height(FalahSpacing.xxs))
                Text(
                    text = if (duas.isEmpty()) "0 Duas" else "${duas.size} Duas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FalahColors.WarmBrown
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(FalahColors.WarmSand)
            )

            Spacer(Modifier.height(FalahSpacing.md))
        }

        if (duas.isEmpty()) {
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
                        color = FalahColors.WarmBrown
                    )
                }
            }
        } else {
            items(duas, key = { it.id }) { dua ->
                DuaListItemCard(
                    dua = dua,
                    onClick = { onDuaClick(dua) }
                )
            }
        }

        item { Spacer(Modifier.height(FalahSpacing.md)) }
    }
}

@Composable
private fun DuaListItemCard(
    dua: JsonDua,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FalahSpacing.screenRegular, vertical = FalahSpacing.xs)
            .clip(FalahShapes.Card)
            .background(FalahColors.ButterCream)
            .border(1.dp, FalahColors.WarmSand, FalahShapes.Card)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(FalahSpacing.md)
    ) {
        Column {
            Text(
                text = dua.title,
                style = MaterialTheme.typography.titleMedium,
                color = FalahColors.Forest,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(FalahSpacing.xxs))
            Text(
                text = dua.whenToRecite,
                style = MaterialTheme.typography.bodyMedium,
                color = FalahColors.WarmBrown
            )
            Spacer(Modifier.height(FalahSpacing.xxs))
            Text(
                text = dua.reference.book,
                style = MaterialTheme.typography.labelSmall,
                color = FalahColors.Brass,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
