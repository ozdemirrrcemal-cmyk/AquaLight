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

/**
 * A type-safe pointer to one runtime value inside [DeviceRuntimeState].
 *
 * Each target owns its exact getter and setter, so reducer operations never use `Any`, unchecked
 * casts, reflection or string field names.
 */
sealed class DeviceRuntimeStateTarget<T>(
    private val supportPredicate: (DeviceRuntimeState) -> Boolean,
    private val read: (DeviceRuntimeState) -> DeviceRuntimeValue<T>,
    private val write: (DeviceRuntimeState, DeviceRuntimeValue<T>) -> DeviceRuntimeState
) {
    fun isSupported(state: DeviceRuntimeState): Boolean = supportPredicate(state)

    fun markLoading(state: DeviceRuntimeState): DeviceRuntimeState = write(
        state,
        read(state).copy(
            phase = DeviceRuntimeFreshness.LOADING,
            fault = null
        )
    )

    fun markUnavailable(state: DeviceRuntimeState): DeviceRuntimeState =
        write(state, DeviceRuntimeValue.unavailable())

    fun markError(
        state: DeviceRuntimeState,
        fault: DeviceRuntimeModuleFault
    ): DeviceRuntimeState = write(
        state,
        read(state).copy(
            phase = DeviceRuntimeFreshness.ERROR,
            fault = fault
        )
    )

    data object Metadata : DeviceRuntimeStateTarget<DeviceRuntimeMetadata>(
        supportPredicate = { state -> state.authenticated },
        read = { state -> state.metadata },
        write = { state, value -> state.copy(metadata = value) }
    )

    data object Security : DeviceRuntimeStateTarget<DeviceSecurityStatusResponse>(
        supportPredicate = { state -> state.support.security },
        read = { state -> state.security },
        write = { state, value -> state.copy(security = value) }
    )

    data object Network : DeviceRuntimeStateTarget<DeviceNetworkStatus>(
        supportPredicate = { state -> state.support.network },
        read = { state -> state.network },
        write = { state, value -> state.copy(network = value) }
    )

    data object Time : DeviceRuntimeStateTarget<DeviceTimeStatus>(
        supportPredicate = { state -> state.support.time },
        read = { state -> state.time },
        write = { state, value -> state.copy(time = value) }
    )

    data object Light : DeviceRuntimeStateTarget<DeviceLightStatus>(
        supportPredicate = { state -> state.support.light },
        read = { state -> state.light },
        write = { state, value -> state.copy(light = value) }
    )

    data object LightTemperatureProtection :
        DeviceRuntimeStateTarget<DeviceLightTemperatureProtectionStatus>(
            supportPredicate = { state -> state.support.lightTemperatureProtection },
            read = { state -> state.lightTemperatureProtection },
            write = { state, value -> state.copy(lightTemperatureProtection = value) }
        )

    data object Timer : DeviceRuntimeStateTarget<DeviceTimerStatus>(
        supportPredicate = { state -> state.support.timer },
        read = { state -> state.timer },
        write = { state, value -> state.copy(timer = value) }
    )

    data object Dosing : DeviceRuntimeStateTarget<DeviceDosingStatus>(
        supportPredicate = { state -> state.support.dosing },
        read = { state -> state.dosing },
        write = { state, value -> state.copy(dosing = value) }
    )

    data object Cooling : DeviceRuntimeStateTarget<DeviceCoolingStatus>(
        supportPredicate = { state -> state.support.cooling },
        read = { state -> state.cooling },
        write = { state, value -> state.copy(cooling = value) }
    )

    data object Firmware : DeviceRuntimeStateTarget<DeviceFirmwareStatus>(
        supportPredicate = { state -> state.support.firmware },
        read = { state -> state.firmware },
        write = { state, value -> state.copy(firmware = value) }
    )

    data object Ota : DeviceRuntimeStateTarget<DeviceFirmwareOtaSnapshot>(
        supportPredicate = { state -> state.support.ota },
        read = { state -> state.ota },
        write = { state, value -> state.copy(ota = value) }
    )
}
