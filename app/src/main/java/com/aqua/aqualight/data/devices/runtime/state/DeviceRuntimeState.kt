package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModuleStatus
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingStatus
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareOtaSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionStatus
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkStatus
import com.aqua.aqualight.data.devices.runtime.modules.security.DeviceSecurityStatusResponse
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeStatus
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerStatus

enum class DeviceRuntimeFreshness {
    UNAVAILABLE,
    LOADING,
    READY,
    STALE,
    ERROR
}

data class DeviceRuntimeModuleFault(
    val module: String,
    val action: String,
    val messageId: String,
    val reason: String
)

data class DeviceRuntimeProtocolFault(
    val module: String,
    val action: String,
    val reason: String,
    val receivedAtMillis: Long,
    val receivedAtElapsedMillis: Long
)

data class DeviceRuntimeValue<T>(
    val phase: DeviceRuntimeFreshness = DeviceRuntimeFreshness.UNAVAILABLE,
    val value: T? = null,
    val receivedAtMillis: Long? = null,
    val receivedAtElapsedMillis: Long? = null,
    val sourceMessageId: String? = null,
    val fault: DeviceRuntimeModuleFault? = null
) {
    companion object {
        fun <T> unavailable(): DeviceRuntimeValue<T> = DeviceRuntimeValue()
    }
}

data class DeviceRuntimeSupport(
    val security: Boolean = false,
    val network: Boolean = false,
    val time: Boolean = false,
    val light: Boolean = false,
    val lightTemperatureProtection: Boolean = false,
    val timer: Boolean = false,
    val dosing: Boolean = false,
    val cooling: Boolean = false,
    val firmware: Boolean = false,
    val ota: Boolean = false
) {
    companion object {
        fun from(metadata: DeviceRuntimeMetadata): DeviceRuntimeSupport = DeviceRuntimeSupport(
            security = true,
            network = metadata.modules.network,
            time = true,
            light = metadata.modules.light,
            lightTemperatureProtection = metadata.modules.light &&
                metadata.modules.temperature &&
                AqlDeviceFeatureKey.LIGHT_TEMPERATURE_PROTECTION in
                metadata.capabilities.supportedFeatures,
            timer = metadata.modules.timerApi,
            dosing = metadata.modules.dosing,
            cooling = metadata.modules.cooling,
            firmware = metadata.modules.firmware,
            ota = metadata.capabilities.capabilities.ota
        )
    }
}

data class DeviceRuntimeState(
    val deviceUid: DeviceUid,
    val generation: DeviceRuntimeConnectionGeneration? = null,
    val authenticated: Boolean = false,
    val support: DeviceRuntimeSupport = DeviceRuntimeSupport(),
    val metadata: DeviceRuntimeValue<DeviceRuntimeMetadata> =
        DeviceRuntimeValue.unavailable(),
    val device: DeviceRuntimeValue<DeviceRuntimeModuleStatus> =
        DeviceRuntimeValue.unavailable(),
    val security: DeviceRuntimeValue<DeviceSecurityStatusResponse> =
        DeviceRuntimeValue.unavailable(),
    val network: DeviceRuntimeValue<DeviceNetworkStatus> =
        DeviceRuntimeValue.unavailable(),
    val time: DeviceRuntimeValue<DeviceTimeStatus> =
        DeviceRuntimeValue.unavailable(),
    val light: DeviceRuntimeValue<DeviceLightStatus> =
        DeviceRuntimeValue.unavailable(),
    val lightTemperatureProtection:
        DeviceRuntimeValue<DeviceLightTemperatureProtectionStatus> =
        DeviceRuntimeValue.unavailable(),
    val timer: DeviceRuntimeValue<DeviceTimerStatus> =
        DeviceRuntimeValue.unavailable(),
    val dosing: DeviceRuntimeValue<DeviceDosingStatus> =
        DeviceRuntimeValue.unavailable(),
    val cooling: DeviceRuntimeValue<DeviceCoolingStatus> =
        DeviceRuntimeValue.unavailable(),
    val firmware: DeviceRuntimeValue<DeviceFirmwareStatus> =
        DeviceRuntimeValue.unavailable(),
    val ota: DeviceRuntimeValue<DeviceFirmwareOtaSnapshot> =
        DeviceRuntimeValue.unavailable(),
    val protocolFault: DeviceRuntimeProtocolFault? = null
)

enum class DeviceRuntimeStateTarget {
    METADATA,
    SECURITY,
    NETWORK,
    TIME,
    LIGHT,
    LIGHT_TEMPERATURE_PROTECTION,
    TIMER,
    DOSING,
    COOLING,
    FIRMWARE,
    OTA
}
