package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationSnapshot
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.light.AquaLightDashboardIcon
import com.aqua.aqualight.ui.common.light.AquaLightDashboardIconKind
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlin.math.ceil

@Composable
internal fun AdaptationIntroCard(
    state: DeviceLightAdaptationUiState,
    visuals: DeviceLightAdaptationVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightAdaptationGeometry.cardPadding
    ) {
        Row(verticalAlignment = Alignment.Top) {
            AquaLightDashboardIcon(
                kind = AquaLightDashboardIconKind.ADAPTATION,
                tint = visuals.colors.action,
                modifier = Modifier.size(DeviceLightAdaptationGeometry.iconSize)
            )
            Spacer(Modifier.width(DeviceLightAdaptationGeometry.iconGap))
            Column(
                modifier = Modifier.weight(CONTENT_WEIGHT),
                verticalArrangement = Arrangement.spacedBy(DeviceLightAdaptationGeometry.titleGap)
            ) {
                BasicText(
                    text = stringResource(state.introTitleRes()),
                    style = visuals.typography.title
                )
                BasicText(
                    text = stringResource(R.string.device_light_adaptation_intro_body),
                    style = visuals.typography.caption
                )
            }
            Spacer(Modifier.width(DeviceLightAdaptationGeometry.iconGap))
            AdaptationStatusChip(state, visuals)
        }
    }
}

@Composable
private fun AdaptationStatusChip(
    state: DeviceLightAdaptationUiState,
    visuals: DeviceLightAdaptationVisuals
) {
    val status = state.toStatusChip(visuals)
    Box(
        modifier = Modifier
            .clip(DeviceLightAdaptationGeometry.statusShape)
            .background(status.color.copy(alpha = DeviceLightAdaptationAlpha.softSurface))
            .padding(DeviceLightAdaptationGeometry.statusPadding),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = stringResource(status.labelRes),
            style = visuals.typography.micro.copy(color = status.color)
        )
    }
}

@Composable
internal fun AdaptationInformationCard(
    state: DeviceLightAdaptationUiState,
    visuals: DeviceLightAdaptationVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightAdaptationGeometry.compactCardPadding
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                colorFilter = ColorFilter.tint(visuals.colors.card.secondaryText),
                modifier = Modifier.size(DeviceLightAdaptationGeometry.infoIconSize)
            )
            Spacer(Modifier.width(DeviceLightAdaptationGeometry.iconGap))
            BasicText(
                text = stringResource(state.informationRes()),
                style = visuals.typography.caption,
                modifier = Modifier.weight(CONTENT_WEIGHT)
            )
        }
    }
}

@Composable
internal fun AdaptationActiveProgressCard(
    state: DeviceLightAdaptationUiState,
    visuals: DeviceLightAdaptationVisuals
) {
    val snapshot = state.snapshot ?: return
    val progress = snapshot.progressFraction()
    val elapsedDays = snapshot.elapsedDays()
    val remainingDays = snapshot.remainingDays()
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightAdaptationGeometry.cardPadding
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DeviceLightAdaptationGeometry.titleGap)
        ) {
            AdaptationCurrentLevel(snapshot, visuals)
            AdaptationProgressDetails(snapshot, progress, visuals)
            AdaptationDayDetails(snapshot, elapsedDays, remainingDays, visuals)
        }
    }
}

@Composable
private fun AdaptationCurrentLevel(
    snapshot: DeviceLightAdaptationSnapshot,
    visuals: DeviceLightAdaptationVisuals
) {
    BasicText(
        text = stringResource(
            R.string.device_light_adaptation_decimal_percent,
            snapshot.currentPercentValue()
        ),
        style = visuals.typography.title.copy(
            color = visuals.colors.card.primaryText,
            fontSize = DeviceLightAdaptationGeometry.largePercentSize,
            lineHeight = DeviceLightAdaptationGeometry.largePercentLineHeight,
            textAlign = TextAlign.Center
        )
    )
    BasicText(
        text = stringResource(R.string.device_light_adaptation_current_level),
        style = visuals.typography.caption
    )
}

@Composable
private fun AdaptationProgressDetails(
    snapshot: DeviceLightAdaptationSnapshot,
    progress: Float,
    visuals: DeviceLightAdaptationVisuals
) {
    AdaptationProgressRail(progress, visuals)
    Row(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = stringResource(
                R.string.device_light_adaptation_progress_start,
                snapshot.startPercent
            ),
            style = visuals.typography.micro,
            modifier = Modifier.weight(CONTENT_WEIGHT)
        )
        BasicText(
            text = stringResource(
                R.string.device_light_adaptation_progress_target,
                snapshot.targetPercent
            ),
            style = visuals.typography.micro.copy(textAlign = TextAlign.End),
            modifier = Modifier.weight(CONTENT_WEIGHT)
        )
    }
}

