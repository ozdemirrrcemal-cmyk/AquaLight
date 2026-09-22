package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import java.util.Locale

@Suppress("ComplexCondition", "ReturnCount")
class DeviceFirmwareUpdatePlanner(
    private val preferredLocaleTags: () -> List<String> = {
        listOf(Locale.getDefault().toLanguageTag())
    }
) {

    fun evaluateUpdate(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareAvailability> = runCatching {
        requireValidatedSnapshot(snapshot)
        evaluateTrustedIdentity(
            identity = snapshot.toMaintenanceIdentity(),
            manifest = manifest,
            applyNow = applyNow,
            runtimeMetadataGeneration = snapshot.runtimeMetadataGeneration
        )
    }

    /**
     * Commercial rescue-plane admission.
     *
     * This path intentionally does not depend on domain capabilities, screens, modules or a
     * successful domain bootstrap. The authenticated firmware.status.get response is the current
     * device authority for exact product/hardware identity and current firmware version.
     */
    fun evaluateMaintenanceUpdate(
        identity: DeviceFirmwareMaintenanceIdentity,
        manifest: DeviceFirmwareManifest,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareAvailability> = runCatching {
        evaluateTrustedIdentity(
            identity = identity,
            manifest = manifest,
            applyNow = applyNow,
            runtimeMetadataGeneration = 0L
        )
    }

    fun planUpdate(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareUpdatePlan> = evaluateUpdate(snapshot, manifest, applyNow)
        .toUpdatePlanResult()

    fun planMaintenanceUpdate(
        identity: DeviceFirmwareMaintenanceIdentity,
        manifest: DeviceFirmwareManifest,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareUpdatePlan> = evaluateMaintenanceUpdate(identity, manifest, applyNow)
        .toUpdatePlanResult()

    private fun evaluateTrustedIdentity(
        identity: DeviceFirmwareMaintenanceIdentity,
        manifest: DeviceFirmwareManifest,
        applyNow: Boolean,
        runtimeMetadataGeneration: Long
    ): DeviceFirmwareAvailability {
        validateManifestEnvelope(manifest)

        val artifact = manifest.artifacts.single()
        validateArtifactAgainstIdentity(artifact, manifest, identity)
        val releaseContent = manifest.releaseNotes
            .resolve(preferredLocaleTags())
            .copy(mandatory = artifact.updatePolicy.isRequired)

        return if (
            DeviceFirmwareVersionComparator.compare(
                artifact.firmware.version,
                identity.currentVersion
            ) <= 0
        ) {
            DeviceFirmwareAvailability.UpToDate(
                currentVersion = identity.currentVersion,
                latestVersion = artifact.firmware.version,
                releaseContent = releaseContent
            )
        } else {
            DeviceFirmwareContractRegistry.requireTargetCompatible(
                artifact.contracts,
                identity.family
            )
            DeviceFirmwareAvailability.UpdateAvailable(
                createPlan(
                    identity = identity,
                    manifest = manifest,
                    artifact = artifact,
                    releaseContent = releaseContent,
                    applyNow = applyNow,
                    runtimeMetadataGeneration = runtimeMetadataGeneration
                )
            )
        }
    }

    private fun validateManifestEnvelope(manifest: DeviceFirmwareManifest) {
        require(manifest.isSupportedSchema) { "Unsupported AquaLight OTA manifest." }
        require(manifest.platform == OFFICIAL_PLATFORM) {
            "OTA manifest platform differs from AquaLight-Firmware/main."
        }
        require(manifest.artifacts.size == 1) {
            "Product-scoped OTA manifest must contain exactly one artifact."
        }
        require(manifest.hasExpectedReleaseTag()) {
            "OTA manifest tag must be <env>-v<version> for its single product."
        }
    }

    private fun requireValidatedSnapshot(snapshot: DeviceSnapshot) {
        require(snapshot.hasValidatedRuntimeMetadata) {
            "Normal OTA planning requires current authenticated runtime metadata."
        }
        require(snapshot.capabilities.ota) { "Authenticated firmware metadata does not authorize OTA." }
        require(snapshot.product.brand == DeviceFirmwareRuntimeContract.Manifest.BRAND) {
            "Authenticated product brand is not AquaLight."
        }
        require(snapshot.product.productKey.isNotBlank()) { "Authenticated productKey is missing." }
        require(snapshot.product.productId.isNotBlank()) { "Authenticated productId is missing." }
        require(snapshot.product.family.wireValue.isNotBlank()) { "Authenticated family is missing." }
        require(snapshot.product.model.isNotBlank()) { "Authenticated product model is missing." }
        require(snapshot.product.displayName.isNotBlank()) {
            "Authenticated immutable product displayName is missing."
        }
        require(snapshot.product.skuCode.isNotBlank()) { "Authenticated product skuCode is missing." }
        require(snapshot.product.hardwareRevision.isNotBlank()) {
            "Authenticated hardwareRevision is missing."
        }
        require(snapshot.firmwareVersion.isNotBlank()) { "Current firmware version is not known." }
    }

    private fun DeviceSnapshot.toMaintenanceIdentity() = DeviceFirmwareMaintenanceIdentity(
        deviceUid = deviceUid,
        currentVersion = firmwareVersion,
        productKey = product.productKey,
        productId = product.productId,
        family = product.family,
        model = product.model,
        hardwareRevision = product.hardwareRevision,
        displayName = product.displayName,
        skuCode = product.skuCode
    )

    private fun createPlan(
        identity: DeviceFirmwareMaintenanceIdentity,
        manifest: DeviceFirmwareManifest,
        artifact: DeviceFirmwareManifestArtifact,
        releaseContent: DeviceFirmwareReleaseContent,
        applyNow: Boolean,
        runtimeMetadataGeneration: Long
    ): DeviceFirmwareUpdatePlan {
        val payload = DeviceFirmwareOtaStartPayload(
            url = artifact.firmware.url,
            version = artifact.firmware.version,
            sha256 = artifact.firmware.sha256,
            expectedSize = artifact.firmware.size,
            productKey = identity.productKey,
            productId = identity.productId,
            model = identity.model,
            hardwareRevision = identity.hardwareRevision,
            applyNow = applyNow,
            allowInsecureHttp = false
        )
        return DeviceFirmwareUpdatePlan(
            deviceUid = identity.deviceUid,
            currentVersion = identity.currentVersion,
            targetVersion = payload.version,
            channel = manifest.channel,
            env = artifact.env,
            productKey = payload.productKey,
            productId = payload.productId,
            model = payload.model,
            hardwareRevision = payload.hardwareRevision,
            displayName = identity.displayName,
            firmware = artifact.firmware,
            payload = payload,
            runtimeMetadataGeneration = runtimeMetadataGeneration,
            manifestTag = manifest.tag,
            releaseContent = releaseContent,
            updatePolicy = artifact.updatePolicy
        )
    }

    private fun validateArtifactAgainstIdentity(
        artifact: DeviceFirmwareManifestArtifact,
        manifest: DeviceFirmwareManifest,
        identity: DeviceFirmwareMaintenanceIdentity
    ) {
        val expectedEnvironment = identity.productKey.lowercase(Locale.ROOT)
        require(artifact.env == expectedEnvironment) {
            "OTA artifact environment does not match authenticated productKey."
        }
        require(artifact.product.productKey == identity.productKey)
        require(artifact.product.productId == identity.productId)
        require(artifact.product.brand == DeviceFirmwareRuntimeContract.Manifest.BRAND)
        require(artifact.product.family == identity.family.wireValue)
        require(artifact.product.model == identity.model)
        require(
            artifact.product.displayName == releaseDisplayName(
                DeviceFirmwareRuntimeContract.Manifest.BRAND,
                identity.displayName
            )
        )
        require(artifact.product.skuCode == identity.skuCode)
        require(artifact.product.hardwareRevision == identity.hardwareRevision)
        require(artifact.compatibility.productKey == identity.productKey)
        require(artifact.compatibility.productId == identity.productId)
        require(artifact.compatibility.family == identity.family.wireValue)
        require(artifact.compatibility.model == identity.model)
        require(artifact.compatibility.hardwareRevision == identity.hardwareRevision)
        require(artifact.firmware.version == manifest.version) {
            "OTA artifact firmware.version differs from the manifest version."
        }
        require(artifact.firmware.filename == manifest.expectedFirmwareFilename(artifact)) {
            "OTA artifact filename does not match env/tag contract."
        }
        require(
            artifact.firmware.url == DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                "${manifest.tag}/${artifact.firmware.filename}"
        ) {
            "OTA artifact URL differs from the exact official release URL."
        }
        require(
            artifact.firmware.format == DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT
        ) {
            "OTA artifact format differs from firmware manifest contract."
        }
        require(artifact.firmware.otaSlotCompatible) {
            "OTA artifact is not marked as OTA slot compatible."
        }
    }

    private fun Result<DeviceFirmwareAvailability>.toUpdatePlanResult():
        Result<DeviceFirmwareUpdatePlan> = mapCatching { availability ->
        when (availability) {
            is DeviceFirmwareAvailability.UpdateAvailable -> availability.plan
            is DeviceFirmwareAvailability.ReleaseNotPublished -> error(
                "No official OTA release information is published for this product."
            )
            is DeviceFirmwareAvailability.UpToDate -> error(
                "No newer compatible OTA artifact found. " +
                    "Current=${availability.currentVersion} manifest=${availability.latestVersion}"
            )
        }
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

object DeviceFirmwareVersionComparator {

    fun compare(
        left: String,
        right: String
    ): Int {
        val leftParts = left.versionPartsOrNull()
            ?: error("Invalid firmware version: $left")
        val rightParts = right.versionPartsOrNull()
            ?: error("Invalid firmware version: $right")

        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val leftPart = leftParts.getOrElse(index) { 0 }
            val rightPart = rightParts.getOrElse(index) { 0 }
            if (leftPart != rightPart) {
                return leftPart.compareTo(rightPart)
            }
        }
        return 0
    }

    private fun String.versionPartsOrNull(): List<Int>? {
        val normalized = trim()
            .removePrefix("v")
            .substringBefore("-")
            .substringBefore("+")

        val parts = normalized.split(".")
        if (parts.isEmpty()) return null

        val numbers = parts.map { part ->
            part.toIntOrNull() ?: return null
        }

        return numbers.takeIf { values -> values.isNotEmpty() }
    }
}
