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

    private const val APP_TIMER_BASE_INDEX = 0
    private const val APP_TIMER_SLOTS_PER_CHANNEL = 4
    private const val APP_TIMER_CHANNEL_COUNT = 4

    fun createReadDosingStatePayload(
        channelIndex: Int
    ): JSONObject {
        return JSONObject().apply {
            put(
                KEY_L_PWM_CHANNEL_TIMER,
                JSONObject().apply {
                    put(
                        "All",
                        0
                    )
                }
            )

            put(
                KEY_L_TIMER,
                JSONObject().apply {
                    put(
                        "All",
                        0
                    )
                }
            )
        }
    }

    fun createReadDosingRuntimePayload(): JSONObject {
        return JSONObject().apply {
            put(
                KEY_L_PWM_CHANNEL_TIMER,
                JSONObject().apply {
                    put(
                        "All",
                        0
                    )
                }
            )
        }
    }

    fun parseDosingRuntimeChannels(
        response: JSONObject
    ): List<DosingEspChannelState> {
        val channelDataJson =
        response.optJSONObject(
            KEY_L_PWM_CHANNEL_TIMER
        )?.optJSONObject(
            KEY_DATA
        ) ?: JSONObject()

        return List(
            size = APP_TIMER_CHANNEL_COUNT
        ) {
            channelIndex ->
            val channelJson =
            channelDataJson.optJSONObject(
                channelIndex.toString()
            ) ?: JSONObject()

            parseChannelState(
                json = channelJson
            )
        }
    }

    fun createReadChannelRestPayload(
        channelIndex: Int
    ): JSONObject {
        val safeChannelIndex =
        channelIndex.coerceIn(
            minimumValue = 0,
            maximumValue = APP_TIMER_CHANNEL_COUNT - 1
        )

        return JSONObject().apply {
            put(
                KEY_L_PWM_CHANNEL_TIMER,
                JSONObject().apply {
                    put(
                        KEY_DATA,
                        JSONObject().apply {
                            put(
                                safeChannelIndex.toString(),
                                JSONObject().apply {
                                    put(
                                        "Rest",
                                        0
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
    }

    fun parseChannelRestMl(
        response: JSONObject,
        channelIndex: Int
    ): Float? {
        val safeChannelIndex =
        channelIndex.coerceIn(
            minimumValue = 0,
            maximumValue = APP_TIMER_CHANNEL_COUNT - 1
        ).toString()

        return response
        .optJSONObject(KEY_L_PWM_CHANNEL_TIMER)
        ?.optJSONObject(KEY_DATA)
        ?.optJSONObject(safeChannelIndex)
        ?.optNullableFloat(
            key = "Rest"
        )
    }

    fun createWriteChannelRestPayload(
        channelIndex: Int,
        restMl: Float
    ): JSONObject {
        val safeChannelIndex =
        channelIndex.coerceIn(
            minimumValue = 0,
            maximumValue = APP_TIMER_CHANNEL_COUNT - 1
        )

        val safeRestMl =
        restMl.coerceAtLeast(
            minimumValue = 0f
        )

        return JSONObject().apply {
            put(
                KEY_L_PWM_CHANNEL_TIMER,
                JSONObject().apply {
                    put(
                        KEY_DATA,
                        JSONObject().apply {
                            put(
                                safeChannelIndex.toString(),
                                JSONObject().apply {
                                    put(
                                        "Rest",
                                        formatFloatForEsp(
                                            value = safeRestMl
                                        )
                                    )
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
        val channelIndexKey =
        channelIndex.coerceIn(
            minimumValue = 0,
            maximumValue = APP_TIMER_CHANNEL_COUNT - 1
        ).toString()

        val timerDataJson =
        response
        .optJSONObject(KEY_L_TIMER)
        ?.optJSONObject(KEY_DATA)
        ?: JSONObject()

        val channelJson =
        response
        .optJSONObject(KEY_L_PWM_CHANNEL_TIMER)
        ?.optJSONObject(KEY_DATA)
        ?.optJSONObject(channelIndexKey)
        ?: JSONObject()

        val channelState =
        parseChannelState(
            json = channelJson
        )

        val allTimers =
        parseAllTimerStates(
            timerDataJson = timerDataJson
        )

        val channelTimers =
        allTimers
        .filter {
            timer ->
            timer.belongsToGpioPwm(
                targetGpioPwm = channelState.gpioPwm
            )
        }
        .sortedBy {
            timer ->
            timer.index
        }

        val primaryTimer =
        selectPrimaryTimer(
            channelIndex = channelIndex,
            channelState = channelState,
            timerDataJson = timerDataJson,
            channelTimers = channelTimers
        )

        val customPeriodTimers =
        channelTimers.filter {
            timer ->
            timer.name.contains(
                other = "CUSTOM_PERIODS",
                ignoreCase = true
            ) ||
            timer.name.contains(
                other = "CUSTOM_TIME",
                ignoreCase = true
            )
        }

        val detectedMode =
        detectActiveMode(
            channelTimers = channelTimers,
            fallbackTimerName = primaryTimer.name
        )

        return DosingEspState(
            channel = channelState,
            timer = primaryTimer,
            activeMode = detectedMode,
            customPeriodTimers = customPeriodTimers,
            timers = if (allTimers.isNotEmpty()) {
                allTimers
            } else {
                listOf(
                    primaryTimer
                )
            }
        )
    }

    fun createMergedSingleSchedulePayload(
        existingTimers: List<DosingEspTimerState>,
        channelNumber: Int,
        gpioPwm: String,
        totalDailyDoseMl: Float,
        weekDays: List<Boolean>,
        startTime: String,
        enabled: Boolean
    ): JSONObject {
        val newTimers =
        listOf(
            DosingTimerSavePayload(
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

        return createMergedTimerListPayload(
            existingTimers = existingTimers,
            targetGpioPwm = gpioPwm,
            newTimers = newTimers
        )
    }

    fun createMergedHourly24SchedulePayload(
        existingTimers: List<DosingEspTimerState>,
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
        ) / count.toFloat()

        val newTimers =
        listOf(
            DosingTimerSavePayload(
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

        return createMergedTimerListPayload(
            existingTimers = existingTimers,
            targetGpioPwm = gpioPwm,
            newTimers = newTimers
        )
    }

    fun createMergedGenericTimerSchedulePayload(
        existingTimers: List<DosingEspTimerState>,
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
        val newTimers =
        listOf(
            DosingTimerSavePayload(
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

        return createMergedTimerListPayload(
            existingTimers = existingTimers,
            targetGpioPwm = gpioPwm,
            newTimers = newTimers
        )
    }

    fun createMergedCustomPeriodsSchedulePayload(
        existingTimers: List<DosingEspTimerState>,
        channelNumber: Int,
        gpioPwm: String,
        totalDailyDoseMl: Float,
        weekDays: List<Boolean>,
        periods: List<DosingCustomPeriodSaveItem>,
        enabled: Boolean
    ): JSONObject {
        val safePeriods =
        periods.take(
            n = APP_TIMER_SLOTS_PER_CHANNEL
        )

        val totalDoseCount =
        safePeriods.sumOf {
            period ->
            period.doseCount.coerceAtLeast(
                minimumValue = 0
            )
        }.coerceAtLeast(
            minimumValue = 1
        )

        val perDoseMl =
        totalDailyDoseMl.coerceAtLeast(
            minimumValue = 0f
        ) / totalDoseCount.toFloat()

        val newTimers =
        safePeriods.mapIndexed {
            periodIndex, period ->
            DosingTimerSavePayload(
                enabled = enabled,
                name = "AQL_CH${channelNumber}_CUSTOM_PERIODS_${periodIndex + 1}",
                gpioPwm = gpioPwm,
                dosePerRunMl = perDoseMl,
                weekDays = weekDays,
                timeStart = normalizeTime(
                    value = period.startTime
                ),
                intervalOn = "00:00",
                intervalOff = calculatePeriodIntervalOff(
                    startTime = period.startTime,
                    endTime = period.endTime,
                    doseCount = period.doseCount
                ),
                count = period.doseCount.coerceAtLeast(
                    minimumValue = 1
                )
            )
        }

        return createMergedTimerListPayload(
            existingTimers = existingTimers,
            targetGpioPwm = gpioPwm,
            newTimers = newTimers
        )
    }

    fun createMergedTimerModeSchedulePayload(
        existingTimers: List<DosingEspTimerState>,
        channelNumber: Int,
        gpioPwm: String,
        weekDays: List<Boolean>,
        doses: List<DosingTimerDoseSaveItem>,
        enabled: Boolean
    ): JSONObject {
        val safeDoses =
        doses.take(
            n = APP_TIMER_SLOTS_PER_CHANNEL
        )

        val newTimers =
        safeDoses.mapIndexed {
            doseIndex, dose ->
            DosingTimerSavePayload(
                enabled = enabled,
                name = "AQL_CH${channelNumber}_TIMER_${doseIndex + 1}",
                gpioPwm = gpioPwm,
                dosePerRunMl = dose.doseMl.coerceAtLeast(
                    minimumValue = 0f
                ),
                weekDays = weekDays,
                timeStart = normalizeTime(
                    value = dose.startTime
                ),
                intervalOn = "00:00",
                intervalOff = "00:00",
                count = 1
            )
        }

        return createMergedTimerListPayload(
            existingTimers = existingTimers,
            targetGpioPwm = gpioPwm,
            newTimers = newTimers
        )
    }

    fun createTimerEnabledWeekDaysPayload(
        channelIndex: Int,
        enabled: Boolean,
        weekDays: List<Boolean>
    ): JSONObject {
        return createTimerEnabledWeekDaysPayload(
            timerIndices = getAppTimerSlotIndicesForChannel(
                channelIndex = channelIndex
            ),
            enabled = enabled,
            weekDays = weekDays
        )
    }

    fun createTimerEnabledWeekDaysPayload(
        timerIndices: List<Int>,
        enabled: Boolean,
        weekDays: List<Boolean>
    ): JSONObject {
        val timerData =
        JSONObject()

        timerIndices
        .distinct()
        .sorted()
        .forEach {
            timerIndex ->
            timerData.put(
                timerIndex.coerceAtLeast(
                    minimumValue = 0
                ).toString(),
                JSONObject().apply {
                    put(
                        "Enabled",
                        enabled
                    )

                    put(
                        "WDay",
                        createWeekDayArray(
                            weekDays = weekDays
                        )
                    )
                }
            )
        }

        return JSONObject().apply {
            put(
                KEY_L_TIMER,
                JSONObject().apply {
                    put(
                        KEY_DATA,
                        timerData
                    )
                }
            )
        }
    }

    fun createManualDosePayload(
        channelIndex: Int,
        doseMl: Float,
        calibrationMsPerMl: Long
    ): JSONObject {
        val safeDurationMs =
        max(
            0L,
            (
                doseMl.coerceAtLeast(
                    minimumValue = 0f
                ) * calibrationMsPerMl
            ).roundToLong()
        )

        return JSONObject().apply {
            put(
                KEY_L_PWM_CHANNEL_TIMER,
                JSONObject().apply {
                    put(
                        KEY_DATA,
                        JSONObject().apply {
                            put(
                                channelIndex.coerceIn(
                                    minimumValue = 0,
                                    maximumValue = APP_TIMER_CHANNEL_COUNT - 1
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

    fun createResetDosingChannelPayload(
        channelIndex: Int,
        channelNumber: Int,
        existingTimers: List<DosingEspTimerState>,
        gpioPwm: String
    ): JSONObject {
        val safeChannelIndex =
        channelIndex.coerceIn(
            minimumValue = 0,
            maximumValue = APP_TIMER_CHANNEL_COUNT - 1
        )

        val safeChannelNumber =
        channelNumber.coerceIn(
            minimumValue = 1,
            maximumValue = APP_TIMER_CHANNEL_COUNT
        )

        val cleanGpioPwm =
        gpioPwm.trim().takeIf {
            value ->
            value.isNotBlank() && value != "-"
        } ?: "PWM$safeChannelNumber"

        val targetTimerIndices =
        existingTimers
        .filter {
            timer ->
            timer.belongsToGpioPwm(
                targetGpioPwm = cleanGpioPwm
            ) &&
            timer.index >= 0
        }
        .map {
            timer ->
            timer.index
        }
        .distinct()
        .sorted()
        .ifEmpty {
            getAppTimerSlotIndicesForChannel(
                channelIndex = safeChannelIndex
            )
        }

        val timerData =
        JSONObject()

        targetTimerIndices.forEach {
            timerIndex ->
            timerData.put(
                timerIndex.toString(),
                JSONObject().apply {
                    put(
                        "Enabled",
                        false
                    )

                    put(
                        "Regime",
                        "Off"
                    )

                    put(
                        "Name",
                        "-"
                    )

                    put(
                        "GPIO_PWM",
                        "-"
                    )

                    put(
                        "YE",
                        0
                    )

                    put(
                        "WDay",
                        createWeekDayArray(
                            weekDays = List(size = 7) {
                                true
                            }
                        )
                    )

                    put(
                        "TimeStart",
                        "00:00"
                    )

                    put(
                        "IntervalOn",
                        "00:00"
                    )

                    put(
                        "IntervalOff",
                        "00:00"
                    )

                    put(
                        "Count",
                        0
                    )
                }
            )
        }

        return JSONObject().apply {
            put(
                KEY_L_PWM_CHANNEL_TIMER,
                JSONObject().apply {
                    put(
                        KEY_DATA,
                        JSONObject().apply {
                            put(
                                safeChannelIndex.toString(),
                                JSONObject().apply {
                                    put(
                                        "Name",
                                        "Channel $safeChannelNumber"
                                    )

                                    put(
                                        "GPIO_PWM",
                                        cleanGpioPwm
                                    )

                                    put(
                                        "YE",
                                        0
                                    )

                                    put(
                                        "Dimension",
                                        "ml"
                                    )

                                    put(
                                        "Rest",
                                        0
                                    )

                                    put(
                                        "VManual",
                                        JSONObject().apply {
                                            put(
                                                "V",
                                                -1
                                            )

                                            put(
                                                "TOffMs",
                                                0
                                            )
                                        }
                                    )
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
                        timerData
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
            DosingScheduleMode.SINGLE -> "SINGLE"
            DosingScheduleMode.HOURLY_24 -> "HOURLY_24"
            DosingScheduleMode.CUSTOM_PERIODS -> "CUSTOM_PERIODS"
            DosingScheduleMode.TIMER -> "TIMER"
        }

        return "AQL_CH${channelNumber}_$suffix"
    }

    fun getAppTimerSlotIndicesForChannel(
        channelIndex: Int
    ): List<Int> {
        val safeChannelIndex =
        channelIndex.coerceIn(
            minimumValue = 0,
            maximumValue = APP_TIMER_CHANNEL_COUNT - 1
        )

        val startIndex =
        APP_TIMER_BASE_INDEX + safeChannelIndex * APP_TIMER_SLOTS_PER_CHANNEL

        return List(
            size = APP_TIMER_SLOTS_PER_CHANNEL
        ) {
            offset ->
            startIndex + offset
        }
    }

    fun getPrimaryAppTimerSlotIndex(
        channelIndex: Int
    ): Int {
        return getAppTimerSlotIndicesForChannel(
            channelIndex = channelIndex
        ).first()
    }

    private fun createMergedTimerListPayload(
        existingTimers: List<DosingEspTimerState>,
        targetGpioPwm: String,
        newTimers: List<DosingTimerSavePayload>
    ): JSONObject {
        val cleanTargetGpioPwm =
        targetGpioPwm.trim()

        val preservedTimers =
        existingTimers
        .filter {
            timer ->
            shouldKeepExistingTimer(
                timer = timer
            )
        }
        .filterNot {
            timer ->
            timer.belongsToGpioPwm(
                targetGpioPwm = cleanTargetGpioPwm
            )
        }
        .sortedBy {
            timer ->
            timer.index
        }
        .map {
            timer ->
            timer.toSavePayload()
        }

        val mergedTimers =
        preservedTimers + newTimers

        return createFullTimerListPayload(
            timers = mergedTimers
        )
    }

    private fun createFullTimerListPayload(
        timers: List<DosingTimerSavePayload>
    ): JSONObject {
        val timerData =
        JSONObject()

        timers.forEachIndexed {
            index, timer ->
            timerData.put(
                index.toString(),
                createTimerObject(
                    timer = timer
                )
            )
        }

        return JSONObject().apply {
            put(
                KEY_L_TIMER,
                JSONObject().apply {
                    put(
                        "Count",
                        timers.size
                    )

                    put(
                        KEY_DATA,
                        timerData
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
                "GPIO_PWM",
                timer.gpioPwm
            )

            put(
                "YE",
                formatFloatForEsp(
                    value = timer.dosePerRunMl
                )
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

    private fun shouldKeepExistingTimer(
        timer: DosingEspTimerState
    ): Boolean {
        val hasName =
        timer.name.isNotBlank() &&
        timer.name != "-"

        val hasGpio =
        timer.hasValidGpioPwm

        val hasDose =
        timer.dosePerRunMl > 0f

        return hasName ||
        hasGpio ||
        hasDose ||
        timer.enabled
    }

    private fun DosingEspTimerState.toSavePayload(): DosingTimerSavePayload {
        return DosingTimerSavePayload(
            enabled = enabled,
            name = name,
            gpioPwm = gpioPwm,
            dosePerRunMl = dosePerRunMl,
            weekDays = if (weekDays.size == 7) {
                weekDays
            } else {
                List(
                    size = 7
                ) {
                    true
                }
            },
            timeStart = timeStart,
            intervalOn = intervalOn,
            intervalOff = intervalOff,
            count = count.coerceAtLeast(
                minimumValue = 1
            )
        )
    }

    private fun parseAllTimerStates(
        timerDataJson: JSONObject
    ): List<DosingEspTimerState> {
        val timers =
        mutableListOf<DosingEspTimerState>()

        val keys =
        timerDataJson.keys()

        while (keys.hasNext()) {
            val key =
            keys.next()

            val timerIndex =
            key.toIntOrNull() ?: continue

            val timerJson =
            timerDataJson.optJSONObject(
                key
            ) ?: continue

            timers.add(
                parseTimerState(
                    index = timerIndex,
                    json = timerJson,
                    fallbackGpioPwm = "-"
                )
            )
        }

        return timers.sortedBy {
            timer ->
            timer.index
        }
    }

    private fun selectPrimaryTimer(
        channelIndex: Int,
        channelState: DosingEspChannelState,
        timerDataJson: JSONObject,
        channelTimers: List<DosingEspTimerState>
    ): DosingEspTimerState {
        channelTimers.firstOrNull {
            timer ->
            timer.isAquaLightTimer &&
            timer.enabled
        }?.let {
            timer ->
            return timer
        }

        channelTimers.firstOrNull {
            timer ->
            timer.enabled
        }?.let {
            timer ->
            return timer
        }

        channelTimers.firstOrNull {
            timer ->
            timer.isAquaLightTimer
        }?.let {
            timer ->
            return timer
        }

        channelTimers.firstOrNull()?.let {
            timer ->
            return timer
        }

        val primarySlotIndex =
        getPrimaryAppTimerSlotIndex(
            channelIndex = channelIndex
        )

        val legacyTimerJson =
        timerDataJson.optJSONObject(
            primarySlotIndex.toString()
        )

        if (legacyTimerJson != null) {
            return parseTimerState(
                index = primarySlotIndex,
                json = legacyTimerJson,
                fallbackGpioPwm = channelState.gpioPwm
            )
        }

        return createEmptyTimerState(
            index = primarySlotIndex,
            fallbackGpioPwm = channelState.gpioPwm
        )
    }

    private fun createEmptyTimerState(
        index: Int,
        fallbackGpioPwm: String
    ): DosingEspTimerState {
        return DosingEspTimerState(
            index = index,
            enabled = false,
            name = "",
            gpioPwm = fallbackGpioPwm,
            dosePerRunMl = 0f,
            weekDays = List(
                size = 7
            ) {
                true
            },
            timeStart = "00:00",
            intervalOn = "00:00",
            intervalOff = "00:00",
            count = 0,
            status = null
        )
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
        index: Int,
        json: JSONObject,
        fallbackGpioPwm: String
    ): DosingEspTimerState {
        return DosingEspTimerState(
            index = index,
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
            intervalOn = normalizeTime(
                value = json.optString(
                    "IntervalOn",
                    "00:00"
                )
            ),
            intervalOff = normalizeTime(
                value = json.optString(
                    "IntervalOff",
                    "00:00"
                )
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
        channelTimers: List<DosingEspTimerState>,
        fallbackTimerName: String
    ): DosingScheduleMode {
        val names =
        if (channelTimers.isEmpty()) {
            listOf(
                fallbackTimerName
            )
        } else {
            channelTimers.map {
                timer ->
                timer.name
            }
        }

        return when {
            names.any {
                name ->
                name.contains(
                    other = "CUSTOM_PERIODS",
                    ignoreCase = true
                ) ||
                name.contains(
                    other = "CUSTOM_TIME",
                    ignoreCase = true
                )
            } -> DosingScheduleMode.CUSTOM_PERIODS

            names.any {
                name ->
                name.contains(
                    other = "HOURLY_24",
                    ignoreCase = true
                )
            } -> DosingScheduleMode.HOURLY_24

            names.any {
                name ->
                name.contains(
                    other = "TIMER",
                    ignoreCase = true
                )
            } -> DosingScheduleMode.TIMER

            else -> DosingScheduleMode.SINGLE
        }
    }

    private fun calculatePeriodIntervalOff(
        startTime: String,
        endTime: String,
        doseCount: Int
    ): String {
        if (doseCount <= 1) {
            return "00:00"
        }

        val startMinutes =
        timeToMinutes(
            value = startTime
        )

        val endMinutes =
        timeToMinutes(
            value = endTime
        )

        val durationMinutes =
        (endMinutes - startMinutes).coerceAtLeast(
            minimumValue = 0
        )

        val intervalMinutes =
        durationMinutes / (doseCount - 1).coerceAtLeast(
            minimumValue = 1
        )

        return formatMinutesToEspTime(
            minutes = intervalMinutes
        )
    }

    private fun timeToMinutes(
        value: String
    ): Int {
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

        return hour * 60 + minute
    }

    private fun formatMinutesToEspTime(
        minutes: Int
    ): String {
        val safeMinutes =
        minutes.coerceAtLeast(
            minimumValue = 0
        )

        val hour =
        safeMinutes / 60

        val minute =
        safeMinutes % 60

        return String.format(
            Locale.US,
            "%02d:%02d",
            hour,
            minute
        )
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
            safeWeekDays.forEach {
                selected ->
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
        ) {
            index ->
            val value =
            jsonArray.opt(
                index
            )

            when (value) {
                is Boolean -> value

                is Number -> value.toInt() != 0

                is String -> {
                    value == "1" ||
                    value.equals(
                        other = "true",
                        ignoreCase = true
                    )
                } else -> false
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
            is Boolean -> value

            is Number -> value.toInt() != 0

            is String -> {
                value == "1" ||
                value.equals(
                    other = "true",
                    ignoreCase = true
                )
            } else -> defaultValue
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
            is Number -> value.toFloat()

            is String -> {
                value.replace(
                    oldValue = ",",
                    newValue = "."
                ).toFloatOrNull() ?: defaultValue
            } else -> defaultValue
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
            is Number -> value.toLong()

            is String -> value.toLongOrNull() ?: defaultValue

            else -> defaultValue
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
            is Number -> value.toInt()

            is String -> value.toIntOrNull() ?: defaultValue

            else -> defaultValue
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