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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.light.AquaLightLibraryAlpha
import com.aqua.aqualight.ui.common.light.AquaLightLibraryGeometry
import com.aqua.aqualight.ui.common.light.AquaLightManualColors

@Composable
internal fun ManualLibraryCard(
    entry: DeviceLightLibraryEntry,
    payload: DeviceLightLibraryPayload.Manual,
    actions: DeviceLightLibraryActions,
    visuals: DeviceLightLibraryVisuals
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightLibraryGeometry.cardHeaderGap)) {
            LibraryCardHeader(entry, actions.onMoreClick, visuals)
            Column(
                verticalArrangement = Arrangement.spacedBy(
                    AquaLightLibraryGeometry.manualRowGap
                )
            ) {
                entry.channels.forEach { channel ->
                    ManualValueRow(channel, payload.scene, visuals)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LibraryLoadButton(entry, actions.onLoadClick, visuals)
            }
        }
    }
}

@Composable
internal fun CustomLibraryCard(
    entry: DeviceLightLibraryEntry,
    payload: DeviceLightLibraryPayload.Custom,
    actions: DeviceLightLibraryActions,
    visuals: DeviceLightLibraryVisuals
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaLightLibraryGeometry.cardHeaderGap)) {
            LibraryCardHeader(entry, actions.onMoreClick, visuals)
            val pointCount = payload.points.size
            BasicText(
                text = stringResource(
                    R.string.device_light_library_days_points_format,
                    lightLibraryWeekdaysText(payload.weekdaysMask),
                    pluralStringResource(
                        R.plurals.device_light_library_point_count,
                        pointCount,
                        pointCount
                    )
                ),
                style = visuals.typography.caption
            )
            CustomCurveChart(entry, payload, visuals)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartLegend(entry.channels, visuals)
                Spacer(Modifier.weight(1f))
                LibraryLoadButton(entry, actions.onLoadClick, visuals)
            }
        }
    }
}

@Composable
private fun LibraryCardHeader(
    entry: DeviceLightLibraryEntry,
    onMoreClick: (String) -> Unit,
    visuals: DeviceLightLibraryVisuals
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = entry.name,
            style = visuals.typography.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        LibraryKindBadge(entry, visuals)
        Spacer(Modifier.width(2.dp))
        val description = stringResource(
            R.string.device_light_library_more_actions_description,
            entry.name
        )
        Canvas(
            modifier = Modifier
                .size(AquaLightLibraryGeometry.moreTouchSize)
                .clickable(role = Role.Button) { onMoreClick(entry.id) }
                .semantics { contentDescription = description }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val gap = AquaLightLibraryGeometry.moreDotGap.toPx()
            repeat(3) { index ->
                drawCircle(
                    color = visuals.colors.card.secondaryText,
                    radius = AquaLightLibraryGeometry.moreDotRadius.toPx(),
                    center = center.copy(y = center.y + (index - 1) * gap)
                )
            }
        }
    }
}

@Composable
private fun LibraryKindBadge(
    entry: DeviceLightLibraryEntry,
    visuals: DeviceLightLibraryVisuals
) {
    val text = when (entry.payload) {
        is DeviceLightLibraryPayload.Manual ->
            stringResource(R.string.device_light_library_badge_manual)
        is DeviceLightLibraryPayload.Custom ->
            stringResource(R.string.device_light_library_badge_custom)
    }
    val shape = RoundedCornerShape(AquaLightLibraryGeometry.badgeCornerRadius)
    BasicText(
        text = text,
        style = visuals.typography.micro.copy(color = visuals.colors.action),
        modifier = Modifier
            .background(
                visuals.colors.action.copy(alpha = AquaLightLibraryAlpha.badgeSurface),
                shape
            )
            .padding(
                horizontal = AquaLightLibraryGeometry.badgeHorizontalPadding,
                vertical = AquaLightLibraryGeometry.badgeVerticalPadding
            )
    )
}

@Composable
private fun ManualValueRow(
    channel: DeviceLightLibraryChannel,
    scene: DeviceLightLibraryScene,
    visuals: DeviceLightLibraryVisuals
) {
    val percent = scene.channels.getValue(channel)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightLibraryGeometry.manualRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = channel.shortLabel(),
            style = visuals.typography.body,
            modifier = Modifier.width(AquaLightLibraryGeometry.manualLabelWidth)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(AquaLightLibraryGeometry.manualRailHeight)
                .background(
                    visuals.colors.card.secondaryText.copy(alpha = AquaLightLibraryAlpha.rail),
                    RoundedCornerShape(percent = 50)
                )
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent / 100f)
                    .background(
                        channel.libraryColor(visuals.colors),
                        RoundedCornerShape(percent = 50)
                    )
            )
        }
        BasicText(
            text = stringResource(R.string.device_light_library_channel_percent_format, percent),
            style = visuals.typography.body.copy(textAlign = TextAlign.End),
            modifier = Modifier.width(AquaLightLibraryGeometry.manualValueWidth)
        )
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
            .padding(horizontal = 12.dp),
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
            style = visuals.typography.body.copy(color = visuals.colors.action)
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
    val dayResources = listOf(
        R.string.device_light_library_day_monday,
        R.string.device_light_library_day_tuesday,
        R.string.device_light_library_day_wednesday,
        R.string.device_light_library_day_thursday,
        R.string.device_light_library_day_friday,
        R.string.device_light_library_day_saturday,
        R.string.device_light_library_day_sunday
    )
    val labels = mutableListOf<String>()
    dayResources.forEachIndexed { index, dayResource ->
        if (mask and (1 shl index) != 0) {
            labels += stringResource(dayResource)
        }
    }
    return labels.joinToString(" · ")
}
