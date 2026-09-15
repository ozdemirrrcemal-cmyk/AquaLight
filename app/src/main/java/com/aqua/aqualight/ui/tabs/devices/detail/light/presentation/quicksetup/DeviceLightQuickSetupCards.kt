@file:Suppress("LongMethod")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.smartsetup.SmartLightPhaseDraft
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupChannel
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupDecision
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupFactor
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupRecommendation
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface

@Composable
internal fun QuickSetupIntroCard(
    state: DeviceLightQuickSetupUiState,
    visuals: DeviceLightQuickSetupVisuals
) {
    val snapshot = state.snapshot ?: return
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightQuickSetupGeometry.cardPadding
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.contentGap)) {
            QuickSetupSectionTitle(
                iconRes = R.drawable.ic_light_quick_setup,
                title = stringResource(R.string.device_light_smart_setup_intro_title),
                subtitle = stringResource(
                    R.string.device_light_smart_setup_intro_summary,
                    snapshot.tankName
                ),
                visuals = visuals
            )
            QuickSetupStatusBadge(state, visuals)
            BasicText(
                text = stringResource(R.string.device_light_smart_setup_intro_safety),
                style = visuals.typography.caption
            )
        }
    }
}

@Composable
private fun QuickSetupStatusBadge(
    state: DeviceLightQuickSetupUiState,
    visuals: DeviceLightQuickSetupVisuals
) {
    val (text, tone) = when {
        state.planInstalled -> stringResource(
            R.string.device_light_smart_setup_status_installed
        ) to visuals.colors.card.success
        state.operationInProgress -> stringResource(
            R.string.device_light_smart_setup_status_applying
        ) to visuals.colors.action
        state.editMode -> stringResource(
            R.string.device_light_smart_setup_status_collecting
        ) to visuals.colors.card.warning
        state.decision is SmartSetupDecision.Ready -> stringResource(
            R.string.device_light_smart_setup_status_ready
        ) to visuals.colors.card.success
        state.decision is SmartSetupDecision.Unsupported -> stringResource(
            R.string.device_light_smart_setup_status_unsupported
        ) to visuals.colors.card.danger
        else -> stringResource(R.string.device_light_smart_setup_status_incomplete) to
            visuals.colors.card.warning
    }
    Box(
        modifier = Modifier
            .clip(DeviceLightQuickSetupGeometry.statusShape)
            .background(tone.copy(alpha = DeviceLightQuickSetupAlpha.softSurface))
            .padding(DeviceLightQuickSetupGeometry.statusPadding)
    ) {
        BasicText(text = text, style = visuals.typography.micro.copy(color = tone))
    }
}

