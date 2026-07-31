package com.aqua.aqualight.data.devices.runtime.modules.dosing

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceDosingRegime(
    val wireValue: String
) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off");

    companion object {
        fun fromWire(value: String): DeviceDosingRegime {
            return values().singleOrNull { it.wireValue == value }
                ?: error("Unknown firmware dosing regime: $value")
        }
    }
}

data class DeviceDosingRuntimeCapabilities(
    val module: String,
    val readOnly: Boolean,
    val supportsConfigApply: Boolean,
    val supportsSchedules: Boolean,
    val supportsChannels: Boolean,
    val supportsPrime: Boolean,
    val supportsManualDose: Boolean,
    val supportsCalibrationWorkflow: Boolean,
    val supportsReservoirRefill: Boolean,
    val event: String
)

data class DeviceDosingChannelEditable(
    val hardware: Boolean,
    val displayName: Boolean,
    val hardwareCalibration: Boolean,
    val dosingCalibration: Boolean,
    val reservoir: Boolean
)

data class DeviceDosingPumpStatus(
    val doseMsPerMl: Long,
    val doseUnit: String,
    val lastCalibratedAt: Long,
    val calibrated: Boolean,
    val reservoirTrackingEnabled: Boolean,
    val reservoirCapacityMl: Double,
    val reservoirRemainingMl: Double,
    val reservoirRemainingPercent: Double
)

data class DeviceDosingChannelStatus(
    val index: Int,
    val key: String,
    val name: String,
    val displayName: String,
    val profileManaged: Boolean,
    val regime: DeviceDosingRegime,
    val channelKind: String,
    val gpio: Int,
    val ledcChannel: Int,
    val group: Int,
    val valueNow: Double,
    val valueAuto: Double,
    val valueManual: Double,
    val manualTimeoutMs: Long,
    val invert: Boolean,
    val pwmResolutionBits: Int,
    val pwmFrequencyHz: Int,
    val editable: DeviceDosingChannelEditable,
    val dosing: DeviceDosingPumpStatus
)

data class DeviceDosingScheduleStatus(
    val index: Int,
    val enabled: Boolean,
    val runtimeEnabled: Boolean,
    val name: String,
    val channelKey: String,
    val bound: Boolean,
    val group: Int,
    val weekdays: List<Boolean>,
    val startTimeMs: Long,
    val startTime: String,
    val intervalOnMs: Long,
    val intervalOn: String,
    val intervalOffMs: Long,
    val intervalOff: String,
    val repeatCount: Int,
    val amountMl: Double,
    val pulseCountRuntime: Int,
    val pulseOffPending: Boolean,
    val pulseRemainingMs: Long
)

data class DeviceDosingStatus(
    val supported: Boolean,
    val channelCount: Int,
    val scheduleCount: Int,
    val lockLoop: Boolean,
    val schema: String,
    val rootName: String,
    val unit: String,
    val uptimeMs: Long,
    val channels: List<DeviceDosingChannelStatus>,
    val schedules: List<DeviceDosingScheduleStatus>,
    val runtime: DeviceDosingRuntimeCapabilities
)

