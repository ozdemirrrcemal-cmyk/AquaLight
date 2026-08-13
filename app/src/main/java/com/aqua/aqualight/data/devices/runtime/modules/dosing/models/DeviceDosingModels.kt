package com.aqua.aqualight.data.devices.runtime.modules.dosing.models

import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DOSING_DEVICE_UPTIME_MAX_MS
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DOSING_NON_NEGATIVE_LONG
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DOSING_UNSET_CALIBRATION
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DOSING_UNSET_RESERVOIR
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DOSING_WEEKDAY_COUNT
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import org.json.JSONArray
import org.json.JSONObject

enum class DeviceDosingRegime(val wireValue: String) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off")
}

enum class DeviceDosingCalibrationState(val wireValue: String) {
    IDLE("idle"),
    RUNNING("running"),
    PENDING_VERIFICATION("pendingVerification")
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
    val supportsCalibrationSessionState: Boolean,
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

data class DeviceDosingCalibrationSessionStatus(
    val state: DeviceDosingCalibrationState,
    val startedAtUptimeMs: Long,
    val durationMs: Long,
    val measuredMl: Double,
    val pendingDoseMsPerMl: Long,
    val verificationDoseStarted: Boolean,
    val verificationDoseComplete: Boolean,
    val verificationDoseRemainingMs: Long
)

data class DeviceDosingPumpStatus(
    val unit: String,
    val doseMsPerMl: Long,
    val lastCalibratedAt: Long,
    val calibrated: Boolean,
    val calibration: DeviceDosingCalibrationSessionStatus,
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

data class DeviceDosingChannelStatusSnapshot(
    val listIndex: Int,
    val channel: DeviceDosingChannelStatus
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

data class DeviceDosingChannelConfigSnapshot(
    val listIndex: Int,
    val channelKey: String,
    val displayNameOverride: String?,
    val regime: DeviceDosingRegime,
    val dosing: DeviceDosingChannelDosingConfigSnapshot
)

data class DeviceDosingChannelDosingConfigSnapshot(
    val doseMsPerMl: Long,
    val lastCalibratedAt: Long,
    val reservoirTrackingEnabled: Boolean,
    val reservoirCapacityMl: Double
)

data class DeviceDosingScheduleConfigSnapshot(
    val listIndex: Int,
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

sealed interface DeviceDosingMutationResult {
    val command: String
}

data class DeviceDosingConfigApplyResult(
    val operation: String,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val runtimeTransport: String,
    override val command: String,
    val event: String,
    val appliedChannels: Boolean,
    val appliedSchedules: Boolean,
    val config: DeviceDosingConfigSnapshot
) : DeviceDosingMutationResult

data class DeviceDosingPumpCommandResult(
    val operation: String,
    val channelKey: String,
    val manualActive: Boolean,
    val verificationReset: Boolean,
    val saved: Boolean,
    val runtimeTransport: String,
    override val command: String,
    val event: String,
    val channel: DeviceDosingChannelStatusSnapshot
) : DeviceDosingMutationResult

data class DeviceDosingDoseNowResult(
    val operation: String,
    val channelKey: String,
    val amountMl: Double,
    val durationMs: Long,
    val doseMsPerMl: Long,
    val usePendingCalibration: Boolean,
    val calibrationState: DeviceDosingCalibrationState,
    val manualActive: Boolean,
    val saved: Boolean,
    val runtimeTransport: String,
    override val command: String,
    val event: String,
    val channel: DeviceDosingChannelStatusSnapshot
) : DeviceDosingMutationResult

data class DeviceDosingCalibrationStartResult(
    val operation: String,
    val channelKey: String,
    val durationMs: Long,
    val calibrationState: DeviceDosingCalibrationState,
    val manualActive: Boolean,
    val saved: Boolean,
    val runtimeTransport: String,
    override val command: String,
    val event: String
) : DeviceDosingMutationResult

data class DeviceDosingCalibrationFinishResult(
    val operation: String,
    val channelKey: String,
    val measuredMl: Double,
    val durationMs: Long,
    val pendingDoseMsPerMl: Long,
    val pending: Boolean,
    val calibrationState: DeviceDosingCalibrationState,
    val saved: Boolean,
    val runtimeTransport: String,
    override val command: String,
    val event: String,
    val channel: DeviceDosingChannelStatusSnapshot
) : DeviceDosingMutationResult

data class DeviceDosingCalibrationConfirmResult(
    val operation: String,
    val channelKey: String,
    val doseMsPerMl: Long,
    val lastCalibratedAt: Long,
    val calibrationState: DeviceDosingCalibrationState,
    val saved: Boolean,
    val runtimeTransport: String,
    override val command: String,
    val event: String,
    val channel: DeviceDosingChannelStatusSnapshot
) : DeviceDosingMutationResult

data class DeviceDosingCalibrationCancelResult(
    val operation: String,
    val channelKey: String,
    val restoredPreviousCalibration: Boolean,
    val discardedPendingCalibration: Boolean,
    val calibrationState: DeviceDosingCalibrationState,
    val saved: Boolean,
    val runtimeTransport: String,
    override val command: String,
    val event: String,
    val channel: DeviceDosingChannelStatusSnapshot
) : DeviceDosingMutationResult

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
    override val command: String,
    val event: String,
    val channel: DeviceDosingChannelStatusSnapshot
) : DeviceDosingMutationResult

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
        ) { "A Dosing channel config must change at least one dosing field." }
        require(
            doseMsPerMl == null ||
                doseMsPerMl == DOSING_UNSET_CALIBRATION ||
                doseMsPerMl in 1L..DeviceDosingRuntimeContract.Limit.MAX_DOSE_MS_PER_ML
        ) { "doseMsPerMl is outside the firmware-safe range." }
        require(lastCalibratedAt == null || lastCalibratedAt in 0L..DOSING_DEVICE_UPTIME_MAX_MS) {
            "lastCalibratedAt is outside the firmware unsigned-long range."
        }
        require(
            reservoirCapacityMl == null ||
                reservoirCapacityMl == DOSING_UNSET_RESERVOIR ||
                reservoirCapacityMl.isFinite() && reservoirCapacityMl > 0.0
        ) { "reservoirCapacityMl must be -1 or a positive finite value." }
    }

