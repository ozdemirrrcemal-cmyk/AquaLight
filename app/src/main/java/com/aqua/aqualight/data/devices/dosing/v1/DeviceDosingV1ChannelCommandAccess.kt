package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

internal suspend fun DeviceDosingV1StateAdapter.repositoryProgramApply(
    uid: DeviceUid,
    channelKey: DeviceDosingV1ChannelKey,
    revision: Long,
    program: DeviceDosingProgram
): DeviceRuntimeCommandOutcome<DeviceDosingV1SavedMutationResult> = repository.applyProgram(
    uid,
    DeviceDosingV1ProgramApplyRequest(
        channelKey = channelKey,
        expectedRevision = revision,
        program = DeviceDosingV1ProgramSnapshotMapper.toWireProgram(program)
    )
)

internal suspend fun DeviceDosingV1StateAdapter.repositoryConfigApply(
    uid: DeviceUid,
    request: DeviceDosingV1ConfigApplyRequest
) = repository.applyConfig(uid, request)

internal suspend fun DeviceDosingV1StateAdapter.repositoryReservoirRefill(
    uid: DeviceUid,
    channelKey: DeviceDosingV1ChannelKey
) = repository.refillReservoir(uid, channelKey)

internal suspend fun DeviceDosingV1StateAdapter.repositoryDoseNow(
    uid: DeviceUid,
    request: DeviceDosingV1DoseNowRequest
) = repository.doseNow(uid, request)

internal suspend fun DeviceDosingV1StateAdapter.repositoryDoseStop(
    uid: DeviceUid,
    channelKey: DeviceDosingV1ChannelKey
) = repository.stopDose(uid, channelKey)

internal suspend fun DeviceDosingV1StateAdapter.repositoryChannelReset(
    uid: DeviceUid,
    request: DeviceDosingV1ChannelResetRequest
) = repository.resetChannel(uid, request)
