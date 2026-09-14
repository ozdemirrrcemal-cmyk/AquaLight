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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.aquaLightManualColors

@Composable
internal fun DeviceLightCustomCurveScreen(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightManualColors()
    val visuals = DeviceLightCustomVisuals(colors, aquaDeviceCardTypography(colors.card))
    LazyColumn(
        modifier = modifier.fillMaxSize().background(colorResource(R.color.background_color)),
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
        item(key = "days") { ProgramDaysCard(state, actions, visuals) }
        item(key = "curve") { CurveCard(state, actions, visuals) }
        item(key = "point") { SelectedPointCard(state, actions, visuals) }
        item(key = "preview") { VirtualTimePreviewCard(state, actions, visuals) }
        item(key = "library") { LibraryActions(state, actions, visuals) }
        item(key = "reset") { ResetAction(state, actions, visuals) }
        item(key = "info") { CustomInformation(visuals) }
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
            style = visuals.typography.caption.copy(color = visuals.colors.card.secondaryText),
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
            SectionHeading(
                title = stringResource(R.string.device_light_custom_program_days),
                subtitle = stringResource(R.string.device_light_custom_program_days_summary),
                visuals = visuals
            )
            DaySelectionRow(state, actions, visuals)
        }
    }
}

@Composable
private fun DaySelectionRow(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DAY_SPACING_DP.dp)
    ) {
        DayButton(
            state = DayButtonState(
                label = stringResource(R.string.device_light_custom_every_day),
                selected = state.draft.weekdaysMask == EVERY_DAY_MASK,
                enabled = state.contentEnabled
            ),
            onClick = actions.onEveryDayClick,
            modifier = Modifier.weight(EVERY_DAY_WEIGHT),
            visuals = visuals
        )
        weekdayLabels().forEachIndexed { index, label ->
            DayButton(
                state = DayButtonState(
                    label = label,
                    selected = state.draft.weekdaysMask and (1 shl index) != 0,
                    enabled = state.contentEnabled
                ),
                onClick = { actions.onWeekdayClick(index) },
                modifier = Modifier.weight(WEEKDAY_WEIGHT),
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        )
    }
}

@Composable
private fun ResetAction(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    CustomOutlinedButton(
        button = CustomOutlinedButtonState(
            label = stringResource(R.string.device_light_custom_reset),
            description = stringResource(R.string.device_light_custom_reset_description),
            enabled = !state.operationInProgress
        ),
        appearance = CustomOutlinedButtonAppearance(
            color = visuals.colors.card.primaryText,
            iconRes = R.drawable.ic_dosing_reset_24
        ),
        onClick = actions.onResetClick,
        modifier = Modifier.fillMaxWidth()
    )
}

private data class DayButtonState(
    val label: String,
    val selected: Boolean,
    val enabled: Boolean
)

private const val SCREEN_HORIZONTAL_PADDING_DP = 9
private const val SCREEN_TOP_PADDING_DP = 2
private const val SCREEN_BOTTOM_PADDING_DP = 18
private const val SECTION_SPACING_DP = 7
private const val UNSAVED_HORIZONTAL_PADDING_DP = 45
private const val UNSAVED_VERTICAL_PADDING_DP = 1
private const val UNSAVED_DOT_SIZE_DP = 8
private const val UNSAVED_TEXT_PADDING_DP = 8
private const val DAYS_CONTENT_SPACING_DP = 10
private const val DAY_SPACING_DP = 5
private const val EVERY_DAY_WEIGHT = 1.8f
private const val WEEKDAY_WEIGHT = 1f
private const val ENABLED_ALPHA = 1f
private const val DISABLED_ALPHA = 0.38f
private const val DAY_BUTTON_CORNER_DP = 11
private const val DAY_BUTTON_HEIGHT_DP = 40
private const val BORDER_WIDTH_DP = 1
