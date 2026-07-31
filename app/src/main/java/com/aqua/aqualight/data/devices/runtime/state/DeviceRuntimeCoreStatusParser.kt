package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.runtime.parsing.optionalBoolean
import com.aqua.aqualight.data.devices.runtime.parsing.optionalNonNegativeLong
import com.aqua.aqualight.data.devices.runtime.parsing.requireAllowedAndRequiredKeys
import com.aqua.aqualight.data.devices.runtime.parsing.requireExactKeys
import com.aqua.aqualight.data.devices.runtime.parsing.requiredBoolean
import com.aqua.aqualight.data.devices.runtime.parsing.requiredInt
import com.aqua.aqualight.data.devices.runtime.parsing.requiredNonNegativeLong
import com.aqua.aqualight.data.devices.runtime.parsing.requiredObject
import com.aqua.aqualight.data.devices.runtime.parsing.requiredPort
import com.aqua.aqualight.data.devices.runtime.parsing.requiredString
import com.aqua.aqualight.data.devices.runtime.parsing.requiredStringAllowEmpty
import org.json.JSONObject

object DeviceRuntimeCoreStatusParser {

    fun parseDeviceStatus(data: JSONObject): Result<DeviceRuntimeDeviceStatus> = runCatching {
        data.requireExactKeys(DEVICE_STATUS_KEYS, "device.status.get.data")
        val device = data.requiredObject("device")
        val product = data.requiredObject("product")
        val runtime = data.requiredObject("runtime")
        val modules = data.requiredObject("modules")

        device.requireExactKeys(NAME_STATUS_KEYS, "device.status.get.data.device")
        product.requireExactKeys(PRODUCT_STATUS_KEYS, "device.status.get.data.product")
        runtime.requireExactKeys(DEVICE_RUNTIME_KEYS, "device.status.get.data.runtime")
        modules.requireExactKeys(MODULE_STATUS_KEYS, "device.status.get.data.modules")

        DeviceRuntimeDeviceStatus(
            state = data.requiredString("state").also { require(it == BOOTED_STATE) },
            authenticated = data.requiredBoolean("authenticated").also { require(it) },
            uptimeMs = data.requiredNonNegativeLong("uptimeMs"),
            device = parseNameStatusExact(device),
            product = DeviceRuntimeProductStatus(
                productKey = product.requiredString("productKey"),
                family = product.requiredString("family"),
                model = product.requiredString("model"),
                displayName = product.requiredString("displayName")
            ),
            runtime = DeviceRuntimeTransportStatus(
                transport = runtime.requiredString("transport").also {
                    require(it == WEBSOCKET_TRANSPORT)
                },
                wsSchema = runtime.requiredString("wsSchema").also {
                    require(it == AqlWsContract.SCHEMA)
                },
                wsPath = runtime.requiredString("wsPath").also {
                    require(it == AqlWsContract.DEFAULT_PATH)
                },
                wsPort = runtime.requiredPort("wsPort")
            ),
            modules = DeviceRuntimeCompiledModules(
                light = modules.requiredBoolean("light"),
                cooling = modules.requiredBoolean("cooling"),
                temperature = modules.requiredBoolean("temperature"),
                timerApi = modules.requiredBoolean("timerApi"),
                timerEngine = modules.requiredBoolean("timerEngine"),
                dosing = modules.requiredBoolean("dosing"),
                network = modules.requiredBoolean("network"),
                discovery = modules.requiredBoolean("discovery"),
                firmware = modules.requiredBoolean("firmware"),
                system = modules.requiredBoolean("system")
            )
        )
    }

    fun parseNameStatus(data: JSONObject): Result<DeviceRuntimeNameStatus> = runCatching {
        data.requireExactKeys(NAME_STATUS_KEYS, "device name status")
        parseNameStatusExact(data)
    }

