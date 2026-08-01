package com.aqua.aqualight.data.devices.runtime.modules.security

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

data class DeviceSecurityStatus(
    val tokenGateEnabled: Boolean,
    val dynamicPairingEnabled: Boolean,
    val paired: Boolean,
    val runtime: DeviceSecurityRuntimePolicy,
    val ownership: DeviceSecurityOwnershipPolicy,
    val storage: DeviceSecurityCredentialStorage,
    val deviceUid: DeviceUid,
    val shortId: String,
    val serialNumber: String
)

data class DeviceSecurityRuntimePolicy(
    val transport: String,
    val authMessageType: String,
    val authScheme: String,
    val credentialSerialized: Boolean,
    val replayProtection: String,
    val mutatingCommandsRequireAuth: Boolean
)

data class DeviceSecurityOwnershipPolicy(
    val initialOwnershipTransport: String,
    val firstTokenTransport: String,
    val webSocketPairingCommand: String,
    val webSocketPairingCommandAuth: String,
    val webSocketPairingPurpose: String,
    val publicFirstPairingSupported: Boolean,
    val tokenReturnedByStatus: Boolean
)

data class DeviceSecurityCredentialStorage(
    val backend: String,
    val format: String,
    val storedPlaintext: Boolean,
    val tokenFormat: String,
    val tokenHexLength: Int,
    val tokenVersion: Long?,
    val pairedAtMs: Long?,
    val lastRotatedAtMs: Long?,
    val provisioningTokenPending: Boolean?
)

data class DeviceSecurityPairResult(
    val paired: Boolean,
    val tokenReturned: Boolean,
    val runtimeTransport: String,
    val authMessageType: String,
    val authScheme: String,
    val credentialSerialized: Boolean,
    val credentialRotationTransport: String,
    val credentialSerializedOnWebSocket: Boolean
)

data class DeviceSecurityRevocationResult(
    val operation: String,
    val command: String,
    val message: String,
    val status: DeviceSecurityStatus
)

sealed interface DeviceSecurityRevocationOutcome {
    data class Completed(
        val commandOutcome: DeviceRuntimeCommandOutcome.Success<DeviceSecurityRevocationResult>
    ) : DeviceSecurityRevocationOutcome

    data class CommandFailed(
        val commandOutcome: DeviceRuntimeCommandOutcome<DeviceSecurityRevocationResult>
    ) : DeviceSecurityRevocationOutcome

    data class LocalTeardownFailed(
        val commandOutcome: DeviceRuntimeCommandOutcome.Success<DeviceSecurityRevocationResult>,
        val reason: String
    ) : DeviceSecurityRevocationOutcome
}
