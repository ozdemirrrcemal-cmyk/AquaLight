package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceLightRegime(val wireValue: String) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off");

    companion object {
        private val byWireValue = entries.associateBy(DeviceLightRegime::wireValue)
        fun fromWireExact(value: String): DeviceLightRegime? = byWireValue[value]
    }
}

enum class DeviceLightChannelKind(val wireValue: String) {
    GPIO("gpio"),
    DIGITAL("digital"),
    NONE("none");

    companion object {
        private val byWireValue = entries.associateBy(DeviceLightChannelKind::wireValue)
        fun fromWireExact(value: String): DeviceLightChannelKind? = byWireValue[value]
    }
}

data class DeviceLightRuntimeCapabilities(
    val module: String,
    val readOnly: Boolean,
    val supportsManualSet: Boolean,
    val supportsChannelRegimeSet: Boolean,
    val supportsProgramApply: Boolean,
    val supportsProgramDelete: Boolean,
    val supportsLiveEdit: Boolean,
    val event: String
)

data class DeviceLightChannelEditable(
    val hardware: Boolean,
    val displayName: Boolean,
    val color: Boolean,
    val hardwareCalibration: Boolean
)

data class DeviceLightChannelStatus(
    val index: Int,
    val key: String,
    val name: String,
    val displayName: String,
    val profileManaged: Boolean,
    val regime: DeviceLightRegime,
    val channelKind: DeviceLightChannelKind,
    val gpio: Int,
    val ledcChannel: Int,
    val group: Int,
    val valueNow: Double,
    val valueAuto: Double,
    val valueManual: Double,
    val manualTimeoutMs: Long,
    val percentNow: Double,
    val percentAuto: Double,
    val percentManual: Double,
    val invert: Boolean,
    val pwmResolutionBits: Int,
    val pwmFrequencyHz: Int,
    val color: Long,
    val lumen: Double,
    val lux: Double,
    val watt: Double,
    val editable: DeviceLightChannelEditable
)

data class DeviceLightChannelSnapshot(
    val channel: DeviceLightChannelStatus,
    val listIndex: Int
)

data class DeviceLightProgramPointStatus(
    val timeMs: Long,
    val time: String,
    val value: Double,
    val percent: Double
)

data class DeviceLightProgramPointSnapshot(
    val index: Int,
    val point: DeviceLightProgramPointStatus
)

data class DeviceLightProgramStatus(
    val index: Int,
    val channelKey: String,
    val bound: Boolean,
    val pointCount: Int,
    val points: List<DeviceLightProgramPointStatus>
)

data class DeviceLightProgramSnapshot(
    val listIndex: Int,
    val index: Int,
    val channelKey: String,
    val bound: Boolean,
    val pointCount: Int,
    val points: List<DeviceLightProgramPointSnapshot>
)

data class DeviceLightStatus(
    val supported: Boolean,
    val manualSupported: Boolean,
    val programSupported: Boolean,
    val presetsSupported: Boolean,
    val simulationSupported: Boolean,
    val channelCount: Int,
    val programCount: Int,
    val liveEditEnabled: Boolean,
    val channelEdit: String,
    val powerLimitW: Double,
    val lockLoop: Boolean,
    val temperatureDownStepPercent: Double,
    val temperatureRecoveryMs: Long,
    val lightCorrectionFactor: Double,
    val uptimeMs: Long,
    val channels: List<DeviceLightChannelStatus>,
    val programs: List<DeviceLightProgramStatus>,
    val runtime: DeviceLightRuntimeCapabilities
)

data class DeviceLightManualChannelPayload(
    val channelKey: String,
    val percent: Double
) {
    val canonicalChannelKey: String = normalizeLightChannelKey(channelKey)

    init {
        requireFiniteRange(percent, 0.0, 100.0, "percent")
    }

    fun toJson(clear: Boolean): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.CHANNEL_KEY, canonicalChannelKey)
        .apply {
            if (!clear) put(DeviceLightRuntimeContract.Field.PERCENT, percent)
        }
}

