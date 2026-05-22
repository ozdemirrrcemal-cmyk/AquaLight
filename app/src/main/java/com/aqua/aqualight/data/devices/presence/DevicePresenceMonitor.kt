package com.aqua.aqualight.data.devices.presence

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.DevicesDataStoreManager.DeviceInfoUi
import com.aqua.aqualight.data.devices.discovery.DeviceDiscoveryService
import com.aqua.aqualight.data.devices.discovery.DeviceScanReason
import com.aqua.aqualight.ui.tabs.devices.DiscoveredDevice
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
    private const val LIVE_CHECK_TIMEOUT_MS = 2_000L

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
        val devicesStore = DevicesDataStoreManager.create(appContext)

        monitorJob = scope.launch {
            refreshOnce(
                context = appContext,
                devicesStore = devicesStore
            )

            while (isActive) {
                delay(SCAN_INTERVAL_MS)

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
        knownIp: String
    ): DeviceStatusState? {
        val appContext = context.applicationContext
        val devicesStore = DevicesDataStoreManager.create(appContext)

        val savedDevice = devicesStore.devicesFlow
        .first()
        .firstOrNull {
            device ->
            device.id == deviceId
        } ?: return null

        val now = System.currentTimeMillis()

        setCheckingState(
            device = savedDevice,
            now = now
        )

        val result = DeviceDiscoveryService.scan(
            context = appContext,
            timeoutMs = LIVE_CHECK_TIMEOUT_MS,
            reason = DeviceScanReason.LIVE_CHECK
        )

        val matchedDevice = if (
            result.skippedBecauseBusy ||
            result.error != null
        ) {
            null
        } else {
            result.devices.firstOrNull {
                discoveredDevice ->
                discoveredDevice.id == deviceId ||
                discoveredDevice.ip == knownIp
            }
        }

        val latestNow = System.currentTimeMillis()

        return if (matchedDevice != null) {
            devicesStore.updateDevicesLastSeen(
                discovered = listOf(
                    DevicesDataStoreManager.DeviceLastSeenUpdate(
                        id = savedDevice.id,
                        ip = matchedDevice.ip,
                        firmwareBuild = matchedDevice.firmwareBuild.orEmpty()
                    )
                )
            )

            missedChecks[savedDevice.id] = 0

            val state = buildState(
                device = savedDevice,
                now = latestNow,
                missedCount = 0,
                lastSeenOverride = latestNow,
                ipOverride = matchedDevice.ip
            )

            upsertStatus(state)

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
                missedCount = missedCount
            )

            upsertStatus(state)

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
            emitPassiveStates(savedDevices)
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
        discoveredDevices: List<DiscoveredDevice>
    ) {
        val now = System.currentTimeMillis()

        val matchedByDeviceId = savedDevices.associate {
            savedDevice ->
            savedDevice.id to findMatchingDiscoveredDevice(
                savedDevice = savedDevice,
                discoveredDevices = discoveredDevices
            )
        }

        val updates = matchedByDeviceId.mapNotNull {
            entry ->
            val savedDeviceId = entry.key
            val discoveredDevice = entry.value ?: return@mapNotNull null

            DevicesDataStoreManager.DeviceLastSeenUpdate(
                id = savedDeviceId,
                ip = discoveredDevice.ip,
                firmwareBuild = discoveredDevice.firmwareBuild.orEmpty()
            )
        }

        if (updates.isNotEmpty()) {
            devicesStore.updateDevicesLastSeen(
                discovered = updates
            )
        }

        val states = savedDevices.associate {
            savedDevice ->
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

            savedDevice.id to buildState(
                device = savedDevice,
                now = now,
                missedCount = missedCount,
                lastSeenOverride = if (matchedDevice != null) {
                    now
                } else {
                    null
                },
                ipOverride = matchedDevice?.ip
            )
        }

        _statuses.value = states
    }

    private fun emitPassiveStates(
        savedDevices: List<DeviceInfoUi>
    ) {
        val now = System.currentTimeMillis()

        val states = savedDevices.associate {
            savedDevice ->
            val missedCount = missedChecks.getOrDefault(
                savedDevice.id,
                0
            )

            savedDevice.id to buildState(
                device = savedDevice,
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

        val fallbackOnline = device.lastSeenMillis > 0L &&
        now - device.lastSeenMillis <= ONLINE_TIMEOUT_MS

        val state = DeviceStatusState(
            deviceId = device.id,
            ip = previousState?.ip ?: device.ip,
            status = DeviceConnectionStatus.CHECKING,
            isOnline = previousState?.isOnline ?: fallbackOnline,
            lastSeenMillis = previousState?.lastSeenMillis ?: device.lastSeenMillis,
            lastCheckedMillis = now,
            missedChecks = missedChecks.getOrDefault(
                device.id,
                0
            )
        )

        upsertStatus(state)
    }

    private fun buildState(
        device: DeviceInfoUi,
        now: Long,
        missedCount: Int,
        lastSeenOverride: Long? = null,
        ipOverride: String? = null
    ): DeviceStatusState {
        val lastSeen = lastSeenOverride ?: device.lastSeenMillis
        val age = if (lastSeen > 0L) {
            now - lastSeen
        } else {
            Long.MAX_VALUE
        }

        val status = when {
            lastSeen <= 0L -> DeviceConnectionStatus.UNKNOWN

            age <= ONLINE_TIMEOUT_MS -> DeviceConnectionStatus.ONLINE

            age <= STALE_TIMEOUT_MS &&
            missedCount < OFFLINE_AFTER_MISSED_CHECKS -> {
                DeviceConnectionStatus.STALE
            } else -> DeviceConnectionStatus.OFFLINE
        }

        return DeviceStatusState(
            deviceId = device.id,
            ip = ipOverride ?: device.ip,
            status = status,
            isOnline = status == DeviceConnectionStatus.ONLINE,
            lastSeenMillis = lastSeen,
            lastCheckedMillis = now,
            missedChecks = missedCount
        )
    }

    private fun findMatchingDiscoveredDevice(
        savedDevice: DeviceInfoUi,
        discoveredDevices: List<DiscoveredDevice>
    ): DiscoveredDevice? {
        return discoveredDevices.firstOrNull {
            discoveredDevice ->
            discoveredDevice.id == savedDevice.id ||
            discoveredDevice.ip == savedDevice.ip
        }
    }

    private fun upsertStatus(
        state: DeviceStatusState
    ) {
        _statuses.update {
            current ->
            current + (
                state.deviceId to state
            )
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }
}