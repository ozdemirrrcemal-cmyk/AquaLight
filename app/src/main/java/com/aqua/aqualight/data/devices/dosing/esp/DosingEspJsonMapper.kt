package com.aqua.aqualight.data.devices.dosing.esp

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

object DosingEspJsonMapper {

    private const val KEY_L_PWM_CHANNEL_TIMER = "LPWMChanelTimer"
    private const val KEY_L_TIMER = "LTimer"
    private const val KEY_DATA = "Data"
    private const val KEY_MAIN = "Main"

    fun createReadDosingStatePayload(
        channelIndex: Int
    ): JSONObject {
        val indexKey =
            channelIndex.coerceAtLeast(
                minimumValue = 0
            ).toString()

        return JSONObject().apply {
            put(
                KEY_L_PWM_CHANNEL_TIMER,
                JSONObject().apply {
                    put(
                        KEY_DATA,
                        JSONObject().apply {
                            put(
                                indexKey,
                                JSONObject().apply {
                                    put("Name", 0)
                                    put("Regime", 0)
                                    put("GPIO_PWM", 0)
                                    put("YE", 0)
                                    put("Dimension", 0)
                                    put("Rest", 0)
                                    put("VNow", 0)
                                    put("VMin", 0)
                                    put("VMax", 0)
                                }
                            )
                        }
                    )
                }
            )

            put(
                KEY_L_TIMER,
                JSONObject().apply {
                    put(
                        KEY_DATA,
                        JSONObject().apply {
                            put(
                                indexKey,
                                JSONObject().apply {
                                    put("Enabled", 0)
                                    put("Name", 0)
                                    put("GPIO_PWM", 0)
                                    put("YE", 0)
                                    put("WDay", 0)
                                    put("TimeStart", 0)
                                    put("IntervalOn", 0)
                                    put("IntervalOff", 0)
                                    put("Count", 0)
                                    put("Status", 0)
                                }
                            )
                        }
                    )
                }
            )
        }
    }

    fun parseDosingState(
        response: JSONObject,
        channelIndex: Int
    ): DosingEspState {
        val indexKey =
            channelIndex.coerceAtLeast(
                minimumValue = 0
            ).toString()

        val channelJson =
            response.optJSONObject(
                KEY_L_PWM_CHANNEL_TIMER
            )?.optJSONObject(
                KEY_DATA
            )?.optJSONObject(
                indexKey
            ) ?: JSONObject()

        val timerJson =
            response.optJSONObject(
                KEY_L_TIMER
            )?.optJSONObject(
                KEY_DATA
            )?.optJSONObject(
                indexKey
            ) ?: JSONObject()

        val channelState =
            parseChannelState(
                json = channelJson
            )

        val timerState =
            parseTimerState(
                json = timerJson,
                fallbackGpioPwm = channelState.gpioPwm
            )

        return DosingEspState(
            channel = channelState,
            timer = timerState,
            activeMode = detectActiveMode(
                timerName = timerState.name
            )
        )
    }

    fun createSingleSchedulePayload(
        channelIndex: Int,
        channelNumber: Int,
        gpioPwm: String,
        totalDailyDoseMl: Float,
        weekDays: List<Boolean>,
        startTime: String,
        enabled: Boolean
    ): JSONObject {
        return createTimerDataPayload(
            channelIndex = channelIndex,
            timer = DosingTimerSavePayload(
                enabled = enabled,
                name = createTimerName(
                    channelNumber = channelNumber,
                    mode = DosingScheduleMode.SINGLE
                ),
                gpioPwm = gpioPwm,
                dosePerRunMl = totalDailyDoseMl.coerceAtLeast(
                    minimumValue = 0f
                ),
                weekDays = weekDays,
                timeStart = normalizeTime(
                    value = startTime
                ),
                intervalOn = "00:00",
                intervalOff = "00:00",
                count = 1
            )
        )
    }

    fun createHourly24SchedulePayload(
        channelIndex: Int,
        channelNumber: Int,
        gpioPwm: String,
        totalDailyDoseMl: Float,
        weekDays: List<Boolean>,
        startTime: String,
        enabled: Boolean
    ): JSONObject {
        val count =
            24

        val dosePerRunMl =
            totalDailyDoseMl.coerceAtLeast(
                minimumValue = 0f
            ) / count

        return createTimerDataPayload(
            channelIndex = channelIndex,
            timer = DosingTimerSavePayload(
                enabled = enabled,
                name = createTimerName(
                    channelNumber = channelNumber,
                    mode = DosingScheduleMode.HOURLY_24
                ),
                gpioPwm = gpioPwm,
                dosePerRunMl = dosePerRunMl,
                weekDays = weekDays,
                timeStart = normalizeTime(
                    value = startTime
                ),
                intervalOn = "00:00",
                intervalOff = "01:00",
                count = count
            )
        )
    }

