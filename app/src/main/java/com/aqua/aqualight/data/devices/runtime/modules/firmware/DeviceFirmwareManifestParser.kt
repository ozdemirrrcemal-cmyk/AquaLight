package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceLimits
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

@Suppress("TooManyFunctions")
object DeviceFirmwareManifestParser {

    fun parse(raw: String): Result<DeviceFirmwareManifest> = runCatching {
        require(raw.isNotBlank()) { "OTA manifest must not be blank." }
        val root = JSONObject(raw)
        root.requireExactKeys(ROOT_KEYS, "manifest")
        val manifest = DeviceFirmwareManifest(
            schema = root.requiredString("schema"),
            brand = root.requiredString("brand"),
            channel = root.requiredString("channel"),
            version = root.requiredString("version"),
            tag = root.requiredString("tag"),
            releaseRepo = root.requiredString("releaseRepo"),
            generatedAt = root.requiredString("generatedAt"),
            platform = parsePlatform(root.requiredObject("platform")),
            releaseNotes = parseReleaseNotes(root.requiredObject("releaseNotes")),
            artifacts = parseArtifacts(root.requiredArray("artifacts")),
            signature = parseSignature(root.requiredObject("signature"))
        )
        validateManifest(manifest)
        manifest
    }

    private fun parsePlatform(json: JSONObject): DeviceFirmwareManifestPlatform {
        json.requireExactKeys(PLATFORM_KEYS, "platform")
        return DeviceFirmwareManifestPlatform(
            framework = json.requiredString("framework"),
            core = json.requiredString("core"),
            platform = json.requiredString("platform"),
            partitionTable = json.requiredString("partitionTable"),
            normalOtaAssetType = json.requiredString("normalOtaAssetType")
        )
    }

    private fun parseReleaseNotes(json: JSONObject): DeviceFirmwareReleaseNotes {
        json.requireExactKeys(RELEASE_NOTES_KEYS, "releaseNotes")
        val items = json.requiredArray("items").mapObjects("releaseNotes.items") { item, index ->
            item.requireExactKeys(RELEASE_NOTE_ITEM_KEYS, "releaseNotes.items[$index]")
            DeviceFirmwareReleaseNoteItem(
                tr = item.requiredReleaseNoteText("tr"),
                en = item.requiredReleaseNoteText("en")
            )
        }
        return DeviceFirmwareReleaseNotes(
            schema = json.requiredString("schema"),
            defaultLocale = json.requiredString("defaultLocale"),
            items = items
        )
    }

    private fun parseSignature(json: JSONObject): DeviceFirmwareManifestSignature {
        json.requireExactKeys(SIGNATURE_KEYS, "signature")
        return DeviceFirmwareManifestSignature(
            scheme = json.requiredString("scheme"),
            keyId = json.requiredString("keyId"),
            payloadHash = json.requiredString("payloadHash").lowercase(Locale.ROOT),
            value = json.requiredString("value")
        )
    }

    private fun parseArtifacts(array: JSONArray): List<DeviceFirmwareManifestArtifact> =
        array.mapObjects("artifacts") { json, index -> parseArtifact(json, index) }

    private fun parseArtifact(
        json: JSONObject,
        index: Int
    ): DeviceFirmwareManifestArtifact {
        val label = "artifacts[$index]"
        json.requireExactKeys(ARTIFACT_KEYS, label)
        return DeviceFirmwareManifestArtifact(
            env = json.requiredString("env"),
            product = parseProduct(json.requiredObject("product"), "$label.product"),
            compatibility = parseCompatibility(
                json.requiredObject("compatibility"),
                "$label.compatibility"
            ),
            firmware = parseFirmware(json.requiredObject("firmware"), "$label.firmware"),
            factory = json.requiredNullableObject("factory")?.let { factory ->
                parseFactory(factory, "$label.factory")
            }
        )
    }