@Composable
private fun AdaptationDayDetails(
    snapshot: DeviceLightAdaptationSnapshot,
    elapsedDays: Int,
    remainingDays: Int,
    visuals: DeviceLightAdaptationVisuals
) {
    BasicText(
        text = stringResource(
            R.string.device_light_adaptation_day_progress,
            elapsedDays,
            pluralStringResource(
                R.plurals.device_light_adaptation_days_value,
                snapshot.durationDays,
                snapshot.durationDays
            )
        ),
        style = visuals.typography.body
    )
    BasicText(
        text = pluralStringResource(
            R.plurals.device_light_adaptation_days_left_compact,
            remainingDays,
            remainingDays
        ),
        style = visuals.typography.caption
    )
}

@Composable
private fun AdaptationProgressRail(
    progress: Float,
    visuals: DeviceLightAdaptationVisuals
) {
    Box(
        modifier = Modifier
            .padding(top = DeviceLightAdaptationGeometry.progressLabelTopGap)
            .fillMaxWidth()
            .height(DeviceLightAdaptationGeometry.progressHeight)
            .clip(DeviceLightAdaptationGeometry.progressShape)
            .background(
                visuals.colors.card.mediaOutline.copy(alpha = DeviceLightAdaptationAlpha.rail)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(DeviceLightAdaptationGeometry.progressHeight)
                .background(visuals.colors.action)
        )
    }
}

@Composable
internal fun AdaptationCompletedCard(
    state: DeviceLightAdaptationUiState,
    visuals: DeviceLightAdaptationVisuals
) {
    val snapshot = state.snapshot ?: return
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightAdaptationGeometry.cardPadding
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DeviceLightAdaptationGeometry.titleGap)
        ) {
            BasicText(
                text = stringResource(
                    R.string.device_light_adaptation_percent,
                    snapshot.targetPercent
                ),
                style = visuals.typography.title.copy(
                    color = visuals.colors.card.success,
                    fontSize = DeviceLightAdaptationGeometry.largePercentSize,
                    lineHeight = DeviceLightAdaptationGeometry.largePercentLineHeight
                )
            )
            BasicText(
                text = stringResource(R.string.device_light_adaptation_completed_body),
                style = visuals.typography.caption.copy(textAlign = TextAlign.Center)
            )
        }
    }
}

private fun DeviceLightAdaptationUiState.introTitleRes(): Int = when (screenState) {
    DeviceLightAdaptationScreenState.SETUP -> R.string.device_light_adaptation_intro_title
    DeviceLightAdaptationScreenState.ACTIVE -> R.string.device_light_adaptation_active_title
    DeviceLightAdaptationScreenState.COMPLETED -> R.string.device_light_adaptation_completed_title
}

private fun DeviceLightAdaptationUiState.informationRes(): Int = when {
    snapshot?.clockReady == false -> R.string.device_light_adaptation_clock_info
    screenState == DeviceLightAdaptationScreenState.ACTIVE ->
        R.string.device_light_adaptation_active_manual_info
    else -> R.string.device_light_adaptation_manual_info
}

private fun DeviceLightAdaptationUiState.toStatusChip(
    visuals: DeviceLightAdaptationVisuals
): AdaptationStatusChip = when (screenState) {
    DeviceLightAdaptationScreenState.SETUP -> AdaptationStatusChip(
        R.string.device_light_adaptation_off_uppercase,
        visuals.colors.card.secondaryText
    )
    DeviceLightAdaptationScreenState.ACTIVE -> AdaptationStatusChip(
        R.string.device_light_active_uppercase,
        visuals.colors.action
    )
    DeviceLightAdaptationScreenState.COMPLETED -> AdaptationStatusChip(
        R.string.device_light_adaptation_completed_uppercase,
        visuals.colors.card.success
    )
}

private fun DeviceLightAdaptationSnapshot.currentPercentValue(): String {
    val permille = currentPermille ?: return "—"
    val value = permille.toDouble() / DeviceLightAdaptationSpec.percentScale
    return NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = if (permille % DeviceLightAdaptationSpec.percentScale == 0) 0 else 1
        maximumFractionDigits = 1
    }.format(value)
}

private fun DeviceLightAdaptationSnapshot.progressFraction(): Float {
    val start = startPercent * DeviceLightAdaptationSpec.percentScale
    val target = targetPercent * DeviceLightAdaptationSpec.percentScale
    val current = currentPermille ?: start
    return (current - start).toFloat() / (target - start).coerceAtLeast(1)
}

internal fun DeviceLightAdaptationSnapshot.remainingDays(): Int = remainingSeconds?.let { seconds ->
    ceil(seconds.toDouble() / DeviceLightAdaptationSpec.secondsPerDay).toInt()
} ?: 0

internal fun DeviceLightAdaptationSnapshot.elapsedDays(): Int =
    (durationDays - remainingDays()).coerceIn(0, durationDays)

internal fun DeviceLightAdaptationSnapshot.endDateText(): String = endsAtEpochSeconds?.let { epoch ->
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epoch * MILLIS_PER_SECOND))
} ?: "—"

private data class AdaptationStatusChip(val labelRes: Int, val color: Color)

private const val CONTENT_WEIGHT = 1f
private const val MILLIS_PER_SECOND = 1_000L