    internal fun toJson(): JSONObject = JSONObject().also { data ->
        doseMsPerMl?.let { data.put(DeviceDosingRuntimeContract.Field.DOSE_MS_PER_ML, it) }
        lastCalibratedAt?.let {
            data.put(DeviceDosingRuntimeContract.Field.LAST_CALIBRATED_AT, it)
        }
        reservoirTrackingEnabled?.let {
            data.put(DeviceDosingRuntimeContract.Field.RESERVOIR_TRACKING_ENABLED, it)
        }
        reservoirCapacityMl?.let {
            data.put(DeviceDosingRuntimeContract.Field.RESERVOIR_CAPACITY_ML, it)
        }
    }
}

data class DeviceDosingChannelConfig(
    val channelKey: String,
    val displayName: String? = null,
    val regime: DeviceDosingRegime? = null,
    val dosing: DeviceDosingChannelDosingConfig? = null
) {
    val normalizedChannelKey: String = channelKey.trim().lowercase()
    val normalizedDisplayName: String? = displayName?.trim()

    init {
        require(normalizedChannelKey.isNotEmpty()) { "channelKey must not be blank." }
        require(normalizedChannelKey.none(Char::isISOControl)) {
            "channelKey must not contain control characters."
        }
        require(displayName != null || regime != null || dosing != null) {
            "A Dosing channel config must change displayName, regime and/or dosing settings."
        }
        require(normalizedDisplayName?.none(Char::isISOControl) != false) {
            "displayName must not contain control characters."
        }
        require(
            normalizedDisplayName
                ?.toByteArray(Charsets.UTF_8)
                ?.size
                ?.let { size ->
                    size <= DeviceDosingRuntimeContract.Limit.MAX_CHANNEL_DISPLAY_NAME_BYTES
                } ?: true
        ) {
            "displayName must be at most " +
                "${DeviceDosingRuntimeContract.Limit.MAX_CHANNEL_DISPLAY_NAME_BYTES} UTF-8 bytes."
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .also { data ->
            normalizedDisplayName?.let {
                data.put(DeviceDosingRuntimeContract.Field.DISPLAY_NAME, it)
            }
            regime?.let { data.put(DeviceDosingRuntimeContract.Field.REGIME, it.wireValue) }
            dosing?.let { data.put(DeviceDosingRuntimeContract.Field.DOSING, it.toJson()) }
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
    val normalizedName: String = name.trim()
    val normalizedChannelKey: String = channelKey.trim().lowercase()

    init {
        require(normalizedName.isNotEmpty()) { "Dosing schedule name must not be blank." }
        require(normalizedName.none(Char::isISOControl)) {
            "Dosing schedule name must not contain control characters."
        }
        require(
            normalizedName.toByteArray(Charsets.UTF_8).size <=
                DeviceDosingRuntimeContract.Limit.MAX_SCHEDULE_NAME_BYTES
        ) {
            "Dosing schedule name must be at most " +
                "${DeviceDosingRuntimeContract.Limit.MAX_SCHEDULE_NAME_BYTES} UTF-8 bytes."
        }
        require(normalizedChannelKey.isNotEmpty()) { "channelKey must not be blank." }
        require(normalizedChannelKey.none(Char::isISOControl)) {
            "channelKey must not contain control characters."
        }
        require(weekdays.size == DOSING_WEEKDAY_COUNT) {
            "Dosing weekdays must contain exactly $DOSING_WEEKDAY_COUNT values."
        }
        require(startTimeMs in 0L..DeviceDosingRuntimeContract.Limit.LAST_MILLISECOND_OF_DAY) {
            "startTimeMs must be inside one day."
        }
        require(intervalOnMs in DOSING_NON_NEGATIVE_LONG..DOSING_DEVICE_UPTIME_MAX_MS) {
            "intervalOnMs is outside the firmware unsigned-long range."
        }
        require(intervalOffMs in DOSING_NON_NEGATIVE_LONG..DOSING_DEVICE_UPTIME_MAX_MS) {
            "intervalOffMs is outside the firmware unsigned-long range."
        }
        require(intervalOnMs <= DOSING_DEVICE_UPTIME_MAX_MS - intervalOffMs) {
            "Dosing interval sum exceeds the firmware unsigned-long range."
        }
        require(repeatCount >= 0) { "repeatCount must be zero or greater." }
        require(amountMl.isFinite() && amountMl > 0.0) {
            "amountMl must be a positive finite value."
        }
        if (enabled) {
            require(weekdays.any { selected -> selected }) {
                "An enabled Dosing schedule requires at least one weekday."
            }
            require(repeatCount > 0) {
                "An enabled Dosing schedule requires a positive repeatCount."
            }
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceDosingRuntimeContract.Field.ENABLED, enabled)
        .put(DeviceDosingRuntimeContract.Field.NAME, normalizedName)
        .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .put(DeviceDosingRuntimeContract.Field.WEEKDAYS, JSONArray(weekdays))
        .put(DeviceDosingRuntimeContract.Field.START_TIME_MS, startTimeMs)
        .put(DeviceDosingRuntimeContract.Field.INTERVAL_ON_MS, intervalOnMs)
        .put(DeviceDosingRuntimeContract.Field.INTERVAL_OFF_MS, intervalOffMs)
        .put(DeviceDosingRuntimeContract.Field.REPEAT_COUNT, repeatCount)
        .put(DeviceDosingRuntimeContract.Field.AMOUNT_ML, amountMl)
}

data class DeviceDosingConfigApplyPayload(
    val channels: List<DeviceDosingChannelConfig>? = null,
    val schedules: List<DeviceDosingScheduleConfig>? = null,
    val save: Boolean = true
) {
    init {
        require(channels != null || schedules != null) {
            "dosing.config.apply requires channels and/or schedules."
        }
        require((schedules?.size ?: 0) <= DeviceDosingRuntimeContract.Limit.MAX_SCHEDULES) {
            "Dosing supports at most ${DeviceDosingRuntimeContract.Limit.MAX_SCHEDULES} schedules."
        }
        require((channels?.size ?: 0) <= DeviceDosingRuntimeContract.Limit.MAX_CHANNELS) {
            "Dosing channel request exceeds the WebSocket safety limit."
        }
        require(
            channels
                ?.map(DeviceDosingChannelConfig::normalizedChannelKey)
                ?.let { keys -> keys.distinct().size == keys.size } ?: true
        ) { "channelKey must be unique in the request." }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceDosingRuntimeContract.Field.SAVE, save)
        .also { data ->
            channels?.let { items ->
                data.put(
                    DeviceDosingRuntimeContract.Field.CHANNELS,
                    JSONArray().also { array -> items.forEach { item -> array.put(item.toJson()) } }
                )
            }
            schedules?.let { items ->
                data.put(
                    DeviceDosingRuntimeContract.Field.SCHEDULES,
                    JSONArray().also { array -> items.forEach { item -> array.put(item.toJson()) } }
                )
            }
        }
}

internal data class DeviceDosingChannelKeyPayload(val channelKey: String) {
    val normalizedChannelKey: String = channelKey.trim().lowercase()

    init {
        require(normalizedChannelKey.isNotEmpty()) { "channelKey must not be blank." }
        require(normalizedChannelKey.none(Char::isISOControl)) {
            "channelKey must not contain control characters."
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
}

data class DeviceDosingCalibrationStartPayload(
    val channelKey: String,
    val durationMs: Long = DeviceDosingRuntimeContract.Limit.DEFAULT_CALIBRATION_DURATION_MS
) {
    val normalizedChannelKey: String = DeviceDosingChannelKeyPayload(channelKey).normalizedChannelKey

    init {
        require(
            durationMs in
                DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_DURATION_MS..
                DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_DURATION_MS
        ) { "durationMs is outside the firmware calibration range." }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .put(DeviceDosingRuntimeContract.Field.DURATION_MS, durationMs)
}

data class DeviceDosingCalibrationFinishPayload(
    val channelKey: String,
    val measuredMl: Double
) {
    val normalizedChannelKey: String = DeviceDosingChannelKeyPayload(channelKey).normalizedChannelKey

    init {
        require(
            measuredMl.isFinite() &&
                measuredMl in
                DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_MEASURED_ML..
                DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_MEASURED_ML
        ) { "measuredMl is outside the firmware calibration range." }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .put(DeviceDosingRuntimeContract.Field.MEASURED_ML, measuredMl)
}

data class DeviceDosingDoseNowPayload(
    val channelKey: String,
    val amountMl: Double,
    val usePendingCalibration: Boolean = false
) {
    val normalizedChannelKey: String = DeviceDosingChannelKeyPayload(channelKey).normalizedChannelKey

    init {
        require(
            amountMl.isFinite() &&
                amountMl > 0.0 &&
                amountMl <= DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_ML
        ) {
            "amountMl must be between 0 and " +
                "${DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_ML}."
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .put(DeviceDosingRuntimeContract.Field.AMOUNT_ML, amountMl)
        .put(DeviceDosingRuntimeContract.Field.USE_PENDING_CALIBRATION, usePendingCalibration)
}

internal fun DeviceDosingScheduleConfigSnapshot.toPayload(): DeviceDosingScheduleConfig =
    DeviceDosingScheduleConfig(
        enabled = enabled,
        name = name,
        channelKey = channelKey,
        weekdays = weekdays,
        startTimeMs = startTimeMs,
        intervalOnMs = intervalOnMs,
        intervalOffMs = intervalOffMs,
        repeatCount = repeatCount,
        amountMl = amountMl
    )
