package com.aqua.aqualight.data.devices.dosing.esp

class DosingEspRepository(
    private val api: DosingEspApi = DosingEspApi()
) {

    suspend fun fetchDosingState(
        deviceIp: String,
        channelIndex: Int
    ): DosingEspState {
        val payload =
        DosingEspJsonMapper.createReadDosingStatePayload(
            channelIndex = channelIndex
        )

        val response =
        api.getJson(
            deviceIp = deviceIp,
            payload = payload
        )

        return DosingEspJsonMapper.parseDosingState(
            response = response,
            channelIndex = channelIndex
        )
    }

    suspend fun readChannelRestMl(
        deviceIp: String,
        channelIndex: Int
    ): Float? {
        val payload =
        DosingEspJsonMapper.createReadChannelRestPayload(
            channelIndex = channelIndex
        )

        val response =
        api.getJson(
            deviceIp = deviceIp,
            payload = payload
        )

        return DosingEspJsonMapper.parseChannelRestMl(
            response = response,
            channelIndex = channelIndex
        )
    }

    suspend fun writeChannelRestMl(
        deviceIp: String,
        channelIndex: Int,
        restMl: Float
    ) {
        val payload =
        DosingEspJsonMapper.createWriteChannelRestPayload(
            channelIndex = channelIndex,
            restMl = restMl.coerceAtLeast(
                minimumValue = 0f
            )
        )

        api.postJson(
            deviceIp = deviceIp,
            payload = payload
        )
    }

    suspend fun refillChannelReservoir(
        deviceIp: String,
        channelIndex: Int,
        capacityMl: Float
    ) {
        require(
            value = capacityMl > 0f
        ) {
            "Reservoir capacity must be greater than 0 ml."
        }

        writeChannelRestMl(
            deviceIp = deviceIp,
            channelIndex = channelIndex,
            restMl = capacityMl
        )
    }

    suspend fun saveSingleSchedule(
        deviceIp: String,
        channelIndex: Int,
        channelNumber: Int,
        gpioPwm: String,
        totalDailyDoseMl: Float,
        weekDays: List<Boolean>,
        startTime: String,
        enabled: Boolean
    ) {
        val currentState =
        fetchDosingState(
            deviceIp = deviceIp,
            channelIndex = channelIndex
        )

        val payload =
        DosingEspJsonMapper.createMergedSingleSchedulePayload(
            existingTimers = currentState.timers,
            channelNumber = channelNumber,
            gpioPwm = gpioPwm,
            totalDailyDoseMl = totalDailyDoseMl,
            weekDays = weekDays,
            startTime = startTime,
            enabled = enabled
        )

        api.postJson(
            deviceIp = deviceIp,
            payload = payload
        )

        saveTimerToDevice(
            deviceIp = deviceIp
        )
    }

    suspend fun saveHourly24Schedule(
        deviceIp: String,
        channelIndex: Int,
        channelNumber: Int,
        gpioPwm: String,
        totalDailyDoseMl: Float,
        weekDays: List<Boolean>,
        startTime: String,
        enabled: Boolean
    ) {
        val currentState =
        fetchDosingState(
            deviceIp = deviceIp,
            channelIndex = channelIndex
        )

        val payload =
        DosingEspJsonMapper.createMergedHourly24SchedulePayload(
            existingTimers = currentState.timers,
            channelNumber = channelNumber,
            gpioPwm = gpioPwm,
            totalDailyDoseMl = totalDailyDoseMl,
            weekDays = weekDays,
            startTime = startTime,
            enabled = enabled
        )

        api.postJson(
            deviceIp = deviceIp,
            payload = payload
        )

        saveTimerToDevice(
            deviceIp = deviceIp
        )
    }

    suspend fun saveCustomPeriodsSchedule(
        deviceIp: String,
        channelIndex: Int,
        channelNumber: Int,
        gpioPwm: String,
        totalDailyDoseMl: Float,
        weekDays: List<Boolean>,
        periods: List<DosingCustomPeriodSaveItem>,
        enabled: Boolean
    ) {
        val currentState =
        fetchDosingState(
            deviceIp = deviceIp,
            channelIndex = channelIndex
        )

        val payload =
        DosingEspJsonMapper.createMergedCustomPeriodsSchedulePayload(
            existingTimers = currentState.timers,
            channelNumber = channelNumber,
            gpioPwm = gpioPwm,
            totalDailyDoseMl = totalDailyDoseMl,
            weekDays = weekDays,
            periods = periods,
            enabled = enabled
        )

        api.postJson(
            deviceIp = deviceIp,
            payload = payload
        )

        saveTimerToDevice(
            deviceIp = deviceIp
        )
    }

    suspend fun saveTimerModeSchedule(
        deviceIp: String,
        channelIndex: Int,
        channelNumber: Int,
        gpioPwm: String,
        weekDays: List<Boolean>,
        doses: List<DosingTimerDoseSaveItem>,
        enabled: Boolean
    ) {
        val currentState =
        fetchDosingState(
            deviceIp = deviceIp,
            channelIndex = channelIndex
        )

        val payload =
        DosingEspJsonMapper.createMergedTimerModeSchedulePayload(
            existingTimers = currentState.timers,
            channelNumber = channelNumber,
            gpioPwm = gpioPwm,
            weekDays = weekDays,
            doses = doses,
            enabled = enabled
        )

        api.postJson(
            deviceIp = deviceIp,
            payload = payload
        )

        saveTimerToDevice(
            deviceIp = deviceIp
        )
    }

    suspend fun saveGenericTimerSchedule(
        deviceIp: String,
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
    ) {
        val currentState =
        fetchDosingState(
            deviceIp = deviceIp,
            channelIndex = channelIndex
        )

        val payload =
        DosingEspJsonMapper.createMergedGenericTimerSchedulePayload(
            existingTimers = currentState.timers,
            channelNumber = channelNumber,
            mode = mode,
            gpioPwm = gpioPwm,
            dosePerRunMl = dosePerRunMl,
            weekDays = weekDays,
            timeStart = timeStart,
            intervalOn = intervalOn,
            intervalOff = intervalOff,
            count = count,
            enabled = enabled
        )

        api.postJson(
            deviceIp = deviceIp,
            payload = payload
        )

        saveTimerToDevice(
            deviceIp = deviceIp
        )
    }

    suspend fun updateTimerEnabledAndWeekDays(
        deviceIp: String,
        channelIndex: Int,
        enabled: Boolean,
        weekDays: List<Boolean>
    ) {
        val currentState =
        fetchDosingState(
            deviceIp = deviceIp,
            channelIndex = channelIndex
        )

        val targetTimerIndices =
        findExistingScheduleTimerIndicesForChannel(
            state = currentState
        )

        if (targetTimerIndices.isEmpty()) {
            return
        }

        val payload =
        DosingEspJsonMapper.createTimerEnabledWeekDaysPayload(
            timerIndices = targetTimerIndices,
            enabled = enabled,
            weekDays = weekDays
        )

        api.postJson(
            deviceIp = deviceIp,
            payload = payload
        )

        saveTimerToDevice(
            deviceIp = deviceIp
        )
    }

    suspend fun updateCustomPeriodsEnabledAndWeekDays(
        deviceIp: String,
        channelIndex: Int,
        activePeriodCount: Int,
        enabled: Boolean,
        weekDays: List<Boolean>
    ) {
        updateTimerEnabledAndWeekDays(
            deviceIp = deviceIp,
            channelIndex = channelIndex,
            enabled = enabled,
            weekDays = weekDays
        )
    }

    suspend fun sendManualDose(
        deviceIp: String,
        channelIndex: Int,
        doseMl: Float,
        calibrationMsPerMl: Long
    ) {
        require(
            value = doseMl > 0f
        ) {
            "Manual dose amount must be greater than 0 ml."
        }

        require(
            value = calibrationMsPerMl > 0L
        ) {
            "Pump calibration is required before manual dosing."
        }

        val payload =
        DosingEspJsonMapper.createManualDosePayload(
            channelIndex = channelIndex,
            doseMl = doseMl,
            calibrationMsPerMl = calibrationMsPerMl
        )

        api.postJson(
            deviceIp = deviceIp,
            payload = payload
        )
    }

    suspend fun updateChannelRest(
        deviceIp: String,
        channelIndex: Int,
        restMl: Float
    ) {
        val payload =
        DosingEspJsonMapper.createChannelRestPayload(
            channelIndex = channelIndex,
            restMl = restMl
        )

        api.postJson(
            deviceIp = deviceIp,
            payload = payload
        )
    }

    suspend fun refillChannelReservoir(
        deviceIp: String,
        channelIndex: Int,
        capacityMl: Float
    ): DosingEspState {
        val safeCapacityMl =
        capacityMl.coerceAtLeast(
            minimumValue = 0f
        )

        updateChannelRest(
            deviceIp = deviceIp,
            channelIndex = channelIndex,
            restMl = safeCapacityMl
        )

        return fetchDosingState(
            deviceIp = deviceIp,
            channelIndex = channelIndex
        )
    }

    private fun findExistingScheduleTimerIndicesForChannel(
        state: DosingEspState
    ): List<Int> {
        val channelGpioPwm =
        state.channel.gpioPwm.trim()

        if (
            channelGpioPwm.isBlank() ||
            channelGpioPwm == "-"
        ) {
            return emptyList()
        }

        return state.timers
        .filter {
            timer ->
            timer.belongsToGpioPwm(
                targetGpioPwm = channelGpioPwm
            ) &&
            timer.name.isNotBlank() &&
            timer.name != "-" &&
            timer.dosePerRunMl > 0f &&
            timer.count > 0 &&
            timer.index >= 0
        }
        .map {
            timer ->
            timer.index
        }
        .distinct()
        .sorted()
    }

    private suspend fun saveTimerToDevice(
        deviceIp: String
    ) {
        api.postJson(
            deviceIp = deviceIp,
            payload = DosingEspJsonMapper.createSaveTimerPayload()
        )
    }
}