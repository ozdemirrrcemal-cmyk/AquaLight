package com.aqua.aqualight.ui.tabs.devices.detail.dosing.calibration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors

@Composable
internal fun DosingCalibrationIllustration(
    state: DosingCalibrationUiState,
    modifier: Modifier = Modifier
) {
    val cardColors = aquaDeviceCardColors()
    val colors = remember(cardColors) {
        DosingCalibrationArtColors(
            primary = cardColors.primaryText,
            secondary = cardColors.secondaryText,
            accent = cardColors.accent,
            surface = cardColors.mediaSurface,
            outline = cardColors.mediaOutline
        )
    }
    val flowTransition = rememberInfiniteTransition(label = "dosing-calibration-flow")
    val flowPhase by flowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = FLOW_CYCLE_DURATION_MS,
                easing = LinearEasing
            )
        ),
        label = "dosing-calibration-flow-phase"
    )
    val timedFill = remember { Animatable(0f) }
    val measuredFillTarget = calibrationVolumeFraction(
        parseCalibrationDecimal(state.measuredMlInput)
    )
    val measuredFill by animateFloatAsState(
        targetValue = measuredFillTarget,
        animationSpec = tween(durationMillis = MEASUREMENT_LEVEL_ANIMATION_MS),
        label = "dosing-calibration-measured-fill"
    )
    val verificationTarget = calibrationVolumeFraction(
        parseCalibrationDecimal(state.verificationMlInput)
    )

    LaunchedEffect(
        state.step,
        state.operation,
        state.calibrationDurationMs,
        state.verificationDurationMs,
        verificationTarget
    ) {
        when {
            state.operation == DosingCalibrationOperation.CALIBRATION_DOSING -> {
                timedFill.snapTo(0f)
                timedFill.animateTo(
                    targetValue = CALIBRATION_VISUAL_FILL,
                    animationSpec = tween(
                        durationMillis = state.calibrationDurationMs.toAnimationDuration(),
                        easing = LinearEasing
                    )
                )
            }
            state.operation == DosingCalibrationOperation.VERIFYING -> {
                timedFill.snapTo(0f)
                timedFill.animateTo(
                    targetValue = verificationTarget.coerceAtLeast(MIN_VERIFICATION_VISUAL_FILL),
                    animationSpec = tween(
                        durationMillis = state.verificationDurationMs.toAnimationDuration(),
                        easing = LinearEasing
                    )
                )
            }
            state.step !in setOf(
                DosingCalibrationStep.CALIBRATION_DOSE,
                DosingCalibrationStep.VERIFY_DOSE
            ) -> timedFill.snapTo(0f)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ILLUSTRATION_ASPECT_RATIO)
    ) {
        when (state.step) {
            DosingCalibrationStep.NAME -> {
                drawCalibrationBottle(colors)
                drawCalibrationTube(colors, flowPhase, active = false)
            }
            DosingCalibrationStep.PRIME -> {
                drawCalibrationBottle(colors)
                drawCalibrationTube(colors, flowPhase, active = state.primeActive)
                drawFallingDrops(colors, flowPhase, active = state.primeActive)
            }
            DosingCalibrationStep.CALIBRATION_DOSE -> {
                val active = state.operation == DosingCalibrationOperation.CALIBRATION_DOSING
                drawCalibrationBottle(colors)
                drawCalibrationTube(colors, flowPhase, active = active)
                drawCalibrationCylinder(colors, fillFraction = timedFill.value)
                drawFallingDrops(colors, flowPhase, active = active)
            }
            DosingCalibrationStep.MEASURE -> {
                drawCalibrationCylinder(colors, fillFraction = measuredFill)
            }
            DosingCalibrationStep.VERIFY_DOSE -> {
                val active = state.operation == DosingCalibrationOperation.VERIFYING
                drawCalibrationBottle(colors)
                drawCalibrationTube(colors, flowPhase, active = active)
                drawCalibrationCylinder(
                    colors = colors,
                    fillFraction = if (active) timedFill.value else 0f,
                    targetFraction = verificationTarget.takeIf { it > 0f }
                )
                drawFallingDrops(colors, flowPhase, active = active)
            }
            DosingCalibrationStep.CONFIRM -> {
                drawCalibrationCylinder(
                    colors = colors,
                    fillFraction = verificationTarget,
                    targetFraction = verificationTarget.takeIf { it > 0f }
                )
                drawCalibrationSuccessSeal(colors)
            }
        }
    }
}

private fun Long.toAnimationDuration(): Int =
    coerceIn(MIN_ANIMATION_DURATION_MS, Int.MAX_VALUE.toLong()).toInt()

private const val FLOW_CYCLE_DURATION_MS = 1_350
private const val MEASUREMENT_LEVEL_ANIMATION_MS = 420
private const val MIN_ANIMATION_DURATION_MS = 1L
private const val CALIBRATION_VISUAL_FILL = 0.68f
private const val MIN_VERIFICATION_VISUAL_FILL = 0.18f
private const val ILLUSTRATION_ASPECT_RATIO = 1.62f
