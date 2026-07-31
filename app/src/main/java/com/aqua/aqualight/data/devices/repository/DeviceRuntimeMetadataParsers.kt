package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.contract.AqlCatalogKeySet
import com.aqua.aqualight.data.devices.contract.parseAqlDeviceFeatureKeysExact
import com.aqua.aqualight.data.devices.contract.parseAqlDeviceScreenKeysExact
import com.aqua.aqualight.data.devices.model.DEVICE_CUSTOM_NAME_MAX_BYTES
import com.aqua.aqualight.data.devices.model.DeviceApiVersion
import com.aqua.aqualight.data.devices.model.DeviceCapabilitySet
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceFirmwareVersion
import com.aqua.aqualight.data.devices.model.DeviceHardwareRevision
import com.aqua.aqualight.data.devices.model.DeviceLimitSet
import com.aqua.aqualight.data.devices.model.DeviceProductId
import com.aqua.aqualight.data.devices.model.DeviceProductKey
import com.aqua.aqualight.data.devices.model.DeviceProductLine
import com.aqua.aqualight.data.devices.model.DeviceProductModel
import com.aqua.aqualight.data.devices.model.DeviceProtocolVersion
import com.aqua.aqualight.data.devices.model.DeviceRuntimeCapabilities
import com.aqua.aqualight.data.devices.model.DeviceRuntimeIdentity
import com.aqua.aqualight.data.devices.model.DeviceRuntimeIdentityEnvelope
import com.aqua.aqualight.data.devices.model.DeviceRuntimeTransportMetadata
import com.aqua.aqualight.data.devices.model.DeviceSkuCode
import com.aqua.aqualight.data.devices.model.DeviceSkuId
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.json.JSONArray
import org.json.JSONObject

typealias ParsedDeviceRuntimeIdentity = DeviceRuntimeIdentityEnvelope

object DeviceRuntimeIdentityParser {

    fun parse(
        expectedDeviceUid: DeviceUid,
        data: JSONObject
    ): Result<ParsedDeviceRuntimeIdentity> = runCatching {
        data.requireExactKeys(IDENTITY_KEYS, "device.identity.get.data")
        val runtime = data.requireObject("runtime")
        runtime.requireExactKeys(RUNTIME_KEYS, "device.identity.get.data.runtime")

        val reportedDeviceUid = DeviceUid(data.requireExactString("deviceUid"))
        require(reportedDeviceUid == expectedDeviceUid) {
            "device.identity.get deviceUid does not match the authenticated device."
        }
        val family = requireNotNull(
            DeviceFamily.fromWireExact(data.requireExactString("family"))
        ) { "device.identity.get family is not an exact commercial family." }
        val displayName = data.requireExactString("displayName")
        val customName = data.requireExactOptionalString("customName")
        val effectiveDisplayName = data.requireExactString("effectiveDisplayName")
        val nameEditable = data.requireExactBoolean("nameEditable")
        val customNameMaxBytes = data.requireExactInt("customNameMaxBytes")

        require(nameEditable) { "device.identity.get must advertise editable device names." }
        require(customNameMaxBytes == DEVICE_CUSTOM_NAME_MAX_BYTES) {
            "device.identity.get customNameMaxBytes is incompatible."
        }
        require(customName.toByteArray(Charsets.UTF_8).size <= customNameMaxBytes) {
            "device.identity.get customName exceeds its UTF-8 byte limit."
        }
        require(effectiveDisplayName == customName.ifBlank { displayName }) {
            "device.identity.get effectiveDisplayName violates the fallback contract."
        }

        ParsedDeviceRuntimeIdentity(
            identity = DeviceRuntimeIdentity(
                deviceUid = reportedDeviceUid,
                productKey = DeviceProductKey(data.requireExactString("productKey")),
                productId = DeviceProductId(data.requireExactString("productId")),
                family = family,
                line = DeviceProductLine(data.requireExactString("line")),
                model = DeviceProductModel(data.requireExactString("model")),
                brand = data.requireExactString("brand"),
                displayName = displayName,
                customName = customName,
                effectiveDisplayName = effectiveDisplayName,
                nameEditable = nameEditable,
                customNameMaxBytes = customNameMaxBytes,
                skuId = DeviceSkuId(data.requireExactString("skuId")),
                skuCode = DeviceSkuCode(data.requireExactString("skuCode")),
                hardwareRevision = DeviceHardwareRevision(
                    data.requireExactString("hardwareRevision")
                ),
                firmwareVersion = DeviceFirmwareVersion(
                    data.requireExactString("firmwareVersion")
                ),
                apiVersion = DeviceApiVersion(data.requireExactInt("apiVersion")),
                protocolVersion = DeviceProtocolVersion(data.requireExactInt("protocolVersion"))
            ),
            shortId = data.requireExactString("shortId"),
            serialNumber = data.requireExactString("serialNumber"),
            firmwareSerial = data.requireExactString("firmwareSerial"),
            macAddress = data.requireExactString("macAddress"),
            setupCode = data.requireExactString("setupCode"),
            runtime = DeviceRuntimeTransportMetadata(
                transport = runtime.requireExactString("transport"),
                wsSchema = runtime.requireExactString("wsSchema"),
                wsPath = runtime.requireExactString("wsPath"),
                wsPort = runtime.requireExactInt("wsPort"),
                wsProtocolVersion = runtime.requireExactInt("wsProtocolVersion")
            )
        )
    }

