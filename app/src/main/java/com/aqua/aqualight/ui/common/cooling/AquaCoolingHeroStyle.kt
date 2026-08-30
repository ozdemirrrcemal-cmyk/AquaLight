@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.common.cooling

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Pixel-stable visual contract for the Cooling device hero. */
object AquaCoolingHeroGeometry {
    const val aspectRatio = 1.22f
    val minimumHeight = 276.dp
    val maximumHeight = 332.dp
}

/**
 * Cooling intentionally uses the same dark industrial language as Dose Pro while reserving cyan
 * for live airflow/status energy and glass/water speculars.
 */
object AquaCoolingHeroPalette {
    val metalShadow = Color(0xE6000000)
    val metalDeep = Color(0xFF11151B)
    val metalDark = Color(0xFF252B33)
    val metalMid = Color(0xFF4A515C)
    val metalLight = Color(0xFF858D98)
    val metalHighlight = Color(0xFFD8DDE4)

    val ventBlack = Color(0xFF05080C)
    val ventCell = Color(0xFF171D24)
    val ventEdge = Color(0xFF67717C)

    val rotorBlack = Color(0xFF030609)
    val rotorMid = Color(0xFF20262E)
    val rotorBlade = Color(0xFF717A86)
    val rotorHighlight = Color(0xFFC5CBD3)

    val accent = Color(0xFF41E5D1)
    val accentBright = Color(0xFFA6FFF4)
    val accentDeep = Color(0xFF138F89)

    val glass = Color(0x2449E6F2)
    val glassEdge = Color(0x995DE8F2)
    val glassHighlight = Color(0xCCB9FAFF)

    val waterDeep = Color(0xFF031522)
    val waterMid = Color(0xFF073044)
    val waterSurface = Color(0xFF0B5364)
    val waterSpecular = Color(0xB9BDFBFF)
    val waterCyan = Color(0xA95DECF0)
    val waterGreen = Color(0x6658A46F)

    val cable = Color(0xFF11151B)
    val cableHighlight = Color(0xFF5B626C)
    val sensorMetal = Color(0xFF9CA5AF)
}
