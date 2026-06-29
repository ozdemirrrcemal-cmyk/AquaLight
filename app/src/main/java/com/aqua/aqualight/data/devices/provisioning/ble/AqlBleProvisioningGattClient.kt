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
import android.os.Handler
import android.os.Looper
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
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _events = MutableSharedFlow<AqlBleProvisioningGattEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val events: SharedFlow<AqlBleProvisioningGattEvent> = _events.asSharedFlow()

    private val gattQueue = AqlBleGattOperationQueue(
        handler = mainHandler,
        startOperation = { operation -> startGattOperation(operation) },
        onStartFailure = { operation ->
            failAndClose("BLE GATT operation could not be started: $operation.")
        }
    )

    @Volatile private var activeGatt: BluetoothGatt? = null
    @Volatile private var activeDraft: AqlProvisioningDraft? = null
    @Volatile private var deviceInfoCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var startSessionCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var wifiCredentialsCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var provisioningStatusCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var runtimeEndpointCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var negotiatedMtu = DEFAULT_ATT_MTU
    @Volatile private var deviceInfoVerified = false
    @Volatile private var deviceInfoReadAttempts = 0
    @Volatile private var deviceClaimRequired: Boolean? = null
    @Volatile private var deviceNonce = ""
    @Volatile private var statusNotificationsEnabled = false
    @Volatile private var runtimeNotificationsEnabled = false
    @Volatile private var startSessionWritten = false
    @Volatile private var wifiCredentialsWriteStarted = false
    @Volatile private var wifiCredentialsWritten = false

    private val statusPollRunnable = Runnable {
        gattQueue.enqueue(AqlBleGattOperation.READ_PROVISIONING_STATUS)
    }

    private val deviceInfoRetryRunnable = Runnable {
        gattQueue.enqueue(AqlBleGattOperation.READ_DEVICE_INFO)
    }

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

        val device = runCatching { adapter.getRemoteDevice(draft.bleAddress) }.getOrElse { error ->
            emit(AqlBleProvisioningGattEvent.Failed(error.message ?: "Selected BLE device address is invalid."))
            return
        }

        activeDraft = draft
        negotiatedMtu = DEFAULT_ATT_MTU
        deviceInfoVerified = false
        deviceInfoReadAttempts = 0
        deviceClaimRequired = null
        deviceNonce = ""
        statusNotificationsEnabled = false
        runtimeNotificationsEnabled = false
        startSessionWritten = false
        wifiCredentialsWriteStarted = false
        wifiCredentialsWritten = false
        gattQueue.clear()
        mainHandler.removeCallbacks(statusPollRunnable)
        mainHandler.removeCallbacks(deviceInfoRetryRunnable)
        emit(AqlBleProvisioningGattEvent.Connecting(draft.bleAddress))

        activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, false, callback)
        }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        mainHandler.removeCallbacks(statusPollRunnable)
        mainHandler.removeCallbacks(deviceInfoRetryRunnable)
        gattQueue.clear()

        val gatt = activeGatt
        activeGatt = null

        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }

        activeDraft = null
        deviceInfoCharacteristic = null
        startSessionCharacteristic = null
        wifiCredentialsCharacteristic = null
        provisioningStatusCharacteristic = null
        runtimeEndpointCharacteristic = null
        negotiatedMtu = DEFAULT_ATT_MTU
        deviceInfoVerified = false
        deviceInfoReadAttempts = 0
        deviceClaimRequired = null
        deviceNonce = ""
        statusNotificationsEnabled = false
        runtimeNotificationsEnabled = false
        startSessionWritten = false
        wifiCredentialsWriteStarted = false
        wifiCredentialsWritten = false
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
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

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (activeGatt !== gatt) return
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
            gattQueue.enqueue(AqlBleGattOperation.REQUEST_MTU)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (activeGatt !== gatt) return
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS && mtu > DEFAULT_ATT_MTU) mtu else DEFAULT_ATT_MTU
            gattQueue.complete(AqlBleGattOperation.REQUEST_MTU)
            gattQueue.enqueue(AqlBleGattOperation.READ_DEVICE_INFO)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (activeGatt !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndClose("BLE notification setup failed with status $status.")
                return
            }

            when (descriptor.characteristic?.uuid) {
                PROVISIONING_STATUS_UUID -> {
                    statusNotificationsEnabled = true
                    gattQueue.complete(AqlBleGattOperation.ENABLE_STATUS_NOTIFICATIONS)
                    gattQueue.enqueue(AqlBleGattOperation.ENABLE_RUNTIME_NOTIFICATIONS)
                }
                RUNTIME_ENDPOINT_UUID -> {
                    runtimeNotificationsEnabled = true
                    gattQueue.complete(AqlBleGattOperation.ENABLE_RUNTIME_NOTIFICATIONS)
                    gattQueue.enqueue(AqlBleGattOperation.WRITE_START_SESSION)
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (activeGatt !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndClose("BLE write failed with status $status.")
                return
            }

            when (characteristic.uuid) {
                START_SESSION_UUID -> {
                    startSessionWritten = true
                    emit(AqlBleProvisioningGattEvent.StartSessionWritten)
                    gattQueue.complete(AqlBleGattOperation.WRITE_START_SESSION)
                    gattQueue.enqueue(AqlBleGattOperation.READ_PROVISIONING_STATUS)
                    scheduleStatusPoll()
                }
                WIFI_CREDENTIALS_UUID -> {
                    wifiCredentialsWritten = true
                    emit(AqlBleProvisioningGattEvent.WifiCredentialsWritten)
                    gattQueue.complete(AqlBleGattOperation.WRITE_WIFI_CREDENTIALS)
                    gattQueue.enqueue(AqlBleGattOperation.READ_PROVISIONING_STATUS)
                    scheduleStatusPoll()
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            handleCharacteristicReadValue(
                gatt = gatt,
                characteristic = characteristic,
                value = characteristic.value ?: ByteArray(0),
                status = status
            )
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleCharacteristicReadValue(
                gatt = gatt,
                characteristic = characteristic,
                value = value,
                status = status
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleCharacteristicNotification(
                gatt = gatt,
                characteristic = characteristic,
                value = characteristic.value ?: ByteArray(0)
            )
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicNotification(gatt = gatt, characteristic = characteristic, value = value)
        }
    }

    private fun startGattOperation(operation: AqlBleGattOperation): Boolean {
        val gatt = activeGatt ?: return false
        return when (operation) {
            AqlBleGattOperation.REQUEST_MTU -> requestProvisioningMtu(gatt)
            AqlBleGattOperation.READ_DEVICE_INFO -> readDeviceInfo(gatt)
            AqlBleGattOperation.ENABLE_STATUS_NOTIFICATIONS -> enableProvisioningStatusNotifications(gatt)
            AqlBleGattOperation.ENABLE_RUNTIME_NOTIFICATIONS -> enableRuntimeEndpointNotifications(gatt)
            AqlBleGattOperation.WRITE_START_SESSION -> writeStartSession(gatt)
            AqlBleGattOperation.WRITE_WIFI_CREDENTIALS -> writeWifiCredentials(gatt)
            AqlBleGattOperation.READ_PROVISIONING_STATUS -> readProvisioningStatus(gatt)
            AqlBleGattOperation.READ_RUNTIME_ENDPOINT -> readRuntimeEndpoint(gatt)
        }
    }

    private fun handleCharacteristicReadValue(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        if (activeGatt !== gatt) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            failAndClose("BLE read failed with status $status.")
            return
        }

        when (characteristic.uuid) {
            DEVICE_INFO_UUID -> handleDeviceInfoRead(gatt, value)
            PROVISIONING_STATUS_UUID -> handleProvisioningStatusRead(gatt, value)
            RUNTIME_ENDPOINT_UUID -> handleRuntimeEndpointValue(value, completeReadOperation = true)
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
    private fun requestProvisioningMtu(gatt: BluetoothGatt): Boolean {
        if (!hasConnectPermission()) {
            failAndClose("Bluetooth connect permission is missing.")
            return false
        }
        if (!gatt.requestMtu(REQUESTED_ATT_MTU)) {
            negotiatedMtu = DEFAULT_ATT_MTU
            gattQueue.complete(AqlBleGattOperation.REQUEST_MTU)
            gattQueue.enqueue(AqlBleGattOperation.READ_DEVICE_INFO)
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun readDeviceInfo(gatt: BluetoothGatt): Boolean {
        val characteristic = deviceInfoCharacteristic
        if (characteristic == null) {
            failAndClose("DeviceInfo characteristic is missing.")
            return false
        }
        if (!hasConnectPermission()) {
            failAndClose("Bluetooth connect permission is missing.")
            return false
        }
        if (!gatt.readCharacteristic(characteristic)) {
            retryDeviceInfoReadOrFail(gatt, "DeviceInfo read could not be started.")
        }
        return true
    }

    private fun handleDeviceInfoRead(gatt: BluetoothGatt, value: ByteArray) {
        val raw = String(value, Charsets.UTF_8).trim()
        if (raw.isBlank()) {
            retryDeviceInfoReadOrFail(gatt, "DeviceInfo payload is empty.")
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
        deviceInfoReadAttempts = 0
        deviceClaimRequired = deviceInfo.claimRequired
        deviceNonce = deviceInfo.deviceNonce
        mainHandler.removeCallbacks(deviceInfoRetryRunnable)
        gattQueue.complete(AqlBleGattOperation.READ_DEVICE_INFO)
        gattQueue.enqueue(AqlBleGattOperation.ENABLE_STATUS_NOTIFICATIONS)
    }

    private fun retryDeviceInfoReadOrFail(gatt: BluetoothGatt, finalMessage: String) {
        if (activeGatt !== gatt || deviceInfoVerified) return

        deviceInfoReadAttempts += 1
        gattQueue.complete(AqlBleGattOperation.READ_DEVICE_INFO)
        if (deviceInfoReadAttempts <= MAX_DEVICE_INFO_READ_ATTEMPTS) {
            mainHandler.removeCallbacks(deviceInfoRetryRunnable)
            mainHandler.postDelayed(deviceInfoRetryRunnable, DEVICE_INFO_RETRY_DELAY_MS)
            return
        }

        failAndClose(finalMessage)
    }

    private fun writeStartSession(gatt: BluetoothGatt): Boolean {
        if (!deviceInfoVerified) {
            failAndClose("DeviceInfo must be verified before StartSession is written.")
            return false
        }
        if (!statusNotificationsEnabled || !runtimeNotificationsEnabled) {
            failAndClose("BLE notifications must be enabled before StartSession is written.")
            return false
        }

        val draft = activeDraft ?: return false
        val characteristic = startSessionCharacteristic
        if (characteristic == null) {
            failAndClose("Start session characteristic is missing.")
            return false
        }

        return writeString(gatt = gatt, characteristic = characteristic, value = codec.startSessionJson(draft, deviceNonce))
    }

    private fun writeWifiCredentialsIfReady(): Boolean {
        if (wifiCredentialsWritten || wifiCredentialsWriteStarted) return true
        if (!startSessionWritten) return false

        gattQueue.enqueue(AqlBleGattOperation.WRITE_WIFI_CREDENTIALS)
        return true
    }

    private fun writeWifiCredentials(gatt: BluetoothGatt): Boolean {
        if (!deviceInfoVerified) {
            failAndClose("DeviceInfo must be verified before Wi-Fi credentials are written.")
            return false
        }
        if (!statusNotificationsEnabled || !runtimeNotificationsEnabled) {
            failAndClose("BLE notifications must be enabled before Wi-Fi credentials are written.")
            return false
        }
        if (!startSessionWritten) {
            scheduleStatusPoll()
            return true
        }
        if (wifiCredentialsWriteStarted || wifiCredentialsWritten) return true

        val draft = activeDraft ?: return false
        val characteristic = wifiCredentialsCharacteristic
        if (characteristic == null) {
            failAndClose("Wi-Fi credentials characteristic is missing.")
            return false
        }

        mainHandler.removeCallbacks(statusPollRunnable)
        wifiCredentialsWriteStarted = true
        return writeString(gatt = gatt, characteristic = characteristic, value = codec.wifiCredentialsJson(draft))
    }

    @SuppressLint("MissingPermission")
    private fun readProvisioningStatus(gatt: BluetoothGatt): Boolean {
        val characteristic = provisioningStatusCharacteristic
        if (characteristic == null) {
            failAndClose("ProvisioningStatus characteristic is missing.")
            return false
        }
        if (!hasConnectPermission()) {
            failAndClose("Bluetooth connect permission is missing.")
            return false
        }
        return gatt.readCharacteristic(characteristic)
    }

    private fun handleProvisioningStatusRead(gatt: BluetoothGatt, value: ByteArray) {
        val raw = String(value, Charsets.UTF_8).trim()
        if (raw.isNotBlank()) {
            val statusMessage = codec.parseStatus(raw)
            emit(AqlBleProvisioningGattEvent.StatusReceived(statusMessage))
            handleProvisioningStatus(gatt = gatt, status = statusMessage.status, message = statusMessage.message)
        } else {
            scheduleStatusPoll()
        }
        gattQueue.complete(AqlBleGattOperation.READ_PROVISIONING_STATUS)
    }

    @SuppressLint("MissingPermission")
    private fun readRuntimeEndpoint(gatt: BluetoothGatt): Boolean {
        val characteristic = runtimeEndpointCharacteristic
        if (characteristic == null) {
            failAndClose("RuntimeEndpoint characteristic is missing.")
            return false
        }
        if (!hasConnectPermission()) {
            failAndClose("Bluetooth connect permission is missing.")
            return false
        }
        return gatt.readCharacteristic(characteristic)
    }

    private fun handleCharacteristicNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        if (activeGatt !== gatt) return
        val raw = String(value, Charsets.UTF_8).trim()
        if (raw.isBlank()) return

        when (characteristic.uuid) {
            PROVISIONING_STATUS_UUID -> {
                val statusMessage = codec.parseStatus(raw)
                emit(AqlBleProvisioningGattEvent.StatusReceived(statusMessage))
                handleProvisioningStatus(gatt = gatt, status = statusMessage.status, message = statusMessage.message)
            }
            RUNTIME_ENDPOINT_UUID -> handleRuntimeEndpointValue(value, completeReadOperation = false)
        }
    }

    private fun handleRuntimeEndpointValue(
        value: ByteArray,
        completeReadOperation: Boolean
    ) {
        val raw = String(value, Charsets.UTF_8).trim()
        if (raw.isBlank()) {
            if (completeReadOperation) gattQueue.complete(AqlBleGattOperation.READ_RUNTIME_ENDPOINT)
            return
        }

        val draft = activeDraft
        val fallbackUid = draft?.candidateId.orEmpty()
        val handoff = codec.parseRuntimeHandoff(raw = raw, fallbackDeviceUid = fallbackUid).getOrElse { error ->
            emit(AqlBleProvisioningGattEvent.Failed(error.message ?: "Runtime endpoint handoff is invalid."))
            if (completeReadOperation) gattQueue.complete(AqlBleGattOperation.READ_RUNTIME_ENDPOINT)
            scheduleStatusPoll()
            return
        }

        emit(AqlBleProvisioningGattEvent.RuntimeHandoffReceived(handoff))
        if (completeReadOperation) gattQueue.complete(AqlBleGattOperation.READ_RUNTIME_ENDPOINT)
        if (handoff.isUsable) {
            mainHandler.removeCallbacks(statusPollRunnable)
            emit(AqlBleProvisioningGattEvent.Completed)
            close()
        } else {
            scheduleStatusPoll()
        }
    }

    private fun handleProvisioningStatus(
        gatt: BluetoothGatt,
        status: AqlProvisioningStatus,
        message: String
    ) {
        when (status) {
            AqlProvisioningStatus.PROVISIONING_IN_PROGRESS -> {
                if (!writeWifiCredentialsIfReady()) scheduleStatusPoll()
            }
            AqlProvisioningStatus.PHYSICAL_RESET -> {
                if (!wifiCredentialsWritten && deviceClaimRequired == false) {
                    if (!writeWifiCredentialsIfReady()) scheduleStatusPoll()
                } else {
                    scheduleStatusPoll()
                }
            }
            AqlProvisioningStatus.WIFI_CREDENTIALS_RECEIVED,
            AqlProvisioningStatus.WIFI_CONNECTING -> scheduleStatusPoll()
            AqlProvisioningStatus.WIFI_CONNECTED -> scheduleStatusPoll()
            AqlProvisioningStatus.WEB_SOCKET_TOKEN_READY -> {
                mainHandler.removeCallbacks(statusPollRunnable)
                gattQueue.enqueue(AqlBleGattOperation.READ_RUNTIME_ENDPOINT)
            }
            AqlProvisioningStatus.COMPLETED -> {
                failAndClose("Provisioning completed before RuntimeEndpoint handoff was received.")
            }
            AqlProvisioningStatus.CLAIM_REJECTED,
            AqlProvisioningStatus.WIFI_FAILED,
            AqlProvisioningStatus.ERROR,
            AqlProvisioningStatus.TIMEOUT -> {
                failAndClose(message.ifBlank { "Provisioning was rejected by the device: ${status.wireValue}." })
            }
            AqlProvisioningStatus.FACTORY,
            AqlProvisioningStatus.CLAIM_VALIDATING,
            AqlProvisioningStatus.IDLE,
            AqlProvisioningStatus.UNKNOWN -> scheduleStatusPoll()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableProvisioningStatusNotifications(gatt: BluetoothGatt): Boolean {
        val characteristic = provisioningStatusCharacteristic
        if (characteristic == null) {
            failAndClose("ProvisioningStatus characteristic is missing.")
            return false
        }
        return enableNotifications(gatt = gatt, characteristic = characteristic, label = "ProvisioningStatus")
    }

    @SuppressLint("MissingPermission")
    private fun enableRuntimeEndpointNotifications(gatt: BluetoothGatt): Boolean {
        val characteristic = runtimeEndpointCharacteristic
        if (characteristic == null) {
            failAndClose("RuntimeEndpoint characteristic is missing.")
            return false
        }
        return enableNotifications(gatt = gatt, characteristic = characteristic, label = "RuntimeEndpoint")
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        label: String
    ): Boolean {
        if (!hasConnectPermission()) {
            failAndClose("Bluetooth connect permission is missing.")
            return false
        }
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            failAndClose("$label notifications could not be enabled locally.")
            return false
        }

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor == null) {
            failAndClose("$label CCCD descriptor was not found.")
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeString(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: String
    ): Boolean {
        if (!hasConnectPermission()) {
            failAndClose("Bluetooth connect permission is missing.")
            return false
        }

        val bytes = value.toByteArray(Charsets.UTF_8)
        val maxPayloadBytes = (negotiatedMtu - ATT_MTU_OVERHEAD_BYTES).coerceAtLeast(DEFAULT_ATT_PAYLOAD_BYTES)

        if (bytes.size > AqlBleProvisioningContract.BLE_JSON_MAX_BYTES) {
            failAndClose("BLE JSON payload is ${bytes.size} bytes, limit is ${AqlBleProvisioningContract.BLE_JSON_MAX_BYTES} bytes.")
            return false
        }
        if (bytes.size > maxPayloadBytes) {
            failAndClose("BLE payload is ${bytes.size} bytes, negotiated MTU $negotiatedMtu allows $maxPayloadBytes bytes.")
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = bytes
                gatt.writeCharacteristic(characteristic)
            }
        }
    }

    private fun parseDeviceInfo(raw: String): Result<AqlBleDeviceInfo> {
        return runCatching {
            val json = JSONObject(raw.trim())
            AqlBleDeviceInfo(
                deviceUid = firstJsonString(json, AqlBleProvisioningContract.Json.KEY_DEVICE_UID, "uid", "device_uid"),
                deviceNonce = firstJsonString(json, AqlBleProvisioningContract.Json.KEY_DEVICE_NONCE, "device_nonce"),
                bleName = firstJsonString(json, "bleName", "ble_name", "name"),
                mode = firstJsonString(json, AqlBleProvisioningContract.Json.KEY_STATUS, "mode"),
                claimRequired = firstJsonBoolean(json, "claimRequired", "claim_required")
            )
        }
    }

    private fun validateDeviceInfo(deviceInfo: AqlBleDeviceInfo): String? {
        val draft = activeDraft ?: return "Provisioning draft is missing."

        if (deviceInfo.deviceUid.isBlank()) return "DeviceInfo does not include device uid."
        if (!deviceInfo.deviceNonce.isUuidV4()) return "DeviceInfo does not include a valid deviceNonce."

        val expectedUid = draft.candidateId.trim().takeUnless { value -> value.isLikelyBleAddress() }.orEmpty()
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

        if (deviceInfo.claimRequired == null) return "DeviceInfo does not include claimRequired."
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

    private fun firstJsonString(json: JSONObject, vararg keys: String): String {
        return keys.asSequence()
            .map { key -> json.optString(key).trim() }
            .firstOrNull { value -> value.isNotBlank() }
            .orEmpty()
    }

    private fun firstJsonBoolean(json: JSONObject, vararg keys: String): Boolean? {
        return keys.asSequence().firstNotNullOfOrNull { key ->
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

    private fun String.isUuidV4(): Boolean {
        return matches(Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"))
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun scheduleStatusPoll() {
        if (activeGatt == null) return
        mainHandler.removeCallbacks(statusPollRunnable)
        mainHandler.postDelayed(statusPollRunnable, STATUS_POLL_INTERVAL_MS)
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
        val deviceNonce: String,
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
        const val STATUS_POLL_INTERVAL_MS = 1_500L
        const val MAX_DEVICE_INFO_READ_ATTEMPTS = 3
        const val DEVICE_INFO_RETRY_DELAY_MS = 350L

        val SERVICE_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.SERVICE_UUID)
        val DEVICE_INFO_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.DEVICE_INFO_UUID)
        val START_SESSION_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.START_SESSION_UUID)
        val WIFI_CREDENTIALS_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.WIFI_CREDENTIALS_UUID)
        val PROVISIONING_STATUS_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.PROVISIONING_STATUS_UUID)
        val RUNTIME_ENDPOINT_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.RUNTIME_ENDPOINT_UUID)
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
