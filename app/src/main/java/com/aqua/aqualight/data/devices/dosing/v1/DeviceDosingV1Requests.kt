package com.aqua.aqualight.data.devices.dosing.v1

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import org.json.JSONArray
import org.json.JSONObject

@JvmInline
value class DeviceDosingV1ChannelKey private constructor(val value: String) {
    init {
        require(value.isNotEmpty()) { "channelKey must not be empty." }
        require(value.toByteArray(Charsets.UTF_8).size <= DeviceDosingV1Contract.Limit.MAX_CHANNEL_KEY_BYTES) {
            "channelKey exceeds the firmware byte limit."
        }
        require(value.all { character ->
            character in 'a'..'z' ||
                character in '0'..'9' ||
                character == '_' ||
                character == '-'
        }) { "channelKey must contain only ASCII letters, digits, underscore, or hyphen." }
    }

    companion object {
        fun from(raw: String): DeviceDosingV1ChannelKey =
            DeviceDosingV1ChannelKey(raw.trim().lowercase(Locale.ROOT))

        internal fun parseCanonical(raw: String): DeviceDosingV1ChannelKey =
            from(raw).also { parsed ->
                require(parsed.value == raw) {
                    "Firmware channelKey must already be canonical lowercase text."
                }
            }
    }
}

/** Positive amount represented exactly in firmware 0.001 ml quanta. */
@JvmInline
value class DeviceDosingV1Amount private constructor(val quanta: Long) {
    init {
        require(quanta in 1L..DeviceDosingV1Contract.Limit.MAX_UNSIGNED_INT) {
            "Dosing amount is outside the firmware unsigned range."
        }
    }

    val milliliters: Double
        get() = quanta.toDouble() / DeviceDosingV1Contract.Limit.AMOUNT_QUANTA_PER_ML

    companion object {
        fun fromMilliliters(value: Double): DeviceDosingV1Amount {
            require(value.isFinite() && value > 0.0) {
                "Dosing amount must be finite and positive."
            }
            val scaled = value * DeviceDosingV1Contract.Limit.AMOUNT_QUANTA_PER_ML
            val normalized = round(scaled)
            require(abs(scaled - normalized) <= AMOUNT_NORMALIZATION_TOLERANCE) {
                "Dosing amount must be exactly representable at 0.001 ml resolution."
            }
            require(normalized <= DeviceDosingV1Contract.Limit.MAX_UNSIGNED_INT.toDouble()) {
                "Dosing amount exceeds the firmware unsigned range."
            }
            return DeviceDosingV1Amount(normalized.toLong())
        }

        private const val AMOUNT_NORMALIZATION_TOLERANCE = 0.000_001
    }
}

data class DeviceDosingV1Weekdays private constructor(
    val values: List<Boolean>
) {
    constructor(values: Collection<Boolean>) : this(values.toList())

    init {
        require(values.size == DeviceDosingV1Contract.Limit.WEEKDAY_COUNT) {
            "weekdays must contain Monday through Sunday in exactly seven positions."
        }
    }

    internal fun toJson(): JSONArray = JSONArray().also { output ->
        values.forEach { selected -> output.put(selected) }
    }
}

sealed interface DeviceDosingV1ProgramConfig {
    val mode: String

    data class Single(
        val dailyDose: DeviceDosingV1Amount,
        val startTimeMillis: Long
    ) : DeviceDosingV1ProgramConfig {
        override val mode = "single"

        init {
            requireDosingTime(startTimeMillis, "startTimeMillis")
        }
    }

    data class Hourly24(
        val dailyDose: DeviceDosingV1Amount,
        val startTimeMillis: Long
    ) : DeviceDosingV1ProgramConfig {
        override val mode = "hourly24"

        init {
            requireDosingTime(startTimeMillis, "startTimeMillis")
        }
    }

    data class CustomPeriod(
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val doseCount: Int
    ) {
        init {
            requireDosingTime(startTimeMillis, "startTimeMillis")
            requireDosingTime(endTimeMillis, "endTimeMillis")
            require(startTimeMillis < endTimeMillis) {
                "A custom period must end after it starts."
            }
            require(doseCount in 1..DeviceDosingV1Contract.Limit.MAX_EVENTS_PER_CHANNEL) {
                "doseCount is outside the firmware range."
            }
        }
    }