@Composable
internal fun QuickSetupProfileSummaryCard(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    visuals: DeviceLightQuickSetupVisuals
) {
    val input = state.snapshot?.input ?: return
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightQuickSetupGeometry.cardPadding
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)) {
            QuickSetupSectionTitle(
                iconRes = R.drawable.ic_care_device_24,
                title = stringResource(R.string.device_light_smart_setup_profile_title),
                subtitle = stringResource(R.string.device_light_smart_setup_profile_summary),
                visuals = visuals
            )
            QuickSetupDivider(visuals)
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_aquarium_age),
                value = input.setupDay?.let { day ->
                    stringResource(R.string.device_light_smart_setup_day_value, day)
                } ?: stringResource(R.string.device_light_smart_setup_not_selected),
                visuals = visuals
            )
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_setup_date),
                value = input.setupDateEpochDay?.let { date -> quickSetupDateText(date) }
                    ?: stringResource(R.string.device_light_smart_setup_not_selected),
                visuals = visuals
            )
            input.lifecycleStage?.let { stage ->
                QuickSetupSummaryRow(
                    label = stringResource(R.string.device_light_smart_setup_lifecycle_stage),
                    value = stringResource(stage.labelRes()),
                    visuals = visuals
                )
            }
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_aquarium_type),
                value = stringResource(
                    if (input.isPlanted) R.string.device_light_smart_setup_planted
                    else R.string.device_light_smart_setup_unplanted
                ),
                visuals = visuals
            )
            input.plantDensity?.let { density ->
                QuickSetupSummaryRow(
                    label = stringResource(R.string.device_light_smart_setup_plant_density),
                    value = stringResource(density.labelRes()),
                    visuals = visuals
                )
            }
            input.highestPlantLightDemand?.let { demand ->
                QuickSetupSummaryRow(
                    label = stringResource(R.string.device_light_smart_setup_plant_demand),
                    value = stringResource(demand.labelRes()),
                    visuals = visuals
                )
            }
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_co2_status),
                value = input.co2Status?.let { value -> stringResource(value.labelRes()) }
                    ?: stringResource(R.string.device_light_smart_setup_not_selected),
                visuals = visuals
            )
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_active_soil),
                value = input.isActiveSoil?.let { active ->
                    stringResource(
                        if (active) R.string.device_light_smart_setup_yes
                        else R.string.device_light_smart_setup_no
                    )
                } ?: stringResource(R.string.device_light_smart_setup_not_selected),
                visuals = visuals
            )
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_installation),
                value = if (input.waterDepthCm != null && input.fixtureMountHeightCm != null) {
                    stringResource(
                        R.string.device_light_smart_setup_installation_value,
                        input.waterDepthCm,
                        input.fixtureMountHeightCm
                    )
                } else {
                    stringResource(R.string.device_light_smart_setup_not_selected)
                },
                visuals = visuals
            )
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_viewing_window),
                value = if (
                    input.preferredViewingStartMinuteOfDay != null &&
                    input.preferredViewingEndMinuteOfDay != null
                ) {
                    stringResource(
                        R.string.device_light_smart_setup_time_range,
                        quickSetupTimeText(input.preferredViewingStartMinuteOfDay),
                        quickSetupTimeText(input.preferredViewingEndMinuteOfDay)
                    )
                } else {
                    stringResource(R.string.device_light_smart_setup_not_selected)
                },
                visuals = visuals
            )
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_algae_observation),
                value = input.algaeObservation?.let { severity ->
                    stringResource(severity.labelRes())
                } ?: stringResource(R.string.device_light_smart_setup_not_selected),
                visuals = visuals
            )
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_stress_observation),
                value = input.plantStressObservation?.let { severity ->
                    stringResource(severity.labelRes())
                } ?: stringResource(R.string.device_light_smart_setup_not_selected),
                visuals = visuals
            )
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_observation_date),
                value = input.observationDateEpochDay?.let { date -> quickSetupDateText(date) }
                    ?: stringResource(R.string.device_light_smart_setup_not_confirmed),
                visuals = visuals
            )
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_device_product),
                value = input.deviceProductKey,
                visuals = visuals
            )
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_calibration_profile),
                value = input.calibrationProfile?.id
                    ?: stringResource(R.string.device_light_smart_setup_not_selected),
                visuals = visuals
            )
            QuickSetupOutlineButton(
                text = stringResource(R.string.device_light_smart_setup_edit_profile),
                enabled = !state.operationInProgress,
                onClick = actions.onEditClick,
                visuals = visuals,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DeviceLightQuickSetupGeometry.microGap)
            )
        }
    }
}

@Composable
internal fun QuickSetupMissingDataCard(
    decision: SmartSetupDecision.MissingData,
    visuals: DeviceLightQuickSetupVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightQuickSetupGeometry.compactCardPadding
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)) {
            QuickSetupSectionTitle(
                iconRes = R.drawable.ic_warning,
                title = stringResource(R.string.device_light_smart_setup_missing_title),
                subtitle = stringResource(R.string.device_light_smart_setup_missing_summary),
                visuals = visuals,
                tone = visuals.colors.card.warning
            )
            decision.fields.sortedBy { field -> field.ordinal }.forEach { field ->
                QuickSetupBullet(stringResource(field.labelRes()), visuals.colors.card.warning, visuals)
            }
        }
    }
}

