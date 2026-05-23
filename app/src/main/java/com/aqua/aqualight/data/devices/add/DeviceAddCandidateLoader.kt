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
        val savedDeviceIds = devicesStore.devicesFlow
            .first()
            .map { device -> device.id }
            .toSet()

        val setupDeferred = async {
            SetupApScanner.scan(context)
        }

        val localDeferred = async {
            loadLocalNetworkCandidates(
                savedDeviceIds = savedDeviceIds
            )
        }

        val setupCandidates = setupDeferred.await()
        val localCandidates = localDeferred.await()

        return@coroutineScope mergeCandidates(
            setupCandidates = setupCandidates,
            localCandidates = localCandidates
        )
    }

    private suspend fun loadLocalNetworkCandidates(
        savedDeviceIds: Set<Long>
    ): List<DeviceAddCandidate> {
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
                    stateText = "Already connected to Wi-Fi",
                    actionText = "Add",
                    localDevice = device
                )
            }
    }

    private fun mergeCandidates(
        setupCandidates: List<DeviceAddCandidate>,
        localCandidates: List<DeviceAddCandidate>
    ): List<DeviceAddCandidate> {
        val localKeys = localCandidates
            .mapNotNull { candidate ->
                candidate.setupShortId
            }
            .toSet()

        val filteredSetupCandidates = setupCandidates.filter { candidate ->
            candidate.setupShortId !in localKeys
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