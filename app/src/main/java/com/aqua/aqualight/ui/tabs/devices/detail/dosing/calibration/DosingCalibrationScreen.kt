package com.aqua.aqualight.ui.tabs.devices.detail.dosing.calibration

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingPumpDevice
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingPumpHeadUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingPumpVisualState

@Composable
internal fun DeviceDosingCalibrationScreen(
    state: DosingCalibrationUiState,
    onAction: (DosingCalibrationAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaDeviceCardColors()
    val typography = aquaDeviceCardTypography(colors)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SCREEN_HORIZONTAL_PADDING,
            top = SCREEN_TOP_PADDING,
            end = SCREEN_HORIZONTAL_PADDING,
            bottom = SCREEN_BOTTOM_PADDING
        ),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.loaded && state.pumpCount in SUPPORTED_PUMP_COUNTS) {
            item(key = PUMP_ITEM_KEY) {
                CalibrationPumpDevice(state)
            }
            item(key = PROGRESS_ITEM_KEY) {
                CalibrationStepProgress(state.step.position)
            }
            item(key = CONTENT_ITEM_KEY) {
                AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(CONTENT_GAP)
                    ) {
                        BasicText(
                            text = stringResource(
                                R.string.device_dosing_calibration_step_format,
                                state.step.position,
                                DosingCalibrationStep.COUNT
                            ),
                            style = typography.micro.copy(color = colors.accent)
                        )
                        BasicText(
                            text = stringResource(state.step.titleRes),
                            style = typography.title.copy(color = colors.primaryText)
                        )
                        BasicText(
                            text = stringResource(state.step.descriptionRes),
                            style = typography.caption.copy(color = colors.secondaryText)
                        )

                        AnimatedContent(
                            targetState = state.step,
                            transitionSpec = {
                                (fadeIn(tween(STEP_TRANSITION_MS)) +
                                    slideInVertically(
                                        animationSpec = tween(STEP_TRANSITION_MS),
                                        initialOffsetY = { height -> height / STEP_OFFSET_DIVISOR }
                                    )).togetherWith(
                                    fadeOut(tween(STEP_TRANSITION_MS)) +
                                        slideOutVertically(
                                            animationSpec = tween(STEP_TRANSITION_MS),
                                            targetOffsetY = { height -> -height / STEP_OFFSET_DIVISOR }
                                        )
                                )
                            },
                            label = "dosing-calibration-step"
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(CONTENT_GAP)
                            ) {
                                DosingCalibrationIllustration(state = state)
                                DosingCalibrationStepControls(
                                    state = state,
                                    onAction = onAction
                                )
                            }
                        }

                        state.errorMessageRes?.let { errorRes ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = colors.danger.copy(alpha = ERROR_BACKGROUND_ALPHA),
                                        shape = ERROR_SHAPE
                                    )
                                    .padding(ERROR_PADDING),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicText(
                                    text = stringResource(errorRes),
                                    style = typography.caption.copy(color = colors.danger)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            item(key = LOADING_ITEM_KEY) {
                Spacer(modifier = Modifier.height(LOADING_TOP_SPACE))
                BasicText(
                    text = stringResource(R.string.device_dosing_calibration_loading),
                    modifier = Modifier.fillMaxWidth(),
                    style = typography.body.copy(
                        color = if (state.errorMessageRes == null) {
                            colors.secondaryText
                        } else {
                            colors.danger
                        },
                        textAlign = TextAlign.Center
                    )
                )
                state.errorMessageRes?.let { errorRes ->
                    BasicText(
                        text = stringResource(errorRes),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = LOADING_ERROR_TOP_PADDING),
                        style = typography.caption.copy(
                            color = colors.danger,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CalibrationPumpDevice(state: DosingCalibrationUiState) {
    val pumpRunning = state.operation in RUNNING_PUMP_OPERATIONS
    val heads = List(state.pumpCount) { index ->
        val channelNumber = index + 1
        DosingPumpHeadUiState(
            channelNumber = channelNumber,
            visualState = when {
                channelNumber != state.channelNumber -> DosingPumpVisualState.IDLE
                pumpRunning -> DosingPumpVisualState.RUNNING
                else -> DosingPumpVisualState.SELECTED
            }
        )
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        val maximumWidth = if (state.pumpCount == DOSING_PRO_2_PUMP_COUNT) {
            DOSING_PRO_2_MAX_WIDTH
        } else {
            DOSING_PRO_4_MAX_WIDTH
        }
        DosingPumpDevice(
            pumpHeads = heads,
            onPumpClick = {},
            pumpClicksEnabled = false,
            modifier = Modifier.width(minOf(maxWidth, maximumWidth))
        )
    }
}

@Composable
private fun CalibrationStepProgress(currentStep: Int) {
    val colors = aquaDeviceCardColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PROGRESS_SEGMENT_GAP)
    ) {
        repeat(DosingCalibrationStep.COUNT) { index ->
            val completedOrCurrent = index + 1 <= currentStep
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(PROGRESS_SEGMENT_HEIGHT)
                    .background(
                        color = if (completedOrCurrent) {
                            colors.accent
                        } else {
                            colors.mediaOutline.copy(alpha = PROGRESS_INACTIVE_ALPHA)
                        },
                        shape = PROGRESS_SHAPE
                    )
            )
        }
    }
}

private val RUNNING_PUMP_OPERATIONS = setOf(
    DosingCalibrationOperation.STARTING_PRIME,
    DosingCalibrationOperation.PRIMING,
    DosingCalibrationOperation.CALIBRATION_DOSING,
    DosingCalibrationOperation.VERIFYING
)
private val SUPPORTED_PUMP_COUNTS = setOf(2, 4)
private const val PUMP_ITEM_KEY = "calibration-pump"
private const val PROGRESS_ITEM_KEY = "calibration-progress"
private const val CONTENT_ITEM_KEY = "calibration-content"
private const val LOADING_ITEM_KEY = "calibration-loading"
private const val DOSING_PRO_2_PUMP_COUNT = 2
private const val STEP_TRANSITION_MS = 260
private const val STEP_OFFSET_DIVISOR = 12
private const val ERROR_BACKGROUND_ALPHA = 0.10f
private const val PROGRESS_INACTIVE_ALPHA = 0.56f
private const val SCREEN_HORIZONTAL_PADDING_DP = 16
private const val SCREEN_TOP_PADDING_DP = 12
private const val SCREEN_BOTTOM_PADDING_DP = 28
private const val SECTION_GAP_DP = 14
private const val CONTENT_GAP_DP = 12
private const val PROGRESS_SEGMENT_GAP_DP = 5
private const val PROGRESS_SEGMENT_HEIGHT_DP = 4
private const val ERROR_CORNER_RADIUS_DP = 10
private const val ERROR_PADDING_DP = 10
private const val LOADING_TOP_SPACE_DP = 48
private const val LOADING_ERROR_TOP_PADDING_DP = 10
private const val DOSING_PRO_2_MAX_WIDTH_DP = 360
private const val DOSING_PRO_4_MAX_WIDTH_DP = 760
private val SCREEN_HORIZONTAL_PADDING = SCREEN_HORIZONTAL_PADDING_DP.dp
private val SCREEN_TOP_PADDING = SCREEN_TOP_PADDING_DP.dp
private val SCREEN_BOTTOM_PADDING = SCREEN_BOTTOM_PADDING_DP.dp
private val SECTION_GAP = SECTION_GAP_DP.dp
private val CONTENT_GAP = CONTENT_GAP_DP.dp
private val PROGRESS_SEGMENT_GAP = PROGRESS_SEGMENT_GAP_DP.dp
private val PROGRESS_SEGMENT_HEIGHT = PROGRESS_SEGMENT_HEIGHT_DP.dp
private val ERROR_SHAPE = RoundedCornerShape(ERROR_CORNER_RADIUS_DP.dp)
private val PROGRESS_SHAPE = RoundedCornerShape(PROGRESS_SEGMENT_HEIGHT_DP.dp)
private val ERROR_PADDING = ERROR_PADDING_DP.dp
private val LOADING_TOP_SPACE = LOADING_TOP_SPACE_DP.dp
private val LOADING_ERROR_TOP_PADDING = LOADING_ERROR_TOP_PADDING_DP.dp
private val DOSING_PRO_2_MAX_WIDTH = DOSING_PRO_2_MAX_WIDTH_DP.dp
private val DOSING_PRO_4_MAX_WIDTH = DOSING_PRO_4_MAX_WIDTH_DP.dp