@Composable
internal fun QuickSetupUnsupportedCard(
    decision: SmartSetupDecision.Unsupported,
    visuals: DeviceLightQuickSetupVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightQuickSetupGeometry.cardPadding
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)) {
            QuickSetupSectionTitle(
                iconRes = R.drawable.ic_warning,
                title = stringResource(R.string.device_light_smart_setup_unsupported_title),
                subtitle = stringResource(decision.reason.labelRes()),
                visuals = visuals,
                tone = visuals.colors.card.danger
            )
            BasicText(
                text = stringResource(R.string.device_light_smart_setup_unsupported_safety),
                style = visuals.typography.caption
            )
        }
    }
}

@Composable
internal fun QuickSetupPlanCard(
    recommendation: SmartSetupRecommendation,
    installed: Boolean,
    visuals: DeviceLightQuickSetupVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightQuickSetupGeometry.cardPadding
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.contentGap)) {
            QuickSetupSectionTitle(
                iconRes = if (installed) R.drawable.ic_check_24 else R.drawable.ic_care_light_24,
                title = stringResource(
                    if (installed) R.string.device_light_smart_setup_plan_installed_title
                    else R.string.device_light_smart_setup_plan_title
                ),
                subtitle = stringResource(
                    R.string.device_light_smart_setup_plan_summary,
                    recommendation.plan.phases.size
                ),
                visuals = visuals,
                tone = if (installed) visuals.colors.card.success else visuals.colors.action
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)
            ) {
                QuickSetupMetric(
                    label = stringResource(R.string.device_light_smart_setup_confidence),
                    value = stringResource(recommendation.confidence.labelRes()),
                    tone = when (recommendation.confidence) {
                        com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupConfidence.HIGH ->
                            visuals.colors.card.success
                        com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupConfidence.MODERATE ->
                            visuals.colors.card.warning
                    },
                    visuals = visuals,
                    modifier = Modifier.weight(1f)
                )
                QuickSetupMetric(
                    label = stringResource(R.string.device_light_smart_setup_initial_output),
                    value = stringResource(
                        R.string.device_light_smart_setup_percent,
                        recommendation.plan.initialStartPercent
                    ),
                    tone = visuals.colors.action,
                    visuals = visuals,
                    modifier = Modifier.weight(1f)
                )
                QuickSetupMetric(
                    label = stringResource(R.string.device_light_smart_setup_reevaluation),
                    value = quickSetupDateText(recommendation.reevaluationEpochDay),
                    tone = visuals.colors.card.primaryText,
                    visuals = visuals,
                    modifier = Modifier.weight(1f)
                )
            }
            recommendation.plan.phases.forEachIndexed { index, phase ->
                if (index > 0) QuickSetupDivider(visuals)
                QuickSetupPhase(index, phase, visuals)
            }
        }
    }
}

