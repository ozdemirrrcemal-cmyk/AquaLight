package com.aqua.aqualight.data.devices.dosing

import kotlin.math.roundToLong
import org.json.JSONObject
import java.util.Locale

object EspDosingCommandClient {

    private const val MANUAL_ON_VALUE = 1f
    private const val MANUAL_OFF_VALUE = -1f

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
        enabled: Boolean
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
        startTime.ifBlank {
            "00:00"
        }

        val safeWeekDays =
        List(
            size = 7
        ) {
            index ->
            weekDays.getOrNull(
                index = index
            ) == true
        }

        val weekDaysJson =
        safeWeekDays.joinToString(
            separator = ","
        ) {
            selected ->
            if (selected) {
                "1"
            } else {
                "0"
            }
        }

        val doseText =
        String.format(
            java.util.Locale.US,
            "%.3f",
            safeDoseMl
        ).trimEnd(
            '0'
        ).trimEnd(
            '.'
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
              "$safeTimerIndex": {
                "Enabled": ${if (enabled) 1 else 0},
                "Name": ${JSONObject.quote("Single ${safeChannelIndex + 1}")},
                "GPIO_PWM": ${JSONObject.quote(safeGpioPwm)},
                "YE": $doseText,
                "WDay": [$weekDaysJson],
                "TimeStart": ${JSONObject.quote(safeStartTime)},
                "IntervalOff": "00:00",
                "Count": 1
              }
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
    enabled: Boolean
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

    val safeWeekDays =
        List(
            size = 7
        ) { index ->
            weekDays.getOrNull(
                index = index
            ) == true
        }

    val weekDaysJson =
        safeWeekDays.joinToString(
            separator = ","
        ) { selected ->
            if (selected) {
                "1"
            } else {
                "0"
            }
        }

    val perDoseText =
        formatFloatForJson(
            value = perDoseMl
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
              "$safeTimerIndex": {
                "Enabled": ${if (enabled) 1 else 0},
                "Name": ${JSONObject.quote("24 hourly ${safeChannelIndex + 1}")},
                "GPIO_PWM": ${JSONObject.quote(safeGpioPwm)},
                "YE": $perDoseText,
                "WDay": [$weekDaysJson],
                "TimeStart": ${JSONObject.quote(timeStart)},
                "IntervalOff": ${JSONObject.quote(intervalOff)},
                "Count": 24
              }
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

private const val ONE_HOUR_MS = 3_600_000L

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
            calibrationDurationMs.toDouble() / measuredAmountMl.toDouble()
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
            yeMsPerMl.toDouble() * doseMl.toDouble()
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
}