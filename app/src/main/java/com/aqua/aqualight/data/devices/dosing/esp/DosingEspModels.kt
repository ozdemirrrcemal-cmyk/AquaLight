package com.aqua.aqualight.data.devices.dosing.esp

enum class DosingScheduleMode {
    SINGLE,
    HOURLY_24,
    CUSTOM_PERIODS,
    TIMER
}

object DosingGpioPwmMapping {

    private const val GPIO_PWM_CHANNEL_1 = "G25|0|16"
    private const val GPIO_PWM_CHANNEL_2 = "G26|1|16"
    private const val GPIO_PWM_CHANNEL_3 = "G27|2|16"
    private const val GPIO_PWM_CHANNEL_4 = "G33|3|16"

    fun fixedGpioPwmForChannel(
        channelIndex: Int
    ): String {
        return when (
            channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )
        ) {
            0 -> GPIO_PWM_CHANNEL_1
            1 -> GPIO_PWM_CHANNEL_2
            2 -> GPIO_PWM_CHANNEL_3
            else -> GPIO_PWM_CHANNEL_4
        }
    }

    fun fixedGpioPwmForChannelNumber(
        channelNumber: Int
    ): String {
        return fixedGpioPwmForChannel(
            channelIndex = channelNumber.coerceIn(
                minimumValue = 1,
                maximumValue = 4
            ) - 1
        )
    }

    fun isKnownGpioPwm(
        value: String
    ): Boolean {
        return when (value.trim()) {
            GPIO_PWM_CHANNEL_1,
            GPIO_PWM_CHANNEL_2,
            GPIO_PWM_CHANNEL_3,
            GPIO_PWM_CHANNEL_4 -> true

            else -> false
        }
    }

    fun areSameGpioPwm(
        first: String,
        second: String
    ): Boolean {
        val cleanFirst =
            first.trim()

        val cleanSecond =
            second.trim()

        return isKnownGpioPwm(
            value = cleanFirst
        ) &&
            isKnownGpioPwm(
                value = cleanSecond
            ) &&
            cleanFirst == cleanSecond
    }
}

data class DosingEspChannelState(
    val gpioPwm: String,
    val name: String,
    val regime: String,
    val calibrationMsPerMl: Long,
    val dimension: String,
    val restMl: Float?,
    val vNow: Float?,
    val vMin: Float?,
    val vMax: Float?
) {
    val isCalibrated: Boolean
        get() = calibrationMsPerMl > 0L
}

data class DosingEspTimerState(
    val index: Int = -1,
    val enabled: Boolean,
    val name: String,
    val gpioPwm: String,
    val dosePerRunMl: Float,
    val weekDays: List<Boolean>,
    val timeStart: String,
    val intervalOn: String,
    val intervalOff: String,
    val count: Int,
    val status: String?
) {
    val configuredDailyDoseMl: Float
        get() =
            dosePerRunMl * count.coerceAtLeast(
                minimumValue = 0
            )

    val activeDailyDoseMl: Float
        get() =
            if (enabled) {
                configuredDailyDoseMl
            } else {
                0f
            }

    val hasValidGpioPwm: Boolean
        get() =
            DosingGpioPwmMapping.isKnownGpioPwm(
                value = gpioPwm
            )

    val isAquaLightTimer: Boolean
        get() =
            name.startsWith(
                prefix = "AQL_",
                ignoreCase = true
            )

    fun belongsToGpioPwm(
        targetGpioPwm: String
    ): Boolean {
        return DosingGpioPwmMapping.areSameGpioPwm(
            first = gpioPwm,
            second = targetGpioPwm
        )
    }
}

data class DosingEspState(
    val channel: DosingEspChannelState,

    val timer: DosingEspTimerState,

    val activeMode: DosingScheduleMode,

    val customPeriodTimers: List<DosingEspTimerState> = emptyList(),

    val timers: List<DosingEspTimerState> =
        buildList {
            add(
                timer
            )

            customPeriodTimers.forEach { customTimer ->
                if (
                    none { existingTimer ->
                        existingTimer.index == customTimer.index &&
                            existingTimer.index >= 0
                    }
                ) {
                    add(
                        customTimer
                    )
                }
            }
        }
) {
    val channelTimers: List<DosingEspTimerState>
        get() {
            val channelGpioPwm =
                channel.gpioPwm.trim()

            if (
                !DosingGpioPwmMapping.isKnownGpioPwm(
                    value = channelGpioPwm
                )
            ) {
                return emptyList()
            }

            return timers
                .filter { timer ->
                    timer.belongsToGpioPwm(
                        targetGpioPwm = channelGpioPwm
                    )
                }
                .sortedBy { timer ->
                    timer.index
                }
        }

    val aquaLightChannelTimers: List<DosingEspTimerState>
        get() =
            channelTimers.filter { timer ->
                timer.isAquaLightTimer
            }

    val customPeriodsConfiguredDailyDoseMl: Float
        get() =
            customPeriodTimers.sumOf { timer ->
                timer.configuredDailyDoseMl.toDouble()
            }.toFloat()

    val channelConfiguredDailyDoseMl: Float
        get() =
            channelTimers.sumOf { timer ->
                timer.configuredDailyDoseMl.toDouble()
            }.toFloat()

    val channelActiveDailyDoseMl: Float
        get() =
            channelTimers.sumOf { timer ->
                timer.activeDailyDoseMl.toDouble()
            }.toFloat()

    val scheduleEnabled: Boolean
        get() =
            if (channelTimers.isEmpty()) {
                timer.enabled
            } else {
                channelTimers.any { timer ->
                    timer.enabled
                }
            }

    val configuredDailyDoseMl: Float
        get() =
            when {
                channelConfiguredDailyDoseMl > 0f -> {
                    channelConfiguredDailyDoseMl
                }

                activeMode == DosingScheduleMode.CUSTOM_PERIODS &&
                    customPeriodsConfiguredDailyDoseMl > 0f -> {
                    customPeriodsConfiguredDailyDoseMl
                }

                else -> {
                    timer.configuredDailyDoseMl
                }
            }

    val activeDailyDoseMl: Float
        get() =
            when {
                channelTimers.isNotEmpty() -> {
                    channelActiveDailyDoseMl
                }

                scheduleEnabled -> {
                    configuredDailyDoseMl
                }

                else -> {
                    0f
                }
            }
}

data class DosingCustomPeriodSaveItem(
    val startTime: String,
    val endTime: String,
    val doseCount: Int
)

data class DosingTimerDoseSaveItem(
    val startTime: String,
    val doseMl: Float
)

data class DosingTimerSavePayload(
    val enabled: Boolean,
    val name: String,
    val gpioPwm: String,
    val dosePerRunMl: Float,
    val weekDays: List<Boolean>,
    val timeStart: String,
    val intervalOn: String,
    val intervalOff: String,
    val count: Int
)