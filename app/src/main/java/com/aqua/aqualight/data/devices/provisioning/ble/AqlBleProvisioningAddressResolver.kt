package com.aqua.aqualight.data.devices.provisioning.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningBleAddressCache
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class AqlBleProvisioningAddressResolver(
    context: Context
) {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val preflightClient = AqlBleDeviceInfoPreflightClient(appContext)

    suspend fun resolveAddress(
        bleNameOrAddress: String
    ): Result<String> {
        val target = bleNameOrAddress.trim()

        if (target.isBlank()) {
            return Result.failure(
                IllegalArgumentException("QR payload does not contain a BLE name.")
            )
        }

        if (MAC_ADDRESS_REGEX.matches(target)) {
            return Result.success(target)
        }

        val scanner = scannerOrFailure().getOrElse { error ->
            return Result.failure(error)
        }

        val exact = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            awaitExactNameAddress(
                scanner = scanner,
                targetName = target
            )
        }

        return exact ?: Result.failure(
            IllegalStateException("AquaLight BLE device '$target' was not found nearby.")
        )
    }

    suspend fun resolveQrAddress(
        draft: AqlProvisioningDraft
    ): Result<String> {
        val existingAddress = draft.bleAddress.trim()
        val targetName = draft.bleName.trim()

        if (existingAddress.isNotBlank() && MAC_ADDRESS_REGEX.matches(existingAddress)) {
            if (targetName.isNotBlank()) {
                AqlProvisioningBleAddressCache.put(targetName, existingAddress)
            }
            return Result.success(existingAddress)
        }

        val cachedAddress = AqlProvisioningBleAddressCache.get(targetName)
        if (cachedAddress.isNotBlank() && MAC_ADDRESS_REGEX.matches(cachedAddress)) {
            return Result.success(cachedAddress)
        }

        if (targetName.isBlank()) {
            return Result.failure(
                IllegalArgumentException("QR payload does not contain a BLE name.")
            )
        }

        val scanner = scannerOrFailure().getOrElse { error ->
            return Result.failure(error)
        }

        val candidates = linkedMapOf<String, ScanCandidate>()

        val exact = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            awaitExactNameAddress(
                scanner = scanner,
                targetName = targetName,
                candidates = candidates
            )
        }

        if (exact != null) {
            return exact.onSuccess { address ->
                AqlProvisioningBleAddressCache.put(targetName, address)
            }
        }

        return verifyQrCandidates(
            draft = draft,
            targetName = targetName,
            candidates = candidates.values.toList()
        )
    }

    private fun scannerOrFailure(): Result<BluetoothLeScanner> {
        if (!hasRequiredPermissions()) {
            return Result.failure(
                SecurityException("Bluetooth scan/connect permission is required.")
            )
        }

        val adapter = bluetoothManager?.adapter
            ?: return Result.failure(IllegalStateException("Bluetooth adapter is unavailable."))

        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("Bluetooth is disabled."))
        }

        return adapter.bluetoothLeScanner?.let { scanner ->
            Result.success(scanner)
        } ?: Result.failure(IllegalStateException("Bluetooth LE scanner is unavailable."))
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitExactNameAddress(
        scanner: BluetoothLeScanner,
        targetName: String,
        candidates: MutableMap<String, ScanCandidate> = linkedMapOf()
    ): Result<String> {
        return suspendCancellableCoroutine { continuation ->
            lateinit var callback: ScanCallback

            callback = object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult
                ) {
                    val address = runCatching {
                        result.device.address
                    }.getOrNull().orEmpty()

                    if (address.isBlank()) {
                        return
                    }

                    val candidate = result.toScanCandidate(address)
                    candidates.putIfAbsent(address, candidate)

                    if (!candidate.matchesTargetName(targetName)) {
                        return
                    }

                    stopScan(scanner, callback)

                    if (continuation.isActive) {
                        continuation.resume(Result.success(address))
                    }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { result ->
                        onScanResult(0, result)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    stopScan(scanner, callback)

                    if (continuation.isActive) {
                        continuation.resume(
                            Result.failure(
                                IllegalStateException("BLE resolve scan failed with code $errorCode.")
                            )
                        )
                    }
                }
            }

            continuation.invokeOnCancellation {
                stopScan(scanner, callback)
            }

            runCatching {
                scanner.startScan(
                    scanFilters(),
                    scanSettings(),
                    callback
                )
            }.onFailure { error ->
                stopScan(scanner, callback)

                if (continuation.isActive) {
                    continuation.resume(
                        Result.failure(error)
                    )
                }
            }
        }
    }

    private suspend fun verifyQrCandidates(
        draft: AqlProvisioningDraft,
        targetName: String,
        candidates: List<ScanCandidate>
    ): Result<String> {
        if (candidates.isEmpty()) {
            return Result.failure(
                IllegalStateException("AquaLight BLE device '$targetName' was not found nearby.")
            )
        }

        val orderedCandidates = candidates
            .distinctBy { candidate -> candidate.address }
            .take(MAX_QR_CANDIDATES_TO_PREFLIGHT)

        var lastRejection = ""

        for (candidate in orderedCandidates) {
            when (val result = preflightClient.verifyQrCandidate(candidate.address, draft)) {
                is QrCandidatePreflightResult.Allowed -> {
                    delay(QR_PREFLIGHT_GATT_SETTLE_DELAY_MS)
                    AqlProvisioningBleAddressCache.put(targetName, result.bleAddress)
                    return Result.success(result.bleAddress)
                }

                is QrCandidatePreflightResult.Rejected -> {
                    lastRejection = result.message
                }

                is QrCandidatePreflightResult.Failed -> {
                    lastRejection = result.message
                }
            }
        }

        return Result.failure(
            IllegalStateException(
                lastRejection.ifBlank {
                    "Nearby AquaLight setup devices were found, but none matched the scanned QR code."
                }
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun ScanResult.toScanCandidate(address: String): ScanCandidate {
        val advertisedName = scanRecord?.deviceName.orEmpty()
        val deviceName = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        ) {
            ""
        } else {
            try {
                device.name.orEmpty()
            } catch (error: SecurityException) {
                Log.w(
                    TAG,
                    "BLE device name became unavailable after the permission check.",
                    error
                )
                ""
            }
        }

        return ScanCandidate(
            address = address,
            advertisedName = advertisedName,
            deviceName = deviceName
        )
    }

    private fun ScanCandidate.matchesTargetName(
        targetName: String
    ): Boolean {
        return advertisedName.equals(targetName, ignoreCase = true) ||
            deviceName.equals(targetName, ignoreCase = true)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan(
        scanner: BluetoothLeScanner,
        callback: ScanCallback
    ) {
        runCatching {
            scanner.stopScan(callback)
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

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private data class ScanCandidate(
        val address: String,
        val advertisedName: String,
        val deviceName: String
    )

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 12_000L
        const val MAX_QR_CANDIDATES_TO_PREFLIGHT = 4
        const val QR_PREFLIGHT_GATT_SETTLE_DELAY_MS = 250L
        const val TAG = "AqlBleAddressResolver"
        val MAC_ADDRESS_REGEX =
            Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")
    }
}
