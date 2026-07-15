package com.aqua.aqualight.ui.tabs.devices.add

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattEvent
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import kotlinx.coroutines.flow.Flow

/**
 * Testable boundary for the provisioning progress state machine.
 *
 * Android, BLE, owner scope, draft storage and repository transactions are
 * implemented by the composition layer rather than constructed by the ViewModel.
 */
interface DeviceProvisioningProgressOperations {
    val ownerUid: String
    val gattEvents: Flow<AqlBleProvisioningGattEvent>

    fun getDraft(sessionId: String): AqlProvisioningDraft?

    fun removeDraft(sessionId: String)

    suspend fun resolveQrAddress(
        draft: AqlProvisioningDraft
    ): Result<String>

    fun startGatt(draft: AqlProvisioningDraft)

    fun finalizeSetup(handoff: AqlProvisioningRuntimeHandoff)

    fun closeGatt()

    suspend fun prepareAndConnect(
        draft: AqlProvisioningDraft,
        handoff: AqlProvisioningRuntimeHandoff
    ): Result<DeviceSnapshot>

    suspend fun commitPreparedRegistration(
        snapshot: DeviceSnapshot
    ): Result<DeviceSnapshot>

    suspend fun rollbackProvisioningRegistration(
        deviceUid: DeviceUid
    ): Result<Unit>

    suspend fun rollbackProvisioningRegistrationForOwner(
        ownerUid: String,
        deviceUid: DeviceUid
    ): Result<Unit>

    fun resolveRoute(
        snapshot: DeviceSnapshot,
        requestedDeviceUid: String
    ): DeviceRoute
}