    private fun parseProduct(
        json: JSONObject,
        label: String
    ): DeviceFirmwareManifestProduct {
        json.requireExactKeys(PRODUCT_KEYS, label)
        return DeviceFirmwareManifestProduct(
            productKey = json.requiredString("productKey"),
            productId = json.requiredString("productId"),
            brand = json.requiredString("brand"),
            family = json.requiredString("family"),
            line = json.requiredString("line"),
            model = json.requiredString("model"),
            displayName = json.requiredString("displayName"),
            skuCode = json.requiredString("skuCode"),
            hardwareRevision = json.requiredString("hardwareRevision"),
            capabilities = parseCapabilities(json.requiredObject("capabilities"), "$label.capabilities"),
            limits = parseLimits(json.requiredObject("limits"), "$label.limits")
        )
    }

    private fun parseCapabilities(json: JSONObject, label: String): DeviceCapabilities {
        json.requireExactKeys(CAPABILITY_KEYS, label)
        return DeviceCapabilities(
            light = json.requiredBoolean("light"),
            manualLight = json.requiredBoolean("manualLight"),
            lightProgram = json.requiredBoolean("lightProgram"),
            lightPresets = json.requiredBoolean("lightPresets"),
            lightSimulation = json.requiredBoolean("lightSimulation"),
            fan = json.requiredBoolean("fan"),
            cooling = json.requiredBoolean("cooling"),
            temperature = json.requiredBoolean("temperature"),
            standaloneTimer = json.requiredBoolean("standaloneTimer"),
            dosing = json.requiredBoolean("dosing"),
            timeSync = json.requiredBoolean("timeSync"),
            ota = json.requiredBoolean("ota")
        )
    }

    private fun parseLimits(json: JSONObject, label: String): DeviceLimits {
        json.requireExactKeys(LIMIT_KEYS, label)
        return DeviceLimits(
            lightChannelCount = json.requiredNonNegativeInt("lightChannelCount"),
            fanOutputCount = json.requiredNonNegativeInt("fanOutputCount"),
            temperatureSensorCount = json.requiredNonNegativeInt("temperatureSensorCount"),
            timerChannelCount = json.requiredNonNegativeInt("timerChannelCount"),
            dosingChannelCount = json.requiredNonNegativeInt("dosingChannelCount")
        )
    }

    private fun parseCompatibility(
        json: JSONObject,
        label: String
    ): DeviceFirmwareCompatibility {
        json.requireExactKeys(COMPATIBILITY_KEYS, label)
        return DeviceFirmwareCompatibility(
            productKey = json.requiredString("productKey"),
            productId = json.requiredString("productId"),
            family = json.requiredString("family"),
            line = json.requiredString("line"),
            model = json.requiredString("model"),
            hardwareRevision = json.requiredString("hardwareRevision")
        )
    }

    private fun parseFirmware(json: JSONObject, label: String): DeviceFirmwareAsset {
        json.requireExactKeys(FIRMWARE_KEYS, label)
        return DeviceFirmwareAsset(
            version = json.requiredString("version"),
            filename = json.requiredString("filename"),
            url = json.requiredString("url"),
            sha256 = json.requiredString("sha256").lowercase(Locale.ROOT),
            size = json.requiredPositiveInt("size"),
            format = json.requiredString("format"),
            otaSlotCompatible = json.requiredBoolean("otaSlotCompatible")
        )
    }

    private fun parseFactory(json: JSONObject, label: String): DeviceFirmwareFactoryAsset {
        json.requireExactKeys(FACTORY_KEYS, label)
        return DeviceFirmwareFactoryAsset(
            filename = json.requiredString("filename"),
            url = json.requiredString("url"),
            sha256 = json.requiredString("sha256").lowercase(Locale.ROOT),
            size = json.requiredPositiveInt("size")
        )
    }

