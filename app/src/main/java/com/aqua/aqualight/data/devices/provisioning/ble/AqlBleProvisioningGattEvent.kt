package com.aqua.aqualight.data.devices.provisioning.ble

import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff

sealed interface AqlBleProvisioningGattEvent {
    data class Connecting(val address: String) : AqlBleProvisioningGattEvent
    data class Connected(val address: String) : AqlBleProvisioningGattEvent
    object ServicesDiscovered : AqlBleProvisioningGattEvent
    data class DeviceInfoVerified(
        val deviceTitle: String,
        val deviceSerial: String,
        val deviceModel: String
    ) : AqlBleProvisioningGattEvent
    object StartSessionWritten : AqlBleProvisioningGattEvent
    object WifiCredentialsWritten : AqlBleProvisioningGattEvent
    data class StatusReceived(val statusMessage: AqlBleProvisioningStatusMessage) : AqlBleProvisioningGattEvent
    data class RuntimeHandoffReceived(val handoff: AqlProvisioningRuntimeHandoff) : AqlBleProvisioningGattEvent
    object Completed : AqlBleProvisioningGattEvent
    data class Failed(val message: String) : AqlBleProvisioningGattEvent
    object Disconnected : AqlBleProvisioningGattEvent
}
