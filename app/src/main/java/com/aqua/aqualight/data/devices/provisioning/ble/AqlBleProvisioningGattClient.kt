package com.aqua.aqualight.data.devices.provisioning.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningStatus
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

class AqlBleProvisioningGattClient(
    context: Context,
    private val codec: AqlBleProvisioningMessageCodec = AqlBleProvisioningMessageCodec()
) {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val _events = MutableSharedFlow<AqlBleProvisioningGattEvent>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY
    )
    val events: SharedFlow<AqlBleProvisioningGattEvent> = _events.asSharedFlow()

    @Volatile
    private var activeGatt: BluetoothGatt? = null

    @Volatile
    private var activeDraft: AqlProvisioningDraft? = null

    @Volatile
    private var deviceInfoCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var startSessionCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var wifiCredentialsCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var provisioningStatusCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var runtimeEndpointCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var negotiatedMtu = DEFAULT_ATT_MTU

    @Volatile
    private var deviceInfoVerified = false

    @Volatile
    private var statusNotificationsEnabled = false

    @Volatile
    private var runtimeNotificationsEnabled = false

    @Volatile
    private var wifiCredentialsWritten = false

    @SuppressLint("MissingPermission")
    fun start(draft: AqlProvisioningDraft) {
        close()

        if (!hasConnectPermission()) {
            emit(AqlBleProvisioningGattEvent.Failed("Bluetooth connect permission is missing."))
            return
        }

        val adapter = bluetoothManager?.adapter
        if (adapter == null) {
            emit(AqlBleProvisioningGattEvent.Failed("Bluetooth adapter is unavailable."))
            return
        }

        if (!adapter.isEnabled) {
            emit(AqlBleProvisioningGattEvent.Failed("Bluetooth is disabled."))
            return
        }

        val device = runCatching {
            adapter.getRemoteDevice(draft.bleAddress)
        }.getOrElse { error ->
            emit(
                AqlBleProvisioningGattEvent.Failed(
                    error.message ?: "Selected BLE device address is invalid."
                )
            )
            return
        }

        activeDraft = draft
        negotiatedMtu = DEFAULT_ATT_MTU
        deviceInfoVerified = false
        statusNotificationsEnabled = false
        runtimeNotificationsEnabled = false
        wifiCredentialsWritten = false
        emit(AqlBleProvisioningGattEvent.Connecting(draft.bleAddress))

        activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(
                appContext,
                false,
                callback,
                BluetoothDevice.TRANSPORT_LE
            )
        } else {
            device.connectGatt(
                appContext,
                false,
                callback
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        val gatt = activeGatt
        activeGatt = null

        runCatching {
            gatt?.disconnect()
        }

        runCatching {
            gatt?.close()
        }

        deviceInfoCharacteristic = null
        startSessionCharacteristic = null
        wifiCredentialsCharacteristic = null
        provisioningStatusCharacteristic = null
        runtimeEndpointCharacteristic = null
        negotiatedMtu = DEFAULT_ATT_MTU
        deviceInfoVerified = false
        statusNotificationsEnabled = false
        runtimeNotificationsEnabled = false
        wifiCredentialsWritten = false
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndClose("BLE connection failed with status $status.")
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    emit(AqlBleProvisioningGattEvent.Connected(gatt.device.address))
                    discoverServices(gatt)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    emit(AqlBleProvisioningGattEvent.Disconnected)
                    close()
                }
            }
        }

        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndClose("BLE service discovery failed with status $status.")
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                failAndClose("AquaLight provisioning service was not found.")
                return
            }

            deviceInfoCharacteristic = service.getCharacteristic(DEVICE_INFO_UUID)
            startSessionCharacteristic = service.getCharacteristic(START_SESSION_UUID)
            wifiCredentialsCharacteristic = service.getCharacteristic(WIFI_CREDENTIALS_UUID)
            provisioningStatusCharacteristic = service.getCharacteristic(PROVISIONING_STATUS_UUID)
            runtimeEndpointCharacteristic = service.getCharacteristic(RUNTIME_ENDPOINT_UUID)

            if (deviceInfoCharacteristic == null ||
                startSessionCharacteristic == null ||
                wifiCredentialsCharacteristic == null ||
                provisioningStatusCharacteristic == null ||
                runtimeEndpointCharacteristic == null
            ) {
                failAndClose("Required provisioning characteristics were not found.")
                return
            }

            emit(AqlBleProvisioningGattEvent.ServicesDiscovered)
            requestProvisioningMtu(gatt)
        }

        override fun onMtuChanged(
            gatt: BluetoothGatt,
            mtu: Int,
            status: Int
        ) {
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS && mtu > DEFAULT_ATT_MTU) {
                mtu
            } else {
                DEFAULT_ATT_MTU
            }
            readDeviceInfo(gatt)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndClose("BLE notification setup failed with status $status.")
                return
            }

            when (descriptor.characteristic?.uuid) {
                PROVISIONING_STATUS_UUID -> {
                    statusNotificationsEnabled = true
                    enableRuntimeEndpointNotifications(gatt)
                }

                RUNTIME_ENDPOINT_UUID -> {
                    runtimeNotificationsEnabled = true
                    writeStartSession(gatt)
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndClose("BLE write failed with status $status.")
                return
            }

            when (characteristic.uuid) {
                START_SESSION_UUID -> {
                    emit(AqlBleProvisioningGattEvent.StartSessionWritten)
                    readProvisioningStatus(gatt)
                }

                WIFI_CREDENTIALS_UUID -> {
                    wifiCredentialsWritten = true
                    emit(AqlBleProvisioningGattEvent.WifiCredentialsWritten)
                    readProvisioningStatus(gatt)
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndClose("BLE read failed with status $status.")
                return
            }

            val value = characteristic.value ?: ByteArray(0)
            if (characteristic.uuid == DEVICE_INFO_UUID) {
                handleDeviceInfoRead(gatt, value)
                return
            }

            handleCharacteristicValue(
                gatt = gatt,
                characteristic = characteristic,
                value = value
            )
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleCharacteristicValue(
                gatt = gatt,
                characteristic = characteristic,
                value = characteristic.value ?: ByteArray(0)
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun discoverServices(gatt: BluetoothGatt) {
        if (!hasConnectPermission()) {
            failAndClose("Bluetooth connect permission is missing.")
            return
        }

        if (!gatt.discoverServices()) {
            failAndClose("BLE service discovery could not be started.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestProvisioningMtu(gatt: BluetoothGatt) {
        if (!hasConnectPermission()) {
            failAndClose("Bluetooth connect permission is missing.")
            return
        }

        if (!gatt.requestMtu(REQUESTED_ATT_MTU)) {
            negotiatedMtu = DEFAULT_ATT_MTU
            readDeviceInfo(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readDeviceInfo(gatt: BluetoothGatt) {
        val characteristic = deviceInfoCharacteristic
        if (characteristic == null) {
            failAndClose("DeviceInfo characteristic is missing.")
            return
        }

        if (!hasConnectPermission()) {
            failAndClose("Bluetooth connect permission is missing.")
            return
        }

        if (!gatt.readCharacteristic(characteristic)) {
            failAndClose("DeviceInfo read could not be started.")
        }
    }

    private fun handleDeviceInfoRead(gatt: BluetoothGatt, value: ByteArray) {
        val raw = String(value, Charsets.UTF_8).trim()
        if (raw.isBlank()) {
            failAndClose("DeviceInfo payload is empty.")
            return
        }

        val deviceInfo = parseDeviceInfo(raw).getOrElse { error ->
            failAndClose(error.message ?: "DeviceInfo payload is invalid.")
            return
        }

        val validationError = validateDeviceInfo(deviceInfo)
        if (validationError != null) {
            failAndClose(validationError)
            return
        }

        deviceInfoVerified = true
        enableProvisioningStatusNotifications(gatt)
    }

    private fun writeStartSession(gatt: BluetoothGatt) {
        if (!deviceInfoVerified) {
            failAndClose("DeviceInfo must be verified before StartSession is written.")
            return
        }

        if (!statusNotificationsEnabled || !runtimeNotificationsEnabled) {
            failAndClose("BLE notifications must be enabled before StartSession is written.")
            return
        }

        val draft = activeDraft ?: return
        val characteristic = startSessionCharacteristic
        if (characteristic == null) {
            failAndClose("Start session characteristic is missing.")
            return
        }

        writeString(
            gatt = gatt,
            characteristic = characteristic,
            value = codec.startSessionJson(draft)
        )
    }

    private fun writeWifiCredentials(gatt: BluetoothGatt) {
        if (!deviceInfoVerified) {
            failAndClose("DeviceInfo must be verified before Wi-Fi credentials are written.")
            return
        }

        if (!statusNotificationsEnabled || !runtimeNotificationsEnabled) {
            failAndClose("BLE notifications must be enabled before Wi-Fi credentials are written.")
            return
        }

        val draft = activeDraft ?: return
        val characteristic = wifiCredentialsCharacteristic
        if (characteristic == null) {
            failAndClose("Wi-Fi credentials characteristic is missing.")
            return
        }

        writeString(
            gatt = gatt,
            characteristic = characteristic,
            value = codec.wifiCredentialsJson(draft)
        )
    }

    @SuppressLint("MissingPermission")
    private fun readProvisioningStatus(gatt: BluetoothGatt) {
        val characteristic = provisioningStatusCharacteristic
        if (characteristic == null) {
            failAndClose("ProvisioningStatus characteristic is missing.")
            return
        }

        if (!hasConnectPermission()) {
            failAndClose("Bluetooth connect permission is missing.")
            return
        }

        if (!gatt.readCharacteristic(characteristic)) {
            if (!wifiCredentialsWritten) {
                writeWifiCredentials(gatt)
            } else {
                readRuntimeEndpoint(gatt)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun readRuntimeEndpoint(gatt: BluetoothGatt) {
        val characteristic = runtimeEndpointCharacteristic
        if (characteristic == null) {
            failAndClose("RuntimeEndpoint characteristic is missing.")
            return
        }

        if (!hasConnectPermission()) {
            failAndClose("Bluetooth connect permission is missing.")
            return
        }

        if (!gatt.readCharacteristic(characteristic)) {
            failAndClose("RuntimeEndpoint read could not be started.")
        }
    }

    private fun handleCharacteristicValue(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        val raw = String(value, Charsets.UTF_8).trim()
        if (raw.isBlank()) {
            return
        }

        when (characteristic.uuid) {
            PROVISIONING_STATUS_UUID -> {
                val statusMessage = codec.parseStatus(raw)
                emit(AqlBleProvisioningGattEvent.StatusReceived(statusMessage))

                handleProvisioningStatus(
                    gatt = gatt,
                    status = statusMessage.status,
                    message = statusMessage.message
                )
            }

            RUNTIME_ENDPOINT_UUID -> {
                val draft = activeDraft
                val fallbackUid = draft?.candidateId.orEmpty()
                val handoff = codec.parseRuntimeHandoff(
                    raw = raw,
                    fallbackDeviceUid = fallbackUid
                ).getOrElse { error ->
                    emit(
                        AqlBleProvisioningGattEvent.Failed(
                            error.message ?: "Runtime endpoint handoff is invalid."
                        )
                    )
                    return
                }

                emit(AqlBleProvisioningGattEvent.RuntimeHandoffReceived(handoff))

                if (handoff.isUsable) {
                    emit(AqlBleProvisioningGattEvent.Completed)
                    close()
                }
            }
        }
    }

    private fun handleProvisioningStatus(
        gatt: BluetoothGatt,
        status: AqlProvisioningStatus,
        message: String
    ) {
        when (status) {
            AqlProvisioningStatus.PROVISIONING_IN_PROGRESS,
            AqlProvisioningStatus.WIFI_CREDENTIALS_RECEIVED,
            AqlProvisioningStatus.WIFI_CONNECTING,
            AqlProvisioningStatus.WIFI_CONNECTED -> {
                if (!wifiCredentialsWritten) {
                    writeWifiCredentials(gatt)
                }
            }

            AqlProvisioningStatus.WEB_SOCKET_TOKEN_READY,
            AqlProvisioningStatus.COMPLETED -> {
                readRuntimeEndpoint(gatt)
            }

            AqlProvisioningStatus.CLAIM_REJECTED,
            AqlProvisioningStatus.WIFI_FAILED,
            AqlProvisioningStatus.ERROR,
            AqlProvisioningStatus.TIMEOUT -> {
                failAndClose(
                    message.ifBlank { "Provisioning was rejected by the device: ${status.wireValue}." }
                )
            }

            AqlProvisioningStatus.PHYSICAL_RESET,
            AqlProvisioningStatus.FACTORY,
            AqlProvisioningStatus.CLAIM_VALIDATING,
            AqlProvisioningStatus.IDLE,
            AqlProvisioningStatus.UNKNOWN -> {
                // Wait for a later explicit provisioning state. Do not send Wi-Fi
                // credentials only because the BLE write transport succeeded.
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableProvisioningStatusNotifications(gatt: BluetoothGatt) {
        val characteristic = provisioningStatusCharacteristic
        if (characteristic == null) {
            failAndClose("ProvisioningStatus characteristic is missing.")
            return
        }

        enableNotifications(
            gatt = gatt,
            characteristic = characteristic,
            label = "ProvisioningStatus"
        )
    }

    @SuppressLint("MissingPermission")
    private fun enableRuntimeEndpointNotifications(gatt: BluetoothGatt) {
        val characteristic = runtimeEndpointCharacteristic
        if (characteristic == null) {
            failAndClose("RuntimeEndpoint characteristic is missing.")
            return
        }

        enableNotifications(
            gatt = gatt,
            characteristic = characteristic,
            label = "RuntimeEndpoint"
        )
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        label: String
    ) {
        if (!hasConnectPermission()) {
            failAndClose("Bluetooth connect permission is missing.")
            return
        }

        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            failAndClose("$label notifications could not be enabled locally.")
            return
        }

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor == null) {
            failAndClose("$label CCCD descriptor was not found.")
            return
        }

        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }

        if (!started) {
            failAndClose("$label CCCD write could not be started.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeString(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: String
    ) {
        if (!hasConnectPermission()) {
            failAndClose("Bluetooth connect permission is missing.")
            return
        }

        val bytes = value.toByteArray(Charsets.UTF_8)
        val maxPayloadBytes = (negotiatedMtu - ATT_MTU_OVERHEAD_BYTES)
            .coerceAtLeast(DEFAULT_ATT_PAYLOAD_BYTES)

        if (bytes.size > AqlBleProvisioningContract.BLE_JSON_MAX_BYTES) {
            failAndClose(
                "BLE JSON payload is ${bytes.size} bytes, limit is ${AqlBleProvisioningContract.BLE_JSON_MAX_BYTES} bytes."
            )
            return
        }

        if (bytes.size > maxPayloadBytes) {
            failAndClose(
                "BLE payload is ${bytes.size} bytes, negotiated MTU $negotiatedMtu allows $maxPayloadBytes bytes."
            )
            return
        }

        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = bytes
                gatt.writeCharacteristic(characteristic)
            }
        }

        if (!started) {
            failAndClose("BLE write operation could not be started.")
        }
    }

    private fun parseDeviceInfo(raw: String): Result<AqlBleDeviceInfo> {
        return runCatching {
            val json = JSONObject(raw.trim())
            AqlBleDeviceInfo(
                deviceUid = firstJsonString(
                    json,
                    AqlBleProvisioningContract.Json.KEY_DEVICE_UID,
                    "uid",
                    "device_uid"
                ),
                bleName = firstJsonString(
                    json,
                    "bleName",
                    "ble_name",
                    "name"
                ),
                mode = firstJsonString(
                    json,
                    AqlBleProvisioningContract.Json.KEY_STATUS,
                    "mode"
                ),
                claimRequired = firstJsonBoolean(
                    json,
                    "claimRequired",
                    "claim_required"
                )
            )
        }
    }

    private fun validateDeviceInfo(deviceInfo: AqlBleDeviceInfo): String? {
        val draft = activeDraft ?: return "Provisioning draft is missing."

        if (deviceInfo.deviceUid.isBlank()) {
            return "DeviceInfo does not include device uid."
        }

        val expectedUid = draft.candidateId
            .trim()
            .takeUnless { value -> value.isLikelyBleAddress() }
            .orEmpty()

        if (expectedUid.isNotBlank() && !deviceInfo.deviceUid.equals(expectedUid, ignoreCase = true)) {
            return "QR device uid does not match the connected BLE device."
        }

        val expectedBleName = draft.bleName.trim()
        if (expectedBleName.isNotBlank() && deviceInfo.bleName != expectedBleName) {
            return "QR BLE name does not match the connected BLE device."
        }

        if (!isAllowedProvisioningMode(deviceInfo.mode)) {
            return "Connected BLE device is not in provisioning mode: ${deviceInfo.mode.ifBlank { "unknown" }}."
        }

        if (deviceInfo.claimRequired == null) {
            return "DeviceInfo does not include claimRequired."
        }

        if (deviceInfo.claimRequired && draft.claimCode.isBlank()) {
            return "Connected BLE device requires a claim code, but QR payload does not include one."
        }

        return null
    }

    private fun isAllowedProvisioningMode(mode: String): Boolean {
        return when (mode) {
            AqlBleProvisioningContract.Status.FACTORY,
            AqlBleProvisioningContract.Status.PHYSICAL_RESET,
            AqlBleProvisioningContract.Status.PROVISIONING_IN_PROGRESS -> true
            else -> false
        }
    }

    private fun firstJsonString(
        json: JSONObject,
        vararg keys: String
    ): String {
        return keys
            .asSequence()
            .map { key -> json.optString(key).trim() }
            .firstOrNull { value -> value.isNotBlank() }
            .orEmpty()
    }

    private fun firstJsonBoolean(
        json: JSONObject,
        vararg keys: String
    ): Boolean? {
        return keys
            .asSequence()
            .firstNotNullOfOrNull { key ->
                if (!json.has(key)) {
                    null
                } else {
                    when (val value = json.opt(key)) {
                        is Boolean -> value
                        is String -> value.trim().lowercase().toBooleanStrictOrNull()
                        is Number -> value.toInt() != 0
                        else -> null
                    }
                }
            }
    }

    private fun String.isLikelyBleAddress(): Boolean {
        return matches(Regex("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun failAndClose(message: String) {
        emit(AqlBleProvisioningGattEvent.Failed(message))
        close()
    }

    private fun emit(event: AqlBleProvisioningGattEvent) {
        _events.tryEmit(event)
    }

    private data class AqlBleDeviceInfo(
        val deviceUid: String,
        val bleName: String,
        val mode: String,
        val claimRequired: Boolean?
    )

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 64
        const val REQUESTED_ATT_MTU = 517
        const val DEFAULT_ATT_MTU = 23
        const val ATT_MTU_OVERHEAD_BYTES = 3
        const val DEFAULT_ATT_PAYLOAD_BYTES = 20

        val SERVICE_UUID: UUID =
            UUID.fromString(AqlBleProvisioningContract.SERVICE_UUID)
        val DEVICE_INFO_UUID: UUID =
            UUID.fromString(AqlBleProvisioningContract.DEVICE_INFO_UUID)
        val START_SESSION_UUID: UUID =
            UUID.fromString(AqlBleProvisioningContract.START_SESSION_UUID)
        val WIFI_CREDENTIALS_UUID: UUID =
            UUID.fromString(AqlBleProvisioningContract.WIFI_CREDENTIALS_UUID)
        val PROVISIONING_STATUS_UUID: UUID =
            UUID.fromString(AqlBleProvisioningContract.PROVISIONING_STATUS_UUID)
        val RUNTIME_ENDPOINT_UUID: UUID =
            UUID.fromString(AqlBleProvisioningContract.RUNTIME_ENDPOINT_UUID)
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
