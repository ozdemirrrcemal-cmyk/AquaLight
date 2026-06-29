package com.aqua.aqualight.data.devices.provisioning.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

class AqlProvisioningRuntimeMetadataResolver {

    suspend fun resolveAndConnect(
        repository: DevicesRepository,
        provisionalSnapshot: DeviceSnapshot,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
    ): Result<DeviceSnapshot> {
        return runCatching {
            val events = repository.runtimeEvents()

            if (events == null) {
                repository.connectRuntime(provisionalSnapshot.deviceUid)
                return@runCatching provisionalSnapshot
            }

            withTimeoutOrNull(timeoutMillis) {
                coroutineScope {
                    val resolved = CompletableDeferred<DeviceSnapshot>()

                    var identityRequestId = ""
                    var capabilitiesRequestId = ""
                    var identityData: JSONObject? = null
                    var capabilitiesData: JSONObject? = null

                    fun completeIfReady() {
                        val identity = identityData
                        val capabilities = capabilitiesData

                        if (
                            identity != null &&
                            capabilities != null &&
                            !resolved.isCompleted
                        ) {
                            resolved.complete(
                                provisionalSnapshot.withRuntimeMetadata(
                                    identityData = identity,
                                    capabilitiesData = capabilities
                                )
                            )
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

                                    when (response.id) {
                                        identityRequestId -> {
                                            identityData = data
                                            completeIfReady()
                                        }

                                        capabilitiesRequestId -> {
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
            } ?: provisionalSnapshot
        }
    }

    private fun DeviceSnapshot.withRuntimeMetadata(
        identityData: JSONObject,
        capabilitiesData: JSONObject
    ): DeviceSnapshot {
        val runtime = identityData.optJSONObject("runtime") ?: JSONObject()
        val capabilitiesJson = capabilitiesData.optJSONObject("capabilities") ?: JSONObject()
        val limitsJson = capabilitiesData.optJSONObject("limits") ?: JSONObject()

        val familyRaw = identityData.optString("family")
            .trim()
            .ifBlank {
                product.familyRaw.ifBlank { product.family.wireValue }
            }

        val reportedDeviceUid = identityData.optString("deviceUid")
            .trim()
        if (reportedDeviceUid.isNotBlank() && reportedDeviceUid != deviceUid.value) {
            error("Runtime identity deviceUid does not match registered device uid.")
        }

        return copy(
            identity = identity.copy(
                shortId = identityData.optString("shortId")
                    .trim()
                    .ifBlank { identity.shortId },
                macAddress = identityData.optString("macAddress")
                    .trim()
                    .ifBlank { identity.macAddress },
                serialNumber = identityData.optString("serialNumber")
                    .trim()
                    .ifBlank { identity.serialNumber },
                firmwareSerial = identityData.optString("firmwareSerial")
                    .trim()
                    .ifBlank { identity.firmwareSerial },
                displayName = identityData.optString("displayName")
                    .trim()
                    .ifBlank { identity.displayName },
                setupCode = identityData.optString("setupCode")
                    .trim()
                    .ifBlank { identity.setupCode }
            ),
            product = DeviceProduct(
                brand = identityData.optString("brand")
                    .trim()
                    .ifBlank { product.brand },
                productId = identityData.optString("productId")
                    .trim()
                    .ifBlank { product.productId },
                productKey = identityData.optString("productKey")
                    .trim()
                    .ifBlank { product.productKey },
                family = DeviceFamily.fromWire(familyRaw),
                familyRaw = familyRaw,
                line = identityData.optString("line")
                    .trim()
                    .ifBlank { product.line },
                model = identityData.optString("model")
                    .trim()
                    .ifBlank { product.model },
                displayName = identityData.optString("displayName")
                    .trim()
                    .ifBlank { product.displayName },
                skuId = identityData.optString("skuId")
                    .trim()
                    .ifBlank { product.skuId },
                skuCode = identityData.optString("skuCode")
                    .trim()
                    .ifBlank { product.skuCode },
                setupCode = identityData.optString("setupCode")
                    .trim()
                    .ifBlank { product.setupCode },
                hardwareRevision = identityData.optString("hardwareRevision")
                    .trim()
                    .ifBlank { product.hardwareRevision }
            ),
            firmwareVersion = identityData.optString("firmwareVersion")
                .trim()
                .ifBlank { firmwareVersion },
            firmwareBuild = identityData.optString("firmwareBuild")
                .trim()
                .ifBlank { firmwareBuild },
            apiVersion = identityData.optString("apiVersion")
                .trim()
                .ifBlank { apiVersion },
            protocolVersion = identityData.optString("protocolVersion")
                .trim()
                .ifBlank { protocolVersion },
            endpoint = endpoint.withRuntimeMetadata(runtime),
            capabilities = capabilitiesJson.toDeviceCapabilities(),
            limits = limitsJson.toDeviceLimits(),
            supportedFeatures = capabilitiesData.optStringArray("supportedFeatures")
                .ifEmpty { supportedFeatures },
            supportedScreens = capabilitiesData.optStringArray("supportedScreens")
                .ifEmpty { supportedScreens }
        )
    }

    private fun DeviceRuntimeEndpoint.withRuntimeMetadata(
        runtime: JSONObject
    ): DeviceRuntimeEndpoint {
        return copy(
            runtimeTransport = runtime.optString("transport")
                .trim()
                .ifBlank { runtimeTransport },
            wsPath = runtime.optString("wsPath")
                .trim()
                .ifBlank { wsPath },
            wsPort = runtime.optInt("wsPort", wsPort),
            wsProtocol = runtime.optString("wsSchema")
                .trim()
                .ifBlank { wsProtocol },
            wsProtocolVersion = runtime.optInt("wsProtocolVersion", wsProtocolVersion)
        )
    }

    private fun JSONObject.toDeviceCapabilities(): DeviceCapabilities {
        return DeviceCapabilities(
            light = optBoolean("light", false),
            manualLight = optBoolean("manualLight", false),
            lightProgram = optBoolean("lightProgram", false),
            lightPresets = optBoolean("lightPresets", false),
            lightSimulation = optBoolean("lightSimulation", false),
            fan = optBoolean("fan", false),
            cooling = optBoolean("cooling", false),
            temperature = optBoolean("temperature", false),
            standaloneTimer = optBoolean("standaloneTimer", false),
            dosing = optBoolean("dosing", false),
            timeSync = optBoolean("timeSync", false),
            ota = optBoolean("ota", false)
        )
    }

    private fun JSONObject.toDeviceLimits(): DeviceLimits {
        val fanOutputCount = optInt("fanOutputCount", 0)

        return DeviceLimits(
            lightChannelCount = optInt("lightChannelCount", 0),
            fanOutputCount = fanOutputCount,
            fanChannelCount = optInt("fanChannelCount", fanOutputCount),
            temperatureSensorCount = optInt("temperatureSensorCount", 0),
            timerChannelCount = optInt("timerChannelCount", 0),
            dosingChannelCount = optInt("dosingChannelCount", 0)
        )
    }

    private fun JSONObject.optStringArray(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return array.toStringList()
    }

    private fun JSONArray.toStringList(): List<String> {
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 12_000L
    }
}
