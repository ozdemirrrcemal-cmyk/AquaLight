package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticWeekday
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface

@Composable
internal fun DeviceLightAutomaticEditorDaysCard(
    state: DeviceLightAutomaticProgramEditorUiState,
    actions: DeviceLightAutomaticDayActions,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightAutomaticEditorGeometry.cardPadding
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(
                DeviceLightAutomaticEditorGeometry.cardContentGap
            )
        ) {
            EditorSectionHeading(
                title = stringResource(R.string.device_light_auto_editor_days),
                subtitle = null,
                visuals = visuals,
                icon = { color ->
                    AutomaticCalendarIcon(
                        color = color,
                        modifier = Modifier.size(
                            DeviceLightAutomaticEditorGeometry.sectionIconSize
                        )
                    )
                }
            )
            QuickDayButtons(state, actions, visuals)
            WeekdayButtons(state, actions, visuals)
        }
    }
}

@Composable
private fun WeekdayButtons(
    state: DeviceLightAutomaticProgramEditorUiState,
    actions: DeviceLightAutomaticDayActions,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DeviceLightAutomaticEditorGeometry.dayButtonGap)
    ) {
        DeviceLightAutomaticWeekday.entries.forEach { day ->
            EditorSelectionButton(
                state = DeviceLightAutomaticSelectionState(
                    label = stringResource(day.labelRes()),
                    selected = state.draft.weekdaysMask and day.mask != 0,
                    enabled = state.contentEnabled
                ),
                onClick = { actions.onDayClick(day) },
                modifier = Modifier
                    .weight(DAY_BUTTON_WEIGHT)
                    .height(DeviceLightAutomaticEditorGeometry.dayButtonHeight),
                visuals = visuals
            )
        }
    }
}

@Composable
private fun QuickDayButtons(
    state: DeviceLightAutomaticProgramEditorUiState,
    actions: DeviceLightAutomaticDayActions,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            DeviceLightAutomaticEditorGeometry.quickDayButtonGap
        )
    ) {
        EditorSelectionButton(
            state = DeviceLightAutomaticSelectionState(
                label = stringResource(R.string.device_light_auto_every_day),
                selected = state.draft.weekdaysMask == DEVICE_LIGHT_AUTOMATIC_EVERY_DAY_MASK,
                enabled = state.contentEnabled
            ),
            onClick = actions.onEveryDayClick,
            visuals = visuals,
            modifier = Modifier
                .weight(QUICK_DAY_BUTTON_WEIGHT)
                .height(DeviceLightAutomaticEditorGeometry.quickDayButtonHeight)
        )
        EditorSelectionButton(
            state = DeviceLightAutomaticSelectionState(
                label = stringResource(R.string.device_light_auto_editor_weekdays),
                selected = state.draft.weekdaysMask == AUTOMATIC_WEEKDAYS_MASK,
                enabled = state.contentEnabled
            ),
            onClick = actions.onWeekdaysClick,
            visuals = visuals,
            modifier = Modifier
                .weight(QUICK_DAY_BUTTON_WEIGHT)
                .height(DeviceLightAutomaticEditorGeometry.quickDayButtonHeight)
        )
        EditorSelectionButton(
            state = DeviceLightAutomaticSelectionState(
                label = stringResource(R.string.device_light_auto_editor_weekend),
                selected = state.draft.weekdaysMask == AUTOMATIC_WEEKEND_MASK,
                enabled = state.contentEnabled
            ),
            onClick = actions.onWeekendClick,
            visuals = visuals,
            modifier = Modifier
                .weight(QUICK_DAY_BUTTON_WEIGHT)
                .height(DeviceLightAutomaticEditorGeometry.quickDayButtonHeight)
        )
        EditorSelectionButton(
            state = DeviceLightAutomaticSelectionState(
                label = stringResource(R.string.device_light_auto_editor_custom_days),
                selected = state.draft.weekdaysMask !in AUTOMATIC_QUICK_DAY_MASKS,
                enabled = state.contentEnabled
            ),
            onClick = actions.onCustomClick,
            visuals = visuals,
            modifier = Modifier
                .weight(QUICK_DAY_BUTTON_WEIGHT)
                .height(DeviceLightAutomaticEditorGeometry.quickDayButtonHeight)
        )
    }
}