@Composable
private fun QuickSetupPhase(
    index: Int,
    phase: SmartLightPhaseDraft,
    visuals: DeviceLightQuickSetupVisuals
) {
    val startMinute = (phase.startTimeMs / MILLIS_PER_MINUTE).toInt()
    val endMinute = (phase.endTimeMs / MILLIS_PER_MINUTE).toInt()
    val dateRange = phase.validUntilEpochDayExclusive?.let { exclusiveEnd ->
        stringResource(
            R.string.device_light_smart_setup_date_range,
            quickSetupDateText(phase.validFromEpochDay),
            quickSetupDateText(exclusiveEnd - 1L)
        )
    } ?: stringResource(
        R.string.device_light_smart_setup_date_range_open,
        quickSetupDateText(phase.validFromEpochDay)
    )
    Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(DeviceLightQuickSetupGeometry.phaseMarkerSize)
                    .clip(DeviceLightQuickSetupGeometry.statusShape)
                    .background(visuals.colors.action.copy(alpha = DeviceLightQuickSetupAlpha.softSurface)),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = (index + 1).toString(),
                    style = visuals.typography.body.copy(
                        color = visuals.colors.action,
                        textAlign = TextAlign.Center
                    )
                )
            }
            Spacer(Modifier.width(DeviceLightQuickSetupGeometry.iconGap))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = stringResource(R.string.device_light_smart_setup_phase, index + 1),
                    style = visuals.typography.body
                )
                BasicText(text = dateRange, style = visuals.typography.micro)
            }
            BasicText(
                text = stringResource(
                    R.string.device_light_smart_setup_time_range,
                    quickSetupTimeText(startMinute),
                    quickSetupTimeText(endMinute)
                ),
                style = visuals.typography.body.copy(textAlign = TextAlign.End)
            )
        }
        BasicText(
            text = stringResource(
                R.string.device_light_smart_setup_phase_transition,
                phase.transitionDays,
                quickSetupDurationText(phase.rampDurationMs)
            ),
            style = visuals.typography.caption
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SmartSetupChannel.entries.forEach { channel ->
                val value = phase.scene.channels[channel] ?: return@forEach
                QuickSetupChannelValue(channel, value, visuals)
            }
        }
    }
}

@Composable
private fun QuickSetupChannelValue(
    channel: SmartSetupChannel,
    value: Int,
    visuals: DeviceLightQuickSetupVisuals
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(DeviceLightQuickSetupGeometry.channelDotSize)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(channel.color(visuals))
        )
        Spacer(Modifier.width(DeviceLightQuickSetupGeometry.microGap))
        BasicText(
            text = stringResource(
                R.string.device_light_smart_setup_channel_value,
                stringResource(channel.labelRes()),
                value
            ),
            style = visuals.typography.micro
        )
    }
}

@Composable
internal fun QuickSetupEvidenceCard(
    recommendation: SmartSetupRecommendation,
    visuals: DeviceLightQuickSetupVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightQuickSetupGeometry.cardPadding
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)) {
            QuickSetupSectionTitle(
                iconRes = R.drawable.ic_info,
                title = stringResource(R.string.device_light_smart_setup_evidence_title),
                subtitle = stringResource(R.string.device_light_smart_setup_evidence_summary),
                visuals = visuals
            )
            recommendation.factors.forEach { factor ->
                QuickSetupFactorRow(factor, visuals)
            }
            QuickSetupDivider(visuals)
            BasicText(
                text = stringResource(R.string.device_light_smart_setup_sources),
                style = visuals.typography.body
            )
            recommendation.sourceIds.sorted().forEach { sourceId ->
                QuickSetupBullet(quickSetupSourceText(sourceId), visuals.colors.action, visuals)
            }
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_calibration_profile),
                value = recommendation.calibrationProfileId,
                visuals = visuals
            )
            QuickSetupSummaryRow(
                label = stringResource(R.string.device_light_smart_setup_profile_fingerprint),
                value = recommendation.profileFingerprint,
                visuals = visuals
            )
        }
    }
}

@Composable
private fun QuickSetupFactorRow(
    factor: SmartSetupFactor,
    visuals: DeviceLightQuickSetupVisuals
) {
    val effect = stringResource(factor.effect.labelRes())
    QuickSetupSummaryRow(
        label = stringResource(factor.id.labelRes()),
        value = stringResource(R.string.device_light_smart_setup_factor_value, effect, factor.value),
        visuals = visuals
    )
}

@Composable
internal fun QuickSetupAutomationInfoCard(visuals: DeviceLightQuickSetupVisuals) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightQuickSetupGeometry.compactCardPadding
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)) {
            QuickSetupSectionTitle(
                iconRes = R.drawable.ic_info,
                title = stringResource(R.string.device_light_smart_setup_automation_title),
                subtitle = stringResource(R.string.device_light_smart_setup_automation_summary),
                visuals = visuals
            )
            BasicText(
                text = stringResource(R.string.device_light_smart_setup_safety_summary),
                style = visuals.typography.caption
            )
        }
    }
}

