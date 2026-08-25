package com.falahpro.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
object FalahRadii {
    val small = 8.dp
    val input = 14.dp
    val card = 20.dp
    val pill = 999.dp
}

object FalahShapes {
    val Small = RoundedCornerShape(FalahRadii.small)
    val Input = RoundedCornerShape(FalahRadii.input)
    val Card = RoundedCornerShape(FalahRadii.card)
    val Pill = RoundedCornerShape(FalahRadii.pill)
    val Circle = CircleShape
}

val FalahMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(FalahRadii.small),
    small = RoundedCornerShape(FalahRadii.small),
    medium = RoundedCornerShape(FalahRadii.input),
    large = RoundedCornerShape(FalahRadii.card),
    extraLarge = RoundedCornerShape(FalahRadii.card)
)