    private val IDENTITY_KEYS = setOf(
        "productKey", "productId", "setupCode", "deviceUid", "shortId",
        "serialNumber", "firmwareSerial", "macAddress", "brand", "family",
        "line", "model", "displayName", "customName", "effectiveDisplayName",
        "nameEditable", "customNameMaxBytes", "skuId", "skuCode", "firmwareVersion",
        "hardwareRevision", "apiVersion", "protocolVersion", "runtime"
    )
    private val RUNTIME_KEYS = setOf(
        "transport", "wsSchema", "wsPath", "wsPort", "wsProtocolVersion"
    )
}

object DeviceRuntimeCapabilitiesParser {

    fun parse(data: JSONObject): Result<DeviceRuntimeCapabilities> = runCatching {
        data.requireExactKeys(CAPABILITY_RESPONSE_KEYS, "device.capabilities.get.data")
        val capabilities = data.requireObject("capabilities")
        val limits = data.requireObject("limits")
        capabilities.requireExactKeys(CAPABILITY_KEYS, "device.capabilities.get.data.capabilities")
        limits.requireExactKeys(LIMIT_KEYS, "device.capabilities.get.data.limits")

        val featureWireValues = data.requireStringArray("supportedFeatures")
        val screenWireValues = data.requireStringArray("supportedScreens")
        require(featureWireValues.size == featureWireValues.toSet().size) {
            "supportedFeatures must not contain duplicate wire values."
        }
        require(screenWireValues.size == screenWireValues.toSet().size) {
            "supportedScreens must not contain duplicate wire values."
        }

        val featureKeys = when (val parsed = featureWireValues.parseAqlDeviceFeatureKeysExact()) {
            is AqlCatalogKeySet.Valid -> parsed.values
            is AqlCatalogKeySet.Invalid -> error(
                "supportedFeatures contains unknown exact keys: ${parsed.unknownWireValues.sorted()}"
            )
        }
        val screenKeys = when (val parsed = screenWireValues.parseAqlDeviceScreenKeysExact()) {
            is AqlCatalogKeySet.Valid -> parsed.values
            is AqlCatalogKeySet.Invalid -> error(
                "supportedScreens contains unknown exact keys: ${parsed.unknownWireValues.sorted()}"
            )
        }

        DeviceRuntimeCapabilities(
            capabilities = DeviceCapabilitySet(
                light = capabilities.requireExactBoolean("light"),
                manualLight = capabilities.requireExactBoolean("manualLight"),
                lightProgram = capabilities.requireExactBoolean("lightProgram"),
                lightPresets = capabilities.requireExactBoolean("lightPresets"),
                lightSimulation = capabilities.requireExactBoolean("lightSimulation"),
                fan = capabilities.requireExactBoolean("fan"),
                cooling = capabilities.requireExactBoolean("cooling"),
                temperature = capabilities.requireExactBoolean("temperature"),
                standaloneTimer = capabilities.requireExactBoolean("standaloneTimer"),
                dosing = capabilities.requireExactBoolean("dosing"),
                timeSync = capabilities.requireExactBoolean("timeSync"),
                ota = capabilities.requireExactBoolean("ota")
            ),
            limits = DeviceLimitSet(
                lightChannelCount = limits.requireExactInt("lightChannelCount"),
                fanOutputCount = limits.requireExactInt("fanOutputCount"),
                temperatureSensorCount = limits.requireExactInt("temperatureSensorCount"),
                timerChannelCount = limits.requireExactInt("timerChannelCount"),
                dosingChannelCount = limits.requireExactInt("dosingChannelCount")
            ),
            supportedFeatures = featureKeys,
            supportedScreens = screenKeys
        )
    }

