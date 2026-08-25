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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falahpro.app.dua.json.JsonCategory
import com.falahpro.app.dua.repository.DuaRepository
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahShapes
import com.falahpro.app.ui.theme.FalahSpacing

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
            // Status-bar safe header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FalahSpacing.screenRegular, vertical = FalahSpacing.sm)
            ) {
                Text(
                    text = "Dua Library",
                    style = MaterialTheme.typography.titleLarge,
                    color = FalahColors.Forest
                )
                Spacer(Modifier.height(FalahSpacing.xxs))
                Text(
                    text = "Essential authentic duas for every Muslim.",
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
                        color = FalahColors.WarmBrown
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Category icon
            Text(
                text = category.icon,
                fontSize = 26.sp
            )

            Spacer(Modifier.width(FalahSpacing.md))

            // Title + description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = FalahColors.Forest,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(FalahSpacing.xxs))
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FalahColors.WarmBrown
                )
            }

            Spacer(Modifier.width(FalahSpacing.sm))

            // Count badge
            Box(
                modifier = Modifier
                    .background(FalahColors.SoftBrass.copy(alpha = 0.55f), FalahShapes.Pill)
                    .padding(horizontal = FalahSpacing.sm, vertical = FalahSpacing.xxs)
            ) {
                Text(
                    text = "$duaCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = FalahColors.WarmBrown,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
