package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingMutationReconciliation
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityValidation
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirMutationOrigin
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSettings
import com.aqua.aqualight.application.devices.dosing.applyReservoirSettingsAgainstOrigin
import com.aqua.aqualight.application.devices.dosing.requiresLowReservoirAttention
import com.aqua.aqualight.application.devices.dosing.setReservoirLowLevelAlertPreference
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class DeviceDosingReservoirDraft(
    val reservoirCapacityMicroliters: Long =
        DeviceDosingReservoirCapacityPolicy.DEFAULT_CAPACITY_MICROLITERS,
    val trackingEnabled: Boolean = false,
    val lowLevelAlertEnabled: Boolean = false
)

internal enum class DeviceDosingReservoirNotificationAvailability {
    AVAILABLE,
    OWNER_PREFERENCE_DISABLED,
    ANDROID_BLOCKED
}

internal data class DeviceDosingReservoirEditorState(
    val draft: DeviceDosingReservoirDraft = DeviceDosingReservoirDraft(),
    val savedDraft: DeviceDosingReservoirDraft = DeviceDosingReservoirDraft(),
    val remainingMicroliters: Long? = null,
    val remainingAccountingCertain: Boolean = true,
    val reservoirNeedsAttention: Boolean = false,
    val editable: Boolean = false,
    val refillSupported: Boolean = false,
    val operationInProgress: Boolean = false,
    val initialized: Boolean = false,
    val baseRevision: Long? = null,
    val capacityRejection: DeviceDosingReservoirCapacityRejection? = null,
    val notificationAvailability: DeviceDosingReservoirNotificationAvailability =
        DeviceDosingReservoirNotificationAvailability.AVAILABLE
) {
    val firmwareConfigDirty: Boolean
        get() = !draft.hasSameFirmwareReservoirConfig(savedDraft)

    val alertPreferenceDirty: Boolean
        get() = draft.effectiveLowLevelAlertEnabled != savedDraft.effectiveLowLevelAlertEnabled

    val dirty: Boolean
        get() = firmwareConfigDirty || alertPreferenceDirty

    val canSave: Boolean
        get() = initialized && dirty && !operationInProgress && capacityRejection == null &&
            (!firmwareConfigDirty || (editable && baseRevision != null))

    val canRefill: Boolean
        get() {
            val authoritativeCapacity = savedDraft.reservoirCapacityMicroliters
            // Refill confirms a physical fill. An uncertain legacy balance is
            // not converted into a recovery instruction for the user.
            val refillNeeded = remainingAccountingCertain &&
                remainingMicroliters?.let { remaining -> remaining < authoritativeCapacity } == true
            return initialized && refillSupported && savedDraft.trackingEnabled &&
                authoritativeCapacity > 0L && !firmwareConfigDirty && !operationInProgress &&
                refillNeeded
        }

    val settingsIntent: DeviceDosingReservoirSettings
        get() = DeviceDosingReservoirSettings(
            trackingEnabled = draft.trackingEnabled,
            capacityMicroliters = draft.reservoirCapacityMicroliters.takeIf { draft.trackingEnabled },
            lowLevelAlertEnabled = draft.effectiveLowLevelAlertEnabled
        )
}

internal sealed interface DeviceDosingReservoirEvent {
    data object Saved : DeviceDosingReservoirEvent
    data class SaveRejected(val reason: DeviceDosingChannelRejection) : DeviceDosingReservoirEvent
    data object SaveFailed : DeviceDosingReservoirEvent
    data object Refilled : DeviceDosingReservoirEvent
    data object RefillFailed : DeviceDosingReservoirEvent
}

