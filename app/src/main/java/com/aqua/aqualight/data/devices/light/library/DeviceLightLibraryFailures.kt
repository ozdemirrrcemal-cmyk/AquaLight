package com.aqua.aqualight.data.devices.light.library

import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryNamePolicy
import com.aqua.aqualight.data.devices.model.DeviceUid

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

internal fun String.toDeviceUidOrNull(): DeviceUid? = trim()
    .takeIf(String::isNotBlank)
    ?.let(::DeviceUid)

internal fun failed(failure: DeviceLightLibraryFailure) =
    DeviceLightLibraryMutationResult.Failed(failure)
