package com.aqua.aqualight.data.devices.provisioning.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeCapabilities
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeCapabilitiesParser
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeIdentityParser
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeMetadataProjector
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.repository.ParsedDeviceRuntimeIdentity
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AqlProvisioningRuntimeMetadataResolver {

    suspend fun resolveAndConnect(
        repository: DevicesRepository,
        provisionalSnapshot: DeviceSnapshot,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
    ): Result<DeviceSnapshot> {
        return runCatching {
            val events = repository.runtimeEvents()
                ?: error("Runtime event stream is unavailable; device identity cannot be verified.")

            var bestResolvedSnapshot: DeviceSnapshot? = null

            withTimeoutOrNull(timeoutMillis) {
                coroutineScope {
                    val resolved = CompletableDeferred<DeviceSnapshot>()
                    var identity: ParsedDeviceRuntimeIdentity? = null
                    var capabilities: DeviceRuntimeCapabilities? = null
                    var terminalFailure: Throwable? = null

                    fun completeIfReady() {
                        val readyIdentity = identity ?: return
                        val readyCapabilities = capabilities ?: return
                        if (!resolved.isCompleted) {
                            val snapshot = DeviceRuntimeMetadataProjector.applyProvisioningMetadata(
                                snapshot = provisionalSnapshot,
                                parsedIdentity = readyIdentity,
                                capabilities = readyCapabilities
                            )
                            bestResolvedSnapshot = snapshot
                            resolved.complete(snapshot)
                        }
                    }

                    val collectorJob = launch {
                        events.collect { event ->
                            if (event.deviceUid != provisionalSnapshot.deviceUid) {
                                return@collect
                            }
                            val response = (event as? AqlWsEvent.Message)
                                ?.parsed as? AqlWsIncomingMessage.Response
                                ?: return@collect
                            if (!response.ok) return@collect

                            when {
                                response.isDeviceAction(AqlWsContract.ACTION_DEVICE_IDENTITY_GET) -> {
                                    DeviceRuntimeIdentityParser.parse(
                                        expectedDeviceUid = provisionalSnapshot.deviceUid,
                                        data = response.data
                                    ).onSuccess { parsed ->
                                        val previous = identity
                                        if (previous != null && previous != parsed) {
                                            terminalFailure = IllegalStateException(
                                                "Provisioning received conflicting identity metadata."
                                            )
                                        } else {
                                            identity = parsed
                                            completeIfReady()
                                        }
                                    }.onFailure { error ->
                                        terminalFailure = error
                                    }
                                }

                                response.isDeviceAction(AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET) -> {
                                    DeviceRuntimeCapabilitiesParser.parse(response.data)
                                        .onSuccess { parsed ->
                                            val previous = capabilities
                                            if (previous != null && previous != parsed) {
                                                terminalFailure = IllegalStateException(
                                                    "Provisioning received conflicting capability metadata."
                                                )
                                            } else {
                                                capabilities = parsed
                                                completeIfReady()
                                            }
                                        }
                                        .onFailure { error -> terminalFailure = error }
                                }
                            }

                            val failure = terminalFailure
                            if (failure != null && !resolved.isCompleted) {
                                resolved.completeExceptionally(failure)
                            }
                        }
                    }

                    repository.connectRuntime(provisionalSnapshot.deviceUid)
                        .getOrThrow()

                    try {
                        resolved.await()
                    } finally {
                        collectorJob.cancel()
                    }
                }
            } ?: bestResolvedSnapshot ?: error(
                "Exact runtime identity and capabilities were not received before timeout."
            )
        }
    }

    private fun AqlWsIncomingMessage.Response.isDeviceAction(actionName: String): Boolean {
        return module == AqlWsContract.MODULE_DEVICE && action == actionName
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}
