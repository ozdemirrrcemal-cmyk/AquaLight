package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.light.aquaLightDashboardColors
import com.aqua.aqualight.ui.common.light.aquaLightDashboardTypography

@Composable
internal fun DeviceLightAutomaticProgramsScreen(
    state: DeviceLightAutomaticProgramsUiState,
    actions: DeviceLightAutomaticProgramsActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightDashboardColors()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = DeviceLightAutomaticGeometry.screenHorizontalPadding,
                top = DeviceLightAutomaticGeometry.screenTopPadding,
                end = DeviceLightAutomaticGeometry.screenHorizontalPadding,
                bottom = DeviceLightAutomaticGeometry.screenBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(DeviceLightAutomaticGeometry.cardGap)
        ) {
            items(state.programs, key = { program -> program.programId }) { program ->
                DeviceLightAutomaticProgramCard(
                    program = program,
                    channels = state.channels,
                    enabled = state.contentEnabled && !state.operationInProgress,
                    actions = actions
                )
            }
        }
        AutomaticAddButton(
            enabled = state.canAdd,
            onClick = actions.onAddClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = DeviceLightAutomaticGeometry.addButtonHorizontalPadding,
                    end = DeviceLightAutomaticGeometry.addButtonHorizontalPadding,
                    bottom = DeviceLightAutomaticGeometry.addButtonBottomPadding
                )
        )
    }
}

@Composable
private fun AutomaticAddButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightDashboardColors()
    val typography = aquaLightDashboardTypography(colors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DeviceLightAutomaticGeometry.addButtonHeight)
            .alpha(if (enabled) 1f else DeviceLightAutomaticAlpha.disabled)
            .background(colors.accent, DeviceLightAutomaticGeometry.addButtonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutomaticPlusIcon(
            color = colors.primaryText,
            modifier = Modifier.size(DeviceLightAutomaticGeometry.addButtonIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.addButtonContentGap))
        BasicText(
            text = stringResource(R.string.device_light_auto_add_program),
            style = typography.title.copy(color = colors.primaryText)
        )
    }
}