@Composable
internal fun QuickSetupFailureCard(
    failure: DeviceLightQuickSetupLoadFailure,
    actions: DeviceLightQuickSetupActions,
    visuals: DeviceLightQuickSetupVisuals
) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightQuickSetupGeometry.cardPadding
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.contentGap)) {
            QuickSetupSectionTitle(
                iconRes = R.drawable.ic_warning,
                title = stringResource(R.string.device_light_smart_setup_load_error_title),
                subtitle = stringResource(failure.labelRes()),
                visuals = visuals,
                tone = visuals.colors.card.danger
            )
            QuickSetupOutlineButton(
                text = stringResource(R.string.device_light_smart_setup_retry),
                enabled = true,
                onClick = actions.onRetryClick,
                visuals = visuals,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun QuickSetupMetric(
    label: String,
    value: String,
    tone: Color,
    visuals: DeviceLightQuickSetupVisuals,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(DeviceLightQuickSetupGeometry.statusShape)
            .background(tone.copy(alpha = DeviceLightQuickSetupAlpha.softSurface))
            .padding(DeviceLightQuickSetupGeometry.statusPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            text = value,
            style = visuals.typography.body.copy(color = tone, textAlign = TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = label,
            style = visuals.typography.micro.copy(textAlign = TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun QuickSetupSummaryRow(
    label: String,
    value: String,
    visuals: DeviceLightQuickSetupVisuals
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)
    ) {
        BasicText(
            text = label,
            style = visuals.typography.caption,
            modifier = Modifier.weight(SUMMARY_LABEL_WEIGHT)
        )
        BasicText(
            text = value,
            style = visuals.typography.body.copy(textAlign = TextAlign.End),
            modifier = Modifier.weight(SUMMARY_VALUE_WEIGHT)
        )
    }
}

@Composable
private fun QuickSetupBullet(
    text: String,
    tone: Color,
    visuals: DeviceLightQuickSetupVisuals
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = DeviceLightQuickSetupGeometry.smallGap)
                .size(DeviceLightQuickSetupGeometry.channelDotSize)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(tone)
        )
        Spacer(Modifier.width(DeviceLightQuickSetupGeometry.smallGap))
        BasicText(text = text, style = visuals.typography.caption, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun QuickSetupDivider(visuals: DeviceLightQuickSetupVisuals) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightQuickSetupGeometry.dividerHeight)
            .background(
                visuals.colors.card.outline.copy(alpha = DeviceLightQuickSetupAlpha.divider)
            )
    )
}

@Composable
private fun quickSetupSourceText(sourceId: String): String {
    val label = when (sourceId) {
        "aql.smart-light.policy.2026-09" ->
            stringResource(R.string.device_light_smart_setup_source_policy)
        "aql.wrgb-pro-elite.design-model-r2" ->
            stringResource(R.string.device_light_smart_setup_source_calibration)
        "tropica_growing_in" ->
            stringResource(R.string.device_light_smart_setup_source_growing_in)
        "tropica_quick_guide" ->
            stringResource(R.string.device_light_smart_setup_source_quick_guide)
        else -> sourceId
    }
    return stringResource(R.string.device_light_smart_setup_source_value, label, sourceId)
}

private fun SmartSetupChannel.color(visuals: DeviceLightQuickSetupVisuals): Color = when (this) {
    SmartSetupChannel.RED -> visuals.colors.red
    SmartSetupChannel.GREEN -> visuals.colors.green
    SmartSetupChannel.BLUE -> visuals.colors.blue
    SmartSetupChannel.WHITE -> visuals.colors.white
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val SUMMARY_LABEL_WEIGHT = 0.42f
private const val SUMMARY_VALUE_WEIGHT = 0.58f
