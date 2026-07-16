package com.aqua.aqualight.data.devices.provisioning

import com.aqua.aqualight.application.devices.provisioning.ProvisioningErrorCode
import com.aqua.aqualight.application.devices.provisioning.ProvisioningRuntimeEndpoint
import com.aqua.aqualight.application.devices.provisioning.ProvisioningRuntimeHandoff
import com.aqua.aqualight.application.devices.provisioning.ProvisioningSessionSnapshot
import com.aqua.aqualight.application.devices.provisioning.ProvisioningStatus
import com.aqua.aqualight.application.devices.provisioning.ProvisioningStatusMessage
import com.aqua.aqualight.application.devices.provisioning.ProvisioningTransportEvent
import com.aqua.aqualight.application.devices.provisioning.ProvisioningVerifiedDeviceInfo
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattEvent
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningStatusMessage
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningStatus

internal fun AqlProvisioningDraft.toApplicationSession(): ProvisioningSessionSnapshot =
    ProvisioningSessionSnapshot(
        sessionId = sessionId,
        candidateId = candidateId,
        bleAddress = bleAddress,
        bleName = bleName,
        deviceTitle = deviceTitle,
        deviceSerial = deviceSerial,
        deviceModel = deviceModel,
        wifiSsid = wifiCredentials.ssid,
        createdAtMillis = createdAtMillis
    )

internal fun AqlProvisioningDraft.withVerifiedInfo(
    info: ProvisioningVerifiedDeviceInfo?
): AqlProvisioningDraft {
    if (info == null) return this
    return copy(
        deviceTitle = info.title.ifBlank { deviceTitle },
        deviceSerial = info.serial.ifBlank { deviceSerial },
        deviceModel = info.model.ifBlank { deviceModel }
    )
}

internal fun AqlBleProvisioningGattEvent.toApplicationEvent(
    runtimeHandoffMapper: (AqlProvisioningRuntimeHandoff) -> ProvisioningRuntimeHandoff =
        { handoff -> handoff.toApplicationReference(TEST_HANDOFF_REFERENCE) }
): ProvisioningTransportEvent = when (this) {
    is AqlBleProvisioningGattEvent.Connecting ->
        ProvisioningTransportEvent.Connecting(address)
    is AqlBleProvisioningGattEvent.Connected ->
        ProvisioningTransportEvent.Connected(address)
    AqlBleProvisioningGattEvent.ServicesDiscovered ->
        ProvisioningTransportEvent.ServicesDiscovered
    is AqlBleProvisioningGattEvent.DeviceInfoVerified ->
        ProvisioningTransportEvent.DeviceInfoVerified(
            ProvisioningVerifiedDeviceInfo(deviceTitle, deviceSerial, deviceModel)
        )
    AqlBleProvisioningGattEvent.StartSessionWritten ->
        ProvisioningTransportEvent.StartSessionWritten
    AqlBleProvisioningGattEvent.WifiCredentialsWritten ->
        ProvisioningTransportEvent.WifiCredentialsWritten
    is AqlBleProvisioningGattEvent.StatusReceived ->
        ProvisioningTransportEvent.StatusReceived(statusMessage.toApplicationMessage())
    is AqlBleProvisioningGattEvent.RuntimeHandoffReceived ->
        ProvisioningTransportEvent.RuntimeHandoffReceived(runtimeHandoffMapper(handoff))
    AqlBleProvisioningGattEvent.FinalizeSetupWritten ->
        ProvisioningTransportEvent.FinalizeSetupWritten
    AqlBleProvisioningGattEvent.Completed -> ProvisioningTransportEvent.Completed
    is AqlBleProvisioningGattEvent.Failed -> ProvisioningTransportEvent.Failed(message)
    AqlBleProvisioningGattEvent.Disconnected -> ProvisioningTransportEvent.Disconnected
}

private fun AqlBleProvisioningStatusMessage.toApplicationMessage(): ProvisioningStatusMessage =
    ProvisioningStatusMessage(
        status = ProvisioningStatus.valueOf(status.name),
        message = message,
        errorCode = errorCode.toApplicationErrorCode(),
        rawErrorCode = errorCode,
        retryable = retryable,
        rawPayload = raw
    )

private fun String.toApplicationErrorCode(): ProvisioningErrorCode = when (this) {
    AqlBleProvisioningContract.ErrorCode.WIFI_AUTH_FAILED ->
        ProvisioningErrorCode.WIFI_AUTH_FAILED
    AqlBleProvisioningContract.ErrorCode.WIFI_NETWORK_NOT_FOUND ->
        ProvisioningErrorCode.WIFI_NETWORK_NOT_FOUND
    AqlBleProvisioningContract.ErrorCode.WIFI_HANDSHAKE_FAILED ->
        ProvisioningErrorCode.WIFI_HANDSHAKE_FAILED
    AqlBleProvisioningContract.ErrorCode.WIFI_ASSOCIATION_FAILED ->
        ProvisioningErrorCode.WIFI_ASSOCIATION_FAILED
    AqlBleProvisioningContract.ErrorCode.WIFI_TIMEOUT ->
        ProvisioningErrorCode.WIFI_TIMEOUT
    AqlBleProvisioningContract.ErrorCode.NETWORK_SAVE_FAILED ->
        ProvisioningErrorCode.NETWORK_SAVE_FAILED
    else -> ProvisioningErrorCode.UNKNOWN
}

internal fun AqlProvisioningRuntimeHandoff.toApplicationReference(
    handoffId: String
): ProvisioningRuntimeHandoff = ProvisioningRuntimeHandoff(
    handoffId = handoffId.trim().also { reference ->
        require(reference.isNotBlank()) { "Provisioning handoff reference is unavailable." }
    },
    deviceUid = deviceUid.value,
    endpoint = endpoint.toApplicationEndpoint()
)

private fun DeviceRuntimeEndpoint.toApplicationEndpoint(): ProvisioningRuntimeEndpoint =
    ProvisioningRuntimeEndpoint(
        ip = ip,
        wifiMode = wifiMode,
        wifiConnected = wifiConnected,
        setupApActive = setupApActive,
        runtimeTransport = runtimeTransport,
        webSocketPort = wsPort,
        webSocketPath = wsPath,
        webSocketProtocol = wsProtocol,
        webSocketProtocolVersion = wsProtocolVersion,
        discoveryPort = discoveryPort
    )

private const val TEST_HANDOFF_REFERENCE = "mapping-only-handoff-reference"
