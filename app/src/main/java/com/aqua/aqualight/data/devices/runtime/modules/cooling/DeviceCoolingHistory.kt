package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceCoolingHistoryRange(val wireValue: String) {
    HOURS_24("24h"),
    DAYS_7("7d"),
    DAYS_30("30d");

    companion object {
        fun parse(value: String): DeviceCoolingHistoryRange =
            entries.firstOrNull { range -> range.wireValue == value }
                ?: error("Unsupported cooling history range: $value")
    }
}

data class DeviceCoolingHistorySample(
    val sampledAtMs: Long,
    val temperatureC: Double
)

data class DeviceCoolingHistorySummary(
    val minimumTemperatureC: Double?,
    val averageTemperatureC: Double?,
    val maximumTemperatureC: Double?
)

data class DeviceCoolingHistoryDay(
    val dayStartAtMs: Long,
    val minimumTemperatureC: Double,
    val averageTemperatureC: Double,
    val maximumTemperatureC: Double
)

data class DeviceCoolingHistorySnapshot(
    val range: DeviceCoolingHistoryRange,
    val generatedAtMs: Long,
    val summary: DeviceCoolingHistorySummary,
    val samples: List<DeviceCoolingHistorySample>,
    val days: List<DeviceCoolingHistoryDay>
)

data class DeviceCoolingHistoryGetPayload(
    val range: DeviceCoolingHistoryRange
) {
    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceCoolingRuntimeContract.Field.RANGE, range.wireValue)
}

object DeviceCoolingHistoryParser {
    private val HISTORY_KEYS = setOf(
        DeviceCoolingRuntimeContract.Field.RANGE,
        DeviceCoolingRuntimeContract.Field.GENERATED_AT_MS,
        DeviceCoolingRuntimeContract.Field.SUMMARY,
        DeviceCoolingRuntimeContract.Field.SAMPLES,
        DeviceCoolingRuntimeContract.Field.DAYS
    )
    private val SUMMARY_KEYS = setOf(
        DeviceCoolingRuntimeContract.Field.MIN_TEMPERATURE_C,
        DeviceCoolingRuntimeContract.Field.AVG_TEMPERATURE_C,
        DeviceCoolingRuntimeContract.Field.MAX_TEMPERATURE_C
    )
    private val SAMPLE_KEYS = setOf(
        DeviceCoolingRuntimeContract.Field.SAMPLED_AT_MS,
        DeviceCoolingRuntimeContract.Field.TEMPERATURE_C
    )
    private val DAY_KEYS = setOf(
        DeviceCoolingRuntimeContract.Field.DAY_START_AT_MS,
        DeviceCoolingRuntimeContract.Field.MIN_TEMPERATURE_C,
        DeviceCoolingRuntimeContract.Field.AVG_TEMPERATURE_C,
        DeviceCoolingRuntimeContract.Field.MAX_TEMPERATURE_C
    )

    fun parse(data: JSONObject): DeviceCoolingHistorySnapshot {
        data.requireCoolingKeys(HISTORY_KEYS, "cooling history")
        val snapshot = DeviceCoolingHistorySnapshot(
            range = DeviceCoolingHistoryRange.parse(
                data.requireCoolingText(DeviceCoolingRuntimeContract.Field.RANGE)
            ),
            generatedAtMs = data.requireCoolingLong(
                DeviceCoolingRuntimeContract.Field.GENERATED_AT_MS,
                minimum = 0L
            ),
            summary = parseSummary(
                data.requireCoolingObject(DeviceCoolingRuntimeContract.Field.SUMMARY)
            ),
            samples = parseSamples(
                data.requireCoolingArray(DeviceCoolingRuntimeContract.Field.SAMPLES)
            ),
            days = parseDays(
                data.requireCoolingArray(DeviceCoolingRuntimeContract.Field.DAYS)
            )
        )
        validate(snapshot)
        return snapshot
    }

    private fun parseSummary(data: JSONObject): DeviceCoolingHistorySummary {
        data.requireCoolingKeys(SUMMARY_KEYS, "cooling history summary")
        return DeviceCoolingHistorySummary(
            minimumTemperatureC = data.requireCoolingNullableDouble(
                DeviceCoolingRuntimeContract.Field.MIN_TEMPERATURE_C,
                HISTORY_MIN_TEMPERATURE_C,
                HISTORY_MAX_TEMPERATURE_C
            ),
            averageTemperatureC = data.requireCoolingNullableDouble(
                DeviceCoolingRuntimeContract.Field.AVG_TEMPERATURE_C,
                HISTORY_MIN_TEMPERATURE_C,
                HISTORY_MAX_TEMPERATURE_C
            ),
            maximumTemperatureC = data.requireCoolingNullableDouble(
                DeviceCoolingRuntimeContract.Field.MAX_TEMPERATURE_C,
                HISTORY_MIN_TEMPERATURE_C,
                HISTORY_MAX_TEMPERATURE_C
            )
        )
    }

