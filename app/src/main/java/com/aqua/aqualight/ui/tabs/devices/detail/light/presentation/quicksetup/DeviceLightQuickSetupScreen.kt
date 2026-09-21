package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowButton

@Composable
internal fun DeviceLightQuickSetupScreen(
    state: DeviceLightQuickSetupUiState,
    onAction: (DeviceLightQuickSetupAction) -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color))
            .padding(horizontal = DeviceLightQuickSetupGeometry.screenHorizontalPadding)
    ) {
        if (state.context != null) {
            Spacer(Modifier.height(DeviceLightQuickSetupGeometry.screenTopPadding))
            QuickSetupProgress(state)
            Spacer(Modifier.height(DeviceLightQuickSetupGeometry.sectionGap))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when {
                state.loading -> QuickSetupCalculatingScreen()
                state.context == null -> QuickSetupBlockedScreen(state, onAction)
                else -> QuickSetupStageContent(state, onAction)
            }
        }

        if (!state.loading && state.context != null) {
            state.blockReason?.let { reason ->
                Spacer(Modifier.height(8.dp))
                QuickSetupErrorBanner(reason)
            }
            Spacer(Modifier.height(10.dp))
            QuickSetupStageFooter(state, onAction, onDone)
        }
    }
}

@Composable
private fun QuickSetupStageContent(
    state: DeviceLightQuickSetupUiState,
    onAction: (DeviceLightQuickSetupAction) -> Unit
) {
    when (state.stage) {
        DeviceLightQuickSetupStage.PROFILE -> QuickSetupProfileScreen(state)
        DeviceLightQuickSetupStage.WATER_HEIGHT -> QuickSetupWaterHeightScreen(
            state = state,
            onValueChange = {
                onAction(DeviceLightQuickSetupAction.WaterHeightChanged(it))
            }
        )
        DeviceLightQuickSetupStage.FIXTURE_HEIGHT -> QuickSetupFixtureHeightScreen(
            state = state,
            onValueChange = {
                onAction(DeviceLightQuickSetupAction.FixtureHeightChanged(it))
            }
        )
        DeviceLightQuickSetupStage.LIGHT_TIME -> QuickSetupLightTimeScreen(
            state = state,
            onChanged = {
                onAction(DeviceLightQuickSetupAction.FirstLightTimeChanged(it))
            }
        )
        DeviceLightQuickSetupStage.CO2_CONFIRMATION -> QuickSetupCo2Screen(
            state = state,
            onCheckedChange = {
                onAction(DeviceLightQuickSetupAction.Co2PrechargedChanged(it))
            }
        )
        DeviceLightQuickSetupStage.CALCULATING -> QuickSetupCalculatingScreen()
        DeviceLightQuickSetupStage.REVIEW -> QuickSetupReviewScreen(state)
        DeviceLightQuickSetupStage.APPLYING -> QuickSetupApplyingScreen()
        DeviceLightQuickSetupStage.LIVE -> QuickSetupLiveScreen(state, onAction)
    }
}

@Composable
private fun QuickSetupStageFooter(
    state: DeviceLightQuickSetupUiState,
    onAction: (DeviceLightQuickSetupAction) -> Unit,
    onDone: () -> Unit
) {
    when (state.stage) {
        DeviceLightQuickSetupStage.PROFILE -> QuickSetupFooter(
            showBack = false,
            primaryText = stringResource(R.string.device_light_quick_setup_continue),
            onBack = {},
            onPrimary = { onAction(DeviceLightQuickSetupAction.Next) }
        )
        DeviceLightQuickSetupStage.WATER_HEIGHT,
        DeviceLightQuickSetupStage.FIXTURE_HEIGHT,
        DeviceLightQuickSetupStage.LIGHT_TIME -> QuickSetupFooter(
            showBack = true,
            primaryText = stringResource(R.string.device_light_quick_setup_continue),
            onBack = { onAction(DeviceLightQuickSetupAction.Back) },
            onPrimary = { onAction(DeviceLightQuickSetupAction.Next) }
        )
        DeviceLightQuickSetupStage.CO2_CONFIRMATION -> QuickSetupFooter(
            showBack = true,
            primaryText = stringResource(R.string.device_light_quick_setup_calculate),
            onBack = { onAction(DeviceLightQuickSetupAction.Back) },
            onPrimary = { onAction(DeviceLightQuickSetupAction.Next) }
        )
        DeviceLightQuickSetupStage.REVIEW -> QuickSetupFooter(
            showBack = true,
            primaryText = stringResource(R.string.device_light_quick_setup_apply),
            primaryEnabled = state.recommendation != null,
            onBack = { onAction(DeviceLightQuickSetupAction.Back) },
            onPrimary = { onAction(DeviceLightQuickSetupAction.Apply) }
        )
        DeviceLightQuickSetupStage.LIVE -> QuickSetupFooter(
            showBack = false,
            primaryText = stringResource(R.string.device_light_quick_setup_done),
            onBack = {},
            onPrimary = onDone
        )
        DeviceLightQuickSetupStage.CALCULATING,
        DeviceLightQuickSetupStage.APPLYING -> Unit
    }
}

@Composable
private fun QuickSetupBlockedScreen(
    state: DeviceLightQuickSetupUiState,
    onAction: (DeviceLightQuickSetupAction) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(20.dp))
        QuickSetupHeading(
            title = stringResource(R.string.device_light_quick_setup_unavailable_title),
            description = stringResource(R.string.device_light_quick_setup_unavailable_description)
        )
        state.blockReason?.let { reason ->
            Spacer(Modifier.height(18.dp))
            QuickSetupErrorBanner(reason)
        }
        Spacer(Modifier.height(18.dp))
        AquaGuidedFlowButton(
            text = stringResource(R.string.device_light_quick_setup_retry),
            onClick = { onAction(DeviceLightQuickSetupAction.Retry) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