    data class CustomPeriods(
        val dailyDose: DeviceDosingV1Amount,
        val periods: List<CustomPeriod>
    ) : DeviceDosingV1ProgramConfig {
        override val mode = "customPeriods"

        init {
            require(periods.isNotEmpty()) { "At least one custom period is required." }
            require(periods.size <= DeviceDosingV1Contract.Limit.MAX_CUSTOM_PERIODS_PER_CHANNEL) {
                "Too many custom periods."
            }
            require(
                periods.sumOf { period -> period.doseCount } <=
                    DeviceDosingV1Contract.Limit.MAX_EVENTS_PER_CHANNEL
            ) {
                "Custom periods compile to more than the firmware occurrence limit."
            }
            require(periods.zipWithNext().all { (left, right) ->
                left.endTimeMillis < right.startTimeMillis
            }) { "Custom periods must be ordered, non-overlapping, and non-touching." }
        }
    }

    data class TimerEvent(
        val timeMillis: Long,
        val amount: DeviceDosingV1Amount
    ) {
        init {
            requireDosingTime(timeMillis, "timeMillis")
        }
    }

    data class Timer(
        val events: List<TimerEvent>
    ) : DeviceDosingV1ProgramConfig {
        override val mode = "timer"

        init {
            require(events.isNotEmpty()) { "At least one timer event is required." }
            require(events.size <= DeviceDosingV1Contract.Limit.MAX_EVENTS_PER_CHANNEL) {
                "Too many timer events."
            }
            require(events.map(TimerEvent::timeMillis).distinct().size == events.size) {
                "Timer event times must be unique."
            }
        }
    }
}

data class DeviceDosingV1Program(
    val enabled: Boolean,
    val weekdays: DeviceDosingV1Weekdays,
    val config: DeviceDosingV1ProgramConfig,
    val missedDoseRecoveryEnabled: Boolean? = null
) {
    internal fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("weekdays", weekdays.toJson())
        .put("mode", config.mode)
        .also { output ->
            missedDoseRecoveryEnabled?.let { enabled ->
                output.put("missedDoseRecoveryEnabled", enabled)
            }
        }
        .put("config", config.toJson())
}

sealed interface DeviceDosingV1DisplayNameUpdate {
    data object Omitted : DeviceDosingV1DisplayNameUpdate
    data object Clear : DeviceDosingV1DisplayNameUpdate

    data class Set(val value: String) : DeviceDosingV1DisplayNameUpdate {
        val normalizedValue = value.trim()

        init {
            require(normalizedValue.isNotEmpty()) {
                "Use Clear rather than Set for a blank display name."
            }
            require(normalizedValue.none(Char::isISOControl)) {
                "displayName must not contain control characters."
            }
            require(
                normalizedValue.toByteArray(Charsets.UTF_8).size <=
                    DeviceDosingV1Contract.Limit.MAX_DISPLAY_NAME_BYTES
            ) { "displayName exceeds the firmware UTF-8 byte limit." }
        }
    }
}

