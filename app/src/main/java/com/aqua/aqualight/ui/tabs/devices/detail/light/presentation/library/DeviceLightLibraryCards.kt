package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.light.AquaLightLibraryAlpha
import com.aqua.aqualight.ui.common.light.AquaLightLibraryGeometry
import com.aqua.aqualight.ui.common.light.AquaLightManualColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.AutomaticCalendarIcon
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.AutomaticClockIcon
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.AutomaticMoreButton
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticGeometry

@Composable
internal fun ManualLibraryCard(
    entry: DeviceLightLibraryEntry,
    payload: DeviceLightLibraryPayload.Manual,
    actions: DeviceLightLibraryActions,
    visuals: DeviceLightLibraryVisuals
) {
    LibraryCardSurface {
        LibraryCardHeader(entry, actions, visuals)
        ManualLibrarySummary(visuals)
        LibraryCardDivider(visuals)
        ManualChannelSummary(entry.channels, payload.scene, visuals)
    }
}

@Composable
internal fun CustomLibraryCard(
    entry: DeviceLightLibraryEntry,
    payload: DeviceLightLibraryPayload.Custom,
    actions: DeviceLightLibraryActions,
    visuals: DeviceLightLibraryVisuals
) {
    LibraryCardSurface {
        LibraryCardHeader(entry, actions, visuals)
        CustomLibrarySummary(payload, visuals)
        LibraryCardDivider(visuals)
        CustomChannelSummary(entry.channels, payload, visuals)
    }
}

@Composable
private fun LibraryCardSurface(content: @Composable () -> Unit) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightAutomaticGeometry.cardContentPadding
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DeviceLightAutomaticGeometry.cardContentMinimumHeight),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            content()
        }
    }
}

@Composable
private fun LibraryCardHeader(
    entry: DeviceLightLibraryEntry,
    actions: DeviceLightLibraryActions,
    visuals: DeviceLightLibraryVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryEntryIcon(
            payload = entry.payload,
            color = visuals.colors.card.secondaryText,
            modifier = Modifier.size(DeviceLightAutomaticGeometry.cardHeaderIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.headerIconGap))
        BasicText(
            text = entry.name,
            style = visuals.typography.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.headerControlGap))
        LibraryLoadButton(entry, actions.onLoadClick, visuals)
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.headerControlGap))
        AutomaticMoreButton(
            color = visuals.colors.card.secondaryText,
            contentDescriptionText = stringResource(
                R.string.device_light_library_more_actions_description,
                entry.name
            ),
            enabled = true,
            onClick = { actions.onMoreClick(entry.id) }
        )
    }
}

@Composable
private fun ManualLibrarySummary(visuals: DeviceLightLibraryVisuals) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryBulbIcon(
            color = visuals.colors.card.warning,
            modifier = Modifier.size(DeviceLightAutomaticGeometry.scheduleEventIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.scheduleTextGap))
        BasicText(
            text = stringResource(R.string.device_light_library_fixed_light),
            style = visuals.typography.title,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        LibrarySummaryDivider(visuals)
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.scheduleDividerGap))
        LibraryLightningIcon(
            color = visuals.colors.action,
            modifier = Modifier.size(DeviceLightAutomaticGeometry.scheduleEventIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.scheduleTextGap))
        BasicText(
            text = stringResource(R.string.device_light_library_applies_instantly),
            style = visuals.typography.body.copy(color = visuals.colors.card.secondaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CustomLibrarySummary(
    payload: DeviceLightLibraryPayload.Custom,
    visuals: DeviceLightLibraryVisuals
) {
    val pointCount = payload.points.size
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutomaticCalendarIcon(
            color = visuals.colors.card.secondaryText,
            modifier = Modifier.size(DeviceLightAutomaticGeometry.scheduleEventIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.scheduleTextGap))
        BasicText(
            text = lightLibraryWeekdaysText(payload.weekdaysMask),
            style = visuals.typography.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = AquaLightLibraryGeometry.customDaysMaxWidth)
        )
        Spacer(Modifier.width(AquaLightLibraryGeometry.customMetadataGap))
        BasicText(
            text = stringResource(R.string.device_light_library_metadata_separator),
            style = visuals.typography.body.copy(color = visuals.colors.card.secondaryText)
        )
        Spacer(Modifier.width(AquaLightLibraryGeometry.customMetadataGap))
        LibraryCurveIcon(
            color = visuals.colors.card.secondaryText,
            modifier = Modifier.size(DeviceLightAutomaticGeometry.scheduleEventIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.scheduleTextGap))
        BasicText(
            text = pluralStringResource(
                R.plurals.device_light_library_point_count,
                pointCount,
                pointCount
            ),
            style = visuals.typography.body,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        LibrarySummaryDivider(visuals)
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.scheduleDividerGap))
        AutomaticClockIcon(
            color = visuals.colors.card.secondaryText,
            modifier = Modifier.size(DeviceLightAutomaticGeometry.scheduleEventIconSize)
        )
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.scheduleTextGap))
        BasicText(
            text = stringResource(
                R.string.device_light_library_light_duration_hours,
                payload.activeLightDurationHours()
            ),
            style = visuals.typography.body,
            maxLines = 1
        )
    }
}

@Composable
private fun LibrarySummaryDivider(visuals: DeviceLightLibraryVisuals) {
    Box(
        Modifier
            .width(DeviceLightAutomaticGeometry.scheduleDividerWidth)
            .height(DeviceLightAutomaticGeometry.scheduleDividerHeight)
            .background(visuals.colors.card.mediaOutline)
    )
}