    private val CAPABILITY_RESPONSE_KEYS = setOf(
        "capabilities", "limits", "supportedFeatures", "supportedScreens"
    )
    private val CAPABILITY_KEYS = setOf(
        "light", "manualLight", "lightProgram", "lightPresets", "lightSimulation",
        "fan", "cooling", "temperature", "standaloneTimer", "dosing", "timeSync", "ota"
    )
    private val LIMIT_KEYS = setOf(
        "lightChannelCount", "fanOutputCount", "temperatureSensorCount",
        "timerChannelCount", "dosingChannelCount"
    )
}

private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
    val actual = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }
    require(actual == expected) {
        "$label keys differ from the commercial contract; expected=$expected actual=$actual"
    }
}

private fun JSONObject.requireObject(key: String): JSONObject {
    require(has(key) && !isNull(key)) { "$key is required." }
    val value = get(key)
    require(value is JSONObject) { "$key must be a JSON object." }
    return value
}

private fun JSONObject.requireExactString(key: String): String {
    val value = requireExactOptionalString(key)
    require(value.isNotEmpty()) { "$key must not be empty." }
    return value
}

private fun JSONObject.requireExactOptionalString(key: String): String {
    require(has(key) && !isNull(key)) { "$key is required." }
    val value = get(key)
    require(value is String) { "$key must be a string." }
    require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace())) {
        "$key must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) { "$key must not contain control characters." }
    return value
}

private fun JSONObject.requireExactBoolean(key: String): Boolean {
    require(has(key) && !isNull(key)) { "$key is required." }
    val value = get(key)
    require(value is Boolean) { "$key must be a boolean." }
    return value
}

private fun JSONObject.requireExactInt(key: String): Int {
    require(has(key) && !isNull(key)) { "$key is required." }
    val value = get(key)
    require(value is Number) { "$key must be an integer." }
    val asDouble = value.toDouble()
    val asLong = value.toLong()
    require(asDouble.isFinite() && asDouble == asLong.toDouble()) { "$key must be an integer." }
    require(asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        "$key is outside the supported integer range."
    }
    return asLong.toInt()
}

private fun JSONObject.requireStringArray(key: String): List<String> {
    require(has(key) && !isNull(key)) { "$key is required." }
    val value = get(key)
    require(value is JSONArray) { "$key must be a JSON array." }
    return buildList {
        repeat(value.length()) { index ->
            val item = value.get(index)
            require(item is String) { "$key[$index] must be a string." }
            require(item.isNotEmpty()) { "$key[$index] must not be empty." }
            require(!item.first().isWhitespace() && !item.last().isWhitespace()) {
                "$key[$index] must not contain surrounding whitespace."
            }
            require(item.none(Char::isISOControl)) {
                "$key[$index] must not contain control characters."
            }
            add(item)
        }
    }
}
