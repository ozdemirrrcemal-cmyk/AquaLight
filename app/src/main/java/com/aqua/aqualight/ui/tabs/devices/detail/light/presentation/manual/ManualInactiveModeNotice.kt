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
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualAlpha
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualGeometry

@Composable
internal fun ManualInactiveModeNotice(
    mode: DeviceLightManualMode,
    visuals: DeviceLightManualVisuals
) {
    val shape = RoundedCornerShape(AquaLightManualGeometry.bannerCornerRadius)
    val messageRes = mode.inactiveOutputMessageRes()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(visuals.colors.action.copy(alpha = AquaLightManualAlpha.bannerSurface))
            .border(AquaLightManualGeometry.bannerOutlineWidth, visuals.colors.action, shape)
            .padding(
                horizontal = AquaLightManualGeometry.bannerHorizontalPadding,
                vertical = AquaLightManualGeometry.bannerVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(visuals.colors.action),
            modifier = Modifier.size(AquaLightManualGeometry.bannerIconSize)
        )
        Column(
            modifier = Modifier.padding(start = AquaLightManualGeometry.bannerContentGap),
            verticalArrangement = Arrangement.spacedBy(AquaLightManualGeometry.bannerTextGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_light_manual_inactive_mode_title),
                style = visuals.typography.body.copy(color = visuals.colors.action)
            )
            BasicText(
                text = stringResource(messageRes),
                style = visuals.typography.caption.copy(color = visuals.colors.card.primaryText)
            )
        }
    }
}

@StringRes
internal fun DeviceLightManualMode.inactiveOutputMessageRes(): Int = when (this) {
    DeviceLightManualMode.AUTOMATIC ->
        R.string.device_light_manual_inactive_automatic_message
    DeviceLightManualMode.CUSTOM ->
        R.string.device_light_manual_inactive_custom_message
    DeviceLightManualMode.MANUAL -> error("Manual mode does not require an inactive-mode notice.")
}
