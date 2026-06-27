package com.aqua.aqualight.data.devices.provisioning.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AqlBleProvisioningScanner(
    context: Context,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val lock = Any()
    private val advertisementParser = AqlBleProvisioningAdvertisementParser()
    private val candidatesByAddress = linkedMapOf<String, AqlBleProvisioningCandidate>()

    private val _candidates = MutableStateFlow<List<AqlBleProvisioningCandidate>>(emptyList())
    val candidates: StateFlow<List<AqlBleProvisioningCandidate>> = _candidates.asStateFlow()

    @Volatile
    private var scanCallback: ScanCallback? = null

    fun startScan(): StartResult {
        if (!hasRequiredPermissions()) {
            return StartResult.MissingPermission
        }

        val adapter = bluetoothManager?.adapter
            ?: return StartResult.BluetoothUnavailable

        if (!adapter.isEnabled) {
            return StartResult.BluetoothOff
        }

        val scanner = adapter.bluetoothLeScanner
            ?: return StartResult.BluetoothUnavailable

        if (scanCallback != null) {
            return StartResult.Started
        }

        clearCandidates()

        val callback = createScanCallback()
        scanCallback = callback

        return try {
            scanner.startScan(
                scanFilters(),
                scanSettings(),
                callback
            )
            StartResult.Started
        } catch (securityException: SecurityException) {
            scanCallback = null
            StartResult.MissingPermission
        } catch (error: Throwable) {
            scanCallback = null
            StartResult.Failed(error.message.orEmpty())
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val callback = scanCallback ?: return
        scanCallback = null

        runCatching {
            bluetoothManager
                ?.adapter
                ?.bluetoothLeScanner
                ?.stopScan(callback)
        }
    }

    fun clearCandidates() {
        synchronized(lock) {
            candidatesByAddress.clear()
            _candidates.value = emptyList()
        }
    }

    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return runCatching {
            bluetoothManager?.adapter?.isEnabled == true
        }.getOrDefault(false)
    }

    private fun createScanCallback(): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                upsertCandidate(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    upsertCandidate(result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                scanCallback = null
            }
        }
    }

    private fun upsertCandidate(result: ScanResult) {
        val address = runCatching { result.device.address }
            .getOrNull()
            ?.takeIf { value -> value.isNotBlank() }
            ?: return

        val now = clockMillis()
        val fallbackName = runCatching { result.device.name }
            .getOrNull()
            .orEmpty()
            .ifBlank { result.scanRecord?.deviceName.orEmpty() }
            .ifBlank { "AquaLight Device" }

        val advertisement = advertisementParser.parse(
            scanRecord = result.scanRecord,
            fallbackBleName = fallbackName
        )

        synchronized(lock) {
            val previous = candidatesByAddress[address]
            candidatesByAddress[address] = AqlBleProvisioningCandidate(
                address = address,
                name = advertisement.bleName.ifBlank { fallbackName },
                rssi = result.rssi,
                firstSeenAtMillis = previous?.firstSeenAtMillis ?: now,
                lastSeenAtMillis = now,
                deviceUid = advertisement.deviceUid,
                productName = advertisement.displayTitle,
                model = advertisement.model,
                serialNumber = advertisement.displaySerial,
                claimState = advertisement.displayStatus,
                rawAdvertisementPayload = advertisement.rawPayload
            )

            _candidates.value = candidatesByAddress.values
                .sortedByDescending { candidate -> candidate.rssi }
        }
    }

    private fun scanFilters(): List<ScanFilter> {
        return listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(AqlBleProvisioningContract.SERVICE_UUID))
                .build()
        )
    }

    private fun scanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    sealed interface StartResult {
        object Started : StartResult
        object MissingPermission : StartResult
        object BluetoothUnavailable : StartResult
        object BluetoothOff : StartResult
        data class Failed(val message: String) : StartResult
    }
}
