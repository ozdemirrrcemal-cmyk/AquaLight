package com.aqua.aqualight.data.devices.dosing

import kotlin.math.roundToLong
import org.json.JSONObject
import java.util.Locale

data class CustomDosingPeriodCommand(
    val startTime: String,
    val endTime: String,
    val doseCount: Int
)

object EspDosingCommandClient {

    private const val MANUAL_ON_VALUE = 1f
    private const val MANUAL_OFF_VALUE = -1f
    private const val ONE_HOUR_MS = 3_600_000L
    private const val CUSTOM_PERIOD_MAX_COUNT = 4

    suspend fun startPrime(
        deviceIp: String,
        channelIndex: Int
    ): Boolean {
        return setManualValue(
            deviceIp = deviceIp,
            channelIndex = channelIndex,
            value = MANUAL_ON_VALUE,
            durationMs = 60_000L
        )
    }

    suspend fun stopManual(
        deviceIp: String,
        channelIndex: Int
    ): Boolean {
        return setManualValue(
            deviceIp = deviceIp,
            channelIndex = channelIndex,
            value = MANUAL_OFF_VALUE,
            durationMs = 0L
        )
    }

    suspend fun runTimedDose(
        deviceIp: String,
        channelIndex: Int,
        durationMs: Long
    ): Boolean {
        val safeDurationMs =
            durationMs.coerceAtLeast(
                minimumValue = 1L
            )

        return setManualValue(
            deviceIp = deviceIp,
            channelIndex = channelIndex,
            value = MANUAL_ON_VALUE,
            durationMs = safeDurationMs
        )
    }

    suspend fun saveCalibrationCoefficient(
        deviceIp: String,
        channelIndex: Int,
        yeMsPerMl: Long,
        liquidName: String
    ): Boolean {
        val safeChannelIndex =
            channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )

        val safeYe =
            yeMsPerMl.coerceAtLeast(
                minimumValue = 1L
            )

        val safeLiquidName =
            liquidName.trim().ifBlank {
                "Channel ${safeChannelIndex + 1}"
            }

        val saveCalibrationJson =
            """
            {
              "LPWMChanelTimer": {
                "Data": {
                  "$safeChannelIndex": {
                    "Name": ${JSONObject.quote(safeLiquidName)},
                    "YE": $safeYe,
                    "Dimension": "ml"
                  }
                }
              }
            }
            """.trimIndent()

        val calibrationSaved =
            EspDosingHttpClient.postJson(
                deviceIp = deviceIp,
                requestJson = saveCalibrationJson
            ) != null

        if (!calibrationSaved) {
            return false
        }

