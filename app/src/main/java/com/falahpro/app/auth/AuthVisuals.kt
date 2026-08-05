package com.falahpro.app.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

internal object LoginColors {
    val EmeraldDeep = Color(0xFF0A3328)
    val Emerald = Color(0xFF145A45)
    val EmeraldMid = Color(0xFF1F6B52)
    val EmeraldSoft = Color(0xFF2F8A68)
    val Forest = Color(0xFF0E4032)
    val Gold = Color(0xFFC4A35A)
    val GoldSoft = Color(0xFFE8D9A8)
    val Ivory = Color(0xFFF8F4EC)
    val Cream = Color(0xFFF3EDE1)
    val Mist = Color(0xFFE4EDE7)
    val TextPrimary = Color(0xFF102820)
    val TextSecondary = Color(0xFF4A5C54)
    val TextMuted = Color(0xFF6B7A72)

    val DarkBgTop = Color(0xFF061510)
    val DarkBgMid = Color(0xFF0B241C)
    val DarkBgBottom = Color(0xFF12352A)
    val DarkCard = Color(0xE6143228)
    val DarkText = Color(0xFFF5F0E6)
    val DarkMuted = Color(0xFFA8B8B0)
}

@Composable
internal fun AuthHeroBadge(
    floatOffset: Float,
    glowPulse: Float,
    darkTheme: Boolean = isSystemInDarkTheme()
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(220.dp)
            .offset(y = floatOffset.dp)
    ) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            LoginColors.GoldSoft.copy(alpha = glowPulse * 0.9f),
                            LoginColors.EmeraldSoft.copy(alpha = glowPulse * 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (darkTheme) {
                                LoginColors.EmeraldMid.copy(alpha = 0.35f)
                            } else {
                                Color.White.copy(alpha = 0.55f)
                            },
                            Color.Transparent
                        )
                    )
                )
        )
        PremiumIslamicHero(
            modifier = Modifier.size(168.dp),
            darkTheme = darkTheme,
            glowPulse = glowPulse
        )
    }
}

@Composable
internal fun PremiumIslamicHero(
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
    glowPulse: Float
) {
    val lineColor = if (darkTheme) {
        LoginColors.GoldSoft.copy(alpha = 0.9f)
    } else {
        LoginColors.EmeraldDeep.copy(alpha = 0.88f)
    }
    val fillColor = if (darkTheme) {
        LoginColors.EmeraldSoft.copy(alpha = 0.22f)
    } else {
        LoginColors.Emerald.copy(alpha = 0.10f)
    }
    val accent = LoginColors.Gold.copy(alpha = 0.55f + glowPulse * 0.35f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val baseY = h * 0.78f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(cx, baseY),
                radius = w * 0.42f
            ),
            radius = w * 0.42f,
            center = Offset(cx, baseY)
        )

        val crescentCenter = Offset(cx + w * 0.22f, h * 0.22f)
        drawCircle(
            color = accent,
            radius = w * 0.07f,
            center = crescentCenter,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(
            color = if (darkTheme) LoginColors.DarkBgMid else LoginColors.Ivory,
            radius = w * 0.055f,
            center = Offset(crescentCenter.x + w * 0.02f, crescentCenter.y - w * 0.01f)
        )

        drawStarOrnament(
            center = Offset(cx - w * 0.28f, h * 0.18f),
            radius = 5.dp.toPx(),
            color = accent.copy(alpha = 0.7f)
        )
        drawStarOrnament(
            center = Offset(cx + w * 0.08f, h * 0.12f),
            radius = 3.5.dp.toPx(),
            color = accent.copy(alpha = 0.55f)
        )
        drawStarOrnament(
            center = Offset(cx - w * 0.12f, h * 0.28f),
            radius = 2.8.dp.toPx(),
            color = accent.copy(alpha = 0.45f)
        )

        val domeWidth = w * 0.34f
        val domeLeft = cx - domeWidth / 2f
        val domePath = Path().apply {
            moveTo(domeLeft, baseY - h * 0.18f)
            quadraticTo(cx, baseY - h * 0.42f, domeLeft + domeWidth, baseY - h * 0.18f)
            lineTo(domeLeft + domeWidth, baseY)
            lineTo(domeLeft, baseY)
            close()
        }
        drawPath(domePath, color = fillColor)
        drawPath(domePath, color = lineColor, style = Stroke(width = 2.dp.toPx()))

        drawLine(
            color = lineColor,
            start = Offset(cx, baseY - h * 0.42f),
            end = Offset(cx, baseY - h * 0.50f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = accent,
            radius = 3.dp.toPx(),
            center = Offset(cx, baseY - h * 0.52f)
        )

        drawMinaret(
            centerX = cx - w * 0.28f,
            baseY = baseY,
            height = h * 0.38f,
            width = w * 0.045f,
            lineColor = lineColor,
            fillColor = fillColor,
            accent = accent
        )
        drawMinaret(
            centerX = cx + w * 0.28f,
            baseY = baseY,
            height = h * 0.38f,
            width = w * 0.045f,
            lineColor = lineColor,
            fillColor = fillColor,
            accent = accent
        )

        drawRoundRect(
            color = fillColor,
            topLeft = Offset(cx - w * 0.38f, baseY),
            size = Size(w * 0.76f, h * 0.05f),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(cx - w * 0.38f, baseY),
            size = Size(w * 0.76f, h * 0.05f),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(width = 1.6.dp.toPx())
        )
    }
}

