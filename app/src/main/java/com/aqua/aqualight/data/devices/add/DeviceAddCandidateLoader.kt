package com.aqua.aqualight.data.devices.add

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.discovery.DeviceDiscoveryService
import com.aqua.aqualight.data.devices.discovery.DeviceScanReason
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

class DeviceAddCandidateLoader(
    private val context: Context,
    private val devicesStore: DevicesDataStoreManager
) {

    suspend fun loadCandidates(): List<DeviceAddCandidate> = coroutineScope {
        val savedDevices = devicesStore.devicesFlow.first()

        val setupDeferred = async {
            SetupApScanner.scan(context)
        }

        val localDeferred = async {
            loadLocalNetworkCandidates(
                savedDevices = savedDevices
            )
        }

        val setupCandidates = setupDeferred.await()
        val localCandidates = localDeferred.await()

        return@coroutineScope mergeCandidates(
            setupCandidates = setupCandidates,
            localCandidates = localCandidates,
            savedDevices = savedDevices
        )
    }

    private suspend fun loadLocalNetworkCandidates(
        savedDevices: List<DevicesDataStoreManager.DeviceInfoUi>
    ): List<DeviceAddCandidate> {
        val savedDeviceIds = savedDevices
            .map { device -> device.id }
            .toSet()

        val result = DeviceDiscoveryService.scan(
            context = context,
            timeoutMs = LOCAL_DISCOVERY_TIMEOUT_MS,
            reason = DeviceScanReason.MANUAL_SCAN
        )

        if (result.error != null) {
            return emptyList()
        }

        return result.devices
            .filter { device ->
                device.id !in savedDeviceIds
            }
            .mapNotNull { device ->
                val definition = AquaDeviceCatalog.findByType(
                    type = device.deviceType
                ) ?: return@mapNotNull null

                DeviceAddCandidate(
                    key = "local:${device.id}",
                    source = DeviceAddSource.LOCAL_NETWORK,
                    displayName = definition.displayName,
                    familyName = definition.family.displayName,
                    deviceType = definition.type,
                    stateText = "Already connected",
                    actionText = "Add",
                    localDevice = device
                )
            }
    }

    private fun mergeCandidates(
        setupCandidates: List<DeviceAddCandidate>,
        localCandidates: List<DeviceAddCandidate>,
        savedDevices: List<DevicesDataStoreManager.DeviceInfoUi>
    ): List<DeviceAddCandidate> {
        val savedShortIds = savedDevices
            .flatMap { device ->
                buildList {
                    add(device.id.toString())

                    val serialSuffix = device.serial
                        .substringAfterLast(
                            delimiter = "-",
                            missingDelimiterValue = ""
                        )
                        .trim()

                    if (serialSuffix.isNotBlank()) {
                        add(serialSuffix)
                    }
                }
            }
            .toSet()

        val localDeviceIds = localCandidates
            .mapNotNull { candidate ->
                candidate.localDevice?.id?.toString()
            }
            .toSet()

        val filteredSetupCandidates = setupCandidates.filter { candidate ->
            val shortId = candidate.setupShortId?.trim().orEmpty()

            if (shortId.isBlank()) {
                return@filter true
            }

            shortId !in savedShortIds &&
                shortId !in localDeviceIds
        }

        return buildList {
            addAll(filteredSetupCandidates)
            addAll(localCandidates)
        }
    }

    private companion object {
        const val LOCAL_DISCOVERY_TIMEOUT_MS = 3_000L
    }
}