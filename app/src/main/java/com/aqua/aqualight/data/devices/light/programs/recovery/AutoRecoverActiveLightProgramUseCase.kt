package com.aqua.aqualight.data.devices.light.programs.recovery

import com.aqua.aqualight.data.devices.api.light.LightMode
import com.aqua.aqualight.data.devices.light.programs.LightProgramDataStoreManager
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.light.programs.sync.LightProgramDeviceSyncMatcher
import com.aqua.aqualight.data.devices.light.programs.sync.LightProgramDeviceSyncStatus
import com.aqua.aqualight.data.devices.light.programs.sync.LightProgramRuntimeScheduleMapper
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeSnapshot
import kotlinx.coroutines.flow.first

/**
 * Reconciles the app's local program records with the controller's active LP
 * schedule. If the controller matches a saved record, that record becomes the
 * local active/on-device program. If it does not match any saved record, the
 * schedule is recovered as one compact local program card.
 */
class AutoRecoverActiveLightProgramUseCase(
    private val store: LightProgramDataStoreManager
) {

    suspend fun recoverIfNeeded(
        deviceId: Long,
        snapshot: LightRuntimeSnapshot?
    ): SavedLightProgram? {
        if (deviceId <= 0L || snapshot == null) return null
        if (!snapshot.mode.canAutoRecoverSchedule()) return null

        val checksum = LightProgramRuntimeScheduleMapper.checksum(
            scheduleChannels = snapshot.scheduleChannels
        )?.trim().orEmpty()
        if (checksum.isBlank()) return null

        val existingPrograms = store.programsForDeviceFlow(deviceId).first()
        val syncState = LightProgramDeviceSyncMatcher.match(
            programs = existingPrograms,
            snapshot = snapshot
        )

        if (syncState.status == LightProgramDeviceSyncStatus.LOCAL_ACTIVE_MATCHED) {
            return null
        }

        val matchedProgramId = syncState.matchedProgramId
        if (!matchedProgramId.isNullOrBlank()) {
            return store.markProgramActiveFromDevice(
                deviceId = deviceId,
                programId = matchedProgramId,
                checksum = checksum
            )
        }

        val draft = LightProgramRecoveredDraftMapper.toEditableDraft(
            scheduleChannels = snapshot.scheduleChannels
        ) ?: return null

        return store.createRecoveredProgramFromDevice(
            deviceId = deviceId,
            draft = draft,
            checksum = checksum
        )
    }

    private fun LightMode.canAutoRecoverSchedule(): Boolean {
        return when (this) {
            LightMode.AUTO,
            LightMode.UNKNOWN -> true
            LightMode.MANUAL,
            LightMode.SCENE,
            LightMode.MOONLIGHT,
            LightMode.IDLE -> false
        }
    }
}
