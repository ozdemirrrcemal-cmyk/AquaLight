package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareStatusParser
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Canonical reducer for metadata reported through the WebSocket runtime.
 *
 * Runtime responses are authoritative for firmware identity, hardware revision, capabilities and
 * feature/screen lists. UDP discovery is intentionally excluded from this reducer because UDP only
 * proves LAN presence and endpoint location.
 */
class DeviceRuntimeMetadataReducer {

    fun reduce(
        snapshot: DeviceSnapshot,
        response: AqlWsIncomingMessage.Response
    ): DeviceSnapshot? {
        if (!response.ok) return null

        val data = response.data
        return when {
            response.isDeviceAction(AqlWsContract.ACTION_DEVICE_IDENTITY_GET) ->
                applyDeviceIdentity(snapshot, data)

            response.isDeviceAction(AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET) ->
                applyDeviceCapabilities(snapshot, data)

            response.isFirmwareAction(AqlWsContract.ACTION_FIRMWARE_STATUS_GET) ->
                applyFirmwareStatus(snapshot, data)

            else -> null
        }
    }

    fun applyDeviceIdentity(
        snapshot: DeviceSnapshot,
        identityData: JSONObject
    ): DeviceSnapshot {
        val runtime = identityData.optJSONObject("runtime") ?: JSONObject()
        val familyRaw = identityData.optString("family")
            .trim()
            .ifBlank {
                snapshot.product.familyRaw.ifBlank { snapshot.product.family.wireValue }
            }

        val reportedDeviceUid = identityData.optString("deviceUid").trim()
        require(reportedDeviceUid.isBlank() || reportedDeviceUid == snapshot.deviceUid.value) {
            "Runtime identity deviceUid does not match registered device uid."
        }

        val reportedFamily = DeviceFamily.fromWire(familyRaw)
        val family = when {
            reportedFamily != DeviceFamily.UNKNOWN -> reportedFamily
            snapshot.product.family != DeviceFamily.UNKNOWN -> snapshot.product.family
            else -> DeviceFamily.UNKNOWN
        }

        return snapshot.copy(
            identity = snapshot.identity.copy(
                shortId = identityData.optString("shortId").trim()
                    .ifBlank { snapshot.identity.shortId },
                macAddress = identityData.optString("macAddress").trim()
                    .ifBlank { snapshot.identity.macAddress },
                serialNumber = identityData.optString("serialNumber").trim()
                    .ifBlank { snapshot.identity.serialNumber },
                firmwareSerial = identityData.optString("firmwareSerial").trim()
                    .ifBlank { snapshot.identity.firmwareSerial },
                displayName = identityData.optString("displayName").trim()
                    .ifBlank { snapshot.identity.displayName },
                setupCode = identityData.optString("setupCode").trim()
                    .ifBlank { snapshot.identity.setupCode }
            ),
            product = DeviceProduct(
                brand = identityData.optString("brand").trim()
                    .ifBlank { snapshot.product.brand },
                productId = identityData.optString("productId").trim()
                    .ifBlank { snapshot.product.productId },
                productKey = identityData.optString("productKey").trim()
                    .ifBlank { snapshot.product.productKey },
                family = family,
                familyRaw = familyRaw,
                line = identityData.optString("line").trim()
                    .ifBlank { snapshot.product.line },
                model = identityData.optString("model").trim()
                    .ifBlank { snapshot.product.model },
                displayName = identityData.optString("displayName").trim()
                    .ifBlank { snapshot.product.displayName },
                skuId = identityData.optString("skuId").trim()
                    .ifBlank { snapshot.product.skuId },
                skuCode = identityData.optString("skuCode").trim()
                    .ifBlank { snapshot.product.skuCode },
                setupCode = identityData.optString("setupCode").trim()
                    .ifBlank { snapshot.product.setupCode },
                hardwareRevision = identityData.optString("hardwareRevision").trim()
                    .ifBlank { snapshot.product.hardwareRevision }
            ),
            firmwareVersion = identityData.optString("firmwareVersion").trim()
                .ifBlank { snapshot.firmwareVersion },
            firmwareBuild = identityData.optString("firmwareBuild").trim()
                .ifBlank { snapshot.firmwareBuild },
            apiVersion = identityData.optString("apiVersion").trim()
                .ifBlank { snapshot.apiVersion },
            protocolVersion = identityData.optString("protocolVersion").trim()
                .ifBlank { snapshot.protocolVersion },
            endpoint = snapshot.endpoint.withRuntimeMetadata(runtime)
        )
    }