    fun createGenericTimerSchedulePayload(
        channelIndex: Int,
        channelNumber: Int,
        mode: DosingScheduleMode,
        gpioPwm: String,
        dosePerRunMl: Float,
        weekDays: List<Boolean>,
        timeStart: String,
        intervalOn: String,
        intervalOff: String,
        count: Int,
        enabled: Boolean
    ): JSONObject {
        return createTimerDataPayload(
            channelIndex = channelIndex,
            timer = DosingTimerSavePayload(
                enabled = enabled,
                name = createTimerName(
                    channelNumber = channelNumber,
                    mode = mode
                ),
                gpioPwm = gpioPwm,
                dosePerRunMl = dosePerRunMl.coerceAtLeast(
                    minimumValue = 0f
                ),
                weekDays = weekDays,
                timeStart = normalizeTime(
                    value = timeStart
                ),
                intervalOn = intervalOn.ifBlank {
                    "00:00"
                },
                intervalOff = intervalOff.ifBlank {
                    "00:00"
                },
                count = count.coerceAtLeast(
                    minimumValue = 1
                )
            )
        )
    }

    fun createManualDosePayload(
        channelIndex: Int,
        doseMl: Float,
        calibrationMsPerMl: Long
    ): JSONObject {
        val safeDurationMs =
            max(
                0L,
                (doseMl.coerceAtLeast(
                    minimumValue = 0f
                ) * calibrationMsPerMl).roundToLong()
            )

        return JSONObject().apply {
            put(
                KEY_L_PWM_CHANNEL_TIMER,
                JSONObject().apply {
                    put(
                        KEY_DATA,
                        JSONObject().apply {
                            put(
                                channelIndex.coerceAtLeast(
                                    minimumValue = 0
                                ).toString(),
                                JSONObject().apply {
                                    put(
                                        "VManual",
                                        JSONObject().apply {
                                            put(
                                                "V",
                                                1
                                            )

                                            put(
                                                "TOffMs",
                                                safeDurationMs
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
    }

    fun createSaveTimerPayload(): JSONObject {
        return JSONObject().apply {
            put(
                KEY_MAIN,
                JSONObject().apply {
                    put(
                        "SaveTimer",
                        1
                    )
                }
            )
        }
    }

    fun createTimerName(
        channelNumber: Int,
        mode: DosingScheduleMode
    ): String {
        val suffix =
            when (mode) {
                DosingScheduleMode.SINGLE -> {
                    "SINGLE"
                }

                DosingScheduleMode.HOURLY_24 -> {
                    "HOURLY_24"
                }

                DosingScheduleMode.CUSTOM_PERIODS -> {
                    "CUSTOM_PERIODS"
                }

                DosingScheduleMode.TIMER -> {
                    "TIMER"
                }
            }

        return "AQL_CH${channelNumber}_$suffix"
    }

    private fun createTimerDataPayload(
        channelIndex: Int,
        timer: DosingTimerSavePayload
    ): JSONObject {
        return JSONObject().apply {
            put(
                KEY_L_TIMER,
                JSONObject().apply {
                    put(
                        KEY_DATA,
                        JSONObject().apply {
                            put(
                                channelIndex.coerceAtLeast(
                                    minimumValue = 0
                                ).toString(),
                                createTimerObject(
                                    timer = timer
                                )
                            )
                        }
                    )
                }
            )
        }
    }

    private fun createTimerObject(
        timer: DosingTimerSavePayload
    ): JSONObject {
        return JSONObject().apply {
            put(
                "Enabled",
                timer.enabled
            )

            put(
                "Name",
                timer.name
            )

            put(
                "YE",
                formatFloatForEsp(
                    value = timer.dosePerRunMl
                )
            )

            put(
                "GPIO_PWM",
                timer.gpioPwm
            )

            put(
                "WDay",
                createWeekDayArray(
                    weekDays = timer.weekDays
                )
            )

            put(
                "TimeStart",
                normalizeTime(
                    value = timer.timeStart
                )
            )

            put(
                "IntervalOn",
                timer.intervalOn.ifBlank {
                    "00:00"
                }
            )

            put(
                "IntervalOff",
                timer.intervalOff.ifBlank {
                    "00:00"
                }
            )

            put(
                "Count",
                timer.count.coerceAtLeast(
                    minimumValue = 1
                )
            )
        }
    }

    private fun parseChannelState(
        json: JSONObject
    ): DosingEspChannelState {
        return DosingEspChannelState(
            gpioPwm = json.optString(
                "GPIO_PWM",
                "-"
            ),
            name = json.optString(
                "Name",
                ""
            ),
            regime = json.optString(
                "Regime",
                "Off"
            ),
            calibrationMsPerMl = json.optLongFlexible(
                key = "YE",
                defaultValue = 0L
            ),
            dimension = json.optString(
                "Dimension",
                "ml"
            ),
            restMl = json.optNullableFloat(
                key = "Rest"
            ),
            vNow = json.optNullableFloat(
                key = "VNow"
            ),
            vMin = json.optNullableFloat(
                key = "VMin"
            ),
            vMax = json.optNullableFloat(
                key = "VMax"
            )
        )
    }

    private fun parseTimerState(
        json: JSONObject,
        fallbackGpioPwm: String
    ): DosingEspTimerState {
        return DosingEspTimerState(
            enabled = json.optBooleanFlexible(
                key = "Enabled",
                defaultValue = false
            ),
            name = json.optString(
                "Name",
                ""
            ),
            gpioPwm = json.optString(
                "GPIO_PWM",
                fallbackGpioPwm
            ),
            dosePerRunMl = json.optFloatFlexible(
                key = "YE",
                defaultValue = 0f
            ),
            weekDays = parseWeekDays(
                jsonArray = json.optJSONArray(
                    "WDay"
                )
            ),
            timeStart = normalizeTime(
                value = json.optString(
                    "TimeStart",
                    "00:00"
                )
            ),
            intervalOn = json.optString(
                "IntervalOn",
                "00:00"
            ),
            intervalOff = json.optString(
                "IntervalOff",
                "00:00"
            ),
            count = json.optIntFlexible(
                key = "Count",
                defaultValue = 0
            ),
            status = if (json.has("Status")) {
                json.optString(
                    "Status"
                )
            } else {
                null
            }
        )
    }

    private fun detectActiveMode(
        timerName: String
    ): DosingScheduleMode {
        return when {
            timerName.contains(
                other = "HOURLY_24",
                ignoreCase = true
            ) -> {
                DosingScheduleMode.HOURLY_24
            }

            timerName.contains(
                other = "CUSTOM_PERIODS",
                ignoreCase = true
            ) -> {
                DosingScheduleMode.CUSTOM_PERIODS
            }

            timerName.contains(
                other = "TIMER",
                ignoreCase = true
            ) -> {
                DosingScheduleMode.TIMER
            }

            else -> {
                DosingScheduleMode.SINGLE
            }
        }
    }

    private fun createWeekDayArray(
        weekDays: List<Boolean>
    ): JSONArray {
        val safeWeekDays =
            if (weekDays.size == 7) {
                weekDays
            } else {
                List(
                    size = 7
                ) {
                    true
                }
            }

        return JSONArray().apply {
            safeWeekDays.forEach { selected ->
                put(
                    if (selected) {
                        1
                    } else {
                        0
                    }
                )
            }
        }
    }

    private fun parseWeekDays(
        jsonArray: JSONArray?
    ): List<Boolean> {
        if (jsonArray == null) {
            return List(
                size = 7
            ) {
                true
            }
        }

        return List(
            size = 7
        ) { index ->
            val value =
                jsonArray.opt(
                    index
                )

            when (value) {
                is Boolean -> {
                    value
                }

                is Number -> {
                    value.toInt() != 0
                }

                is String -> {
                    value == "1" ||
                        value.equals(
                            other = "true",
                            ignoreCase = true
                        )
                }

                else -> {
                    false
                }
            }
        }
    }

    private fun normalizeTime(
        value: String
    ): String {
        val parts =
            value.ifBlank {
                "00:00"
            }.split(
                ":"
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

    private fun formatFloatForEsp(
        value: Float
    ): Double {
        return String.format(
            Locale.US,
            "%.3f",
            value
        ).trimEnd(
            '0'
        ).trimEnd(
            '.'
        ).ifBlank {
            "0"
        }.toDouble()
    }

    private fun JSONObject.optBooleanFlexible(
        key: String,
        defaultValue: Boolean
    ): Boolean {
        if (!has(key)) {
            return defaultValue
        }

        return when (val value = opt(key)) {
            is Boolean -> {
                value
            }

            is Number -> {
                value.toInt() != 0
            }

            is String -> {
                value == "1" ||
                    value.equals(
                        other = "true",
                        ignoreCase = true
                    )
            }

            else -> {
                defaultValue
            }
        }
    }

    private fun JSONObject.optFloatFlexible(
        key: String,
        defaultValue: Float
    ): Float {
        if (!has(key)) {
            return defaultValue
        }

        return when (val value = opt(key)) {
            is Number -> {
                value.toFloat()
            }

            is String -> {
                value.replace(
                    oldValue = ",",
                    newValue = "."
                ).toFloatOrNull() ?: defaultValue
            }

            else -> {
                defaultValue
            }
        }
    }

    private fun JSONObject.optLongFlexible(
        key: String,
        defaultValue: Long
    ): Long {
        if (!has(key)) {
            return defaultValue
        }

        return when (val value = opt(key)) {
            is Number -> {
                value.toLong()
            }

            is String -> {
                value.toLongOrNull() ?: defaultValue
            }

            else -> {
                defaultValue
            }
        }
    }

    private fun JSONObject.optIntFlexible(
        key: String,
        defaultValue: Int
    ): Int {
        if (!has(key)) {
            return defaultValue
        }

        return when (val value = opt(key)) {
            is Number -> {
                value.toInt()
            }

            is String -> {
                value.toIntOrNull() ?: defaultValue
            }

            else -> {
                defaultValue
            }
        }
    }

    private fun JSONObject.optNullableFloat(
        key: String
    ): Float? {
        if (!has(key)) {
            return null
        }

        return optFloatFlexible(
            key = key,
            defaultValue = 0f
        )
    }
}