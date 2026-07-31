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

    /**
     * Firmware release tooling signs `aql.ota.release-notes.v1` before manifest finalization.
     * The application model is populated as two locales so existing presentation state can consume
     * the exact item list without inventing unsigned titles, summaries, warnings or mandatory flags.
     */
    private fun parseReleaseNotes(json: JSONObject): DeviceFirmwareReleaseNotes {
        json.requireExactKeys(RELEASE_NOTES_KEYS, "releaseNotes")
        require(
            json.requiredString(
                DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES_SCHEMA_FIELD
            ) == DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES_SCHEMA
        ) { "Unsupported OTA release-notes schema." }
        val defaultLocale = json.requiredString(
            DeviceFirmwareRuntimeContract.Manifest.DEFAULT_LOCALE
        )
        require(defaultLocale == DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTE_TR) {
            "OTA release notes defaultLocale must be tr."
        }
        val items = json.requiredArray(
            DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTE_ITEMS
        )
        require(items.length() in 1..DeviceFirmwareRuntimeContract.Limit.MAX_RELEASE_NOTE_ITEMS) {
            "OTA release notes must contain 1-${DeviceFirmwareRuntimeContract.Limit.MAX_RELEASE_NOTE_ITEMS} items."
        }

        val turkish = ArrayList<String>(items.length())
        val english = ArrayList<String>(items.length())
        repeat(items.length()) { index ->
            val item = items.get(index) as? JSONObject
                ?: error("releaseNotes.items[$index] must be an object.")
            item.requireExactKeys(RELEASE_NOTE_ITEM_KEYS, "releaseNotes.items[$index]")
            turkish += item.requiredReleaseNoteText(
                DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTE_TR
            )
            english += item.requiredReleaseNoteText(
                DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTE_EN
            )
        }

        return DeviceFirmwareReleaseNotes(
            defaultLocale = defaultLocale,
            mandatory = false,
            locales = linkedMapOf(
                DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTE_TR to
                    DeviceFirmwareLocalizedReleaseNotes(
                        title = "",
                        summary = "",
                        changes = turkish,
                        warnings = emptyList()
                    ),
                DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTE_EN to
                    DeviceFirmwareLocalizedReleaseNotes(
                        title = "",
                        summary = "",
                        changes = english,
                        warnings = emptyList()
                    )
            )
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

    private fun JSONObject.requiredReleaseNoteText(key: String): String {
        val value = requiredString(key)
        require(value.length <= DeviceFirmwareRuntimeContract.Limit.MAX_RELEASE_NOTE_TEXT_LENGTH) {
            "OTA release note '$key' exceeds the supported length."
        }
        return value
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
    private val RELEASE_NOTES_KEYS = setOf(
        DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES_SCHEMA_FIELD,
        DeviceFirmwareRuntimeContract.Manifest.DEFAULT_LOCALE,
        DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTE_ITEMS
    )
    private val RELEASE_NOTE_ITEM_KEYS = setOf(
        DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTE_TR,
        DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTE_EN
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
}
