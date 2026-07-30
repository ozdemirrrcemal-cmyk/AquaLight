package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingStatus
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareOtaSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionStatus
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeStatus
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerStatus

enum class DeviceRuntimeValuePhase {
    UNAVAILABLE,
    LOADING,
    READY,
    STALE,
    ERROR
}

enum class DeviceRuntimeValueSource {
    RESPONSE,
    EVENT,
    CACHE
}

data class DeviceRuntimeFault(
    val code: String,
    val message: String,
    val field: String = "",
    val module: String = "",
    val action: String = "",
    val messageId: String = "",
    val occurredAtMillis: Long
)

data class DeviceRuntimeValue<T>(
    val phase: DeviceRuntimeValuePhase = DeviceRuntimeValuePhase.UNAVAILABLE,
    val value: T? = null,
    val source: DeviceRuntimeValueSource? = null,
    val messageId: String = "",
    val receivedAtMillis: Long = 0L,
    val fault: DeviceRuntimeFault? = null
) {
    val isReady: Boolean
        get() = phase == DeviceRuntimeValuePhase.READY && value != null

    fun loading(): DeviceRuntimeValue<T> = copy(
        phase = if (value == null) DeviceRuntimeValuePhase.LOADING else DeviceRuntimeValuePhase.STALE,
        fault = null
    )

    fun stale(): DeviceRuntimeValue<T> = copy(
        phase = if (value == null) DeviceRuntimeValuePhase.UNAVAILABLE else DeviceRuntimeValuePhase.STALE
    )

    fun ready(
        nextValue: T,
        nextSource: DeviceRuntimeValueSource,
        nextMessageId: String,
        nowMillis: Long
    ): DeviceRuntimeValue<T> = DeviceRuntimeValue(
        phase = DeviceRuntimeValuePhase.READY,
        value = nextValue,
        source = nextSource,
        messageId = nextMessageId,
        receivedAtMillis = nowMillis,
        fault = null
    )

    fun failed(nextFault: DeviceRuntimeFault): DeviceRuntimeValue<T> = copy(
        phase = DeviceRuntimeValuePhase.ERROR,
        fault = nextFault
    )
}

data class DeviceRuntimeWireRecord(
    val type: String,
    val module: String,
    val action: String,
    val messageId: String,
    val dataJson: String,
    val receivedAtMillis: Long
)

data class DeviceRuntimeState(
    val deviceUid: DeviceUid,
    val authenticated: Boolean = false,
    val metadataGeneration: Long = 0L,
    val device: DeviceRuntimeValue<DeviceRuntimeDeviceStatus> = DeviceRuntimeValue(),
    val security: DeviceRuntimeValue<DeviceRuntimeSecurityStatus> = DeviceRuntimeValue(),
    val network: DeviceRuntimeValue<DeviceRuntimeNetworkStatus> = DeviceRuntimeValue(),
    val time: DeviceRuntimeValue<DeviceTimeStatus> = DeviceRuntimeValue(),
    val firmware: DeviceRuntimeValue<DeviceFirmwareStatus> = DeviceRuntimeValue(),
    val ota: DeviceRuntimeValue<DeviceFirmwareOtaSnapshot> = DeviceRuntimeValue(),
    val light: DeviceRuntimeValue<DeviceLightStatus> = DeviceRuntimeValue(),
    val lightTemperatureProtection:
        DeviceRuntimeValue<DeviceLightTemperatureProtectionStatus> = DeviceRuntimeValue(),
    val cooling: DeviceRuntimeValue<DeviceCoolingStatus> = DeviceRuntimeValue(),
    val timer: DeviceRuntimeValue<DeviceTimerStatus> = DeviceRuntimeValue(),
    val dosing: DeviceRuntimeValue<DeviceDosingStatus> = DeviceRuntimeValue(),
    val lastPayloads: Map<String, DeviceRuntimeWireRecord> = emptyMap(),
    val lastFault: DeviceRuntimeFault? = null,
    val lastMessageAtMillis: Long = 0L
) {
    fun markAuthenticated(): DeviceRuntimeState = copy(
        authenticated = true,
        lastFault = null
    )

    fun markDisconnected(): DeviceRuntimeState = copy(
        authenticated = false,
        device = device.stale(),
        security = security.stale(),
        network = network.stale(),
        time = time.stale(),
        firmware = firmware.stale(),
        ota = ota.stale(),
        light = light.stale(),
        lightTemperatureProtection = lightTemperatureProtection.stale(),
        cooling = cooling.stale(),
        timer = timer.stale(),
        dosing = dosing.stale()
    )

    fun beginBootstrap(
        generation: Long,
        targets: Set<DeviceRuntimeRefreshTarget>
    ): DeviceRuntimeState {
        require(generation > 0L) { "Validated metadata generation must be positive." }
        return copy(
            authenticated = true,
            metadataGeneration = generation,
            device = device.loadingIf(DeviceRuntimeRefreshTarget.DEVICE in targets),
            security = security.loadingIf(DeviceRuntimeRefreshTarget.SECURITY in targets),
            network = network.loadingIf(DeviceRuntimeRefreshTarget.NETWORK in targets),
            time = time.loadingIf(DeviceRuntimeRefreshTarget.TIME in targets),
            firmware = firmware.loadingIf(DeviceRuntimeRefreshTarget.FIRMWARE in targets),
            ota = ota.loadingIf(DeviceRuntimeRefreshTarget.OTA in targets),
            light = light.loadingIf(DeviceRuntimeRefreshTarget.LIGHT in targets),
            lightTemperatureProtection = lightTemperatureProtection.loadingIf(
                DeviceRuntimeRefreshTarget.LIGHT_TEMPERATURE_PROTECTION in targets
            ),
            cooling = cooling.loadingIf(DeviceRuntimeRefreshTarget.COOLING in targets),
            timer = timer.loadingIf(DeviceRuntimeRefreshTarget.TIMER in targets),
            dosing = dosing.loadingIf(DeviceRuntimeRefreshTarget.DOSING in targets),
            lastFault = null
        )
    }
}

private fun <T> DeviceRuntimeValue<T>.loadingIf(enabled: Boolean): DeviceRuntimeValue<T> =
    if (enabled) loading() else this
