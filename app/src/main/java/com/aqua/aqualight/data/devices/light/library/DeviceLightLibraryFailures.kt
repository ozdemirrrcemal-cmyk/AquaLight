package com.aqua.aqualight.data.devices.light.library

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryNamePolicy
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

internal fun String.validatedNameOrFailure(): DeviceLightLibraryNamePolicy.CanonicalName? =
    when (val validation = DeviceLightLibraryNamePolicy.validate(this)) {
        is DeviceLightLibraryNamePolicy.Validation.Valid -> validation.name
        is DeviceLightLibraryNamePolicy.Validation.Invalid -> null
    }

internal fun DeviceLightLibraryKind.toStoredKind(): StoredDeviceLightLibraryKind = when (this) {
    DeviceLightLibraryKind.MANUAL ->
        StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_MANUAL
    DeviceLightLibraryKind.CUSTOM ->
        StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_CUSTOM
}

internal fun DeviceLightControlFailure.toLibraryFailure(): DeviceLightLibraryFailure = when (this) {
    DeviceLightControlFailure.UNAVAILABLE -> DeviceLightLibraryFailure.UNAVAILABLE
    DeviceLightControlFailure.NOT_CONNECTED -> DeviceLightLibraryFailure.NOT_CONNECTED
    DeviceLightControlFailure.UNSUPPORTED -> DeviceLightLibraryFailure.UNSUPPORTED
    DeviceLightControlFailure.REJECTED -> DeviceLightLibraryFailure.REJECTED
    DeviceLightControlFailure.INVALID_DATA -> DeviceLightLibraryFailure.INVALID_DATA
}

internal fun DeviceRuntimeCommandOutcome<*>.toLibraryMutationResult(
    entryId: String
): DeviceLightLibraryMutationResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> DeviceLightLibraryMutationResult.Success(entryId)
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated ->
        failed(DeviceLightLibraryFailure.NOT_CONNECTED)
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
        failed(DeviceLightLibraryFailure.UNSUPPORTED)
    is DeviceRuntimeCommandOutcome.FirmwareError ->
        failed(DeviceLightLibraryFailure.REJECTED)
    is DeviceRuntimeCommandOutcome.ProtocolError ->
        failed(DeviceLightLibraryFailure.INVALID_DATA)
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled ->
        failed(DeviceLightLibraryFailure.UNAVAILABLE)
}

internal fun String.toDeviceUidOrNull(): DeviceUid? = trim()
    .takeIf(String::isNotBlank)
    ?.let(::DeviceUid)

internal fun failed(failure: DeviceLightLibraryFailure) =
    DeviceLightLibraryMutationResult.Failed(failure)
