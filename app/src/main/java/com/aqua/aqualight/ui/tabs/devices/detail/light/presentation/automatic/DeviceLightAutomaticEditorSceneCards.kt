package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSlider
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSliderActions
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSliderState

@Composable
internal fun DeviceLightAutomaticEditorPresetCard(
    state: DeviceLightAutomaticProgramEditorUiState,
    onClick: () -> Unit,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state.contentEnabled, role = Role.Button, onClick = onClick),
        contentPadding = DeviceLightAutomaticEditorGeometry.cardPadding
    ) {
        Row(
            modifier = Modifier.height(DeviceLightAutomaticEditorGeometry.presetHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_care_plant_health_24),
                contentDescription = null,
                colorFilter = ColorFilter.tint(visuals.colors.green),
                modifier = Modifier.size(DeviceLightAutomaticEditorGeometry.sectionIconSize)
            )
            Spacer(Modifier.width(DeviceLightAutomaticEditorGeometry.sectionIconGap))
            Column(Modifier.weight(PRESET_COPY_WEIGHT)) {
                BasicText(
                    text = stringResource(R.string.device_light_auto_editor_preset),
                    style = visuals.typography.title.copy(color = visuals.colors.card.primaryText)
                )
                BasicText(
                    text = stringResource(R.string.device_light_auto_editor_preset_summary),
                    style = visuals.typography.caption.copy(color = visuals.colors.card.secondaryText)
                )
            }
            BasicText(
                text = stringResource(R.string.device_light_auto_editor_preset_empty),
                style = visuals.typography.caption.copy(color = visuals.colors.card.secondaryText)
            )
            Spacer(Modifier.width(DeviceLightAutomaticEditorGeometry.presetChevronSize))
            Image(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                colorFilter = ColorFilter.tint(visuals.colors.card.secondaryText),
                modifier = Modifier.size(DeviceLightAutomaticEditorGeometry.presetChevronSize)
            )
        }
    }
}

