package com.aqua.aqualight.data.devices.presence

import android.content.Context
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
    private const val ONLINE_TIMEOUT_MS = 90_000L
    private const val STALE_TIMEOUT_MS = 180_000L
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
            val currentMissedCount = missedChecks.getOrDefault(
                savedDevice.id,
                0
            )

            val fallbackState = if (allowRecentOnlineCache) {
                buildPassiveState(
                    device = savedDevice,
                    previousState = currentState,
                    now = latestNow,
                    missedCount = currentMissedCount,
                    ipOverride = knownIp.ifBlank {
                        savedDevice.ip
                    }
                )
            } else {
                val missedCount = currentMissedCount + 1

                missedChecks[savedDevice.id] = missedCount

                buildMissingState(
                    device = savedDevice,
                    previousState = currentState,
                    now = latestNow,
                    missedCount = missedCount,
                    ipOverride = knownIp.ifBlank {
                        savedDevice.ip
                    }
                )
            }

            upsertStatus(
                state = fallbackState
            )

            return fallbackState
        }

        val matchedDevice = result.devices.firstOrNull { discoveredDevice ->
            discoveredDevice.id == deviceId
        }

        return if (matchedDevice != null) {
            devicesStore.updateDevicesLastSeen(
                discovered = listOf(
                    matchedDevice.toLastSeenUpdate(
                        savedDeviceId = savedDevice.id
                    )
                )
            )

            missedChecks[savedDevice.id] = 0

            val state = buildOnlineState(
                device = savedDevice,
                now = latestNow,
                ipOverride = matchedDevice.ip
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

            val state = buildMissingState(
                device = savedDevice,
                previousState = currentState,
                now = latestNow,
                missedCount = missedCount,
                ipOverride = currentState?.ip?.ifBlank {
                    knownIp.ifBlank {
                        savedDevice.ip
                    }
                } ?: knownIp.ifBlank {
                    savedDevice.ip
                }
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

            discoveredDevice.toLastSeenUpdate(
                savedDeviceId = savedDeviceId
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

            savedDevice.id to if (matchedDevice != null) {
                buildOnlineState(
                    device = savedDevice,
                    now = now,
                    ipOverride = matchedDevice.ip
                )
            } else {
                buildMissingState(
                    device = savedDevice,
                    previousState = _statuses.value[savedDevice.id],
                    now = now,
                    missedCount = missedCount,
                    ipOverride = _statuses.value[savedDevice.id]?.ip?.ifBlank {
                        savedDevice.ip
                    } ?: savedDevice.ip
                )
            }
        }

        _statuses.value = states
    }

    private fun emitPassiveStates(
        savedDevices: List<DeviceInfoUi>
    ) {
        val now = System.currentTimeMillis()

        val states = savedDevices.associate { savedDevice ->
            val missedCount = missedChecks.getOrDefault(
                savedDevice.id,
                0
            )

            savedDevice.id to buildPassiveState(
                device = savedDevice,
                previousState = _statuses.value[savedDevice.id],
                now = now,
                missedCount = missedCount
            )
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
            isOnline = false,
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

    private fun buildOnlineState(
        device: DeviceInfoUi,
        now: Long,
        ipOverride: String? = null
    ): DeviceStatusState {
        return DeviceStatusState(
            deviceId = device.id,
            ip = ipOverride ?: device.ip,
            status = DeviceConnectionStatus.ONLINE,
            isOnline = true,
            lastSeenMillis = now,
            lastCheckedMillis = now,
            missedChecks = 0
        )
    }

    private fun buildMissingState(
        device: DeviceInfoUi,
        previousState: DeviceStatusState?,
        now: Long,
        missedCount: Int,
        ipOverride: String? = null
    ): DeviceStatusState {
        val lastSeen = previousState?.lastSeenMillis ?: device.lastSeenMillis

        val status = when {
            lastSeen <= 0L -> {
                DeviceConnectionStatus.OFFLINE
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
            isOnline = false,
            lastSeenMillis = lastSeen,
            lastCheckedMillis = now,
            missedChecks = missedCount
        )
    }

    private fun buildPassiveState(
        device: DeviceInfoUi,
        previousState: DeviceStatusState?,
        now: Long,
        missedCount: Int,
        ipOverride: String? = null
    ): DeviceStatusState {
        if (previousState == null) {
            return DeviceStatusState(
                deviceId = device.id,
                ip = ipOverride ?: device.ip,
                status = DeviceConnectionStatus.UNKNOWN,
                isOnline = false,
                lastSeenMillis = device.lastSeenMillis,
                lastCheckedMillis = now,
                missedChecks = missedCount
            )
        }

        val lastSeen = previousState.lastSeenMillis

        val status = when {
            lastSeen <= 0L -> {
                DeviceConnectionStatus.UNKNOWN
            }

            now - lastSeen <= ONLINE_TIMEOUT_MS &&
                previousState.status == DeviceConnectionStatus.ONLINE -> {
                DeviceConnectionStatus.ONLINE
            }

            now - lastSeen <= STALE_TIMEOUT_MS &&
                missedCount < OFFLINE_AFTER_MISSED_CHECKS -> {
                DeviceConnectionStatus.STALE
            }

            else -> {
                DeviceConnectionStatus.OFFLINE
            }
        }

        return DeviceStatusState(
            deviceId = device.id,
            ip = ipOverride ?: previousState.ip.ifBlank {
                device.ip
            },
            status = status,
            isOnline = status == DeviceConnectionStatus.ONLINE,
            lastSeenMillis = lastSeen,
            lastCheckedMillis = now,
            missedChecks = missedCount
        )
    }

    private fun findMatchingDiscoveredDevice(
        savedDevice: DeviceInfoUi,
        discoveredDevices: List<DiscoveredAquaDevice>
    ): DiscoveredAquaDevice? {
        return discoveredDevices.firstOrNull { discoveredDevice ->
            discoveredDevice.id == savedDevice.id
        }
    }

    private fun DiscoveredAquaDevice.toLastSeenUpdate(
        savedDeviceId: Long
    ): DevicesDataStoreManager.DeviceLastSeenUpdate {
        return DevicesDataStoreManager.DeviceLastSeenUpdate(
            id = savedDeviceId,
            ip = ip,
            firmwareBuild = firmwareBuild,
            deviceUid = deviceUid,
            macAddress = macAddress,
            firmwareSerial = serialNumber,

            deviceType = deviceType,

            udpVersion = udpVersion,
            tabLight = tabLight,
            tabTimer = tabTimer,
            tabTemperature = tabTemperature,

            productId = productId,
            productFamily = productFamily,
            productModel = productModel,
            hardwareRevision = hardwareRevision,
            firmwareVersion = firmwareVersion,
            apiVersion = apiVersion,

            channelCount = channelCount,
            sensorCount = sensorCount,

            supportedFeatures = supportedFeatures,
            supportedScreens = supportedScreens
        )
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