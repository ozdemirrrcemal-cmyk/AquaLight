package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.model.DeviceProduct
import java.util.Locale

internal data class DeviceFirmwareProductIdentity(
    val productKey: String,
    val productId: String,
    val family: String,
    val line: String,
    val model: String,
    val hardwareRevision: String
) {
    val isComplete: Boolean
        get() = listOf(
            productKey,
            productId,
            family,
            line,
            model,
            hardwareRevision
        ).all { value -> value.isNotBlank() }

    companion object {
        fun fromCatalog(product: AqlCommercialCatalogProduct) =
            DeviceFirmwareProductIdentity(
                productKey = product.productKey.value,
                productId = product.productId.value,
                family = product.family.wireValue,
                line = product.line.value,
                model = product.model.value,
                hardwareRevision = product.hardwareRevision.value
            )

        fun fromSnapshot(product: DeviceProduct) =
            DeviceFirmwareProductIdentity(
                productKey = product.productKey,
                productId = product.productId,
                family = product.family.wireValue,
                line = product.line,
                model = product.model,
                hardwareRevision = product.hardwareRevision
            )

        fun fromManifest(product: DeviceFirmwareManifestProduct) =
            DeviceFirmwareProductIdentity(
                productKey = product.productKey,
                productId = product.productId,
                family = product.family,
                line = product.line,
                model = product.model,
                hardwareRevision = product.hardwareRevision
            )

        fun fromCompatibility(compatibility: DeviceFirmwareCompatibility) =
            DeviceFirmwareProductIdentity(
                productKey = compatibility.productKey,
                productId = compatibility.productId,
                family = compatibility.family,
                line = compatibility.line,
                model = compatibility.model,
                hardwareRevision = compatibility.hardwareRevision
            )
    }
}

internal object DeviceFirmwareManifestContractValidator {

    fun requireValid(
        artifact: DeviceFirmwareManifestArtifact,
        manifest: DeviceFirmwareManifest,
        product: AqlCommercialCatalogProduct
    ) {
        requireProductIdentityMatchesCatalog(artifact, product)
        requireFirmwareIdentityMatchesManifest(artifact, manifest)
        requireReleaseChannelMatchesContract(manifest)
    }

    private fun requireProductIdentityMatchesCatalog(
        artifact: DeviceFirmwareManifestArtifact,
        product: AqlCommercialCatalogProduct
    ) {
        val catalogIdentity = DeviceFirmwareProductIdentity.fromCatalog(product)
        require(DeviceFirmwareProductIdentity.fromManifest(artifact.product) == catalogIdentity) {
            "OTA manifest product identity differs from the Android catalog."
        }
        require(
            DeviceFirmwareProductIdentity.fromCompatibility(artifact.compatibility) ==
                catalogIdentity
        ) {
            "OTA compatibility identity differs from the Android catalog."
        }
        require(artifact.env == catalogIdentity.productKey.lowercase(Locale.ROOT)) {
            "OTA artifact environment does not match the exact catalog product."
        }
    }

    private fun requireFirmwareIdentityMatchesManifest(
        artifact: DeviceFirmwareManifestArtifact,
        manifest: DeviceFirmwareManifest
    ) {
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
        require(
            artifact.firmware.format == DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT
        ) {
            "OTA artifact format does not match the release pipeline contract."
        }
        require(artifact.firmware.otaSlotCompatible) {
            "OTA artifact is not marked as OTA slot compatible."
        }
        artifact.factory?.let { factory ->
            require(factory.filename == "AquaLight-${artifact.env}-${manifest.tag}-factory.zip") {
                "Factory artifact filename does not match env/tag contract."
            }
            require(factory.url.endsWith("/${factory.filename}")) {
                "Factory artifact URL does not end with its filename."
            }
        }
    }

    private fun requireReleaseChannelMatchesContract(manifest: DeviceFirmwareManifest) {
        require(manifest.channel in SUPPORTED_CHANNELS) {
            "OTA manifest release channel is not supported."
        }
    }

    private val SUPPORTED_CHANNELS = setOf(
        DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
        DeviceFirmwareRuntimeContract.Manifest.BETA_CHANNEL,
        DeviceFirmwareRuntimeContract.Manifest.DEV_CHANNEL
    )
}
