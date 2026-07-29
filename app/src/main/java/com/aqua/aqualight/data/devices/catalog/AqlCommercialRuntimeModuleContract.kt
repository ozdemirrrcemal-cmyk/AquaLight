package com.aqua.aqualight.data.devices.catalog

import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModules

/** Exact build-time module profile expected from each commercial catalog product. */
internal fun AqlCommercialCatalogProduct.expectedRuntimeModules(): DeviceRuntimeModules {
    val profileCapabilities = profile.capabilities
    val standaloneTimer = family == DeviceFamily.TIMER && profileCapabilities.standaloneTimer
    val dosingProduct = family == DeviceFamily.DOSING && profileCapabilities.dosing
    return DeviceRuntimeModules(
        light = family == DeviceFamily.LIGHT && profileCapabilities.light,
        cooling = profileCapabilities.cooling,
        temperature = profileCapabilities.temperature,
        timerApi = standaloneTimer,
        timerEngine = standaloneTimer || dosingProduct,
        dosing = dosingProduct,
        network = true,
        discovery = true,
        firmware = true,
        system = true
    )
}
