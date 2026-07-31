package com.aqua.aqualight.data.devices.model

import com.aqua.aqualight.data.devices.contract.AqlWsContract

@JvmInline
value class DeviceProductKey(val value: String) {
    init { requireExact(value, "productKey", PRODUCT_KEY_PATTERN) }
}

@JvmInline
value class DeviceProductId(val value: String) {
    init {
        requireExact(value, "productId", PRODUCT_ID_PATTERN)
        require(value.startsWith(AQUALIGHT_PRODUCT_ID_PREFIX)) {
            "productId must use the AquaLight namespace."
        }
    }
}

@JvmInline
value class DeviceProductLine(val value: String) {
    init { requireExact(value, "line", LOWER_SNAKE_PATTERN) }
}

@JvmInline
value class DeviceProductModel(val value: String) {
    init { requireExact(value, "model", LOWER_SNAKE_PATTERN) }
}

@JvmInline
value class DeviceSkuId(val value: String) {
    init { requireExact(value, "skuId", PRODUCT_ID_PATTERN) }
}

@JvmInline
value class DeviceSkuCode(val value: String) {
    init { requireExact(value, "skuCode", SKU_CODE_PATTERN) }
}

@JvmInline
value class DeviceHardwareRevision(val value: String) {
    init { requireExact(value, "hardwareRevision", HARDWARE_REVISION_PATTERN) }
}

@JvmInline
value class DeviceFirmwareVersion(val value: String) {
    init { requireExactText(value, "firmwareVersion") }
}

@JvmInline
value class DeviceApiVersion(val value: Int) {
    init {
        require(value == SUPPORTED_DEVICE_API_VERSION) {
            "apiVersion is incompatible with the Android commercial contract."
        }
    }
}

@JvmInline
value class DeviceProtocolVersion(val value: Int) {
    init {
        require(value == AqlWsContract.PROTOCOL_VERSION) {
            "protocolVersion is incompatible with the Android WebSocket contract."
        }
    }
}

/** Complete runtime product identity required before Android may expose device functionality. */
data class DeviceRuntimeIdentity(
    val deviceUid: DeviceUid,
    val productKey: DeviceProductKey,
    val productId: DeviceProductId,
    val family: DeviceFamily,
    val line: DeviceProductLine,
    val model: DeviceProductModel,
    val brand: String,
    /** Immutable compiled product name. */
    val displayName: String,
    /** User-owned persisted/runtime override. Empty means use [displayName]. */
    val customName: String = "",
    /** Firmware-resolved presentation name; must match the identity split exactly. */
    val effectiveDisplayName: String = customName.ifBlank { displayName },
    val nameEditable: Boolean = true,
    val customNameMaxBytes: Int = DEVICE_CUSTOM_NAME_MAX_BYTES,
    val skuId: DeviceSkuId,
    val skuCode: DeviceSkuCode,
    val hardwareRevision: DeviceHardwareRevision,
    val firmwareVersion: DeviceFirmwareVersion,
    val apiVersion: DeviceApiVersion,
    val protocolVersion: DeviceProtocolVersion
) {
    init {
        require(family != DeviceFamily.UNKNOWN) {
            "Runtime identity must contain an exact commercial product family."
        }
        requireExactText(deviceUid.value, "deviceUid")
        requireExactText(brand, "brand")
        requireExactText(displayName, "displayName")
        requireOptionalExactText(customName, "customName")
        requireExactText(effectiveDisplayName, "effectiveDisplayName")
        require(nameEditable) { "Commercial firmware must advertise editable device names." }
        require(customNameMaxBytes == DEVICE_CUSTOM_NAME_MAX_BYTES) {
            "customNameMaxBytes is incompatible with the firmware contract."
        }
        require(customName.toByteArray(Charsets.UTF_8).size <= customNameMaxBytes) {
            "customName exceeds the firmware UTF-8 byte limit."
        }
        require(effectiveDisplayName == customName.ifBlank { displayName }) {
            "effectiveDisplayName does not match customName/displayName fallback rules."
        }
    }

    val compatibilityIdentity: DeviceCompatibilityIdentity
        get() = DeviceCompatibilityIdentity(
            productKey = productKey,
            productId = productId,
            model = model,
            hardwareRevision = hardwareRevision
        )
}

/** Authenticated runtime endpoint values embedded in `device.identity.get`. */
data class DeviceRuntimeTransportMetadata(
    val transport: String,
    val wsSchema: String,
    val wsPath: String,
    val wsPort: Int,
    val wsProtocolVersion: Int
) {
    init {
        require(transport == RUNTIME_TRANSPORT) {
            "device.identity.get runtime transport is incompatible."
        }
        require(wsSchema == AqlWsContract.SCHEMA) {
            "device.identity.get runtime schema is incompatible."
        }
        require(wsPath == AqlWsContract.DEFAULT_PATH) {
            "device.identity.get runtime path is incompatible."
        }
        require(wsPort == AqlWsContract.DEFAULT_PORT) {
            "device.identity.get runtime port is incompatible."
        }
        require(wsProtocolVersion == AqlWsContract.PROTOCOL_VERSION) {
            "device.identity.get runtime protocol version is incompatible."
        }
    }
}

/** Full exact identity response, including fields required during initial provisioning. */
data class DeviceRuntimeIdentityEnvelope(
    val identity: DeviceRuntimeIdentity,
    val shortId: String,
    val serialNumber: String,
    val firmwareSerial: String,
    val macAddress: String,
    val setupCode: String,
    val runtime: DeviceRuntimeTransportMetadata
) {
    init {
        requireExactText(shortId, "shortId")
        requireExactText(serialNumber, "serialNumber")
        requireExactText(firmwareSerial, "firmwareSerial")
        requireExactText(macAddress, "macAddress")
        requireExactText(setupCode, "setupCode")
    }
}

/** Exact package-selection tuple shared by runtime identity and OTA compatibility. */
data class DeviceCompatibilityIdentity(
    val productKey: DeviceProductKey,
    val productId: DeviceProductId,
    val model: DeviceProductModel,
    val hardwareRevision: DeviceHardwareRevision
)

private fun requireExact(value: String, field: String, pattern: Regex) {
    requireExactText(value, field)
    require(pattern.matches(value)) { "$field has an invalid commercial wire format." }
}

private fun requireExactText(value: String, field: String) {
    require(value.isNotEmpty()) { "$field must not be empty." }
    requireOptionalExactText(value, field)
}

private fun requireOptionalExactText(value: String, field: String) {
    require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace())) {
        "$field must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) { "$field must not contain control characters." }
}

const val DEVICE_CUSTOM_NAME_MAX_BYTES = 64

private val PRODUCT_KEY_PATTERN = Regex("^[A-Z][A-Z0-9_]*$")
private val PRODUCT_ID_PATTERN = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)+$")
private val LOWER_SNAKE_PATTERN = Regex("^[a-z0-9]+(?:_[a-z0-9]+)*$")
private val SKU_CODE_PATTERN = Regex("^[A-Z0-9]+(?:-[A-Z0-9]+)*$")
private val HARDWARE_REVISION_PATTERN = Regex("^[0-9]+(?:\\.[0-9]+)*$")
private const val AQUALIGHT_PRODUCT_ID_PREFIX = "com.aqualight."
private const val SUPPORTED_DEVICE_API_VERSION = 1
private const val RUNTIME_TRANSPORT = "websocket"
