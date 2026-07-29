package com.aqua.aqualight.data.devices.provisioning.repository

import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Waits for the same authenticated, generation-scoped metadata publication used by normal runtime.
 * Provisioning has no parallel identity/capability reducer and cannot publish two-response metadata.
 */
class AqlProvisioningRuntimeMetadataResolver {

    suspend fun resolveAndConnect(
        repository: DevicesRepository,
        provisionalSnapshot: DeviceSnapshot,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
    ): Result<DeviceSnapshot> = runCatching {
        withTimeoutOrNull(timeoutMillis) {
            collectValidatedSnapshot(repository, provisionalSnapshot)
        } ?: error(
            "Exact runtime identity, capabilities and modules were not validated before timeout."
        )
    }

    private suspend fun collectValidatedSnapshot(
        repository: DevicesRepository,
        provisionalSnapshot: DeviceSnapshot
    ): DeviceSnapshot = coroutineScope {
        val resolved = CompletableDeferred<DeviceSnapshot>()
        val collectorJob = launch {
            repository.observeDevice(provisionalSnapshot.deviceUid).collect { snapshot ->
                if (snapshot == null || !snapshot.hasValidatedRuntimeMetadata) return@collect
                when (val validation = AqlCommercialDeviceCatalog.validateSnapshot(snapshot)) {
                    is AqlCommercialCatalogValidation.Valid -> resolved.complete(snapshot)
                    is AqlCommercialCatalogValidation.Invalid -> resolved.completeExceptionally(
                        IllegalStateException(
                            "Validated runtime snapshot failed catalog projection: " +
                                "${validation.failure.code}:${validation.failure.field}"
                        )
                    )
                }
            }
        }

        repository.connectRuntime(provisionalSnapshot.deviceUid).getOrThrow()
        try {
            resolved.await()
        } finally {
            collectorJob.cancel()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}
