@file:Suppress("LongMethod", "LongParameterList", "MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.lighting.AquariumObservationSeverity
import com.aqua.aqualight.application.aquarium.lighting.Co2Status
import com.aqua.aqualight.application.aquarium.lighting.PlantDensity
import com.aqua.aqualight.application.aquarium.lighting.PlantLightDemand
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface

@Composable
internal fun QuickSetupEditorCard(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    visuals: DeviceLightQuickSetupVisuals
) {
    val evaluationDay = state.snapshot?.input?.evaluationEpochDay
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightQuickSetupGeometry.cardPadding
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.contentGap)) {
            QuickSetupSectionTitle(
                iconRes = R.drawable.ic_edit_24,
                title = stringResource(R.string.device_light_smart_setup_profile_title),
                subtitle = stringResource(R.string.device_light_smart_setup_profile_editor_summary),
                visuals = visuals
            )

            QuickSetupChoiceSection(
                title = stringResource(R.string.device_light_smart_setup_aquarium_age),
                helper = state.editor.aquariumAgeDays(evaluationDay)?.let { days ->
                    stringResource(R.string.device_light_smart_setup_day_value, days)
                } ?: stringResource(R.string.device_light_smart_setup_not_selected),
                values = AQUARIUM_AGE_PRESETS,
                selected = state.editor.aquariumAgeDays(evaluationDay),
                label = { days -> stringResource(R.string.device_light_smart_setup_day_value, days) },
                onSelected = actions.onAquariumAgeSelected,
                visuals = visuals
            )
            QuickSetupStepper(
                label = stringResource(R.string.device_light_smart_setup_exact_aquarium_age),
                value = state.editor.aquariumAgeDays(evaluationDay)?.let { days ->
                    stringResource(R.string.device_light_smart_setup_day_value, days)
                } ?: stringResource(R.string.device_light_smart_setup_not_selected),
                decreaseDescription = stringResource(
                    R.string.device_light_smart_setup_decrease_aquarium_age
                ),
                increaseDescription = stringResource(
                    R.string.device_light_smart_setup_increase_aquarium_age
                ),
                enabled = state.editor.setupDateEpochDay != null,
                onDecrease = { actions.onAquariumAgeAdjusted(-1) },
                onIncrease = { actions.onAquariumAgeAdjusted(1) },
                visuals = visuals
            )

            if (state.isPlanted) {
                QuickSetupChoiceSection(
                    title = stringResource(R.string.device_light_smart_setup_plant_density),
                    helper = stringResource(R.string.device_light_smart_setup_plant_density_help),
                    values = PlantDensity.entries,
                    selected = state.editor.plantDensity,
                    label = { value -> stringResource(value.labelRes()) },
                    onSelected = actions.onPlantDensitySelected,
                    visuals = visuals
                )
                QuickSetupChoiceSection(
                    title = stringResource(R.string.device_light_smart_setup_plant_demand),
                    helper = stringResource(R.string.device_light_smart_setup_plant_demand_help),
                    values = PlantLightDemand.entries,
                    selected = state.editor.highestPlantLightDemand,
                    label = { value -> stringResource(value.labelRes()) },
                    onSelected = actions.onPlantLightDemandSelected,
                    visuals = visuals
                )
            }

            QuickSetupChoiceSection(
                title = stringResource(R.string.device_light_smart_setup_co2_status),
                helper = stringResource(R.string.device_light_smart_setup_co2_help),
                values = Co2Status.entries,
                selected = state.editor.co2Status,
                label = { value -> stringResource(value.labelRes()) },
                onSelected = actions.onCo2StatusSelected,
                visuals = visuals
            )
            QuickSetupChoiceSection(
                title = stringResource(R.string.device_light_smart_setup_active_soil),
                helper = stringResource(R.string.device_light_smart_setup_active_soil_help),
                values = listOf(false, true),
                selected = state.editor.isActiveSoil,
                label = { active ->
                    stringResource(
                        if (active) R.string.device_light_smart_setup_yes
                        else R.string.device_light_smart_setup_no
                    )
                },
                onSelected = actions.onActiveSoilSelected,
                visuals = visuals,
                columns = 2
            )

            QuickSetupMeasurement(
                title = stringResource(R.string.device_light_smart_setup_water_depth),
                helper = stringResource(R.string.device_light_smart_setup_water_depth_help),
                value = state.editor.waterDepthCm,
                presets = WATER_DEPTH_PRESETS,
                onSelected = actions.onWaterDepthSelected,
                onAdjusted = actions.onWaterDepthAdjusted,
                decreaseDescription = stringResource(
                    R.string.device_light_smart_setup_decrease_water_depth
                ),
                increaseDescription = stringResource(
                    R.string.device_light_smart_setup_increase_water_depth
                ),
                visuals = visuals
            )
            QuickSetupMeasurement(
                title = stringResource(R.string.device_light_smart_setup_mount_height),
                helper = stringResource(R.string.device_light_smart_setup_mount_height_help),
                value = state.editor.fixtureMountHeightCm,
                presets = MOUNT_HEIGHT_PRESETS,
                onSelected = actions.onMountHeightSelected,
                onAdjusted = actions.onMountHeightAdjusted,
                decreaseDescription = stringResource(
                    R.string.device_light_smart_setup_decrease_mount_height
                ),
                increaseDescription = stringResource(
                    R.string.device_light_smart_setup_increase_mount_height
                ),
                visuals = visuals
            )

            QuickSetupViewingWindowEditor(state.editor, actions, visuals)

            QuickSetupChoiceSection(
                title = stringResource(R.string.device_light_smart_setup_algae_observation),
                helper = stringResource(R.string.device_light_smart_setup_observation_help),
                values = AquariumObservationSeverity.entries,
                selected = state.editor.algaeObservation,
                label = { value -> stringResource(value.labelRes()) },
                onSelected = actions.onAlgaeObservationSelected,
                visuals = visuals
            )
            QuickSetupChoiceSection(
                title = stringResource(R.string.device_light_smart_setup_stress_observation),
                helper = stringResource(R.string.device_light_smart_setup_observation_help),
                values = AquariumObservationSeverity.entries,
                selected = state.editor.plantStressObservation,
                label = { value -> stringResource(value.labelRes()) },
                onSelected = actions.onPlantStressObservationSelected,
                visuals = visuals
            )
            QuickSetupObservationDate(state, actions, visuals)
        }
    }
}

