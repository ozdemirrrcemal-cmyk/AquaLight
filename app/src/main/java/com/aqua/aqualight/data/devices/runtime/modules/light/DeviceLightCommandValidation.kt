package com.aqua.aqualight.data.devices.runtime.modules.light

/** Verifies that a successful firmware response describes the exact requested mutation. */
internal object DeviceLightCommandValidation {
    fun validateManual(
        request: DeviceLightManualSetPayload,
        result: DeviceLightManualMutationResult
    ) {
        val expectedOperation = if (request.clear) {
            DeviceLightManualOperation.CLEAR_MANUAL
        } else {
            DeviceLightManualOperation.MANUAL_STATE
        }
        require(result.operation == expectedOperation)
        require(result.manualActive == !request.clear)
        val expectedDuration = if (request.clear) {
            0L
        } else {
            request.durationMs ?: DeviceLightRuntimeContract.Limit.DEFAULT_MANUAL_DURATION_MS
        }
        require(result.durationMs == expectedDuration)
        val requestedKeys = request.channels.map { channel -> channel.channelKey.normalizedKey() }
        require(requestedKeys.toSet().size == requestedKeys.size) {
            "Manual Light request contains duplicate channel keys."
        }
        if (requestedKeys.isNotEmpty()) {
            val returnedKeys = result.channels.map { item -> item.channel.key }
            require(returnedKeys.toSet() == requestedKeys.toSet()) {
                "Firmware returned different manual Light channels."
            }
        }
        if (!request.clear) validateManualValues(request, result)
    }

    fun validateChannelRegime(
        request: DeviceLightChannelRegimeSetPayload,
        result: DeviceLightChannelRegimeMutationResult
    ) {
        require(result.channelKey == request.channelKey.normalizedKey())
        require(result.regime == request.regime)
        require(result.saveRequested == request.save)
        require(result.saved == request.save)
    }

    fun validateProgramApply(
        request: DeviceLightProgramApplyPayload,
        result: DeviceLightProgramApplyResult
    ) {
        require(result.channelKey == request.channelKey.normalizedKey())
        require(result.saveRequested == request.save)
        require(result.saved == request.save)
        require(result.created == (request.programIndex == null))
        request.programIndex?.let { expectedIndex ->
            require(result.programIndex == expectedIndex)
        }
        require(result.program.channelKey == request.channelKey.normalizedKey())
        require(result.program.points.size == request.points.size)
        request.points.zip(result.program.points).forEach { (requested, returned) ->
            requested.timeMs?.let { timeMs ->
                require(returned.timeMs == timeMs % DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY)
            }
            requested.percent?.let { percent -> require(closeLightValue(returned.percent, percent)) }
            requested.value?.let { value -> require(closeLightValue(returned.value, value)) }
        }
    }

    fun validateProgramDelete(
        request: DeviceLightProgramDeletePayload,
        result: DeviceLightProgramDeleteResult
    ) {
        require(result.programIndex == request.programIndex)
        require(result.saveRequested == request.save)
        require(result.saved == request.save)
    }

    fun validateTemperatureProtection(
        request: DeviceLightTemperatureProtectionSetPayload,
        result: DeviceLightTemperatureProtectionSetResult
    ) {
        require(result.saveRequested == request.save)
        require(result.saved == request.save)
        val returnedThreshold = requireNotNull(result.status.temperatureProtection.thresholdC)
        require(closeLightValue(returnedThreshold, request.thresholdC))
    }

    private fun validateManualValues(
        request: DeviceLightManualSetPayload,
        result: DeviceLightManualMutationResult
    ) {
        val returnedByKey = result.channels.associateBy { item -> item.channel.key }
        request.channels.forEach { requested ->
            val returned = requireNotNull(returnedByKey[requested.channelKey.normalizedKey()])
                .channel
            requested.percent?.let { percent ->
                require(closeLightValue(returned.percentManual, percent))
            }
            requested.value?.let { value ->
                require(closeLightValue(returned.valueManual, value))
            }
        }
    }
}

private fun String.normalizedKey(): String = trim().lowercase()

private fun closeLightValue(left: Double, right: Double): Boolean =
    kotlin.math.abs(left - right) <= LIGHT_RESPONSE_TOLERANCE

private const val LIGHT_RESPONSE_TOLERANCE = 0.001
