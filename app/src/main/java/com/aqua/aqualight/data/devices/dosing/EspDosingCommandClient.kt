package com.aqua.aqualight.data.devices.dosing

import kotlin.math.roundToLong
import org.json.JSONObject

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
                "Name": ${org.json.JSONObject.quote(safeLiquidName)},
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