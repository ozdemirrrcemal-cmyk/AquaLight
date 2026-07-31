package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

internal data class DeviceRuntimeRefreshCommand(
    val module: String,
    val action: String
)

internal object DeviceRuntimeRefreshCatalog {

    fun bootstrapTargets(snapshot: DeviceSnapshot): Set<DeviceRuntimeRefreshTarget> = buildSet {
        add(DeviceRuntimeRefreshTarget.DEVICE)
        add(DeviceRuntimeRefreshTarget.SECURITY)
        add(DeviceRuntimeRefreshTarget.TIME)
        if (NETWORK_MODULE in snapshot.modules) add(DeviceRuntimeRefreshTarget.NETWORK)
        if (FIRMWARE_MODULE in snapshot.modules) add(DeviceRuntimeRefreshTarget.FIRMWARE)
        if (snapshot.capabilities.ota) add(DeviceRuntimeRefreshTarget.OTA)
        if (snapshot.capabilities.light) {
            add(DeviceRuntimeRefreshTarget.LIGHT)
            if (
                AqlDeviceFeatureKey.LIGHT_TEMPERATURE_PROTECTION.wireValue in
                snapshot.supportedFeatures
            ) {
                add(DeviceRuntimeRefreshTarget.LIGHT_TEMPERATURE_PROTECTION)
            }
        }
        if (snapshot.capabilities.cooling || snapshot.capabilities.fan) {
            add(DeviceRuntimeRefreshTarget.COOLING)
        }
        if (snapshot.capabilities.standaloneTimer) add(DeviceRuntimeRefreshTarget.TIMER)
        if (snapshot.capabilities.dosing) add(DeviceRuntimeRefreshTarget.DOSING)
    }

    fun command(target: DeviceRuntimeRefreshTarget): DeviceRuntimeRefreshCommand =
        checkNotNull(COMMANDS[target]) {
            "No firmware status command registered for $target."
        }

    fun isReadCommand(module: String, action: String): Boolean =
        action == AqlWsContract.ACTION_STATUS_GET ||
            module == AqlWsContract.MODULE_FIRMWARE &&
            action == AqlWsContract.ACTION_FIRMWARE_OTA_STATUS ||
            module == AqlWsContract.MODULE_LIGHT &&
            action == AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_STATUS_GET ||
            module == AqlWsContract.MODULE_DEVICE && action in DEVICE_METADATA_READ_ACTIONS

    private val COMMANDS = mapOf(
        DeviceRuntimeRefreshTarget.DEVICE to DeviceRuntimeRefreshCommand(
            AqlWsContract.MODULE_DEVICE,
            AqlWsContract.ACTION_DEVICE_STATUS_GET
        ),
        DeviceRuntimeRefreshTarget.SECURITY to DeviceRuntimeRefreshCommand(
            AqlWsContract.MODULE_SECURITY,
            AqlWsContract.ACTION_SECURITY_STATUS_GET
        ),
        DeviceRuntimeRefreshTarget.NETWORK to DeviceRuntimeRefreshCommand(
            AqlWsContract.MODULE_NETWORK,
            AqlWsContract.ACTION_NETWORK_STATUS_GET
        ),
        DeviceRuntimeRefreshTarget.TIME to DeviceRuntimeRefreshCommand(
            AqlWsContract.MODULE_TIME,
            AqlWsContract.ACTION_TIME_STATUS_GET
        ),
        DeviceRuntimeRefreshTarget.FIRMWARE to DeviceRuntimeRefreshCommand(
            AqlWsContract.MODULE_FIRMWARE,
            AqlWsContract.ACTION_FIRMWARE_STATUS_GET
        ),
        DeviceRuntimeRefreshTarget.OTA to DeviceRuntimeRefreshCommand(
            AqlWsContract.MODULE_FIRMWARE,
            AqlWsContract.ACTION_FIRMWARE_OTA_STATUS
        ),
        DeviceRuntimeRefreshTarget.LIGHT to DeviceRuntimeRefreshCommand(
            AqlWsContract.MODULE_LIGHT,
            AqlWsContract.ACTION_LIGHT_STATUS_GET
        ),
        DeviceRuntimeRefreshTarget.LIGHT_TEMPERATURE_PROTECTION to
            DeviceRuntimeRefreshCommand(
                AqlWsContract.MODULE_LIGHT,
                AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_STATUS_GET
            ),
        DeviceRuntimeRefreshTarget.COOLING to DeviceRuntimeRefreshCommand(
            AqlWsContract.MODULE_COOLING,
            AqlWsContract.ACTION_COOLING_STATUS_GET
        ),
        DeviceRuntimeRefreshTarget.TIMER to DeviceRuntimeRefreshCommand(
            AqlWsContract.MODULE_TIMER,
            AqlWsContract.ACTION_TIMER_STATUS_GET
        ),
        DeviceRuntimeRefreshTarget.DOSING to DeviceRuntimeRefreshCommand(
            AqlWsContract.MODULE_DOSING,
            AqlWsContract.ACTION_DOSING_STATUS_GET
        )
    )
    private val DEVICE_METADATA_READ_ACTIONS = setOf(
        AqlWsContract.ACTION_DEVICE_IDENTITY_GET,
        AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET
    )

    private const val NETWORK_MODULE = "network"
    private const val FIRMWARE_MODULE = "firmware"
}
