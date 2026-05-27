package com.aqua.aqualight.data.devices.dosing

import org.json.JSONArray
import org.json.JSONObject

data class EspDosingChannelState(
    val channelIndex: Int,
    val name: String,
    val regime: String,
    val gpioPwm: String,
    val calibrationYeMsPerMl: Long,
    val dimension: String,
    val restMl: Float?,
    val currentValue: Float?,
    val calibrated: Boolean
)

data class EspDosingTimerState(
    val timerIndex: Int,
    val enabled: Boolean,
    val name: String,
    val gpioPwm: String,
    val doseMl: Float,
    val weekDays: List<Boolean>,
    val timeStart: String,
    val intervalOff: String,
    val count: Int,
    val status: String
)

data class EspDosingChannelSettingsSnapshot(
    val channel: EspDosingChannelState,
    val timer: EspDosingTimerState?,
    val timersForChannel: List<EspDosingTimerState>
)

object EspDosingSettingsClient {

    suspend fun readChannelSettingsSnapshot(
        deviceIp: String,
        channelIndex: Int
    ): EspDosingChannelSettingsSnapshot? {
        val channel =
            readChannelState(
                deviceIp = deviceIp,
                channelIndex = channelIndex
            ) ?: return null

        val timers =
            readTimers(
                deviceIp = deviceIp
            )

        val timersForChannel =
            timers
                .filter { timer ->
                    timer.gpioPwm.isNotBlank() &&
                        timer.gpioPwm != "-" &&
                        timer.gpioPwm == channel.gpioPwm
                }
                .sortedBy { timer ->
                    timer.timerIndex
                }

        val primaryTimer =
            timersForChannel.firstOrNull()

        return EspDosingChannelSettingsSnapshot(
            channel = channel,
            timer = primaryTimer,
            timersForChannel = timersForChannel
        )
    }

    suspend fun readChannelState(
        deviceIp: String,
        channelIndex: Int
    ): EspDosingChannelState? {
        val safeChannelIndex =
            channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )

        val requestJson =
            """
            {
              "LPWMChanelTimer": {
                "Data": {
                  "$safeChannelIndex": {
                    "Name": 0,
                    "Regime": 0,
                    "GPIO_PWM": 0,
                    "YE": 0,
                    "Dimension": 0,
                    "Rest": 0,
                    "VNow": 0
                  }
                }
              }
            }
            """.trimIndent()

        val root =
            EspDosingHttpClient.getJson(
                deviceIp = deviceIp,
                requestJson = requestJson
            ) ?: return null

        val channelJson =
            root.optJSONObject("LPWMChanelTimer")
                ?.optJSONObject("Data")
                ?.optJSONObject(safeChannelIndex.toString())
                ?: return null

        val calibrationYe =
            channelJson.optLong(
                "YE",
                -1L
            )

        return EspDosingChannelState(
            channelIndex = safeChannelIndex,
            name = channelJson.optString(
                "Name",
                "Channel ${safeChannelIndex + 1}"
            ),
            regime = channelJson.optString(
                "Regime",
                "Off"
            ),
            gpioPwm = channelJson.optString(
                "GPIO_PWM",
                "-"
            ),
            calibrationYeMsPerMl = calibrationYe,
            dimension = channelJson.optString(
                "Dimension",
                "ml"
            ),
            restMl = channelJson.optFloatOrNull(
                key = "Rest"
            ),
            currentValue = channelJson.optFloatOrNull(
                key = "VNow"
            ),
            calibrated = calibrationYe >= 1L
        )
    }

    suspend fun readTimers(
        deviceIp: String
    ): List<EspDosingTimerState> {
        val requestJson =
            """
            {
              "LTimer": {
                "Count": 0,
                "Data": {
                  "All": {
                    "Enabled": 0,
                    "Name": 0,
                    "GPIO_PWM": 0,
                    "YE": 0,
                    "WDay": 0,
                    "TimeStart": 0,
                    "IntervalOff": 0,
                    "Count": 0,
                    "Status": 0
                  }
                }
              }
            }
            """.trimIndent()

        val root =
            EspDosingHttpClient.getJson(
                deviceIp = deviceIp,
                requestJson = requestJson
            ) ?: return emptyList()

        val dataJson =
            root.optJSONObject("LTimer")
                ?.optJSONObject("Data")
                ?: return emptyList()

        val timers =
            mutableListOf<EspDosingTimerState>()

        val keys =
            dataJson.keys()

        while (keys.hasNext()) {
            val key =
                keys.next()

            val timerIndex =
                key.toIntOrNull() ?: continue

            val timerJson =
                dataJson.optJSONObject(
                    key
                ) ?: continue

            timers.add(
                timerJson.toTimerState(
                    timerIndex = timerIndex
                )
            )
        }

        return timers.sortedBy { timer ->
            timer.timerIndex
        }
    }

    private fun JSONObject.toTimerState(
        timerIndex: Int
    ): EspDosingTimerState {
        return EspDosingTimerState(
            timerIndex = timerIndex,
            enabled = optBooleanCompat(
                key = "Enabled"
            ),
            name = optString(
                "Name",
                "Timer $timerIndex"
            ),
            gpioPwm = optString(
                "GPIO_PWM",
                "-"
            ),
            doseMl = optFloatOrNull(
                key = "YE"
            ) ?: 0f,
            weekDays = optWeekDays(
                key = "WDay"
            ),
            timeStart = optString(
                "TimeStart",
                "00:00"
            ),
            intervalOff = optString(
                "IntervalOff",
                "00:00"
            ),
            count = optInt(
                "Count",
                0
            ),
            status = optString(
                "Status",
                "Disabled"
            )
        )
    }

    private fun JSONObject.optFloatOrNull(
        key: String
    ): Float? {
        if (!has(key) || isNull(key)) {
            return null
        }

        return runCatching {
            when (val value = get(key)) {
                is Number -> {
                    value.toFloat()
                }

                is String -> {
                    value.replace(
                        oldValue = ",",
                        newValue = "."
                    ).toFloatOrNull()
                }

                else -> {
                    null
                }
            }
        }.getOrNull()
    }

    private fun JSONObject.optBooleanCompat(
        key: String
    ): Boolean {
        if (!has(key) || isNull(key)) {
            return false
        }

        return when (val value = opt(key)) {
            is Boolean -> {
                value
            }

            is Number -> {
                value.toInt() != 0
            }

            is String -> {
                value.equals(
                    other = "true",
                    ignoreCase = true
                ) || value == "1"
            }

            else -> {
                false
            }
        }
    }

    private fun JSONObject.optWeekDays(
        key: String
    ): List<Boolean> {
        val rawValue =
            opt(key)

        val array =
            when (rawValue) {
                is JSONArray -> {
                    rawValue
                }

                is String -> {
                    runCatching {
                        JSONArray(
                            rawValue
                        )
                    }.getOrNull() ?: JSONArray()
                }

                else -> {
                    JSONArray()
                }
            }

        return List(
            size = 7
        ) { index ->
            when (val value = array.opt(index)) {
                is Boolean -> {
                    value
                }

                is Number -> {
                    value.toInt() != 0
                }

                is String -> {
                    value.equals(
                        other = "true",
                        ignoreCase = true
                    ) || value == "1"
                }

                else -> {
                    false
                }
            }
        }
    }
}