package com.aqua.aqualight.data.devices.light.adaptation

import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationFailure
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationPolicy
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationReadResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationSnapshot
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationPolicy
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationState
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus

internal fun project(
    deviceUid: DeviceUid,
    status: DeviceLightStatus?
): DeviceLightAdaptationReadResult = when {
    status == null -> readFailure(DeviceLightAdaptationFailure.UNAVAILABLE)
    !status.supportsAdaptation() -> readFailure(DeviceLightAdaptationFailure.UNSUPPORTED)
    else -> status.acclimation.toSnapshot(deviceUid, status.policy.acclimation)
        ?.let(DeviceLightAdaptationReadResult::Available)
        ?: readFailure(DeviceLightAdaptationFailure.INVALID_DATA)
}

private fun DeviceLightStatus.supportsAdaptation(): Boolean =
    product == DeviceLightProduct.WRGB_PRO_ELITE &&
        features.acclimation &&
        acclimation.supported &&
        policy.acclimation.supported

internal fun DeviceLightAcclimationPolicy.accepts(
    startPercent: Int,
    durationDays: Int
): Boolean {
    val startMin = startPercentMin
    val startMax = startPercentMax
    val startStep = startPercentStep
    val durationMin = durationDaysMin
    val durationMax = durationDaysMax
    val durationStep = durationDaysStep
    return if (
        startMin == null || startMax == null || startStep == null ||
        durationMin == null || durationMax == null || durationStep == null
    ) {
        false
    } else {
        startPercent in startMin..startMax &&
            (startPercent - startMin) % startStep == 0 &&
            durationDays in durationMin..durationMax &&
            (durationDays - durationMin) % durationStep == 0
    }
}

internal fun DeviceLightAcclimationStatus.toSnapshot(
    deviceUid: DeviceUid,
    policy: DeviceLightAcclimationPolicy
): DeviceLightAdaptationSnapshot? {
    val applicationPolicy = policy.toApplicationPolicy()
    val revisionValue = revision
    val stateValue = state
    val clockReadyValue = clockReady
    return if (
        applicationPolicy == null || revisionValue == null ||
        stateValue == null || clockReadyValue == null
    ) {
        null
    } else {
        DeviceLightAdaptationSnapshot(
            deviceUid = deviceUid.value,
            revision = revisionValue,
            state = stateValue.toApplicationState(),
            clockReady = clockReadyValue,
            startPercent = startPercent ?: applicationPolicy.defaultStartPercent,
            currentPermille = currentPermille,
            targetPercent = targetPercent ?: applicationPolicy.targetPercent,
            durationDays = durationDays ?: applicationPolicy.defaultDurationDays,
            startedAtEpochSeconds = startedAtEpochSeconds,
            endsAtEpochSeconds = endsAtEpochSeconds,
            remainingSeconds = remainingSeconds,
            policy = applicationPolicy
        )
    }
}

private fun DeviceLightAcclimationPolicy.toApplicationPolicy(): DeviceLightAdaptationPolicy? {
    val startMin = startPercentMin
    val startMax = startPercentMax
    val startStep = startPercentStep
    val defaultStart = defaultStartPercent
    val durationMin = durationDaysMin
    val durationMax = durationDaysMax
    val durationStep = durationDaysStep
    val defaultDuration = defaultDurationDays
    val target = targetPercent
    return if (
        startMin == null || startMax == null || startStep == null || defaultStart == null ||
        durationMin == null || durationMax == null || durationStep == null ||
        defaultDuration == null || target == null
    ) {
        null
    } else {
        DeviceLightAdaptationPolicy(
            startPercentMin = startMin,
            startPercentMax = startMax,
            startPercentStep = startStep,
            defaultStartPercent = defaultStart,
            durationDaysMin = durationMin,
            durationDaysMax = durationMax,
            durationDaysStep = durationStep,
            defaultDurationDays = defaultDuration,
            targetPercent = target
        )
    }
}

private fun DeviceLightAcclimationState.toApplicationState(): DeviceLightAdaptationState =
    when (this) {
        DeviceLightAcclimationState.DISABLED -> DeviceLightAdaptationState.DISABLED
        DeviceLightAcclimationState.ACTIVE -> DeviceLightAdaptationState.ACTIVE
        DeviceLightAcclimationState.COMPLETED -> DeviceLightAdaptationState.COMPLETED
    }
