package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightLibraryGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AutomaticCalendarIcon
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AutomaticClockIcon
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AutomaticMoreButton
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.DeviceLightAutomaticGeometry

@Composable
internal fun ManualLibraryCard(
    entry: DeviceLightLibraryEntry,
    payload: DeviceLightLibraryPayload.Manual,
    presentation: DeviceLightLibraryCardPresentation,
    actions: DeviceLightLibraryActions,
    visuals: DeviceLightLibraryVisuals
) {
    LibraryCardSurface {
        LibraryCardHeader(entry, actions, visuals)
        ManualLibrarySummary(visuals)
        LibraryCardDivider(visuals)
        ManualChannelSummary(entry.channels, presentation.descriptors, payload.scene, visuals)
    }
}

@Composable
internal fun CustomLibraryCard(
    entry: DeviceLightLibraryEntry,
    payload: DeviceLightLibraryPayload.Custom,
    presentation: DeviceLightLibraryCardPresentation,
    actions: DeviceLightLibraryActions,
    visuals: DeviceLightLibraryVisuals
) {
    LibraryCardSurface {
        LibraryCardHeader(entry, actions, visuals)
        CustomLibrarySummary(payload, visuals)
        LibraryCardDivider(visuals)
        CustomChannelSummary(entry.channels, presentation.descriptors, payload, visuals)
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

private const val EVERY_DAY_MASK = 0x7f