    fun applyDeviceCapabilities(
        snapshot: DeviceSnapshot,
        capabilitiesData: JSONObject
    ): DeviceSnapshot {
        val capabilitiesJson = capabilitiesData.optJSONObject("capabilities") ?: JSONObject()
        val limitsJson = capabilitiesData.optJSONObject("limits") ?: JSONObject()

        return snapshot.copy(
            capabilities = capabilitiesJson.toDeviceCapabilities(snapshot.capabilities),
            limits = limitsJson.toDeviceLimits(snapshot.limits),
            supportedFeatures = capabilitiesData.optStringArray("supportedFeatures")
                .ifEmpty { snapshot.supportedFeatures },
            supportedScreens = capabilitiesData.optStringArray("supportedScreens")
                .ifEmpty { snapshot.supportedScreens },
            modules = capabilitiesData.optStringArray("modules")
                .ifEmpty { snapshot.modules }
        )
    }

    fun applyFirmwareStatus(
        snapshot: DeviceSnapshot,
        firmwareData: JSONObject
    ): DeviceSnapshot {
        val status = DeviceFirmwareStatusParser.parseFirmwareStatus(firmwareData)
        val familyRaw = status.family.ifBlank { snapshot.product.familyRaw }
        val reportedFamily = if (status.family.isNotBlank()) {
            DeviceFamily.fromWire(status.family)
        } else {
            DeviceFamily.UNKNOWN
        }
        val family = when {
            reportedFamily != DeviceFamily.UNKNOWN -> reportedFamily
            snapshot.product.family != DeviceFamily.UNKNOWN -> snapshot.product.family
            else -> DeviceFamily.fromWire(familyRaw)
        }

        return snapshot.copy(
            product = snapshot.product.copy(
                productKey = status.productKey.ifBlank { snapshot.product.productKey },
                productId = status.productId.ifBlank { snapshot.product.productId },
                family = family,
                familyRaw = familyRaw,
                model = status.model.ifBlank { snapshot.product.model },
                displayName = status.displayName.ifBlank { snapshot.product.displayName },
                skuCode = status.skuCode.ifBlank { snapshot.product.skuCode },
                hardwareRevision = status.hardwareRevision.ifBlank { snapshot.product.hardwareRevision }
            ),
            firmwareVersion = status.version.ifBlank { snapshot.firmwareVersion },
            firmwareBuild = status.build.ifBlank { snapshot.firmwareBuild },
            capabilities = if (status.otaSupported) {
                snapshot.capabilities.copy(ota = true)
            } else {
                snapshot.capabilities
            }
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
                .ifBlank { runtime.optString("wsProtocol").trim() }
                .ifBlank { wsProtocol },
            wsProtocolVersion = runtime.optInt("wsProtocolVersion", wsProtocolVersion)
        )
    }

    private fun JSONObject.toDeviceCapabilities(previous: DeviceCapabilities): DeviceCapabilities {
        return previous.copy(
            light = optBooleanOrPrevious("light", previous.light),
            manualLight = optBooleanOrPrevious("manualLight", previous.manualLight),
            lightProgram = optBooleanOrPrevious("lightProgram", previous.lightProgram),
            lightPresets = optBooleanOrPrevious("lightPresets", previous.lightPresets),
            lightSimulation = optBooleanOrPrevious("lightSimulation", previous.lightSimulation),
            fan = optBooleanOrPrevious("fan", previous.fan),
            cooling = optBooleanOrPrevious("cooling", previous.cooling),
            temperature = optBooleanOrPrevious("temperature", previous.temperature),
            standaloneTimer = optBooleanOrPrevious("standaloneTimer", previous.standaloneTimer),
            dosing = optBooleanOrPrevious("dosing", previous.dosing),
            timeSync = optBooleanOrPrevious("timeSync", previous.timeSync),
            ota = optBooleanOrPrevious("ota", previous.ota)
        )
    }

    private fun JSONObject.toDeviceLimits(previous: DeviceLimits): DeviceLimits {
        return previous.copy(
            lightChannelCount = optIntOrPrevious("lightChannelCount", previous.lightChannelCount),
            fanOutputCount = optIntOrPrevious("fanOutputCount", previous.fanOutputCount),
            temperatureSensorCount = optIntOrPrevious("temperatureSensorCount", previous.temperatureSensorCount),
            timerChannelCount = optIntOrPrevious("timerChannelCount", previous.timerChannelCount),
            dosingChannelCount = optIntOrPrevious("dosingChannelCount", previous.dosingChannelCount)
        )
    }

    private fun JSONObject.optBooleanOrPrevious(
        key: String,
        previous: Boolean
    ): Boolean {
        return if (has(key)) optBoolean(key, previous) else previous
    }

    private fun JSONObject.optIntOrPrevious(
        key: String,
        previous: Int
    ): Int {
        return if (has(key)) optInt(key, previous) else previous
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

    private fun AqlWsIncomingMessage.Response.isDeviceAction(actionName: String): Boolean {
        return module == AqlWsContract.MODULE_DEVICE && action == actionName
    }

    private fun AqlWsIncomingMessage.Response.isFirmwareAction(actionName: String): Boolean {
        return module == AqlWsContract.MODULE_FIRMWARE && action == actionName
    }
}
