@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingAutomaticAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingAutomaticGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardPalette
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun DeviceCoolingAutomaticSettingsScreen(
    state: DeviceCoolingAutomaticSettingsUiState,
    onStartTemperatureClick: () -> Unit,
    onMaximumTemperatureClick: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AquaCoolingAutomaticGeometry.screenHorizontalPadding,
            top = AquaCoolingAutomaticGeometry.screenTopPadding,
            end = AquaCoolingAutomaticGeometry.screenHorizontalPadding,
            bottom = AquaCoolingAutomaticGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.sectionGap)
    ) {
        when (state.loadState) {
            DeviceCoolingAutomaticLoadState.LOADING -> item(key = "loading") {
                AutomaticMessageCard(
                    title = stringResource(R.string.device_cooling_automatic_loading_title),
                    message = stringResource(R.string.device_cooling_automatic_loading_message),
                    colors = colors,
                    typography = typography
                )
            }

            DeviceCoolingAutomaticLoadState.ERROR -> item(key = "error") {
                AutomaticMessageCard(
                    title = stringResource(R.string.device_cooling_automatic_unavailable_title),
                    message = stringResource(R.string.device_cooling_automatic_unavailable_message),
                    colors = colors,
                    typography = typography,
                    actionLabel = stringResource(R.string.device_cooling_history_retry),
                    onAction = onRetry
                )
            }

            DeviceCoolingAutomaticLoadState.CONTENT -> {
                item(key = "live") {
                    AutomaticLiveStatusCard(state, colors, typography)
                }
                item(key = "range") {
                    AutomaticTemperatureRangeCard(
                        state = state,
                        colors = colors,
                        typography = typography,
                        onStartTemperatureClick = onStartTemperatureClick,
                        onMaximumTemperatureClick = onMaximumTemperatureClick
                    )
                }
                item(key = "behavior") {
                    AutomaticBehaviorCard(state, colors, typography)
                }
                if (state.saveState == DeviceCoolingAutomaticSaveState.ERROR) {
                    item(key = "save-error") {
                        BasicText(
                            text = stringResource(R.string.device_cooling_automatic_save_failed),
                            style = typography.caption.copy(color = colors.danger),
                            modifier = Modifier.padding(
                                horizontal = AquaCoolingDashboardGeometry.cardHorizontalPadding
                            )
                        )
                    }
                }
                item(key = "save") {
                    AutomaticSaveButton(
                        state = state,
                        colors = colors,
                        typography = typography,
                        onSave = onSave
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomaticLiveStatusCard(
    state: DeviceCoolingAutomaticSettingsUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingAutomaticGeometry.liveCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.liveMetricGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_automatic_live_status_title),
                style = typography.title.copy(color = colors.primaryText)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.liveMetricGap)
            ) {
                AutomaticLiveMetric(
                    label = stringResource(R.string.device_cooling_automatic_water_temperature),
                    value = automaticTemperatureText(state.tankTemperatureC),
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.weight(1f)
                )
                AutomaticLiveMetric(
                    label = stringResource(R.string.device_cooling_automatic_fan_output),
                    value = automaticFanPercentText(state.fanPercentNow),
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.weight(1f)
                )
                AutomaticLiveMetric(
                    label = stringResource(R.string.device_cooling_automatic_status),
                    value = automaticRuntimeStatusText(state.fanPercentNow),
                    colors = colors,
                    typography = typography,
                    valueColor = if ((state.fanPercentNow ?: 0.0) > 0.5) {
                        AquaCoolingDashboardPalette.success
                    } else {
                        colors.secondaryText
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AutomaticLiveMetric(
    label: String,
    value: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = colors.primaryText
) {
    Column(
        modifier = modifier.padding(vertical = AquaCoolingAutomaticGeometry.liveMetricVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.liveMetricGap / 2)
    ) {
        BasicText(
            text = label,
            style = typography.micro.copy(color = colors.secondaryText),
            maxLines = 1
        )
        BasicText(
            text = value,
            style = typography.body.copy(color = valueColor),
            maxLines = 1
        )
    }
}

@Composable
private fun AutomaticTemperatureRangeCard(
    state: DeviceCoolingAutomaticSettingsUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onStartTemperatureClick: () -> Unit,
    onMaximumTemperatureClick: () -> Unit
) {
    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingAutomaticGeometry.rangeCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.editorRowGap)
        ) {
            BasicText(
                text = stringResource(R.string.device_cooling_automatic_range_title),
                style = typography.title.copy(color = colors.primaryText)
            )
            AutomaticEditorRow(
                title = stringResource(R.string.device_cooling_fan_start_temperature),
                helper = stringResource(R.string.device_cooling_automatic_start_helper),
                value = automaticTemperatureText(state.draftStartTemperatureC),
                enabled = state.editable,
                colors = colors,
                typography = typography,
                onClick = onStartTemperatureClick
            )
            AutomaticEditorRow(
                title = stringResource(R.string.device_cooling_max_speed_temperature),
                helper = stringResource(R.string.device_cooling_automatic_max_helper),
                value = automaticTemperatureText(state.draftMaximumSpeedTemperatureC),
                enabled = state.editable,
                colors = colors,
                typography = typography,
                onClick = onMaximumTemperatureClick
            )
            AutomaticTemperatureRangeVisual(
                startTemperatureC = state.draftStartTemperatureC,
                maximumTemperatureC = state.draftMaximumSpeedTemperatureC,
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun AutomaticEditorRow(
    title: String,
    helper: String,
    value: String,
    enabled: Boolean,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onClick: () -> Unit
) {
    val shape = AquaCoolingAutomaticGeometry.editorRowShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                AquaCoolingDashboardPalette.insetSurface.copy(
                    alpha = AquaCoolingAutomaticAlpha.rowBackground
                )
            )
            .border(
                width = AquaCoolingDashboardGeometry.chartGridStrokeWidth,
                color = AquaCoolingDashboardPalette.insetOutline.copy(
                    alpha = AquaCoolingAutomaticAlpha.rowOutline
                ),
                shape = shape
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(
                horizontal = AquaCoolingAutomaticGeometry.editorRowHorizontalPadding,
                vertical = AquaCoolingAutomaticGeometry.editorRowVerticalPadding
            )
            .alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.editorRowGap / 2)
        ) {
            BasicText(
                text = title,
                style = typography.body.copy(color = colors.primaryText),
                maxLines = 1
            )
            BasicText(
                text = helper,
                style = typography.micro.copy(color = colors.secondaryText),
                maxLines = 2
            )
        }
        BasicText(
            text = value,
            style = typography.body.copy(
                color = colors.primaryText,
                textAlign = TextAlign.End
            ),
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(AquaCoolingAutomaticGeometry.editorRowGap))
        AutomaticChevron(colors = colors)
    }
}

@Composable
private fun AutomaticChevron(colors: AquaDeviceCardColors) {
    Canvas(
        modifier = Modifier
            .width(AquaCoolingAutomaticGeometry.editorChevronWidth)
            .height(AquaCoolingAutomaticGeometry.editorChevronHeight)
    ) {
        val x = size.width * 0.38f
        val top = size.height * 0.28f
        val middle = size.height * 0.50f
        val bottom = size.height * 0.72f
        drawLine(
            color = colors.accent,
            start = Offset(x, top),
            end = Offset(size.width * 0.62f, middle),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = colors.accent,
            start = Offset(size.width * 0.62f, middle),
            end = Offset(x, bottom),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun AutomaticTemperatureRangeVisual(
    startTemperatureC: Double?,
    maximumTemperatureC: Double?,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    if (startTemperatureC == null || maximumTemperatureC == null) return
    val lowerBound = floor(startTemperatureC - RANGE_VISUAL_MARGIN_C)
    val upperBound = ceil(maximumTemperatureC + RANGE_VISUAL_MARGIN_C)
        .coerceAtLeast(lowerBound + 1.0)
    val span = upperBound - lowerBound
    val startFraction = ((startTemperatureC - lowerBound) / span).toFloat().coerceIn(0f, 1f)
    val maximumFraction = ((maximumTemperatureC - lowerBound) / span).toFloat().coerceIn(0f, 1f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.rangeLegendGap)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AquaCoolingAutomaticGeometry.rangeVisualHeight)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val horizontalPadding = AquaCoolingAutomaticGeometry.rangeTrackHorizontalPadding.toPx()
                val trackWidth = (size.width - horizontalPadding * 2f).coerceAtLeast(1f)
                val y = size.height * 0.52f
                val startX = horizontalPadding + trackWidth * startFraction
                val maximumX = horizontalPadding + trackWidth * maximumFraction
                val trackStroke = AquaCoolingAutomaticGeometry.rangeTrackHeight.toPx()

                drawLine(
                    color = colors.secondaryText.copy(
                        alpha = AquaCoolingAutomaticAlpha.rangeInactiveTrack
                    ),
                    start = Offset(horizontalPadding, y),
                    end = Offset(horizontalPadding + trackWidth, y),
                    strokeWidth = trackStroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = colors.accent.copy(alpha = AquaCoolingAutomaticAlpha.rangeActiveTrack),
                    start = Offset(startX, y),
                    end = Offset(maximumX, y),
                    strokeWidth = trackStroke,
                    cap = StrokeCap.Round
                )
                listOf(startX, maximumX).forEach { x ->
                    drawCircle(
                        color = colors.primaryText,
                        radius = AquaCoolingAutomaticGeometry.rangeMarkerRadius.toPx() +
                            AquaCoolingAutomaticGeometry.rangeMarkerOutlineWidth.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = colors.accent.copy(
                            alpha = AquaCoolingAutomaticAlpha.rangeMarkerFill
                        ),
                        radius = AquaCoolingAutomaticGeometry.rangeMarkerRadius.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicText(
                    text = automaticAxisTemperatureText(lowerBound),
                    style = typography.micro.copy(color = colors.secondaryText)
                )
                BasicText(
                    text = automaticAxisTemperatureText(upperBound),
                    style = typography.micro.copy(color = colors.secondaryText)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(
                stringResource(R.string.device_cooling_automatic_zone_off),
                stringResource(R.string.device_cooling_automatic_zone_gradual),
                stringResource(R.string.device_cooling_automatic_zone_maximum)
            ).forEach { label ->
                BasicText(
                    text = label,
                    style = typography.micro.copy(
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AutomaticBehaviorCard(
    state: DeviceCoolingAutomaticSettingsUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val start = state.draftStartTemperatureC ?: return
    val maximum = state.draftMaximumSpeedTemperatureC ?: return
    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingAutomaticGeometry.behaviorCardMinimumHeight)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicText(
                text = stringResource(R.string.device_cooling_automatic_behavior_title),
                style = typography.title.copy(color = colors.primaryText)
            )
            Spacer(modifier = Modifier.height(AquaCoolingAutomaticGeometry.editorRowGap))
            AutomaticBehaviorRow(
                range = stringResource(R.string.device_cooling_automatic_below_format, start),
                behavior = stringResource(R.string.device_cooling_automatic_behavior_off),
                colors = colors,
                typography = typography
            )
            AutomaticBehaviorDivider(colors)
            AutomaticBehaviorRow(
                range = stringResource(
                    R.string.device_cooling_automatic_between_format,
                    start,
                    maximum
                ),
                behavior = stringResource(R.string.device_cooling_automatic_behavior_gradual),
                colors = colors,
                typography = typography
            )
            AutomaticBehaviorDivider(colors)
            AutomaticBehaviorRow(
                range = stringResource(R.string.device_cooling_automatic_above_format, maximum),
                behavior = stringResource(R.string.device_cooling_automatic_behavior_maximum),
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun AutomaticBehaviorRow(
    range: String,
    behavior: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AquaCoolingAutomaticGeometry.behaviorRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = range,
            style = typography.caption.copy(color = colors.secondaryText),
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        BasicText(
            text = behavior,
            style = typography.caption.copy(
                color = colors.primaryText,
                textAlign = TextAlign.End
            ),
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
    }
}

@Composable
private fun AutomaticBehaviorDivider(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingAutomaticGeometry.behaviorDividerHeight)
            .background(colors.outline.copy(alpha = AquaCoolingAutomaticAlpha.divider))
    )
}

@Composable
private fun AutomaticSaveButton(
    state: DeviceCoolingAutomaticSettingsUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    onSave: () -> Unit
) {
    val enabled = state.canSave
    val alpha = if (enabled) {
        AquaCoolingAutomaticAlpha.saveEnabled
    } else {
        AquaCoolingAutomaticAlpha.saveDisabled
    }
    val label = when (state.saveState) {
        DeviceCoolingAutomaticSaveState.SAVING ->
            stringResource(R.string.device_cooling_automatic_saving)
        DeviceCoolingAutomaticSaveState.SAVED ->
            stringResource(R.string.device_cooling_automatic_saved)
        DeviceCoolingAutomaticSaveState.IDLE,
        DeviceCoolingAutomaticSaveState.ERROR ->
            stringResource(R.string.device_cooling_automatic_save)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingAutomaticGeometry.actionHeight)
            .clip(AquaCoolingAutomaticGeometry.actionShape)
            .background(colors.accent.copy(alpha = alpha))
            .clickable(enabled = enabled, role = Role.Button, onClick = onSave)
            .padding(horizontal = AquaCoolingAutomaticGeometry.actionHorizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = typography.body.copy(
                color = colors.primaryText,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun AutomaticMessageCard(
    title: String,
    message: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingAutomaticGeometry.messageCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.messageGap)
        ) {
            BasicText(
                text = title,
                style = typography.title.copy(color = colors.primaryText)
            )
            BasicText(
                text = message,
                style = typography.caption.copy(color = colors.secondaryText)
            )
            if (actionLabel != null && onAction != null) {
                val shape = AquaCoolingAutomaticGeometry.retryShape
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(
                            colors.accent.copy(alpha = AquaCoolingAutomaticAlpha.retryBackground)
                        )
                        .border(
                            width = AquaCoolingDashboardGeometry.chartGridStrokeWidth,
                            color = colors.accent,
                            shape = shape
                        )
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(
                            horizontal = AquaCoolingAutomaticGeometry.retryHorizontalPadding,
                            vertical = AquaCoolingAutomaticGeometry.retryVerticalPadding
                        )
                ) {
                    BasicText(
                        text = actionLabel,
                        style = typography.caption.copy(color = colors.accent)
                    )
                }
            }
        }
    }
}

@Composable
private fun automaticTemperatureText(value: Double?): String = if (value?.isFinite() == true) {
    stringResource(R.string.device_cooling_temperature_value_format, value)
} else {
    stringResource(R.string.device_cooling_value_unavailable)
}

@Composable
private fun automaticFanPercentText(value: Double?): String = if (value?.isFinite() == true) {
    stringResource(R.string.device_cooling_percent_value_format, value.roundToInt().coerceIn(0, 100))
} else {
    stringResource(R.string.device_cooling_value_unavailable)
}

@Composable
private fun automaticRuntimeStatusText(fanPercent: Double?): String = when {
    fanPercent == null || !fanPercent.isFinite() ->
        stringResource(R.string.device_cooling_value_unavailable)
    fanPercent > 0.5 -> stringResource(R.string.device_cooling_automatic_status_cooling)
    else -> stringResource(R.string.device_cooling_automatic_status_waiting)
}

@Composable
private fun automaticAxisTemperatureText(value: Double): String =
    stringResource(R.string.device_cooling_temperature_axis_value_format, value)

private const val RANGE_VISUAL_MARGIN_C = 3.0
