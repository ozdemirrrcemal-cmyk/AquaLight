package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.DeviceCoolingRootUiState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.design.CoolingHeroGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.design.CoolingHeroInteractionStyle
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.hero.CoolingHeroSection
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.hero.CoolingHeroUiState

/** Cooling-only root composition. */
@Composable
internal fun DeviceCoolingRootScreen(
    state: DeviceCoolingRootUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .alpha(
                if (state.contentEnabled) {
                    CoolingHeroInteractionStyle.enabledContentAlpha
                } else {
                    CoolingHeroInteractionStyle.disabledContentAlpha
                }
            )
            .semantics {
                if (!state.contentEnabled) disabled()
            }
    ) {
        CoolingHeroSection(
            state = CoolingHeroUiState(
                fanSpeedFraction = state.fanSpeedPercent
                    ?.coerceIn(MINIMUM_FAN_PERCENT, MAXIMUM_FAN_PERCENT)
                    ?.let { percent -> percent / MAXIMUM_FAN_PERCENT.toFloat() }
            ),
            modifier = Modifier.padding(
                start = CoolingHeroGeometry.screenHorizontalPadding,
                top = CoolingHeroGeometry.screenTopPadding,
                end = CoolingHeroGeometry.screenHorizontalPadding
            )
        )
    }
}

private const val MINIMUM_FAN_PERCENT = 0
private const val MAXIMUM_FAN_PERCENT = 100
