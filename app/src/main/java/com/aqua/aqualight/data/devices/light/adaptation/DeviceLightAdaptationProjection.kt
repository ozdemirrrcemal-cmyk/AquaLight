package com.aqua.aqualight.data.devices.light.adaptation

import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationFailure
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationPolicy
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationReadResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationSnapshot
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.data.devices.light.supportsLightAdaptation
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationPolicy
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationState
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus

internal fun project(
    deviceUid: DeviceUid,
    status: DeviceLightStatus?,
    firmwareWriteAuthoritative: Boolean
): DeviceLightAdaptationReadResult = when {
    status == null -> readFailure(DeviceLightAdaptationFailure.UNAVAILABLE)
    !status.supportsLightAdaptation() -> readFailure(DeviceLightAdaptationFailure.UNSUPPORTED)
    else -> status.acclimation.toSnapshot(
        deviceUid,
        status.policy.acclimation,
        firmwareWriteAuthoritative
    )
        ?.let(DeviceLightAdaptationReadResult::Available)
        ?: readFailure(DeviceLightAdaptationFailure.INVALID_DATA)
}

internal fun DeviceLightAcclimationPolicy.accepts(
    startPercent: Int,
    durationDays: Int
): Boolean {
    val startRange = policyRangeOrNull(startPercentMin, startPercentMax, startPercentStep)
    val durationRange = policyRangeOrNull(durationDaysMin, durationDaysMax, durationDaysStep)
    return startRange?.accepts(startPercent) == true &&
        durationRange?.accepts(durationDays) == true
}

internal fun DeviceLightAcclimationStatus.toSnapshot(
    deviceUid: DeviceUid,
    policy: DeviceLightAcclimationPolicy,
    firmwareWriteAuthoritative: Boolean
): DeviceLightAdaptationSnapshot? {
    val applicationPolicy = policy.toApplicationPolicy()
    val statusFields = requiredStatusFieldsOrNull(revision, state, clockReady)
    return if (applicationPolicy == null || statusFields == null) {
        null
    } else {
        DeviceLightAdaptationSnapshot(
            deviceUid = deviceUid.value,
            revision = statusFields.revision,
            state = statusFields.state.toApplicationState(),
            clockReady = statusFields.clockReady,
            startPercent = startPercent ?: applicationPolicy.defaultStartPercent,
            currentPermille = currentPermille,
            targetPercent = targetPercent ?: applicationPolicy.targetPercent,
            durationDays = durationDays ?: applicationPolicy.defaultDurationDays,
            startedAtEpochSeconds = startedAtEpochSeconds,
            endsAtEpochSeconds = endsAtEpochSeconds,
            remainingSeconds = remainingSeconds,
            policy = applicationPolicy,
            firmwareWriteAuthoritative = firmwareWriteAuthoritative
        )
    }
}

private fun DeviceLightAcclimationPolicy.toApplicationPolicy(): DeviceLightAdaptationPolicy? {
    val startRange = policyRangeOrNull(startPercentMin, startPercentMax, startPercentStep)
    val durationRange = policyRangeOrNull(durationDaysMin, durationDaysMax, durationDaysStep)
    val defaults = policyDefaultsOrNull(
        defaultStartPercent,
        defaultDurationDays,
        targetPercent
    )
    return if (startRange == null || durationRange == null || defaults == null) {
        null
    } else {
        DeviceLightAdaptationPolicy(
            startPercentMin = startRange.minimum,
            startPercentMax = startRange.maximum,
            startPercentStep = startRange.step,
            defaultStartPercent = defaults.startPercent,
            durationDaysMin = durationRange.minimum,
            durationDaysMax = durationRange.maximum,
            durationDaysStep = durationRange.step,
            defaultDurationDays = defaults.durationDays,
            targetPercent = defaults.targetPercent
        )
    }
}

private fun policyRangeOrNull(
    minimum: Int?,
    maximum: Int?,
    step: Int?
): AdaptationPolicyRange? = when {
    minimum == null || maximum == null || step == null -> null
    step <= 0 -> null
    else -> AdaptationPolicyRange(minimum, maximum, step)
}

private fun policyDefaultsOrNull(
    startPercent: Int?,
    durationDays: Int?,
    targetPercent: Int?
): AdaptationPolicyDefaults? = if (
    startPercent == null || durationDays == null || targetPercent == null
) {
    null
} else {
    AdaptationPolicyDefaults(startPercent, durationDays, targetPercent)
}

private fun requiredStatusFieldsOrNull(
    revision: Long?,
    state: DeviceLightAcclimationState?,
    clockReady: Boolean?
): AdaptationStatusFields? = if (revision == null || state == null || clockReady == null) {
    null
} else {
    AdaptationStatusFields(revision, state, clockReady)
}

private fun DeviceLightAcclimationState.toApplicationState(): DeviceLightAdaptationState =
    when (this) {
        DeviceLightAcclimationState.DISABLED -> DeviceLightAdaptationState.DISABLED
        DeviceLightAcclimationState.ACTIVE -> DeviceLightAdaptationState.ACTIVE
        DeviceLightAcclimationState.COMPLETED -> DeviceLightAdaptationState.COMPLETED
    }

private data class AdaptationPolicyRange(
    val minimum: Int,
    val maximum: Int,
    val step: Int
) {
    fun accepts(value: Int): Boolean =
        value in minimum..maximum && (value - minimum) % step == 0
}

private data class AdaptationPolicyDefaults(
    val startPercent: Int,
    val durationDays: Int,
    val targetPercent: Int
)

private data class AdaptationStatusFields(
    val revision: Long,
    val state: DeviceLightAcclimationState,
    val clockReady: Boolean
)
