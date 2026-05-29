package com.aqua.aqualight.data.devices.dosing.esp

import kotlin.math.abs

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

    suspend fun fetchDosingScreenStates(
        deviceIp: String
    ): List<DosingEspState> {
        val payload =
        DosingEspJsonMapper.createReadDosingStatePayload(
            channelIndex = 0
        )

        val response =
        api.getJson(
            deviceIp = deviceIp,
            payload = payload
        )

        return List(
            size = DOSING_CHANNEL_COUNT
        ) {
            channelIndex ->
            DosingEspJsonMapper.parseDosingState(
                response = response,
                channelIndex = channelIndex
            )
        }
    }

    suspend fun fetchDosingRuntimeChannels(
        deviceIp: String
    ): List<DosingEspChannelState> {
        val payload =
        DosingEspJsonMapper.createReadDosingRuntimePayload()

        val response =
        api.getJson(
            deviceIp = deviceIp,
            payload = payload
        )

        return DosingEspJsonMapper.parseDosingRuntimeChannels(
            response = response
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
        val safeRestMl =
        restMl.coerceAtLeast(
            minimumValue = 0f
        )

        val payload =
        DosingEspJsonMapper.createWriteChannelRestPayload(
            channelIndex = channelIndex,
            restMl = safeRestMl
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

        val updatedState =
        fetchDosingState(
            deviceIp = deviceIp,
            channelIndex = channelIndex
        )

        val updatedRestMl =
        updatedState.channel.restMl

        val refillVerified =
        updatedRestMl != null &&
        abs(
            updatedRestMl - capacityMl
        ) <= REST_WRITE_TOLERANCE_ML

        if (!refillVerified) {
            throw IllegalStateException(
                "Reservoir refill could not be verified."
            )
        }

        return updatedState
    }

    suspend fun saveSingleSchedule(
        deviceIp: String,
        channelIndex: Int,
        channelNumber: Int,
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

        val fixedGpioPwm =
        DosingEspJsonMapper.fixedGpioPwmForChannelIndex(
            channelIndex = channelIndex
        )

        val payload =
        DosingEspJsonMapper.createMergedSingleSchedulePayload(
            existingTimers = currentState.timers,
            channelNumber = channelNumber,
            gpioPwm = fixedGpioPwm,
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

        val fixedGpioPwm =
        DosingEspJsonMapper.fixedGpioPwmForChannelIndex(
            channelIndex = channelIndex
        )

        val payload =
        DosingEspJsonMapper.createMergedHourly24SchedulePayload(
            existingTimers = currentState.timers,
            channelNumber = channelNumber,
            gpioPwm = fixedGpioPwm,
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

        val fixedGpioPwm =
        DosingEspJsonMapper.fixedGpioPwmForChannelIndex(
            channelIndex = channelIndex
        )

        val payload =
        DosingEspJsonMapper.createMergedCustomPeriodsSchedulePayload(
            existingTimers = currentState.timers,
            channelNumber = channelNumber,
            gpioPwm = fixedGpioPwm,
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

        val fixedGpioPwm =
        DosingEspJsonMapper.fixedGpioPwmForChannelIndex(
            channelIndex = channelIndex
        )

        val payload =
        DosingEspJsonMapper.createMergedTimerModeSchedulePayload(
            existingTimers = currentState.timers,
            channelNumber = channelNumber,
            gpioPwm = fixedGpioPwm,
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

        val fixedGpioPwm =
        DosingEspJsonMapper.fixedGpioPwmForChannelIndex(
            channelIndex = channelIndex
        )

        val payload =
        DosingEspJsonMapper.createMergedGenericTimerSchedulePayload(
            existingTimers = currentState.timers,
            channelNumber = channelNumber,
            mode = mode,
            gpioPwm = fixedGpioPwm,
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
            channelIndex = channelIndex,
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

    suspend fun resetDosingChannel(
        deviceIp: String,
        channelIndex: Int,
        channelNumber: Int
    ): DosingEspState {
        val currentState =
        fetchDosingState(
            deviceIp = deviceIp,
            channelIndex = channelIndex
        )

        val fixedGpioPwm =
        DosingEspJsonMapper.fixedGpioPwmForChannelIndex(
            channelIndex = channelIndex
        )

        val payload =
        DosingEspJsonMapper.createResetDosingChannelPayload(
            channelIndex = channelIndex,
            channelNumber = channelNumber,
            existingTimers = currentState.timers,
            gpioPwm = fixedGpioPwm
        )

        api.postJson(
            deviceIp = deviceIp,
            payload = payload
        )

        saveTimerToDevice(
            deviceIp = deviceIp
        )

        return fetchDosingState(
            deviceIp = deviceIp,
            channelIndex = channelIndex
        )
    }

    private fun findExistingScheduleTimerIndicesForChannel(
        channelIndex: Int,
        state: DosingEspState
    ): List<Int> {
        val fixedGpioPwm =
        DosingEspJsonMapper.fixedGpioPwmForChannelIndex(
            channelIndex = channelIndex
        )

        val fixedSlotIndices =
        DosingEspJsonMapper.getAppTimerSlotIndicesForChannel(
            channelIndex = channelIndex
        )

        return state.timers
        .filter {
            timer ->
            timer.index in fixedSlotIndices &&
            timer.belongsToGpioPwm(
                targetGpioPwm = fixedGpioPwm
            ) &&
            timer.name.isNotBlank() &&
            timer.name != "-" &&
            timer.dosePerRunMl > 0f &&
            timer.count > 0
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

    private companion object {
        private const val REST_WRITE_TOLERANCE_ML = 0.5f
        private const val DOSING_CHANNEL_COUNT = 4
    }
}