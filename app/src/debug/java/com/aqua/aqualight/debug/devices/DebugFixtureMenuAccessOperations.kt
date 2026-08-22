package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.toOwnerDeviceFamily

/** Bypasses physical liveness only for known debug fixture UIDs, while retaining catalog closure. */
internal class DebugFixtureMenuAccessOperations(
    private val delegate: DeviceMenuAccessOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceMenuAccessOperations {

    override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult {
        val snapshot = fixtures.snapshot(deviceUid)
        return if (snapshot == null) {
            delegate.resolve(deviceUid)
        } else {
            resolveFixture(snapshot)
        }
    }

    private fun resolveFixture(snapshot: DeviceSnapshot): DeviceMenuAccessResult =
        if (!snapshot.hasValidatedRuntimeMetadata) {
            unavailable(snapshot, DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN)
        } else {
            when (val validation = AqlCommercialDeviceCatalog.validateSnapshot(snapshot)) {
                is AqlCommercialCatalogValidation.Valid -> DeviceMenuAccessResult.Available(
                    deviceUid = snapshot.deviceUid.value,
                    title = snapshot.title,
                    family = validation.product.family.toOwnerDeviceFamily(),
                    presentationPrepared = true
                )
                is AqlCommercialCatalogValidation.Invalid -> unavailable(
                    snapshot,
                    DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH
                )
            }
        }

    private fun unavailable(
        snapshot: DeviceSnapshot,
        reason: DeviceMenuUnavailableReason
    ): DeviceMenuAccessResult.Unavailable = DeviceMenuAccessResult.Unavailable(
        title = snapshot.title,
        reason = reason
    )
}
