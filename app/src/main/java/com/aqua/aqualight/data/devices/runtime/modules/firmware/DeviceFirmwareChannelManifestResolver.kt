package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareChannel
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import java.util.Locale

/** Resolves the official signed OTA channel from authenticated immutable product metadata. */
internal class DeviceFirmwareChannelManifestResolver {

    fun resolve(
        snapshot: DeviceSnapshot,
        channel: DeviceFirmwareChannel
    ): String {
        require(snapshot.hasValidatedRuntimeMetadata) {
            "OTA channel resolution requires current authenticated runtime metadata."
        }

        val productKey = snapshot.product.productKey.trim()
        require(PRODUCT_KEY_PATTERN.matches(productKey)) {
            "Authenticated productKey cannot be mapped to an OTA product channel."
        }

        val environment = productKey.lowercase(Locale.ROOT)
        return DeviceFirmwareRuntimeContract.OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX +
            "${channel.wireValue}/$environment.json"
    }

    private companion object {
        val PRODUCT_KEY_PATTERN = Regex("^[A-Z0-9]+(?:_[A-Z0-9]+)*$")
    }
}