    private fun validateManifest(manifest: DeviceFirmwareManifest) {
        require(manifest.isSupportedSchema) {
            "Unsupported OTA manifest schema, brand or release repository."
        }
        require(manifest.channel in SUPPORTED_CHANNELS) {
            "Unsupported OTA manifest channel: ${manifest.channel}"
        }
        require(manifest.tag == "v${manifest.version}") {
            "OTA manifest tag must be the exact v-prefixed version."
        }
        require(manifest.platform == OFFICIAL_PLATFORM) {
            "OTA manifest platform differs from AquaLight-Firmware/main."
        }
        require(manifest.artifacts.isNotEmpty()) {
            "OTA manifest does not contain any artifacts."
        }
        require(manifest.artifacts.map(DeviceFirmwareManifestArtifact::env).distinct().size ==
            manifest.artifacts.size) {
            "OTA manifest environments must be unique."
        }
        require(
            manifest.signature.scheme ==
                DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256
        ) {
            "Unsupported OTA manifest signature scheme: ${manifest.signature.scheme}"
        }
        require(manifest.signature.payloadHash.isSha256Hex()) {
            "OTA manifest signature payloadHash must be 64 hex characters."
        }
        manifest.artifacts.forEach { artifact -> validateArtifact(manifest, artifact) }
    }

    private fun validateArtifact(
        manifest: DeviceFirmwareManifest,
        artifact: DeviceFirmwareManifestArtifact
    ) {
        validateArtifactIdentity(manifest, artifact)
        validateFirmwareAsset(manifest, artifact)
        validateFactoryAsset(manifest, artifact)
    }

    private fun validateArtifactIdentity(
        manifest: DeviceFirmwareManifest,
        artifact: DeviceFirmwareManifestArtifact
    ) {
        require(ENVIRONMENT_PATTERN.matches(artifact.env)) {
            "Invalid OTA manifest environment: ${artifact.env}"
        }
        require(artifact.env == artifact.product.productKey.lowercase(Locale.ROOT)) {
            "OTA environment must equal lowercase productKey for ${artifact.env}."
        }
        require(artifact.product.productKey == artifact.compatibility.productKey) {
            "Manifest productKey mismatch for ${artifact.env}."
        }
        require(artifact.product.productId == artifact.compatibility.productId) {
            "Manifest productId mismatch for ${artifact.env}."
        }
        require(artifact.product.family == artifact.compatibility.family) {
            "Manifest family mismatch for ${artifact.env}."
        }
        require(artifact.product.line == artifact.compatibility.line) {
            "Manifest line mismatch for ${artifact.env}."
        }
        require(artifact.product.model == artifact.compatibility.model) {
            "Manifest model mismatch for ${artifact.env}."
        }
        require(artifact.product.hardwareRevision == artifact.compatibility.hardwareRevision) {
            "Manifest hardwareRevision mismatch for ${artifact.env}."
        }
        require(artifact.product.brand == manifest.brand) {
            "Manifest product brand mismatch for ${artifact.env}."
        }
        require(artifact.product.capabilities.ota) {
            "Manifest product must declare OTA capability for ${artifact.env}."
        }
    }

    private fun validateFirmwareAsset(
        manifest: DeviceFirmwareManifest,
        artifact: DeviceFirmwareManifestArtifact
    ) {
        val expected = PublishedAssetExpectation(
            filename = "AquaLight-${artifact.env}-${manifest.tag}-ota.bin",
            tag = manifest.tag,
            label = "firmware"
        )
        validatePublishedAsset(artifact.firmware.asPublishedAsset(), expected)
        require(artifact.firmware.version == manifest.version) {
            "OTA firmware version differs from manifest version for ${artifact.env}."
        }
        require(artifact.firmware.format == DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT) {
            "OTA firmware format differs from firmware release contract."
        }
        require(artifact.firmware.otaSlotCompatible) {
            "OTA firmware must be OTA-slot compatible."
        }
    }

    private fun validateFactoryAsset(
        manifest: DeviceFirmwareManifest,
        artifact: DeviceFirmwareManifestArtifact
    ) {
        val factory = artifact.factory ?: return
        val expected = PublishedAssetExpectation(
            filename = "AquaLight-${artifact.env}-${manifest.tag}-factory.zip",
            tag = manifest.tag,
            label = "factory"
        )
        validatePublishedAsset(factory.asPublishedAsset(), expected)
    }

