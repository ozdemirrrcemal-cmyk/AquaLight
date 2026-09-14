@file:Suppress("LongMethod", "MagicNumber", "TooManyFunctions")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSlider
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSliderActions
import com.aqua.aqualight.ui.common.light.AquaLightManualPercentSliderState
import com.aqua.aqualight.ui.common.light.AquaLightManualColors
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
        contentPadding = PaddingValues(start = 9.dp, top = 2.dp, end = 9.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (state.hasUnsavedChanges) {
            item(key = "unsaved") { UnsavedChangesIndicator(visuals) }
        }
        item(key = "days") { ProgramDaysCard(state, actions, visuals) }
        item(key = "curve") { CurveCard(state, actions, visuals) }
        item(key = "point") { SelectedPointCard(state, actions, visuals) }
        item(key = "preview") { VirtualTimePreviewCard(state, actions, visuals) }
        item(key = "library") { LibraryActions(state, actions, visuals) }
        item(key = "reset") {
            CustomOutlinedButton(
                label = stringResource(R.string.device_light_custom_reset),
                description = stringResource(R.string.device_light_custom_reset_description),
                enabled = !state.operationInProgress,
                color = visuals.colors.card.primaryText,
                iconRes = R.drawable.ic_dosing_reset_24,
                onClick = actions.onResetClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item(key = "info") { CustomInformation(visuals) }
    }
}

@Composable
private fun UnsavedChangesIndicator(visuals: DeviceLightCustomVisuals) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 45.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp).background(
                visuals.colors.card.warning,
                RoundedCornerShape(percent = 50)
            )
        )
        BasicText(
            text = stringResource(R.string.device_light_custom_unsaved_changes),
            style = visuals.typography.caption.copy(color = visuals.colors.card.secondaryText),
            modifier = Modifier.padding(start = 8.dp)
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading(
                title = stringResource(R.string.device_light_custom_program_days),
                subtitle = stringResource(R.string.device_light_custom_program_days_summary),
                visuals = visuals
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                DayButton(
                    label = stringResource(R.string.device_light_custom_every_day),
                    selected = state.draft.weekdaysMask == EVERY_DAY_MASK,
                    enabled = state.contentEnabled,
                    onClick = actions.onEveryDayClick,
                    modifier = Modifier.weight(1.8f),
                    visuals = visuals
                )
                weekdayLabels().forEachIndexed { index, label ->
                    DayButton(
                        label = label,
                        selected = state.draft.weekdaysMask and (1 shl index) != 0,
                        enabled = state.contentEnabled,
                        onClick = { actions.onWeekdayClick(index) },
                        modifier = Modifier.weight(1f),
                        visuals = visuals
                    )
                }
            }
        }
    }
}

@Composable
private fun DayButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    visuals: DeviceLightCustomVisuals
) {
    val alpha = if (enabled) 1f else 0.38f
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = modifier.height(40.dp).clip(shape)
            .background(if (selected) visuals.colors.action.copy(alpha = alpha) else Color.Transparent)
            .border(1.dp, visuals.colors.card.mediaOutline.copy(alpha = alpha), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            maxLines = 1,
            style = visuals.typography.caption.copy(
                color = visuals.colors.card.primaryText.copy(alpha = alpha),
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun CurveCard(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) {
                    SectionHeading(
                        title = stringResource(R.string.device_light_custom_curve_heading),
                        subtitle = stringResource(R.string.device_light_custom_curve_helper),
                        visuals = visuals
                    )
                }
                CompactActionButton(
                    label = stringResource(R.string.device_light_custom_add_point),
                    enabled = state.contentEnabled && !state.operationInProgress,
                    onClick = actions.onAddPointClick,
                    visuals = visuals
                )
            }
            EditableCurveChart(state, actions.onGraphTimeClick, visuals)
            CurveLegend(state.channels, visuals)
        }
    }
}

