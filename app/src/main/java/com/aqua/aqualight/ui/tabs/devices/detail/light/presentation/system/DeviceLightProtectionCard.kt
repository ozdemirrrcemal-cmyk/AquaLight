package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface

@Composable
internal fun DeviceLightProtectionCard(
    state: DeviceLightSystemUiState,
    actions: DeviceLightSystemActions,
    visuals: DeviceLightSystemVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightSystemGeometry.protectionCardHeight),
        contentPadding = DeviceLightSystemGeometry.protectionCardPadding
    ) {
        Column(Modifier.fillMaxSize()) {
            DeviceLightProtectionHeader(visuals)
            DeviceLightProtectionIntro(visuals)
            Spacer(Modifier.height(DeviceLightSystemGeometry.protectionDividerGap))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(DeviceLightSystemGeometry.dividerHeight)
                    .background(visuals.colors.card.mediaOutline)
            )
            DeviceLightProtectionThreshold(state, actions, visuals)
        }
    }
}

@Composable
private fun DeviceLightProtectionHeader(visuals: DeviceLightSystemVisuals) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightSystemGeometry.protectionHeaderHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = stringResource(R.string.device_light_system_light_protection),
            style = visuals.typography.title,
            modifier = Modifier.weight(1f)
        )
        DeviceLightProtectionBadge(visuals)
    }
}

@Composable
private fun DeviceLightProtectionIntro(visuals: DeviceLightSystemVisuals) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightSystemGeometry.protectionIntroHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeviceLightProtectionIcon(
            tint = visuals.colors.action,
            modifier = Modifier.size(DeviceLightSystemGeometry.protectionIconSize)
        )
        Spacer(Modifier.width(DeviceLightSystemGeometry.protectionTextGap))
        BasicText(
            text = stringResource(R.string.device_light_system_protection_description),
            style = visuals.typography.caption,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DeviceLightProtectionThreshold(
    state: DeviceLightSystemUiState,
    actions: DeviceLightSystemActions,
    visuals: DeviceLightSystemVisuals
) {
    val minimum = state.snapshot?.protectionThresholdPolicy?.minimum
        ?: DEFAULT_PROTECTION_TEMPERATURE_MINIMUM
    val maximum = state.snapshot?.protectionThresholdPolicy?.maximum
        ?: DEFAULT_PROTECTION_TEMPERATURE_MAXIMUM
    DeviceLightTemperatureControlRow(
        control = DeviceLightTemperatureControlSpec(
            labelRes = R.string.device_light_system_protection_threshold,
            value = state.selectedProtectionThresholdCelsius,
            minimum = minimum,
            maximum = maximum,
            enabled = state.controlsEnabled,
            onValueChanged = actions.onProtectionThresholdChanged
        ),
        visuals = visuals
    )
    DeviceLightTemperatureLimitLabels(minimum, maximum, visuals)
}

@Composable
private fun DeviceLightProtectionBadge(visuals: DeviceLightSystemVisuals) {
    Row(
        modifier = Modifier
            .clip(DeviceLightSystemGeometry.protectionBadgeShape)
            .background(
                visuals.colors.action.copy(alpha = DeviceLightSystemAlpha.badgeSurface)
            )
            .padding(DeviceLightSystemGeometry.protectionBadgePadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeviceLightLockIcon(
            tint = visuals.colors.action,
            modifier = Modifier.size(DeviceLightSystemGeometry.infoIconSize)
        )
        Spacer(Modifier.width(DeviceLightSystemGeometry.modeHelperGap))
        BasicText(
            text = stringResource(R.string.device_light_system_always_on),
            style = visuals.typography.micro.copy(color = visuals.colors.action)
        )
    }
}

private const val DEFAULT_PROTECTION_TEMPERATURE_MINIMUM = 50
private const val DEFAULT_PROTECTION_TEMPERATURE_MAXIMUM = 70