    private fun validatePublishedAsset(
        asset: PublishedAsset,
        expected: PublishedAssetExpectation
    ) {
        require(asset.filename == expected.filename) {
            "OTA ${expected.label} filename differs from firmware release naming."
        }
        val expectedUrl = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "${expected.tag}/${expected.filename}"
        require(asset.url == expectedUrl) {
            "OTA ${expected.label} URL differs from the official release artifact URL."
        }
        require(asset.url.length <= DeviceFirmwareRuntimeContract.Limit.MAX_URL_LENGTH) {
            "OTA ${expected.label} URL exceeds the firmware limit."
        }
        require(asset.sha256.isSha256Hex()) {
            "OTA ${expected.label} sha256 must be 64 hex characters."
        }
        require(asset.size > 0) {
            "OTA ${expected.label} size must be greater than zero."
        }
    }

    private fun DeviceFirmwareAsset.asPublishedAsset(): PublishedAsset = PublishedAsset(
        filename = filename,
        url = url,
        sha256 = sha256,
        size = size
    )

    private fun DeviceFirmwareFactoryAsset.asPublishedAsset(): PublishedAsset = PublishedAsset(
        filename = filename,
        url = url,
        sha256 = sha256,
        size = size
    )

    private fun JSONObject.requiredObject(key: String): JSONObject {
        require(has(key) && !isNull(key)) { "OTA manifest object '$key' is missing." }
        return get(key) as? JSONObject ?: error("OTA manifest field '$key' must be an object.")
    }

    private fun JSONObject.requiredNullableObject(key: String): JSONObject? {
        require(has(key)) { "OTA manifest field '$key' is missing." }
        if (isNull(key)) return null
        return get(key) as? JSONObject ?: error("OTA manifest field '$key' must be an object or null.")
    }

    private fun JSONObject.requiredArray(key: String): JSONArray {
        require(has(key) && !isNull(key)) { "OTA manifest array '$key' is missing." }
        return get(key) as? JSONArray ?: error("OTA manifest field '$key' must be an array.")
    }

    private fun JSONObject.requiredString(key: String): String {
        require(has(key) && !isNull(key)) { "OTA manifest field '$key' is missing." }
        val value = get(key) as? String ?: error("OTA manifest field '$key' must be a string.")
        require(value.isNotEmpty()) { "OTA manifest field '$key' must not be empty." }
        require(!value.first().isWhitespace() && !value.last().isWhitespace()) {
            "OTA manifest field '$key' must not contain surrounding whitespace."
        }
        require(value.none(Char::isISOControl)) {
            "OTA manifest field '$key' must not contain control characters."
        }
        return value
    }

    private fun JSONObject.requiredReleaseNoteText(key: String): String {
        require(has(key) && !isNull(key)) { "OTA release note '$key' is missing." }
        val value = get(key) as? String ?: error("OTA release note '$key' must be a string.")
        require(value.isNotEmpty()) { "OTA release note '$key' must not be empty." }
        require(!value.first().isWhitespace() && !value.last().isWhitespace()) {
            "OTA release note '$key' must not contain surrounding whitespace."
        }
        require(value.none { character -> character < ' ' }) {
            "OTA release note '$key' must not contain C0 control characters."
        }
        require(value.length <= DeviceFirmwareRuntimeContract.Limit.MAX_RELEASE_NOTE_TEXT_LENGTH) {
            "OTA release note '$key' exceeds the firmware length limit."
        }
        return value
    }

    private fun JSONObject.requiredBoolean(key: String): Boolean {
        require(has(key) && !isNull(key)) { "OTA manifest field '$key' is missing." }
        return get(key) as? Boolean ?: error("OTA manifest field '$key' must be a boolean.")
    }

    private fun JSONObject.requiredPositiveInt(key: String): Int =
        requiredInteger(key, minimum = 1)

    private fun JSONObject.requiredNonNegativeInt(key: String): Int =
        requiredInteger(key, minimum = 0)