data class DeviceDosingChannelDosingConfig(
    val doseMsPerMl: Long? = null,
    val lastCalibratedAt: Long? = null,
    val reservoirTrackingEnabled: Boolean? = null,
    val reservoirCapacityMl: Double? = null
) {
    init {
        require(
            doseMsPerMl != null ||
                lastCalibratedAt != null ||
                reservoirTrackingEnabled != null ||
                reservoirCapacityMl != null
        ) { "Dosing channel config must contain at least one dosing field." }
        doseMsPerMl?.let {
            require(it == -1L || it in 1L..MAX_DOSING_DOSE_MS_PER_ML) {
                "doseMsPerMl must be -1 or inside the firmware safe range."
            }
        }
        lastCalibratedAt?.let {
            require(it >= 0L) { "lastCalibratedAt must be zero or greater." }
        }
        reservoirCapacityMl?.let {
            require(it.isFinite() && (it == -1.0 || it > 0.0)) {
                "reservoirCapacityMl must be -1 or greater than zero."
            }
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()

        if (doseMsPerMl != null) {
            json.put(DeviceDosingRuntimeContract.Field.DOSE_MS_PER_ML, doseMsPerMl)
        }

        if (lastCalibratedAt != null) {
            json.put(DeviceDosingRuntimeContract.Field.LAST_CALIBRATED_AT, lastCalibratedAt)
        }

        if (reservoirTrackingEnabled != null) {
            json.put(
                DeviceDosingRuntimeContract.Field.RESERVOIR_TRACKING_ENABLED,
                reservoirTrackingEnabled
            )
        }

        if (reservoirCapacityMl != null) {
            json.put(
                DeviceDosingRuntimeContract.Field.RESERVOIR_CAPACITY_ML,
                reservoirCapacityMl
            )
        }

        return json
    }
}

data class DeviceDosingChannelConfig(
    val channelKey: String,
    val displayName: String? = null,
    val regime: DeviceDosingRegime? = null,
    val dosing: DeviceDosingChannelDosingConfig? = null
) {
    init {
        requireDosingChannelKey(channelKey)
        displayName?.let {
            requireCanonicalDosingText(it, "displayName", allowEmpty = true)
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
            .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, channelKey)

        if (displayName != null) {
            json.put(DeviceDosingRuntimeContract.Field.DISPLAY_NAME, displayName)
        }

        if (regime != null) {
            json.put(DeviceDosingRuntimeContract.Field.REGIME, regime.wireValue)
        }

        if (dosing != null) {
            json.put(DeviceDosingRuntimeContract.Field.DOSING, dosing.toJson())
        }

        return json
    }
}

data class DeviceDosingScheduleConfig(
    val enabled: Boolean,
    val name: String,
    val channelKey: String,
    val weekdays: List<Boolean>,
    val startTimeMs: Long,
    val intervalOnMs: Long = 0L,
    val intervalOffMs: Long = 0L,
    val repeatCount: Int = 1,
    val amountMl: Double
) {
    init {
        requireCanonicalDosingText(name, "name", allowEmpty = true)
        requireCanonicalDosingText(channelKey, "channelKey", allowEmpty = true)
        if (channelKey.isNotEmpty()) {
            requireDosingChannelKey(channelKey)
        }
        require(weekdays.size == 7) { "Dosing weekdays must contain exactly 7 values." }
        require(startTimeMs in 0L..86_399_999L) { "startTimeMs must be inside one day." }
        require(intervalOnMs >= 0L) { "intervalOnMs must be zero or greater." }
        require(intervalOffMs >= 0L) { "intervalOffMs must be zero or greater." }
        require(repeatCount >= 0) { "repeatCount must be zero or greater." }
        require(amountMl.isFinite() && amountMl > 0.0) {
            "amountMl must be a finite value greater than zero."
        }
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put(DeviceDosingRuntimeContract.Field.ENABLED, enabled)
            .put(DeviceDosingRuntimeContract.Field.NAME, name)
            .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, channelKey)
            .put(DeviceDosingRuntimeContract.Field.WEEKDAYS, JSONArray(weekdays))
            .put(DeviceDosingRuntimeContract.Field.START_TIME_MS, startTimeMs)
            .put(DeviceDosingRuntimeContract.Field.INTERVAL_ON_MS, intervalOnMs)
            .put(DeviceDosingRuntimeContract.Field.INTERVAL_OFF_MS, intervalOffMs)
            .put(DeviceDosingRuntimeContract.Field.REPEAT_COUNT, repeatCount)
            .put(DeviceDosingRuntimeContract.Field.AMOUNT_ML, amountMl)
    }
}

data class DeviceDosingConfigApplyPayload(
    val channels: List<DeviceDosingChannelConfig> = emptyList(),
    val schedules: List<DeviceDosingScheduleConfig> = emptyList(),
    val save: Boolean = true
) {
    init {
        require(channels.isNotEmpty() || schedules.isNotEmpty()) {
            "dosing.config.apply requires channels and/or schedules."
        }
        require(schedules.size <= DeviceDosingRuntimeContract.Limit.MAX_SCHEDULES) {
            "Dosing supports at most ${DeviceDosingRuntimeContract.Limit.MAX_SCHEDULES} schedules."
        }
        require(channels.map(DeviceDosingChannelConfig::channelKey).distinct().size == channels.size) {
            "Dosing config channelKey values must be unique."
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
            .put(DeviceDosingRuntimeContract.Field.SAVE, save)

        if (channels.isNotEmpty()) {
            json.put(
                DeviceDosingRuntimeContract.Field.CHANNELS,
                JSONArray(channels.map { it.toJson() })
            )
        }

        if (schedules.isNotEmpty()) {
            json.put(
                DeviceDosingRuntimeContract.Field.SCHEDULES,
                JSONArray(schedules.map { it.toJson() })
            )
        }

        return json
    }
}

data class DeviceDosingChannelKeyPayload(
    val channelKey: String
) {
    init {
        requireDosingChannelKey(channelKey)
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, channelKey)
    }
}

data class DeviceDosingCalibrationStartPayload(
    val channelKey: String,
    val durationMs: Long = DeviceDosingRuntimeContract.Limit.DEFAULT_CALIBRATION_DURATION_MS
) {
    init {
        requireDosingChannelKey(channelKey)
        require(
            durationMs in
                DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_DURATION_MS..
                DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_DURATION_MS
        ) { "durationMs is outside firmware calibration range." }
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, channelKey)
            .put(DeviceDosingRuntimeContract.Field.DURATION_MS, durationMs)
    }
}

data class DeviceDosingCalibrationFinishPayload(
    val channelKey: String,
    val measuredMl: Double
) {
    init {
        requireDosingChannelKey(channelKey)
        require(
            measuredMl.isFinite() &&
                measuredMl in
                DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_MEASURED_ML..
                DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_MEASURED_ML
        ) { "measuredMl is outside firmware calibration range." }
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, channelKey)
            .put(DeviceDosingRuntimeContract.Field.MEASURED_ML, measuredMl)
    }
}

