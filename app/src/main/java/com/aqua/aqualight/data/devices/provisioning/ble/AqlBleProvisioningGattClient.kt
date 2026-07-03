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
import java.net.URLDecoder
import java.util.Locale
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
        onStartFailure = { operation, failure -> handleGattOperationStartFailure(operation, failure) }
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
    @Volatile private var deviceNonce = ""
    @Volatile private var deviceUid = ""
    @Volatile private var deviceSessionMode = ""
    @Volatile private var devicePublicKey = ""
    @Volatile private var statusNotificationsEnabled = false
    @Volatile private var runtimeNotificationsEnabled = false
    @Volatile private var startSessionWritten = false
    @Volatile private var wifiCredentialsWriteStarted = false
    @Volatile private var wifiCredentialsWritten = false
    @Volatile private var runtimeHandoffReceived = false
    @Volatile private var runtimeEndpointReadRequested = false

    private val operationStartFailures = mutableMapOf<AqlBleGattOperation, Int>()
    private val statusPollRunnable = Runnable { gattQueue.enqueue(AqlBleGattOperation.READ_PROVISIONING_STATUS) }
    private val deviceInfoRetryRunnable = Runnable { gattQueue.enqueue(AqlBleGattOperation.READ_DEVICE_INFO) }

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
        deviceNonce = ""
        deviceUid = ""
        deviceSessionMode = ""
        devicePublicKey = ""
        statusNotificationsEnabled = false
        runtimeNotificationsEnabled = false
        startSessionWritten = false
        wifiCredentialsWriteStarted = false
        wifiCredentialsWritten = false
        runtimeHandoffReceived = false
        runtimeEndpointReadRequested = false
        operationStartFailures.clear()
        codec.resetSecureSession()
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
        operationStartFailures.clear()
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
        deviceNonce = ""
        deviceUid = ""
        deviceSessionMode = ""
        devicePublicKey = ""
        statusNotificationsEnabled = false
        runtimeNotificationsEnabled = false
        startSessionWritten = false
        wifiCredentialsWriteStarted = false
        wifiCredentialsWritten = false
        runtimeHandoffReceived = false
        runtimeEndpointReadRequested = false
        codec.resetSecureSession()
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
            if (deviceInfoCharacteristic == null || startSessionCharacteristic == null || wifiCredentialsCharacteristic == null ||
                provisioningStatusCharacteristic == null || runtimeEndpointCharacteristic == null) {
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

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (activeGatt !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndClose("BLE write failed with status $status.")
                return
            }
            when (characteristic.uuid) {
                START_SESSION_UUID -> {
                    startSessionWritten = true
                    operationStartFailures.remove(AqlBleGattOperation.WRITE_START_SESSION)
                    emit(AqlBleProvisioningGattEvent.StartSessionWritten)
                    gattQueue.complete(AqlBleGattOperation.WRITE_START_SESSION)
                    gattQueue.enqueue(AqlBleGattOperation.READ_PROVISIONING_STATUS)
                    scheduleStatusPoll()
                }
                WIFI_CREDENTIALS_UUID -> {
                    wifiCredentialsWritten = true
                    operationStartFailures.remove(AqlBleGattOperation.WRITE_WIFI_CREDENTIALS)
                    emit(AqlBleProvisioningGattEvent.WifiCredentialsWritten)
                    gattQueue.complete(AqlBleGattOperation.WRITE_WIFI_CREDENTIALS)
                    gattQueue.enqueue(AqlBleGattOperation.READ_PROVISIONING_STATUS)
                    scheduleStatusPoll()
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handleCharacteristicReadValue(gatt, characteristic, characteristic.value ?: ByteArray(0), status)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            handleCharacteristicReadValue(gatt, characteristic, value, status)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristicNotification(gatt, characteristic, characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicNotification(gatt, characteristic, value)
        }
    }

    private fun startGattOperation(operation: AqlBleGattOperation): AqlBleGattOperationStartResult {
        val gatt = activeGatt ?: return gattNotStarted("BLE GATT connection is not active.")
        val result = when (operation) {
            AqlBleGattOperation.REQUEST_MTU -> requestProvisioningMtu(gatt)
            AqlBleGattOperation.READ_DEVICE_INFO -> readDeviceInfo(gatt)
            AqlBleGattOperation.ENABLE_STATUS_NOTIFICATIONS -> enableProvisioningStatusNotifications(gatt)
            AqlBleGattOperation.ENABLE_RUNTIME_NOTIFICATIONS -> enableRuntimeEndpointNotifications(gatt)
            AqlBleGattOperation.WRITE_START_SESSION -> writeStartSession(gatt)
            AqlBleGattOperation.WRITE_WIFI_CREDENTIALS -> writeWifiCredentials(gatt)
            AqlBleGattOperation.READ_PROVISIONING_STATUS -> readProvisioningStatus(gatt)
            AqlBleGattOperation.READ_RUNTIME_ENDPOINT -> readRuntimeEndpoint(gatt)
        }
        if (result is AqlBleGattOperationStartResult.Started) operationStartFailures.remove(operation)
        return result
    }

    private fun handleGattOperationStartFailure(operation: AqlBleGattOperation, failure: AqlBleGattOperationStartResult.NotStarted) {
        val attempt = (operationStartFailures[operation] ?: 0) + 1
        operationStartFailures[operation] = attempt
        if (failure.retryable && activeGatt != null && attempt <= MAX_GATT_OPERATION_START_RETRIES) {
            gattQueue.enqueueDelayed(operation, GATT_OPERATION_START_RETRY_DELAY_MS)
            return
        }
        failAndClose(failure.message)
    }

    private fun handleCharacteristicReadValue(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
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
        if (!gatt.discoverServices()) failAndClose("BLE service discovery could not be started.")
    }

    @SuppressLint("MissingPermission")
    private fun requestProvisioningMtu(gatt: BluetoothGatt): AqlBleGattOperationStartResult {
        if (!hasConnectPermission()) return gattNotStarted("Bluetooth connect permission is missing.")
        if (!gatt.requestMtu(REQUESTED_ATT_MTU)) {
            negotiatedMtu = DEFAULT_ATT_MTU
            gattQueue.complete(AqlBleGattOperation.REQUEST_MTU)
            gattQueue.enqueue(AqlBleGattOperation.READ_DEVICE_INFO)
        }
        return gattStarted()
    }

    @SuppressLint("MissingPermission")
    private fun readDeviceInfo(gatt: BluetoothGatt): AqlBleGattOperationStartResult {
        val characteristic = deviceInfoCharacteristic ?: return gattNotStarted("DeviceInfo characteristic is missing.")
        if (!hasConnectPermission()) return gattNotStarted("Bluetooth connect permission is missing.")
        if (!gatt.readCharacteristic(characteristic)) retryDeviceInfoReadOrFail(gatt, "DeviceInfo read could not be started.")
        return gattStarted()
    }

    private fun handleDeviceInfoRead(gatt: BluetoothGatt, value: ByteArray) {
        val raw = String(value, Charsets.UTF_8).trim()
        if (raw.isBlank()) {
            retryDeviceInfoReadOrFail(gatt, "DeviceInfo payload is empty.")
            return
        }
        val info = parseDeviceInfo(raw).getOrElse { error ->
            failAndClose(error.message ?: "DeviceInfo payload is invalid.")
            return
        }
        val validationError = validateDeviceInfo(info)
        if (validationError != null) {
            failAndClose(validationError)
            return
        }

        val existingDraft = activeDraft
        val verifiedTitle = info.displayName.ifBlank { existingDraft?.deviceTitle.orEmpty() }
        val verifiedSerial = info.serialNumber.ifBlank { existingDraft?.deviceSerial.orEmpty() }
        val verifiedModel = info.productModel.ifBlank { existingDraft?.deviceModel.orEmpty() }
        if (existingDraft != null) {
            activeDraft = existingDraft.copy(
                deviceTitle = verifiedTitle,
                deviceSerial = verifiedSerial,
                deviceModel = verifiedModel
            )
        }

        deviceInfoVerified = true
        deviceInfoReadAttempts = 0
        deviceNonce = info.deviceNonce
        deviceUid = info.deviceUid
        deviceSessionMode = info.sessionMode
        devicePublicKey = info.devicePublicKey
        emit(AqlBleProvisioningGattEvent.DeviceInfoVerified(verifiedTitle, verifiedSerial, verifiedModel))
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

    private fun writeStartSession(gatt: BluetoothGatt): AqlBleGattOperationStartResult {
        if (!deviceInfoVerified) {
            failAndClose("DeviceInfo must be verified before StartSession is written.")
            return gattStarted()
        }
        if (!statusNotificationsEnabled || !runtimeNotificationsEnabled) {
            failAndClose("BLE notifications must be enabled before StartSession is written.")
            return gattStarted()
        }
        val draft = activeDraft ?: return gattNotStarted("Provisioning draft is missing.")
        val characteristic = startSessionCharacteristic ?: return gattNotStarted("Start session characteristic is missing.")
        val payload = codec.startSessionJson(
            draft = draft,
            deviceInfo = AqlBleProvisioningCrypto.DeviceInfo(
                deviceUid = deviceUid,
                deviceNonce = deviceNonce,
                sessionMode = deviceSessionMode,
                devicePublicKey = devicePublicKey
            )
        ).getOrElse { error ->
            failAndClose(error.message ?: "Secure StartSession could not be prepared.")
            return gattStarted()
        }
        return writeString(gatt, characteristic, payload, "StartSession")
    }

    private fun writeWifiCredentialsIfReady(): Boolean {
        if (wifiCredentialsWritten || wifiCredentialsWriteStarted) return true
        if (!startSessionWritten) return false
        gattQueue.enqueue(AqlBleGattOperation.WRITE_WIFI_CREDENTIALS)
        return true
    }

    private fun writeWifiCredentials(gatt: BluetoothGatt): AqlBleGattOperationStartResult {
        if (!deviceInfoVerified) {
            failAndClose("DeviceInfo must be verified before Wi-Fi credentials are written.")
            return gattStarted()
        }
        if (!statusNotificationsEnabled || !runtimeNotificationsEnabled) {
            failAndClose("BLE notifications must be enabled before Wi-Fi credentials are written.")
            return gattStarted()
        }
        if (!startSessionWritten) {
            scheduleStatusPoll()
            return gattNotStarted("StartSession must be written before Wi-Fi credentials.", retryable = true)
        }
        if (wifiCredentialsWriteStarted || wifiCredentialsWritten) return gattStarted()
        val draft = activeDraft ?: return gattNotStarted("Provisioning draft is missing.")
        val characteristic = wifiCredentialsCharacteristic ?: return gattNotStarted("Wi-Fi credentials characteristic is missing.")
        val encryptedPayload = codec.wifiCredentialsJson(draft).getOrElse { error ->
            failAndClose(error.message ?: "Secure Wi-Fi credentials could not be prepared.")
            return gattStarted()
        }
        mainHandler.removeCallbacks(statusPollRunnable)
        val result = writeString(gatt, characteristic, encryptedPayload, "WiFiCredentials")
        if (result is AqlBleGattOperationStartResult.Started) wifiCredentialsWriteStarted = true
        return result
    }

    @SuppressLint("MissingPermission")
    private fun readProvisioningStatus(gatt: BluetoothGatt): AqlBleGattOperationStartResult {
        val characteristic = provisioningStatusCharacteristic ?: return gattNotStarted("ProvisioningStatus characteristic is missing.")
        if (!hasConnectPermission()) return gattNotStarted("Bluetooth connect permission is missing.")
        return if (gatt.readCharacteristic(characteristic)) {
            gattStarted()
        } else {
            gattNotStarted("ProvisioningStatus read could not be started.", retryable = true)
        }
    }

    private fun handleProvisioningStatusRead(gatt: BluetoothGatt, value: ByteArray) {
        val raw = String(value, Charsets.UTF_8).trim()
        if (raw.isNotBlank()) {
            val statusMessage = codec.parseStatus(raw)
            emit(AqlBleProvisioningGattEvent.StatusReceived(statusMessage))
            handleProvisioningStatus(gatt, statusMessage.status, statusMessage.message)
        } else {
            scheduleStatusPoll()
        }
        gattQueue.complete(AqlBleGattOperation.READ_PROVISIONING_STATUS)
    }

    @SuppressLint("MissingPermission")
    private fun readRuntimeEndpoint(gatt: BluetoothGatt): AqlBleGattOperationStartResult {
        val characteristic = runtimeEndpointCharacteristic ?: return gattNotStarted("RuntimeEndpoint characteristic is missing.")
        if (!hasConnectPermission()) return gattNotStarted("Bluetooth connect permission is missing.")
        return if (gatt.readCharacteristic(characteristic)) {
            gattStarted()
        } else {
            gattNotStarted("RuntimeEndpoint read could not be started.", retryable = true)
        }
    }

    private fun handleCharacteristicNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (activeGatt !== gatt) return
        val raw = String(value, Charsets.UTF_8).trim()
        if (raw.isBlank()) return
        when (characteristic.uuid) {
            PROVISIONING_STATUS_UUID -> {
                val statusMessage = codec.parseStatus(raw)
                emit(AqlBleProvisioningGattEvent.StatusReceived(statusMessage))
                handleProvisioningStatus(gatt, statusMessage.status, statusMessage.message)
            }
            RUNTIME_ENDPOINT_UUID -> handleRuntimeEndpointValue(value, completeReadOperation = false)
        }
    }

    private fun handleRuntimeEndpointValue(value: ByteArray, completeReadOperation: Boolean) {
        if (runtimeHandoffReceived) {
            if (completeReadOperation) gattQueue.complete(AqlBleGattOperation.READ_RUNTIME_ENDPOINT)
            return
        }

        val raw = String(value, Charsets.UTF_8).trim()
        if (raw.isBlank()) {
            runtimeEndpointReadRequested = false
            if (completeReadOperation) gattQueue.complete(AqlBleGattOperation.READ_RUNTIME_ENDPOINT)
            scheduleStatusPoll()
            return
        }

        val fallbackUid = activeDraft?.candidateId.orEmpty()
        val handoff = codec.parseRuntimeHandoff(raw, fallbackUid).getOrElse { error ->
            runtimeEndpointReadRequested = false
            emit(AqlBleProvisioningGattEvent.Failed(error.message ?: "Runtime endpoint handoff is invalid."))
            if (completeReadOperation) gattQueue.complete(AqlBleGattOperation.READ_RUNTIME_ENDPOINT)
            scheduleStatusPoll()
            return
        }

        runtimeHandoffReceived = true
        runtimeEndpointReadRequested = false
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

    private fun handleProvisioningStatus(gatt: BluetoothGatt, status: AqlProvisioningStatus, message: String) {
        when (status) {
            AqlProvisioningStatus.PROVISIONING_IN_PROGRESS -> {
                if (!writeWifiCredentialsIfReady()) scheduleStatusPoll()
            }
            AqlProvisioningStatus.PHYSICAL_RESET,
            AqlProvisioningStatus.FACTORY,
            AqlProvisioningStatus.CLAIM_VALIDATING,
            AqlProvisioningStatus.IDLE,
            AqlProvisioningStatus.UNKNOWN -> scheduleStatusPoll()
            AqlProvisioningStatus.WIFI_CREDENTIALS_RECEIVED,
            AqlProvisioningStatus.WIFI_CONNECTING,
            AqlProvisioningStatus.WIFI_CONNECTED -> scheduleStatusPoll()
            AqlProvisioningStatus.WEB_SOCKET_TOKEN_READY -> {
                mainHandler.removeCallbacks(statusPollRunnable)
                if (!runtimeHandoffReceived && !runtimeEndpointReadRequested) {
                    runtimeEndpointReadRequested = true
                    gattQueue.enqueue(AqlBleGattOperation.READ_RUNTIME_ENDPOINT)
                }
            }
            AqlProvisioningStatus.COMPLETED -> {
                mainHandler.removeCallbacks(statusPollRunnable)
                if (runtimeHandoffReceived) {
                    emit(AqlBleProvisioningGattEvent.Completed)
                    close()
                } else if (!runtimeEndpointReadRequested) {
                    runtimeEndpointReadRequested = true
                    gattQueue.enqueue(AqlBleGattOperation.READ_RUNTIME_ENDPOINT)
                } else {
                    scheduleStatusPoll()
                }
            }
            AqlProvisioningStatus.CLAIM_REJECTED,
            AqlProvisioningStatus.WIFI_FAILED,
            AqlProvisioningStatus.ERROR,
            AqlProvisioningStatus.TIMEOUT -> {
                failAndClose(message.ifBlank { "Provisioning was rejected by the device: ${status.wireValue}." })
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableProvisioningStatusNotifications(gatt: BluetoothGatt): AqlBleGattOperationStartResult {
        val characteristic = provisioningStatusCharacteristic ?: return gattNotStarted("ProvisioningStatus characteristic is missing.")
        return enableNotifications(gatt, characteristic, "ProvisioningStatus")
    }

    @SuppressLint("MissingPermission")
    private fun enableRuntimeEndpointNotifications(gatt: BluetoothGatt): AqlBleGattOperationStartResult {
        val characteristic = runtimeEndpointCharacteristic ?: return gattNotStarted("RuntimeEndpoint characteristic is missing.")
        return enableNotifications(gatt, characteristic, "RuntimeEndpoint")
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, label: String): AqlBleGattOperationStartResult {
        if (!hasConnectPermission()) return gattNotStarted("Bluetooth connect permission is missing.")
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            failAndClose("$label notifications could not be enabled locally.")
            return gattStarted()
        }
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            ?: return gattNotStarted("$label CCCD descriptor was not found.")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            bluetoothStatusToStartResult(status, "$label CCCD write")
        } else {
            @Suppress("DEPRECATION")
            run {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (gatt.writeDescriptor(descriptor)) {
                    gattStarted()
                } else {
                    gattNotStarted("$label CCCD write could not be started.", retryable = true)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeString(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: String,
        label: String
    ): AqlBleGattOperationStartResult {
        if (!hasConnectPermission()) return gattNotStarted("Bluetooth connect permission is missing.")
        val bytes = value.toByteArray(Charsets.UTF_8)
        val maxPayloadBytes = (negotiatedMtu - ATT_MTU_OVERHEAD_BYTES).coerceAtLeast(DEFAULT_ATT_PAYLOAD_BYTES)
        if (bytes.size > AqlBleProvisioningContract.BLE_JSON_MAX_BYTES) {
            failAndClose("BLE JSON payload is ${bytes.size} bytes, limit is ${AqlBleProvisioningContract.BLE_JSON_MAX_BYTES} bytes.")
            return gattStarted()
        }
        if (bytes.size > maxPayloadBytes) {
            failAndClose("BLE payload is ${bytes.size} bytes, negotiated MTU $negotiatedMtu allows $maxPayloadBytes bytes.")
            return gattStarted()
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            bluetoothStatusToStartResult(status, "$label write")
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = bytes
                if (gatt.writeCharacteristic(characteristic)) {
                    gattStarted()
                } else {
                    gattNotStarted("$label write could not be started.", retryable = true)
                }
            }
        }
    }

    private fun bluetoothStatusToStartResult(status: Int, label: String): AqlBleGattOperationStartResult {
        return when (status) {
            BluetoothStatusCodes.SUCCESS -> gattStarted()
            BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY -> gattNotStarted("$label could not be started: ${bluetoothStatusName(status)}.", retryable = true)
            BluetoothStatusCodes.ERROR_GATT_WRITE_NOT_ALLOWED,
            BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION,
            BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND,
            BluetoothStatusCodes.ERROR_UNKNOWN -> gattNotStarted("$label could not be started: ${bluetoothStatusName(status)}.")
            else -> gattNotStarted("$label could not be started: BluetoothStatusCode($status).")
        }
    }

    private fun bluetoothStatusName(status: Int): String {
        return when (status) {
            BluetoothStatusCodes.SUCCESS -> "SUCCESS"
            BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY -> "ERROR_GATT_WRITE_REQUEST_BUSY"
            BluetoothStatusCodes.ERROR_GATT_WRITE_NOT_ALLOWED -> "ERROR_GATT_WRITE_NOT_ALLOWED"
            BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION -> "ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION"
            BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND -> "ERROR_PROFILE_SERVICE_NOT_BOUND"
            BluetoothStatusCodes.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
            else -> "BluetoothStatusCode($status)"
        }
    }

    private fun gattStarted(): AqlBleGattOperationStartResult = AqlBleGattOperationStartResult.Started

    private fun gattNotStarted(message: String, retryable: Boolean = false): AqlBleGattOperationStartResult {
        return AqlBleGattOperationStartResult.NotStarted(retryable = retryable, message = message)
    }

    private fun parseDeviceInfo(raw: String): Result<AqlBleDeviceInfo> {
        return runCatching {
            val json = JSONObject(raw.trim())
            AqlBleDeviceInfo(
                contractVersion = requiredJsonInt(json, AqlBleProvisioningContract.Json.KEY_CONTRACT_VERSION, DEVICE_INFO_LABEL),
                securityVersion = requiredJsonInt(json, AqlBleProvisioningContract.Json.KEY_SECURITY_VERSION, DEVICE_INFO_LABEL),
                deviceUid = requiredJsonString(json, AqlBleProvisioningContract.Json.KEY_DEVICE_UID, DEVICE_INFO_LABEL),
                serialNumber = json.optString(KEY_SERIAL_NUMBER).trim(),
                shortId = requiredJsonString(json, KEY_SHORT_ID, DEVICE_INFO_LABEL),
                brand = json.optString(KEY_BRAND).trim(),
                productId = json.optString(KEY_PRODUCT_ID).trim(),
                productModel = requiredJsonString(json, KEY_PRODUCT_MODEL, DEVICE_INFO_LABEL),
                displayName = requiredJsonString(json, KEY_DISPLAY_NAME, DEVICE_INFO_LABEL),
                hardwareRevision = json.optString(KEY_HARDWARE_REVISION).trim(),
                firmwareVersion = json.optString(KEY_FIRMWARE_VERSION).trim(),
                bleName = requiredJsonString(json, KEY_BLE_NAME, DEVICE_INFO_LABEL),
                deviceNonce = requiredJsonString(json, AqlBleProvisioningContract.Json.KEY_DEVICE_NONCE, DEVICE_INFO_LABEL),
                mode = requiredJsonString(json, KEY_MODE, DEVICE_INFO_LABEL),
                claimRequired = requiredJsonBoolean(json, KEY_CLAIM_REQUIRED, DEVICE_INFO_LABEL),
                physicalReset = requiredJsonBoolean(json, KEY_PHYSICAL_RESET, DEVICE_INFO_LABEL),
                sessionMode = requiredJsonString(json, AqlBleProvisioningContract.Json.KEY_SESSION_MODE, DEVICE_INFO_LABEL),
                devicePublicKey = json.optString(AqlBleProvisioningContract.Json.KEY_DEVICE_PUBLIC_KEY).trim()
            )
        }
    }

    private fun validateDeviceInfo(info: AqlBleDeviceInfo): String? {
        val draft = activeDraft ?: return "Provisioning draft is missing."
        if (info.contractVersion != AqlBleProvisioningContract.CONTRACT_VERSION) return "Unsupported DeviceInfo contractVersion: ${info.contractVersion}."
        if (info.securityVersion != AqlBleProvisioningContract.PROVISIONING_SECURITY_VERSION) return "Unsupported DeviceInfo securityVersion: ${info.securityVersion}."
        if (!info.deviceNonce.isUuidV4()) return "DeviceInfo does not include a valid deviceNonce."
        if (info.brand.isNotBlank() && !info.brand.equals(AqlBleProvisioningContract.BRAND, ignoreCase = true)) return "DeviceInfo brand is not supported: ${info.brand}."

        val expectedUid = draft.candidateId.trim().takeUnless { value -> value.isLikelyBleAddress() }.orEmpty()
        if (expectedUid.isNotBlank() && !info.deviceUid.equals(expectedUid, ignoreCase = true)) return "QR device uid does not match the connected BLE device."
        val expectedBleName = draft.bleName.trim()
        if (expectedBleName.isNotBlank() && info.bleName != expectedBleName) return "QR BLE name does not match the connected BLE device."

        val qrFields = parseQrFields(draft.rawQrPayload)
        val expectedBrand = qrField(qrFields, AqlBleProvisioningContract.Qr.KEY_BRAND)
        if (expectedBrand.isNotBlank() && info.brand.isNotBlank() && !info.brand.equals(expectedBrand, ignoreCase = true)) return "QR brand does not match the connected BLE device."
        val expectedSerialNumber = qrField(qrFields, AqlBleProvisioningContract.Qr.KEY_SERIAL_NUMBER)
        if (expectedSerialNumber.isNotBlank() && info.serialNumber.isNotBlank() && !info.serialNumber.equals(expectedSerialNumber, ignoreCase = true)) return "QR serial number does not match the connected BLE device."
        val expectedProductId = qrField(qrFields, AqlBleProvisioningContract.Qr.KEY_PRODUCT_ID)
        if (expectedProductId.isNotBlank() && info.productId.isNotBlank() && !info.productId.equals(expectedProductId, ignoreCase = true)) return "QR product id does not match the connected BLE device."
        val expectedProductModel = qrField(qrFields, AqlBleProvisioningContract.Qr.KEY_MODEL)
        if (expectedProductModel.isNotBlank() && !info.productModel.equals(expectedProductModel, ignoreCase = true)) return "QR product model does not match the connected BLE device."
        val expectedDisplayName = qrField(qrFields, AqlBleProvisioningContract.Qr.KEY_DISPLAY_NAME)
        if (expectedDisplayName.isNotBlank() && !info.displayName.equals(expectedDisplayName, ignoreCase = true)) return "QR display name does not match the connected BLE device."
        val expectedHardwareRevision = qrField(qrFields, AqlBleProvisioningContract.Qr.KEY_HARDWARE_REVISION)
        if (expectedHardwareRevision.isNotBlank() && info.hardwareRevision.isNotBlank() && !info.hardwareRevision.equals(expectedHardwareRevision, ignoreCase = true)) return "QR hardware revision does not match the connected BLE device."

        if (!isAllowedProvisioningMode(info.mode)) return "Connected BLE device is not in provisioning mode: ${info.mode.ifBlank { "unknown" }}."
        if (info.mode == AqlBleProvisioningContract.Status.FACTORY) {
            if (!info.claimRequired || info.physicalReset) return "Factory QR mode must require claim validation."
            if (info.sessionMode != AqlBleProvisioningContract.SessionMode.QR_CLAIM_SECURE) return "Factory QR mode requires secure QR claim session."
            if (draft.claimCode.isBlank()) return "Factory setup requires the secure QR code."
        }
        if (info.mode == AqlBleProvisioningContract.Status.PHYSICAL_RESET) {
            if (!info.physicalReset) return "DeviceInfo physicalReset flag does not match physical reset mode."
            if (info.claimRequired) return "Physical reset recovery must not require QR claim validation."
            if (info.sessionMode != AqlBleProvisioningContract.SessionMode.PHYSICAL_RESET_SECURE) return "Physical reset recovery requires secure recovery session."
            if (info.devicePublicKey.isBlank()) return "Physical reset recovery device public key is missing."
        }
        if (draft.claimCode.isBlank() && info.mode != AqlBleProvisioningContract.Status.PHYSICAL_RESET) {
            return "Manual BLE setup without QR is accepted only in physical reset mode."
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

    private fun parseQrFields(rawQrPayload: String): Map<String, String> {
        val normalized = rawQrPayload.trim()
        if (normalized.isBlank()) return emptyMap()
        return runCatching {
            if (normalized.startsWith("{")) {
                val json = JSONObject(normalized)
                val fields = mutableMapOf<String, String>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    fields[key.trim().lowercase(Locale.US)] = json.optString(key).trim()
                }
                fields
            } else {
                val query = normalized.substringAfter("?", normalized)
                val fields = mutableMapOf<String, String>()
                query.split("&").asSequence().filter { part -> part.isNotBlank() && part.contains("=") }.forEach { part ->
                    val key = decode(part.substringBefore("=")).trim().lowercase(Locale.US)
                    val value = decode(part.substringAfter("=")).trim()
                    if (key.isNotBlank()) fields[key] = value
                }
                fields
            }
        }.getOrDefault(emptyMap())
    }

    private fun qrField(fields: Map<String, String>, key: String): String = fields[key.trim().lowercase(Locale.US)].orEmpty().trim()

    private fun requiredJsonString(json: JSONObject, key: String, label: String): String {
        return json.optString(key).trim().takeIf { value -> value.isNotBlank() } ?: error("$label field '$key' is missing.")
    }

    private fun requiredJsonInt(json: JSONObject, key: String, label: String): Int {
        val value = json.opt(key)
        val intValue = when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
        return intValue ?: error("$label field '$key' is missing or invalid.")
    }

    private fun requiredJsonBoolean(json: JSONObject, key: String, label: String): Boolean {
        return when (val value = json.opt(key)) {
            is Boolean -> value
            else -> error("$label field '$key' is missing or invalid.")
        }
    }

    private fun String.isLikelyBleAddress(): Boolean = matches(Regex("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))
    private fun String.isUuidV4(): Boolean = matches(Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"))

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
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

    private fun emit(event: AqlBleProvisioningGattEvent) { _events.tryEmit(event) }
    private fun decode(value: String): String = runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)

    private data class AqlBleDeviceInfo(
        val contractVersion: Int,
        val securityVersion: Int,
        val deviceUid: String,
        val serialNumber: String,
        val shortId: String,
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
        const val EVENT_BUFFER_CAPACITY = 64
        const val REQUESTED_ATT_MTU = 517
        const val DEFAULT_ATT_MTU = 23
        const val ATT_MTU_OVERHEAD_BYTES = 3
        const val DEFAULT_ATT_PAYLOAD_BYTES = 20
        const val STATUS_POLL_INTERVAL_MS = 1_500L
        const val MAX_DEVICE_INFO_READ_ATTEMPTS = 3
        const val DEVICE_INFO_RETRY_DELAY_MS = 350L
        const val GATT_OPERATION_START_RETRY_DELAY_MS = 700L
        const val MAX_GATT_OPERATION_START_RETRIES = 2
        const val DEVICE_INFO_LABEL = "DeviceInfo"
        const val KEY_SERIAL_NUMBER = "serialNumber"
        const val KEY_SHORT_ID = "shortId"
        const val KEY_BRAND = "brand"
        const val KEY_PRODUCT_ID = "productId"
        const val KEY_PRODUCT_MODEL = "productModel"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_HARDWARE_REVISION = "hardwareRevision"
        const val KEY_FIRMWARE_VERSION = "firmwareVersion"
        const val KEY_BLE_NAME = "bleName"
        const val KEY_MODE = "mode"
        const val KEY_CLAIM_REQUIRED = "claimRequired"
        const val KEY_PHYSICAL_RESET = "physicalReset"
        val SERVICE_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.SERVICE_UUID)
        val DEVICE_INFO_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.DEVICE_INFO_UUID)
        val START_SESSION_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.START_SESSION_UUID)
        val WIFI_CREDENTIALS_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.WIFI_CREDENTIALS_UUID)
        val PROVISIONING_STATUS_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.PROVISIONING_STATUS_UUID)
        val RUNTIME_ENDPOINT_UUID: UUID = UUID.fromString(AqlBleProvisioningContract.RUNTIME_ENDPOINT_UUID)
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
