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
        val payload =
            DosingEspJsonMapper.createSingleSchedulePayload(
                channelIndex = channelIndex,
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
        val payload =
            DosingEspJsonMapper.createHourly24SchedulePayload(
                channelIndex = channelIndex,
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
        val payload =
            DosingEspJsonMapper.createGenericTimerSchedulePayload(
                channelIndex = channelIndex,
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

    private suspend fun saveTimerToDevice(
        deviceIp: String
    ) {
        api.postJson(
            deviceIp = deviceIp,
            payload = DosingEspJsonMapper.createSaveTimerPayload()
        )
    }
}