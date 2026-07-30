package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONObject

object DeviceLightTemperatureProtectionParser {

    fun parseStatus(data: JSONObject): Result<DeviceLightTemperatureProtectionStatus> =
        runCatching { parseStatusExact(data) }

    fun parseSetResult(data: JSONObject): Result<DeviceLightTemperatureProtectionSetResult> =
        runCatching {
            data.requireExactKeys(SET_RESULT_KEYS, "light.temperature-protection.set.data")
            require(
                data.requiredExactString(DeviceLightRuntimeContract.Field.OPERATION) ==
                    DeviceLightRuntimeContract.Operation.TEMPERATURE_PROTECTION_SET
            )
            require(
                data.requiredExactString(DeviceLightRuntimeContract.Field.RUNTIME_TRANSPORT) ==
                    DeviceLightRuntimeContract.Transport.WEBSOCKET
            )
            require(
                data.requiredExactString(DeviceLightRuntimeContract.Field.COMMAND) ==
                    DeviceLightRuntimeContract.QualifiedCommand.TEMPERATURE_PROTECTION_SET
            )
            require(
                data.requiredExactString(DeviceLightRuntimeContract.Field.EVENT) ==
                    DeviceLightRuntimeContract.Event.STATUS_CHANGED
            )

            val changed = data.requiredExactBoolean(DeviceLightRuntimeContract.Field.CHANGED)
            val saved = data.requiredExactBoolean(DeviceLightRuntimeContract.Field.SAVED)
            val saveRequested = data.requiredExactBoolean(
                DeviceLightRuntimeContract.Field.SAVE_REQUESTED
            )
            require(saved == saveRequested) {
                "Firmware persistence echo differs from saveRequested."
            }

            val status = parseStatusExact(
                data.requiredObject(DeviceLightRuntimeContract.Field.STATUS)
            )
            require(status.supported) {
                "Successful set response must report supported temperature protection."
            }

            DeviceLightTemperatureProtectionSetResult(
                changed = changed,
                saved = saved,
                saveRequested = saveRequested,
                status = status
            )
        }

    private fun parseStatusExact(data: JSONObject): DeviceLightTemperatureProtectionStatus {
        data.requireExactKeys(STATUS_KEYS, "light.temperature-protection.status.get.data")
        val supported = data.requiredExactBoolean(DeviceLightRuntimeContract.Field.SUPPORTED)
        val protection = parseProtection(
            data.requiredObject(DeviceLightRuntimeContract.Field.TEMPERATURE_PROTECTION)
        )
        val runtime = parseRuntime(data.requiredObject(DeviceLightRuntimeContract.Field.RUNTIME))

        require(protection.supported == supported) {
            "Top-level and nested temperature-protection support differ."
        }
        require(runtime.supportsSet == supported) {
            "Runtime supportsSet differs from product support."
        }

        validateProtection(protection)

        return DeviceLightTemperatureProtectionStatus(
            supported = supported,
            temperatureProtection = protection,
            runtime = runtime
        )
    }

    private fun parseProtection(data: JSONObject): DeviceLightTemperatureProtectionSnapshot {
        data.requireExactKeys(PROTECTION_KEYS, "light temperature-protection snapshot")
        return DeviceLightTemperatureProtectionSnapshot(
            supported = data.requiredExactBoolean(DeviceLightRuntimeContract.Field.SUPPORTED),
            active = data.requiredExactBoolean(DeviceLightRuntimeContract.Field.ACTIVE),
            thresholdEditable = data.requiredExactBoolean(
                DeviceLightRuntimeContract.Field.THRESHOLD_EDITABLE
            ),
            thresholdC = data.requiredNullableExactDouble(
                DeviceLightRuntimeContract.Field.THRESHOLD_C
            ),
            minimumC = data.requiredNullableExactDouble(
                DeviceLightRuntimeContract.Field.MINIMUM_C
            ),
            maximumC = data.requiredNullableExactDouble(
                DeviceLightRuntimeContract.Field.MAXIMUM_C
            )
        )
    }

