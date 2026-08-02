package com.aqua.aqualight.data.devices.runtime.modules.cooling

internal object DeviceCoolingCommandValidation {
    fun validateRequest(
        payload: DeviceCoolingConfigApplyPayload,
        currentStatus: DeviceCoolingStatus?
    ) {
        val effectiveMinimum = payload.minTemperatureC ?: currentStatus?.minTemperatureC
        val effectiveMaximum = payload.maxTemperatureC ?: currentStatus?.maxTemperatureC
        if (effectiveMinimum != null && effectiveMaximum != null) {
            require(effectiveMinimum < effectiveMaximum) {
                "The requested cooling range is invalid against current firmware state."
            }
        }
        if (payload.fans.isNotEmpty() && currentStatus != null) {
            require(currentStatus.runtime.supportsFanDisplayName) {
                "Fan display names are not supported by this device."
            }
            require(payload.fans.size <= currentStatus.fanOutputCount)
            val fansByKey = currentStatus.fans.associateBy(DeviceCoolingFanStatus::key)
            payload.fans.forEach { requested ->
                val fan = requireNotNull(fansByKey[requested.normalizedFanKey]) {
                    "Unknown cooling fan key: ${requested.normalizedFanKey}"
                }
                require(fan.editable.displayName) {
                    "Fan displayName is fixed for ${requested.normalizedFanKey}."
                }
            }
        }
    }

    fun validateResult(
        payload: DeviceCoolingConfigApplyPayload,
        result: DeviceCoolingConfigApplyResult
    ) {
        require(result.saveRequested == payload.save)
        require(result.saved == payload.save)
        require(result.appliedGlobalConfig == payload.hasGlobalConfig)
        require(result.appliedFanDisplayNames == payload.fans.isNotEmpty())
        payload.mode?.let { requested -> require(result.config.mode == requested) }
        payload.minTemperatureC?.let { requested ->
            require(coolingValuesEquivalent(result.config.minTemperatureC, requested))
        }
        payload.maxTemperatureC?.let { requested ->
            require(coolingValuesEquivalent(result.config.maxTemperatureC, requested))
        }
        validateFanDisplayNames(payload, result.config)
    }

    private fun validateFanDisplayNames(
        payload: DeviceCoolingConfigApplyPayload,
        config: DeviceCoolingConfigSnapshot
    ) {
        if (payload.fans.isEmpty()) return
        val fansByKey = config.fans.associateBy { snapshot -> snapshot.fan.key }
        payload.fans.forEach { requested ->
            val returned = requireNotNull(fansByKey[requested.normalizedFanKey]) {
                "Firmware omitted requested fan ${requested.normalizedFanKey}."
            }.fan
            val expectedDisplayName = requested.normalizedDisplayName ?: returned.name
            require(returned.displayName == expectedDisplayName) {
                "Firmware fan displayName differs for ${requested.normalizedFanKey}."
            }
        }
    }
}