@Composable
private fun EditableCurveChart(
    state: DeviceLightCustomCurveUiState,
    onTimeClick: (Long) -> Unit,
    visuals: DeviceLightCustomVisuals
) {
    val chartDescription = pluralStringResource(
        R.plurals.device_light_library_chart_description,
        state.draft.points.size,
        state.draft.points.size
    )
    Column {
        state.selectedTimeMs?.let { selected ->
            BasicText(
                text = formatTime(selected),
                style = visuals.typography.micro.copy(color = visuals.colors.card.primaryText),
                modifier = Modifier.fillMaxWidth().padding(start = 46.dp),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.width(34.dp).height(134.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(100, 75, 50, 25, 0).forEach { value ->
                    BasicText(
                        text = stringResource(R.string.device_light_library_channel_percent_format, value),
                        style = visuals.typography.micro
                    )
                }
            }
            Canvas(
                modifier = Modifier.weight(1f).height(134.dp)
                    .pointerInput(state.contentEnabled, state.operationInProgress, state.draft.points) {
                        if (state.contentEnabled && !state.operationInProgress) {
                            detectTapGestures { offset ->
                                val time = (offset.x / size.width.toFloat()).coerceIn(0f, 1f) *
                                    (MILLIS_PER_DAY - MILLIS_PER_MINUTE)
                                onTimeClick(time.toLong())
                            }
                        }
                    }
                    .clearAndSetSemantics {
                        contentDescription = chartDescription
                    }
            ) {
                drawCurveGrid(visuals.colors.card)
                state.selectedTimeMs?.let { time -> drawSelectedGuide(time, visuals) }
                state.channels.forEach { channel ->
                    drawChannelCurve(state.draft.points, channel, visuals)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 34.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            (0..HOURS_PER_DAY step CHART_HOUR_STEP).forEach { hour ->
                BasicText(text = hour.toString().padStart(2, '0'), style = visuals.typography.micro)
            }
        }
    }
}

private fun DrawScope.drawCurveGrid(colors: AquaDeviceCardColors) {
    repeat(CHART_PERCENT_DIVISIONS + 1) { index ->
        val fraction = index / CHART_PERCENT_DIVISIONS.toFloat()
        drawLine(
            color = colors.outline.copy(alpha = 0.45f),
            start = Offset(0f, size.height * fraction),
            end = Offset(size.width, size.height * fraction),
            strokeWidth = 1.dp.toPx()
        )
    }
    repeat(HOURS_PER_DAY / CHART_HOUR_STEP + 1) { index ->
        val fraction = index * CHART_HOUR_STEP / HOURS_PER_DAY.toFloat()
        drawLine(
            color = colors.outline.copy(alpha = 0.32f),
            start = Offset(size.width * fraction, 0f),
            end = Offset(size.width * fraction, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private fun DrawScope.drawSelectedGuide(timeMs: Long, visuals: DeviceLightCustomVisuals) {
    val x = size.width * timeMs / (MILLIS_PER_DAY - MILLIS_PER_MINUTE).toFloat()
    drawLine(
        color = visuals.colors.card.primaryText.copy(alpha = 0.8f),
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 1.2.dp.toPx(),
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
            floatArrayOf(5.dp.toPx(), 4.dp.toPx())
        )
    )
}

private fun DrawScope.drawChannelCurve(
    points: List<DeviceLightCustomPointUiState>,
    channel: DeviceLightCustomChannelId,
    visuals: DeviceLightCustomVisuals
) {
    if (points.isEmpty()) return
    val color = visuals.channelColor(channel)
    val path = Path()
    points.forEachIndexed { index, point ->
        val x = size.width * point.timeMs / (MILLIS_PER_DAY - MILLIS_PER_MINUTE).toFloat()
        val y = size.height * (1f - (point.channels[channel] ?: 0) / 100f)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
    points.forEach { point ->
        val x = size.width * point.timeMs / (MILLIS_PER_DAY - MILLIS_PER_MINUTE).toFloat()
        val y = size.height * (1f - (point.channels[channel] ?: 0) / 100f)
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(x, y))
    }
}

@Composable
private fun CurveLegend(
    channels: List<DeviceLightCustomChannelId>,
    visuals: DeviceLightCustomVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        channels.forEach { channel ->
            Row(
                modifier = Modifier.padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(9.dp).background(
                        visuals.channelColor(channel),
                        RoundedCornerShape(percent = 50)
                    )
                )
                BasicText(
                    text = channel.name.first().toString(),
                    style = visuals.typography.micro,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectedPointCard(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText(
                text = stringResource(R.string.device_light_custom_selected_point),
                style = visuals.typography.title.copy(color = visuals.colors.card.primaryText)
            )
            val point = state.selectedPoint
            if (point == null) {
                BasicText(
                    text = stringResource(R.string.device_light_custom_no_point_selected),
                    style = visuals.typography.caption,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    PointTimeButton(
                        point = point,
                        enabled = state.contentEnabled,
                        onClick = actions.onEditTimeClick,
                        visuals = visuals,
                        modifier = Modifier.weight(1f)
                    )
                    SquareIconButton(
                        iconRes = R.drawable.ic_add_24,
                        description = stringResource(R.string.device_light_custom_duplicate_point),
                        enabled = state.contentEnabled && !state.operationInProgress,
                        color = visuals.colors.action,
                        onClick = actions.onDuplicatePointClick
                    )
                    SquareIconButton(
                        iconRes = R.drawable.ic_delete_24,
                        description = stringResource(R.string.device_light_custom_delete_point),
                        enabled = !state.operationInProgress,
                        color = visuals.colors.card.danger,
                        onClick = actions.onDeletePointClick
                    )
                }
                point.channels.forEach { (channel, percent) ->
                    CustomChannelRow(channel, percent, state, actions, visuals)
                }
            }
        }
    }
}

@Composable
private fun PointTimeButton(
    point: DeviceLightCustomPointUiState,
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightCustomVisuals,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier.height(44.dp).clip(shape)
            .border(1.dp, visuals.colors.card.mediaOutline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClockGlyph(visuals.colors.card.secondaryText)
        BasicText(
            text = formatTime(point.timeMs),
            style = visuals.typography.title.copy(color = visuals.colors.card.primaryText),
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun CustomChannelRow(
    channel: DeviceLightCustomChannelId,
    percent: Int,
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    val label = stringResource(channel.labelRes)
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = visuals.typography.body,
            modifier = Modifier.width(43.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        StepButton(
            stringResource(R.string.device_light_manual_minus_symbol),
            !state.operationInProgress && percent > 0,
            { actions.onChannelStep(channel, -1) },
            visuals
        )
        AquaLightManualPercentSlider(
            state = AquaLightManualPercentSliderState(
                percent = percent,
                enabled = state.contentEnabled && !state.operationInProgress,
                channelColor = visuals.channelColor(channel),
                stateText = "$percent%",
                accessibilityDescription = label
            ),
            actions = AquaLightManualPercentSliderActions(
                onValueChanged = { value -> actions.onChannelChanged(channel, value) },
                onValueChangeFinished = {}
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 1.dp)
        )
        StepButton(
            stringResource(R.string.device_light_manual_plus_symbol),
            !state.operationInProgress && percent < 100,
            { actions.onChannelStep(channel, 1) },
            visuals
        )
        BasicText(
            text = stringResource(R.string.device_light_library_channel_percent_format, percent),
            style = visuals.typography.body.copy(textAlign = TextAlign.End),
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
private fun StepButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightCustomVisuals
) {
    val alpha = if (enabled) 1f else 0.38f
    Box(
        modifier = Modifier.size(36.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(29.dp).border(
                1.dp,
                visuals.colors.card.mediaOutline.copy(alpha = alpha),
                RoundedCornerShape(percent = 50)
            ),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                symbol,
                style = visuals.typography.title.copy(
                    color = visuals.colors.card.primaryText.copy(alpha = alpha)
                )
            )
        }
    }
}

@Composable
private fun VirtualTimePreviewCard(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading(
                title = stringResource(R.string.device_light_custom_virtual_preview),
                subtitle = stringResource(R.string.device_light_custom_virtual_preview_summary),
                visuals = visuals
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        text = formatTime(state.previewTimeMs),
                        style = visuals.typography.caption.copy(
                            color = visuals.colors.card.primaryText,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    AquaLightManualPercentSlider(
                        state = AquaLightManualPercentSliderState(
                            percent = ((state.previewTimeMs * 100) /
                                (MILLIS_PER_DAY - MILLIS_PER_MINUTE)).toInt(),
                            enabled = state.contentEnabled && !state.operationInProgress,
                            channelColor = visuals.colors.action,
                            stateText = formatTime(state.previewTimeMs),
                            accessibilityDescription = stringResource(
                                R.string.device_light_custom_virtual_time_description
                            )
                        ),
                        actions = AquaLightManualPercentSliderActions(
                            onValueChanged = { percent ->
                                actions.onPreviewTimeChanged(
                                    (MILLIS_PER_DAY - MILLIS_PER_MINUTE) * percent / 100
                                )
                            },
                            onValueChangeFinished = {}
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("00", "06", "12", "18", "24").forEach { label ->
                            BasicText(text = label, style = visuals.typography.micro)
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                CustomOutlinedButton(
                    label = stringResource(R.string.device_light_custom_preview),
                    description = stringResource(R.string.device_light_custom_preview_description),
                    enabled = state.contentEnabled && !state.operationInProgress,
                    color = visuals.colors.action,
                    iconRes = null,
                    onClick = actions.onPreviewClick,
                    modifier = Modifier.width(84.dp),
                    buttonHeight = 36.dp,
                    showPlayIcon = true
                )
            }
        }
    }
}

@Composable
private fun LibraryActions(
    state: DeviceLightCustomCurveUiState,
    actions: DeviceLightCustomCurveActions,
    visuals: DeviceLightCustomVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CustomOutlinedButton(
            label = stringResource(R.string.device_light_custom_load),
            description = stringResource(R.string.device_light_custom_load_description),
            enabled = state.contentEnabled && !state.operationInProgress,
            color = visuals.colors.action,
            iconRes = R.drawable.ic_light_library,
            onClick = actions.onLoadClick,
            modifier = Modifier.weight(1f)
        )
        CustomOutlinedButton(
            label = stringResource(R.string.device_light_custom_save_as),
            description = stringResource(R.string.device_light_custom_save_as_description),
            enabled = state.canSaveAs,
            color = visuals.colors.action,
            iconRes = R.drawable.ic_add_24,
            onClick = actions.onSaveAsClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CustomOutlinedButton(
    label: String,
    description: String,
    enabled: Boolean,
    color: Color,
    iconRes: Int?,
    onClick: () -> Unit,
    modifier: Modifier,
    buttonHeight: Dp = 53.dp,
    showPlayIcon: Boolean = false
) {
    val alpha = if (enabled) 1f else 0.38f
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = modifier.height(buttonHeight).clip(shape)
            .border(1.dp, color.copy(alpha = alpha), shape)
            .clearAndSetSemantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showPlayIcon) {
            PlayGlyph(color.copy(alpha = alpha))
            Spacer(Modifier.width(5.dp))
        }
        iconRes?.let {
            androidx.compose.foundation.Image(
                painter = painterResource(it),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color.copy(alpha = alpha)),
                modifier = Modifier.size(23.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        BasicText(
            text = label,
            style = androidx.compose.ui.text.TextStyle(color = color.copy(alpha = alpha)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayGlyph(color: Color) {
    Canvas(Modifier.size(15.dp)) {
        val play = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.16f)
            lineTo(size.width * 0.82f, size.height * 0.5f)
            lineTo(size.width * 0.28f, size.height * 0.84f)
            close()
        }
        drawPath(play, color)
    }
}

@Composable
private fun CompactActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightCustomVisuals
) {
    val alpha = if (enabled) 1f else 0.38f
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier.height(31.dp).clip(shape)
            .border(1.dp, visuals.colors.action.copy(alpha = alpha), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_add_24),
            contentDescription = null,
            colorFilter = ColorFilter.tint(visuals.colors.action.copy(alpha = alpha)),
            modifier = Modifier.size(16.dp)
        )
        BasicText(
            label,
            style = visuals.typography.caption.copy(color = visuals.colors.action.copy(alpha = alpha)),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun SquareIconButton(
    iconRes: Int,
    description: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier.size(44.dp).clip(shape)
            .border(1.dp, color.copy(alpha = alpha), shape)
            .clearAndSetSemantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color.copy(alpha = alpha)),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ClockGlyph(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        drawCircle(color, style = Stroke(width = 2.dp.toPx()))
        drawLine(color, center, Offset(center.x, center.y - 6.dp.toPx()), 2.dp.toPx())
        drawLine(color, center, Offset(center.x + 5.dp.toPx(), center.y), 2.dp.toPx())
    }
}

@Composable
private fun CustomInformation(visuals: DeviceLightCustomVisuals) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(visuals.colors.card.secondaryText),
            modifier = Modifier.size(18.dp)
        )
        BasicText(
            text = stringResource(R.string.device_light_custom_information),
            style = visuals.typography.micro,
            modifier = Modifier.padding(start = 7.dp)
        )
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String, visuals: DeviceLightCustomVisuals) {
    Column {
        BasicText(
            text = title,
            style = visuals.typography.title.copy(color = visuals.colors.card.primaryText)
        )
        BasicText(
            text = subtitle,
            style = visuals.typography.caption.copy(color = visuals.colors.card.secondaryText)
        )
    }
}

@Composable
private fun weekdayLabels(): List<String> = listOf(
    stringResource(R.string.device_light_library_day_monday),
    stringResource(R.string.device_light_library_day_tuesday),
    stringResource(R.string.device_light_library_day_wednesday),
    stringResource(R.string.device_light_library_day_thursday),
    stringResource(R.string.device_light_library_day_friday),
    stringResource(R.string.device_light_library_day_saturday),
    stringResource(R.string.device_light_library_day_sunday)
)

private fun formatTime(timeMs: Long): String {
    val totalMinutes = timeMs / MILLIS_PER_MINUTE
    val hour = totalMinutes / 60
    val minute = totalMinutes % 60
    return hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
}

private fun DeviceLightCustomVisuals.channelColor(channel: DeviceLightCustomChannelId): Color =
    when (channel) {
        DeviceLightCustomChannelId.RED -> colors.red
        DeviceLightCustomChannelId.GREEN -> colors.green
        DeviceLightCustomChannelId.BLUE -> colors.blue
        DeviceLightCustomChannelId.WHITE -> colors.white
    }

@Immutable
private data class DeviceLightCustomVisuals(
    val colors: AquaLightManualColors,
    val typography: AquaDeviceCardTypography
)

private const val HOURS_PER_DAY = 24
private const val CHART_HOUR_STEP = 2
private const val CHART_PERCENT_DIVISIONS = 4
