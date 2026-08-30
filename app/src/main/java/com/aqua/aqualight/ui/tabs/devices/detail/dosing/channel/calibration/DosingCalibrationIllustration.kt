package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowColors

/** Step-aware calibration visual assembled from small, reusable drawing primitives. */
@Composable
internal fun DosingCalibrationIllustration(
    step: DeviceDosingCalibrationStep,
    colors: AquaGuidedFlowColors,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    operationDurationMillis: Int = CALIBRATION_DEFAULT_OPERATION_MILLIS
) {
    val transition = rememberInfiniteTransition(label = "dosing-calibration-illustration")
    val flowPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = ONE,
        animationSpec = infiniteRepeatable(
            animation = tween(CALIBRATION_FLOW_CYCLE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dosing-calibration-flow"
    )
    val fillProgress by animateFloatAsState(
        targetValue = if (active) ONE else 0f,
        animationSpec = tween(
            durationMillis = operationDurationMillis.coerceIn(
                CALIBRATION_MIN_OPERATION_MILLIS,
                CALIBRATION_MAX_OPERATION_MILLIS
            ),
            easing = LinearEasing
        ),
        label = "dosing-calibration-fill"
    )
    val description = stringResource(step.illustrationDescriptionRes)

    Canvas(
        modifier = modifier.semantics { contentDescription = description }
    ) {
        drawCalibrationScene(
            step = step,
            colors = colors,
            animation = CalibrationFluidAnimation(
                flowPhase = flowPhase,
                active = active,
                fillProgress = fillProgress
            )
        )
    }
}
