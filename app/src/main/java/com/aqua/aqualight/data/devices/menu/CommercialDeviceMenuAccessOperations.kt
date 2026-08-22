package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuPresentationPreparationOperations
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.toOwnerDeviceFamily
import java.util.concurrent.CancellationException

internal class CommercialDeviceMenuAccessOperations(
    private val livenessOperations: DeviceMenuAccessOperations,
    private val currentSnapshot: (DeviceUid) -> DeviceSnapshot?,
    private val presentationPreparationOperations:
        DeviceMenuPresentationPreparationOperations
) : DeviceMenuAccessOperations {

    override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult {
        return when (val liveness = livenessOperations.resolve(deviceUid)) {
            is DeviceMenuAccessResult.Unavailable -> liveness
            is DeviceMenuAccessResult.Available -> when (
                val validated = validateCommercialProduct(liveness)
            ) {
                is DeviceMenuAccessResult.Unavailable -> validated
                is DeviceMenuAccessResult.Available -> preparePresentation(validated)
            }
        }
    }

    private suspend fun preparePresentation(
        validated: DeviceMenuAccessResult.Available
    ): DeviceMenuAccessResult {
        val prepared = try {
            presentationPreparationOperations.prepare(validated)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
        return if (prepared) {
            validated.copy(presentationPrepared = true)
        } else {
            unavailable(validated, DeviceMenuUnavailableReason.CURRENT_DATA_NOT_READY)
        }
    }

    private fun validateCommercialProduct(
        liveness: DeviceMenuAccessResult.Available
    ): DeviceMenuAccessResult {
        val snapshot = currentSnapshot(DeviceUid(liveness.deviceUid))
        return when {
            snapshot == null -> unavailable(
                liveness,
                DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED
            )
            !snapshot.hasValidatedRuntimeMetadata -> unavailable(
                liveness,
                DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
            )
            else -> validateCatalog(snapshot, liveness)
        }
    }

    private fun validateCatalog(
        snapshot: DeviceSnapshot,
        liveness: DeviceMenuAccessResult.Available
    ): DeviceMenuAccessResult = when (
        val validation = AqlCommercialDeviceCatalog.validateSnapshot(snapshot)
    ) {
        is AqlCommercialCatalogValidation.Valid -> liveness.copy(
            family = validation.product.family.toOwnerDeviceFamily()
        )
        is AqlCommercialCatalogValidation.Invalid -> unavailable(
            liveness,
            DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH
        )
    }

    private fun unavailable(
        liveness: DeviceMenuAccessResult.Available,
        reason: DeviceMenuUnavailableReason
    ): DeviceMenuAccessResult.Unavailable = DeviceMenuAccessResult.Unavailable(
        title = liveness.title,
        reason = reason
    )
}
