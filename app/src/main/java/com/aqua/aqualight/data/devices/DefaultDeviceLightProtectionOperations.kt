package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceLightProtectionOperations
import com.aqua.aqualight.application.devices.DeviceLightProtectionSnapshot
import com.aqua.aqualight.application.devices.DeviceLightProtectionThresholdPolicy
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.DeviceRuntimeModuleProvider
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionStatus
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Owner-scoped adapter that keeps catalog, runtime and firmware contracts out of presentation. */
internal class DefaultDeviceLightProtectionOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightProtectionOperations {

    override fun observeLightProtection(
        deviceUid: String
    ): Flow<DeviceLightProtectionSnapshot> {
        val uid = deviceUid.toDeviceUidOrNull()
        val modules = devicesRepository.runtimeModules()
        return when {
            uid == null -> flowOf(DeviceLightProtectionSnapshot())
            modules == null -> devicesRepository.observeDevice(uid).map { device ->
                DeviceLightProtectionSnapshot(
                    available = device.supportsLightProtectionSettings()
                )
            }
            else -> combine(
                devicesRepository.observeDevice(uid),
                modules.cooling.states,
                modules.lightTemperatureProtection.states
            ) { device, coolingStates, protectionStates ->
                toDeviceLightProtectionSnapshot(
                    available = device.supportsLightProtectionSettings(),
                    coolingState = coolingStates[uid],
                    protectionStatus = protectionStates[uid]
                )
            }.distinctUntilChanged()
        }
    }

    override fun currentLightProtection(
        deviceUid: String
    ): DeviceLightProtectionSnapshot {
        val uid = deviceUid.toDeviceUidOrNull()
        val modules = devicesRepository.runtimeModules()
        val device = uid?.let(devicesRepository::currentDevice)
        return if (uid == null || modules == null) {
            DeviceLightProtectionSnapshot(
                available = device.supportsLightProtectionSettings()
            )
        } else {
            toDeviceLightProtectionSnapshot(
                available = device.supportsLightProtectionSettings(),
                coolingState = modules.cooling.states.value[uid],
                protectionStatus = modules.lightTemperatureProtection.currentStatus(uid)
            )
        }
    }

    override suspend fun refreshLightProtection(deviceUid: String): Result<Unit> = runCatching {
        val uid = requireRegisteredDeviceUid(deviceUid)
        val modules = requireRuntimeModules()
        check(devicesRepository.currentDevice(uid).supportsLightProtectionSettings()) {
            "Temperature protection is not available for this device."
        }
        devicesRepository.connectRuntime(uid).getOrThrow()

        requestStatusWithRetry {
            modules.lightTemperatureProtection.requestStatus(uid)
        }.requireSuccessValue()

        // Current temperature is useful but not authoritative for threshold editing. A device that
        // exposes protection without a readable Cooling snapshot still keeps its threshold usable.
        requestStatusWithRetry {
            modules.cooling.requestStatus(uid)
        }
        Unit
    }

    override suspend fun updateLightProtectionThreshold(
        deviceUid: String,
        thresholdCelsius: Int
    ): Result<Unit> = runCatching {
        val uid = requireRegisteredDeviceUid(deviceUid)
        val modules = requireRuntimeModules()
        check(devicesRepository.currentDevice(uid).supportsLightProtectionSettings()) {
            "Temperature protection is not available for this device."
        }
        val currentStatus = checkNotNull(
            modules.lightTemperatureProtection.currentStatus(uid)
        ) {
            "Temperature protection status has not been loaded."
        }
        val policy = checkNotNull(currentStatus.toThresholdPolicy()) {
            "Temperature protection threshold is not editable."
        }
        require(thresholdCelsius in policy.minimumCelsius..policy.maximumCelsius) {
            "Temperature protection threshold is outside the device range."
        }
        require(
            (thresholdCelsius - policy.minimumCelsius) % policy.stepCelsius == 0
        ) {
            "Temperature protection threshold does not match the device step."
        }

        val result = modules.lightTemperatureProtection.setThreshold(
            deviceUid = uid,
            payload = DeviceLightTemperatureProtectionSetPayload(
                thresholdC = thresholdCelsius.toDouble(),
                save = true
            )
        ).requireSuccessValue()

        check(result.saved) {
            "Temperature protection threshold was not persisted by firmware."
        }
        check(
            firmwareThresholdMatchesRequest(
                returnedThresholdCelsius = result.status.temperatureProtection.thresholdC,
                requestedThresholdCelsius = thresholdCelsius
            )
        ) {
            "Firmware returned a different temperature protection threshold."
        }
        Unit
    }

    private fun requireRegisteredDeviceUid(deviceUid: String): DeviceUid {
        val uid = checkNotNull(deviceUid.toDeviceUidOrNull()) {
            "Device uid is missing."
        }
        checkNotNull(devicesRepository.currentDevice(uid)) {
            "Device is not registered."
        }
        return uid
    }

