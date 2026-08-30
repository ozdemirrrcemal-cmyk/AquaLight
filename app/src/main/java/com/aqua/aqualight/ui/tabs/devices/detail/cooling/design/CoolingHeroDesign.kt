@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Family-owned visual contract for the Cooling hero.
 *
 * The approved hero artwork is the authoritative product rendering. Compose owns only layout and
 * motion parameters around that artwork; it must not independently redraw the product geometry.
 */
object CoolingHeroGeometry {
    val screenHorizontalPadding = 16.dp
    val screenTopPadding = 12.dp
    val maximumWidth = 760.dp
    val heroShape = RoundedCornerShape(28.dp)

    const val heroAspectRatio = 1478f / 643f

    const val waterLeftRatio = 0.124f
    const val waterRightRatio = 1f
    const val waterBackLeftYRatio = 0.532f
    const val waterBackRightYRatio = 0.430f
    const val waterFrontLeftYRatio = 0.748f
    const val waterFrontRightYRatio = 0.955f
    const val waterImpactXRatio = 0.526f
    const val waterImpactYRatio = 0.625f

    const val rotorCenterXRatio = 0.339f
    const val rotorCenterYRatio = 0.207f
    const val rotorMotionRadiusXRatio = 0.073f
    const val rotorMotionRadiusYRatio = 0.054f
    const val rotorHubRadiusXRatio = 0.017f
    const val rotorHubRadiusYRatio = 0.013f
    val rotorArcStroke = 2.4.dp
    val rotorSecondaryArcStroke = 1.2.dp
}

object CoolingHeroMotion {
    const val waterLoopDurationMillis = 2_900
    const val intensityTransitionMillis = 850
    const val rotorSlowDurationMillis = 1_650
    const val rotorFastDurationMillis = 420
    const val minimumRunningFraction = 0.02f

    const val waterAmbientDisplacementPx = 0.70f
    const val waterFanDisplacementPx = 1.65f
    const val waterImpactDisplacementPx = 4.75f

    const val debugTestFanFraction = 0.72f

    const val rotorVeilBaseAlpha = 0.24f
    const val rotorVeilFanAlphaGain = 0.40f
    const val rotorPrimaryArcAlpha = 0.25f
    const val rotorSecondaryArcAlpha = 0.12f
}

object CoolingHeroMotionPalette {
    val rotorVeil = Color(0xFF02060B)
    val rotorPrimary = Color(0xFFB9C8D5)
    val rotorSecondary = Color(0xFF6E8395)
}

object CoolingHeroInteractionStyle {
    const val enabledContentAlpha = 1f
    const val disabledContentAlpha = 0.42f
}
