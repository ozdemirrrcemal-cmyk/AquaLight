package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightManualColors

@Composable
internal fun DeviceLightAdaptationScreen(
    state: DeviceLightAdaptationUiState,
    actions: DeviceLightAdaptationActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightManualColors()
    val visuals = DeviceLightAdaptationVisuals(
        colors = colors,
        typography = aquaDeviceCardTypography(colors.card)
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color))
    ) {
        LazyColumn(
            modifier = Modifier.weight(CONTENT_WEIGHT),
            contentPadding = PaddingValues(
                start = DeviceLightAdaptationGeometry.screenHorizontalPadding,
                top = DeviceLightAdaptationGeometry.screenTopPadding,
                end = DeviceLightAdaptationGeometry.screenHorizontalPadding,
                bottom = DeviceLightAdaptationGeometry.sectionGap
            ),
            verticalArrangement = Arrangement.spacedBy(DeviceLightAdaptationGeometry.sectionGap)
        ) {
            items(state.screenSections(), key = AdaptationSection::key) { section ->
                AdaptationSectionContent(section, state, actions, visuals)
            }
        }
        DeviceLightAdaptationAction(state, actions, visuals)
    }
}

@Composable
private fun AdaptationSectionContent(
    section: AdaptationSection,
    state: DeviceLightAdaptationUiState,
    actions: DeviceLightAdaptationActions,
    visuals: DeviceLightAdaptationVisuals
) {
    when (section) {
        AdaptationSection.INTRO -> AdaptationIntroCard(state, visuals)
        AdaptationSection.SETTINGS -> AdaptationSettingsCard(state, actions, visuals)
        AdaptationSection.SETUP_SUMMARY -> AdaptationSetupSummaryCard(state, visuals)
        AdaptationSection.ACTIVE_PROGRESS -> AdaptationActiveProgressCard(state, visuals)
        AdaptationSection.ACTIVE_SUMMARY -> AdaptationActiveSummaryCard(state, visuals)
        AdaptationSection.COMPLETED -> AdaptationCompletedCard(state, visuals)
        AdaptationSection.INFO -> AdaptationInformationCard(state, visuals)
    }
}

@Composable
private fun DeviceLightAdaptationAction(
    state: DeviceLightAdaptationUiState,
    actions: DeviceLightAdaptationActions,
    visuals: DeviceLightAdaptationVisuals
) {
    val action = state.toActionConfiguration(actions)
    val alpha = if (action.enabled) 1f else DeviceLightAdaptationAlpha.disabled
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = DeviceLightAdaptationGeometry.screenHorizontalPadding,
                top = DeviceLightAdaptationGeometry.actionTopPadding,
                end = DeviceLightAdaptationGeometry.screenHorizontalPadding,
                bottom = DeviceLightAdaptationGeometry.screenBottomPadding
            )
            .height(DeviceLightAdaptationGeometry.actionHeight)
            .clip(DeviceLightAdaptationGeometry.actionShape)
            .background(if (action.filled) visuals.colors.action.copy(alpha = alpha) else Color.Transparent)
            .border(
                DeviceLightAdaptationGeometry.actionBorderWidth,
                action.color(visuals).copy(alpha = alpha),
                DeviceLightAdaptationGeometry.actionShape
            )
            .clickable(enabled = action.enabled, role = Role.Button, onClick = action.onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = stringResource(action.labelRes),
            style = visuals.typography.title.copy(
                color = action.textColor(visuals).copy(alpha = alpha),
                textAlign = TextAlign.Center
            )
        )
    }
}

private fun DeviceLightAdaptationUiState.screenSections(): List<AdaptationSection> =
    when (screenState) {
        DeviceLightAdaptationScreenState.SETUP -> listOf(
            AdaptationSection.INTRO,
            AdaptationSection.SETTINGS,
            AdaptationSection.SETUP_SUMMARY,
            AdaptationSection.INFO
        )
        DeviceLightAdaptationScreenState.ACTIVE -> listOf(
            AdaptationSection.INTRO,
            AdaptationSection.ACTIVE_PROGRESS,
            AdaptationSection.ACTIVE_SUMMARY,
            AdaptationSection.INFO
        )
        DeviceLightAdaptationScreenState.COMPLETED -> listOf(
            AdaptationSection.INTRO,
            AdaptationSection.COMPLETED,
            AdaptationSection.ACTIVE_SUMMARY,
            AdaptationSection.INFO
        )
    }

private fun DeviceLightAdaptationUiState.toActionConfiguration(
    actions: DeviceLightAdaptationActions
): AdaptationActionConfiguration = when (screenState) {
    DeviceLightAdaptationScreenState.SETUP -> AdaptationActionConfiguration(
        labelRes = R.string.device_light_adaptation_start_action,
        enabled = canStart,
        filled = true,
        danger = false,
        onClick = actions.onStartClick
    )
    DeviceLightAdaptationScreenState.ACTIVE -> AdaptationActionConfiguration(
        labelRes = R.string.device_light_adaptation_stop_action,
        enabled = canStop,
        filled = false,
        danger = true,
        onClick = actions.onStopClick
    )
    DeviceLightAdaptationScreenState.COMPLETED -> AdaptationActionConfiguration(
        labelRes = R.string.device_light_adaptation_configure_again_action,
        enabled = contentEnabled && !operationInProgress,
        filled = true,
        danger = false,
        onClick = actions.onConfigureAgainClick
    )
}

private enum class AdaptationSection(val key: String) {
    INTRO("intro"),
    SETTINGS("settings"),
    SETUP_SUMMARY("setup-summary"),
    ACTIVE_PROGRESS("active-progress"),
    ACTIVE_SUMMARY("active-summary"),
    COMPLETED("completed"),
    INFO("info")
}

private data class AdaptationActionConfiguration(
    val labelRes: Int,
    val enabled: Boolean,
    val filled: Boolean,
    val danger: Boolean,
    val onClick: () -> Unit
) {
    fun color(visuals: DeviceLightAdaptationVisuals) =
        if (danger) visuals.colors.card.danger else visuals.colors.action

    fun textColor(visuals: DeviceLightAdaptationVisuals) = when {
        danger -> visuals.colors.card.danger
        filled -> visuals.colors.card.primaryText
        else -> visuals.colors.action
    }
}

private const val CONTENT_WEIGHT = 1f