/** Reservoir editor backed only by the central channel application boundary. */
internal class DeviceDosingReservoirViewModel(
    private val operations: DeviceDosingChannelOperations
) : ViewModel() {
    private val mutableEditorState = MutableStateFlow(DeviceDosingReservoirEditorState())
    val editorState: StateFlow<DeviceDosingReservoirEditorState> = mutableEditorState.asStateFlow()

    private val eventChannel = Channel<DeviceDosingReservoirEvent>(Channel.BUFFERED)
    val events: Flow<DeviceDosingReservoirEvent> = eventChannel.receiveAsFlow()

    private val jobs = ReservoirEditorJobs()
    private var boundDeviceUid = ""
    private var boundSlotId = ""

    fun currentEditorState(): DeviceDosingReservoirEditorState = mutableEditorState.value

    fun currentDraft(): DeviceDosingReservoirDraft = mutableEditorState.value.draft

    fun bind(deviceUidText: String, slotIdText: String) {
        val deviceUid = deviceUidText.trim()
        val slotId = slotIdText.trim()
        if (deviceUid.isBlank() || slotId.isBlank()) {
            jobs.cancelAll()
            boundDeviceUid = ""
            boundSlotId = ""
            mutableEditorState.value = DeviceDosingReservoirEditorState()
            return
        }
        if (boundDeviceUid == deviceUid && boundSlotId == slotId) return

        jobs.cancelAll()
        boundDeviceUid = deviceUid
        boundSlotId = slotId
        mutableEditorState.value = DeviceDosingReservoirEditorState()

        jobs.observe = viewModelScope.launch {
            operations.observe(deviceUid, slotId).collect { snapshot ->
                if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@collect
                mutableEditorState.value = snapshot?.let { authoritative ->
                    reduceReservoirSnapshot(mutableEditorState.value, authoritative)
                } ?: mutableEditorState.value.asUnavailable()
            }
        }
        jobs.refresh = viewModelScope.launch {
            when (val result = operations.refresh(deviceUid, slotId)) {
                is DeviceDosingChannelOperationResult.Success ->
                    mutableEditorState.value = reduceReservoirSnapshot(
                        mutableEditorState.value,
                        result.snapshot
                    )
                else -> if (!mutableEditorState.value.initialized) {
                    mutableEditorState.value = mutableEditorState.value.asUnavailable()
                }
            }
        }
    }

    fun setTrackingEnabled(enabled: Boolean) = updateDraft { draft ->
        draft.copy(trackingEnabled = enabled)
    }

    fun setLowLevelAlertEnabled(enabled: Boolean) {
        val current = mutableEditorState.value
        if (enabled && !current.draft.trackingEnabled) return
        updateDraft { draft -> draft.copy(lowLevelAlertEnabled = enabled) }
    }

    fun setNotificationAvailability(availability: DeviceDosingReservoirNotificationAvailability) {
        mutableEditorState.value = mutableEditorState.value.copy(notificationAvailability = availability)
    }

    fun setCapacityInput(rawValue: String, locale: Locale) {
        val state = mutableEditorState.value
        if (!state.editable || state.operationInProgress) return
        mutableEditorState.value = when (val validation =
            DeviceDosingReservoirCapacityPolicy.validate(rawValue, locale)
        ) {
            is DeviceDosingReservoirCapacityValidation.Accepted -> state.copy(
                draft = state.draft.copy(
                    reservoirCapacityMicroliters = validation.capacityMicroliters
                ),
                capacityRejection = null
            )
            is DeviceDosingReservoirCapacityValidation.Rejected ->
                state.copy(capacityRejection = validation.reason)
        }
    }

    fun save() {
        val deviceUid = boundDeviceUid
        val slotId = boundSlotId
        val state = mutableEditorState.value
        if (deviceUid.isBlank() || slotId.isBlank() || !state.canSave) return

        mutableEditorState.value = state.copy(operationInProgress = true)
        jobs.mutation?.cancel()
        jobs.mutation = viewModelScope.launch {
            val reconciliation = try {
                performReservoirSave(operations, deviceUid, slotId, state)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                DeviceDosingMutationReconciliation(DeviceDosingChannelOperationResult.Failed)
            }
            if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@launch

            val resolution = resolveReservoirSaveResult(
                operations = operations,
                deviceUid = deviceUid,
                slotId = slotId,
                current = mutableEditorState.value,
                reconciliation = reconciliation
            )
            if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@launch

            mutableEditorState.value = resolution.state
            eventChannel.send(resolution.event)
        }
    }

    fun refill() {
        val deviceUid = boundDeviceUid
        val slotId = boundSlotId
        val state = mutableEditorState.value
        if (deviceUid.isBlank() || slotId.isBlank() || !state.canRefill) return

        mutableEditorState.value = state.copy(operationInProgress = true)
        jobs.mutation?.cancel()
        jobs.mutation = viewModelScope.launch {
            val result = try {
                operations.refillReservoir(deviceUid, slotId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                DeviceDosingChannelOperationResult.Failed
            }
            if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@launch
            when (result) {
                is DeviceDosingChannelOperationResult.Success -> {
                    mutableEditorState.value = reduceReservoirSnapshot(
                        mutableEditorState.value.copy(operationInProgress = false),
                        result.snapshot
                    )
                    eventChannel.send(DeviceDosingReservoirEvent.Refilled)
                }
                else -> {
                    mutableEditorState.value = mutableEditorState.value.copy(
                        operationInProgress = false
                    )
                    eventChannel.send(DeviceDosingReservoirEvent.RefillFailed)
                }
            }
        }
    }

    private inline fun updateDraft(
        transform: (DeviceDosingReservoirDraft) -> DeviceDosingReservoirDraft
    ) {
        val current = mutableEditorState.value
        if (!current.editable || current.operationInProgress) return
        val updated = transform(current.draft)
        if (updated == current.draft) return
        mutableEditorState.value = current.copy(
            draft = updated,
            capacityRejection = null,
            notificationAvailability = if (!updated.effectiveLowLevelAlertEnabled) {
                DeviceDosingReservoirNotificationAvailability.AVAILABLE
            } else {
                current.notificationAvailability
            }
        )
    }
}

private class ReservoirEditorJobs {
    var observe: Job? = null
    var refresh: Job? = null
    var mutation: Job? = null

    fun cancelAll() {
        observe?.cancel()
        refresh?.cancel()
        mutation?.cancel()
    }
}

private data class ReservoirSaveResolution(
    val state: DeviceDosingReservoirEditorState,
    val event: DeviceDosingReservoirEvent
)

private data class ReservoirEditorBinding(
    val deviceUid: String,
    val slotId: String
)

private data class ReservoirSaveRejection(
    val reason: DeviceDosingChannelRejection,
    val authoritativeSnapshot: DeviceDosingChannelSnapshot?
)

private suspend fun resolveReservoirSaveResult(
    operations: DeviceDosingChannelOperations,
    deviceUid: String,
    slotId: String,
    current: DeviceDosingReservoirEditorState,
    reconciliation: DeviceDosingMutationReconciliation
): ReservoirSaveResolution = when (val result = reconciliation.result) {
    is DeviceDosingChannelOperationResult.Success -> ReservoirSaveResolution(
        state = reduceReservoirSnapshot(
            current.copy(operationInProgress = false),
            result.snapshot,
            forceAuthoritative = true
        ),
        event = DeviceDosingReservoirEvent.Saved
    )
    is DeviceDosingChannelCommittedResult -> ReservoirSaveResolution(
        // Persisted config is committed, but authoritative readback is still pending. Advance only
        // the editor's save base and local draft bookkeeping; never synthesize firmware state.
        state = current.copy(
            savedDraft = current.draft,
            operationInProgress = false,
            baseRevision = result.revision,
            capacityRejection = null
        ),
        event = DeviceDosingReservoirEvent.Saved
    )
    is DeviceDosingChannelOperationResult.Rejected -> resolveReservoirSaveRejection(
        operations = operations,
        binding = ReservoirEditorBinding(deviceUid, slotId),
        current = current,
        rejection = ReservoirSaveRejection(
            reason = result.reason,
            authoritativeSnapshot = reconciliation.authoritativeSnapshot
        )
    )
    DeviceDosingChannelOperationResult.Unavailable,
    DeviceDosingChannelOperationResult.Failed -> ReservoirSaveResolution(
        state = current.copy(operationInProgress = false),
        event = DeviceDosingReservoirEvent.SaveFailed
    )
}

private suspend fun resolveReservoirSaveRejection(
    operations: DeviceDosingChannelOperations,
    binding: ReservoirEditorBinding,
    current: DeviceDosingReservoirEditorState,
    rejection: ReservoirSaveRejection
): ReservoirSaveResolution {
    val resolvedState = if (rejection.reason == DeviceDosingChannelRejection.CONFLICT) {
        val refreshed = rejection.authoritativeSnapshot ?: refreshReservoirConflict(
            operations,
            binding.deviceUid,
            binding.slotId
        )
        refreshed?.let { snapshot -> rebaseReservoirDraft(current, snapshot) } ?: current
    } else {
        current
    }
    return ReservoirSaveResolution(
        state = resolvedState.copy(operationInProgress = false),
        event = DeviceDosingReservoirEvent.SaveRejected(rejection.reason)
    )
}

private suspend fun performReservoirSave(
    operations: DeviceDosingChannelOperations,
    deviceUid: String,
    slotId: String,
    state: DeviceDosingReservoirEditorState
): DeviceDosingMutationReconciliation = if (state.firmwareConfigDirty) {
    DeviceDosingMutationReconciliation(
        operations.applyReservoirSettingsAgainstOrigin(
            deviceUid = deviceUid,
            slotId = slotId,
            settings = state.settingsIntent,
            origin = DeviceDosingReservoirMutationOrigin(
                revision = checkNotNull(state.baseRevision),
                trackingEnabled = state.savedDraft.trackingEnabled,
                capacityMicroliters = state.savedDraft.reservoirCapacityMicroliters
                    .takeIf { state.savedDraft.trackingEnabled }
            )
        )
    )
} else {
    DeviceDosingMutationReconciliation(
        operations.setReservoirLowLevelAlertPreference(
            deviceUid = deviceUid,
            slotId = slotId,
            enabled = state.draft.effectiveLowLevelAlertEnabled
        )
    )
}

private suspend fun refreshReservoirConflict(
    operations: DeviceDosingChannelOperations,
    deviceUid: String,
    slotId: String
): DeviceDosingChannelSnapshot? = try {
    (operations.refresh(deviceUid, slotId) as? DeviceDosingChannelOperationResult.Success)?.snapshot
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    null
}

private fun reduceReservoirSnapshot(
    current: DeviceDosingReservoirEditorState,
    snapshot: DeviceDosingChannelSnapshot,
    forceAuthoritative: Boolean = false
): DeviceDosingReservoirEditorState {
    val authoritative = snapshot.toReservoirDraft()
    val preserveFirmwareDraft = current.initialized && current.firmwareConfigDirty && !forceAuthoritative
    val preserveAlertOnly = current.initialized && current.alertPreferenceDirty && !preserveFirmwareDraft &&
        !forceAuthoritative
    val draft = when {
        preserveFirmwareDraft -> current.draft
        preserveAlertOnly -> authoritative.copy(
            lowLevelAlertEnabled = current.draft.lowLevelAlertEnabled
        )
        else -> authoritative
    }
    val savedDraft = if (preserveFirmwareDraft) current.savedDraft else authoritative
    val firmwareBaselineStillCurrent = current.savedDraft.hasSameFirmwareReservoirConfig(
        authoritative
    )
    val baseRevision = if (preserveFirmwareDraft && !firmwareBaselineStillCurrent) {
        current.baseRevision
    } else {
        snapshot.revision
    }
    val reservoir = snapshot.reservoir
    return current.copy(
        draft = draft,
        savedDraft = savedDraft,
        remainingMicroliters = reservoir.remainingMicroliters.takeIf { reservoir.trackingEnabled },
        remainingAccountingCertain = reservoir.accountingCertain,
        reservoirNeedsAttention = reservoir.requiresLowReservoirAttention,
        editable = snapshot.controls.reservoirEditable,
        refillSupported = snapshot.controls.refillSupported,
        initialized = true,
        baseRevision = baseRevision,
        capacityRejection = null,
        notificationAvailability = if (!draft.effectiveLowLevelAlertEnabled) {
            DeviceDosingReservoirNotificationAvailability.AVAILABLE
        } else {
            current.notificationAvailability
        }
    )
}

private fun rebaseReservoirDraft(
    current: DeviceDosingReservoirEditorState,
    snapshot: DeviceDosingChannelSnapshot
): DeviceDosingReservoirEditorState {
    val authoritative = snapshot.toReservoirDraft()
    val reservoir = snapshot.reservoir
    return current.copy(
        savedDraft = authoritative,
        remainingMicroliters = reservoir.remainingMicroliters.takeIf { reservoir.trackingEnabled },
        remainingAccountingCertain = reservoir.accountingCertain,
        reservoirNeedsAttention = reservoir.requiresLowReservoirAttention,
        editable = snapshot.controls.reservoirEditable,
        refillSupported = snapshot.controls.refillSupported,
        baseRevision = snapshot.revision,
        capacityRejection = null
    )
}

private fun DeviceDosingReservoirEditorState.asUnavailable(): DeviceDosingReservoirEditorState =
    copy(editable = false, refillSupported = false, operationInProgress = false)

private fun DeviceDosingChannelSnapshot.toReservoirDraft(): DeviceDosingReservoirDraft =
    DeviceDosingReservoirDraft(
        reservoirCapacityMicroliters = DeviceDosingReservoirCapacityPolicy
            .normalizePersistedMicroliters(reservoir.capacityMicroliters),
        trackingEnabled = reservoir.trackingEnabled,
        lowLevelAlertEnabled = reservoir.lowLevelAlertEnabled
    )

private val DeviceDosingReservoirDraft.effectiveLowLevelAlertEnabled: Boolean
    get() = trackingEnabled && lowLevelAlertEnabled

private fun DeviceDosingReservoirDraft.hasSameFirmwareReservoirConfig(
    other: DeviceDosingReservoirDraft
): Boolean = trackingEnabled == other.trackingEnabled &&
    (!trackingEnabled || reservoirCapacityMicroliters == other.reservoirCapacityMicroliters)