        return saveTimerConfig(
            deviceIp = deviceIp
        )
    }

    suspend fun saveSingleModeSchedule(
        deviceIp: String,
        channelIndex: Int,
        timerIndex: Int,
        channelGpioPwm: String,
        doseMl: Float,
        startTime: String,
        weekDays: List<Boolean>,
        enabled: Boolean,
        oldTimerIndexesForChannel: List<Int> = emptyList()
    ): Boolean {
        val safeChannelIndex =
            channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )

        val safeTimerIndex =
            timerIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 99
            )

        val safeGpioPwm =
            channelGpioPwm.trim()

        if (
            safeGpioPwm.isBlank() ||
            safeGpioPwm == "-"
        ) {
            return false
        }

        val safeDoseMl =
            doseMl.coerceAtLeast(
                minimumValue = 0.01f
            )

        val safeStartTime =
            normalizeEspTime(
                value = startTime
            )

        val weekDaysJson =
            weekDaysToJson(
                weekDays = weekDays
            )

        val timerJsonEntries =
            mutableListOf<String>()

        oldTimerIndexesForChannel
            .distinct()
            .filter { oldTimerIndex ->
                oldTimerIndex in 0..99 &&
                    oldTimerIndex != safeTimerIndex
            }
            .forEach { oldTimerIndex ->
                timerJsonEntries.add(
                    createDisabledTimerJsonEntry(
                        timerIndex = oldTimerIndex
                    )
                )
            }

        timerJsonEntries.add(
            """
            "$safeTimerIndex": {
              "Enabled": ${if (enabled) 1 else 0},
              "Name": ${JSONObject.quote("Single ${safeChannelIndex + 1}")},
              "GPIO_PWM": ${JSONObject.quote(safeGpioPwm)},
              "YE": ${formatFloatForJson(safeDoseMl)},
              "WDay": [$weekDaysJson],
              "TimeStart": ${JSONObject.quote(safeStartTime)},
              "IntervalOff": "00:00",
              "Count": 1
            }
            """.trimIndent()
        )

        val requestJson =
            """
            {
              "LPWMChanelTimer": {
                "Data": {
                  "$safeChannelIndex": {
                    "Regime": "Auto"
                  }
                }
              },
              "LTimer": {
                "Data": {
                  ${timerJsonEntries.joinToString(separator = ",")}
                }
              }
            }
            """.trimIndent()

        val scheduleSaved =
            EspDosingHttpClient.postJson(
                deviceIp = deviceIp,
                requestJson = requestJson
            ) != null

        if (!scheduleSaved) {
            return false
        }

        return saveTimerConfig(
            deviceIp = deviceIp
        )
    }

    suspend fun saveHourly24ModeSchedule(
        deviceIp: String,
        channelIndex: Int,
        timerIndex: Int,
        channelGpioPwm: String,
        channelCalibrationYeMsPerMl: Long,
        dailyDoseMl: Float,
        selectedMinute: Int,
        weekDays: List<Boolean>,
        enabled: Boolean,
        oldTimerIndexesForChannel: List<Int> = emptyList()
    ): Boolean {
        val safeChannelIndex =
            channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )

        val safeTimerIndex =
            timerIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 99
            )

        val safeGpioPwm =
            channelGpioPwm.trim()

        if (
            safeGpioPwm.isBlank() ||
            safeGpioPwm == "-"
        ) {
            return false
        }

        val safeCalibrationYe =
            channelCalibrationYeMsPerMl.coerceAtLeast(
                minimumValue = 1L
            )

        val safeDailyDoseMl =
            dailyDoseMl.coerceAtLeast(
                minimumValue = 0.01f
            )

        val perDoseMl =
            safeDailyDoseMl / 24f

        val doseRunMs =
            (
                perDoseMl.toDouble() *
                    safeCalibrationYe.toDouble()
                ).roundToLong()
                .coerceAtLeast(
                    minimumValue = 1L
                )

        if (doseRunMs >= ONE_HOUR_MS) {
            return false
        }

        val intervalOffMs =
            (ONE_HOUR_MS - doseRunMs).coerceAtLeast(
                minimumValue = 1L
            )

        val safeMinute =
            selectedMinute.coerceIn(
                minimumValue = 0,
                maximumValue = 59
            )

        val timeStart =
            String.format(
                Locale.US,
                "00:%02d",
                safeMinute
            )

        val intervalOff =
            formatDurationForEsp(
                millis = intervalOffMs
            )

        val weekDaysJson =
            weekDaysToJson(
                weekDays = weekDays
            )

        val timerJsonEntries =
            mutableListOf<String>()

        oldTimerIndexesForChannel
            .distinct()
            .filter { oldTimerIndex ->
                oldTimerIndex in 0..99 &&
                    oldTimerIndex != safeTimerIndex
            }
            .forEach { oldTimerIndex ->
                timerJsonEntries.add(
                    createDisabledTimerJsonEntry(
                        timerIndex = oldTimerIndex
                    )
                )
            }

        timerJsonEntries.add(
            """
            "$safeTimerIndex": {
              "Enabled": ${if (enabled) 1 else 0},
              "Name": ${JSONObject.quote("24 hourly ${safeChannelIndex + 1}")},
              "GPIO_PWM": ${JSONObject.quote(safeGpioPwm)},
              "YE": ${formatFloatForJson(perDoseMl)},
              "WDay": [$weekDaysJson],
              "TimeStart": ${JSONObject.quote(timeStart)},
              "IntervalOff": ${JSONObject.quote(intervalOff)},
              "Count": 24
            }
            """.trimIndent()
        )

        val requestJson =
            """
            {
              "LPWMChanelTimer": {
                "Data": {
                  "$safeChannelIndex": {
                    "Regime": "Auto"
                  }
                }
              },
              "LTimer": {
                "Data": {
                  ${timerJsonEntries.joinToString(separator = ",")}
                }
              }
            }
            """.trimIndent()

        val scheduleSaved =
            EspDosingHttpClient.postJson(
                deviceIp = deviceIp,
                requestJson = requestJson
            ) != null

        if (!scheduleSaved) {
            return false
        }

        return saveTimerConfig(
            deviceIp = deviceIp
        )
    }

    suspend fun saveCustomPeriodsSchedule(
        deviceIp: String,
        channelIndex: Int,
        channelGpioPwm: String,
        channelCalibrationYeMsPerMl: Long,
        dailyDoseMl: Float,
        periods: List<CustomDosingPeriodCommand>,
        weekDays: List<Boolean>,
        oldTimerIndexesForChannel: List<Int>,
        enabled: Boolean
    ): Boolean {
        val safeChannelIndex =
            channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )

        val safeGpioPwm =
            channelGpioPwm.trim()

        if (
            safeGpioPwm.isBlank() ||
            safeGpioPwm == "-"
        ) {
            return false
        }

        val safeCalibrationYe =
            channelCalibrationYeMsPerMl.coerceAtLeast(
                minimumValue = 1L
            )

        val validPeriods =
            periods
                .map { period ->
                    period.copy(
                        startTime = normalizeEspTime(
                            value = period.startTime
                        ),
                        endTime = normalizeEspTime(
                            value = period.endTime
                        ),
                        doseCount = period.doseCount.coerceAtLeast(
                            minimumValue = 1
                        )
                    )
                }
                .filter { period ->
                    timeToMinutes(
                        value = period.endTime
                    ) > timeToMinutes(
                        value = period.startTime
                    )
                }
                .take(
                    n = CUSTOM_PERIOD_MAX_COUNT
                )

        if (validPeriods.isEmpty()) {
            return false
        }

        val totalDoseCount =
            validPeriods.sumOf { period ->
                period.doseCount
            }

        if (totalDoseCount <= 0) {
            return false
        }

        val safeDailyDoseMl =
            dailyDoseMl.coerceAtLeast(
                minimumValue = 0.01f
            )

        val perDoseMl =
            safeDailyDoseMl / totalDoseCount.toFloat()

        val doseRunMs =
            (
                perDoseMl.toDouble() *
                    safeCalibrationYe.toDouble()
                ).roundToLong()
                .coerceAtLeast(
                    minimumValue = 1L
                )

        val weekDaysJson =
            weekDaysToJson(
                weekDays = weekDays
            )

        val customSlotStartIndex =
            safeChannelIndex * CUSTOM_PERIOD_MAX_COUNT

        val newTimerIndexes =
            validPeriods.indices.map { periodIndex ->
                customSlotStartIndex + periodIndex
            }

        val timerJsonEntries =
            mutableListOf<String>()

        oldTimerIndexesForChannel
            .distinct()
            .filter { oldTimerIndex ->
                oldTimerIndex in 0..99 &&
                    !newTimerIndexes.contains(
                        element = oldTimerIndex
                    )
            }
            .forEach { oldTimerIndex ->
                timerJsonEntries.add(
                    createDisabledTimerJsonEntry(
                        timerIndex = oldTimerIndex
                    )
                )
            }

        validPeriods.forEachIndexed { periodIndex, period ->
            val timerIndex =
                customSlotStartIndex + periodIndex

            val periodStartMinutes =
                timeToMinutes(
                    value = period.startTime
                )

            val periodEndMinutes =
                timeToMinutes(
                    value = period.endTime
                )

            val periodDurationMs =
                (periodEndMinutes - periodStartMinutes) * 60_000L

            val spacingMs =
                (
                    periodDurationMs.toDouble() /
                        period.doseCount.toDouble()
                    ).roundToLong()
                    .coerceAtLeast(
                        minimumValue = 1L
                    )

            if (doseRunMs >= spacingMs) {
                return false
            }

            val intervalOffMs =
                (spacingMs - doseRunMs).coerceAtLeast(
                    minimumValue = 1L
                )

            val intervalOff =
                formatDurationForEsp(
                    millis = intervalOffMs
                )

            timerJsonEntries.add(
                """
                "$timerIndex": {
                  "Enabled": ${if (enabled) 1 else 0},
                  "Name": ${JSONObject.quote("Custom C${safeChannelIndex + 1} P${periodIndex + 1}")},
                  "GPIO_PWM": ${JSONObject.quote(safeGpioPwm)},
                  "YE": ${formatFloatForJson(perDoseMl)},
                  "WDay": [$weekDaysJson],
                  "TimeStart": ${JSONObject.quote(period.startTime)},
                  "IntervalOff": ${JSONObject.quote(intervalOff)},
                  "Count": ${period.doseCount}
                }
                """.trimIndent()
            )
        }

        val requestJson =
            """
            {
              "LPWMChanelTimer": {
                "Data": {
                  "$safeChannelIndex": {
                    "Regime": "Auto"
                  }
                }
              },
              "LTimer": {
                "Data": {
                  ${timerJsonEntries.joinToString(separator = ",")}
                }
              }
            }
            """.trimIndent()

        val scheduleSaved =
            EspDosingHttpClient.postJson(
                deviceIp = deviceIp,
                requestJson = requestJson
            ) != null

        if (!scheduleSaved) {
            return false
        }

        return saveTimerConfig(
            deviceIp = deviceIp
        )
    }

    suspend fun resetCalibrationCoefficient(
        deviceIp: String,
        channelIndex: Int
    ): Boolean {
        val safeChannelIndex =
            channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )

        val resetJson =
            """
            {
              "LPWMChanelTimer": {
                "Data": {
                  "$safeChannelIndex": {
                    "YE": 0,
                    "Dimension": "ml"
                  }
                }
              }
            }
            """.trimIndent()

        val resetDone =
            EspDosingHttpClient.postJson(
                deviceIp = deviceIp,
                requestJson = resetJson
            ) != null

        if (!resetDone) {
            return false
        }

        return saveTimerConfig(
            deviceIp = deviceIp
        )
    }

    fun calculateYeMsPerMl(
        calibrationDurationMs: Long,
        measuredAmountMl: Float
    ): Long? {
        if (
            calibrationDurationMs <= 0L ||
            measuredAmountMl <= 0f
        ) {
            return null
        }

        return (
            calibrationDurationMs.toDouble() /
                measuredAmountMl.toDouble()
            ).roundToLong()
            .coerceAtLeast(
                minimumValue = 1L
            )
    }

    fun calculateDurationForDose(
        yeMsPerMl: Long,
        doseMl: Float
    ): Long? {
        if (
            yeMsPerMl <= 0L ||
            doseMl <= 0f
        ) {
            return null
        }

        return (
            yeMsPerMl.toDouble() *
                doseMl.toDouble()
            ).roundToLong()
            .coerceAtLeast(
                minimumValue = 1L
            )
    }

    private suspend fun setManualValue(
        deviceIp: String,
        channelIndex: Int,
        value: Float,
        durationMs: Long
    ): Boolean {
        val safeChannelIndex =
            channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )

        val safeDurationMs =
            durationMs.coerceAtLeast(
                minimumValue = 0L
            )

        val requestJson =
            """
            {
              "LPWMChanelTimer": {
                "Data": {
                  "$safeChannelIndex": {
                    "VManual": {
                      "V": $value,
                      "TOffMs": $safeDurationMs
                    }
                  }
                }
              }
            }
            """.trimIndent()

        return EspDosingHttpClient.postJson(
            deviceIp = deviceIp,
            requestJson = requestJson
        ) != null
    }

    private suspend fun saveTimerConfig(
        deviceIp: String
    ): Boolean {
        val requestJson =
            """
            {
              "Main": {
                "SaveTimer": 0
              }
            }
            """.trimIndent()

        return EspDosingHttpClient.postJson(
            deviceIp = deviceIp,
            requestJson = requestJson
        ) != null
    }

    private fun createDisabledTimerJsonEntry(
        timerIndex: Int
    ): String {
        return """
        "$timerIndex": {
          "Enabled": 0,
          "Name": ${JSONObject.quote("Disabled")},
          "GPIO_PWM": "-",
          "YE": 0,
          "WDay": [0,0,0,0,0,0,0],
          "TimeStart": "00:00",
          "IntervalOff": "00:00",
          "Count": 0
        }
        """.trimIndent()
    }

    private fun weekDaysToJson(
        weekDays: List<Boolean>
    ): String {
        val safeWeekDays =
            List(
                size = 7
            ) { index ->
                weekDays.getOrNull(
                    index = index
                ) == true
            }

        return safeWeekDays.joinToString(
            separator = ","
        ) { selected ->
            if (selected) {
                "1"
            } else {
                "0"
            }
        }
    }

    private fun formatFloatForJson(
        value: Float
    ): String {
        return String.format(
            Locale.US,
            "%.4f",
            value
        ).trimEnd(
            '0'
        ).trimEnd(
            '.'
        )
    }

    private fun normalizeEspTime(
        value: String
    ): String {
        val parts =
            value.ifBlank {
                "00:00"
            }.split(
                delimiter = ":"
            )

        val hour =
            parts.getOrNull(
                index = 0
            )?.toIntOrNull()
                ?.coerceIn(
                    minimumValue = 0,
                    maximumValue = 23
                ) ?: 0

        val minute =
            parts.getOrNull(
                index = 1
            )?.toIntOrNull()
                ?.coerceIn(
                    minimumValue = 0,
                    maximumValue = 59
                ) ?: 0

        return String.format(
            Locale.US,
            "%02d:%02d",
            hour,
            minute
        )
    }

    private fun timeToMinutes(
        value: String
    ): Int {
        val normalized =
            normalizeEspTime(
                value = value
            )

        val parts =
            normalized.split(
                delimiter = ":"
            )

        val hour =
            parts.getOrNull(
                index = 0
            )?.toIntOrNull() ?: 0

        val minute =
            parts.getOrNull(
                index = 1
            )?.toIntOrNull() ?: 0

        return hour * 60 + minute
    }

    private fun formatDurationForEsp(
        millis: Long
    ): String {
        var remaining =
            millis.coerceAtLeast(
                minimumValue = 0L
            )

        val hours =
            remaining / ONE_HOUR_MS

        remaining -=
            hours * ONE_HOUR_MS

        val minutes =
            remaining / 60_000L

        remaining -=
            minutes * 60_000L

        val seconds =
            remaining / 1_000L

        remaining -=
            seconds * 1_000L

        return if (remaining > 0L) {
            String.format(
                Locale.US,
                "%02d:%02d:%02d.%03d",
                hours,
                minutes,
                seconds,
                remaining
            )
        } else if (seconds > 0L) {
            String.format(
                Locale.US,
                "%02d:%02d:%02d",
                hours,
                minutes,
                seconds
            )
        } else {
            String.format(
                Locale.US,
                "%02d:%02d",
                hours,
                minutes
            )
        }
    }
}