data class DeviceDosingDoseNowPayload(
    val channelKey: String,
    val amountMl: Double,
    val usePendingCalibration: Boolean = false
) {
    init {
        requireDosingChannelKey(channelKey)
        require(
            amountMl.isFinite() &&
                amountMl > 0.0 &&
                amountMl <= DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_ML
        ) {
            "amountMl must be between 0 and ${DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_ML}."
        }
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, channelKey)
            .put(DeviceDosingRuntimeContract.Field.AMOUNT_ML, amountMl)
            .put(DeviceDosingRuntimeContract.Field.USE_PENDING_CALIBRATION, usePendingCalibration)
    }
}

data class DeviceDosingChannelDosingConfigSnapshot(
    val doseMsPerMl: Long?,
    val lastCalibratedAt: Long?,
    val reservoirTrackingEnabled: Boolean?,
    val reservoirCapacityMl: Double?
)

data class DeviceDosingChannelConfigSnapshot(
    val channelKey: String,
    val displayName: String?,
    val regime: DeviceDosingRegime,
    val dosing: DeviceDosingChannelDosingConfigSnapshot?
)

data class DeviceDosingScheduleConfigSnapshot(
    val enabled: Boolean,
    val name: String,
    val channelKey: String,
    val weekdays: List<Boolean>,
    val startTimeMs: Long,
    val intervalOnMs: Long,
    val intervalOffMs: Long,
    val repeatCount: Int,
    val amountMl: Double
)

data class DeviceDosingConfigSnapshot(
    val channels: List<DeviceDosingChannelConfigSnapshot>,
    val schedules: List<DeviceDosingScheduleConfigSnapshot>
)

data class DeviceDosingConfigApplyResult(
    val operation: String,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val runtimeTransport: String,
    val command: String,
    val event: String,
    val appliedChannels: Boolean,
    val appliedSchedules: Boolean,
    val config: DeviceDosingConfigSnapshot
)

data class DeviceDosingChannelSnapshot(
    val listIndex: Int,
    val channel: DeviceDosingChannelStatus
)

data class DeviceDosingManualPumpResult(
    val operation: String,
    val channelKey: String,
    val manualActive: Boolean,
    val saved: Boolean,
    val runtimeTransport: String,
    val command: String,
    val event: String,
    val channel: DeviceDosingChannelSnapshot
)

data class DeviceDosingDoseNowResult(
    val operation: String,
    val channelKey: String,
    val amountMl: Double,
    val durationMs: Long,
    val doseMsPerMl: Long,
    val usePendingCalibration: Boolean,
    val manualActive: Boolean,
    val saved: Boolean,
    val runtimeTransport: String,
    val command: String,
    val event: String,
    val channel: DeviceDosingChannelSnapshot
)

data class DeviceDosingCalibrationStartResult(
    val operation: String,
    val channelKey: String,
    val durationMs: Long,
    val manualActive: Boolean,
    val saved: Boolean,
    val runtimeTransport: String,
    val command: String,
    val event: String
)

data class DeviceDosingCalibrationFinishResult(
    val operation: String,
    val channelKey: String,
    val measuredMl: Double,
    val durationMs: Long,
    val pendingDoseMsPerMl: Long,
    val pending: Boolean,
    val saved: Boolean,
    val runtimeTransport: String,
    val command: String,
    val event: String,
    val channel: DeviceDosingChannelSnapshot
)

data class DeviceDosingCalibrationConfirmResult(
    val operation: String,
    val channelKey: String,
    val doseMsPerMl: Long,
    val lastCalibratedAt: Long,
    val saved: Boolean,
    val runtimeTransport: String,
    val command: String,
    val event: String,
    val channel: DeviceDosingChannelSnapshot
)

data class DeviceDosingCalibrationCancelResult(
    val operation: String,
    val channelKey: String,
    val restoredPreviousCalibration: Boolean,
    val saved: Boolean,
    val runtimeTransport: String,
    val command: String,
    val event: String,
    val channel: DeviceDosingChannelSnapshot
)

data class DeviceDosingReservoirRefillResult(
    val operation: String,
    val channelKey: String,
    val changed: Boolean,
    val reservoirRemainingMlBefore: Double,
    val reservoirRemainingMl: Double,
    val reservoirCapacityMl: Double,
    val persisted: Boolean,
    val saved: Boolean,
    val runtimeTransport: String,
    val command: String,
    val event: String,
    val channel: DeviceDosingChannelSnapshot
)

private fun requireDosingChannelKey(value: String) {
    requireCanonicalDosingText(value, "channelKey", allowEmpty = false)
    require(value == value.lowercase()) { "channelKey must use canonical lowercase form." }
    require(value != "-" && value != "none") {
        "channelKey must target a configured dosing pump."
    }
}

private fun requireCanonicalDosingText(
    value: String,
    field: String,
    allowEmpty: Boolean
) {
    require(allowEmpty || value.isNotEmpty()) { "$field must not be empty." }
    require(value == value.trim()) { "$field must not contain surrounding whitespace." }
    require(value.none(Char::isISOControl)) { "$field must not contain control characters." }
}

private const val MAX_DOSING_DOSE_MS_PER_ML = 3_600_000L
