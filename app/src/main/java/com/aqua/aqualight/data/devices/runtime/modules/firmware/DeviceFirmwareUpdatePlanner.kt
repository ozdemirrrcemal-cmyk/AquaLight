package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceSnapshot

class DeviceFirmwareUpdatePlanner {

    fun planUpdate(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareUpdatePlan> {
        return runCatching {
            require(manifest.isSupportedSchema) {
                "Unsupported AquaLight OTA manifest."
            }
            require(snapshot.capabilities.ota) {
                "Device does not report OTA capability."
            }

            val currentVersion = snapshot.firmwareVersion.trim()
            require(currentVersion.isNotBlank()) {
                "Current firmware version is not known. Read firmware.status.get before OTA check."
            }
            require(manifest.version.isNotBlank()) {
                "Manifest firmware version is missing."
            }

            val productKey = snapshot.product.productKey.trim()
            val productId = snapshot.product.productId.trim()
            val productModel = snapshot.product.model.trim()
            val hardwareRevision = snapshot.product.hardwareRevision.trim()

            require(productKey.isNotBlank()) { "Device productKey is missing." }
            require(productId.isNotBlank()) { "Device productId is missing." }
            require(productModel.isNotBlank()) { "Device product model is missing." }
            require(hardwareRevision.isNotBlank()) { "Device hardwareRevision is missing." }

            val compatibleArtifacts = compatibleArtifacts(snapshot = snapshot, manifest = manifest)

            require(compatibleArtifacts.isNotEmpty()) {
                "No compatible OTA artifact found for $productKey / $productModel / hw $hardwareRevision."
            }

            require(DeviceFirmwareVersionComparator.compare(manifest.version, currentVersion) > 0) {
                "No newer compatible OTA artifact found. Current=$currentVersion manifest=${manifest.version}"
            }

            val artifact = compatibleArtifacts.first()
            validateArtifactAgainstManifest(artifact = artifact, manifest = manifest)

            val payload = DeviceFirmwareOtaStartPayload(
                url = artifact.firmware.url,
                version = manifest.version,
                sha256 = artifact.firmware.sha256,
                expectedSize = artifact.firmware.size,
                productKey = productKey,
                productId = productId,
                hardwareRevision = hardwareRevision,
                applyNow = applyNow,
                allowInsecureHttp = false
            )

            DeviceFirmwareUpdatePlan(
                deviceUid = snapshot.deviceUid,
                currentVersion = currentVersion,
                targetVersion = payload.version,
                channel = manifest.channel,
                env = artifact.env,
                productKey = productKey,
                productId = productId,
                model = productModel,
                hardwareRevision = hardwareRevision,
                displayName = snapshot.title,
                firmware = artifact.firmware,
                payload = payload
            )
        }
    }

    fun compatibleArtifacts(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest
    ): List<DeviceFirmwareManifestArtifact> {
        val productKey = snapshot.product.productKey.trim()
        val productId = snapshot.product.productId.trim()
        val model = snapshot.product.model.trim()
        val hardwareRevision = snapshot.product.hardwareRevision.trim()

        if (productKey.isBlank() || productId.isBlank() || model.isBlank() || hardwareRevision.isBlank()) {
            return emptyList()
        }

        return manifest.artifacts.filter { artifact ->
            artifact.compatibility.productKey == productKey &&
                artifact.compatibility.productId == productId &&
                artifact.compatibility.model == model &&
                artifact.compatibility.hardwareRevision == hardwareRevision
        }
    }

    private fun validateArtifactAgainstManifest(
        artifact: DeviceFirmwareManifestArtifact,
        manifest: DeviceFirmwareManifest
    ) {
        require(manifest.tag.isNotBlank()) {
            "OTA manifest tag is missing."
        }
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
