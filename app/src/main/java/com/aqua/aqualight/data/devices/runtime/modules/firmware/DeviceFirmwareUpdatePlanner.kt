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
        require(manifest.isSupportedSchema) { "Unsupported AquaLight OTA manifest." }
        require(manifest.platform == OFFICIAL_PLATFORM) {
            "OTA manifest platform differs from AquaLight-Firmware/main."
        }
        require(manifest.tag == "v${manifest.version}") {
            "OTA manifest tag does not match its exact firmware version."
        }

        val currentVersion = snapshot.firmwareVersion
        require(currentVersion.isNotBlank()) { "Current firmware version is not known." }

        val artifact = exactSingleArtifactOrNull(snapshot, manifest)
            ?: return@runCatching DeviceFirmwareAvailability.UpToDate(
                currentVersion = currentVersion,
                latestVersion = currentVersion,
                releaseContent = DeviceFirmwareReleaseContent.EMPTY
            )
        validateArtifactAgainstSnapshot(artifact, manifest, snapshot)
        val releaseContent = manifest.releaseNotes.resolve(preferredLocaleTags())

        if (DeviceFirmwareVersionComparator.compare(artifact.firmware.version, currentVersion) <= 0) {
            DeviceFirmwareAvailability.UpToDate(
                currentVersion = currentVersion,
                latestVersion = artifact.firmware.version,
                releaseContent = releaseContent
            )
        } else {
            DeviceFirmwareAvailability.UpdateAvailable(
                createPlan(
                    snapshot = snapshot,
                    manifest = manifest,
                    artifact = artifact,
                    releaseContent = releaseContent,
                    applyNow = applyNow
                )
            )
        }
    }

    fun planUpdate(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareUpdatePlan> = evaluateUpdate(snapshot, manifest, applyNow).mapCatching {
        availability ->
        when (availability) {
            is DeviceFirmwareAvailability.UpdateAvailable -> availability.plan
            is DeviceFirmwareAvailability.UpToDate -> error(
                "No newer compatible OTA artifact found. " +
                    "Current=${availability.currentVersion} manifest=${availability.latestVersion}"
            )
        }
    }

    fun compatibleArtifacts(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest
    ): List<DeviceFirmwareManifestArtifact> {
        val productKey = snapshot.product.productKey
        val productId = snapshot.product.productId
        val family = snapshot.product.family.wireValue
        val line = snapshot.product.line
        val model = snapshot.product.model
        val hardwareRevision = snapshot.product.hardwareRevision
        val environment = productKey.lowercase(Locale.ROOT)

        if (
            productKey.isBlank() ||
            productId.isBlank() ||
            family.isBlank() ||
            line.isBlank() ||
            model.isBlank() ||
            hardwareRevision.isBlank() ||
            environment.isBlank()
        ) {
            return emptyList()
        }

        return manifest.artifacts.filter { artifact ->
            artifact.env == environment &&
                artifact.compatibility.productKey == productKey &&
                artifact.compatibility.productId == productId &&
                artifact.compatibility.family == family &&
                artifact.compatibility.line == line &&
                artifact.compatibility.model == model &&
                artifact.compatibility.hardwareRevision == hardwareRevision
        }
    }

    private fun requireValidatedSnapshot(snapshot: DeviceSnapshot) {
        require(snapshot.hasValidatedRuntimeMetadata) {
            "OTA requires current authenticated runtime metadata."
        }
        require(snapshot.capabilities.ota) { "Authenticated firmware metadata does not authorize OTA." }
        require(snapshot.product.brand == DeviceFirmwareRuntimeContract.Manifest.BRAND) {
            "Authenticated product brand is not AquaLight."
        }
        require(snapshot.product.productKey.isNotBlank()) { "Authenticated productKey is missing." }
        require(snapshot.product.productId.isNotBlank()) { "Authenticated productId is missing." }
        require(snapshot.product.family.wireValue.isNotBlank()) { "Authenticated family is missing." }
        require(snapshot.product.line.isNotBlank()) { "Authenticated product line is missing." }
        require(snapshot.product.model.isNotBlank()) { "Authenticated product model is missing." }
        require(snapshot.product.displayName.isNotBlank()) {
            "Authenticated immutable product displayName is missing."
        }
        require(snapshot.product.skuCode.isNotBlank()) { "Authenticated product skuCode is missing." }
        require(snapshot.product.hardwareRevision.isNotBlank()) {
            "Authenticated hardwareRevision is missing."
        }
    }

    private fun exactSingleArtifactOrNull(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest
    ): DeviceFirmwareManifestArtifact? {
        val compatible = compatibleArtifacts(snapshot, manifest)
        require(compatible.size <= 1) {
            "Ambiguous OTA manifest: ${compatible.size} artifacts match the exact device identity."
        }
        return compatible.singleOrNull()
    }

    private fun createPlan(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest,
        artifact: DeviceFirmwareManifestArtifact,
        releaseContent: DeviceFirmwareReleaseContent,
        applyNow: Boolean
    ): DeviceFirmwareUpdatePlan {
        val payload = DeviceFirmwareOtaStartPayload(
            url = artifact.firmware.url,
            version = artifact.firmware.version,
            sha256 = artifact.firmware.sha256,
            expectedSize = artifact.firmware.size,
            productKey = snapshot.product.productKey,
            productId = snapshot.product.productId,
            model = snapshot.product.model,
            hardwareRevision = snapshot.product.hardwareRevision,
            applyNow = applyNow,
            allowInsecureHttp = false
        )
        return DeviceFirmwareUpdatePlan(
            deviceUid = snapshot.deviceUid,
            currentVersion = snapshot.firmwareVersion,
            targetVersion = payload.version,
            channel = manifest.channel,
            env = artifact.env,
            productKey = payload.productKey,
            productId = payload.productId,
            model = payload.model,
            hardwareRevision = payload.hardwareRevision,
            displayName = snapshot.title,
            firmware = artifact.firmware,
            payload = payload,
            runtimeMetadataGeneration = snapshot.runtimeMetadataGeneration,
            manifestTag = manifest.tag,
            releaseContent = releaseContent
        )
    }

    private fun validateArtifactAgainstSnapshot(
        artifact: DeviceFirmwareManifestArtifact,
        manifest: DeviceFirmwareManifest,
        snapshot: DeviceSnapshot
    ) {
        val product = snapshot.product
        val expectedEnvironment = product.productKey.lowercase(Locale.ROOT)
        require(artifact.env == expectedEnvironment) {
            "OTA artifact environment does not match authenticated productKey."
        }
        require(artifact.product.productKey == product.productKey)
        require(artifact.product.productId == product.productId)
        require(artifact.product.brand == product.brand)
        require(artifact.product.family == product.family.wireValue)
        require(artifact.product.line == product.line)
        require(artifact.product.model == product.model)
        require(artifact.product.displayName == releaseDisplayName(product.brand, product.displayName))
        require(artifact.product.skuCode == product.skuCode)
        require(artifact.product.hardwareRevision == product.hardwareRevision)
        require(artifact.product.capabilities == snapshot.capabilities) {
            "OTA manifest capabilities differ from authenticated firmware metadata."
        }
        require(artifact.product.limits == snapshot.limits) {
            "OTA manifest limits differ from authenticated firmware metadata."
        }
        require(artifact.compatibility.productKey == product.productKey)
        require(artifact.compatibility.productId == product.productId)
        require(artifact.compatibility.family == product.family.wireValue)
        require(artifact.compatibility.line == product.line)
        require(artifact.compatibility.model == product.model)
        require(artifact.compatibility.hardwareRevision == product.hardwareRevision)
        require(artifact.firmware.version == manifest.version) {
            "OTA artifact firmware.version differs from the manifest version."
        }
        require(artifact.firmware.filename == "AquaLight-${artifact.env}-${manifest.tag}-ota.bin") {
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
