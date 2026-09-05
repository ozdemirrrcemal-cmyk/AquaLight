package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowGeometry
import com.aqua.aqualight.ui.common.flow.aquaGuidedFlowColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeviceDosingCalibrationScreen(
    state: DeviceDosingCalibrationUiState,
    onAction: (DeviceDosingCalibrationAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaGuidedFlowColors()
    val listState = rememberLazyListState()
    val formBringIntoViewRequester = remember { BringIntoViewRequester() }
    val imeVisible = WindowInsets.isImeVisible
    val usesKeyboard = state.step.usesKeyboard()

    LaunchedEffect(imeVisible, state.step) {
        if (imeVisible && usesKeyboard) {
            formBringIntoViewRequester.bringIntoView()
        } else {
            listState.scrollToItem(CALIBRATION_FIRST_ITEM_INDEX)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        CalibrationPump(
            pumpCount = state.pumpCount,
            channelNumber = state.channelNumber,
            active = state.isPumpActive,
            modifier = Modifier.padding(
                start = AquaGuidedFlowGeometry.screenHorizontalPadding,
                top = CALIBRATION_SCREEN_TOP_PADDING,
                end = AquaGuidedFlowGeometry.screenHorizontalPadding
            )
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding(),
            contentPadding = PaddingValues(
                start = AquaGuidedFlowGeometry.screenHorizontalPadding,
                top = AquaGuidedFlowGeometry.sectionGap,
                end = AquaGuidedFlowGeometry.screenHorizontalPadding
            ),
            verticalArrangement = Arrangement.spacedBy(AquaGuidedFlowGeometry.sectionGap),
            userScrollEnabled =
                state.step != DeviceDosingCalibrationStep.PRIME || !state.isPumpActive
        ) {
            item(key = CALIBRATION_PROGRESS_KEY) {
                CalibrationProgressLabel(step = state.step, colors = colors)
            }
            item(key = CALIBRATION_ILLUSTRATION_KEY) {
                CalibrationIllustrationPanel(state = state, colors = colors)
            }
            item(key = CALIBRATION_COPY_KEY) {
                CalibrationStepCopy(state)
            }
            item(key = "$CALIBRATION_CONTROLS_KEY-${state.step.name}") {
                CalibrationStepControls(
                    state = state,
                    colors = colors,
                    onAction = onAction,
                    modifier = Modifier.bringIntoViewRequester(formBringIntoViewRequester)
                )
            }
            item(key = CALIBRATION_BOTTOM_SPACE_KEY) {
                Spacer(Modifier.height(AquaGuidedFlowGeometry.screenBottomPadding))
            }
        }
    }
}

private const val CALIBRATION_PROGRESS_KEY = "progress"
private const val CALIBRATION_ILLUSTRATION_KEY = "illustration"
private const val CALIBRATION_COPY_KEY = "copy"
private const val CALIBRATION_CONTROLS_KEY = "controls"
private const val CALIBRATION_BOTTOM_SPACE_KEY = "bottom-space"
private const val CALIBRATION_FIRST_ITEM_INDEX = 0

private fun DeviceDosingCalibrationStep.usesKeyboard(): Boolean =
    this == DeviceDosingCalibrationStep.NAME ||
        this == DeviceDosingCalibrationStep.MEASUREMENT
