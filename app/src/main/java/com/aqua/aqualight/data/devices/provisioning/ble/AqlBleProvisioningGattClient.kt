package com.aqua.aqualight.data.devices.provisioning.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
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
    private var startSessionCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var wifiCredentialsCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var provisioningStatusCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var runtimeEndpointCharacteristic: BluetoothGattCharacteristic? = null

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

        startSessionCharacteristic = null
        wifiCredentialsCharacteristic = null
        provisioningStatusCharacteristic = null
        runtimeEndpointCharacteristic = null
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emit(AqlBleProvisioningGattEvent.Failed("BLE connection failed with status $status."))
                close()
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
                emit(AqlBleProvisioningGattEvent.Failed("BLE service discovery failed with status $status."))
                close()
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                emit(AqlBleProvisioningGattEvent.Failed("AquaLight provisioning service was not found."))
                close()
                return
            }

            startSessionCharacteristic = service.getCharacteristic(START_SESSION_UUID)
            wifiCredentialsCharacteristic = service.getCharacteristic(WIFI_CREDENTIALS_UUID)
            provisioningStatusCharacteristic = service.getCharacteristic(PROVISIONING_STATUS_UUID)
            runtimeEndpointCharacteristic = service.getCharacteristic(RUNTIME_ENDPOINT_UUID)

            if (startSessionCharacteristic == null || wifiCredentialsCharacteristic == null) {
                emit(AqlBleProvisioningGattEvent.Failed("Required provisioning characteristics were not found."))
                close()
                return
            }

            emit(AqlBleProvisioningGattEvent.ServicesDiscovered)
            writeStartSession(gatt)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emit(AqlBleProvisioningGattEvent.Failed("BLE write failed with status $status."))
                close()
                return
            }

            when (characteristic.uuid) {
                START_SESSION_UUID -> {
                    emit(AqlBleProvisioningGattEvent.StartSessionWritten)
                    writeWifiCredentials(gatt)
                }

                WIFI_CREDENTIALS_UUID -> {
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
                emit(AqlBleProvisioningGattEvent.Failed("BLE read failed with status $status."))
                return
            }

            handleCharacteristicValue(
                gatt = gatt,
                characteristic = characteristic,
                value = characteristic.value ?: ByteArray(0)
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
            emit(AqlBleProvisioningGattEvent.Failed("Bluetooth connect permission is missing."))
            close()
            return
        }

        if (!gatt.discoverServices()) {
            emit(AqlBleProvisioningGattEvent.Failed("BLE service discovery could not be started."))
            close()
        }
    }

    private fun writeStartSession(gatt: BluetoothGatt) {
        val draft = activeDraft ?: return
        val characteristic = startSessionCharacteristic
        if (characteristic == null) {
            emit(AqlBleProvisioningGattEvent.Failed("Start session characteristic is missing."))
            close()
            return
        }

        writeString(
            gatt = gatt,
            characteristic = characteristic,
            value = codec.startSessionJson(draft)
        )
    }

    private fun writeWifiCredentials(gatt: BluetoothGatt) {
        val draft = activeDraft ?: return
        val characteristic = wifiCredentialsCharacteristic
        if (characteristic == null) {
            emit(AqlBleProvisioningGattEvent.Failed("Wi-Fi credentials characteristic is missing."))
            close()
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
            readRuntimeEndpoint(gatt)
            return
        }

        if (!hasConnectPermission()) {
            emit(AqlBleProvisioningGattEvent.Failed("Bluetooth connect permission is missing."))
            close()
            return
        }

        if (!gatt.readCharacteristic(characteristic)) {
            readRuntimeEndpoint(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readRuntimeEndpoint(gatt: BluetoothGatt) {
        val characteristic = runtimeEndpointCharacteristic
        if (characteristic == null) {
            return
        }

        if (!hasConnectPermission()) {
            emit(AqlBleProvisioningGattEvent.Failed("Bluetooth connect permission is missing."))
            close()
            return
        }

        gatt.readCharacteristic(characteristic)
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

                if (
                    statusMessage.status == AqlProvisioningStatus.WEB_SOCKET_TOKEN_READY ||
                    statusMessage.status == AqlProvisioningStatus.COMPLETED
                ) {
                    readRuntimeEndpoint(gatt)
                }
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

    @SuppressLint("MissingPermission")
    private fun writeString(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: String
    ) {
        if (!hasConnectPermission()) {
            emit(AqlBleProvisioningGattEvent.Failed("Bluetooth connect permission is missing."))
            close()
            return
        }

        val bytes = value.toByteArray(Charsets.UTF_8)

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
            emit(AqlBleProvisioningGattEvent.Failed("BLE write operation could not be started."))
            close()
        }
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

    private fun emit(event: AqlBleProvisioningGattEvent) {
        _events.tryEmit(event)
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 64

        val SERVICE_UUID: UUID =
            UUID.fromString(AqlBleProvisioningContract.SERVICE_UUID)
        val START_SESSION_UUID: UUID =
            UUID.fromString(AqlBleProvisioningContract.START_SESSION_UUID)
        val WIFI_CREDENTIALS_UUID: UUID =
            UUID.fromString(AqlBleProvisioningContract.WIFI_CREDENTIALS_UUID)
        val PROVISIONING_STATUS_UUID: UUID =
            UUID.fromString(AqlBleProvisioningContract.PROVISIONING_STATUS_UUID)
        val RUNTIME_ENDPOINT_UUID: UUID =
            UUID.fromString(AqlBleProvisioningContract.RUNTIME_ENDPOINT_UUID)
    }
}
