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

/** Complete authenticated `device.status.get` product envelope and module state. */
data class DeviceRuntimeModuleStatus(
    val productKey: DeviceProductKey,
    val family: DeviceFamily,
    val model: DeviceProductModel,
    val displayName: String,
    val uptimeMs: Long,
    val modules: DeviceRuntimeModules,
    val nameStatus: DeviceRuntimeNameStatus = DeviceRuntimeNameStatus.defaultFor(displayName)
) {
    init {
        require(family != DeviceFamily.UNKNOWN) {
            "Runtime module status must contain an exact commercial family."
        }
        require(displayName.isNotEmpty()) { "Runtime module status displayName must not be empty." }
        require(!displayName.first().isWhitespace() && !displayName.last().isWhitespace()) {
            "Runtime module status displayName must not contain surrounding whitespace."
        }
        require(displayName.none(Char::isISOControl)) {
            "Runtime module status displayName must not contain control characters."
        }
        require(uptimeMs >= 0L) { "Runtime module status uptimeMs must not be negative." }
        require(nameStatus.productDisplayName == displayName) {
            "Runtime status product display names differ."
        }
    }

    fun mismatchField(identity: DeviceRuntimeIdentity): String? = when {
        productKey != identity.productKey -> "productKey"
        family != identity.family -> "family"
        model != identity.model -> "model"
        displayName != identity.displayName -> "displayName"
        else -> null
    }

    fun mismatchField(envelope: DeviceRuntimeIdentityEnvelope): String? =
        mismatchField(envelope.identity) ?: when {
            nameStatus.productDisplayName != envelope.nameStatus.productDisplayName ->
                "device.productDisplayName"
            nameStatus.customName != envelope.nameStatus.customName -> "device.customName"
            nameStatus.effectiveDisplayName != envelope.nameStatus.effectiveDisplayName ->
                "device.effectiveDisplayName"
            nameStatus.nameEditable != envelope.nameStatus.nameEditable -> "device.nameEditable"
            nameStatus.customNameMaxBytes != envelope.nameStatus.customNameMaxBytes ->
                "device.customNameMaxBytes"
            else -> null
        }
}
