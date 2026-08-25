package com.falahpro.app.ui.theme

import androidx.compose.material3.Typography as MaterialTypography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp

private val LatinFamily = FontFamily.SansSerif

/**
 * Latin UI scale. Screens should use these roles instead of ad-hoc fontSize values
 * as they are redesigned.
 */
val FalahTypography = MaterialTypography(
    displaySmall = TextStyle(
        fontFamily = LatinFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.3).sp,
        color = FalahColors.InkBrown
    ),
    headlineMedium = TextStyle(
        fontFamily = LatinFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp,
        color = FalahColors.InkBrown
    ),
    titleLarge = TextStyle(
        fontFamily = LatinFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = FalahColors.InkBrown
    ),
    titleMedium = TextStyle(
        fontFamily = LatinFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
        color = FalahColors.InkBrown
    ),
    bodyLarge = TextStyle(
        fontFamily = LatinFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        color = FalahColors.InkBrown
    ),
    bodyMedium = TextStyle(
        fontFamily = LatinFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
        color = FalahColors.WarmBrown
    ),
    labelLarge = TextStyle(
        fontFamily = LatinFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
        color = FalahColors.Forest
    ),
    labelMedium = TextStyle(
        fontFamily = LatinFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
        color = FalahColors.WarmBrown
    ),
    labelSmall = TextStyle(
        fontFamily = LatinFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
        color = FalahColors.WarmBrown
    )
)

/**
 * Arabic/RTL body style for later screen work. Uses the platform default Arabic
 * fallback (no extra font dependency). Apply with wrapping + this line height
 * so glyphs are not clipped.
 */
val FalahArabicTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 22.sp,
    lineHeight = 40.sp,
    textDirection = TextDirection.Rtl
)