@Composable
private fun LibraryCardDivider(visuals: DeviceLightLibraryVisuals) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(DeviceLightAutomaticGeometry.cardDividerHeight)
            .background(visuals.colors.card.mediaOutline)
    )
}

@Composable
private fun ManualChannelSummary(
    channels: List<DeviceLightLibraryChannel>,
    scene: DeviceLightLibraryScene,
    visuals: DeviceLightLibraryVisuals
) {
    val visibleChannels = LIBRARY_CHANNEL_ORDER.filter(channels::contains)
    val labels = visibleChannels.associateWith { channel ->
        stringResource(
            R.string.device_light_library_channel_summary_format,
            channel.shortLabel(),
            scene.channels.getValue(channel)
        )
    }
    LibraryChannelSummary(visibleChannels, labels, visuals)
}

@Composable
private fun CustomChannelSummary(
    channels: List<DeviceLightLibraryChannel>,
    payload: DeviceLightLibraryPayload.Custom,
    visuals: DeviceLightLibraryVisuals
) {
    val visibleChannels = LIBRARY_CHANNEL_ORDER.filter(channels::contains)
    val labels = visibleChannels.associateWith { channel ->
        val range = payload.channelRange(channel)
        stringResource(
            R.string.device_light_library_channel_range_format,
            channel.shortLabel(),
            range.first,
            range.last
        )
    }
    LibraryChannelSummary(visibleChannels, labels, visuals)
}

@Composable
private fun LibraryChannelSummary(
    channels: List<DeviceLightLibraryChannel>,
    labels: Map<DeviceLightLibraryChannel, String>,
    visuals: DeviceLightLibraryVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        channels.forEach { channel ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    DeviceLightAutomaticGeometry.channelTextGap
                )
            ) {
                Canvas(modifier = Modifier.size(DeviceLightAutomaticGeometry.channelDotSize)) {
                    drawCircle(color = channel.libraryColor(visuals.colors))
                }
                BasicText(
                    text = labels.getValue(channel),
                    style = visuals.typography.body,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LibraryLoadButton(
    entry: DeviceLightLibraryEntry,
    onLoadClick: (String) -> Unit,
    visuals: DeviceLightLibraryVisuals
) {
    val enabled = !entry.isLoaded
    val text = stringResource(
        if (entry.isLoaded) {
            R.string.device_light_library_loaded
        } else {
            R.string.device_light_library_load
        }
    )
    val description = stringResource(
        if (entry.isLoaded) {
            R.string.device_light_library_loaded_description
        } else {
            R.string.device_light_library_load_description
        },
        entry.name
    )
    val shape = RoundedCornerShape(AquaLightLibraryGeometry.loadButtonCornerRadius)
    Row(
        modifier = Modifier
            .requiredWidthIn(min = AquaLightLibraryGeometry.loadButtonMinWidth)
            .height(AquaLightLibraryGeometry.loadButtonHeight)
            .alpha(if (enabled) 1f else AquaLightLibraryAlpha.disabled)
            .border(
                AquaLightLibraryGeometry.loadButtonOutlineWidth,
                visuals.colors.action,
                shape
            )
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button) { onLoadClick(entry.id) }
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            }
            .padding(horizontal = AquaLightLibraryGeometry.loadButtonHorizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(
                if (entry.isLoaded) R.drawable.ic_check_24 else R.drawable.ic_light_library
            ),
            contentDescription = null,
            colorFilter = ColorFilter.tint(visuals.colors.action),
            modifier = Modifier.size(AquaLightLibraryGeometry.loadButtonIconSize)
        )
        Spacer(Modifier.width(AquaLightLibraryGeometry.loadButtonGap))
        BasicText(
            text = text,
            style = visuals.typography.title.copy(color = visuals.colors.action),
            maxLines = 1
        )
    }
}

@Composable
internal fun DeviceLightLibraryChannel.shortLabel(): String = stringResource(
    when (this) {
        DeviceLightLibraryChannel.RED -> R.string.device_light_plan_channel_red
        DeviceLightLibraryChannel.GREEN -> R.string.device_light_plan_channel_green
        DeviceLightLibraryChannel.BLUE -> R.string.device_light_plan_channel_blue
        DeviceLightLibraryChannel.WHITE -> R.string.device_light_plan_channel_white
    }
)

internal fun DeviceLightLibraryChannel.libraryColor(colors: AquaLightManualColors): Color =
    when (this) {
        DeviceLightLibraryChannel.RED -> colors.red
        DeviceLightLibraryChannel.GREEN -> colors.green
        DeviceLightLibraryChannel.BLUE -> colors.blue
        DeviceLightLibraryChannel.WHITE -> colors.white
    }

@Composable
private fun lightLibraryWeekdaysText(mask: Int): String {
    if (mask == EVERY_DAY_MASK) {
        return stringResource(R.string.device_light_library_every_day)
    }
    val dayResources = listOf(
        R.string.device_light_library_day_monday,
        R.string.device_light_library_day_tuesday,
        R.string.device_light_library_day_wednesday,
        R.string.device_light_library_day_thursday,
        R.string.device_light_library_day_friday,
        R.string.device_light_library_day_saturday,
        R.string.device_light_library_day_sunday
    )
    return dayResources.mapIndexedNotNull { index, dayResource ->
        stringResource(dayResource).takeIf { mask and (1 shl index) != 0 }
    }.joinToString(" · ")
}

private val LIBRARY_CHANNEL_ORDER = listOf(
    DeviceLightLibraryChannel.WHITE,
    DeviceLightLibraryChannel.RED,
    DeviceLightLibraryChannel.GREEN,
    DeviceLightLibraryChannel.BLUE
)

private const val EVERY_DAY_MASK = 0x7f
