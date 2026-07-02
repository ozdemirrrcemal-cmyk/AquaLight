package com.aqua.aqualight.data.devices.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private const val AQL_KNOWN_DEVICES_DATASTORE_NAME = "aql_known_devices_v2"
private val Context.aqlKnownDevicesDataStore by preferencesDataStore(
    name = AQL_KNOWN_DEVICES_DATASTORE_NAME
)

/**
 * Durable non-secret known-device store.
 *
 * WebSocket tokens stay in DeviceCredentialStore. This store only keeps identity/product/endpoint
 * and resolved runtime metadata required to show provisioned devices after app restart and reconnect
 * runtime later. Preferences DataStore is used as the single durable source for this small metadata
 * document; UI code still consumes DevicesRepository, never this store directly.
 */
class DeviceKnownStore(
    context: Context
) {

    private val dataStore = context.applicationContext.aqlKnownDevicesDataStore

    suspend fun loadSnapshots(): List<DeviceSnapshot> {
        val raw = preferences()[KEY_DEVICES].orEmpty().ifBlank { "[]" }
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())

        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                jsonToSnapshot(json)?.let { snapshot ->
                    add(snapshot)
                }
            }
        }
    }

    suspend fun saveSnapshot(snapshot: DeviceSnapshot) {
        val current = loadSnapshots()
            .associateBy { known -> known.deviceUid.value }
            .toMutableMap()

        current[snapshot.deviceUid.value] = snapshot

        saveAll(current.values)
    }

    suspend fun saveSnapshots(snapshots: Iterable<DeviceSnapshot>) {
        val current = loadSnapshots()
            .associateBy { known -> known.deviceUid.value }
            .toMutableMap()

        snapshots.forEach { snapshot ->
            current[snapshot.deviceUid.value] = snapshot
        }

        saveAll(current.values)
    }

    suspend fun remove(deviceUid: DeviceUid) {
        val remaining = loadSnapshots()
            .filterNot { snapshot -> snapshot.deviceUid == deviceUid }

        saveAll(remaining)
    }

    suspend fun ignoreDevice(deviceUid: DeviceUid) {
        val ignored = ignoredDeviceUidValues().toMutableSet()
        ignored += deviceUid.value
        saveIgnoredDeviceUidValues(ignored)
    }

    suspend fun allowDevice(deviceUid: DeviceUid) {
        val ignored = ignoredDeviceUidValues()
        if (deviceUid.value !in ignored) return
        saveIgnoredDeviceUidValues(ignored - deviceUid.value)
    }

    suspend fun isIgnored(deviceUid: DeviceUid): Boolean {
        return deviceUid.value in ignoredDeviceUidValues()
    }

    suspend fun ignoredDeviceUidValues(): Set<String> {
        return preferences()[KEY_IGNORED_DEVICE_UIDS]
            .orEmpty()
            .filter { value -> value.isNotBlank() }
            .toSet()
    }

    suspend fun clearIgnoredDevices() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_IGNORED_DEVICE_UIDS)
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_DEVICES)
        }
    }

    private suspend fun saveIgnoredDeviceUidValues(values: Set<String>) {
        dataStore.edit { preferences ->
            preferences[KEY_IGNORED_DEVICE_UIDS] = values
                .filter { value -> value.isNotBlank() }
                .toSet()
        }
    }

    private suspend fun saveAll(snapshots: Iterable<DeviceSnapshot>) {
        val array = JSONArray()

        snapshots
            .sortedWith(
                compareBy<DeviceSnapshot> { snapshot -> snapshot.title.lowercase() }
                    .thenBy { snapshot -> snapshot.deviceUid.value }
            )
            .forEach { snapshot ->
                array.put(snapshotToJson(snapshot))
            }

        dataStore.edit { preferences ->
            preferences[KEY_DEVICES] = array.toString()
        }
    }

    private suspend fun preferences() = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .first()

    private fun snapshotToJson(snapshot: DeviceSnapshot): JSONObject {
        return JSONObject()
            .put("identity", identityToJson(snapshot.identity))
            .put("product", productToJson(snapshot.product))
            .put("firmwareVersion", snapshot.firmwareVersion)
            .put("firmwareBuild", snapshot.firmwareBuild)
            .put("apiVersion", snapshot.apiVersion)
            .put("protocolVersion", snapshot.protocolVersion)
            .put("endpoint", endpointToJson(snapshot.endpoint))
            .put("capabilities", capabilitiesToJson(snapshot.capabilities))
            .put("limits", limitsToJson(snapshot.limits))
            .put("supportedFeatures", JSONArray(snapshot.supportedFeatures))
            .put("supportedScreens", JSONArray(snapshot.supportedScreens))
            .put("modules", JSONArray(snapshot.modules))
            .put("lastSeenAtMillis", snapshot.lastSeenAtMillis)
    }

    private fun identityToJson(identity: DeviceIdentity): JSONObject {
        return JSONObject()
            .put("uid", identity.uid.value)
            .put("shortId", identity.shortId)
            .put("chipId", identity.chipId)
            .put("espChipId", identity.espChipId)
            .put("efuseMac", identity.efuseMac)
            .put("macAddress", identity.macAddress)
            .put("serialNumber", identity.serialNumber)
            .put("firmwareSerial", identity.firmwareSerial)
            .put("displayName", identity.displayName)
            .put("customName", identity.customName)
            .put("setupCode", identity.setupCode)
            .put("setupSsid", identity.setupSsid)
    }

    private fun productToJson(product: DeviceProduct): JSONObject {
        return JSONObject()
            .put("brand", product.brand)
            .put("productId", product.productId)
            .put("productKey", product.productKey)
            .put("family", product.family.wireValue)
            .put("familyRaw", product.familyRaw)
            .put("line", product.line)
            .put("model", product.model)
            .put("displayName", product.displayName)
            .put("skuId", product.skuId)
            .put("skuCode", product.skuCode)
            .put("setupCode", product.setupCode)
            .put("hardwareRevision", product.hardwareRevision)
    }

    private fun endpointToJson(endpoint: DeviceRuntimeEndpoint): JSONObject {
        return JSONObject()
            .put("ip", endpoint.ip)
            .put("wifiMode", endpoint.wifiMode)
            .put("wifiConnected", endpoint.wifiConnected)
            .put("setupApActive", endpoint.setupApActive)
            .put("runtimeTransport", endpoint.runtimeTransport)
            .put("wsPort", endpoint.wsPort)
            .put("wsPath", endpoint.wsPath)
            .put("wsProtocol", endpoint.wsProtocol)
            .put("wsProtocolVersion", endpoint.wsProtocolVersion)
            .put("discoveryPort", endpoint.discoveryPort)
    }

    private fun capabilitiesToJson(capabilities: DeviceCapabilities): JSONObject {
        return JSONObject()
            .put("light", capabilities.light)
            .put("manualLight", capabilities.manualLight)
            .put("lightProgram", capabilities.lightProgram)
            .put("lightPresets", capabilities.lightPresets)
            .put("lightSimulation", capabilities.lightSimulation)
            .put("fan", capabilities.fan)
            .put("cooling", capabilities.cooling)
            .put("temperature", capabilities.temperature)
            .put("standaloneTimer", capabilities.standaloneTimer)
            .put("dosing", capabilities.dosing)
            .put("timeSync", capabilities.timeSync)
            .put("ota", capabilities.ota)
    }

    private fun limitsToJson(limits: DeviceLimits): JSONObject {
        return JSONObject()
            .put("lightChannelCount", limits.lightChannelCount)
            .put("fanOutputCount", limits.fanOutputCount)
            .put("temperatureSensorCount", limits.temperatureSensorCount)
            .put("timerChannelCount", limits.timerChannelCount)
            .put("dosingChannelCount", limits.dosingChannelCount)
    }

    private fun jsonToCapabilities(json: JSONObject): DeviceCapabilities {
        return DeviceCapabilities(
            light = json.optBoolean("light", false),
            manualLight = json.optBoolean("manualLight", false),
            lightProgram = json.optBoolean("lightProgram", false),
            lightPresets = json.optBoolean("lightPresets", false),
            lightSimulation = json.optBoolean("lightSimulation", false),
            fan = json.optBoolean("fan", false),
            cooling = json.optBoolean("cooling", false),
            temperature = json.optBoolean("temperature", false),
            standaloneTimer = json.optBoolean("standaloneTimer", false),
            dosing = json.optBoolean("dosing", false),
            timeSync = json.optBoolean("timeSync", false),
            ota = json.optBoolean("ota", false)
        )
    }

    private fun jsonToLimits(json: JSONObject): DeviceLimits {
        return DeviceLimits(
            lightChannelCount = json.optInt("lightChannelCount", 0),
            fanOutputCount = json.optInt("fanOutputCount", 0),
            temperatureSensorCount = json.optInt("temperatureSensorCount", 0),
            timerChannelCount = json.optInt("timerChannelCount", 0),
            dosingChannelCount = json.optInt("dosingChannelCount", 0)
        )
    }

    private fun jsonToSnapshot(json: JSONObject): DeviceSnapshot? {
        return runCatching {
            val identityJson = json.getJSONObject("identity")
            val productJson = json.optJSONObject("product") ?: JSONObject()
            val endpointJson = json.optJSONObject("endpoint") ?: JSONObject()
            val capabilitiesJson = json.optJSONObject("capabilities") ?: JSONObject()
            val limitsJson = json.optJSONObject("limits") ?: JSONObject()

            val identity = DeviceIdentity(
                uid = DeviceUid(identityJson.getString("uid")),
                shortId = identityJson.optString("shortId"),
                chipId = identityJson.optString("chipId"),
                espChipId = identityJson.optString("espChipId"),
                efuseMac = identityJson.optString("efuseMac"),
                macAddress = identityJson.optString("macAddress"),
                serialNumber = identityJson.optString("serialNumber"),
                firmwareSerial = identityJson.optString("firmwareSerial"),
                displayName = identityJson.optString("displayName"),
                customName = identityJson.optString("customName"),
                setupCode = identityJson.optString("setupCode"),
                setupSsid = identityJson.optString("setupSsid")
            )

            val familyRaw = productJson.optString("familyRaw")
                .ifBlank { productJson.optString("family") }

            DeviceSnapshot(
                identity = identity,
                product = DeviceProduct(
                    brand = productJson.optString("brand"),
                    productId = productJson.optString("productId"),
                    productKey = productJson.optString("productKey"),
                    family = DeviceFamily.fromWire(familyRaw),
                    familyRaw = familyRaw,
                    line = productJson.optString("line"),
                    model = productJson.optString("model"),
                    displayName = productJson.optString("displayName"),
                    skuId = productJson.optString("skuId"),
                    skuCode = productJson.optString("skuCode"),
                    setupCode = productJson.optString("setupCode"),
                    hardwareRevision = productJson.optString("hardwareRevision")
                ),
                firmwareVersion = json.optString("firmwareVersion"),
                firmwareBuild = json.optString("firmwareBuild"),
                apiVersion = json.optString("apiVersion"),
                protocolVersion = json.optString("protocolVersion"),
                endpoint = DeviceRuntimeEndpoint(
                    ip = endpointJson.optString("ip"),
                    wifiMode = endpointJson.optString("wifiMode"),
                    wifiConnected = endpointJson.optBoolean("wifiConnected", false),
                    setupApActive = endpointJson.optBoolean("setupApActive", false),
                    runtimeTransport = endpointJson.optString("runtimeTransport"),
                    wsPort = endpointJson.optInt("wsPort", 0),
                    wsPath = endpointJson.optString("wsPath", AqlWsContract.DEFAULT_PATH)
                        .ifBlank { AqlWsContract.DEFAULT_PATH },
                    wsProtocol = endpointJson.optString("wsProtocol", AqlWsContract.DEFAULT_PROTOCOL)
                        .ifBlank { AqlWsContract.DEFAULT_PROTOCOL },
                    wsProtocolVersion = endpointJson.optInt("wsProtocolVersion", 0),
                    discoveryPort = endpointJson.optInt("discoveryPort", 0)
                ),
                capabilities = jsonToCapabilities(capabilitiesJson),
                limits = jsonToLimits(limitsJson),
                supportedFeatures = json.optStringArray("supportedFeatures"),
                supportedScreens = json.optStringArray("supportedScreens"),
                modules = json.optStringArray("modules"),
                connectionState = DeviceConnectionState(
                    lastErrorMessage = null
                ),
                lastSeenAtMillis = json.optLong("lastSeenAtMillis", 0L)
            )
        }.getOrNull()
    }

    private fun JSONObject.optStringArray(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
    }

    private companion object {
        val KEY_DEVICES = stringPreferencesKey("devices")
        val KEY_IGNORED_DEVICE_UIDS = stringSetPreferencesKey("ignoredDeviceUids")
    }
}