@Composable
private fun QuickSetupMeasurement(
    title: String,
    helper: String,
    value: Int?,
    presets: List<Int>,
    onSelected: (Int) -> Unit,
    onAdjusted: (Int) -> Unit,
    decreaseDescription: String,
    increaseDescription: String,
    visuals: DeviceLightQuickSetupVisuals
) {
    QuickSetupChoiceSection(
        title = title,
        helper = helper,
        values = presets,
        selected = value,
        label = { centimeters ->
            stringResource(R.string.device_light_smart_setup_centimeters, centimeters)
        },
        onSelected = onSelected,
        visuals = visuals,
        columns = 4
    )
    QuickSetupStepper(
        label = stringResource(R.string.device_light_smart_setup_exact_value),
        value = value?.let { centimeters ->
            stringResource(R.string.device_light_smart_setup_centimeters, centimeters)
        } ?: stringResource(R.string.device_light_smart_setup_not_selected),
        decreaseDescription = decreaseDescription,
        increaseDescription = increaseDescription,
        enabled = value != null,
        onDecrease = { onAdjusted(-1) },
        onIncrease = { onAdjusted(1) },
        visuals = visuals
    )
}

@Composable
private fun QuickSetupViewingWindowEditor(
    editor: DeviceLightQuickSetupEditor,
    actions: DeviceLightQuickSetupActions,
    visuals: DeviceLightQuickSetupVisuals
) {
    val selected = editor.preferredViewingStartMinuteOfDay?.let { start ->
        editor.preferredViewingEndMinuteOfDay?.let { end -> start to end }
    }
    QuickSetupChoiceSection(
        title = stringResource(R.string.device_light_smart_setup_viewing_window),
        helper = stringResource(R.string.device_light_smart_setup_viewing_window_help),
        values = VIEWING_WINDOW_PRESETS,
        selected = selected,
        label = { (start, end) ->
            stringResource(
                R.string.device_light_smart_setup_time_range,
                quickSetupTimeText(start),
                quickSetupTimeText(end)
            )
        },
        onSelected = { (start, end) -> actions.onViewingWindowSelected(start, end) },
        visuals = visuals,
        columns = 2
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)
    ) {
        QuickSetupStepper(
            label = stringResource(R.string.device_light_smart_setup_viewing_start),
            value = editor.preferredViewingStartMinuteOfDay?.let { quickSetupTimeText(it) }
                ?: stringResource(R.string.device_light_smart_setup_not_selected),
            decreaseDescription = stringResource(
                R.string.device_light_smart_setup_decrease_viewing_start
            ),
            increaseDescription = stringResource(
                R.string.device_light_smart_setup_increase_viewing_start
            ),
            enabled = selected != null,
            onDecrease = { actions.onViewingStartAdjusted(-TIME_STEP_MINUTES) },
            onIncrease = { actions.onViewingStartAdjusted(TIME_STEP_MINUTES) },
            visuals = visuals,
            modifier = Modifier.weight(1f)
        )
        QuickSetupStepper(
            label = stringResource(R.string.device_light_smart_setup_viewing_end),
            value = editor.preferredViewingEndMinuteOfDay?.let { quickSetupTimeText(it) }
                ?: stringResource(R.string.device_light_smart_setup_not_selected),
            decreaseDescription = stringResource(
                R.string.device_light_smart_setup_decrease_viewing_end
            ),
            increaseDescription = stringResource(
                R.string.device_light_smart_setup_increase_viewing_end
            ),
            enabled = selected != null,
            onDecrease = { actions.onViewingEndAdjusted(-TIME_STEP_MINUTES) },
            onIncrease = { actions.onViewingEndAdjusted(TIME_STEP_MINUTES) },
            visuals = visuals,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickSetupObservationDate(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    visuals: DeviceLightQuickSetupVisuals
) {
    val dateText = state.editor.observationDateEpochDay?.let { quickSetupDateText(it) }
        ?: stringResource(R.string.device_light_smart_setup_not_confirmed)
    Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)) {
        BasicText(
            text = stringResource(R.string.device_light_smart_setup_observation_date),
            style = visuals.typography.body
        )
        QuickSetupOutlineButton(
            text = stringResource(
                R.string.device_light_smart_setup_confirm_observations,
                dateText
            ),
            enabled = state.editor.algaeObservation != null &&
                state.editor.plantStressObservation != null,
            onClick = actions.onRecordObservationsToday,
            visuals = visuals,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun <T> QuickSetupChoiceSection(
    title: String,
    helper: String,
    values: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    visuals: DeviceLightQuickSetupVisuals,
    columns: Int = 3
) {
    Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)) {
        BasicText(text = title, style = visuals.typography.body)
        BasicText(text = helper, style = visuals.typography.micro)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)
        ) {
            values.chunked(columns).forEach { rowValues ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)
                ) {
                    rowValues.forEach { value ->
                        QuickSetupChoice(
                            text = label(value),
                            selected = value == selected,
                            onClick = { onSelected(value) },
                            visuals = visuals,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - rowValues.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun QuickSetupChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightQuickSetupVisuals,
    modifier: Modifier = Modifier
) {
    val border = if (selected) visuals.colors.action else visuals.colors.card.outline
    val background = if (selected) {
        visuals.colors.action.copy(alpha = DeviceLightQuickSetupAlpha.selectedSurface)
    } else {
        Color.Transparent
    }
    Box(
        modifier = modifier
            .height(DeviceLightQuickSetupGeometry.chipHeight)
            .clip(DeviceLightQuickSetupGeometry.chipShape)
            .background(background)
            .border(DeviceLightQuickSetupGeometry.actionBorderWidth, border, DeviceLightQuickSetupGeometry.chipShape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(DeviceLightQuickSetupGeometry.chipPadding),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = visuals.typography.body.copy(
                color = if (selected) visuals.colors.action else visuals.colors.card.primaryText,
                textAlign = TextAlign.Center
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun QuickSetupStepper(
    label: String,
    value: String,
    decreaseDescription: String,
    increaseDescription: String,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    visuals: DeviceLightQuickSetupVisuals,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)
    ) {
        QuickSetupStepButton(
            symbol = "−",
            description = decreaseDescription,
            enabled = enabled,
            onClick = onDecrease,
            visuals = visuals
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics { stateDescription = value },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                text = label,
                style = visuals.typography.micro.copy(textAlign = TextAlign.Center),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = value,
                style = visuals.typography.body.copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        QuickSetupStepButton(
            symbol = "+",
            description = increaseDescription,
            enabled = enabled,
            onClick = onIncrease,
            visuals = visuals
        )
    }
}

@Composable
private fun QuickSetupStepButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightQuickSetupVisuals
) {
    val alpha = if (enabled) 1f else DeviceLightQuickSetupAlpha.disabled
    Box(
        modifier = Modifier
            .size(DeviceLightQuickSetupGeometry.stepButtonSize)
            .clip(CircleShape)
            .border(
                DeviceLightQuickSetupGeometry.actionBorderWidth,
                visuals.colors.card.outline.copy(alpha = alpha),
                CircleShape
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = symbol,
            style = visuals.typography.title.copy(
                color = visuals.colors.card.primaryText.copy(alpha = alpha),
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
internal fun QuickSetupSectionTitle(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    visuals: DeviceLightQuickSetupVisuals,
    tone: Color = visuals.colors.action
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(DeviceLightQuickSetupGeometry.iconSize)
                .clip(DeviceLightQuickSetupGeometry.statusShape)
                .background(tone.copy(alpha = DeviceLightQuickSetupAlpha.softSurface)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tone),
                modifier = Modifier.size(DeviceLightQuickSetupGeometry.smallIconSize)
            )
        }
        Spacer(Modifier.width(DeviceLightQuickSetupGeometry.iconGap))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(text = title, style = visuals.typography.title)
            BasicText(text = subtitle, style = visuals.typography.caption)
        }
    }
}

@Composable
internal fun QuickSetupOutlineButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightQuickSetupVisuals,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else DeviceLightQuickSetupAlpha.disabled
    Box(
        modifier = modifier
            .height(DeviceLightQuickSetupGeometry.chipHeight)
            .clip(DeviceLightQuickSetupGeometry.chipShape)
            .border(
                DeviceLightQuickSetupGeometry.actionBorderWidth,
                visuals.colors.action.copy(alpha = alpha),
                DeviceLightQuickSetupGeometry.chipShape
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { if (!enabled) disabled() },
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = visuals.typography.body.copy(
                color = visuals.colors.action.copy(alpha = alpha),
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(horizontal = DeviceLightQuickSetupGeometry.smallGap)
        )
    }
}

private val AQUARIUM_AGE_PRESETS = listOf(7, 21, 45, 90, 365)
private val WATER_DEPTH_PRESETS = listOf(25, 35, 45, 55)
private val MOUNT_HEIGHT_PRESETS = listOf(0, 5, 10, 20)
private val VIEWING_WINDOW_PRESETS = listOf(
    8 * 60 to 16 * 60,
    10 * 60 to 18 * 60,
    12 * 60 to 20 * 60,
    14 * 60 to 22 * 60
)
private const val TIME_STEP_MINUTES = 15
