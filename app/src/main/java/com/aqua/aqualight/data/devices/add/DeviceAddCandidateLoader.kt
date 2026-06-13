package com.aqua.aqualight.data.devices.add

import android.content.Context
import com.aqua.aqualight.data.devices.DeviceIdentityMatcher
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
        savedDevices: List<DevicesDataStoreManager.DeviceInfo>
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
                savedDevices.none { savedDevice ->
                    DeviceIdentityMatcher.samePhysicalDevice(
                        savedDevice = savedDevice,
                        discoveredDevice = device
                    )
                }
            }
            .mapNotNull { device ->
                val definition = AquaDeviceCatalog.findByProductId(
                    productId = device.productId
                ) ?: return@mapNotNull null

                val localKey = device.deviceUid
                    ?.takeIf { value -> value.isNotBlank() }
                    ?: device.macAddress
                        ?.takeIf { value -> value.isNotBlank() }
                    ?: device.shortId
                        ?.takeIf { value -> value.isNotBlank() }
                    ?: device.id.toString()

                DeviceAddCandidate(
                    key = "local:$localKey",
                    source = DeviceAddSource.LOCAL_NETWORK,
                    displayName = device.displayName.ifBlank { definition.displayName },
                    familyName = device.productFamily.ifBlank { definition.productFamily },
                    productId = definition.productId,
                    productKey = definition.productKey,
                    category = definition.category,
                    setupCode = definition.setupCode,
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
        savedDevices: List<DevicesDataStoreManager.DeviceInfo>
    ): List<DeviceAddCandidate> {
        val filteredSetupCandidates = setupCandidates.filter { candidate ->
            val shortId = candidate.setupShortId?.trim().orEmpty()

            if (shortId.isBlank()) {
                return@filter true
            }

            val alreadySaved = savedDevices.any { savedDevice ->
                DeviceIdentityMatcher.matchesSetupShortId(
                    savedDevice = savedDevice,
                    setupShortId = shortId
                )
            }

            val alreadyVisibleAsLocalDevice = localCandidates.any { localCandidate ->
                localCandidate.localDevice?.let { localDevice ->
                    DeviceIdentityMatcher.matchesSetupShortId(
                        discoveredDevice = localDevice,
                        setupShortId = shortId
                    )
                } == true
            }

            !alreadySaved && !alreadyVisibleAsLocalDevice
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