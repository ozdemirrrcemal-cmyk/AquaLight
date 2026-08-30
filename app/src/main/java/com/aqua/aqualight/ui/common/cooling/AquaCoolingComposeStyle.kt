@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.common.cooling

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R

/** Central visual contract shared by every Cooling catalog surface. */
object AquaCoolingPalette {
    val surface = Color(0xFF101D2C)
    val surfaceRaised = Color(0xFF162A3E)
    val outline = Color(0xFF22354D)
    val outlineSoft = Color(0xFF183143)
    val textPrimary = Color(0xFFF4F8FC)
    val textSecondary = Color(0xFF9AAEC4)
    val textMuted = Color(0xFF7B8794)
    val accent = Color(0xFF2196F3)
    val cyan = Color(0xFF40C7F4)
    val success = Color(0xFF5FD6B4)
    val disabled = Color(0xFF263A49)
}

object AquaCoolingGeometry {
    val screenHorizontalPadding = 16.dp
    val screenTopPadding = 6.dp
    val screenBottomPadding = 14.dp
    val sectionGap = 8.dp
    val cardGap = 8.dp
    val summaryCardHeight = 132.dp
    val bottomCardHeight = 76.dp
    val fourCardMinWidth = 456.dp
    val bottomRowMinWidth = 440.dp
    val heroHeight = 228.dp
    val chartCardHeight = 146.dp
}

private val InterRegular = FontFamily(Font(R.font.inter_regular))
private val InterSemiBold = FontFamily(Font(R.font.inter_semibold))

fun aquaCoolingTextStyle(
    size: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    semiBold: Boolean = false,
    textAlign: TextAlign = TextAlign.Start
): TextStyle = TextStyle(
    color = color,
    fontFamily = if (semiBold) InterSemiBold else InterRegular,
    fontSize = size,
    lineHeight = lineHeight,
    textAlign = textAlign
)
