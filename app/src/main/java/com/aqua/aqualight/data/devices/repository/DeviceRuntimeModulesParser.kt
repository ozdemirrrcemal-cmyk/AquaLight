package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DEVICE_CUSTOM_NAME_MAX_BYTES
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceProductKey
import com.aqua.aqualight.data.devices.model.DeviceProductModel
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModuleStatus
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModules
import org.json.JSONObject

/** Strict parser for the complete commercial `device.status.get.data` contract. */
object DeviceRuntimeModulesParser {

    fun parseDeviceStatus(data: JSONObject): Result<DeviceRuntimeModuleStatus> = runCatching {
        data.requireStatusKeys()
        require(data.requireStatusString("state") == BOOTED_STATE) {
            "device.status.get.data.state must be $BOOTED_STATE."
        }
        require(data.requireStatusBoolean("authenticated")) {
            "device.status.get.data.authenticated must be true after runtime authentication."
        }

        val product = data.requireStatusObject("product")
        product.requireStatusKeys(PRODUCT_KEYS, "device.status.get.data.product")
        val family = requireNotNull(
            DeviceFamily.fromWireExact(product.requireStatusString("family"))
        ) { "device.status.get.data.product.family is not an exact commercial family." }
        val productDisplayName = product.requireStatusString("displayName")

        val device = data.requireStatusObject("device")
        device.requireStatusKeys(DEVICE_KEYS, "device.status.get.data.device")
        require(device.requireStatusString("productDisplayName") == productDisplayName) {
            "device.status.get product/device display names disagree."
        }
        val customName = device.requireStatusOptionalString("customName")
        val effectiveDisplayName = device.requireStatusString("effectiveDisplayName")
        val editable = device.requireStatusBoolean("editable")
        val maxBytesLong = device.requireStatusNonNegativeLong("maxBytes")
        require(maxBytesLong <= Int.MAX_VALUE.toLong()) {
            "device.status.get device.maxBytes is outside the Android integer range."
        }
        val maxBytes = maxBytesLong.toInt()
        require(editable) { "device.status.get must advertise editable device names." }
        require(maxBytes == DEVICE_CUSTOM_NAME_MAX_BYTES) {
            "device.status.get device.maxBytes is incompatible."
        }
        require(customName.toByteArray(Charsets.UTF_8).size <= maxBytes) {
            "device.status.get customName exceeds its UTF-8 byte limit."
        }
        require(effectiveDisplayName == customName.ifBlank { productDisplayName }) {
            "device.status.get effectiveDisplayName violates the fallback contract."
        }

        val runtime = data.requireStatusObject("runtime")
        runtime.requireStatusKeys(RUNTIME_KEYS, "device.status.get.data.runtime")
        runtime.requireExactRuntimeContract()

        val modules = data.requireStatusObject("modules")
        modules.requireStatusKeys(MODULE_KEYS, "device.status.get.data.modules")

        DeviceRuntimeModuleStatus(
            productKey = DeviceProductKey(product.requireStatusString("productKey")),
            family = family,
            model = DeviceProductModel(product.requireStatusString("model")),
            displayName = productDisplayName,
            uptimeMs = data.requireStatusNonNegativeLong("uptimeMs"),
            modules = DeviceRuntimeModules(
                light = modules.requireStatusBoolean("light"),
                cooling = modules.requireStatusBoolean("cooling"),
                temperature = modules.requireStatusBoolean("temperature"),
                timerApi = modules.requireStatusBoolean("timerApi"),
                timerEngine = modules.requireStatusBoolean("timerEngine"),
                dosing = modules.requireStatusBoolean("dosing"),
                network = modules.requireStatusBoolean("network"),
                discovery = modules.requireStatusBoolean("discovery"),
                firmware = modules.requireStatusBoolean("firmware"),
                system = modules.requireStatusBoolean("system")
            ),
            customName = customName,
            effectiveDisplayName = effectiveDisplayName,
            nameEditable = editable,
            customNameMaxBytes = maxBytes
        )
    }
}

private fun JSONObject.requireStatusKeys() {
    requireStatusKeys(STATUS_KEYS, "device.status.get.data")
}

private fun JSONObject.requireStatusKeys(expected: Set<String>, label: String) {
    val actual = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }
    require(actual == expected) {
        "$label keys differ from the commercial contract; expected=$expected actual=$actual"
    }
}

private fun JSONObject.requireStatusObject(key: String): JSONObject {
    require(has(key) && !isNull(key)) { "$key is required." }
    return get(key) as? JSONObject ?: error("$key must be a JSON object.")
}

private fun JSONObject.requireStatusString(key: String): String {
    val value = requireStatusOptionalString(key)
    require(value.isNotEmpty()) { "$key must not be empty." }
    return value
}

private fun JSONObject.requireStatusOptionalString(key: String): String {
    require(has(key) && !isNull(key)) { "$key is required." }
    val value = get(key) as? String ?: error("$key must be a string.")
    require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace())) {
        "$key must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) { "$key must not contain control characters." }
    return value
}

private fun JSONObject.requireStatusBoolean(key: String): Boolean {
    require(has(key) && !isNull(key)) { "$key is required." }
    return get(key) as? Boolean ?: error("$key must be a boolean.")
}

private fun JSONObject.requireStatusNonNegativeLong(key: String): Long {
    require(has(key) && !isNull(key)) { "$key is required." }
    val value = get(key) as? Number ?: error("$key must be an integer.")
    val asDouble = value.toDouble()
    val asLong = value.toLong()
    require(asDouble.isFinite() && asDouble == asLong.toDouble()) { "$key must be an integer." }
    require(asLong >= 0L) { "$key must not be negative." }
    return asLong
}

private fun JSONObject.requireExactRuntimeContract() {
    require(requireStatusString("transport") == STATUS_TRANSPORT) {
        "device.status.get.data.runtime.transport is incompatible."
    }
    require(requireStatusString("wsSchema") == AqlWsContract.SCHEMA) {
        "device.status.get.data.runtime.wsSchema is incompatible."
    }
    require(requireStatusString("wsPath") == AqlWsContract.DEFAULT_PATH) {
        "device.status.get.data.runtime.wsPath is incompatible."
    }
    require(requireStatusNonNegativeLong("wsPort") == AqlWsContract.DEFAULT_PORT.toLong()) {
        "device.status.get.data.runtime.wsPort is incompatible."
    }
}

private const val BOOTED_STATE = "booted"
private const val STATUS_TRANSPORT = "websocket"
private val STATUS_KEYS = setOf(
    "state", "authenticated", "uptimeMs", "device", "product", "runtime", "modules"
)
private val DEVICE_KEYS = setOf(
    "productDisplayName", "customName", "effectiveDisplayName", "editable", "maxBytes"
)
private val PRODUCT_KEYS = setOf("productKey", "family", "model", "displayName")
private val RUNTIME_KEYS = setOf("transport", "wsSchema", "wsPath", "wsPort")
private val MODULE_KEYS = setOf(
    "light", "cooling", "temperature", "timerApi", "timerEngine", "dosing",
    "network", "discovery", "firmware", "system"
)