    private fun parseRuntime(
        data: JSONObject
    ): DeviceLightTemperatureProtectionRuntimeCapabilities {
        data.requireExactKeys(RUNTIME_KEYS, "light temperature-protection runtime")
        val runtime = DeviceLightTemperatureProtectionRuntimeCapabilities(
            module = data.requiredExactString(DeviceLightRuntimeContract.Field.MODULE),
            readOnly = data.requiredExactBoolean(DeviceLightRuntimeContract.Field.READ_ONLY),
            supportsStatusGet = data.requiredExactBoolean(
                DeviceLightRuntimeContract.Field.SUPPORTS_STATUS_GET
            ),
            supportsSet = data.requiredExactBoolean(
                DeviceLightRuntimeContract.Field.SUPPORTS_SET
            ),
            event = data.requiredExactString(DeviceLightRuntimeContract.Field.EVENT)
        )

        require(runtime.module == DeviceLightRuntimeContract.MODULE)
        require(!runtime.readOnly)
        require(runtime.supportsStatusGet)
        require(runtime.event == DeviceLightRuntimeContract.Event.STATUS_CHANGED)
        return runtime
    }

    private fun validateProtection(snapshot: DeviceLightTemperatureProtectionSnapshot) {
        if (!snapshot.supported) {
            require(!snapshot.active)
            require(!snapshot.thresholdEditable)
            require(snapshot.thresholdC == null)
            require(snapshot.minimumC == null)
            require(snapshot.maximumC == null)
            return
        }

        require(snapshot.thresholdEditable)
        val thresholdC = requireNotNull(snapshot.thresholdC)
        val minimumC = requireNotNull(snapshot.minimumC)
        val maximumC = requireNotNull(snapshot.maximumC)
        require(minimumC == DeviceLightRuntimeContract.Limit.MIN_TEMPERATURE_PROTECTION_C)
        require(maximumC == DeviceLightRuntimeContract.Limit.MAX_TEMPERATURE_PROTECTION_C)
        require(thresholdC in minimumC..maximumC)
    }

    private val STATUS_KEYS = setOf(
        DeviceLightRuntimeContract.Field.SUPPORTED,
        DeviceLightRuntimeContract.Field.TEMPERATURE_PROTECTION,
        DeviceLightRuntimeContract.Field.RUNTIME
    )
    private val PROTECTION_KEYS = setOf(
        DeviceLightRuntimeContract.Field.SUPPORTED,
        DeviceLightRuntimeContract.Field.ACTIVE,
        DeviceLightRuntimeContract.Field.THRESHOLD_EDITABLE,
        DeviceLightRuntimeContract.Field.THRESHOLD_C,
        DeviceLightRuntimeContract.Field.MINIMUM_C,
        DeviceLightRuntimeContract.Field.MAXIMUM_C
    )
    private val RUNTIME_KEYS = setOf(
        DeviceLightRuntimeContract.Field.MODULE,
        DeviceLightRuntimeContract.Field.READ_ONLY,
        DeviceLightRuntimeContract.Field.SUPPORTS_STATUS_GET,
        DeviceLightRuntimeContract.Field.SUPPORTS_SET,
        DeviceLightRuntimeContract.Field.EVENT
    )
    private val SET_RESULT_KEYS = setOf(
        DeviceLightRuntimeContract.Field.OPERATION,
        DeviceLightRuntimeContract.Field.CHANGED,
        DeviceLightRuntimeContract.Field.SAVED,
        DeviceLightRuntimeContract.Field.SAVE_REQUESTED,
        DeviceLightRuntimeContract.Field.RUNTIME_TRANSPORT,
        DeviceLightRuntimeContract.Field.COMMAND,
        DeviceLightRuntimeContract.Field.EVENT,
        DeviceLightRuntimeContract.Field.STATUS
    )
}

private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
    val actual = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }
    require(actual == expected) { "$label keys differ from the firmware contract." }
}

private fun JSONObject.requiredObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be an object.")

private fun JSONObject.requiredExactString(key: String): String {
    val value = get(key) as? String ?: error("$key must be a string.")
    require(value.isNotEmpty()) { "$key must not be empty." }
    require(!value.first().isWhitespace() && !value.last().isWhitespace())
    require(value.none(Char::isISOControl))
    return value
}

private fun JSONObject.requiredExactBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be a boolean.")

private fun JSONObject.requiredNullableExactDouble(key: String): Double? {
    val value = get(key)
    if (value == JSONObject.NULL) return null
    val number = value as? Number ?: error("$key must be numeric or null.")
    return number.toDouble().also { require(it.isFinite()) }
}
