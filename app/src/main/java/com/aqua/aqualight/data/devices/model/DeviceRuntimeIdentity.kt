package com.aqua.aqualight.data.devices.model

@JvmInline
value class DeviceProductKey(val value: String) {
    init {
        requireExact(value, "productKey", PRODUCT_KEY_PATTERN)
    }
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
    init {
        requireExact(value, "line", LOWER_SNAKE_PATTERN)
    }
}

@JvmInline
value class DeviceProductModel(val value: String) {
    init {
        requireExact(value, "model", LOWER_SNAKE_PATTERN)
    }
}

@JvmInline
value class DeviceSkuId(val value: String) {
    init {
        requireExact(value, "skuId", PRODUCT_ID_PATTERN)
    }
}

@JvmInline
value class DeviceSkuCode(val value: String) {
    init {
        requireExact(value, "skuCode", SKU_CODE_PATTERN)
    }
}

@JvmInline
value class DeviceHardwareRevision(val value: String) {
    init {
        requireExact(value, "hardwareRevision", HARDWARE_REVISION_PATTERN)
    }
}

@JvmInline
value class DeviceFirmwareVersion(val value: String) {
    init {
        requireExactText(value, "firmwareVersion")
    }
}

@JvmInline
value class DeviceApiVersion(val value: Int) {
    init {
        require(value > 0) { "apiVersion must be greater than zero." }
    }
}

@JvmInline
value class DeviceProtocolVersion(val value: Int) {
    init {
        require(value > 0) { "protocolVersion must be greater than zero." }
    }
}

/**
 * Complete runtime identity required before Android may route or expose device functionality.
 *
 * Descriptive labels remain strings because they are presentation metadata. Commercial matching
 * always uses [compatibilityIdentity].
 */
data class DeviceRuntimeIdentity(
    val deviceUid: DeviceUid,
    val productKey: DeviceProductKey,
    val productId: DeviceProductId,
    val family: DeviceFamily,
    val line: DeviceProductLine,
    val model: DeviceProductModel,
    val brand: String,
    val displayName: String,
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
    }

    val compatibilityIdentity: DeviceCompatibilityIdentity
        get() = DeviceCompatibilityIdentity(
            productKey = productKey,
            productId = productId,
            model = model,
            hardwareRevision = hardwareRevision
        )
}

/** Exact package-selection tuple shared by runtime identity and OTA compatibility. */
data class DeviceCompatibilityIdentity(
    val productKey: DeviceProductKey,
    val productId: DeviceProductId,
    val model: DeviceProductModel,
    val hardwareRevision: DeviceHardwareRevision
)

private fun requireExact(
    value: String,
    field: String,
    pattern: Regex
) {
    requireExactText(value, field)
    require(pattern.matches(value)) { "$field has an invalid commercial wire format." }
}

private fun requireExactText(value: String, field: String) {
    require(value.isNotEmpty()) { "$field must not be empty." }
    require(value.first().isWhitespace().not() && value.last().isWhitespace().not()) {
        "$field must not contain surrounding whitespace."
    }
    require(value.none { character -> character.isISOControl() }) {
        "$field must not contain control characters."
    }
}

private val PRODUCT_KEY_PATTERN = Regex("^[A-Z][A-Z0-9_]*$")
private val PRODUCT_ID_PATTERN = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)+$")
private val LOWER_SNAKE_PATTERN = Regex("^[a-z0-9]+(?:_[a-z0-9]+)*$")
private val SKU_CODE_PATTERN = Regex("^[A-Z0-9]+(?:-[A-Z0-9]+)*$")
private val HARDWARE_REVISION_PATTERN = Regex("^[0-9]+(?:\\.[0-9]+)*$")
private const val AQUALIGHT_PRODUCT_ID_PREFIX = "com.aqualight."
