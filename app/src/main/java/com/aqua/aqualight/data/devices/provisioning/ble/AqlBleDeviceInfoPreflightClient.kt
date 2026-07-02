package com.aqua.aqualight.data.devices.provisioning.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Manual BLE setup preflight.
 *
 * This client performs a read-only DeviceInfo check before the Wi-Fi form is opened.
 * It never writes StartSession and never sends Wi-Fi credentials.
 */
class AqlBleDeviceInfoPreflightClient(
    context: Context
) {

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    suspend fun verifyManualSetup(bleAddress: String): ManualSetupPreflightResult {
        val address = bleAddress.trim()
        if (address.isBlank()) return ManualSetupPreflightResult.Blocked("BLE address is missing. Scan again.")
        if (!hasRequiredPermissions()) return ManualSetupPreflightResult.Blocked("Bluetooth scan/connect permission is required.")
        val adapter = bluetoothManager?.adapter ?: return ManualSetupPreflightResult.Blocked("Bluetooth adapter is unavailable.")
        if (!adapter.isEnabled) return ManualSetupPreflightResult.Blocked("Bluetooth is disabled.")
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrElse {
            return ManualSetupPreflightResult.Blocked("BLE device address is invalid. Scan again.")
        }
        val deviceInfo = withTimeoutOrNull(PREFLIGHT_TIMEOUT_MS) { readDeviceInfo(device) }
            ?: return ManualSetupPreflightResult.Blocked("DeviceInfo verification timed out. Keep setup mode open and scan again.")
        return deviceInfo.fold(
            onSuccess = { info -> validateManualDeviceInfo(info) },
            onFailure = { error -> ManualSetupPreflightResult.Blocked(error.message ?: "DeviceInfo verification failed. Scan again.") }
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun readDeviceInfo(device: BluetoothDevice): Result<DeviceInfo> {
        return suspendCancellableCoroutine { continuation ->
            var completed = false
            var gattRef: BluetoothGatt? = null

            fun finish(result: Result<DeviceInfo>) {
                if (completed) return
                completed = true
                runCatching { gattRef?.disconnect() }
                runCatching { gattRef?.close() }
                if (continuation.isActive) continuation.resume(result)
            }

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Result.failure(IllegalStateException("BLE connection failed with status $status.")))
                        return
                    }
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            if (!gatt.discoverServices()) finish(Result.failure(IllegalStateException("BLE service discovery could not start.")))
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> finish(Result.failure(IllegalStateException("BLE device disconnected before DeviceInfo was read.")))
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Result.failure(IllegalStateException("BLE service discovery failed with status $status.")))
                        return
                    }
                    val service = gatt.getService(SERVICE_UUID)
                    if (service == null) {
                        finish(Result.failure(IllegalStateException("AquaLight provisioning service was not found.")))
                        return
                    }
                    val characteristic = service.getCharacteristic(DEVICE_INFO_UUID)
                    if (characteristic == null) {
                        finish(Result.failure(IllegalStateException("DeviceInfo characteristic was not found.")))
                        return
                    }
                    if (!gatt.readCharacteristic(characteristic)) finish(Result.failure(IllegalStateException("DeviceInfo read could not start.")))
                }

                @Deprecated("Deprecated Android callback")
                override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                    if (characteristic.uuid != DEVICE_INFO_UUID) return
                    handleDeviceInfoBytes(characteristic.value ?: byteArrayOf(), status, ::finish)
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                    if (characteristic.uuid != DEVICE_INFO_UUID) return
                    handleDeviceInfoBytes(value, status, ::finish)
                }
            }

            continuation.invokeOnCancellation {
                runCatching { gattRef?.disconnect() }
                runCatching { gattRef?.close() }
            }

            gattRef = runCatching { device.connectGatt(appContext, false, callback) }.getOrElse { error ->
                finish(Result.failure(error))
                null
            }
            if (gattRef == null && !completed) finish(Result.failure(IllegalStateException("BLE GATT connection could not be opened.")))
        }
    }

    private fun handleDeviceInfoBytes(value: ByteArray, status: Int, finish: (Result<DeviceInfo>) -> Unit) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            finish(Result.failure(IllegalStateException("DeviceInfo read failed with status $status.")))
            return
        }
        val raw = value.toString(Charsets.UTF_8).trim()
        if (raw.isBlank()) {
            finish(Result.failure(IllegalStateException("DeviceInfo payload is empty.")))
            return
        }
        finish(parseDeviceInfo(raw))
    }

    private fun parseDeviceInfo(raw: String): Result<DeviceInfo> {
        return runCatching {
            val json = JSONObject(raw)
            DeviceInfo(
                contractVersion = requiredInt(json, AqlBleProvisioningContract.Json.KEY_CONTRACT_VERSION),
                securityVersion = requiredInt(json, AqlBleProvisioningContract.Json.KEY_SECURITY_VERSION),
                deviceUid = requiredString(json, AqlBleProvisioningContract.Json.KEY_DEVICE_UID),
                serialNumber = json.optString(AqlBleProvisioningContract.Json.KEY_SERIAL_NUMBER).trim(),
                brand = json.optString(AqlBleProvisioningContract.Json.KEY_BRAND).trim(),
                productId = json.optString(AqlBleProvisioningContract.Json.KEY_PRODUCT_ID).trim(),
                productModel = requiredString(json, AqlBleProvisioningContract.Json.KEY_PRODUCT_MODEL),
                displayName = requiredString(json, AqlBleProvisioningContract.Json.KEY_DISPLAY_NAME),
                hardwareRevision = json.optString(AqlBleProvisioningContract.Json.KEY_HARDWARE_REVISION).trim(),
                firmwareVersion = json.optString(AqlBleProvisioningContract.Json.KEY_FIRMWARE_VERSION).trim(),
                bleName = requiredString(json, AqlBleProvisioningContract.Json.KEY_BLE_NAME),
                deviceNonce = requiredString(json, AqlBleProvisioningContract.Json.KEY_DEVICE_NONCE),
                mode = requiredString(json, AqlBleProvisioningContract.Json.KEY_MODE),
                claimRequired = requiredBoolean(json, AqlBleProvisioningContract.Json.KEY_CLAIM_REQUIRED),
                physicalReset = requiredBoolean(json, AqlBleProvisioningContract.Json.KEY_PHYSICAL_RESET),
                sessionMode = requiredString(json, AqlBleProvisioningContract.Json.KEY_SESSION_MODE),
                devicePublicKey = json.optString(AqlBleProvisioningContract.Json.KEY_DEVICE_PUBLIC_KEY).trim()
            )
        }
    }

    private fun validateManualDeviceInfo(info: DeviceInfo): ManualSetupPreflightResult {
        if (info.contractVersion != AqlBleProvisioningContract.CONTRACT_VERSION) {
            return ManualSetupPreflightResult.Blocked("Unsupported DeviceInfo contractVersion: ${info.contractVersion}.")
        }
        if (info.securityVersion != AqlBleProvisioningContract.PROVISIONING_SECURITY_VERSION) {
            return ManualSetupPreflightResult.Blocked("Unsupported DeviceInfo securityVersion: ${info.securityVersion}.")
        }
        if (info.brand.isNotBlank() && !info.brand.equals(AqlBleProvisioningContract.BRAND, ignoreCase = true)) {
            return ManualSetupPreflightResult.Blocked("DeviceInfo brand is not supported: ${info.brand}.")
        }
        if (info.mode == AqlBleProvisioningContract.Status.FACTORY && info.claimRequired && !info.physicalReset) {
            return ManualSetupPreflightResult.QrRequired("First setup requires the secure QR code. Scan the QR label to continue.")
        }
        if (info.mode != AqlBleProvisioningContract.Status.PHYSICAL_RESET) {
            return ManualSetupPreflightResult.Blocked("Manual BLE setup is available only after holding SETUP/RESET for 5 seconds.")
        }
        if (!info.physicalReset) {
            return ManualSetupPreflightResult.Blocked("DeviceInfo physicalReset flag is not active. Hold SETUP/RESET for 5 seconds, then scan again.")
        }
        if (info.claimRequired) {
            return ManualSetupPreflightResult.QrRequired("This device requires QR claim verification. Scan the QR label to continue.")
        }
        if (info.sessionMode != AqlBleProvisioningContract.SessionMode.PHYSICAL_RESET_SECURE || info.devicePublicKey.isBlank()) {
            return ManualSetupPreflightResult.Blocked("Secure physical reset recovery is not ready. Hold SETUP/RESET for 5 seconds, then scan again.")
        }
        return ManualSetupPreflightResult.Allowed(
            deviceUid = info.deviceUid,
            serialNumber = info.serialNumber,
            productId = info.productId,
            productModel = info.productModel,
            displayName = info.displayName,
            hardwareRevision = info.hardwareRevision,
            firmwareVersion = info.firmwareVersion,
            bleName = info.bleName
        )
    }

    private fun requiredString(json: JSONObject, key: String): String {
        val value = json.optString(key).trim()
        require(value.isNotBlank()) { "DeviceInfo field '$key' is missing." }
        return value
    }

    private fun requiredInt(json: JSONObject, key: String): Int {
        require(json.has(key)) { "DeviceInfo field '$key' is missing." }
        return json.getInt(key)
    }

    private fun requiredBoolean(json: JSONObject, key: String): Boolean {
        require(json.has(key)) { "DeviceInfo field '$key' is missing." }
        return json.getBoolean(key)
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) hasPermission(Manifest.permission.BLUETOOTH_CONNECT) else true
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    private data class DeviceInfo(
        val contractVersion: Int,
        val securityVersion: Int,
        val deviceUid: String,
        val serialNumber: String,
        val brand: String,
        val productId: String,
        val productModel: String,
        val displayName: String,
        val hardwareRevision: String,
        val firmwareVersion: String,
        val bleName: String,
        val deviceNonce: String,
        val mode: String,
        val claimRequired: Boolean,
        val physicalReset: Boolean,
        val sessionMode: String,
        val devicePublicKey: String
    )

    private companion object {
        val SERVICE_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.SERVICE_UUID)
        val DEVICE_INFO_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.DEVICE_INFO_UUID)
        const val PREFLIGHT_TIMEOUT_MS = 12_000L
    }
}

sealed interface ManualSetupPreflightResult {
    data class Allowed(
        val deviceUid: String,
        val serialNumber: String,
        val productId: String,
        val productModel: String,
        val displayName: String,
        val hardwareRevision: String,
        val firmwareVersion: String,
        val bleName: String
    ) : ManualSetupPreflightResult

    data class QrRequired(val message: String) : ManualSetupPreflightResult
    data class Blocked(val message: String) : ManualSetupPreflightResult
}
