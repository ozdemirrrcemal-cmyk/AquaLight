package com.aqua.aqualight.data.devices.runtime.modules.firmware

/**
 * Cross-artifact and device-bound limits that complement the exact JSON parser.
 *
 * These checks run before signature acceptance returns a manifest to the planner. They intentionally
 * duplicate critical firmware publication invariants so a malformed signed catalog fails closed.
 */
internal object DeviceFirmwareManifestContractValidator {

    fun validate(manifest: DeviceFirmwareManifest): DeviceFirmwareManifest {
        require(
            manifest.artifacts
                .map(DeviceFirmwareManifestArtifact::env)
                .distinct()
                .size == manifest.artifacts.size
        ) {
            "OTA channel manifest contains duplicate product environments."
        }

        manifest.artifacts.forEach { artifact ->
            require(artifact.product.capabilities.ota) {
                "OTA artifact product does not authorize OTA: ${artifact.env}"
            }
            require(
                artifact.firmware.url.length <=
                    DeviceFirmwareRuntimeContract.Limit.MAX_URL_LENGTH
            ) {
                "OTA firmware URL exceeds the device limit: ${artifact.env}"
            }
        }

        return manifest
    }
}