data class DeviceLightManualSetPayload(
    val channels: List<DeviceLightManualChannelPayload> = emptyList(),
    val durationMs: Long? = DeviceLightRuntimeContract.Limit.DEFAULT_MANUAL_DURATION_MS,
    val clear: Boolean = false
) {
    init {
        require(channels.size <= DeviceLightRuntimeContract.Limit.MAX_MANUAL_CHANNELS)
        requireUniqueLightKeys(channels.map(DeviceLightManualChannelPayload::canonicalChannelKey))
        if (clear) {
            require(durationMs == null) { "durationMs is forbidden when clear=true." }
        } else {
            require(channels.isNotEmpty()) { "manual.set requires channels when clear=false." }
            val duration = requireNotNull(durationMs)
            require(duration in DeviceLightRuntimeContract.Limit.MIN_MANUAL_DURATION_MS..
                DeviceLightRuntimeContract.Limit.MAX_MANUAL_DURATION_MS)
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.CLEAR, clear)
        .apply {
            if (!clear) put(DeviceLightRuntimeContract.Field.DURATION_MS, durationMs)
            if (channels.isNotEmpty()) {
                put(
                    DeviceLightRuntimeContract.Field.CHANNELS,
                    JSONArray(channels.map { channel -> channel.toJson(clear) })
                )
            }
        }
}

data class DeviceLightChannelRegimeSetPayload(
    val channelKey: String,
    val regime: DeviceLightRegime,
    val save: Boolean = true
) {
    val canonicalChannelKey: String = normalizeLightChannelKey(channelKey)

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.CHANNEL_KEY, canonicalChannelKey)
        .put(DeviceLightRuntimeContract.Field.REGIME, regime.wireValue)
        .put(DeviceLightRuntimeContract.Field.SAVE, save)
}

data class DeviceLightProgramPointPayload(
    val timeMs: Long,
    val percent: Double
) {
    init {
        require(timeMs in 0 until DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY)
        requireFiniteRange(percent, 0.0, 100.0, "percent")
    }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.TIME_MS, timeMs)
        .put(DeviceLightRuntimeContract.Field.PERCENT, percent)
}

data class DeviceLightProgramApplyPayload(
    val channelKey: String,
    val points: List<DeviceLightProgramPointPayload>,
    val programIndex: Int? = null,
    val save: Boolean = true
) {
    val canonicalChannelKey: String = normalizeLightChannelKey(channelKey)

    init {
        require(points.isNotEmpty())
        require(points.size <= DeviceLightRuntimeContract.Limit.MAX_PROGRAM_POINTS)
        programIndex?.let { require(it >= 0) }
    }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.CHANNEL_KEY, canonicalChannelKey)
        .put(DeviceLightRuntimeContract.Field.POINTS, JSONArray(points.map { it.toJson() }))
        .put(DeviceLightRuntimeContract.Field.SAVE, save)
        .apply {
            programIndex?.let { put(DeviceLightRuntimeContract.Field.PROGRAM_INDEX, it) }
        }
}

data class DeviceLightProgramDeletePayload(
    val programIndex: Int,
    val save: Boolean = true
) {
    init { require(programIndex >= 0) }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.PROGRAM_INDEX, programIndex)
        .put(DeviceLightRuntimeContract.Field.SAVE, save)
}

data class DeviceLightManualSetResult(
    val operation: String,
    val manualActive: Boolean,
    val durationMs: Long,
    val runtimeTransport: String,
    val command: String,
    val event: String,
    val channels: List<DeviceLightChannelSnapshot>,
    val affectedChannelCount: Int,
    val saved: Boolean
)

data class DeviceLightChannelRegimeSetResult(
    val operation: String,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val channelKey: String,
    val regime: DeviceLightRegime,
    val runtimeTransport: String,
    val command: String,
    val event: String,
    val channel: DeviceLightChannelSnapshot
)

data class DeviceLightProgramApplyResult(
    val operation: String,
    val created: Boolean,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val programIndex: Int,
    val channelKey: String,
    val channelListIndex: Int,
    val runtimeTransport: String,
    val command: String,
    val event: String,
    val program: DeviceLightProgramSnapshot
)

data class DeviceLightProgramDeleteResult(
    val operation: String,
    val deleted: Boolean,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val programIndex: Int,
    val deletedListIndex: Int,
    val channelKey: String,
    val deletedPointCount: Int,
    val programCount: Int,
    val runtimeTransport: String,
    val command: String,
    val event: String
)

/** Firmware response keys must already be canonical; no response normalization is allowed. */
internal fun canonicalLightChannelKey(raw: String): String {
    require(raw == raw.trim()) { "Firmware channelKey contains surrounding whitespace." }
    require(raw == raw.lowercase()) { "Firmware channelKey is not canonical lowercase." }
    return validateLightChannelKey(raw)
}

private fun normalizeLightChannelKey(raw: String): String =
    validateLightChannelKey(raw.trim().lowercase())

private fun validateLightChannelKey(value: String): String {
    require(value.isNotEmpty() && value != "-" && value != "none")
    require(value.none(Char::isISOControl))
    require(LIGHT_CHANNEL_KEY_REGEX.matches(value)) { "channelKey has an invalid wire format." }
    return value
}

private fun requireUniqueLightKeys(values: List<String>) {
    require(values.size == values.toSet().size) { "Duplicate channelKey values are forbidden." }
}

private fun requireFiniteRange(value: Double, minimum: Double, maximum: Double, field: String) {
    require(value.isFinite()) { "$field must be finite." }
    require(value in minimum..maximum) { "$field is outside $minimum..$maximum." }
}

private val LIGHT_CHANNEL_KEY_REGEX = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
