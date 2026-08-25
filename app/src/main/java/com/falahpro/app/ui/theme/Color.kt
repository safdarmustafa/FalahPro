package com.falahpro.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Falah Pro color tokens.
 *
 * Auth screens still use [com.falahpro.app.auth.LoginColors] until their UI phase;
 * those values already match Forest / Brass / Ivory.
 */
object FalahColors {
    val Ivory = Color(0xFFF8F4EC)
    val ButterCream = Color(0xFFF3EDE1)
    val WarmSand = Color(0xFFE6D9C6)
    val WarmBrown = Color(0xFF6B4E3D)
    val InkBrown = Color(0xFF2C211C)
    val Forest = Color(0xFF0E4032)
    val OldMoneyGreen = Color(0xFF145A45)
    val Sage = Color(0xFF2F8A68)
    val Brass = Color(0xFFC4A35A)
    val SoftBrass = Color(0xFFE8D9A8)
    val Danger = Color(0xFF9B3D3D)

    val OnPrimary = Ivory
    val Surface = ButterCream
    val Outline = WarmSand
    val NavIndicator = SoftBrass.copy(alpha = 0.55f)
}
