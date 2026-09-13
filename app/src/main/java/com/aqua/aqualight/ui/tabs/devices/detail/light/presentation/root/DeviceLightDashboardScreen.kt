package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.light.AquaLightHeroGeometry

@Composable
internal fun DeviceLightDashboardScreen(
    state: DeviceLightRootUiState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color))
            .padding(
                start = AquaLightHeroGeometry.screenHorizontalPadding,
                top = AquaLightHeroGeometry.screenTopPadding,
                end = AquaLightHeroGeometry.screenHorizontalPadding
            )
    ) {
        DeviceLightHero(
            state = state.hero,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
