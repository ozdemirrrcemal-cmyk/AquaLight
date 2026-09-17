package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightManualColors

@Composable
internal fun DeviceLightCustomCurveScreen(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    modifier: Modifier = Modifier
) {
    val background = colorResource(R.color.background_color)
    val colors = aquaLightManualColors()
    val visuals = DeviceLightCustomVisuals(colors, aquaDeviceCardTypography(colors.card))
    if (state.channels.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().background(background)
                .padding(SCREEN_HORIZONTAL_PADDING_DP.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = stringResource(R.string.device_light_custom_data_unavailable),
                style = visuals.typography.body.copy(
                    color = visuals.colors.card.secondaryText,
                    textAlign = TextAlign.Center
                )
            )
        }
        return
    }
    Box(modifier = modifier.fillMaxSize().background(background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SCREEN_HORIZONTAL_PADDING_DP.dp,
                top = SCREEN_TOP_PADDING_DP.dp,
                end = SCREEN_HORIZONTAL_PADDING_DP.dp,
                bottom = SCREEN_BOTTOM_PADDING_DP.dp
            ),
            verticalArrangement = Arrangement.spacedBy(SECTION_SPACING_DP.dp)
        ) {
            if (state.hasUnsavedChanges) {
                item(key = "unsaved") { UnsavedChangesIndicator(visuals) }
            }
            item(key = "curve") { CurveCard(state, actions, visuals) }
            item(key = "point") { SelectedPointCard(state, actions, visuals) }
            item(key = "days") { ProgramDaysCard(state, actions, visuals) }
            item(key = "preview") { VirtualTimePreviewCard(state, actions, visuals) }
        }
        LibraryActions(
            state = state,
            actions = actions,
            visuals = visuals,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(background)
                .padding(
                    start = SCREEN_HORIZONTAL_PADDING_DP.dp,
                    top = STICKY_ACTION_TOP_PADDING_DP.dp,
                    end = SCREEN_HORIZONTAL_PADDING_DP.dp,
                    bottom = STICKY_ACTION_BOTTOM_PADDING_DP.dp
                )
        )
    }
}

@Composable
private fun UnsavedChangesIndicator(visuals: DeviceLightCustomVisuals) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = UNSAVED_HORIZONTAL_PADDING_DP.dp,
            vertical = UNSAVED_VERTICAL_PADDING_DP.dp
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(UNSAVED_DOT_SIZE_DP.dp).background(
                visuals.colors.card.warning,
                RoundedCornerShape(percent = CIRCLE_SHAPE_PERCENT)
            )
        )
        BasicText(
            text = stringResource(R.string.device_light_custom_unsaved_changes),
            style = visuals.typography.caption.copy(color = visuals.colors.card.warning),
            modifier = Modifier.padding(start = UNSAVED_TEXT_PADDING_DP.dp)
        )
    }
}

@Composable
private fun ProgramDaysCard(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DAYS_CONTENT_SPACING_DP.dp)) {
            BasicText(
                text = stringResource(R.string.device_light_custom_days_heading),
                style = visuals.typography.title.copy(color = visuals.colors.card.primaryText)
            )
            WeekdayRow(state, actions, visuals)
        }
    }
}

@Composable
private fun WeekdayRow(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DAY_SPACING_DP.dp)
    ) {
        weekdayLabels().forEachIndexed { index, label ->
            DayButton(
                state = DayButtonState(
                    label = label,
                    selected = state.draft.weekdaysMask and customWeekdayMask(index) != 0,
                    enabled = state.contentEnabled && !state.operationInProgress
                ),
                onClick = { actions.onWeekdayClick(index) },
                modifier = Modifier.weight(1f),
                visuals = visuals
            )
        }
    }
}

@Composable
private fun DayButton(
    state: DayButtonState,
    onClick: () -> Unit,
    modifier: Modifier,
    visuals: DeviceLightCustomVisuals
) {
    val alpha = if (state.enabled) ENABLED_ALPHA else DISABLED_ALPHA
    val shape = RoundedCornerShape(DAY_BUTTON_CORNER_DP.dp)
    Box(
        modifier = modifier.height(DAY_BUTTON_HEIGHT_DP.dp).clip(shape)
            .background(
                if (state.selected) visuals.colors.action.copy(alpha = alpha) else Color.Transparent
            )
            .border(BORDER_WIDTH_DP.dp, visuals.colors.card.mediaOutline.copy(alpha = alpha), shape)
            .clickable(enabled = state.enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = state.label,
            maxLines = 1,
            style = visuals.typography.caption.copy(
                color = visuals.colors.card.primaryText.copy(alpha = alpha),
                textAlign = TextAlign.Center
            )
        )
    }
}

private data class DayButtonState(
    val label: String,
    val selected: Boolean,
    val enabled: Boolean
)

private const val SCREEN_HORIZONTAL_PADDING_DP = 9
private const val SCREEN_TOP_PADDING_DP = 2
private const val SCREEN_BOTTOM_PADDING_DP = 86
private const val SECTION_SPACING_DP = 8
private const val STICKY_ACTION_TOP_PADDING_DP = 8
private const val STICKY_ACTION_BOTTOM_PADDING_DP = 12
private const val UNSAVED_HORIZONTAL_PADDING_DP = 8
private const val UNSAVED_VERTICAL_PADDING_DP = 1
private const val UNSAVED_DOT_SIZE_DP = 8
private const val UNSAVED_TEXT_PADDING_DP = 8
private const val DAYS_CONTENT_SPACING_DP = 8
private const val DAY_SPACING_DP = 5
private const val ENABLED_ALPHA = 1f
private const val DISABLED_ALPHA = 0.38f
private const val DAY_BUTTON_CORNER_DP = 11
private const val DAY_BUTTON_HEIGHT_DP = 42
private const val BORDER_WIDTH_DP = 1
