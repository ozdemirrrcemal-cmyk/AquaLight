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
            gpioPwm.isNotBlank() &&
                gpioPwm != "-"

    val isAquaLightTimer: Boolean
        get() =
            name.startsWith(
                prefix = "AQL_",
                ignoreCase = true
            )

    fun belongsToGpioPwm(
        targetGpioPwm: String
    ): Boolean {
        val cleanTarget =
            targetGpioPwm.trim()

        val cleanTimerGpio =
            gpioPwm.trim()

        return cleanTarget.isNotBlank() &&
            cleanTarget != "-" &&
            cleanTimerGpio == cleanTarget
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
                channelGpioPwm.isBlank() ||
                channelGpioPwm == "-"
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