package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceLightRegime(
    val wireValue: String
) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off");

    companion object {
        fun fromWire(value: String): DeviceLightRegime {
            return when (value.trim().lowercase()) {
                "auto", "schedule", "program" -> AUTO
                "on", "manual_on" -> ON
                else -> OFF
            }
        }
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
    val channelKind: String,
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
    val color: Int,
    val lumen: Double,
    val lux: Double,
    val watt: Double,
    val editable: DeviceLightChannelEditable
)

data class DeviceLightProgramPointStatus(
    val index: Int,
    val timeMs: Long,
    val time: String,
    val value: Double,
    val percent: Double
)

data class DeviceLightProgramStatus(
    val listIndex: Int,
    val index: Int,
    val channelKey: String,
    val bound: Boolean,
    val pointCount: Int,
    val points: List<DeviceLightProgramPointStatus>
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
    val channelEdit: Int,
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
    val percent: Double? = null,
    val value: Double? = null
) {
    init {
        require(channelKey.isNotBlank()) { "channelKey must not be blank." }
        require(percent != null || value != null) { "manual channel requires percent or value." }

        if (percent != null) {
            require(percent in 0.0..100.0) { "percent must be between 0 and 100." }
        }

        if (value != null) {
            require(value in 0.0..1.0) { "value must be between 0 and 1." }
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
            .put(DeviceLightRuntimeContract.Field.CHANNEL_KEY, channelKey)

        if (percent != null) {
            json.put(DeviceLightRuntimeContract.Field.PERCENT, percent)
        } else if (value != null) {
            json.put(DeviceLightRuntimeContract.Field.VALUE, value)
        }

        return json
    }
}

data class DeviceLightManualSetPayload(
    val channels: List<DeviceLightManualChannelPayload> = emptyList(),
    val durationMs: Long? = DeviceLightRuntimeContract.Limit.DEFAULT_MANUAL_DURATION_MS,
    val clear: Boolean = false
) {
    init {
        require(channels.size <= DeviceLightRuntimeContract.Limit.MAX_MANUAL_CHANNELS) {
            "Manual light supports at most ${DeviceLightRuntimeContract.Limit.MAX_MANUAL_CHANNELS} channels."
        }

        if (!clear) {
            require(channels.isNotEmpty()) { "manual.set requires channels when clear=false." }
            val duration = durationMs ?: DeviceLightRuntimeContract.Limit.DEFAULT_MANUAL_DURATION_MS
            require(duration in DeviceLightRuntimeContract.Limit.MIN_MANUAL_DURATION_MS..DeviceLightRuntimeContract.Limit.MAX_MANUAL_DURATION_MS) {
                "durationMs must be between ${DeviceLightRuntimeContract.Limit.MIN_MANUAL_DURATION_MS} and ${DeviceLightRuntimeContract.Limit.MAX_MANUAL_DURATION_MS}."
            }
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
            .put(DeviceLightRuntimeContract.Field.CLEAR, clear)

        if (!clear) {
            json.put(
                DeviceLightRuntimeContract.Field.DURATION_MS,
                durationMs ?: DeviceLightRuntimeContract.Limit.DEFAULT_MANUAL_DURATION_MS
            )
        }

        if (channels.isNotEmpty()) {
            json.put(
                DeviceLightRuntimeContract.Field.CHANNELS,
                JSONArray(channels.map { it.toJson() })
            )
        }

        return json
    }
}

data class DeviceLightChannelRegimeSetPayload(
    val channelKey: String,
    val regime: DeviceLightRegime,
    val save: Boolean = true
) {
    init {
        require(channelKey.isNotBlank()) { "channelKey must not be blank." }
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put(DeviceLightRuntimeContract.Field.CHANNEL_KEY, channelKey)
            .put(DeviceLightRuntimeContract.Field.REGIME, regime.wireValue)
            .put(DeviceLightRuntimeContract.Field.SAVE, save)
    }
}

data class DeviceLightProgramPointPayload(
    val timeMs: Long? = null,
    val time: String? = null,
    val percent: Double? = null,
    val value: Double? = null
) {
    init {
        require(timeMs != null || !time.isNullOrBlank()) { "program point requires timeMs or time." }
        require(percent != null || value != null) { "program point requires percent or value." }

        if (timeMs != null) {
            require(timeMs >= 0L) { "timeMs must be zero or greater." }
        }

        if (percent != null) {
            require(percent in 0.0..100.0) { "percent must be between 0 and 100." }
        }

        if (value != null) {
            require(value in 0.0..1.0) { "value must be between 0 and 1." }
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()

        if (timeMs != null) {
            json.put(DeviceLightRuntimeContract.Field.TIME_MS, timeMs)
        } else if (!time.isNullOrBlank()) {
            json.put(DeviceLightRuntimeContract.Field.TIME, time)
        }

        if (percent != null) {
            json.put(DeviceLightRuntimeContract.Field.PERCENT, percent)
        } else if (value != null) {
            json.put(DeviceLightRuntimeContract.Field.VALUE, value)
        }

        return json
    }
}

data class DeviceLightProgramApplyPayload(
    val channelKey: String,
    val points: List<DeviceLightProgramPointPayload>,
    val programIndex: Int? = null,
    val save: Boolean = true
) {
    init {
        require(channelKey.isNotBlank()) { "channelKey must not be blank." }
        require(points.isNotEmpty()) { "light program points must not be empty." }
        require(points.size <= DeviceLightRuntimeContract.Limit.MAX_PROGRAM_POINTS) {
            "Light program supports at most ${DeviceLightRuntimeContract.Limit.MAX_PROGRAM_POINTS} points."
        }

        if (programIndex != null) {
            require(programIndex >= 0) { "programIndex must be zero or greater." }
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
            .put(DeviceLightRuntimeContract.Field.CHANNEL_KEY, channelKey)
            .put(DeviceLightRuntimeContract.Field.POINTS, JSONArray(points.map { it.toJson() }))
            .put(DeviceLightRuntimeContract.Field.SAVE, save)

        if (programIndex != null) {
            json.put(DeviceLightRuntimeContract.Field.PROGRAM_INDEX, programIndex)
        }

        return json
    }
}

data class DeviceLightProgramDeletePayload(
    val programIndex: Int,
    val save: Boolean = true
) {
    init {
        require(programIndex >= 0) { "programIndex must be zero or greater." }
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put(DeviceLightRuntimeContract.Field.PROGRAM_INDEX, programIndex)
            .put(DeviceLightRuntimeContract.Field.SAVE, save)
    }
}

data class DeviceLightCommandResult(
    val sent: Boolean,
    val skipped: Boolean = false,
    val module: String = DeviceLightRuntimeContract.MODULE,
    val action: String,
    val messageId: String = "",
    val errorMessage: String = ""
) {
    val isSuccess: Boolean
        get() = sent && errorMessage.isBlank()
}
