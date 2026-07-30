package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.json.JSONArray
import org.json.JSONObject

@Suppress("TooManyFunctions")
object DeviceFirmwareManifestParser {

    fun parse(raw: String): Result<DeviceFirmwareManifest> {
        return runCatching {
            val root = JSONObject(raw)
            root.requireKnownKeys(
                required = ROOT_REQUIRED_KEYS,
                optional = ROOT_OPTIONAL_KEYS,
                label = "manifest"
            )
            val manifest = DeviceFirmwareManifest(
                schema = root.requiredString("schema"),
                brand = root.requiredString("brand"),
                channel = root.requiredString("channel"),
                version = root.requiredString("version"),
                tag = root.requiredString("tag"),
                releaseRepo = root.requiredString("releaseRepo"),
                generatedAt = root.optExactString("generatedAt"),
                artifacts = parseArtifacts(root.requiredArray("artifacts")),
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

    private fun parseReleaseNotes(json: JSONObject): DeviceFirmwareReleaseNotes {
        json.requireExactKeys(
            expected = setOf(
                DeviceFirmwareRuntimeContract.Manifest.DEFAULT_LOCALE,
                DeviceFirmwareRuntimeContract.Manifest.MANDATORY,
                DeviceFirmwareRuntimeContract.Manifest.LOCALES
            ),
            label = "releaseNotes"
        )
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
            locales[localeTag] = parseLocalizedReleaseNotes(
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

    private fun parseLocalizedReleaseNotes(
        json: JSONObject,
        localeTag: String
    ): DeviceFirmwareLocalizedReleaseNotes {
        json.requireExactKeys(
            expected = setOf(
                DeviceFirmwareRuntimeContract.Manifest.TITLE,
                DeviceFirmwareRuntimeContract.Manifest.SUMMARY,
                DeviceFirmwareRuntimeContract.Manifest.CHANGES,
                DeviceFirmwareRuntimeContract.Manifest.WARNINGS
            ),
            label = "releaseNotes.locales.$localeTag"
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

    private fun parseArtifacts(array: JSONArray): List<DeviceFirmwareManifestArtifact> {
        return buildList {
            repeat(array.length()) { index ->
                val item = array.get(index) as? JSONObject
                    ?: error("OTA manifest artifact[$index] must be an object.")
                val artifact = parseArtifact(item, index)
                validateArtifact(artifact)
                add(artifact)
            }
        }
    }

    private fun parseArtifact(
        json: JSONObject,
        index: Int
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
            firmware = parseAsset(json.requiredObject("firmware"), "$label.firmware"),
            factory = json.optionalObject("factory")?.let { factory ->
                parseAsset(factory, "$label.factory")
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
            hardwareRevision = json.requiredString("hardwareRevision")
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

    private fun parseAsset(
        json: JSONObject,
        label: String
    ): DeviceFirmwareAsset {
        json.requireExactKeys(ASSET_KEYS, label)
        return DeviceFirmwareAsset(
            filename = json.requiredString("filename"),
            url = json.requiredString("url"),
            sha256 = json.requiredString("sha256").lowercase(),
            size = json.requiredPositiveInt("size"),
            format = json.requiredString("format"),
            otaSlotCompatible = json.requiredBoolean("otaSlotCompatible")
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
        require(
            artifact.firmware.url.startsWith(
                DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX
            )
        ) {
            "OTA firmware URL must target the official AquaLight release repository."
        }
        require(artifact.firmware.url.startsWith("https://")) {
            "OTA firmware URL must use HTTPS."
        }
        require(artifact.firmware.filename.endsWith("-ota.bin")) {
            "OTA firmware filename must end with -ota.bin."
        }
        require(artifact.firmware.sha256.isSha256Hex()) {
            "OTA firmware sha256 must be 64 hex characters."
        }
        require(artifact.firmware.size > 0) {
            "OTA firmware size must be greater than zero."
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
        require(has(key) && !isNull(key)) { "OTA manifest field '$key' is missing." }
        val value = get(key) as? Number
            ?: error("OTA manifest field '$key' must be an integer.")
        val asLong = value.toLong()
        require(value.toDouble().isFinite() && value.toDouble() == asLong.toDouble()) {
            "OTA manifest field '$key' must be an integer."
        }
        require(asLong in 1..Int.MAX_VALUE.toLong()) {
            "OTA manifest field '$key' is outside Android/firmware supported range."
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

    private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
        requireKnownKeys(required = expected, optional = emptySet(), label = label)
    }

    private fun JSONObject.requireKnownKeys(
        required: Set<String>,
        optional: Set<String>,
        label: String
    ) {
        val actual = buildSet {
            val iterator = keys()
            while (iterator.hasNext()) add(iterator.next())
        }
        val missing = required - actual
        val unknown = actual - required - optional
        require(missing.isEmpty() && unknown.isEmpty()) {
            "$label keys differ from the signed OTA manifest contract; " +
                "missing=${missing.sorted()} unknown=${unknown.sorted()}"
        }
    }

    private val ROOT_REQUIRED_KEYS = setOf(
        "schema", "brand", "channel", "version", "tag", "releaseRepo", "artifacts", "signature"
    )
    private val ROOT_OPTIONAL_KEYS = setOf(
        "generatedAt", DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES
    )
    private val SIGNATURE_KEYS = setOf("scheme", "keyId", "payloadHash", "value")
    private val ARTIFACT_REQUIRED_KEYS = setOf("env", "product", "compatibility", "firmware")
    private val ARTIFACT_OPTIONAL_KEYS = setOf("factory")
    private val PRODUCT_KEYS = setOf(
        "productKey", "productId", "brand", "family", "line", "model", "displayName",
        "skuCode", "hardwareRevision"
    )
    private val COMPATIBILITY_KEYS = setOf(
        "productKey", "productId", "family", "line", "model", "hardwareRevision"
    )
    private val ASSET_KEYS = setOf(
        "filename", "url", "sha256", "size", "format", "otaSlotCompatible"
    )
    private val LOCALE_TAG_PATTERN = Regex("^[a-z]{2,3}(?:-[A-Z]{2})?$")
}
