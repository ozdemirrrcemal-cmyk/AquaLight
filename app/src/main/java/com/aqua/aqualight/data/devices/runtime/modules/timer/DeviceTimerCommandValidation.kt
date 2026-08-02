package com.aqua.aqualight.data.devices.runtime.modules.timer

internal object DeviceTimerCommandValidation {
    fun validateStatus(
        status: DeviceTimerStatus,
        access: DeviceTimerRuntimeAccess
    ) {
        require(access.supportsApi) { "Standalone Timer API is not available." }
        require(status.supported)
        require(status.channelCount == access.channelCount) {
            "Timer status channel count differs from authenticated product metadata."
        }
        require(status.runtime.supportsConfigApply)
        require(status.runtime.supportsChannelSet)
        require(status.runtime.supportsChannels)
        require(status.runtime.supportsSchedules == access.supportsSchedules)
        require(status.channels.all { channel ->
            channel.editable.displayName == access.supportsChannelDisplayName
        })
    }

    fun validateConfigRequest(
        payload: DeviceTimerConfigApplyPayload,
        currentStatus: DeviceTimerStatus?,
        access: DeviceTimerRuntimeAccess
    ) {
        require(access.supportsApi) { "Standalone Timer API is not available." }
        if (payload.schedules != null) {
            require(access.supportsSchedules) { "Timer schedules are not available." }
        }
        val status = requireNotNull(currentStatus) {
            "Timer status must be loaded before applying Timer config."
        }
        require(status.supported && status.runtime.supportsConfigApply)
        validateChannelRequests(payload.channels, status, access)
        validateScheduleRequests(payload.schedules, status)
    }

    fun validateConfigResult(
        payload: DeviceTimerConfigApplyPayload,
        result: DeviceTimerConfigApplyResult,
        currentStatus: DeviceTimerStatus?,
        access: DeviceTimerRuntimeAccess
    ) {
        require(result.saveRequested == payload.save)
        require(result.saved == payload.save)
        require(result.appliedChannels == (payload.channels != null))
        require(result.appliedSchedules == (payload.schedules != null))
        validateReturnedChannels(payload.channels, result.config)
        validateReturnedTimerSchedules(payload.schedules, result.config)
        validateConfigSnapshot(result.config, currentStatus, access)
    }

    fun validateChannelRequest(
        payload: DeviceTimerChannelSetPayload,
        currentStatus: DeviceTimerStatus?,
        access: DeviceTimerRuntimeAccess
    ) {
        require(access.supportsApi && access.supportsChannelState) {
            "Timer channel state is not available."
        }
        val status = requireNotNull(currentStatus) {
            "Timer status must be loaded before changing channel state."
        }
        require(status.supported && status.runtime.supportsChannelSet)
        require(status.channels.any { channel -> channel.key == payload.normalizedChannelKey }) {
            "Unknown Timer channel key: ${payload.normalizedChannelKey}"
        }
    }

    fun validateChannelResult(
        payload: DeviceTimerChannelSetPayload,
        result: DeviceTimerChannelSetResult,
        currentStatus: DeviceTimerStatus?,
        access: DeviceTimerRuntimeAccess
    ) {
        require(result.saveRequested == payload.save)
        require(result.saved == payload.save)
        require(result.channelKey == payload.normalizedChannelKey)
        require(result.regime == payload.regime)
        require(result.channel.channel.key == payload.normalizedChannelKey)
        require(result.channel.channel.regime == payload.regime)
        validateChannelSnapshot(result, currentStatus, access)
    }

    fun validateConfigSnapshot(
        config: DeviceTimerConfigSnapshot,
        currentStatus: DeviceTimerStatus?,
        access: DeviceTimerRuntimeAccess
    ) {
        require(access.supportsApi)
        require(config.channels.size == access.channelCount) {
            "Timer config channel count differs from authenticated product metadata."
        }
        if (!access.supportsSchedules) require(config.schedules.isEmpty())
        if (!access.supportsChannelDisplayName) {
            require(config.channels.all { channel -> channel.displayNameOverride == null })
        }
        currentStatus?.let { status ->
            require(
                config.channels.map(DeviceTimerChannelConfigSnapshot::channelKey) ==
                    status.channels.map(DeviceTimerChannelStatus::key)
            ) { "Timer config channel identity differs from current status." }
        }
    }

    fun validateChannelSnapshot(
        result: DeviceTimerChannelSetResult,
        currentStatus: DeviceTimerStatus?,
        access: DeviceTimerRuntimeAccess
    ) {
        require(access.supportsApi && access.supportsChannelState)
        require(result.channel.listIndex in 0 until access.channelCount)
        require(result.channel.channel.editable.displayName == access.supportsChannelDisplayName)
        currentStatus?.let { status ->
            val current = requireNotNull(status.channels.getOrNull(result.channel.listIndex))
            require(current.sameTimerChannelIdentity(result.channel.channel)) {
                "Timer channel mutation identity differs from current status."
            }
        }
    }

    private fun validateChannelRequests(
        channels: List<DeviceTimerChannelConfig>?,
        status: DeviceTimerStatus,
        access: DeviceTimerRuntimeAccess
    ) {
        if (channels == null) return
        require(status.runtime.supportsChannels)
        val channelsByKey = status.channels.associateBy(DeviceTimerChannelStatus::key)
        channels.forEach { requested ->
            val channel = requireNotNull(channelsByKey[requested.normalizedChannelKey]) {
                "Unknown Timer channel key: ${requested.normalizedChannelKey}"
            }
            if (requested.displayName != null) {
                require(access.supportsChannelDisplayName && channel.editable.displayName) {
                    "Timer channel displayName is fixed for ${requested.normalizedChannelKey}."
                }
            }
        }
    }

    private fun validateScheduleRequests(
        schedules: List<DeviceTimerScheduleConfig>?,
        status: DeviceTimerStatus
    ) {
        if (schedules == null) return
        require(status.runtime.supportsSchedules)
        val channelKeys = status.channels.mapTo(linkedSetOf(), DeviceTimerChannelStatus::key)
        schedules.forEach { schedule ->
            require(schedule.normalizedChannelKey in channelKeys) {
                "Unknown Timer schedule channel: ${schedule.normalizedChannelKey}"
            }
        }
    }

    private fun validateReturnedChannels(
        requested: List<DeviceTimerChannelConfig>?,
        config: DeviceTimerConfigSnapshot
    ) {
        if (requested == null) return
        val returnedByKey = config.channels.associateBy(DeviceTimerChannelConfigSnapshot::channelKey)
        requested.forEach { item ->
            val returned = requireNotNull(returnedByKey[item.normalizedChannelKey]) {
                "Firmware omitted requested Timer channel ${item.normalizedChannelKey}."
            }
            item.regime?.let { regime -> require(returned.regime == regime) }
            if (item.displayName != null) {
                val expectedOverride = item.normalizedDisplayName?.takeIf(String::isNotEmpty)
                require(returned.displayNameOverride == expectedOverride) {
                    "Firmware Timer displayName differs for ${item.normalizedChannelKey}."
                }
            }
        }
    }

}
