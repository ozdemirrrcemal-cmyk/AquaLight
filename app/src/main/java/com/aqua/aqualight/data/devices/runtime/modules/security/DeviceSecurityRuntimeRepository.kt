package com.aqua.aqualight.data.devices.runtime.modules.security

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand

class DeviceSecurityRuntimeRepository(
    private val gateway: DeviceRuntimeCommandGateway,
    private val revokeLocalCredential: suspend (DeviceUid) -> Result<Unit>
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceSecurityStatus> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = AqlWsContract.MODULE_SECURITY,
            action = AqlWsContract.ACTION_SECURITY_STATUS_GET,
            successParser = { data -> DeviceSecurityParser.parseStatus(data, deviceUid) }
        )
    )

    suspend fun confirmPairing(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceSecurityPairResult> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = AqlWsContract.MODULE_SECURITY,
            action = AqlWsContract.ACTION_SECURITY_PAIR,
            successParser = DeviceSecurityParser::parsePair
        )
    )

    suspend fun unpair(deviceUid: DeviceUid): DeviceSecurityRevocationOutcome =
        revoke(deviceUid, AqlWsContract.ACTION_SECURITY_UNPAIR)

    suspend fun reset(deviceUid: DeviceUid): DeviceSecurityRevocationOutcome =
        revoke(deviceUid, AqlWsContract.ACTION_SECURITY_RESET)

    private suspend fun revoke(
        deviceUid: DeviceUid,
        action: String
    ): DeviceSecurityRevocationOutcome {
        val outcome = gateway.execute(
            deviceUid,
            DeviceRuntimeJsonCommand(
                module = AqlWsContract.MODULE_SECURITY,
                action = action,
                successParser = { data ->
                    DeviceSecurityParser.parseRevocation(data, deviceUid, action)
                }
            )
        )
        return when (outcome) {
            is DeviceRuntimeCommandOutcome.Success -> revokeLocalCredential(deviceUid).fold(
                onSuccess = { DeviceSecurityRevocationOutcome.Completed(outcome) },
                onFailure = { error ->
                    DeviceSecurityRevocationOutcome.LocalTeardownFailed(
                        commandOutcome = outcome,
                        reason = error.message.orEmpty().ifBlank {
                            "Local credential and session teardown failed."
                        }
                    )
                }
            )
            else -> DeviceSecurityRevocationOutcome.CommandFailed(outcome)
        }
    }
}
