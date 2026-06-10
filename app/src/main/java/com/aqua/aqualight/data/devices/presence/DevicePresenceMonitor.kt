package com.aqua.aqualight.data.devices.presence

import android.content.Context
import com.aqua.aqualight.data.devices.DeviceIdentityMatcher
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.DevicesDataStoreManager.DeviceInfoUi
import com.aqua.aqualight.data.devices.discovery.DeviceDiscoveryService
import com.aqua.aqualight.data.devices.discovery.DeviceScanReason
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object DevicePresenceMonitor {

    private const val SCAN_INTERVAL_MS = 15_000L
    private const val MONITOR_SCAN_TIMEOUT_MS = 3_000L
    private const val LIVE_CHECK_TIMEOUT_MS = 900L

    private const val RECENT_ONLINE_STATUS_VALID_MS = 20_000L
    private const val OFFLINE_AFTER_MISSED_CHECKS = 4

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val _statuses = MutableStateFlow<Map<Long, DeviceStatusState>>(
        emptyMap()
    )

    val statuses: StateFlow<Map<Long, DeviceStatusState>> =
        _statuses.asStateFlow()

    private val missedChecks = mutableMapOf<Long, Int>()

    private var monitorJob: Job? = null

    fun start(
        context: Context
    ) {
        if (monitorJob?.isActive == true) {
            return
        }

        val appContext = context.applicationContext
        val devicesStore = DevicesDataStoreManager.create(
            appContext
        )

        monitorJob = scope.launch {
            refreshOnce(
                context = appContext,
                devicesStore = devicesStore
            )

            while (isActive) {
                delay(
                    SCAN_INTERVAL_MS
                )

                refreshOnce(
                    context = appContext,
                    devicesStore = devicesStore
                )
            }
        }
    }

    suspend fun checkDeviceNow(
        context: Context,
        deviceId: Long,
        knownIp: String,
        allowRecentOnlineCache: Boolean = true
    ): DeviceStatusState? {
        val appContext = context.applicationContext
        val devicesStore = DevicesDataStoreManager.create(
            appContext
        )

        val savedDevice = devicesStore.devicesFlow
            .first()
            .firstOrNull { device ->
                device.id == deviceId
            } ?: return null

        val now = System.currentTimeMillis()

        val currentState = _statuses.value[deviceId]

        if (
            allowRecentOnlineCache &&
            isRecentOnlineState(
                state = currentState,
                now = now
            )
        ) {
            return currentState
        }

        setCheckingState(
            device = savedDevice,
            now = now
        )

        val result = DeviceDiscoveryService.scan(
            context = appContext,
            timeoutMs = LIVE_CHECK_TIMEOUT_MS,
            reason = DeviceScanReason.LIVE_CHECK
        )

        val latestNow = System.currentTimeMillis()

        if (
            result.skippedBecauseBusy ||
            result.error != null
        ) {
            val fallbackState = if (allowRecentOnlineCache) {
                currentState ?: buildUnknownState(
                    device = savedDevice,
                    now = latestNow,
                    ipOverride = knownIp.ifBlank {
                        savedDevice.ip
                    }
                )
            } else {
                buildOfflineState(
                    device = savedDevice,
                    now = latestNow,
                    missedCount = missedChecks.getOrDefault(
                        savedDevice.id,
                        0
                    ) + 1,
                    previousState = currentState,
                    ipOverride = knownIp.ifBlank {
                        savedDevice.ip
                    }
                )
            }

            missedChecks[savedDevice.id] = fallbackState.missedChecks

            upsertStatus(
                state = fallbackState
            )

            return fallbackState
        }

        val matchedDevice = findMatchingDiscoveredDevice(
            savedDevice = savedDevice,
            discoveredDevices = result.devices
        )

        return if (matchedDevice != null) {
            devicesStore.updateDevicesLastSeen(
                discovered = listOf(
                    DevicesDataStoreManager.DeviceLastSeenUpdate(
                        id = savedDevice.id,
                        ip = matchedDevice.ip,
                        firmwareBuild = matchedDevice.firmwareBuild,
                        deviceUid = matchedDevice.deviceUid,
                        macAddress = matchedDevice.macAddress,
                        firmwareSerial = matchedDevice.firmwareSerial,
                        deviceType = matchedDevice.deviceType,
                        udpVersion = matchedDevice.udpVersion,
                        tabLight = matchedDevice.tabLight,
                        tabTimer = matchedDevice.tabTimer,
                        tabTemperature = matchedDevice.tabTemperature,
                        productId = matchedDevice.productId,
                        productFamily = matchedDevice.productFamily,
                        productModel = matchedDevice.productModel,
                        hardwareRevision = matchedDevice.hardwareRevision,
                        firmwareVersion = matchedDevice.firmwareVersion,
                        apiVersion = matchedDevice.apiVersion,
                        channelCount = matchedDevice.channelCount,
                        sensorCount = matchedDevice.sensorCount,
                        supportedFeatures = matchedDevice.supportedFeatures,
                        supportedScreens = matchedDevice.supportedScreens
                    )
                )
            )

            missedChecks[savedDevice.id] = 0

            val state = buildState(
                device = savedDevice,
                now = latestNow,
                missedCount = 0,
                lastSeenOverride = latestNow,
                ipOverride = matchedDevice.ip,
                confirmedOnline = true,
                previousState = currentState
            )

            upsertStatus(
                state = state
            )

            state
        } else {
            val missedCount = missedChecks.getOrDefault(
                savedDevice.id,
                0
            ) + 1

            missedChecks[savedDevice.id] = missedCount

            val state = buildState(
                device = savedDevice,
                now = latestNow,
                missedCount = missedCount,
                ipOverride = currentState?.ip?.ifBlank {
                    knownIp.ifBlank {
                        savedDevice.ip
                    }
                } ?: knownIp.ifBlank {
                    savedDevice.ip
                },
                confirmedOnline = false,
                previousState = currentState
            )

            upsertStatus(
                state = state
            )

            state
        }
    }

    private suspend fun refreshOnce(
        context: Context,
        devicesStore: DevicesDataStoreManager
    ) {
        val savedDevices = devicesStore.devicesFlow.first()

        if (savedDevices.isEmpty()) {
            missedChecks.clear()
            _statuses.value = emptyMap()
            return
        }

        val result = DeviceDiscoveryService.scan(
            context = context,
            timeoutMs = MONITOR_SCAN_TIMEOUT_MS,
            reason = DeviceScanReason.MONITOR
        )

        if (
            result.skippedBecauseBusy ||
            result.error != null
        ) {
            emitPassiveStates(
                savedDevices = savedDevices
            )
            return
        }

        applyDiscoveryResult(
            devicesStore = devicesStore,
            savedDevices = savedDevices,
            discoveredDevices = result.devices
        )
    }

    private suspend fun applyDiscoveryResult(
        devicesStore: DevicesDataStoreManager,
        savedDevices: List<DeviceInfoUi>,
        discoveredDevices: List<DiscoveredAquaDevice>
    ) {
        val now = System.currentTimeMillis()

        val matchedByDeviceId = savedDevices.associate { savedDevice ->
            savedDevice.id to findMatchingDiscoveredDevice(
                savedDevice = savedDevice,
                discoveredDevices = discoveredDevices
            )
        }

        val updates = matchedByDeviceId.mapNotNull { entry ->
            val savedDeviceId = entry.key
            val discoveredDevice = entry.value ?: return@mapNotNull null

            DevicesDataStoreManager.DeviceLastSeenUpdate(
                id = savedDeviceId,
                ip = discoveredDevice.ip,
                firmwareBuild = discoveredDevice.firmwareBuild,
                deviceUid = discoveredDevice.deviceUid,
                macAddress = discoveredDevice.macAddress,
                firmwareSerial = discoveredDevice.firmwareSerial,
                deviceType = discoveredDevice.deviceType,
                udpVersion = discoveredDevice.udpVersion,
                tabLight = discoveredDevice.tabLight,
                tabTimer = discoveredDevice.tabTimer,
                tabTemperature = discoveredDevice.tabTemperature,
                productId = discoveredDevice.productId,
                productFamily = discoveredDevice.productFamily,
                productModel = discoveredDevice.productModel,
                hardwareRevision = discoveredDevice.hardwareRevision,
                firmwareVersion = discoveredDevice.firmwareVersion,
                apiVersion = discoveredDevice.apiVersion,
                channelCount = discoveredDevice.channelCount,
                sensorCount = discoveredDevice.sensorCount,
                supportedFeatures = discoveredDevice.supportedFeatures,
                supportedScreens = discoveredDevice.supportedScreens
            )
        }

        if (updates.isNotEmpty()) {
            devicesStore.updateDevicesLastSeen(
                discovered = updates
            )
        }

        val states = savedDevices.associate { savedDevice ->
            val matchedDevice = matchedByDeviceId[savedDevice.id]

            val missedCount = if (matchedDevice != null) {
                0
            } else {
                missedChecks.getOrDefault(
                    savedDevice.id,
                    0
                ) + 1
            }

            missedChecks[savedDevice.id] = missedCount

            val previousState = _statuses.value[savedDevice.id]

            savedDevice.id to buildState(
                device = savedDevice,
                now = now,
                missedCount = missedCount,
                lastSeenOverride = if (matchedDevice != null) {
                    now
                } else {
                    null
                },
                ipOverride = matchedDevice?.ip,
                confirmedOnline = matchedDevice != null,
                previousState = previousState
            )
        }

        _statuses.value = states
    }

    private fun emitPassiveStates(
        savedDevices: List<DeviceInfoUi>
    ) {
        val now = System.currentTimeMillis()

        val currentStates = _statuses.value

        val states = savedDevices.associate { savedDevice ->
            val previousState = currentStates[savedDevice.id]

            savedDevice.id to (previousState?.copy(
                lastCheckedMillis = now
            ) ?: buildUnknownState(
                device = savedDevice,
                now = now
            ))
        }

        _statuses.value = states
    }

    private fun setCheckingState(
        device: DeviceInfoUi,
        now: Long
    ) {
        val previousState = _statuses.value[device.id]

        val state = DeviceStatusState(
            deviceId = device.id,
            ip = previousState?.ip ?: device.ip,
            status = DeviceConnectionStatus.CHECKING,
            isOnline = previousState?.isOnline == true,
            lastSeenMillis = previousState?.lastSeenMillis ?: device.lastSeenMillis,
            lastCheckedMillis = now,
            missedChecks = missedChecks.getOrDefault(
                device.id,
                0
            )
        )

        upsertStatus(
            state = state
        )
    }

    private fun buildState(
        device: DeviceInfoUi,
        now: Long,
        missedCount: Int,
        lastSeenOverride: Long? = null,
        ipOverride: String? = null,
        confirmedOnline: Boolean = false,
        previousState: DeviceStatusState? = null
    ): DeviceStatusState {
        val lastSeen = when {
            confirmedOnline -> {
                lastSeenOverride ?: now
            }

            previousState != null -> {
                previousState.lastSeenMillis
            }

            else -> {
                device.lastSeenMillis
            }
        }

        val status = when {
            confirmedOnline -> {
                DeviceConnectionStatus.ONLINE
            }

            lastSeen <= 0L && missedCount <= 0 -> {
                DeviceConnectionStatus.UNKNOWN
            }

            missedCount < OFFLINE_AFTER_MISSED_CHECKS -> {
                DeviceConnectionStatus.STALE
            }

            else -> {
                DeviceConnectionStatus.OFFLINE
            }
        }

        return DeviceStatusState(
            deviceId = device.id,
            ip = ipOverride ?: previousState?.ip ?: device.ip,
            status = status,
            isOnline = confirmedOnline,
            lastSeenMillis = lastSeen,
            lastCheckedMillis = now,
            missedChecks = missedCount
        )
    }

    private fun buildUnknownState(
        device: DeviceInfoUi,
        now: Long,
        ipOverride: String? = null
    ): DeviceStatusState {
        return DeviceStatusState(
            deviceId = device.id,
            ip = ipOverride ?: device.ip,
            status = DeviceConnectionStatus.UNKNOWN,
            isOnline = false,
            lastSeenMillis = device.lastSeenMillis,
            lastCheckedMillis = now,
            missedChecks = missedChecks.getOrDefault(
                device.id,
                0
            )
        )
    }

    private fun buildOfflineState(
        device: DeviceInfoUi,
        now: Long,
        missedCount: Int,
        previousState: DeviceStatusState?,
        ipOverride: String? = null
    ): DeviceStatusState {
        return buildState(
            device = device,
            now = now,
            missedCount = missedCount,
            ipOverride = ipOverride,
            confirmedOnline = false,
            previousState = previousState
        )
    }

    private fun findMatchingDiscoveredDevice(
        savedDevice: DeviceInfoUi,
        discoveredDevices: List<DiscoveredAquaDevice>
    ): DiscoveredAquaDevice? {
        return discoveredDevices.firstOrNull { discoveredDevice ->
            DeviceIdentityMatcher.samePhysicalDevice(
                savedDevice = savedDevice,
                discoveredDevice = discoveredDevice
            )
        }
    }

    private fun isRecentOnlineState(
        state: DeviceStatusState?,
        now: Long
    ): Boolean {
        if (state == null) {
            return false
        }

        if (!state.isOnline) {
            return false
        }

        if (state.ip.isBlank()) {
            return false
        }

        if (state.lastCheckedMillis <= 0L) {
            return false
        }

        return now - state.lastCheckedMillis <= RECENT_ONLINE_STATUS_VALID_MS
    }

    private fun upsertStatus(
        state: DeviceStatusState
    ) {
        _statuses.update { current ->
            current + (state.deviceId to state)
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }
}