package com.aqua.aqualight.data.devices.runtime.modules.security

import com.aqua.aqualight.data.devices.model.DeviceUid

data class DeviceSecurityTokenMetadata(
    val tokenVersion: Long,
    val pairedAtMs: Long,
    val lastRotatedAtMs: Long
)

data class DeviceSecurityStatus(
    val tokenGateEnabled: Boolean,
    val dynamicPairingEnabled: Boolean,
    val paired: Boolean,
    val runtimeTransport: String,
    val runtimeAuthMessageType: String,
    val runtimeAuthScheme: String,
    val runtimeCredentialSerialized: Boolean,
    val runtimeReplayProtection: String,
    val initialOwnershipTransport: String,
    val firstTokenTransport: String,
    val webSocketPairingCommand: String,
    val webSocketPairingCommandAuth: String,
    val webSocketPairingPurpose: String,
    val publicFirstPairingSupported: Boolean,
    val mutatingCommandsRequireAuth: Boolean,
    val tokenReturnedByStatus: Boolean,
    val tokenStorageBackend: String,
    val tokenStorageFormat: String,
    val tokenStoredPlaintext: Boolean,
    val tokenFormat: String,
    val tokenHexLength: Int,
    val deviceUid: DeviceUid,
    val shortId: String,
    val serialNumber: String,
    val provisioningTokenPending: Boolean,
    val tokenMetadata: DeviceSecurityTokenMetadata?
)

data class DeviceSecurityStatusResponse(
    val status: DeviceSecurityStatus
)

data class DeviceSecurityPairResult(
    val operation: String,
    val paired: Boolean,
    val tokenReturned: Boolean,
    val credentialRotationTransport: String,
    val credentialSerializedOnWebSocket: Boolean,
    val runtimeTransport: String,
    val authMessageType: String,
    val authScheme: String,
    val credentialSerialized: Boolean,
    val command: String,
    val authenticatedSessionUsed: Boolean
)

data class DeviceSecurityOwnershipResetResult(
    val operation: String,
    val paired: Boolean,
    val credentialReturned: Boolean,
    val runtimeTransport: String,
    val command: String,
    val message: String,
    val status: DeviceSecurityStatus
)
