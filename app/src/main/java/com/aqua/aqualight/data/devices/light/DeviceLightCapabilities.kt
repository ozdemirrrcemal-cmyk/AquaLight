package com.aqua.aqualight.data.devices.light

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalV1Contract

internal fun DeviceRootSnapshot.supportsLightSystem(): Boolean =
    catalogState == DeviceRootCatalogState.VALID &&
        family == OwnerDeviceFamily.LIGHT &&
        productKey == DeviceLightThermalV1Contract.PRODUCT_KEY &&
        fanOutputCount == DeviceLightThermalV1Contract.FAN_OUTPUT_CAPACITY &&
        temperatureSensorCount == DeviceLightThermalV1Contract.TEMPERATURE_SENSOR_CAPACITY &&
        LIGHT_FAN_CONTROL in supportedFeatures &&
        LIGHT_TEMPERATURE_PROTECTION in supportedFeatures

internal fun DeviceLightStatus.supportsLightAdaptation(): Boolean =
    product == DeviceLightProduct.WRGB_PRO_ELITE &&
        features.acclimation &&
        acclimation.supported &&
        policy.acclimation.supported

/**
 * Presentation capability is derived from the last validated Light runtime snapshot.
 *
 * Runtime metadata generation controls write/navigation authority separately; a transient
 * reconnect must not erase a previously validated Light presentation surface.
 */
internal fun DeviceLightStatus.supportsLightSystemPresentation(): Boolean =
    product == DeviceLightProduct.WRGB_PRO_ELITE &&
        features.fanControl &&
        features.temperatureSensor &&
        features.thermal

private const val LIGHT_FAN_CONTROL = "LIGHT_FAN_CONTROL"
private const val LIGHT_TEMPERATURE_PROTECTION = "LIGHT_TEMPERATURE_PROTECTION"
