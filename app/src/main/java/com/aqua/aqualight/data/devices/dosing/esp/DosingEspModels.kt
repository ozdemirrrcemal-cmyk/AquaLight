package com.aqua.aqualight.data.devices.dosing.esp

enum class DosingScheduleMode {
    SINGLE,
    HOURLY_24,
    CUSTOM_PERIODS,
    TIMER
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
}

data class DosingEspState(
    val channel: DosingEspChannelState,
    val timer: DosingEspTimerState,
    val activeMode: DosingScheduleMode,
    val customPeriodTimers: List<DosingEspTimerState> = emptyList()
) {
    val customPeriodsConfiguredDailyDoseMl: Float
        get() =
            customPeriodTimers.sumOf { timer ->
                timer.configuredDailyDoseMl.toDouble()
            }.toFloat()

    val customPeriodsEnabled: Boolean
        get() =
            customPeriodTimers.any { timer ->
                timer.enabled
            }

    val scheduleEnabled: Boolean
        get() =
            when (activeMode) {
                DosingScheduleMode.CUSTOM_PERIODS -> {
                    customPeriodsEnabled
                }

                else -> {
                    timer.enabled
                }
            }

    val configuredDailyDoseMl: Float
        get() =
            when (activeMode) {
                DosingScheduleMode.CUSTOM_PERIODS -> {
                    if (timer.configuredDailyDoseMl > 0f) {
                        timer.configuredDailyDoseMl
                    } else {
                        customPeriodsConfiguredDailyDoseMl
                    }
                }

                else -> {
                    timer.configuredDailyDoseMl
                }
            }

    val activeDailyDoseMl: Float
        get() =
            if (scheduleEnabled) {
                configuredDailyDoseMl
            } else {
                0f
            }
}

data class DosingCustomPeriodSaveItem(
    val startTime: String,
    val endTime: String,
    val doseCount: Int
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