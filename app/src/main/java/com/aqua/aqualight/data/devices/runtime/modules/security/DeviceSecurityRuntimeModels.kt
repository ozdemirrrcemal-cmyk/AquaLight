package com.aqua.aqualight.data.devices.runtime.modules.security

import com.aqua.aqualight.data.devices.model.DeviceUid

data class DeviceSecurityStatus(
    val tokenGateEnabled: Boolean,
    val dynamicPairingEnabled: Boolean,
    val paired: Boolean,
    val mutatingCommandsRequireAuth: Boolean,
    val deviceUid: DeviceUid,
    val shortId: String,
    val serialNumber: String,
    val tokenVersion: Long?,
    val pairedAtMillis: Long?,
    val lastRotatedAtMillis: Long?,
    val provisioningTokenPending: Boolean
)

data class DeviceSecurityPairResult(
    val paired: Boolean,
    val tokenReturned: Boolean,
    val credentialRotationTransport: String
)

data class DeviceSecurityRevocationAck(
    val operation: Operation,
    val paired: Boolean,
    val credentialReturned: Boolean,
    val status: DeviceSecurityStatus
) {
    enum class Operation(val wireValue: String, val action: String) {
        UNPAIR("unpair", "unpair"),
        RESET("reset", "reset")
    }
}
