package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
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
    ): Result<DeviceFirmwareAvailability> {
        return runCatching {
            val product = requireValidatedProduct(snapshot)
            require(manifest.isSupportedSchema) {
                "Unsupported AquaLight OTA manifest."
            }
            require(product.profile.capabilities.ota) {
                "Device catalog does not authorize OTA."
            }

            val currentVersion = snapshot.firmwareVersion
            require(currentVersion.isNotBlank()) {
                "Current firmware version is not known."
            }
            require(manifest.version.isNotBlank()) {
                "Manifest firmware version is missing."
            }
            require(manifest.tag == normalizedReleaseTag(manifest.version)) {
                "OTA manifest tag does not match its firmware version."
            }

            val artifact = exactSingleArtifact(snapshot, manifest)
            validateArtifactAgainstManifest(artifact, manifest, product)
            val releaseContent = manifest.releaseNotes.resolve(preferredLocaleTags())

            if (DeviceFirmwareVersionComparator.compare(manifest.version, currentVersion) <= 0) {
                DeviceFirmwareAvailability.UpToDate(
                    currentVersion = currentVersion,
                    latestVersion = manifest.version,
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

    private fun requireValidatedProduct(snapshot: DeviceSnapshot): AqlCommercialCatalogProduct {
        require(snapshot.hasValidatedRuntimeMetadata) {
            "OTA requires current authenticated runtime metadata."
        }
        return when (val validation = AqlCommercialDeviceCatalog.validateSnapshot(snapshot)) {
            is AqlCommercialCatalogValidation.Valid -> validation.product
            is AqlCommercialCatalogValidation.Invalid -> error(
                "OTA catalog validation failed: " +
                    "${validation.failure.code}:${validation.failure.field}"
            )
        }
    }

    private fun exactSingleArtifact(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest
    ): DeviceFirmwareManifestArtifact {
        val compatible = compatibleArtifacts(snapshot, manifest)
        require(compatible.isNotEmpty()) {
            "No compatible OTA artifact found for " +
                "${snapshot.product.productKey}/${snapshot.product.model}/" +
                "hw ${snapshot.product.hardwareRevision}."
        }
        require(compatible.size == 1) {
            "Ambiguous OTA manifest: ${compatible.size} artifacts match the exact device identity."
        }
        return compatible.single()
    }

    private fun createPlan(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest,
        artifact: DeviceFirmwareManifestArtifact,
        releaseContent: com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent,
        applyNow: Boolean
    ): DeviceFirmwareUpdatePlan {
        val payload = DeviceFirmwareOtaStartPayload(
            url = artifact.firmware.url,
            version = manifest.version,
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

    private fun validateArtifactAgainstManifest(
        artifact: DeviceFirmwareManifestArtifact,
        manifest: DeviceFirmwareManifest,
        product: AqlCommercialCatalogProduct
    ) {
        val expectedEnvironment = product.productKey.value.lowercase(Locale.ROOT)
        require(artifact.env == expectedEnvironment) {
            "OTA artifact environment does not match the exact catalog product."
        }
        require(artifact.product.productKey == product.productKey.value)
        require(artifact.product.productId == product.productId.value)
        require(artifact.product.brand == DeviceFirmwareRuntimeContract.Manifest.BRAND)
        require(artifact.product.family == product.family.wireValue)
        require(artifact.product.line == product.line.value)
        require(artifact.product.model == product.model.value)
        require(artifact.product.skuCode == product.skuCode.value)
        require(artifact.product.hardwareRevision == product.hardwareRevision.value)
        require(artifact.compatibility.family == product.family.wireValue)
        require(artifact.compatibility.line == product.line.value)
        require(manifest.tag.isNotBlank()) { "OTA manifest tag is missing." }
        require(artifact.firmware.filename == "AquaLight-${artifact.env}-${manifest.tag}-ota.bin") {
            "OTA artifact filename does not match env/tag contract."
        }
        require(artifact.firmware.url.endsWith("/${artifact.firmware.filename}")) {
            "OTA artifact URL does not end with its filename."
        }
        require(artifact.firmware.url.contains("/releases/download/${manifest.tag}/")) {
            "OTA artifact URL does not contain the manifest release tag."
        }
        require(artifact.firmware.otaSlotCompatible) {
            "OTA artifact is not marked as OTA slot compatible."
        }
    }

    private fun normalizedReleaseTag(version: String): String {
        val normalized = version
            .substringBefore('-')
            .substringBefore('+')
            .removePrefix("v")
        require(normalized.isNotBlank()) { "OTA version cannot produce a release tag." }
        return "v$normalized"
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