    fun parseSecurityStatus(data: JSONObject): Result<DeviceRuntimeSecurityStatus> = runCatching {
        data.requireAllowedAndRequiredKeys(
            allowed = SECURITY_STATUS_KEYS + SECURITY_DYNAMIC_KEYS,
            required = SECURITY_STATUS_KEYS,
            label = "security.status.get.data"
        )

        DeviceRuntimeSecurityStatus(
            tokenGateEnabled = data.requiredBoolean("tokenGateEnabled"),
            dynamicPairingEnabled = data.requiredBoolean("dynamicPairingEnabled"),
            paired = data.requiredBoolean("paired"),
            runtimeTransport = data.requiredString("runtimeTransport").also {
                require(it == WEBSOCKET_TRANSPORT)
            },
            runtimeAuthMessageType = data.requiredString("runtimeAuthMessageType").also {
                require(it == AqlWsContract.TYPE_AUTH)
            },
            runtimeAuthScheme = data.requiredString("runtimeAuthScheme").also {
                require(it == AqlWsContract.AUTH_SCHEME)
            },
            runtimeCredentialSerialized = data.requiredBoolean("runtimeCredentialSerialized")
                .also { require(!it) },
            runtimeReplayProtection = data.requiredString("runtimeReplayProtection"),
            initialOwnershipTransport = data.requiredString("initialOwnershipTransport"),
            firstTokenTransport = data.requiredString("firstTokenTransport"),
            webSocketPairingCommand = data.requiredString("webSocketPairingCommand"),
            webSocketPairingCommandAuth = data.requiredString("webSocketPairingCommandAuth"),
            webSocketPairingPurpose = data.requiredString("webSocketPairingPurpose"),
            publicFirstPairingSupported = data.requiredBoolean("publicFirstPairingSupported")
                .also { require(!it) },
            mutatingCommandsRequireAuth = data.requiredBoolean("mutatingCommandsRequireAuth"),
            tokenReturnedByStatus = data.requiredBoolean("tokenReturnedByStatus")
                .also { require(!it) },
            tokenStorageBackend = data.requiredString("tokenStorageBackend"),
            tokenStorageFormat = data.requiredString("tokenStorageFormat"),
            tokenStoredPlaintext = data.requiredBoolean("tokenStoredPlaintext")
                .also { require(!it) },
            tokenFormat = data.requiredString("tokenFormat"),
            tokenHexLength = data.requiredInt("tokenHexLength").also {
                require(it == TOKEN_HEX_LENGTH)
            },
            deviceUid = data.requiredString("deviceUid"),
            shortId = data.requiredString("shortId"),
            serialNumber = data.requiredString("serialNumber"),
            tokenVersion = data.optionalNonNegativeLong("tokenVersion"),
            pairedAtMs = data.optionalNonNegativeLong("pairedAtMs"),
            lastRotatedAtMs = data.optionalNonNegativeLong("lastRotatedAtMs"),
            provisioningTokenPending = data.optionalBoolean("provisioningTokenPending")
        )
    }

    private fun parseNameStatusExact(data: JSONObject): DeviceRuntimeNameStatus =
        DeviceRuntimeNameStatus(
            productDisplayName = data.requiredString("productDisplayName"),
            customName = data.requiredStringAllowEmpty("customName"),
            effectiveDisplayName = data.requiredString("effectiveDisplayName"),
            editable = data.requiredBoolean("editable"),
            maxBytes = data.requiredInt("maxBytes").also {
                require(it in MIN_DEVICE_NAME_BYTES..MAX_DEVICE_NAME_BYTES)
            }
        )

    private val DEVICE_STATUS_KEYS = setOf(
        "state", "authenticated", "uptimeMs", "device", "product", "runtime", "modules"
    )
    private val NAME_STATUS_KEYS = setOf(
        "productDisplayName", "customName", "effectiveDisplayName", "editable", "maxBytes"
    )
    private val PRODUCT_STATUS_KEYS = setOf("productKey", "family", "model", "displayName")
    private val DEVICE_RUNTIME_KEYS = setOf("transport", "wsSchema", "wsPath", "wsPort")
    private val MODULE_STATUS_KEYS = setOf(
        "light", "cooling", "temperature", "timerApi", "timerEngine", "dosing",
        "network", "discovery", "firmware", "system"
    )
    private val SECURITY_STATUS_KEYS = setOf(
        "tokenGateEnabled", "dynamicPairingEnabled", "paired", "runtimeTransport",
        "runtimeAuthMessageType", "runtimeAuthScheme", "runtimeCredentialSerialized",
        "runtimeReplayProtection", "initialOwnershipTransport", "firstTokenTransport",
        "webSocketPairingCommand", "webSocketPairingCommandAuth", "webSocketPairingPurpose",
        "publicFirstPairingSupported", "mutatingCommandsRequireAuth", "tokenReturnedByStatus",
        "tokenStorageBackend", "tokenStorageFormat", "tokenStoredPlaintext", "tokenFormat",
        "tokenHexLength", "deviceUid", "shortId", "serialNumber"
    )
    private val SECURITY_DYNAMIC_KEYS = setOf(
        "tokenVersion", "pairedAtMs", "lastRotatedAtMs", "provisioningTokenPending"
    )

    private const val BOOTED_STATE = "booted"
    private const val WEBSOCKET_TRANSPORT = "websocket"
    private const val TOKEN_HEX_LENGTH = 64
    private const val MIN_DEVICE_NAME_BYTES = 1
    private const val MAX_DEVICE_NAME_BYTES = 256
}
