package com.aqua.aqualight.data.devices.runtime.modules.timer

internal object DeviceTimerCommandValidation {
    fun validateStatus(
        status: DeviceTimerStatus,
        requestedChannelKey: String?,
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
        require(status.runtime.supportsSchedules)
        if (requestedChannelKey == null) {
            require(!status.channelScoped)
        } else {
            require(status.channelScoped)
            require(status.selectedChannelKey == normalizeTimerChannelKey(requestedChannelKey))
        }
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
        require(!status.lockLoop) { "Timer runtime is locked until device restart." }
        // The caller's revision is an intentional compare-and-swap token. It may be older than
        // the latest local snapshot (for example, a long-lived program editor); firmware owns the
        // atomic conflict decision and returns the stable CONFLICT/expectedRevision identity.
        val channel = requireNotNull(
            status.channels.singleOrNull { it.key == payload.normalizedChannelKey }
        ) { "Unknown Timer channel key: ${payload.normalizedChannelKey}" }
        if (payload.displayName != DeviceTimerDisplayNameUpdate.Omitted) {
            require(access.supportsChannelDisplayName && channel.editable.displayName) {
                "Timer channel displayName is fixed for ${payload.normalizedChannelKey}."
            }
        }
    }

    fun validateConfigResult(
        payload: DeviceTimerConfigApplyPayload,
        result: DeviceTimerConfigApplyResult,
        currentStatus: DeviceTimerStatus?,
        access: DeviceTimerRuntimeAccess
    ) {
        require(result.saveRequested == payload.save)
        require(result.saved == payload.save)
        require(result.channelKey == payload.normalizedChannelKey)
        require(result.appliedDisplayName ==
            (payload.displayName != DeviceTimerDisplayNameUpdate.Omitted))
        require(result.replacedSchedules == (payload.schedules != null))
        require(result.revision == expectedRevision(payload.expectedRevision, result.changed))
        val current = currentStatus?.channels?.singleOrNull {
            it.key == payload.normalizedChannelKey
        }
        current?.let { require(it.sameTimerChannelIdentity(result.channel)) }
        when (val update = payload.displayName) {
            DeviceTimerDisplayNameUpdate.Omitted -> Unit
            DeviceTimerDisplayNameUpdate.Clear -> require(
                result.channel.displayName == result.channel.name
            )
            is DeviceTimerDisplayNameUpdate.Value -> require(
                result.channel.displayName == update.normalizedDisplayName
            )
        }
        payload.schedules?.let { replacement ->
            require(result.channel.scheduleCount == replacement.size)
        }
        validateMutationChannel(result.channel, access)
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
        require(!status.lockLoop) { "Timer runtime is locked until device restart." }
        require(payload.expectedRevision == status.revision) {
            "Timer channel expectedRevision differs from the authoritative status."
        }
        require(status.channels.any { channel -> channel.key == payload.normalizedChannelKey }) {
            "Unknown Timer channel key: ${payload.normalizedChannelKey}"
        }
        if (payload.durationMs != null) require(status.runtime.supportsTemporaryOverride)
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
        require(result.durationMs == (payload.durationMs ?: 0L))
        val revisionChanged = result.persistentChanged
        require(result.revision == expectedRevision(payload.expectedRevision, revisionChanged))
        val current = currentStatus?.channels?.singleOrNull {
            it.key == payload.normalizedChannelKey
        }
        current?.let { require(it.sameTimerChannelIdentity(result.channel)) }
        validateMutationChannel(result.channel, access)
    }

    private fun validateMutationChannel(
        channel: DeviceTimerChannelStatus,
        access: DeviceTimerRuntimeAccess
    ) {
        require(channel.listIndex in 0 until access.channelCount)
        require(channel.editable.displayName == access.supportsChannelDisplayName)
    }

    private fun expectedRevision(current: Long, changed: Boolean): Long =
        if (!changed) current
        else if (current == DeviceTimerRuntimeContract.Limit.UINT32_MAX) 0L
        else current + 1L
}