@Composable
internal fun DeviceLightAutomaticEditorChannelsCard(
    state: DeviceLightAutomaticProgramEditorUiState,
    onChannelChanged: (DeviceLightAutomaticChannel, Int) -> Unit,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val displayedChannels = CHANNEL_PRESENTATION_ORDER.filter(
        state.source?.channels.orEmpty()::contains
    )
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightAutomaticEditorGeometry.cardPadding
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(
            DeviceLightAutomaticEditorGeometry.cardContentGap
        )) {
            EditorSectionHeading(
                title = stringResource(R.string.device_light_auto_editor_channels),
                subtitle = stringResource(R.string.device_light_auto_editor_channels_summary),
                visuals = visuals,
                icon = { color -> ChannelSlidersIcon(color) }
            )
            displayedChannels.chunked(CHANNEL_COLUMN_COUNT).forEach { rowChannels ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        DeviceLightAutomaticEditorGeometry.channelSliderGap
                    )
                ) {
                    rowChannels.forEach { channel ->
                        ChannelControl(
                            state = ChannelControlState(
                                channel = channel,
                                percent = state.draft.channels[channel] ?: MIN_PERCENT,
                                enabled = state.contentEnabled
                            ),
                            onValueChanged = { value -> onChannelChanged(channel, value) },
                            visuals = visuals,
                            modifier = Modifier.weight(CHANNEL_COLUMN_WEIGHT)
                        )
                    }
                    repeat(CHANNEL_COLUMN_COUNT - rowChannels.size) {
                        Spacer(Modifier.weight(CHANNEL_COLUMN_WEIGHT))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelControl(
    state: ChannelControlState,
    onValueChanged: (Int) -> Unit,
    visuals: DeviceLightAutomaticEditorVisuals,
    modifier: Modifier
) {
    val label = stringResource(state.channel.labelRes())
    Row(
        modifier = modifier.height(DeviceLightAutomaticEditorGeometry.channelRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = visuals.typography.caption.copy(color = visuals.colors.card.primaryText),
            modifier = Modifier.width(DeviceLightAutomaticEditorGeometry.channelLabelWidth)
        )
        AquaLightManualPercentSlider(
            state = AquaLightManualPercentSliderState(
                percent = state.percent,
                enabled = state.enabled,
                channelColor = state.channel.color(visuals),
                stateText = stringResource(
                    R.string.device_light_auto_percent_format,
                    state.percent
                ),
                accessibilityDescription = stringResource(
                    R.string.device_light_auto_editor_channel_description,
                    label
                )
            ),
            actions = AquaLightManualPercentSliderActions(
                onValueChanged = onValueChanged,
                onValueChangeFinished = {}
            ),
            modifier = Modifier.weight(CHANNEL_SLIDER_WEIGHT)
        )
        Spacer(Modifier.width(DeviceLightAutomaticEditorGeometry.channelSliderGap))
        BasicText(
            text = stringResource(R.string.device_light_auto_percent_format, state.percent),
            style = visuals.typography.caption.copy(
                color = visuals.colors.card.primaryText,
                textAlign = TextAlign.End
            ),
            modifier = Modifier.width(DeviceLightAutomaticEditorGeometry.channelValueWidth)
        )
    }
}

private data class ChannelControlState(
    val channel: DeviceLightAutomaticChannel,
    val percent: Int,
    val enabled: Boolean
)

@Composable
private fun ChannelSlidersIcon(color: Color) {
    Canvas(Modifier.size(DeviceLightAutomaticEditorGeometry.sectionIconSize)) {
        val stroke = DeviceLightAutomaticGeometry.iconStrokeWidth.toPx()
        CHANNEL_ICON_X_FRACTIONS.forEachIndexed { index, xFraction ->
            val x = size.width * xFraction
            drawLine(
                color = color,
                start = Offset(x, size.height * CHANNEL_ICON_TOP),
                end = Offset(x, size.height * CHANNEL_ICON_BOTTOM),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = color,
                radius = CHANNEL_ICON_THUMB_RADIUS_DP * density,
                center = Offset(
                    x,
                    size.height * CHANNEL_ICON_THUMB_Y_FRACTIONS[index]
                )
            )
        }
    }
}

private fun DeviceLightAutomaticChannel.labelRes(): Int = when (this) {
    DeviceLightAutomaticChannel.RED -> R.string.device_light_live_output_red
    DeviceLightAutomaticChannel.GREEN -> R.string.device_light_live_output_green
    DeviceLightAutomaticChannel.BLUE -> R.string.device_light_live_output_blue
    DeviceLightAutomaticChannel.WHITE -> R.string.device_light_live_output_white
}

private fun DeviceLightAutomaticChannel.color(visuals: DeviceLightAutomaticEditorVisuals): Color =
    when (this) {
        DeviceLightAutomaticChannel.RED -> visuals.colors.red
        DeviceLightAutomaticChannel.GREEN -> visuals.colors.green
        DeviceLightAutomaticChannel.BLUE -> visuals.colors.blue
        DeviceLightAutomaticChannel.WHITE -> visuals.colors.white
    }

private val CHANNEL_ICON_X_FRACTIONS = listOf(
    CHANNEL_ICON_FIRST_X,
    CHANNEL_ICON_SECOND_X,
    CHANNEL_ICON_THIRD_X
)
private val CHANNEL_PRESENTATION_ORDER = listOf(
    DeviceLightAutomaticChannel.RED,
    DeviceLightAutomaticChannel.GREEN,
    DeviceLightAutomaticChannel.BLUE,
    DeviceLightAutomaticChannel.WHITE
)
private val CHANNEL_ICON_THUMB_Y_FRACTIONS = listOf(
    CHANNEL_ICON_FIRST_THUMB_Y,
    CHANNEL_ICON_SECOND_THUMB_Y,
    CHANNEL_ICON_THIRD_THUMB_Y
)
private const val CHANNEL_COLUMN_COUNT = 2
private const val PRESET_COPY_WEIGHT = 1f
private const val CHANNEL_COLUMN_WEIGHT = 1f
private const val CHANNEL_SLIDER_WEIGHT = 1f
private const val MIN_PERCENT = 0
private const val CHANNEL_ICON_FIRST_X = 0.22f
private const val CHANNEL_ICON_SECOND_X = 0.50f
private const val CHANNEL_ICON_THIRD_X = 0.78f
private const val CHANNEL_ICON_TOP = 0.12f
private const val CHANNEL_ICON_BOTTOM = 0.88f
private const val CHANNEL_ICON_FIRST_THUMB_Y = 0.38f
private const val CHANNEL_ICON_SECOND_THUMB_Y = 0.67f
private const val CHANNEL_ICON_THIRD_THUMB_Y = 0.46f
private const val CHANNEL_ICON_THUMB_RADIUS_DP = 2.4f
