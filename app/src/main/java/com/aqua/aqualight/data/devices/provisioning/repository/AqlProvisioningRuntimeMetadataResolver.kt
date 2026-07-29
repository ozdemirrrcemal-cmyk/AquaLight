package com.aqua.aqualight.data.devices.provisioning.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeCapabilities
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeCapabilitiesParser
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeIdentityParser
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeMetadataProjector
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.repository.ParsedDeviceRuntimeIdentity
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AqlProvisioningRuntimeMetadataResolver {

    suspend fun resolveAndConnect(
        repository: DevicesRepository,
        provisionalSnapshot: DeviceSnapshot,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
    ): Result<DeviceSnapshot> = runCatching {
        val events = repository.runtimeEvents()
            ?: error("Runtime event stream is unavailable; device identity cannot be verified.")
        withTimeoutOrNull(timeoutMillis) {
            collectAndConnect(
                repository = repository,
                events = events,
                provisionalSnapshot = provisionalSnapshot
            )
        } ?: error("Exact runtime identity and capabilities were not received before timeout.")
    }

    private suspend fun collectAndConnect(
        repository: DevicesRepository,
        events: Flow<AqlWsEvent>,
        provisionalSnapshot: DeviceSnapshot
    ): DeviceSnapshot = coroutineScope {
        val resolved = CompletableDeferred<DeviceSnapshot>()
        val accumulator = ProvisioningMetadataAccumulator(provisionalSnapshot)
        val collectorJob = launch {
            events.collect { event ->
                val response = event.successfulResponseFor(provisionalSnapshot.deviceUid)
                val update = response?.let(accumulator::accept)
                update?.fold(
                    onSuccess = { snapshot -> snapshot?.let(resolved::complete) },
                    onFailure = resolved::completeExceptionally
                )
            }
        }

        repository.connectRuntime(provisionalSnapshot.deviceUid).getOrThrow()
        try {
            resolved.await()
        } finally {
            collectorJob.cancel()
        }
    }

    private class ProvisioningMetadataAccumulator(
        private val provisionalSnapshot: DeviceSnapshot
    ) {
        private var identity: ParsedDeviceRuntimeIdentity? = null
        private var capabilities: DeviceRuntimeCapabilities? = null

        fun accept(
            response: AqlWsIncomingMessage.Response
        ): Result<DeviceSnapshot?>? = when {
            response.isDeviceAction(AqlWsContract.ACTION_DEVICE_IDENTITY_GET) ->
                acceptIdentity(response)

            response.isDeviceAction(AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET) ->
                acceptCapabilities(response)

            else -> null
        }

        private fun acceptIdentity(
            response: AqlWsIncomingMessage.Response
        ): Result<DeviceSnapshot?> = DeviceRuntimeIdentityParser.parse(
            expectedDeviceUid = provisionalSnapshot.deviceUid,
            data = response.data
        ).mapCatching { parsed ->
            check(identity == null || identity == parsed) {
                "Provisioning received conflicting identity metadata."
            }
            identity = parsed
            projectIfReady()
        }

        private fun acceptCapabilities(
            response: AqlWsIncomingMessage.Response
        ): Result<DeviceSnapshot?> = DeviceRuntimeCapabilitiesParser.parse(response.data)
            .mapCatching { parsed ->
                check(capabilities == null || capabilities == parsed) {
                    "Provisioning received conflicting capability metadata."
                }
                capabilities = parsed
                projectIfReady()
            }

        private fun projectIfReady(): DeviceSnapshot? {
            val readyIdentity = identity
            val readyCapabilities = capabilities
            return if (readyIdentity != null && readyCapabilities != null) {
                DeviceRuntimeMetadataProjector.applyProvisioningMetadata(
                    snapshot = provisionalSnapshot,
                    parsedIdentity = readyIdentity,
                    capabilities = readyCapabilities
                )
            } else {
                null
            }
        }

        private fun AqlWsIncomingMessage.Response.isDeviceAction(
            actionName: String
        ): Boolean = module == AqlWsContract.MODULE_DEVICE && action == actionName
    }

    private fun AqlWsEvent.successfulResponseFor(
        expectedDeviceUid: DeviceUid
    ): AqlWsIncomingMessage.Response? = (this as? AqlWsEvent.Message)
        ?.takeIf { event -> event.deviceUid == expectedDeviceUid }
        ?.parsed
        ?.let { parsed -> parsed as? AqlWsIncomingMessage.Response }
        ?.takeIf(AqlWsIncomingMessage.Response::ok)

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}
