package com.aqua.aqualight.data.devices.light.custom

import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomMutationResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomReadResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomWriteResult

/**
 * Enforces the durable Custom mutation transaction boundary.
 *
 * A persistent device mutation is never allowed to overlap volatile Preview.
 * Preview restoration must be acknowledged first, the durable mutation runs
 * second, and the result is accepted only after an authoritative device readback.
 */
internal suspend fun executeDeviceLightCustomPersistentWrite(
    clearPreview: suspend () -> DeviceLightCustomMutationResult,
    mutate: suspend () -> DeviceLightCustomMutationResult,
    readAuthoritative: suspend () -> DeviceLightCustomReadResult
): DeviceLightCustomWriteResult {
    val previewFailure = (clearPreview() as? DeviceLightCustomMutationResult.Failed)?.failure
    val mutationResult = if (previewFailure == null) mutate() else null
    val mutationFailure = (mutationResult as? DeviceLightCustomMutationResult.Failed)?.failure
    val authoritativeResult = if (previewFailure == null && mutationFailure == null) {
        readAuthoritative()
    } else {
        null
    }

    return when {
        previewFailure != null -> DeviceLightCustomWriteResult.Failed(previewFailure)
        mutationFailure != null -> DeviceLightCustomWriteResult.Failed(mutationFailure)
        authoritativeResult is DeviceLightCustomReadResult.Available ->
            DeviceLightCustomWriteResult.Success(authoritativeResult.snapshot)
        authoritativeResult is DeviceLightCustomReadResult.Failed ->
            DeviceLightCustomWriteResult.Failed(authoritativeResult.failure)
        else -> DeviceLightCustomWriteResult.Failed(DeviceLightCustomFailure.INVALID_DATA)
    }
}
