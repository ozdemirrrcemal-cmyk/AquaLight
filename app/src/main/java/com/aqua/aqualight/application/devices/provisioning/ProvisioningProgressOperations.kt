package com.aqua.aqualight.application.devices.provisioning

import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import kotlinx.coroutines.flow.Flow

/**
 * Application boundary for the full provisioning transaction.
 *
 * UI receives only owner-safe primitives and application models. BLE/GATT,
 * credentials, draft storage, repository snapshots and rollback implementation
 * remain behind the data adapter.
 */
interface ProvisioningProgressOperations {
    val ownerUid: String
    val events: Flow<ProvisioningTransportEvent>

    fun getSession(sessionId: String): ProvisioningSessionSnapshot?

    fun removeSession(sessionId: String)

    suspend fun resolveBleAddress(sessionId: String): Result<String>

    fun startTransport(
        sessionId: String,
        bleAddress: String
    ): Result<Unit>

    fun finalizeSetup(
        handoff: ProvisioningRuntimeHandoff
    ): Result<Unit>

    fun closeTransport()

    suspend fun prepareRegistration(
        sessionId: String,
        verifiedDeviceInfo: ProvisioningVerifiedDeviceInfo?,
        handoff: ProvisioningRuntimeHandoff
    ): Result<PreparedProvisioningRegistration>

    suspend fun commitPreparedRegistration(
        registration: PreparedProvisioningRegistration
    ): Result<Unit>

    suspend fun rollbackProvisioningRegistration(
        deviceUid: String
    ): Result<Unit>

    suspend fun rollbackProvisioningRegistrationForOwner(
        ownerUid: String,
        deviceUid: String
    ): Result<Unit>
}

data class ProvisioningSessionSnapshot(
    val sessionId: String,
    val candidateId: String,
    val bleAddress: String,
    val bleName: String,
    val deviceTitle: String,
    val deviceSerial: String,
    val deviceModel: String,
    val wifiSsid: String,
    val createdAtMillis: Long
)

data class ProvisioningVerifiedDeviceInfo(
    val title: String,
    val serial: String,
    val model: String
)

data class ProvisioningRuntimeEndpoint(
    val ip: String,
    val wifiMode: String,
    val wifiConnected: Boolean,
    val setupApActive: Boolean,
    val runtimeTransport: String,
    val webSocketPort: Int,
    val webSocketPath: String,
    val webSocketProtocol: String,
    val webSocketProtocolVersion: Int,
    val discoveryPort: Int
)

data class ProvisioningRuntimeHandoff(
    val deviceUid: String,
    val endpoint: ProvisioningRuntimeEndpoint,
    val webSocketToken: String
)

data class PreparedProvisioningRegistration(
    val registrationId: String,
    val device: ProvisionedDevice
)

data class ProvisionedDevice(
    val deviceUid: String,
    val title: String,
    val family: OwnerDeviceFamily
)

data class ProvisioningStatusMessage(
    val status: ProvisioningStatus,
    val message: String,
    val errorCode: ProvisioningErrorCode,
    val rawErrorCode: String,
    val retryable: Boolean,
    val rawPayload: String
)

enum class ProvisioningStatus {
    IDLE,
    FACTORY,
    PHYSICAL_RESET,
    PROVISIONING_IN_PROGRESS,
    CLAIM_VALIDATING,
    CLAIM_REJECTED,
    WIFI_CREDENTIALS_RECEIVED,
    WIFI_CONNECTING,
    WIFI_CONNECTED,
    WIFI_FAILED,
    WEB_SOCKET_TOKEN_READY,
    FINALIZING,
    COMPLETED,
    TIMEOUT,
    ERROR,
    UNKNOWN
}

enum class ProvisioningErrorCode {
    WIFI_AUTH_FAILED,
    WIFI_NETWORK_NOT_FOUND,
    WIFI_HANDSHAKE_FAILED,
    WIFI_ASSOCIATION_FAILED,
    WIFI_TIMEOUT,
    NETWORK_SAVE_FAILED,
    UNKNOWN
}

sealed interface ProvisioningTransportEvent {
    data class Connecting(val address: String) : ProvisioningTransportEvent
    data class Connected(val address: String) : ProvisioningTransportEvent
    data object ServicesDiscovered : ProvisioningTransportEvent
    data class DeviceInfoVerified(
        val info: ProvisioningVerifiedDeviceInfo
    ) : ProvisioningTransportEvent
    data object StartSessionWritten : ProvisioningTransportEvent
    data object WifiCredentialsWritten : ProvisioningTransportEvent
    data class StatusReceived(
        val statusMessage: ProvisioningStatusMessage
    ) : ProvisioningTransportEvent
    data class RuntimeHandoffReceived(
        val handoff: ProvisioningRuntimeHandoff
    ) : ProvisioningTransportEvent
    data object FinalizeSetupWritten : ProvisioningTransportEvent
    data object Completed : ProvisioningTransportEvent
    data class Failed(val message: String) : ProvisioningTransportEvent
    data object Disconnected : ProvisioningTransportEvent
}
