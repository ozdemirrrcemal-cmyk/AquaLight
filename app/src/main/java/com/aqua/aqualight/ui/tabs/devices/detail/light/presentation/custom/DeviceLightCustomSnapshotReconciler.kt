package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomSnapshot

internal data class DeviceLightCustomSnapshotDraftContext(
    val current: DeviceLightCustomCurveUiState,
    val restoredDraft: DeviceLightCustomDraft?,
    val previousCheckpoint: DeviceLightCustomDraft?,
    val restoreDirty: Boolean,
    val restoreUnapplied: Boolean,
    val forceDeviceDraft: Boolean
) {
    val restoreRequested: Boolean
        get() = restoreDirty || restoreUnapplied
}

internal data class DeviceLightCustomDraftResolution(
    val draft: DeviceLightCustomDraft,
    val checkpoint: DeviceLightCustomDraft
)

internal data class DeviceLightCustomSnapshotFocus(
    val selectedTimeMs: Long?,
    val playheadMode: DeviceLightCustomPlayheadMode,
    val playheadTimeMs: Long
)

internal data class DeviceLightCustomSnapshotPresentation(
    val current: DeviceLightCustomCurveUiState,
    val channels: List<DeviceLightCustomChannelId>,
    val draftResolution: DeviceLightCustomDraftResolution,
    val focus: DeviceLightCustomSnapshotFocus,
    val deviceTimeMs: Long?
)

internal fun resolveSnapshotDraft(
    context: DeviceLightCustomSnapshotDraftContext,
    channels: List<DeviceLightCustomChannelId>,
    maxPoints: Int,
    firmwareDraft: DeviceLightCustomDraft
): DeviceLightCustomDraftResolution {
    val restored = context.restoredDraft
        ?.takeUnless { context.forceDeviceDraft }
        ?.takeIf { context.restoreRequested }
        ?.takeIf { draft -> draft.compatibleWith(channels, maxPoints) }
    val currentDraft = context.current.draft
        .takeUnless { context.forceDeviceDraft }
        ?.takeIf {
            context.current.hasUnsavedChanges || context.current.hasUnappliedChanges
        }
        ?.takeIf { draft -> draft.compatibleWith(channels, maxPoints) }
    val draft = restored ?: currentDraft ?: firmwareDraft
    val checkpoint = when {
        context.forceDeviceDraft -> firmwareDraft
        restored != null && !context.restoreDirty -> restored
        restored != null -> context.previousCheckpoint ?: firmwareDraft
        currentDraft != null -> context.previousCheckpoint ?: firmwareDraft
        else -> firmwareDraft
    }
    return DeviceLightCustomDraftResolution(draft = draft, checkpoint = checkpoint)
}

internal fun resolveSnapshotFocus(
    resetEditorFocus: Boolean,
    draft: DeviceLightCustomDraft,
    current: DeviceLightCustomCurveUiState,
    deviceTimeMs: Long?
): DeviceLightCustomSnapshotFocus {
    if (!resetEditorFocus) {
        return DeviceLightCustomSnapshotFocus(
            selectedTimeMs = draft.resolveSelection(current, deviceTimeMs),
            playheadMode = current.playheadMode,
            playheadTimeMs = current.resolvePlayheadTime(deviceTimeMs)
        )
    }
    val referenceTimeMs = deviceTimeMs ?: current.previewTimeMs
    return DeviceLightCustomSnapshotFocus(
        selectedTimeMs = draft.points.minByOrNull { point ->
            kotlin.math.abs(point.timeMs - referenceTimeMs)
        }?.timeMs,
        playheadMode = DeviceLightCustomPlayheadMode.CLOCK,
        playheadTimeMs = referenceTimeMs
    )
}

internal fun DeviceLightCustomSnapshot.toEditorUiState(
    presentation: DeviceLightCustomSnapshotPresentation
): DeviceLightCustomCurveUiState = DeviceLightCustomCurveUiState(
    deviceUid = deviceUid,
    connectionVisualState = presentation.current.connectionVisualState,
    channels = presentation.channels,
    draft = presentation.draftResolution.draft,
    selectedTimeMs = presentation.focus.selectedTimeMs,
    previewTimeMs = presentation.focus.playheadTimeMs,
    deviceTimeMs = presentation.deviceTimeMs,
    playheadMode = presentation.focus.playheadMode,
    maxPoints = maxPoints,
    timeStepMs = timeStepMs,
    contentEnabled = true,
    firmwareWriteAuthoritative = firmwareWriteAuthoritative,
    deviceProgramInstalled = installed,
    initialLoading = false,
    operationInProgress = presentation.current.operationInProgress,
    blockingOperationInProgress = presentation.current.blockingOperationInProgress,
    hasUnsavedChanges =
        presentation.draftResolution.draft != presentation.draftResolution.checkpoint,
    hasUnappliedChanges = presentation.draftResolution.draft != toUiDraft()
)

internal fun DeviceLightCustomSnapshot.toUiDraft() = DeviceLightCustomDraft(
    weekdaysMask = weekdaysMask,
    points = points.map { point ->
        DeviceLightCustomPointUiState(
            timeMs = point.timeMs,
            channels = point.scene.channels.mapKeys { (channel, _) ->
                channel.toUiChannel()
            }
        )
    }
)

private fun DeviceLightCustomDraft.compatibleWith(
    channels: List<DeviceLightCustomChannelId>,
    maxPoints: Int
): Boolean = points.all { point -> point.channels.keys == channels.toSet() } &&
    points.size <= maxPoints

private fun DeviceLightCustomDraft.resolveSelection(
    current: DeviceLightCustomCurveUiState,
    deviceTimeMs: Long?
): Long? {
    val selectionReferenceMs = deviceTimeMs ?: current.previewTimeMs
    return current.selectedTimeMs
        ?.takeIf { selected -> points.any { point -> point.timeMs == selected } }
        ?: points.minByOrNull { point ->
            kotlin.math.abs(point.timeMs - selectionReferenceMs)
        }?.timeMs
}

private fun DeviceLightCustomCurveUiState.resolvePlayheadTime(
    deviceTimeMs: Long?
): Long = when (playheadMode) {
    DeviceLightCustomPlayheadMode.CLOCK -> deviceTimeMs ?: previewTimeMs
    DeviceLightCustomPlayheadMode.EDIT,
    DeviceLightCustomPlayheadMode.PREVIEW -> previewTimeMs
}
