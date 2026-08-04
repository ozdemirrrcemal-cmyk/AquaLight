package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceCapabilitySet
import com.aqua.aqualight.data.devices.model.DeviceLimitSet
import java.time.Instant
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Exact parser for the signed, product-scoped OTA channel manifest emitted by firmware CI. */
@Suppress("TooManyFunctions")
object DeviceFirmwareManifestParser {

    fun parse(raw: String): Result<DeviceFirmwareManifest> = runCatching {
        val root = JSONObject(raw)
        root.requireExactKeys(ROOT_KEYS, "manifest")
        val manifest = DeviceFirmwareManifest(
            schema = root.requiredString("schema"),
            brand = root.requiredString("brand"),
            channel = root.requiredString("channel"),
            releaseRepo = root.requiredString("releaseRepo"),
            generatedAt = root.requiredInstant("generatedAt"),
            artifacts = parseArtifacts(root.requiredArray("artifacts")),
            signature = parseSignature(root.requiredObject("signature"))
        )

        require(manifest.isSupportedSchema) {
            "Unsupported OTA manifest schema, brand or release repository."
        }
        require(manifest.channel in SUPPORTED_CHANNELS) {
            "Unsupported OTA manifest channel: ${manifest.channel}"
        }
        require(manifest.artifacts.isNotEmpty()) {
            "OTA channel manifest does not contain any artifacts."
        }
        require(
            manifest.artifacts
                .map(DeviceFirmwareManifestArtifact::compatibility)
                .distinct()
                .size == manifest.artifacts.size
        ) {
            "OTA channel manifest contains duplicate compatibility identities."
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

        manifest
    }

    private fun parseArtifacts(array: JSONArray): List<DeviceFirmwareManifestArtifact> =
        buildList {
            repeat(array.length()) { index ->
                val item = array.get(index) as? JSONObject
                    ?: error("OTA manifest artifact[$index] must be an object.")
                add(parseArtifact(item, index).also(::validateArtifact))
            }
        }

    private fun parseArtifact(
        json: JSONObject,
        index: Int
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
            platform = parsePlatform(json.requiredObject("platform"), "$label.platform"),
            release = parseRelease(json.requiredObject("release"), "$label.release"),
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
    ): DeviceCapabilitySet {
        json.requireExactKeys(CAPABILITY_KEYS, label)
        return DeviceCapabilitySet(
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
    ): DeviceLimitSet {
        json.requireExactKeys(LIMIT_KEYS, label)
        return DeviceLimitSet(
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

    private fun parsePlatform(
        json: JSONObject,
        label: String
    ): DeviceFirmwarePlatform {
        json.requireExactKeys(PLATFORM_KEYS, label)
        return DeviceFirmwarePlatform(
            framework = json.requiredString("framework"),
            core = json.requiredString("core"),
            platform = json.requiredString("platform"),
            partitionTable = json.requiredString("partitionTable"),
            normalOtaAssetType = json.requiredString("normalOtaAssetType")
        )
    }

    private fun parseRelease(
        json: JSONObject,
        label: String
    ): DeviceFirmwareRelease {
        json.requireExactKeys(RELEASE_KEYS, label)
        return DeviceFirmwareRelease(
            version = json.requiredString("version"),
            tag = json.requiredString("tag"),
            generatedAt = json.requiredInstant("generatedAt"),
            releaseNotes = parseReleaseNotes(
                json.requiredObject(DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES),
                "$label.releaseNotes"
            )
        )
    }

    private fun parseReleaseNotes(
        json: JSONObject,
        label: String
    ): DeviceFirmwareReleaseNotes {
        json.requireExactKeys(RELEASE_NOTES_KEYS, label)
        require(
            json.requiredString("schema") ==
                DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES_SCHEMA
        ) {
            "Unsupported OTA release-notes schema."
        }
        val defaultLocale = json.requiredLocaleTag(
            DeviceFirmwareRuntimeContract.Manifest.DEFAULT_LOCALE_FIELD
        )
        require(defaultLocale in RELEASE_NOTE_LOCALES) {
            "OTA release notes defaultLocale is unsupported: $defaultLocale"
        }
        val items = json.requiredArray(DeviceFirmwareRuntimeContract.Manifest.ITEMS)
        require(items.length() in 1..DeviceFirmwareRuntimeContract.Limit.MAX_RELEASE_NOTE_ITEMS) {
            "OTA release notes must contain a supported number of items."
        }
        val localizedItems = RELEASE_NOTE_LOCALES.associateWith { mutableListOf<String>() }
        repeat(items.length()) { index ->
            val item = items.get(index) as? JSONObject
                ?: error("OTA release note item[$index] must be an object.")
            item.requireExactKeys(RELEASE_NOTE_ITEM_KEYS, "$label.items[$index]")
            RELEASE_NOTE_LOCALES.forEach { locale ->
                localizedItems.getValue(locale).add(item.requiredReleaseNoteText(locale))
            }
        }
        return DeviceFirmwareReleaseNotes(
            defaultLocale = defaultLocale,
            mandatory = false,
            locales = localizedItems.mapValues { (_, changes) ->
                DeviceFirmwareLocalizedReleaseNotes(
                    title = "",
                    summary = "",
                    changes = changes,
                    warnings = emptyList()
                )
            }
        )
    }

    private fun parseFirmware(
        json: JSONObject,
        label: String
    ): DeviceFirmwareAsset {
        json.requireExactKeys(FIRMWARE_KEYS, label)
        return DeviceFirmwareAsset(
            filename = json.requiredString("filename"),
            url = json.requiredString("url"),
            sha256 = json.requiredString("sha256").lowercase(Locale.ROOT),
            size = json.requiredPositiveInt("size"),
            format = json.requiredString("format"),
            otaSlotCompatible = json.requiredBoolean("otaSlotCompatible")
        )
    }

    private fun parseFactory(
        json: JSONObject,
        label: String
    ): DeviceFirmwareFactoryAsset {
        json.requireExactKeys(FACTORY_KEYS, label)
        return DeviceFirmwareFactoryAsset(
            filename = json.requiredString("filename"),
            url = json.requiredString("url"),
            sha256 = json.requiredString("sha256").lowercase(Locale.ROOT),
            size = json.requiredPositiveInt("size")
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

    private fun validateArtifact(artifact: DeviceFirmwareManifestArtifact) {
        validateIdentity(artifact)
        require(ENVIRONMENT_PATTERN.matches(artifact.env)) {
            "OTA artifact environment is invalid: ${artifact.env}"
        }
        require(artifact.env == artifact.product.productKey.lowercase(Locale.ROOT)) {
            "OTA artifact environment does not match lowercase productKey."
        }
        require(artifact.product.brand == DeviceFirmwareRuntimeContract.Manifest.BRAND) {
            "OTA artifact product brand is unsupported."
        }
        validatePlatform(artifact.platform)
        validateRelease(artifact.release)
        validateFirmwareAsset(artifact)
        artifact.factory?.let { factory -> validateFactoryAsset(artifact, factory) }
    }

    private fun validateIdentity(artifact: DeviceFirmwareManifestArtifact) {
        val product = artifact.product
        val compatibility = artifact.compatibility
        require(product.productKey == compatibility.productKey) { "Manifest productKey mismatch." }
        require(product.productId == compatibility.productId) { "Manifest productId mismatch." }
        require(product.family == compatibility.family) { "Manifest family mismatch." }
        require(product.line == compatibility.line) { "Manifest line mismatch." }
        require(product.model == compatibility.model) { "Manifest model mismatch." }
        require(product.hardwareRevision == compatibility.hardwareRevision) {
            "Manifest hardwareRevision mismatch."
        }
    }

    private fun validatePlatform(platform: DeviceFirmwarePlatform) {
        require(platform.framework == DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK)
        require(platform.core == DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE)
        require(platform.platform == DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE)
        require(platform.partitionTable == DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE)
        require(
            platform.normalOtaAssetType ==
                DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
        )
    }

    private fun validateRelease(release: DeviceFirmwareRelease) {
        require(VERSION_PATTERN.matches(release.version)) {
            "OTA artifact release version is invalid: ${release.version}"
        }
        require(release.tag == "v${release.version}") {
            "OTA artifact release tag does not match its version."
        }
    }

    private fun validateFirmwareAsset(artifact: DeviceFirmwareManifestArtifact) {
        val firmware = artifact.firmware
        val expectedFilename = "AquaLight-${artifact.env}-${artifact.release.tag}-ota.bin"
        require(firmware.filename == expectedFilename) {
            "OTA firmware filename does not match env/release contract."
        }
        require(
            firmware.url == DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                "${artifact.release.tag}/$expectedFilename"
        ) {
            "OTA firmware URL does not match the immutable release asset."
        }
        require(firmware.sha256.isSha256Hex()) {
            "OTA firmware sha256 must be 64 hex characters."
        }
        require(firmware.format == DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT) {
            "OTA firmware format is unsupported: ${firmware.format}"
        }
        require(firmware.otaSlotCompatible) {
            "OTA firmware is not marked as OTA slot compatible."
        }
    }

    private fun validateFactoryAsset(
        artifact: DeviceFirmwareManifestArtifact,
        factory: DeviceFirmwareFactoryAsset
    ) {
        val expectedFilename = "AquaLight-${artifact.env}-${artifact.release.tag}-factory.zip"
        require(factory.filename == expectedFilename) {
            "OTA factory filename does not match env/release contract."
        }
        require(
            factory.url == DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                "${artifact.release.tag}/$expectedFilename"
        ) {
            "OTA factory URL does not match the immutable release asset."
        }
        require(factory.sha256.isSha256Hex()) {
            "OTA factory sha256 must be 64 hex characters."
        }
    }

    private fun JSONObject.requiredObject(key: String): JSONObject {
        require(has(key) && !isNull(key)) { "OTA manifest object '$key' is missing." }
        return get(key) as? JSONObject ?: error("OTA manifest field '$key' must be an object.")
    }

    private fun JSONObject.requiredNullableObject(key: String): JSONObject? {
        require(has(key)) { "OTA manifest nullable object '$key' is missing." }
        if (isNull(key)) return null
        return get(key) as? JSONObject ?: error("OTA manifest field '$key' must be an object.")
    }

    private fun JSONObject.requiredArray(key: String): JSONArray {
        require(has(key) && !isNull(key)) { "OTA manifest array '$key' is missing." }
        return get(key) as? JSONArray ?: error("OTA manifest field '$key' must be an array.")
    }

    private fun JSONObject.requiredString(key: String): String {
        require(has(key) && !isNull(key)) { "OTA manifest field '$key' is missing." }
        val value = get(key) as? String ?: error("OTA manifest field '$key' must be a string.")
        require(value.isNotEmpty()) { "OTA manifest field '$key' is missing." }
        require(!value.first().isWhitespace() && !value.last().isWhitespace()) {
            "OTA manifest field '$key' must not contain surrounding whitespace."
        }
        require(value.none(Char::isISOControl)) {
            "OTA manifest field '$key' must not contain control characters."
        }
        return value
    }

    private fun JSONObject.requiredInstant(key: String): String {
        val value = requiredString(key)
        runCatching { Instant.parse(value) }.getOrElse {
            error("OTA manifest field '$key' must be an ISO-8601 instant.")
        }
        return value
    }

    private fun JSONObject.requiredBoolean(key: String): Boolean {
        require(has(key) && !isNull(key)) { "OTA manifest field '$key' is missing." }
        return get(key) as? Boolean ?: error("OTA manifest field '$key' must be a boolean.")
    }

    private fun JSONObject.requiredPositiveInt(key: String): Int = requiredIntInRange(
        key = key,
        range = 1..Int.MAX_VALUE
    )

    private fun JSONObject.requiredNonNegativeInt(key: String): Int = requiredIntInRange(
        key = key,
        range = 0..Int.MAX_VALUE
    )

    private fun JSONObject.requiredIntInRange(key: String, range: IntRange): Int {
        require(has(key) && !isNull(key)) { "OTA manifest field '$key' is missing." }
        val value = get(key) as? Number
            ?: error("OTA manifest field '$key' must be an integer.")
        val asLong = value.toLong()
        require(value.toDouble().isFinite() && value.toDouble() == asLong.toDouble()) {
            "OTA manifest field '$key' must be an integer."
        }
        require(asLong in range.first.toLong()..range.last.toLong()) {
            "OTA manifest field '$key' is outside the supported range."
        }
        return asLong.toInt()
    }

    private fun JSONObject.requiredLocaleTag(key: String): String {
        val localeTag = requiredString(key)
        require(LOCALE_TAG_PATTERN.matches(localeTag)) {
            "OTA manifest field '$key' must use an exact locale tag."
        }
        return localeTag
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
        "schema", "brand", "channel", "releaseRepo", "generatedAt", "artifacts", "signature"
    )
    private val SIGNATURE_KEYS = setOf("scheme", "keyId", "payloadHash", "value")
    private val ARTIFACT_KEYS = setOf(
        "env", "product", "compatibility", "platform", "release", "firmware", "factory"
    )
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
    private val PLATFORM_KEYS = setOf(
        "framework", "core", "platform", "partitionTable", "normalOtaAssetType"
    )
    private val RELEASE_KEYS = setOf("version", "tag", "generatedAt", "releaseNotes")
    private val RELEASE_NOTES_KEYS = setOf("schema", "defaultLocale", "items")
    private val RELEASE_NOTE_ITEM_KEYS = setOf("tr", "en")
    private val RELEASE_NOTE_LOCALES = listOf(
        DeviceFirmwareRuntimeContract.Manifest.TURKISH_LOCALE,
        DeviceFirmwareRuntimeContract.Manifest.ENGLISH_LOCALE
    )
    private val FIRMWARE_KEYS = setOf(
        "filename", "url", "sha256", "size", "format", "otaSlotCompatible"
    )
    private val FACTORY_KEYS = setOf("filename", "url", "sha256", "size")
    private val SUPPORTED_CHANNELS = setOf(
        DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
        DeviceFirmwareRuntimeContract.Manifest.BETA_CHANNEL,
        DeviceFirmwareRuntimeContract.Manifest.DEV_CHANNEL
    )
    private val ENVIRONMENT_PATTERN = Regex("^[a-z0-9_]+$")
    private val VERSION_PATTERN = Regex(
        "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)" +
            "(?:[-.][A-Za-z0-9._-]+)?$"
    )
    private val LOCALE_TAG_PATTERN = Regex("^[a-z]{2,3}(?:-[A-Z]{2})?$")
}
