package com.aqua.aqualight.data.devices.runtime.modules.security

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration

class DeviceSecurityRuntimeRepository(
    private val commandGateway: DeviceRuntimeCommandGateway,
    private val onOwnershipCredentialInvalidated:
        suspend (DeviceUid, DeviceRuntimeConnectionGeneration) -> Unit
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceSecurityStatusResponse> =
        commandGateway.execute(deviceUid, DeviceSecurityStatusGetCommand(deviceUid))

    suspend fun pair(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceSecurityPairResult> =
        commandGateway.execute(deviceUid, DeviceSecurityPairCommand())

    suspend fun unpair(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceSecurityOwnershipResetResult> =
        resetOwnership(deviceUid, AqlWsContract.ACTION_SECURITY_UNPAIR)

    suspend fun reset(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceSecurityOwnershipResetResult> =
        resetOwnership(deviceUid, AqlWsContract.ACTION_SECURITY_RESET)

    private suspend fun resetOwnership(
        deviceUid: DeviceUid,
        action: String
    ): DeviceRuntimeCommandOutcome<DeviceSecurityOwnershipResetResult> {
        val outcome = commandGateway.execute(
            deviceUid,
            DeviceSecurityOwnershipResetCommand(
                expectedDeviceUid = deviceUid,
                action = action
            )
        )
        if (outcome !is DeviceRuntimeCommandOutcome.Success) return outcome

        return try {
            onOwnershipCredentialInvalidated(deviceUid, outcome.generation)
            outcome
        } catch (error: Throwable) {
            DeviceRuntimeCommandOutcome.LocalStateError(
                deviceUid = deviceUid,
                module = outcome.module,
                action = outcome.action,
                messageId = outcome.messageId,
                generation = outcome.generation,
                reason = error.message ?: "Local runtime credential cleanup failed."
            )
        }
    }
}
