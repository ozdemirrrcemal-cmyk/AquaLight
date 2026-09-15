package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.light.AquaLightManualAlpha
import com.aqua.aqualight.ui.common.light.AquaLightManualGeometry

@Composable
internal fun ManualProtectionBanner(
    protection: DeviceLightManualProtectionUiState,
    visuals: DeviceLightManualVisuals
) {
    val shape = RoundedCornerShape(AquaLightManualGeometry.bannerCornerRadius)
    val copy = protection.copyResources()
    val message = protection.effectivePercent?.let { percent ->
        stringResource(copy.percentMessageRes, percent)
    } ?: stringResource(copy.messageRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                visuals.colors.card.warning.copy(alpha = AquaLightManualAlpha.bannerSurface)
            )
            .border(AquaLightManualGeometry.bannerOutlineWidth, visuals.colors.card.warning, shape)
            .padding(
                horizontal = AquaLightManualGeometry.bannerHorizontalPadding,
                vertical = AquaLightManualGeometry.bannerVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            colorFilter = ColorFilter.tint(visuals.colors.card.warning),
            modifier = Modifier.size(AquaLightManualGeometry.bannerIconSize)
        )
        Column(
            modifier = Modifier.padding(start = AquaLightManualGeometry.bannerContentGap),
            verticalArrangement = Arrangement.spacedBy(AquaLightManualGeometry.bannerTextGap)
        ) {
            BasicText(
                text = stringResource(copy.titleRes),
                style = visuals.typography.body.copy(color = visuals.colors.card.warning)
            )
            BasicText(
                text = message,
                style = visuals.typography.caption.copy(color = visuals.colors.card.primaryText)
            )
        }
    }
}

@Composable
internal fun ManualProtectionInformation(visuals: DeviceLightManualVisuals) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AquaLightManualGeometry.informationHorizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(visuals.colors.card.secondaryText),
            modifier = Modifier.size(AquaLightManualGeometry.informationIconSize)
        )
        BasicText(
            text = stringResource(R.string.device_light_manual_protection_information),
            style = visuals.typography.micro.copy(
                color = visuals.colors.card.secondaryText,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(start = AquaLightManualGeometry.informationContentGap)
        )
    }
}

private data class ProtectionCopyResources(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    @StringRes val percentMessageRes: Int
)

private fun DeviceLightManualProtectionUiState.copyResources(): ProtectionCopyResources =
    when (kind) {
        DeviceLightManualProtectionKind.POWER_LIMITED -> ProtectionCopyResources(
            R.string.device_light_manual_power_limited_title,
            R.string.device_light_manual_power_limited_message,
            R.string.device_light_manual_power_limited_percent_message
        )
        DeviceLightManualProtectionKind.THERMAL_LIMITED -> ProtectionCopyResources(
            R.string.device_light_manual_thermal_limited_title,
            R.string.device_light_manual_thermal_limited_message,
            R.string.device_light_manual_thermal_limited_percent_message
        )
        DeviceLightManualProtectionKind.THERMAL_SHUTDOWN -> ProtectionCopyResources(
            R.string.device_light_manual_thermal_shutdown_title,
            R.string.device_light_manual_thermal_shutdown_message,
            R.string.device_light_manual_thermal_shutdown_message
        )
    }
