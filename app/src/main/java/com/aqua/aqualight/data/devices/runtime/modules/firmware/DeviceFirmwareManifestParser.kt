package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.json.JSONArray
import org.json.JSONObject

@Suppress("TooManyFunctions")
object DeviceFirmwareManifestParser {

    fun parse(raw: String): Result<DeviceFirmwareManifest> = runCatching {
        val root = JSONObject(raw)
        root.requireExactKeys(ROOT_KEYS, "manifest")
        val version = root.requiredString("version")
        DeviceFirmwareManifest(
            schema = root.requiredString("schema"),
            brand = root.requiredString("brand"),
            channel = root.requiredString("channel"),
            version = version,
            tag = root.requiredString("tag"),
            releaseRepo = root.requiredString("releaseRepo"),
            generatedAt = root.requiredString("generatedAt"),
            platform = parsePlatform(root.requiredObject("platform")),
            artifacts = parseArtifacts(root.requiredArray("artifacts"), version),
            signature = parseSignature(root.requiredObject("signature")),
            releaseNotes = parseReleaseNotes(
                root.requiredObject(DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES)
            )
        ).also(::validateManifest)
    }

    private fun validateManifest(manifest: DeviceFirmwareManifest) {
        require(manifest.isSupportedSchema) {
            "Unsupported OTA manifest schema, brand or release repository."
        }
        require(manifest.channel in SUPPORTED_CHANNELS) {
            "Unsupported OTA manifest channel: ${manifest.channel}"
        }
        require(manifest.artifacts.isNotEmpty()) {
            "OTA manifest does not contain any artifacts."
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
        require(manifest.signature.value.isNotBlank()) {
            "OTA manifest signature value is missing."
        }
    }

    private fun parsePlatform(json: JSONObject): DeviceFirmwareManifestPlatform {
        json.requireExactKeys(PLATFORM_KEYS, "platform")
        return DeviceFirmwareManifestPlatform(
            framework = json.requiredString("framework"),
            core = json.requiredString("core"),
            platform = json.requiredString("platform"),
            partitionTable = json.requiredString("partitionTable"),
            normalOtaAssetType = json.requiredString("normalOtaAssetType")
        ).also { platform ->
            require(
                platform.partitionTable == DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE
            ) {
                "OTA manifest partitionTable is unsupported: ${platform.partitionTable}"
            }
            require(
                platform.normalOtaAssetType ==
                    DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
            ) {
                "OTA manifest normalOtaAssetType is unsupported: ${platform.normalOtaAssetType}"
            }
        }
    }

    private fun parseSignature(json: JSONObject): DeviceFirmwareManifestSignature {
        json.requireExactKeys(SIGNATURE_KEYS, "signature")
        return DeviceFirmwareManifestSignature(
            scheme = json.requiredString("scheme"),
            keyId = json.requiredString("keyId"),
            payloadHash = json.requiredString("payloadHash").lowercase(),
            value = json.requiredString("value")
        )
    }

    private fun parseReleaseNotes(json: JSONObject): DeviceFirmwareReleaseNotes {
        json.requireExactKeys(RELEASE_NOTES_KEYS, "releaseNotes")
        val schema = json.requiredString("schema")
        val defaultLocale = json.requiredString(
            DeviceFirmwareRuntimeContract.Manifest.DEFAULT_LOCALE
        )
        require(schema == DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES_SCHEMA) {
            "Unsupported OTA release-notes schema: $schema"
        }
        require(defaultLocale in SUPPORTED_RELEASE_NOTE_LOCALES) {
            "OTA release notes defaultLocale must be tr or en."
        }
        return DeviceFirmwareReleaseNotes(
            schema = schema,
            defaultLocale = defaultLocale,
            items = parseReleaseNoteItems(
                json.requiredArray(DeviceFirmwareRuntimeContract.Manifest.ITEMS)
            )
        )
    }

    private fun parseReleaseNoteItems(array: JSONArray): List<DeviceFirmwareReleaseNoteItem> {
        require(array.length() in 1..DeviceFirmwareRuntimeContract.Limit.MAX_RELEASE_NOTE_ITEMS) {
            "OTA release notes must contain a supported number of items."
        }
        return List(array.length()) { index ->
            val item = array.get(index) as? JSONObject
                ?: error("OTA release note item[$index] must be an object.")
            item.requireExactKeys(RELEASE_NOTE_ITEM_KEYS, "releaseNotes.items[$index]")
            DeviceFirmwareReleaseNoteItem(
                tr = item.requiredReleaseNoteText(DeviceFirmwareRuntimeContract.Manifest.TURKISH),
                en = item.requiredReleaseNoteText(DeviceFirmwareRuntimeContract.Manifest.ENGLISH)
            )
        }
    }

    private fun parseArtifacts(
        array: JSONArray,
        manifestVersion: String
    ): List<DeviceFirmwareManifestArtifact> = List(array.length()) { index ->
        val json = array.get(index) as? JSONObject
            ?: error("OTA manifest artifact[$index] must be an object.")
        parseArtifact(json, index, manifestVersion).also(::validateArtifact)
    }

    private fun parseArtifact(
        json: JSONObject,
        index: Int,
        manifestVersion: String
    ): DeviceFirmwareManifestArtifact {
        val label = "artifact[$index]"
        json.requireExactKeys(ARTIFACT_KEYS, label)
        return DeviceFirmwareManifestArtifact(
            env = json.requiredString("env"),
            product = parseProduct(json.requiredObject("product"), "$label.product"),
            compatibility = parseCompatibility(
                json.requiredObject("compatibility"),
                "$label.compatibility"
            ),
            firmware = parseFirmwareAsset(
                json.requiredObject("firmware"),
                "$label.firmware",
                manifestVersion
            ),
            factory = json.requiredNullableObject("factory")?.let { factory ->
                parseFactoryAsset(factory, "$label.factory")
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
            capabilities = parseCapabilities(
                json.requiredObject("capabilities"),
                "$label.capabilities"
            ),
            limits = parseLimits(json.requiredObject("limits"), "$label.limits")
        )
    }

    private fun parseCapabilities(
        json: JSONObject,
        label: String
    ): DeviceFirmwareManifestCapabilities {
        json.requireExactKeys(CAPABILITY_KEYS, label)
        return DeviceFirmwareManifestCapabilities(
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

    private fun parseLimits(
        json: JSONObject,
        label: String
    ): DeviceFirmwareManifestLimits {
        json.requireExactKeys(LIMIT_KEYS, label)
        return DeviceFirmwareManifestLimits(
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

    private fun parseFirmwareAsset(
        json: JSONObject,
        label: String,
        manifestVersion: String
    ): DeviceFirmwareAsset {
        json.requireExactKeys(FIRMWARE_ASSET_KEYS, label)
        val version = json.requiredString("version")
        require(version == manifestVersion) {
            "OTA firmware version does not match the manifest version."
        }
        return DeviceFirmwareAsset(
            version = version,
            filename = json.requiredString("filename"),
            url = json.requiredString("url"),
            sha256 = json.requiredString("sha256").lowercase(),
            size = json.requiredPositiveInt("size"),
            format = json.requiredString("format"),
            otaSlotCompatible = json.requiredBoolean("otaSlotCompatible")
        )
    }

    private fun parseFactoryAsset(
        json: JSONObject,
        label: String
    ): DeviceFirmwareFactoryAsset {
        json.requireExactKeys(FACTORY_ASSET_KEYS, label)
        return DeviceFirmwareFactoryAsset(
            filename = json.requiredString("filename"),
            url = json.requiredString("url"),
            sha256 = json.requiredString("sha256").lowercase(),
            size = json.requiredPositiveInt("size")
        )
    }

    @Suppress("ComplexCondition")
    private fun validateArtifact(artifact: DeviceFirmwareManifestArtifact) {
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
        require(artifact.product.brand == DeviceFirmwareRuntimeContract.Manifest.BRAND) {
            "Manifest product brand mismatch for ${artifact.env}."
        }
        validateFirmwareAsset(artifact.firmware)
        artifact.factory?.let(::validateFactoryAsset)
    }

    private fun validateFirmwareAsset(asset: DeviceFirmwareAsset) {
        require(asset.version.isNotBlank()) { "OTA firmware version is missing." }
        requireOfficialAssetUrl(asset.url, "OTA firmware")
        require(asset.filename.endsWith("-ota.bin")) {
            "OTA firmware filename must end with -ota.bin."
        }
        require(asset.url.endsWith("/${asset.filename}")) {
            "OTA firmware URL must end with its filename."
        }
        require(asset.sha256.isSha256Hex()) {
            "OTA firmware sha256 must be 64 hex characters."
        }
        require(asset.format == DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT) {
            "OTA firmware format is unsupported: ${asset.format}"
        }
        require(asset.otaSlotCompatible) {
            "OTA firmware must be marked as OTA-slot compatible."
        }
    }

    private fun validateFactoryAsset(asset: DeviceFirmwareFactoryAsset) {
        requireOfficialAssetUrl(asset.url, "Factory asset")
        require(asset.filename.endsWith("-factory.zip")) {
            "Factory asset filename must end with -factory.zip."
        }
        require(asset.url.endsWith("/${asset.filename}")) {
            "Factory asset URL must end with its filename."
        }
        require(asset.sha256.isSha256Hex()) {
            "Factory asset sha256 must be 64 hex characters."
        }
    }

    private fun requireOfficialAssetUrl(url: String, label: String) {
        require(url.startsWith(DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX)) {
            "$label URL must target the official AquaLight release repository."
        }
        require(url.startsWith("https://")) {
            "$label URL must use the secure scheme."
        }
    }

    private fun JSONObject.requiredObject(key: String): JSONObject {
        require(has(key) && !isNull(key)) { "OTA manifest object '$key' is missing." }
        return get(key) as? JSONObject
            ?: error("OTA manifest field '$key' must be an object.")
    }

    private fun JSONObject.requiredNullableObject(key: String): JSONObject? {
        require(has(key)) { "OTA manifest field '$key' is missing." }
        if (isNull(key)) return null
        return get(key) as? JSONObject
            ?: error("OTA manifest field '$key' must be an object or null.")
    }

    private fun JSONObject.requiredArray(key: String): JSONArray {
        require(has(key) && !isNull(key)) { "OTA manifest array '$key' is missing." }
        return get(key) as? JSONArray
            ?: error("OTA manifest field '$key' must be an array.")
    }

    private fun JSONObject.requiredString(key: String): String {
        require(has(key) && !isNull(key)) { "OTA manifest field '$key' is missing." }
        val value = get(key) as? String
            ?: error("OTA manifest field '$key' must be a string.")
        require(value.isNotEmpty()) { "OTA manifest field '$key' is missing." }
        require(!value.first().isWhitespace() && !value.last().isWhitespace()) {
            "OTA manifest field '$key' must not contain surrounding whitespace."
        }
        require(value.none(Char::isISOControl)) {
            "OTA manifest field '$key' must not contain control characters."
        }
        return value
    }

    private fun JSONObject.requiredBoolean(key: String): Boolean {
        require(has(key) && !isNull(key)) { "OTA manifest field '$key' is missing." }
        return get(key) as? Boolean
            ?: error("OTA manifest field '$key' must be a boolean.")
    }

    private fun JSONObject.requiredPositiveInt(key: String): Int = requiredInt(key, minimum = 1)

    private fun JSONObject.requiredNonNegativeInt(key: String): Int =
        requiredInt(key, minimum = 0)

    private fun JSONObject.requiredInt(key: String, minimum: Int): Int {
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

    private fun JSONObject.requiredReleaseNoteText(key: String): String {
        val value = requiredString(key)
        require(value.length <= DeviceFirmwareRuntimeContract.Limit.MAX_RELEASE_NOTE_TEXT_LENGTH) {
            "OTA release note '$key' exceeds the supported length."
        }
        return value
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

    private val ROOT_KEYS = setOf(
        "schema", "brand", "channel", "version", "tag", "releaseRepo", "generatedAt",
        "platform", "artifacts", DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES,
        "signature"
    )
    private val PLATFORM_KEYS = setOf(
        "framework", "core", "platform", "partitionTable", "normalOtaAssetType"
    )
    private val SIGNATURE_KEYS = setOf("scheme", "keyId", "payloadHash", "value")
    private val ARTIFACT_KEYS = setOf("env", "product", "compatibility", "firmware", "factory")
    private val PRODUCT_KEYS = setOf(
        "productKey", "productId", "brand", "family", "line", "model", "displayName",
        "skuCode", "hardwareRevision", "capabilities", "limits"
    )
    private val CAPABILITY_KEYS = setOf(
        "light", "manualLight", "lightProgram", "lightPresets", "lightSimulation", "fan",
        "cooling", "temperature", "standaloneTimer", "dosing", "timeSync", "ota"
    )
    private val LIMIT_KEYS = setOf(
        "lightChannelCount", "fanOutputCount", "temperatureSensorCount", "timerChannelCount",
        "dosingChannelCount"
    )
    private val COMPATIBILITY_KEYS = setOf(
        "productKey", "productId", "family", "line", "model", "hardwareRevision"
    )
    private val FIRMWARE_ASSET_KEYS = setOf(
        "version", "filename", "url", "sha256", "size", "format", "otaSlotCompatible"
    )
    private val FACTORY_ASSET_KEYS = setOf("filename", "url", "sha256", "size")
    private val RELEASE_NOTES_KEYS = setOf(
        "schema", DeviceFirmwareRuntimeContract.Manifest.DEFAULT_LOCALE,
        DeviceFirmwareRuntimeContract.Manifest.ITEMS
    )
    private val RELEASE_NOTE_ITEM_KEYS = setOf(
        DeviceFirmwareRuntimeContract.Manifest.TURKISH,
        DeviceFirmwareRuntimeContract.Manifest.ENGLISH
    )
    private val SUPPORTED_RELEASE_NOTE_LOCALES = setOf(
        DeviceFirmwareRuntimeContract.Manifest.TURKISH,
        DeviceFirmwareRuntimeContract.Manifest.ENGLISH
    )
    private val SUPPORTED_CHANNELS = setOf(
        DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
        DeviceFirmwareRuntimeContract.Manifest.BETA_CHANNEL,
        DeviceFirmwareRuntimeContract.Manifest.DEV_CHANNEL
    )
}