    private fun parseSamples(data: JSONArray): List<DeviceCoolingHistorySample> {
        require(data.length() <= DeviceCoolingRuntimeContract.Limit.MAX_HISTORY_SAMPLE_COUNT) {
            "cooling history samples exceed the safety limit."
        }
        return List(data.length()) { index ->
            val item = data.requireCoolingObject(index)
            item.requireCoolingKeys(SAMPLE_KEYS, "cooling history sample")
            DeviceCoolingHistorySample(
                sampledAtMs = item.requireCoolingLong(
                    DeviceCoolingRuntimeContract.Field.SAMPLED_AT_MS,
                    minimum = 0L
                ),
                temperatureC = item.requireCoolingDouble(
                    DeviceCoolingRuntimeContract.Field.TEMPERATURE_C,
                    HISTORY_MIN_TEMPERATURE_C,
                    HISTORY_MAX_TEMPERATURE_C
                )
            )
        }
    }

    private fun parseDays(data: JSONArray): List<DeviceCoolingHistoryDay> {
        require(data.length() <= DeviceCoolingRuntimeContract.Limit.MAX_HISTORY_DAY_COUNT) {
            "cooling history days exceed the safety limit."
        }
        return List(data.length()) { index ->
            val item = data.requireCoolingObject(index)
            item.requireCoolingKeys(DAY_KEYS, "cooling history day")
            DeviceCoolingHistoryDay(
                dayStartAtMs = item.requireCoolingLong(
                    DeviceCoolingRuntimeContract.Field.DAY_START_AT_MS,
                    minimum = 0L
                ),
                minimumTemperatureC = item.requireCoolingDouble(
                    DeviceCoolingRuntimeContract.Field.MIN_TEMPERATURE_C,
                    HISTORY_MIN_TEMPERATURE_C,
                    HISTORY_MAX_TEMPERATURE_C
                ),
                averageTemperatureC = item.requireCoolingDouble(
                    DeviceCoolingRuntimeContract.Field.AVG_TEMPERATURE_C,
                    HISTORY_MIN_TEMPERATURE_C,
                    HISTORY_MAX_TEMPERATURE_C
                ),
                maximumTemperatureC = item.requireCoolingDouble(
                    DeviceCoolingRuntimeContract.Field.MAX_TEMPERATURE_C,
                    HISTORY_MIN_TEMPERATURE_C,
                    HISTORY_MAX_TEMPERATURE_C
                )
            )
        }
    }

    private fun validate(snapshot: DeviceCoolingHistorySnapshot) {
        validateSummary(snapshot.summary)
        require(snapshot.samples.zipWithNext().all { (first, second) ->
            first.sampledAtMs <= second.sampledAtMs
        }) { "cooling history samples must be ordered by timestamp." }
        require(snapshot.days.map(DeviceCoolingHistoryDay::dayStartAtMs).distinct().size ==
            snapshot.days.size) {
            "cooling history day timestamps must be unique."
        }
        snapshot.days.forEach { day ->
            require(day.minimumTemperatureC <= day.averageTemperatureC)
            require(day.averageTemperatureC <= day.maximumTemperatureC)
        }
    }

    private fun validateSummary(summary: DeviceCoolingHistorySummary) {
        val values = listOf(
            summary.minimumTemperatureC,
            summary.averageTemperatureC,
            summary.maximumTemperatureC
        )
        require(values.all { value -> value == null } || values.all { value -> value != null }) {
            "cooling history summary must be either fully populated or fully empty."
        }
        if (values.all { value -> value != null }) {
            val minimum = checkNotNull(summary.minimumTemperatureC)
            val average = checkNotNull(summary.averageTemperatureC)
            val maximum = checkNotNull(summary.maximumTemperatureC)
            require(minimum <= average)
            require(average <= maximum)
        }
    }
}

private const val HISTORY_MIN_TEMPERATURE_C = -40.0
private const val HISTORY_MAX_TEMPERATURE_C = 125.0