@Composable
internal fun EditorSelectionButton(
    state: DeviceLightAutomaticSelectionState,
    onClick: () -> Unit,
    modifier: Modifier,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val alpha = if (state.enabled) ENABLED_ALPHA else DeviceLightAutomaticEditorAlpha.disabled
    val color = if (state.selected) {
        visuals.colors.action
    } else {
        visuals.colors.card.mediaOutline
    }
    Box(
        modifier = modifier
            .clip(DeviceLightAutomaticEditorGeometry.dayButtonShape)
            .background(
                color.copy(
                    alpha = if (state.selected) {
                        ENABLED_ALPHA * alpha
                    } else {
                        DeviceLightAutomaticEditorAlpha.unselectedSurface * alpha
                    }
                )
            )
            .border(
                DeviceLightAutomaticEditorGeometry.actionBorderWidth,
                color.copy(alpha = alpha),
                DeviceLightAutomaticEditorGeometry.dayButtonShape
            )
            .clickable(enabled = state.enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = state.label,
            style = visuals.typography.caption.copy(
                color = visuals.colors.card.primaryText.copy(alpha = alpha),
                textAlign = TextAlign.Center
            ),
            maxLines = BUTTON_LABEL_MAX_LINES
        )
    }
}

internal data class DeviceLightAutomaticSelectionState(
    val label: String,
    val selected: Boolean,
    val enabled: Boolean
)

@Composable
internal fun EditorSectionHeading(
    title: String,
    subtitle: String?,
    visuals: DeviceLightAutomaticEditorVisuals,
    icon: @Composable (Color) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon(visuals.colors.action)
        Spacer(Modifier.width(DeviceLightAutomaticEditorGeometry.sectionIconGap))
        Column {
            BasicText(
                text = title,
                style = visuals.typography.title.copy(color = visuals.colors.card.primaryText)
            )
            subtitle?.let { supportingText ->
                Spacer(Modifier.height(DeviceLightAutomaticEditorGeometry.headingTextGap))
                BasicText(
                    text = supportingText,
                    style = visuals.typography.micro.copy(
                        color = visuals.colors.card.secondaryText
                    )
                )
            }
        }
    }
}

private fun DeviceLightAutomaticWeekday.labelRes(): Int = when (this) {
    DeviceLightAutomaticWeekday.MONDAY -> R.string.device_light_auto_day_monday
    DeviceLightAutomaticWeekday.TUESDAY -> R.string.device_light_auto_day_tuesday
    DeviceLightAutomaticWeekday.WEDNESDAY -> R.string.device_light_auto_day_wednesday
    DeviceLightAutomaticWeekday.THURSDAY -> R.string.device_light_auto_day_thursday
    DeviceLightAutomaticWeekday.FRIDAY -> R.string.device_light_auto_day_friday
    DeviceLightAutomaticWeekday.SATURDAY -> R.string.device_light_auto_day_saturday
    DeviceLightAutomaticWeekday.SUNDAY -> R.string.device_light_auto_day_sunday
}

private val AUTOMATIC_WEEKDAYS_MASK = DeviceLightAutomaticWeekday.entries
    .take(AUTOMATIC_WEEKDAY_COUNT)
    .sumOf(DeviceLightAutomaticWeekday::mask)
private val AUTOMATIC_WEEKEND_MASK = DeviceLightAutomaticWeekday.entries
    .drop(AUTOMATIC_WEEKDAY_COUNT)
    .sumOf(DeviceLightAutomaticWeekday::mask)
private val AUTOMATIC_QUICK_DAY_MASKS = setOf(
    DEVICE_LIGHT_AUTOMATIC_EVERY_DAY_MASK,
    AUTOMATIC_WEEKDAYS_MASK,
    AUTOMATIC_WEEKEND_MASK
)
private const val AUTOMATIC_WEEKDAY_COUNT = 5
private const val DAY_BUTTON_WEIGHT = 1f
private const val QUICK_DAY_BUTTON_WEIGHT = 1f
private const val ENABLED_ALPHA = 1f
private const val BUTTON_LABEL_MAX_LINES = 1
