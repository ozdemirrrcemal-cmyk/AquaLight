package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.json.JSONArray
import org.json.JSONObject

@Suppress("TooManyFunctions")
object DeviceFirmwareManifestParser {

    fun parse(raw: String): Result<DeviceFirmwareManifest> {
        return runCatching {
            val root = JSONObject(raw)
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
                releaseNotes = root.optJSONObject(
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
                val artifact = parseArtifact(item)
                validateArtifact(artifact)
                add(artifact)
            }
        }
    }

    private fun parseArtifact(json: JSONObject): DeviceFirmwareManifestArtifact {
        return DeviceFirmwareManifestArtifact(
            env = json.requiredString("env"),
            product = parseProduct(json.requiredObject("product")),
            compatibility = parseCompatibility(json.requiredObject("compatibility")),
            firmware = parseAsset(json.requiredObject("firmware")),
            factory = json.optJSONObject("factory")?.let { parseAsset(it) }
        )
    }

    private fun parseProduct(json: JSONObject): DeviceFirmwareManifestProduct {
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

    private fun parseCompatibility(json: JSONObject): DeviceFirmwareCompatibility {
        return DeviceFirmwareCompatibility(
            productKey = json.requiredString("productKey"),
            productId = json.requiredString("productId"),
            family = json.requiredString("family"),
            line = json.requiredString("line"),
            model = json.requiredString("model"),
            hardwareRevision = json.requiredString("hardwareRevision")
        )
    }

    private fun parseAsset(json: JSONObject): DeviceFirmwareAsset {
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
        return get(key) as? JSONObject ?: error("OTA manifest object '$key' is missing.")
    }

    private fun JSONObject.requiredArray(key: String): JSONArray {
        return get(key) as? JSONArray ?: error("OTA manifest array '$key' is missing.")
    }

    private fun JSONObject.requiredString(key: String): String {
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
        if (!has(key) || isNull(key)) return ""
        return requiredString(key)
    }

    private fun JSONObject.requiredBoolean(key: String): Boolean {
        return get(key) as? Boolean ?: error("OTA manifest field '$key' must be a boolean.")
    }

    private fun JSONObject.requiredPositiveInt(key: String): Int {
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
        val actual = buildSet {
            val iterator = keys()
            while (iterator.hasNext()) add(iterator.next())
        }
        require(actual == expected) {
            "$label keys differ from the signed OTA manifest contract."
        }
    }

    private val LOCALE_TAG_PATTERN = Regex("^[a-z]{2,3}(?:-[A-Z]{2})?$")
}
