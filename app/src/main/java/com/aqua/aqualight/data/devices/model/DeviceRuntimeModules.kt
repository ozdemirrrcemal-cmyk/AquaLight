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

/** Complete authenticated `device.status.get` product, device-name and module state. */
data class DeviceRuntimeModuleStatus(
    val productKey: DeviceProductKey,
    val family: DeviceFamily,
    val model: DeviceProductModel,
    /** Immutable product display name from `product.displayName`. */
    val displayName: String,
    val uptimeMs: Long,
    val modules: DeviceRuntimeModules,
    val customName: String = "",
    val effectiveDisplayName: String = customName.ifBlank { displayName },
    val nameEditable: Boolean = true,
    val customNameMaxBytes: Int = DEVICE_CUSTOM_NAME_MAX_BYTES
) {
    init {
        require(family != DeviceFamily.UNKNOWN) {
            "Runtime module status must contain an exact commercial family."
        }
        requireExactName(displayName, "displayName", allowEmpty = false)
        requireExactName(customName, "customName", allowEmpty = true)
        requireExactName(effectiveDisplayName, "effectiveDisplayName", allowEmpty = false)
        require(nameEditable) { "Commercial firmware must advertise editable device names." }
        require(customNameMaxBytes == DEVICE_CUSTOM_NAME_MAX_BYTES) {
            "Runtime module status customNameMaxBytes is incompatible."
        }
        require(customName.toByteArray(Charsets.UTF_8).size <= customNameMaxBytes) {
            "Runtime module status customName exceeds the UTF-8 byte limit."
        }
        require(effectiveDisplayName == customName.ifBlank { displayName }) {
            "Runtime module status effectiveDisplayName violates the fallback contract."
        }
        require(uptimeMs >= 0L) { "Runtime module status uptimeMs must not be negative." }
    }

    fun mismatchField(identity: DeviceRuntimeIdentity): String? = when {
        productKey != identity.productKey -> "productKey"
        family != identity.family -> "family"
        model != identity.model -> "model"
        displayName != identity.displayName -> "displayName"
        customName != identity.customName -> "customName"
        effectiveDisplayName != identity.effectiveDisplayName -> "effectiveDisplayName"
        nameEditable != identity.nameEditable -> "nameEditable"
        customNameMaxBytes != identity.customNameMaxBytes -> "customNameMaxBytes"
        else -> null
    }
}

private fun requireExactName(value: String, field: String, allowEmpty: Boolean) {
    require(allowEmpty || value.isNotEmpty()) { "$field must not be empty." }
    require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace())) {
        "$field must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) { "$field must not contain control characters." }
}
