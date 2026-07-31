package com.aqua.aqualight.data.devices.model

/** Exact boolean keys emitted by `device.status.get.modules`. */
enum class DeviceRuntimeModuleKey(val wireValue: String) {
    LIGHT("light"),
    COOLING("cooling"),
    TEMPERATURE("temperature"),
    TIMER_API("timerApi"),
    TIMER_ENGINE("timerEngine"),
    DOSING("dosing"),
    NETWORK("network"),
    DISCOVERY("discovery"),
    FIRMWARE("firmware"),
    SYSTEM("system");

    companion object {
        private val byWireValue = entries.associateBy(DeviceRuntimeModuleKey::wireValue)
        fun fromWireExact(value: String): DeviceRuntimeModuleKey? = byWireValue[value]
    }
}

/** Complete runtime module object reported by firmware. */
data class DeviceRuntimeModules(
    val light: Boolean,
    val cooling: Boolean,
    val temperature: Boolean,
    val timerApi: Boolean,
    val timerEngine: Boolean,
    val dosing: Boolean,
    val network: Boolean,
    val discovery: Boolean,
    val firmware: Boolean,
    val system: Boolean
) {
    operator fun get(key: DeviceRuntimeModuleKey): Boolean = when (key) {
        DeviceRuntimeModuleKey.LIGHT -> light
        DeviceRuntimeModuleKey.COOLING -> cooling
        DeviceRuntimeModuleKey.TEMPERATURE -> temperature
        DeviceRuntimeModuleKey.TIMER_API -> timerApi
        DeviceRuntimeModuleKey.TIMER_ENGINE -> timerEngine
        DeviceRuntimeModuleKey.DOSING -> dosing
        DeviceRuntimeModuleKey.NETWORK -> network
        DeviceRuntimeModuleKey.DISCOVERY -> discovery
        DeviceRuntimeModuleKey.FIRMWARE -> firmware
        DeviceRuntimeModuleKey.SYSTEM -> system
    }

    val enabled: Set<DeviceRuntimeModuleKey>
        get() = DeviceRuntimeModuleKey.entries.filterTo(linkedSetOf()) { key -> this[key] }

    val exposesStandaloneTimerApi: Boolean get() = timerApi
    val usesInternalTimerEngine: Boolean get() = timerEngine
}

/** Exact `device.status.get.device` name policy. */
data class DeviceRuntimeDeviceNameStatus(
    val productDisplayName: String,
    val customName: String,
    val effectiveDisplayName: String,
    val editable: Boolean,
    val maxBytes: Int
) {
    init {
        requireExactNameText(productDisplayName, "productDisplayName")
        requireOptionalNameText(customName, "customName")
        requireExactNameText(effectiveDisplayName, "effectiveDisplayName")
        require(maxBytes == FIRMWARE_DEVICE_CUSTOM_NAME_MAX_BYTES) {
            "device.status.get.data.device.maxBytes differs from the pinned firmware contract."
        }
        require(customName.toByteArray(Charsets.UTF_8).size <= maxBytes) {
            "device.status.get.data.device.customName exceeds maxBytes."
        }
        require(effectiveDisplayName == customName.ifEmpty { productDisplayName }) {
            "device.status.get.data.device.effectiveDisplayName is inconsistent."
        }
    }

    fun mismatchField(identity: DeviceRuntimeIdentity): String? = when {
        productDisplayName != identity.displayName -> "device.productDisplayName"
        customName != identity.customName -> "device.customName"
        effectiveDisplayName != identity.effectiveDisplayName -> "device.effectiveDisplayName"
        editable != identity.nameEditable -> "device.editable"
        maxBytes != identity.customNameMaxBytes -> "device.maxBytes"
        else -> null
    }
}

/** Complete authenticated `device.status.get` product envelope and module state. */
data class DeviceRuntimeModuleStatus(
    val productKey: DeviceProductKey,
    val family: DeviceFamily,
    val model: DeviceProductModel,
    /** Immutable product display name from the `product` object. */
    val displayName: String,
    val uptimeMs: Long,
    val modules: DeviceRuntimeModules,
    val deviceName: DeviceRuntimeDeviceNameStatus = DeviceRuntimeDeviceNameStatus(
        productDisplayName = displayName,
        customName = "",
        effectiveDisplayName = displayName,
        editable = true,
        maxBytes = FIRMWARE_DEVICE_CUSTOM_NAME_MAX_BYTES
    )
) {
    init {
        require(family != DeviceFamily.UNKNOWN) {
            "Runtime module status must contain an exact commercial family."
        }
        requireExactNameText(displayName, "displayName")
        require(displayName == deviceName.productDisplayName) {
            "device.status.get product and device product display names differ."
        }
        require(uptimeMs >= 0L) { "Runtime module status uptimeMs must not be negative." }
    }

    fun mismatchField(identity: DeviceRuntimeIdentity): String? = when {
        productKey != identity.productKey -> "productKey"
        family != identity.family -> "family"
        model != identity.model -> "model"
        displayName != identity.displayName -> "displayName"
        else -> deviceName.mismatchField(identity)
    }
}

private fun requireExactNameText(value: String, field: String) {
    require(value.isNotEmpty()) { "$field must not be empty." }
    requireOptionalNameText(value, field)
}

private fun requireOptionalNameText(value: String, field: String) {
    if (value.isEmpty()) return
    require(!value.first().isWhitespace() && !value.last().isWhitespace()) {
        "$field must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) {
        "$field must not contain control characters."
    }
}