    private fun JSONObject.requiredInteger(key: String, minimum: Int): Int {
        require(has(key) && !isNull(key)) { "OTA manifest field '$key' is missing." }
        val value = get(key) as? Number
            ?: error("OTA manifest field '$key' must be an integer.")
        val asLong = value.toLong()
        require(value.toDouble().isFinite() && value.toDouble() == asLong.toDouble()) {
            "OTA manifest field '$key' must be an integer."
        }
        require(asLong in minimum.toLong()..Int.MAX_VALUE.toLong()) {
            "OTA manifest field '$key' is outside Android/firmware supported range."
        }
        return asLong.toInt()
    }

    private inline fun <T> JSONArray.mapObjects(
        label: String,
        transform: (JSONObject, Int) -> T
    ): List<T> {
        val source = this
        return buildList {
            repeat(source.length()) { index ->
                val item = source.get(index) as? JSONObject
                    ?: error("OTA manifest $label[$index] must be an object.")
                add(transform(item, index))
            }
        }
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
        val actual = buildSet {
            val iterator = keys()
            while (iterator.hasNext()) add(iterator.next())
        }
        val missing = expected - actual
        val unknown = actual - expected
        require(missing.isEmpty() && unknown.isEmpty()) {
            "$label keys differ from the signed OTA manifest contract; " +
                "missing=${missing.sorted()} unknown=${unknown.sorted()}"
        }
    }

    private data class PublishedAsset(
        val filename: String,
        val url: String,
        val sha256: String,
        val size: Int
    )

    private data class PublishedAssetExpectation(
        val filename: String,
        val tag: String,
        val label: String
    )

    private val ROOT_KEYS = setOf(
        "schema",
        "brand",
        "channel",
        "version",
        "tag",
        "releaseRepo",
        "generatedAt",
        "platform",
        "releaseNotes",
        "artifacts",
        "signature"
    )
    private val PLATFORM_KEYS = setOf(
        "framework", "core", "platform", "partitionTable", "normalOtaAssetType"
    )
    private val RELEASE_NOTES_KEYS = setOf("schema", "defaultLocale", "items")
    private val RELEASE_NOTE_ITEM_KEYS = setOf("tr", "en")
    private val SIGNATURE_KEYS = setOf("scheme", "keyId", "payloadHash", "value")
    private val ARTIFACT_KEYS = setOf("env", "product", "compatibility", "firmware", "factory")
    private val PRODUCT_KEYS = setOf(
        "productKey",
        "productId",
        "brand",
        "family",
        "line",
        "model",
        "displayName",
        "skuCode",
        "hardwareRevision",
        "capabilities",
        "limits"
    )
    private val CAPABILITY_KEYS = setOf(
        "light",
        "manualLight",
        "lightProgram",
        "lightPresets",
        "lightSimulation",
        "fan",
        "cooling",
        "temperature",
        "standaloneTimer",
        "dosing",
        "timeSync",
        "ota"
    )
    private val LIMIT_KEYS = setOf(
        "lightChannelCount",
        "fanOutputCount",
        "temperatureSensorCount",
        "timerChannelCount",
        "dosingChannelCount"
    )
    private val COMPATIBILITY_KEYS = setOf(
        "productKey", "productId", "family", "line", "model", "hardwareRevision"
    )
    private val FIRMWARE_KEYS = setOf(
        "version", "filename", "url", "sha256", "size", "format", "otaSlotCompatible"
    )
    private val FACTORY_KEYS = setOf("filename", "url", "sha256", "size")
    private val SUPPORTED_CHANNELS = setOf(
        DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
        DeviceFirmwareRuntimeContract.Manifest.BETA_CHANNEL,
        DeviceFirmwareRuntimeContract.Manifest.DEV_CHANNEL
    )
    private val OFFICIAL_PLATFORM = DeviceFirmwareManifestPlatform(
        framework = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK,
        core = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE,
        platform = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE,
        partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
        normalOtaAssetType = DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
    )
    private val ENVIRONMENT_PATTERN = Regex("^[a-z0-9_]+$")
}