data class DeviceDosingV1ReservoirUpdate(
    val trackingEnabled: Boolean,
    val capacity: DeviceDosingV1Amount? = null
) {
    init {
        require(trackingEnabled == (capacity != null)) {
            "capacity must be present only when reservoir tracking is enabled."
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put("trackingEnabled", trackingEnabled)
        .also { output ->
            if (trackingEnabled) output.put("capacityMl", checkNotNull(capacity).milliliters)
        }
}

data class DeviceDosingV1ConfigApplyRequest(
    val channelKey: DeviceDosingV1ChannelKey,
    val expectedRevision: Long,
    val displayName: DeviceDosingV1DisplayNameUpdate = DeviceDosingV1DisplayNameUpdate.Omitted,
    val reservoir: DeviceDosingV1ReservoirUpdate? = null
) {
    init {
        requireDosingRevision(expectedRevision)
        require(
            displayName !is DeviceDosingV1DisplayNameUpdate.Omitted || reservoir != null
        ) { "config.apply requires displayName and/or reservoir." }
    }

    internal fun toJson(): JSONObject = channelRevisionJson(channelKey, expectedRevision)
        .also { output ->
            when (val update = displayName) {
                DeviceDosingV1DisplayNameUpdate.Omitted -> Unit
                DeviceDosingV1DisplayNameUpdate.Clear ->
                    output.put("displayName", JSONObject.NULL)
                is DeviceDosingV1DisplayNameUpdate.Set ->
                    output.put("displayName", update.normalizedValue)
            }
            reservoir?.let { update -> output.put("reservoir", update.toJson()) }
        }
}

data class DeviceDosingV1ProgramApplyRequest(
    val channelKey: DeviceDosingV1ChannelKey,
    val expectedRevision: Long,
    val program: DeviceDosingV1Program
) {
    init {
        requireDosingRevision(expectedRevision)
    }

    internal fun toJson(): JSONObject = channelRevisionJson(channelKey, expectedRevision)
        .put("program", program.toJson())
}

data class DeviceDosingV1ChannelResetRequest(
    val channelKey: DeviceDosingV1ChannelKey,
    val expectedRevision: Long
) {
    init {
        requireDosingRevision(expectedRevision)
    }

    internal fun toJson(): JSONObject = channelRevisionJson(channelKey, expectedRevision)
}

data class DeviceDosingV1CalibrationStartRequest(
    val channelKey: DeviceDosingV1ChannelKey,
    val durationMillis: Long? = null
) {
    init {
        durationMillis?.let { duration ->
            require(
                duration in DeviceDosingV1Contract.Limit.MIN_CALIBRATION_DURATION_MS..
                    DeviceDosingV1Contract.Limit.MAX_CALIBRATION_DURATION_MS
            ) { "Calibration duration is outside the firmware range." }
        }
    }

    internal fun toJson(): JSONObject = channelJson(channelKey).also { output ->
        durationMillis?.let { duration -> output.put("durationMs", duration) }
    }
}

data class DeviceDosingV1CalibrationFinishRequest(
    val channelKey: DeviceDosingV1ChannelKey,
    val measuredMilliliters: Double
) {
    init {
        require(
            measuredMilliliters.isFinite() &&
                measuredMilliliters in DeviceDosingV1Contract.Limit.MIN_MEASURED_ML..
                DeviceDosingV1Contract.Limit.MAX_MEASURED_ML
        ) { "measuredMl is outside the calibration workflow range." }
    }

    internal fun toJson(): JSONObject = channelJson(channelKey)
        .put("measuredMl", measuredMilliliters)
}

data class DeviceDosingV1DoseNowRequest(
    val channelKey: DeviceDosingV1ChannelKey,
    val amount: DeviceDosingV1Amount,
    val usePendingCalibration: Boolean? = null
) {
    init {
        require(amount.milliliters <= DeviceDosingV1Contract.Limit.MAX_MANUAL_DOSE_ML) {
            "Manual dose exceeds the firmware maximum."
        }
    }

    internal fun toJson(): JSONObject = channelJson(channelKey)
        .put("amountMl", amount.milliliters)
        .also { output ->
            usePendingCalibration?.let { pending ->
                output.put("usePendingCalibration", pending)
            }
        }
}

internal fun channelJson(channelKey: DeviceDosingV1ChannelKey): JSONObject =
    JSONObject().put("channelKey", channelKey.value)

private fun channelRevisionJson(
    channelKey: DeviceDosingV1ChannelKey,
    expectedRevision: Long
): JSONObject = channelJson(channelKey).put("expectedRevision", expectedRevision)

private fun DeviceDosingV1ProgramConfig.toJson(): JSONObject = when (this) {
    is DeviceDosingV1ProgramConfig.Single -> JSONObject()
        .put("dailyDoseMl", dailyDose.milliliters)
        .put("startTimeMs", startTimeMillis)
    is DeviceDosingV1ProgramConfig.Hourly24 -> JSONObject()
        .put("dailyDoseMl", dailyDose.milliliters)
        .put("startTimeMs", startTimeMillis)
    is DeviceDosingV1ProgramConfig.CustomPeriods -> JSONObject()
        .put("dailyDoseMl", dailyDose.milliliters)
        .put(
            "periods",
            JSONArray().also { output ->
                periods.forEach { period ->
                    output.put(
                        JSONObject()
                            .put("startTimeMs", period.startTimeMillis)
                            .put("endTimeMs", period.endTimeMillis)
                            .put("doseCount", period.doseCount)
                    )
                }
            }
        )
    is DeviceDosingV1ProgramConfig.Timer -> JSONObject().put(
        "events",
        JSONArray().also { output ->
            events.forEach { event ->
                output.put(
                    JSONObject()
                        .put("timeMs", event.timeMillis)
                        .put("amountMl", event.amount.milliliters)
                )
            }
        }
    )
}

private fun requireDosingRevision(revision: Long) {
    require(revision in 0L..DeviceDosingV1Contract.Limit.MAX_UNSIGNED_INT) {
        "expectedRevision must be an unsigned 32-bit integer."
    }
}

private fun requireDosingTime(value: Long, field: String) {
    require(value in 0L until DeviceDosingV1Contract.Limit.MILLIS_PER_DAY) {
        "$field must be within the local firmware day."
    }
}