private fun DrawScope.drawMinaret(
    centerX: Float,
    baseY: Float,
    height: Float,
    width: Float,
    lineColor: Color,
    fillColor: Color,
    accent: Color
) {
    val left = centerX - width / 2f
    drawRoundRect(
        color = fillColor,
        topLeft = Offset(left, baseY - height),
        size = Size(width, height),
        cornerRadius = CornerRadius(width / 2f)
    )
    drawRoundRect(
        color = lineColor,
        topLeft = Offset(left, baseY - height),
        size = Size(width, height),
        cornerRadius = CornerRadius(width / 2f),
        style = Stroke(width = 1.6.dp.toPx())
    )
    drawCircle(
        color = accent,
        radius = width * 0.55f,
        center = Offset(centerX, baseY - height),
        style = Stroke(width = 1.8.dp.toPx())
    )
    drawLine(
        color = lineColor,
        start = Offset(centerX, baseY - height - width * 0.55f),
        end = Offset(centerX, baseY - height - width * 1.3f),
        strokeWidth = 1.6.dp.toPx(),
        cap = StrokeCap.Round
    )
}

@Composable
internal fun IslamicPatternBackground(
    modifier: Modifier = Modifier,
    darkTheme: Boolean
) {
    val top = if (darkTheme) LoginColors.DarkBgTop else LoginColors.Ivory
    val mid = if (darkTheme) LoginColors.DarkBgMid else LoginColors.Cream
    val bottom = if (darkTheme) LoginColors.DarkBgBottom else LoginColors.Mist
    val pattern = if (darkTheme) {
        LoginColors.Gold.copy(alpha = 0.045f)
    } else {
        LoginColors.Emerald.copy(alpha = 0.04f)
    }
    val ornament = if (darkTheme) {
        LoginColors.GoldSoft.copy(alpha = 0.06f)
    } else {
        LoginColors.Gold.copy(alpha = 0.05f)
    }
    val glow = if (darkTheme) {
        LoginColors.EmeraldSoft.copy(alpha = 0.22f)
    } else {
        LoginColors.EmeraldSoft.copy(alpha = 0.16f)
    }

    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(top, mid, bottom)
            )
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow, Color.Transparent),
                center = Offset(size.width * 0.18f, size.height * 0.12f),
                radius = size.minDimension * 0.75f
            ),
            radius = size.minDimension * 0.75f,
            center = Offset(size.width * 0.18f, size.height * 0.12f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    LoginColors.GoldSoft.copy(alpha = if (darkTheme) 0.10f else 0.14f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.85f, size.height * 0.28f),
                radius = size.minDimension * 0.55f
            ),
            radius = size.minDimension * 0.55f,
            center = Offset(size.width * 0.85f, size.height * 0.28f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    LoginColors.Forest.copy(alpha = if (darkTheme) 0.28f else 0.08f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.5f, size.height * 0.95f),
                radius = size.width * 0.7f
            ),
            radius = size.width * 0.7f,
            center = Offset(size.width * 0.5f, size.height * 0.95f)
        )

        val step = 88.dp.toPx()
        var y = step
        var row = 0
        while (y < size.height * 0.72f) {
            var x = if (row % 2 == 0) step * 0.5f else step
            while (x < size.width) {
                drawCircle(
                    color = pattern,
                    radius = 7.dp.toPx(),
                    center = Offset(x, y),
                    style = Stroke(width = 1.dp.toPx())
                )
                if ((row + (x / step).toInt()) % 3 == 0) {
                    drawStarOrnament(
                        center = Offset(x, y),
                        radius = 14.dp.toPx(),
                        color = ornament
                    )
                }
                x += step
            }
            y += step
            row++
        }

        val silhouette = if (darkTheme) {
            LoginColors.EmeraldSoft.copy(alpha = 0.10f)
        } else {
            LoginColors.EmeraldDeep.copy(alpha = 0.06f)
        }
        val baseY = size.height * 0.90f
        val skyline = Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, baseY)
            lineTo(size.width * 0.10f, baseY)
            lineTo(size.width * 0.15f, baseY - 40.dp.toPx())
            lineTo(size.width * 0.20f, baseY)
            lineTo(size.width * 0.35f, baseY)
            lineTo(size.width * 0.40f, baseY - 22.dp.toPx())
            lineTo(size.width * 0.45f, baseY - 64.dp.toPx())
            lineTo(size.width * 0.50f, baseY - 22.dp.toPx())
            lineTo(size.width * 0.55f, baseY)
            lineTo(size.width * 0.72f, baseY)
            lineTo(size.width * 0.78f, baseY - 36.dp.toPx())
            lineTo(size.width * 0.84f, baseY)
            lineTo(size.width, baseY)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(skyline, color = silhouette)
    }
}

private fun DrawScope.drawStarOrnament(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path()
    val points = 8
    for (i in 0 until points * 2) {
        val angle = (Math.PI / points) * i - Math.PI / 2
        val r = if (i % 2 == 0) radius else radius * 0.42f
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color, style = Stroke(width = 1.dp.toPx()))
}
