package com.aqua.aqualight.data.devices.provisioning

import android.content.Context
import com.aqua.aqualight.application.devices.provisioning.ProvisioningCandidateSnapshot
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDiscoveryOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningQrPayload
import com.aqua.aqualight.application.devices.provisioning.ProvisioningManualPreflightResult
import com.aqua.aqualight.application.devices.provisioning.ProvisioningScanFailure
import com.aqua.aqualight.application.devices.provisioning.ProvisioningScanStartResult
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningCandidate
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningScanner
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleDeviceInfoPreflightClient
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleScanFailure
import com.aqua.aqualight.data.devices.provisioning.ble.BleProvisioningScanner
import com.aqua.aqualight.data.devices.provisioning.ble.DefaultBleProvisioningScanner
import com.aqua.aqualight.data.devices.provisioning.ble.ManualProvisioningPreflightClient
import com.aqua.aqualight.data.devices.provisioning.ble.ManualSetupPreflightResult
import com.aqua.aqualight.data.devices.provisioning.qr.AqlProvisioningQrParser
import com.aqua.aqualight.data.devices.provisioning.qr.AqlProvisioningQrPayload
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningQrSecretStore
import com.aqua.aqualight.data.devices.provisioning.store.ProvisioningQrSecretStorage
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

class DefaultProvisioningDiscoveryOperations(
    private val scanner: BleProvisioningScanner,
    private val repository: DevicesRepository,
    private val qrParser: AqlProvisioningQrParser,
    private val qrSecretStore: ProvisioningQrSecretStorage,
    private val manualPreflightClient: ManualProvisioningPreflightClient
) : ProvisioningDiscoveryOperations {

    override val candidates: Flow<List<ProvisioningCandidateSnapshot>> =
        scanner.candidates.map { candidates ->
            candidates.map(AqlBleProvisioningCandidate::toApplicationSnapshot)
        }

    override val scanFailures: Flow<ProvisioningScanFailure> =
        scanner.failures.map(AqlBleScanFailure::toApplicationFailure)

    override fun startScan(): ProvisioningScanStartResult =
        when (val result = scanner.startScan()) {
            AqlBleProvisioningScanner.StartResult.Started ->
                ProvisioningScanStartResult.Started
            AqlBleProvisioningScanner.StartResult.MissingPermission ->
                ProvisioningScanStartResult.MissingPermission
            AqlBleProvisioningScanner.StartResult.BluetoothUnavailable ->
                ProvisioningScanStartResult.BluetoothUnavailable
            AqlBleProvisioningScanner.StartResult.BluetoothOff ->
                ProvisioningScanStartResult.BluetoothOff
            is AqlBleProvisioningScanner.StartResult.Failed ->
                ProvisioningScanStartResult.Failed(result.failure.toApplicationFailure())
        }

    override fun stopScan() {
        scanner.stopScan()
    }

    override fun clearCandidates() {
        scanner.clearCandidates()
    }

    override fun parseQr(rawValue: String): Result<ProvisioningQrPayload> =
        qrParser.parse(rawValue).map { payload ->
            val secretReference = qrSecretStore.create(
                claimCode = payload.claimCode,
                rawPayload = payload.raw
            )
            payload.toApplicationPayload(secretReference)
        }

    override fun discardQrPayload(payload: ProvisioningQrPayload) {
        qrSecretStore.remove(payload.secretReference)
    }

    override suspend fun awaitQrCandidate(
        payload: ProvisioningQrPayload,
        timeoutMillis: Long
    ): ProvisioningCandidateSnapshot? {
        val targetBleName = payload.bleName.trim()
        if (targetBleName.isBlank()) return null

        return withTimeoutOrNull(timeoutMillis) {
            scanner.candidates
                .map { candidates ->
                    candidates.firstOrNull { candidate ->
                        candidate.name.equals(targetBleName, ignoreCase = false)
                    }
                }
                .filterNotNull()
                .first()
                .toApplicationSnapshot()
        }
    }

    override suspend fun verifyManualCandidate(
        candidate: ProvisioningCandidateSnapshot
    ): ProvisioningManualPreflightResult =
        when (val result = manualPreflightClient.verifyManualSetup(candidate.address)) {
            is ManualSetupPreflightResult.Allowed ->
                ProvisioningManualPreflightResult.Allowed(
                    candidate = candidate.copy(
                        deviceUid = result.deviceUid,
                        bleName = result.bleName,
                        displayTitle = result.displayName,
                        model = result.productModel,
                        displaySerial = result.serialNumber
                    )
                )
            ManualSetupPreflightResult.QrRequired -> ProvisioningManualPreflightResult.QrRequired
            ManualSetupPreflightResult.ResetRequired -> ProvisioningManualPreflightResult.ResetRequired
            ManualSetupPreflightResult.MissingPermission -> ProvisioningManualPreflightResult.MissingPermission
            ManualSetupPreflightResult.BluetoothOff -> ProvisioningManualPreflightResult.BluetoothOff
            ManualSetupPreflightResult.BluetoothUnavailable -> ProvisioningManualPreflightResult.BluetoothUnavailable
            ManualSetupPreflightResult.ConnectionFailed -> ProvisioningManualPreflightResult.ConnectionFailed
            ManualSetupPreflightResult.IncompatibleDevice -> ProvisioningManualPreflightResult.IncompatibleDevice
        }

    override fun hasCandidates(): Boolean = scanner.candidates.value.isNotEmpty()

    override fun isRegistered(deviceUid: String): Boolean {
        if (deviceUid.isBlank()) return false
        return repository.currentDevice(DeviceUid(deviceUid)) != null
    }

    companion object {
        fun create(
            context: Context,
            repository: DevicesRepository
        ): DefaultProvisioningDiscoveryOperations =
            DefaultProvisioningDiscoveryOperations(
                scanner = DefaultBleProvisioningScanner(context.applicationContext),
                repository = repository,
                qrParser = AqlProvisioningQrParser(),
                qrSecretStore = AqlProvisioningQrSecretStore(context.applicationContext),
                manualPreflightClient = AqlBleDeviceInfoPreflightClient(context.applicationContext)
            )
    }
}

internal fun AqlBleProvisioningCandidate.toApplicationSnapshot():
    ProvisioningCandidateSnapshot = ProvisioningCandidateSnapshot(
        address = address,
        bleName = name,
        rssi = rssi,
        deviceUid = deviceUid,
        displayTitle = displayTitle,
        model = model,
        displaySerial = displaySerial,
        displayStatus = displayStatus,
        rawAdvertisementPayload = rawAdvertisementPayload
    )

internal fun AqlProvisioningQrPayload.toApplicationPayload(
    secretReference: String
): ProvisioningQrPayload = ProvisioningQrPayload(
    deviceUid = deviceUid.value,
    serialNumber = serialNumber,
    productId = productId,
    model = model,
    displayName = displayName,
    hardwareRevision = hardwareRevision,
    skuCode = skuCode,
    provisioningId = provisioningId,
    secretReference = secretReference,
    bleName = bleName
)

private fun AqlBleScanFailure.toApplicationFailure(): ProvisioningScanFailure =
    ProvisioningScanFailure.valueOf(name)
