package com.aqua.aqualight.data.devices.provisioning.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeMetadataReducer
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class AqlProvisioningRuntimeMetadataResolver(
    private val metadataReducer: DeviceRuntimeMetadataReducer = DeviceRuntimeMetadataReducer()
) {

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

                    var identityRequestId = ""
                    var capabilitiesRequestId = ""
                    var identityData: JSONObject? = null
                    var capabilitiesData: JSONObject? = null

                    fun refreshBestSnapshot(): DeviceSnapshot? {
                        val identity = identityData
                        val capabilities = capabilitiesData
                        if (identity == null || capabilities == null) {
                            return null
                        }

                        val snapshot = metadataReducer.applyDeviceCapabilities(
                            snapshot = metadataReducer.applyDeviceIdentity(
                                snapshot = provisionalSnapshot,
                                identityData = identity
                            ),
                            capabilitiesData = capabilities
                        )
                        bestResolvedSnapshot = snapshot
                        return snapshot
                    }

                    fun completeIfReady() {
                        val snapshot = refreshBestSnapshot()
                        if (snapshot != null && !resolved.isCompleted) {
                            resolved.complete(snapshot)
                        }
                    }

                    val collectorJob = launch {
                        events.collect { event ->
                            if (event.deviceUid != provisionalSnapshot.deviceUid) {
                                return@collect
                            }

                            when (event) {
                                is AqlWsEvent.Opened -> {
                                    val commandClient = repository.commandClient(
                                        provisionalSnapshot.deviceUid
                                    ) ?: return@collect

                                    if (identityRequestId.isBlank()) {
                                        identityRequestId = commandClient.command(
                                            module = AqlWsContract.MODULE_DEVICE,
                                            action = AqlWsContract.ACTION_DEVICE_IDENTITY_GET
                                        ).orEmpty()
                                    }

                                    if (capabilitiesRequestId.isBlank()) {
                                        capabilitiesRequestId = commandClient.command(
                                            module = AqlWsContract.MODULE_DEVICE,
                                            action = AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET
                                        ).orEmpty()
                                    }
                                }

                                is AqlWsEvent.Message -> {
                                    val response = event.parsed as? AqlWsIncomingMessage.Response
                                        ?: return@collect

                                    if (!response.ok) {
                                        return@collect
                                    }

                                    val data = response.json.optJSONObject("data")
                                        ?: JSONObject()

                                    when {
                                        response.id == identityRequestId ||
                                            response.isDeviceAction(AqlWsContract.ACTION_DEVICE_IDENTITY_GET) -> {
                                            identityData = data
                                            completeIfReady()
                                        }

                                        response.id == capabilitiesRequestId ||
                                            response.isDeviceAction(AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET) -> {
                                            capabilitiesData = data
                                            completeIfReady()
                                        }
                                    }
                                }

                                else -> Unit
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
            } ?: bestResolvedSnapshot ?: error("Runtime identity and capabilities were not received before timeout.")
        }
    }

    private fun AqlWsIncomingMessage.Response.isDeviceAction(actionName: String): Boolean {
        return module == AqlWsContract.MODULE_DEVICE && action == actionName
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}
