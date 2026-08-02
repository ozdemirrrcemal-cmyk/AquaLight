package com.aqua.aqualight.data.devices.runtime.modules.dosing

import kotlin.math.roundToLong

/** Cross-validates every Dosing request, response and authenticated metadata capability. */
@Suppress("TooManyFunctions")
internal object DeviceDosingCommandValidation {
    fun validateStatus(
        status: DeviceDosingStatus,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsApi) { "Dosing API is not available." }
        require(status.supported)
        require(status.channelCount == access.channelCount) {
            "Dosing status channel count differs from authenticated product metadata."
        }
        require(status.runtime.supportsConfigApply)
        require(status.runtime.supportsChannels)
        require(status.runtime.supportsSchedules == access.supportsSchedules)
        require(status.runtime.supportsPrime == access.supportsPrime)
        require(status.runtime.supportsManualDose == access.supportsManualDose)
        require(
            status.runtime.supportsCalibrationWorkflow == access.supportsCalibrationWorkflow
        )
        require(status.runtime.supportsReservoirRefill == access.supportsReservoirRefill)
        status.channels.forEach { channel ->
            require(channel.editable.displayName == access.supportsChannelDisplayName)
            require(channel.editable.dosingCalibration == access.supportsCalibrationWorkflow)
            require(channel.editable.reservoir == access.supportsReservoirRefill)
        }
    }

    fun validateConfigRequest(
        payload: DeviceDosingConfigApplyPayload,
        currentStatus: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsApi) { "Dosing API is not available." }
        if (payload.schedules != null) {
            require(access.supportsSchedules) { "Dosing schedules are not available." }
        }
        val status = requireStatus(currentStatus, "applying Dosing config")
        require(status.runtime.supportsConfigApply)
        validateChannelRequests(payload.channels, status, access)
        validateScheduleRequests(payload.schedules, status)
    }

    fun validateConfigResult(
        payload: DeviceDosingConfigApplyPayload,
        result: DeviceDosingConfigApplyResult,
        currentStatus: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        require(result.saveRequested == payload.save)
        require(result.saved == payload.save)
        require(result.appliedChannels == (payload.channels != null))
        require(result.appliedSchedules == (payload.schedules != null))
        validateReturnedChannels(payload.channels, result.config)
        validateReturnedSchedules(payload.schedules, result.config)
        validateConfigSnapshot(result.config, currentStatus, access)
    }

    fun validatePrimeRequest(
        channelKey: String,
        currentStatus: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsApi && access.supportsPrime) { "Dosing prime is not available." }
        validateKnownChannel(channelKey, currentStatus, "priming a Dosing pump")
    }

    fun validateDoseRequest(
        payload: DeviceDosingDoseNowPayload,
        currentStatus: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsApi && access.supportsManualDose) {
            "Manual Dosing is not available."
        }
        val channel = validateKnownChannel(
            payload.normalizedChannelKey,
            currentStatus,
            "running a manual dose"
        )
        if (!payload.usePendingCalibration) {
            require(channel.dosing.calibrated) { "Dosing pump must be calibrated." }
            validateDoseDuration(payload.amountMl, channel.dosing.doseMsPerMl)
        }
        if (channel.dosing.reservoirTrackingEnabled) {
            require(channel.dosing.reservoirRemainingMl + DOSING_VALUE_EPSILON >= payload.amountMl) {
                "The Dosing reservoir does not contain the requested amount."
            }
        }
    }

    fun validateCalibrationRequest(
        channelKey: String,
        currentStatus: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsApi && access.supportsCalibrationWorkflow) {
            "Dosing calibration is not available."
        }
        val channel = validateKnownChannel(
            channelKey,
            currentStatus,
            "running the Dosing calibration workflow"
        )
        require(channel.editable.dosingCalibration)
    }

    fun validateReservoirRequest(
        channelKey: String,
        currentStatus: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsApi && access.supportsReservoirRefill) {
            "Dosing reservoir refill is not available."
        }
        val channel = validateKnownChannel(
            channelKey,
            currentStatus,
            "refilling a Dosing reservoir"
        )
        require(channel.editable.reservoir)
        require(channel.dosing.reservoirTrackingEnabled)
        require(channel.dosing.reservoirCapacityMl > 0.0)
    }

    fun validatePumpResult(
        channelKey: String,
        result: DeviceDosingPumpCommandResult,
        currentStatus: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        require(result.channelKey == channelKey)
        validateChannelSnapshot(result.channel, currentStatus, access)
    }

    fun validateDoseResult(
        payload: DeviceDosingDoseNowPayload,
        result: DeviceDosingDoseNowResult,
        currentStatus: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        require(result.channelKey == payload.normalizedChannelKey)
        require(dosingValuesEquivalent(result.amountMl, payload.amountMl))
        require(result.usePendingCalibration == payload.usePendingCalibration)
        validateChannelSnapshot(result.channel, currentStatus, access)
    }

    fun validateCalibrationStartResult(
        payload: DeviceDosingCalibrationStartPayload,
        result: DeviceDosingCalibrationStartResult
    ) {
        require(result.channelKey == payload.normalizedChannelKey)
        require(result.durationMs == payload.durationMs)
    }

    fun validateCalibrationFinishResult(
        payload: DeviceDosingCalibrationFinishPayload,
        result: DeviceDosingCalibrationFinishResult,
        currentStatus: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        require(result.channelKey == payload.normalizedChannelKey)
        require(dosingValuesEquivalent(result.measuredMl, payload.measuredMl))
        validateChannelSnapshot(result.channel, currentStatus, access)
    }

    fun validateCalibrationChannelResult(
        channelKey: String,
        resultChannelKey: String,
        result: DeviceDosingChannelStatusSnapshot,
        currentStatus: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        require(resultChannelKey == channelKey)
        validateChannelSnapshot(result, currentStatus, access)
    }

    fun validateReservoirResult(
        channelKey: String,
        result: DeviceDosingReservoirRefillResult,
        currentStatus: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        require(result.channelKey == channelKey)
        validateChannelSnapshot(result.channel, currentStatus, access)
    }

    fun validateConfigSnapshot(
        config: DeviceDosingConfigSnapshot,
        currentStatus: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsApi)
        require(config.channels.size == access.channelCount) {
            "Dosing config channel count differs from authenticated product metadata."
        }
        if (!access.supportsSchedules) require(config.schedules.isEmpty())
        currentStatus?.let { status ->
            require(
                config.channels.map(DeviceDosingChannelConfigSnapshot::channelKey) ==
                    status.channels.map(DeviceDosingChannelStatus::key)
            ) { "Dosing config channel identity differs from current status." }
        }
    }

    fun validateChannelSnapshot(
        result: DeviceDosingChannelStatusSnapshot,
        currentStatus: DeviceDosingStatus?,
        access: DeviceDosingRuntimeAccess
    ) {
        require(access.supportsApi)
        require(result.listIndex in 0 until access.channelCount)
        require(result.channel.editable.displayName == access.supportsChannelDisplayName)
        require(
            result.channel.editable.dosingCalibration == access.supportsCalibrationWorkflow
        )
        require(result.channel.editable.reservoir == access.supportsReservoirRefill)
        currentStatus?.let { status ->
            val current = requireNotNull(status.channels.getOrNull(result.listIndex))
            require(current.sameDosingChannelIdentity(result.channel)) {
                "Dosing channel mutation identity differs from current status."
            }
        }
    }

    private fun validateChannelRequests(
        channels: List<DeviceDosingChannelConfig>?,
        status: DeviceDosingStatus,
        access: DeviceDosingRuntimeAccess
    ) {
        if (channels == null) return
        require(status.runtime.supportsChannels)
        val channelsByKey = status.channels.associateBy(DeviceDosingChannelStatus::key)
        channels.forEach { requested ->
            val channel = requireNotNull(channelsByKey[requested.normalizedChannelKey]) {
                "Unknown Dosing channel key: ${requested.normalizedChannelKey}"
            }
            validateDisplayNameRequest(requested, channel, access)
            requested.dosing?.let { dosing ->
                validateCalibrationConfigRequest(dosing, channel, access)
                validateReservoirConfigRequest(dosing, channel, access)
            }
        }
    }

    private fun validateDisplayNameRequest(
        requested: DeviceDosingChannelConfig,
        channel: DeviceDosingChannelStatus,
        access: DeviceDosingRuntimeAccess
    ) {
        if (requested.displayName != null) {
            require(access.supportsChannelDisplayName && channel.editable.displayName)
        }
    }

    private fun validateCalibrationConfigRequest(
        dosing: DeviceDosingChannelDosingConfig,
        channel: DeviceDosingChannelStatus,
        access: DeviceDosingRuntimeAccess
    ) {
        val changesCalibration = dosing.doseMsPerMl != null || dosing.lastCalibratedAt != null
        if (changesCalibration) {
            require(access.supportsCalibrationWorkflow && channel.editable.dosingCalibration)
        }
    }

    private fun validateReservoirConfigRequest(
        dosing: DeviceDosingChannelDosingConfig,
        channel: DeviceDosingChannelStatus,
        access: DeviceDosingRuntimeAccess
    ) {
        val changesReservoir = dosing.reservoirTrackingEnabled != null ||
            dosing.reservoirCapacityMl != null
        if (!changesReservoir) return

        require(access.supportsReservoirRefill && channel.editable.reservoir)
        val enabled = dosing.reservoirTrackingEnabled
            ?: channel.dosing.reservoirTrackingEnabled
        val capacity = dosing.reservoirCapacityMl
            ?: channel.dosing.reservoirCapacityMl
        if (enabled) require(capacity > 0.0)
    }

    private fun validateScheduleRequests(
        schedules: List<DeviceDosingScheduleConfig>?,
        status: DeviceDosingStatus
    ) {
        if (schedules == null) return
        require(status.runtime.supportsSchedules)
        val channelsByKey = status.channels.associateBy(DeviceDosingChannelStatus::key)
        schedules.forEach { schedule ->
            val channel = requireNotNull(channelsByKey[schedule.normalizedChannelKey]) {
                "Unknown Dosing schedule channel: ${schedule.normalizedChannelKey}"
            }
            if (schedule.enabled) {
                require(channel.dosing.calibrated) {
                    "An enabled Dosing schedule requires a calibrated pump."
                }
                val doseDurationMs = schedule.amountMl * channel.dosing.doseMsPerMl.toDouble()
                require(doseDurationMs.isFinite() && doseDurationMs >= 1.0)
                require(doseDurationMs <= DOSING_DEVICE_UPTIME_MAX_MS.toDouble())
                val cycleMs = doseDurationMs + schedule.intervalOffMs.toDouble()
                val maximumRepeatCount =
                    ((DOSING_MILLISECONDS_PER_DAY + schedule.intervalOffMs) / cycleMs).toLong()
                require(schedule.repeatCount.toLong() <= maximumRepeatCount) {
                    "repeatCount exceeds the firmware day-bound Dosing capacity."
                }
            }
        }
    }

    private fun validateReturnedChannels(
        requested: List<DeviceDosingChannelConfig>?,
        config: DeviceDosingConfigSnapshot
    ) {
        if (requested == null) return
        val returnedByKey = config.channels.associateBy(DeviceDosingChannelConfigSnapshot::channelKey)
        requested.forEach { item ->
            val returned = requireNotNull(returnedByKey[item.normalizedChannelKey]) {
                "Firmware omitted requested Dosing channel ${item.normalizedChannelKey}."
            }
            item.regime?.let { require(returned.regime == it) }
            if (item.displayName != null) {
                val expectedOverride = item.normalizedDisplayName?.takeIf(String::isNotEmpty)
                require(returned.displayNameOverride == expectedOverride)
            }
            item.dosing?.let { requestedDosing ->
                requestedDosing.doseMsPerMl?.let {
                    require(returned.dosing.doseMsPerMl == it)
                }
                requestedDosing.lastCalibratedAt?.let {
                    require(returned.dosing.lastCalibratedAt == it)
                }
                requestedDosing.reservoirTrackingEnabled?.let {
                    require(returned.dosing.reservoirTrackingEnabled == it)
                }
                requestedDosing.reservoirCapacityMl?.let { capacity ->
                    val expected = capacity.takeIf { it > 0.0 } ?: DOSING_UNSET_RESERVOIR
                    require(dosingValuesEquivalent(returned.dosing.reservoirCapacityMl, expected))
                }
            }
        }
    }

    private fun validateReturnedSchedules(
        requested: List<DeviceDosingScheduleConfig>?,
        config: DeviceDosingConfigSnapshot
    ) {
        if (requested == null) return
        val expected = requested.mapIndexed { index, schedule ->
            DeviceDosingScheduleConfigSnapshot(
                listIndex = index,
                enabled = schedule.enabled,
                name = schedule.normalizedName,
                channelKey = schedule.normalizedChannelKey,
                weekdays = schedule.weekdays.toList(),
                startTimeMs = schedule.startTimeMs,
                intervalOnMs = schedule.intervalOnMs,
                intervalOffMs = schedule.intervalOffMs,
                repeatCount = schedule.repeatCount,
                amountMl = schedule.amountMl
            )
        }
        require(
            config.schedules.size == expected.size &&
                config.schedules.zip(expected).all { (returned, requested) ->
                    returned.matchesRequest(requested)
                }
        ) {
            "Firmware Dosing schedule snapshot differs from the requested replacement."
        }
    }

    private fun validateKnownChannel(
        channelKey: String,
        currentStatus: DeviceDosingStatus?,
        operation: String
    ): DeviceDosingChannelStatus {
        val status = requireStatus(currentStatus, operation)
        return requireNotNull(status.channels.singleOrNull { channel -> channel.key == channelKey }) {
            "Unknown Dosing channel key: $channelKey"
        }
    }

    private fun requireStatus(
        currentStatus: DeviceDosingStatus?,
        operation: String
    ): DeviceDosingStatus = requireNotNull(currentStatus) {
        "Dosing status must be loaded before $operation."
    }

    private fun validateDoseDuration(amountMl: Double, doseMsPerMl: Long) {
        val durationMs = (amountMl * doseMsPerMl.toDouble()).roundToLong()
        require(
            durationMs in
                DeviceDosingRuntimeContract.Limit.MIN_MANUAL_DOSE_DURATION_MS..
                DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_DURATION_MS
        ) { "Calculated manual dose duration is outside the firmware-safe range." }
    }
}

private fun DeviceDosingScheduleConfigSnapshot.matchesRequest(
    requested: DeviceDosingScheduleConfigSnapshot
): Boolean = copy(amountMl = requested.amountMl) == requested &&
    dosingValuesEquivalent(amountMl, requested.amountMl)

internal fun DeviceDosingChannelStatus.sameDosingChannelIdentity(
    other: DeviceDosingChannelStatus
): Boolean = copy(
    regime = other.regime,
    valueNow = other.valueNow,
    valueAuto = other.valueAuto,
    valueManual = other.valueManual,
    manualTimeoutMs = other.manualTimeoutMs,
    dosing = other.dosing
) == other
