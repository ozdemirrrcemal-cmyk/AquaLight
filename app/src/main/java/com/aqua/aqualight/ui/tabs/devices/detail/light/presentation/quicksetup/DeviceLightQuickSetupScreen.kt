package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.smartsetup.SmartSetupDecision
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.aquaLightManualColors

@Composable
internal fun DeviceLightQuickSetupScreen(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    modifier: Modifier = Modifier
) {
    val colors = aquaLightManualColors()
    val visuals = DeviceLightQuickSetupVisuals(
        colors = colors,
        typography = aquaDeviceCardTypography(colors.card)
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color))
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = DeviceLightQuickSetupGeometry.horizontalPadding,
                top = DeviceLightQuickSetupGeometry.topPadding,
                end = DeviceLightQuickSetupGeometry.horizontalPadding,
                bottom = DeviceLightQuickSetupGeometry.sectionGap
            ),
            verticalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.sectionGap)
        ) {
            val failure = state.loadFailure
            if (failure != null) {
                item(key = "load-failure") {
                    QuickSetupFailureCard(failure, actions, visuals)
                }
            } else if (state.snapshot == null) {
                item(key = "loading") { QuickSetupLoadingCard(visuals) }
            } else {
                item(key = "intro") { QuickSetupIntroCard(state, visuals) }
                when (val decision = state.decision) {
                    is SmartSetupDecision.MissingData -> item(key = "missing-data") {
                        QuickSetupMissingDataCard(decision, visuals)
                    }
                    is SmartSetupDecision.Unsupported -> item(key = "unsupported") {
                        QuickSetupUnsupportedCard(decision, visuals)
                    }
                    is SmartSetupDecision.Ready, null -> Unit
                }
                item(key = "profile") {
                    if (state.editMode) {
                        QuickSetupEditorCard(state, actions, visuals)
                    } else {
                        QuickSetupProfileSummaryCard(state, actions, visuals)
                    }
                }
                val ready = state.decision as? SmartSetupDecision.Ready
                if (ready != null && !state.draftDirty && !state.editMode) {
                    item(key = "plan") {
                        QuickSetupPlanCard(ready.recommendation, state.planInstalled, visuals)
                    }
                    item(key = "evidence") {
                        QuickSetupEvidenceCard(ready.recommendation, visuals)
                    }
                }
                item(key = "automation-info") { QuickSetupAutomationInfoCard(visuals) }
            }
        }
        QuickSetupBottomAction(state, actions, visuals)
    }
}

@Composable
private fun QuickSetupLoadingCard(visuals: DeviceLightQuickSetupVisuals) {
    AquaDeviceCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = DeviceLightQuickSetupGeometry.cardPadding
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.contentGap)
        ) {
            Box(
                modifier = Modifier
                    .size(DeviceLightQuickSetupGeometry.iconSize)
                    .clip(CircleShape)
                    .background(
                        visuals.colors.action.copy(alpha = DeviceLightQuickSetupAlpha.softSurface)
                    )
            )
            BasicText(
                text = stringResource(R.string.device_light_smart_setup_loading),
                style = visuals.typography.body,
                modifier = Modifier.weight(LOADING_TEXT_WEIGHT)
            )
        }
    }
}

@Composable
private fun QuickSetupBottomAction(
    state: DeviceLightQuickSetupUiState,
    actions: DeviceLightQuickSetupActions,
    visuals: DeviceLightQuickSetupVisuals
) {
    if (state.snapshot == null || state.loadFailure != null) return
    when {
        state.editMode -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = DeviceLightQuickSetupGeometry.horizontalPadding,
                    top = DeviceLightQuickSetupGeometry.actionTopPadding,
                    end = DeviceLightQuickSetupGeometry.horizontalPadding,
                    bottom = DeviceLightQuickSetupGeometry.bottomPadding
                ),
            horizontalArrangement = Arrangement.spacedBy(DeviceLightQuickSetupGeometry.smallGap)
        ) {
            QuickSetupActionButton(
                text = stringResource(R.string.cancel),
                description = stringResource(R.string.device_light_smart_setup_cancel_edit_description),
                enabled = !state.operationInProgress,
                filled = false,
                onClick = actions.onCancelEditClick,
                visuals = visuals,
                modifier = Modifier.weight(1f)
            )
            QuickSetupActionButton(
                text = if (state.operationInProgress) {
                    stringResource(R.string.device_light_smart_setup_saving)
                } else {
                    stringResource(R.string.device_light_smart_setup_save_profile)
                },
                description = stringResource(R.string.device_light_smart_setup_save_profile_description),
                enabled = state.canSave,
                filled = true,
                onClick = actions.onSaveProfileClick,
                visuals = visuals,
                modifier = Modifier.weight(1f)
            )
        }
        state.decision is SmartSetupDecision.Ready -> QuickSetupActionButton(
            text = when {
                state.operationInProgress -> stringResource(
                    R.string.device_light_smart_setup_applying
                )
                state.planInstalled -> stringResource(R.string.device_light_smart_setup_installed)
                else -> stringResource(R.string.device_light_smart_setup_apply)
            },
            description = stringResource(R.string.device_light_smart_setup_apply_description),
            enabled = state.canApply,
            filled = true,
            onClick = actions.onApplyClick,
            visuals = visuals,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = DeviceLightQuickSetupGeometry.horizontalPadding,
                    top = DeviceLightQuickSetupGeometry.actionTopPadding,
                    end = DeviceLightQuickSetupGeometry.horizontalPadding,
                    bottom = DeviceLightQuickSetupGeometry.bottomPadding
                )
        )
    }
}

@Composable
private fun QuickSetupActionButton(
    text: String,
    description: String,
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightQuickSetupVisuals,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else DeviceLightQuickSetupAlpha.disabled
    Box(
        modifier = modifier
            .height(DeviceLightQuickSetupGeometry.actionHeight)
            .clip(DeviceLightQuickSetupGeometry.actionShape)
            .background(
                if (filled) visuals.colors.action.copy(alpha = alpha) else Color.Transparent
            )
            .border(
                DeviceLightQuickSetupGeometry.actionBorderWidth,
                visuals.colors.action.copy(alpha = alpha),
                DeviceLightQuickSetupGeometry.actionShape
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = visuals.typography.title.copy(
                color = if (filled) {
                    visuals.colors.card.primaryText.copy(alpha = alpha)
                } else {
                    visuals.colors.action.copy(alpha = alpha)
                },
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(horizontal = DeviceLightQuickSetupGeometry.smallGap)
        )
    }
}

private const val LOADING_TEXT_WEIGHT = 1f
