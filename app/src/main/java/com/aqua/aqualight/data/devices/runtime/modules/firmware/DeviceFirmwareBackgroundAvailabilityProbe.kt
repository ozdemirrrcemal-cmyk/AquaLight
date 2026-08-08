package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import java.util.Locale

/**
 * Signed-manifest probe for periodic background discovery.
 *
 * This class intentionally produces only a non-installable hint from durable device metadata.
 * The foreground OTA coordinator must still authenticate the live device and rebuild an exact
 * [DeviceFirmwareUpdatePlan] before installation can start.
 */
class DeviceFirmwareBackgroundAvailabilityProbe(
    private val manifestSource: DeviceFirmwareManifestHttpSource =
        DeviceFirmwareManifestHttpSource(),
    private val planner: DeviceFirmwareUpdatePlanner = DeviceFirmwareUpdatePlanner()
) {

    suspend fun loadManifest(manifestUrl: String): Result<DeviceFirmwareManifest> {
        return manifestSource.load(manifestUrl)
    }

    fun evaluate(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest
    ): Result<DeviceFirmwareAvailabilityHint> = runCatching {
        validateDurableSnapshot(snapshot)
        validateManifest(manifest)

        val compatibleArtifacts = planner.compatibleArtifacts(snapshot, manifest)
        require(compatibleArtifacts.size <= 1) {
            "Ambiguous OTA manifest: ${compatibleArtifacts.size} artifacts match durable metadata."
        }

        val currentVersion = snapshot.firmwareVersion
        val artifact = compatibleArtifacts.singleOrNull()
            ?: return@runCatching DeviceFirmwareAvailabilityHint.UpToDate(
                deviceUid = snapshot.deviceUid.value,
                deviceName = snapshot.title,
                currentVersion = currentVersion,
                targetVersion = currentVersion
            )
        validateArtifact(snapshot, manifest, artifact)
        val targetVersion = artifact.firmware.version

        if (DeviceFirmwareVersionComparator.compare(targetVersion, currentVersion) <= 0) {
            DeviceFirmwareAvailabilityHint.UpToDate(
                deviceUid = snapshot.deviceUid.value,
                deviceName = snapshot.title,
                currentVersion = currentVersion,
                targetVersion = targetVersion
            )
        } else {
            DeviceFirmwareAvailabilityHint.UpdateAvailable(
                deviceUid = snapshot.deviceUid.value,
                deviceName = snapshot.title,
                currentVersion = currentVersion,
                targetVersion = targetVersion
            )
        }
    }

    private fun validateDurableSnapshot(snapshot: DeviceSnapshot) {
        val product = snapshot.product
        require(snapshot.capabilities.ota) {
            "Durable device metadata does not advertise OTA support."
        }
        require(product.brand == DeviceFirmwareRuntimeContract.Manifest.BRAND) {
            "Durable product brand is not AquaLight."
        }
        require(product.productKey.isNotBlank()) { "Durable productKey is missing." }
        require(product.productId.isNotBlank()) { "Durable productId is missing." }
        require(product.family.wireValue.isNotBlank()) { "Durable family is missing." }
        require(product.line.isNotBlank()) { "Durable product line is missing." }
        require(product.model.isNotBlank()) { "Durable product model is missing." }
        require(product.displayName.isNotBlank()) { "Durable product displayName is missing." }
        require(product.skuCode.isNotBlank()) { "Durable product skuCode is missing." }
        require(product.hardwareRevision.isNotBlank()) {
            "Durable hardwareRevision is missing."
        }
        require(snapshot.firmwareVersion.isNotBlank()) {
            "Durable firmware version is missing."
        }
    }

    private fun validateManifest(manifest: DeviceFirmwareManifest) {
        require(manifest.isSupportedSchema) { "Unsupported AquaLight OTA manifest." }
        require(manifest.platform == OFFICIAL_PLATFORM) {
            "OTA manifest platform differs from AquaLight-Firmware/main."
        }
        require(manifest.tag == "v${manifest.version}") {
            "OTA manifest tag does not match its exact firmware version."
        }
    }

    private fun validateArtifact(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest,
        artifact: DeviceFirmwareManifestArtifact
    ) {
        val product = snapshot.product
        val expectedEnvironment = product.productKey.lowercase(Locale.ROOT)

        require(artifact.env == expectedEnvironment)
        require(artifact.product.productKey == product.productKey)
        require(artifact.product.productId == product.productId)
        require(artifact.product.brand == product.brand)
        require(artifact.product.family == product.family.wireValue)
        require(artifact.product.line == product.line)
        require(artifact.product.model == product.model)
        require(
            artifact.product.displayName == releaseDisplayName(
                product.brand,
                product.displayName
            )
        )
        require(artifact.product.skuCode == product.skuCode)
        require(artifact.product.hardwareRevision == product.hardwareRevision)
        require(artifact.product.capabilities == snapshot.capabilities) {
            "OTA manifest capabilities differ from durable device metadata."
        }
        require(artifact.product.limits == snapshot.limits) {
            "OTA manifest limits differ from durable device metadata."
        }
        require(artifact.compatibility.productKey == product.productKey)
        require(artifact.compatibility.productId == product.productId)
        require(artifact.compatibility.family == product.family.wireValue)
        require(artifact.compatibility.line == product.line)
        require(artifact.compatibility.model == product.model)
        require(artifact.compatibility.hardwareRevision == product.hardwareRevision)
        require(artifact.firmware.version == manifest.version)
        require(
            artifact.firmware.filename ==
                "AquaLight-${artifact.env}-${manifest.tag}-ota.bin"
        )
        require(
            artifact.firmware.url == DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                "${manifest.tag}/${artifact.firmware.filename}"
        )
        require(
            artifact.firmware.format == DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT
        )
        require(artifact.firmware.otaSlotCompatible)
    }

    private fun releaseDisplayName(brand: String, displayName: String): String {
        val prefix = "$brand "
        return if (displayName.startsWith(prefix)) displayName else prefix + displayName
    }

    private companion object {
        val OFFICIAL_PLATFORM = DeviceFirmwareManifestPlatform(
            framework = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK,
            core = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE,
            platform = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE,
            partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
            normalOtaAssetType = DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
        )
    }
}

sealed interface DeviceFirmwareAvailabilityHint {
    val deviceUid: String
    val deviceName: String
    val currentVersion: String
    val targetVersion: String

    data class UpToDate(
        override val deviceUid: String,
        override val deviceName: String,
        override val currentVersion: String,
        override val targetVersion: String
    ) : DeviceFirmwareAvailabilityHint

    data class UpdateAvailable(
        override val deviceUid: String,
        override val deviceName: String,
        override val currentVersion: String,
        override val targetVersion: String
    ) : DeviceFirmwareAvailabilityHint
}