    private fun requireRuntimeModules(): DeviceRuntimeModuleProvider = checkNotNull(
        devicesRepository.runtimeModules()
    ) {
        "Device runtime is not configured."
    }
}

internal fun toDeviceLightProtectionSnapshot(
    available: Boolean,
    coolingState: DeviceCoolingRuntimeState?,
    protectionStatus: DeviceLightTemperatureProtectionStatus?
): DeviceLightProtectionSnapshot {
    if (!available) return DeviceLightProtectionSnapshot()

    val temperature = coolingState
        ?.temperature
        ?.takeIf { reading -> reading.readingValid }
        ?.temperatureC
        ?.takeIf(Double::isFinite)
    val protection = protectionStatus?.temperatureProtection
    return DeviceLightProtectionSnapshot(
        available = true,
        currentTemperatureCelsius = temperature,
        thresholdCelsius = protection?.thresholdC?.takeIf(Double::isFinite),
        thresholdPolicy = protectionStatus.toThresholdPolicy(),
        loaded = protectionStatus != null
    )
}

private fun DeviceSnapshot?.supportsLightProtectionSettings(): Boolean {
    val validation = this?.let(AqlCommercialDeviceCatalog::validateSnapshot)
    val product = (validation as? AqlCommercialCatalogValidation.Valid)?.product
    return product != null &&
        product.limits.temperatureSensorCount > 0 &&
        AqlDeviceFeatureKey.LIGHT_TEMPERATURE_PROTECTION in
        product.profile.supportedFeatures &&
        AqlDeviceScreenKey.LIGHT_TEMPERATURE_PROTECTION in
        product.profile.supportedScreens
}

private fun DeviceLightTemperatureProtectionStatus?.toThresholdPolicy():
    DeviceLightProtectionThresholdPolicy? {
    val protection = this?.temperatureProtection
    val threshold = protection?.thresholdC.toExactIntOrNull()
    val minimum = protection?.minimumC.toExactIntOrNull()
    val maximum = protection?.maximumC.toExactIntOrNull()
    val runtimeEditable = this?.runtime?.let { runtime ->
        !runtime.readOnly && runtime.supportsSet
    } == true
    val statusEditable = this?.supported == true &&
        protection?.supported == true &&
        protection.thresholdEditable
    val orderedRange = if (threshold != null && minimum != null && maximum != null) {
        minimum <= threshold && threshold <= maximum
    } else {
        false
    }

    return if (runtimeEditable && statusEditable && orderedRange) {
        DeviceLightProtectionThresholdPolicy(
            currentCelsius = checkNotNull(threshold),
            minimumCelsius = checkNotNull(minimum),
            maximumCelsius = checkNotNull(maximum),
            stepCelsius = TEMPERATURE_THRESHOLD_STEP_CELSIUS
        )
    } else {
        null
    }
}

private suspend fun <T> requestStatusWithRetry(
    request: suspend () -> DeviceRuntimeCommandOutcome<T>
): DeviceRuntimeCommandOutcome<T> {
    var outcome = request()
    repeat(STATUS_REQUEST_MAX_ATTEMPTS - 1) {
        if (!outcome.isTransientReadFailure()) return outcome
        delay(STATUS_REQUEST_RETRY_DELAY_MILLIS)
        outcome = request()
    }
    return outcome
}

private fun DeviceRuntimeCommandOutcome<*>.isTransientReadFailure(): Boolean = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated,
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled -> true
    is DeviceRuntimeCommandOutcome.Success,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
    is DeviceRuntimeCommandOutcome.FirmwareError,
    is DeviceRuntimeCommandOutcome.ProtocolError -> false
}

private fun <T> DeviceRuntimeCommandOutcome<T>.requireSuccessValue(): T = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> value
    else -> error("Device runtime request failed: ${javaClass.simpleName}")
}

private fun String.toDeviceUidOrNull(): DeviceUid? = trim()
    .takeIf(String::isNotBlank)
    ?.let(::DeviceUid)

private fun Double?.toExactIntOrNull(): Int? = this
    ?.takeIf(Double::isFinite)
    ?.let { value ->
        value.roundToInt().takeIf { rounded ->
            abs(value - rounded.toDouble()) <= TEMPERATURE_EPSILON
        }
    }

internal fun firmwareThresholdMatchesRequest(
    returnedThresholdCelsius: Double?,
    requestedThresholdCelsius: Int
): Boolean = returnedThresholdCelsius != null &&
    returnedThresholdCelsius.isFinite() &&
    abs(returnedThresholdCelsius - requestedThresholdCelsius.toDouble()) <= TEMPERATURE_EPSILON

private const val STATUS_REQUEST_MAX_ATTEMPTS = 8
private const val STATUS_REQUEST_RETRY_DELAY_MILLIS = 250L
private const val TEMPERATURE_THRESHOLD_STEP_CELSIUS = 1
private const val TEMPERATURE_EPSILON = 0.000_001
