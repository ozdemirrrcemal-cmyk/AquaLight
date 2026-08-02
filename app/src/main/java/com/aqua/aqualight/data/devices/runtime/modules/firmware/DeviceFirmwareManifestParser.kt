package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.json.JSONArray
import org.json.JSONObject

@Suppress("TooManyFunctions", "LargeClass")
object DeviceFirmwareManifestParser {

    fun parse(raw: String): Result<DeviceFirmwareManifest> {
        return runCatching {
            val root = JSONObject(raw)
            root.requireKnownKeys(
                required = ROOT_REQUIRED_KEYS,
                optional = ROOT_OPTIONAL_KEYS,
                label = "manifest"
            )
            root.optionalObject(PLATFORM_FIELD)?.let(::validatePlatform)
            val version = root.requiredString("version")
            val manifest = DeviceFirmwareManifest(
                schema = root.requiredString("schema"),
                brand = root.requiredString("brand"),
                channel = root.requiredString("channel"),
                version = version,
                tag = root.requiredString("tag"),
                releaseRepo = root.requiredString("releaseRepo"),
                generatedAt = root.optExactString("generatedAt"),
                artifacts = parseArtifacts(root.requiredArray("artifacts"), version),
                signature = parseSignature(root.requiredObject("signature")),
                releaseNotes = root.optionalObject(
                    DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES
                )?.let(::parseReleaseNotes) ?: DeviceFirmwareReleaseNotes.EMPTY
            )

            require(manifest.isSupportedSchema) {
                "Unsupported OTA manifest schema, brand or release repository."
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

            manifest
        }
    }

    private fun validatePlatform(json: JSONObject) {
        json.requireExactKeys(PLATFORM_KEYS, PLATFORM_FIELD)
        require(json.requiredString("framework") == PLATFORM_FRAMEWORK) {
            "Unsupported OTA platform framework."
        }
        json.requiredString("core")
        json.requiredString("platform")
        require(json.requiredString("partitionTable") == PLATFORM_PARTITION_TABLE) {
            "Unsupported OTA partition table."
        }
        require(json.requiredString("normalOtaAssetType") == PLATFORM_NORMAL_OTA_ASSET_TYPE) {
            "Unsupported OTA asset type."
        }
    }

    private fun parseReleaseNotes(json: JSONObject): DeviceFirmwareReleaseNotes {
        return when (json.actualKeys()) {
            PUBLISHED_RELEASE_NOTES_KEYS -> parsePublishedReleaseNotes(json)
            LEGACY_RELEASE_NOTES_KEYS -> parseLegacyReleaseNotes(json)
            else -> error("releaseNotes keys differ from the signed OTA manifest contract.")
        }
    }

    private fun parsePublishedReleaseNotes(json: JSONObject): DeviceFirmwareReleaseNotes {
        require(json.requiredString(RELEASE_NOTES_SCHEMA_FIELD) == RELEASE_NOTES_SCHEMA) {
            "Unsupported OTA release-notes schema."
        }
        val defaultLocale = json.requiredLocaleTag(
            DeviceFirmwareRuntimeContract.Manifest.DEFAULT_LOCALE
        )
        require(defaultLocale in PUBLISHED_RELEASE_NOTE_LOCALES) {
            "OTA release notes defaultLocale must be tr or en."
        }
        val items = json.requiredArray(RELEASE_NOTES_ITEMS_FIELD)
        require(items.length() in 1..MAX_PUBLISHED_RELEASE_NOTE_ITEMS) {
            "OTA release notes must contain between 1 and $MAX_PUBLISHED_RELEASE_NOTE_ITEMS items."
        }
        val localizedItems = PUBLISHED_RELEASE_NOTE_LOCALES.associateWith {
            mutableListOf<String>()
        }
        repeat(items.length()) { index ->
            val item = items.get(index) as? JSONObject
                ?: error("OTA release note item[$index] must be an object.")
            item.requireExactKeys(PUBLISHED_RELEASE_NOTE_ITEM_KEYS, "releaseNotes.items[$index]")
            localizedItems.getValue(RELEASE_NOTES_TR_FIELD) += item.requiredReleaseNoteText(
                RELEASE_NOTES_TR_FIELD
            )
            localizedItems.getValue(RELEASE_NOTES_EN_FIELD) += item.requiredReleaseNoteText(
                RELEASE_NOTES_EN_FIELD
            )
        }
        return DeviceFirmwareReleaseNotes(
            defaultLocale = defaultLocale,
            mandatory = false,
            locales = linkedMapOf(
                RELEASE_NOTES_TR_FIELD to DeviceFirmwareLocalizedReleaseNotes(
                    title = "",
                    summary = "",
                    changes = localizedItems.getValue(RELEASE_NOTES_TR_FIELD),
                    warnings = emptyList()
                ),
                RELEASE_NOTES_EN_FIELD to DeviceFirmwareLocalizedReleaseNotes(
                    title = "",
                    summary = "",
                    changes = localizedItems.getValue(RELEASE_NOTES_EN_FIELD),
                    warnings = emptyList()
                )
            )
        )
    }

    private fun parseLegacyReleaseNotes(json: JSONObject): DeviceFirmwareReleaseNotes {
        val defaultLocale = json.requiredLocaleTag(
            DeviceFirmwareRuntimeContract.Manifest.DEFAULT_LOCALE
        )
        val mandatory = json.requiredBoolean(DeviceFirmwareRuntimeContract.Manifest.MANDATORY)
        val localesObject = json.requiredObject(DeviceFirmwareRuntimeContract.Manifest.LOCALES)
        val locales = linkedMapOf<String, DeviceFirmwareLocalizedReleaseNotes>()
        val keys = localesObject.keys()
        while (keys.hasNext()) {
            val localeTag = keys.next()
            require(LOCALE_TAG_PATTERN.matches(localeTag)) {
                "OTA release notes locale has an invalid exact tag: $localeTag"
            }
            require(localeTag !in locales) { "Duplicate OTA release notes locale: $localeTag" }
            locales[localeTag] = parseLegacyLocalizedReleaseNotes(
                localesObject.requiredObject(localeTag),
                localeTag
            )
        }
        require(locales.isNotEmpty()) { "OTA release notes must contain at least one locale." }
        require(defaultLocale in locales) {
            "OTA release notes defaultLocale must exist in locales."
        }
        return DeviceFirmwareReleaseNotes(
            defaultLocale = defaultLocale,
            mandatory = mandatory,
            locales = locales
        )
    }

    private fun parseLegacyLocalizedReleaseNotes(
        json: JSONObject,
        localeTag: String
    ): DeviceFirmwareLocalizedReleaseNotes {
        json.requireExactKeys(
            LEGACY_LOCALIZED_RELEASE_NOTES_KEYS,
            "releaseNotes.locales.$localeTag"
        )
        return DeviceFirmwareLocalizedReleaseNotes(
            title = json.requiredReleaseNoteText(DeviceFirmwareRuntimeContract.Manifest.TITLE),
            summary = json.requiredReleaseNoteText(DeviceFirmwareRuntimeContract.Manifest.SUMMARY),
            changes = json.requiredReleaseNoteArray(DeviceFirmwareRuntimeContract.Manifest.CHANGES),
            warnings = json.requiredReleaseNoteArray(DeviceFirmwareRuntimeContract.Manifest.WARNINGS)
        )
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

    private fun parseArtifacts(
        array: JSONArray,
        manifestVersion: String
    ): List<DeviceFirmwareManifestArtifact> {
        return buildList {
            repeat(array.length()) { index ->
                val item = array.get(index) as? JSONObject
                    ?: error("OTA manifest artifact[$index] must be an object.")
                val artifact = parseArtifact(item, index, manifestVersion)
                validateArtifact(artifact)
                add(artifact)
            }
        }
    }

    private fun parseArtifact(
        json: JSONObject,
        index: Int,
        manifestVersion: String
    ): DeviceFirmwareManifestArtifact {
        val label = "artifact[$index]"
        json.requireKnownKeys(
            required = ARTIFACT_REQUIRED_KEYS,
            optional = ARTIFACT_OPTIONAL_KEYS,
            label = label
        )
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
            factory = json.optionalNullableObject("factory")?.let { factory ->
                parseFactoryAsset(factory, "$label.factory")
            }
        )
    }

    private fun parseProduct(
        json: JSONObject,
        label: String
    ): DeviceFirmwareManifestProduct {
        val hasCapabilities = json.has(PRODUCT_CAPABILITIES_FIELD)
        if (hasCapabilities) {
            json.requireKnownKeys(
                required = PRODUCT_KEYS,
                optional = PRODUCT_OPTIONAL_KEYS,
                label = label
            )
        } else {
            json.requireExactKeys(PRODUCT_KEYS, label)
        }
        val hasLimits = json.has(PRODUCT_LIMITS_FIELD)
        require(hasCapabilities == hasLimits) {
            "$label must contain capabilities and limits together."
        }
        if (hasCapabilities) {
            validateCapabilities(
                json.requiredObject(PRODUCT_CAPABILITIES_FIELD),
                "$label.$PRODUCT_CAPABILITIES_FIELD"
            )
            validateLimits(
                json.requiredObject(PRODUCT_LIMITS_FIELD),
                "$label.$PRODUCT_LIMITS_FIELD"
            )
        }
        return DeviceFirmwareManifestProduct(
            productKey = json.requiredString("productKey"),
            productId = json.requiredString("productId"),
            brand = json.requiredString("brand"),
            family = json.requiredString("family"),
            line = json.requiredString("line"),
            model = json.requiredString("model"),
            displayName = json.requiredString("displayName"),
            skuCode = json.requiredString("skuCode"),
            hardwareRevision = json.requiredString("hardwareRevision")
        )
    }

    private fun validateCapabilities(
        json: JSONObject,
        label: String
    ) {
        json.requireExactKeys(CAPABILITY_KEYS, label)
        CAPABILITY_KEYS.forEach { key -> json.requiredBoolean(key) }
    }

    private fun validateLimits(
        json: JSONObject,
        label: String
    ) {
        json.requireExactKeys(LIMIT_KEYS, label)
        LIMIT_KEYS.forEach { key -> json.requiredNonNegativeInt(key) }
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
        if (json.has(FIRMWARE_VERSION_FIELD)) {
            json.requireKnownKeys(
                required = ASSET_KEYS,
                optional = FIRMWARE_ASSET_OPTIONAL_KEYS,
                label = label
            )
        } else {
            json.requireExactKeys(ASSET_KEYS, label)
        }
        if (json.has(FIRMWARE_VERSION_FIELD)) {
            require(json.requiredString(FIRMWARE_VERSION_FIELD) == manifestVersion) {
                "OTA firmware version does not match the manifest version."
            }
        }
        return DeviceFirmwareAsset(
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
    ): DeviceFirmwareAsset {
        json.requireExactKeys(FACTORY_ASSET_KEYS, label)
        return DeviceFirmwareAsset(
            filename = json.requiredString("filename"),
            url = json.requiredString("url"),
            sha256 = json.requiredString("sha256").lowercase(),
            size = json.requiredPositiveInt("size")
        )
    }

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
        validateOfficialAsset(artifact.firmware, "firmware")
        require(artifact.firmware.filename.endsWith("-ota.bin")) {
            "OTA firmware filename must end with -ota.bin."
        }
        require(artifact.firmware.otaSlotCompatible) {
            "OTA firmware must be marked as OTA slot compatible."
        }
        artifact.factory?.let { factory ->
            validateOfficialAsset(factory, "factory")
            require(factory.filename.endsWith("-factory.zip")) {
                "OTA factory filename must end with -factory.zip."
            }
        }
    }

    private fun validateOfficialAsset(
        asset: DeviceFirmwareAsset,
        label: String
    ) {
        require(asset.url.startsWith(DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX)) {
            "OTA $label URL must target the official AquaLight release repository."
        }
        require(asset.url.startsWith("https://")) {
            "OTA $label URL must use HTTPS."
        }
        require(asset.url.endsWith("/${asset.filename}")) {
            "OTA $label URL must end with its filename."
        }
        require(asset.sha256.isSha256Hex()) {
            "OTA $label sha256 must be 64 hex characters."
        }
        require(asset.size > 0) {
            "OTA $label size must be greater than zero."
        }
    }

    private fun JSONObject.requiredObject(key: String): JSONObject {
        require(has(key) && !isNull(key)) { "OTA manifest object '$key' is missing." }
        return get(key) as? JSONObject ?: error("OTA manifest field '$key' must be an object.")
    }

    private fun JSONObject.optionalObject(key: String): JSONObject? {
        if (!has(key)) return null
        require(!isNull(key)) { "OTA manifest optional object '$key' must not be null." }
        return get(key) as? JSONObject ?: error("OTA manifest field '$key' must be an object.")
    }

    private fun JSONObject.optionalNullableObject(key: String): JSONObject? {
        if (!has(key) || isNull(key)) return null
        return get(key) as? JSONObject
            ?: error("OTA manifest field '$key' must be an object or null.")
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

    private fun JSONObject.optExactString(key: String): String {
        if (!has(key)) return ""
        require(!isNull(key)) { "OTA manifest optional field '$key' must not be null." }
        return requiredString(key)
    }

    private fun JSONObject.requiredBoolean(key: String): Boolean {
        require(has(key) && !isNull(key)) { "OTA manifest field '$key' is missing." }
        return get(key) as? Boolean ?: error("OTA manifest field '$key' must be a boolean.")
    }

    private fun JSONObject.requiredPositiveInt(key: String): Int {
        val value = requiredExactInt(key)
        require(value > 0) { "OTA manifest field '$key' must be greater than zero." }
        return value
    }

    private fun JSONObject.requiredNonNegativeInt(key: String): Int {
        val value = requiredExactInt(key)
        require(value >= 0) { "OTA manifest field '$key' must not be negative." }
        return value
    }

    private fun JSONObject.requiredExactInt(key: String): Int {
        require(has(key) && !isNull(key)) { "OTA manifest field '$key' is missing." }
        val value = get(key) as? Number
            ?: error("OTA manifest field '$key' must be an integer.")
        val asLong = value.toLong()
        require(value.toDouble().isFinite() && value.toDouble() == asLong.toDouble()) {
            "OTA manifest field '$key' must be an integer."
        }
        require(asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "OTA manifest field '$key' is outside Android supported range."
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
        val value = requiredString(key)
        require(value.length <= DeviceFirmwareRuntimeContract.Limit.MAX_RELEASE_NOTE_TEXT_LENGTH) {
            "OTA release note '$key' exceeds the supported length."
        }
        return value
    }

    private fun JSONObject.requiredReleaseNoteArray(key: String): List<String> {
        val array = requiredArray(key)
        require(array.length() <= DeviceFirmwareRuntimeContract.Limit.MAX_RELEASE_NOTE_ITEMS) {
            "OTA release note '$key' contains too many items."
        }
        return buildList {
            repeat(array.length()) { index ->
                val item = array.get(index) as? String
                    ?: error("OTA release note '$key[$index]' must be a string.")
                require(item.isNotEmpty()) { "OTA release note '$key[$index]' must not be empty." }
                require(!item.first().isWhitespace() && !item.last().isWhitespace()) {
                    "OTA release note '$key[$index]' must not contain surrounding whitespace."
                }
                require(item.none(Char::isISOControl)) {
                    "OTA release note '$key[$index]' must not contain control characters."
                }
                require(
                    item.length <= DeviceFirmwareRuntimeContract.Limit.MAX_RELEASE_NOTE_TEXT_LENGTH
                ) {
                    "OTA release note '$key[$index]' exceeds the supported length."
                }
                add(item)
            }
        }
    }

    private fun JSONObject.actualKeys(): Set<String> = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
        requireKnownKeys(required = expected, optional = emptySet(), label = label)
    }

    private fun JSONObject.requireKnownKeys(
        required: Set<String>,
        optional: Set<String>,
        label: String
    ) {
        val actual = actualKeys()
        val missing = required - actual
        val unknown = actual - required - optional
        require(missing.isEmpty() && unknown.isEmpty()) {
            "$label keys differ from the signed OTA manifest contract; " +
                "missing=${missing.sorted()} unknown=${unknown.sorted()}"
        }
    }

    private const val PLATFORM_FIELD = "platform"
    private const val PLATFORM_FRAMEWORK = "arduino-esp32"
    private const val PLATFORM_PARTITION_TABLE = "aql_ota_16mb"
    private const val PLATFORM_NORMAL_OTA_ASSET_TYPE = "firmware.bin"
    private const val PRODUCT_CAPABILITIES_FIELD = "capabilities"
    private const val PRODUCT_LIMITS_FIELD = "limits"
    private const val FIRMWARE_VERSION_FIELD = "version"
    private const val RELEASE_NOTES_SCHEMA_FIELD = "schema"
    private const val RELEASE_NOTES_SCHEMA = "aql.ota.release-notes.v1"
    private const val RELEASE_NOTES_ITEMS_FIELD = "items"
    private const val RELEASE_NOTES_TR_FIELD = "tr"
    private const val RELEASE_NOTES_EN_FIELD = "en"
    private const val MAX_PUBLISHED_RELEASE_NOTE_ITEMS = 20

    private val ROOT_REQUIRED_KEYS = setOf(
        "schema", "brand", "channel", "version", "tag", "releaseRepo", "artifacts", "signature"
    )
    private val ROOT_OPTIONAL_KEYS = setOf(
        "generatedAt",
        PLATFORM_FIELD,
        DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES
    )
    private val PLATFORM_KEYS = setOf(
        "framework", "core", "platform", "partitionTable", "normalOtaAssetType"
    )
    private val SIGNATURE_KEYS = setOf("scheme", "keyId", "payloadHash", "value")
    private val ARTIFACT_REQUIRED_KEYS = setOf("env", "product", "compatibility", "firmware")
    private val ARTIFACT_OPTIONAL_KEYS = setOf("factory")
    private val PRODUCT_KEYS = setOf(
        "productKey", "productId", "brand", "family", "line", "model", "displayName",
        "skuCode", "hardwareRevision"
    )
    private val PRODUCT_OPTIONAL_KEYS = setOf(PRODUCT_CAPABILITIES_FIELD, PRODUCT_LIMITS_FIELD)
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
    private val ASSET_KEYS = setOf(
        "filename", "url", "sha256", "size", "format", "otaSlotCompatible"
    )
    private val FIRMWARE_ASSET_OPTIONAL_KEYS = setOf(FIRMWARE_VERSION_FIELD)
    private val FACTORY_ASSET_KEYS = setOf("filename", "url", "sha256", "size")
    private val PUBLISHED_RELEASE_NOTES_KEYS = setOf(
        RELEASE_NOTES_SCHEMA_FIELD,
        DeviceFirmwareRuntimeContract.Manifest.DEFAULT_LOCALE,
        RELEASE_NOTES_ITEMS_FIELD
    )
    private val PUBLISHED_RELEASE_NOTE_ITEM_KEYS = setOf(
        RELEASE_NOTES_TR_FIELD,
        RELEASE_NOTES_EN_FIELD
    )
    private val PUBLISHED_RELEASE_NOTE_LOCALES = linkedSetOf(
        RELEASE_NOTES_TR_FIELD,
        RELEASE_NOTES_EN_FIELD
    )
    private val LEGACY_RELEASE_NOTES_KEYS = setOf(
        DeviceFirmwareRuntimeContract.Manifest.DEFAULT_LOCALE,
        DeviceFirmwareRuntimeContract.Manifest.MANDATORY,
        DeviceFirmwareRuntimeContract.Manifest.LOCALES
    )
    private val LEGACY_LOCALIZED_RELEASE_NOTES_KEYS = setOf(
        DeviceFirmwareRuntimeContract.Manifest.TITLE,
        DeviceFirmwareRuntimeContract.Manifest.SUMMARY,
        DeviceFirmwareRuntimeContract.Manifest.CHANGES,
        DeviceFirmwareRuntimeContract.Manifest.WARNINGS
    )
    private val LOCALE_TAG_PATTERN = Regex("^[a-z]{2,3}(?:-[A-Z]{2})?$")
}
