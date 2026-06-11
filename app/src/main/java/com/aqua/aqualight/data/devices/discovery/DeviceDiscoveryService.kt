package com.aqua.aqualight.data.devices.discovery

import android.content.Context
import android.os.SystemClock
import com.aqua.aqualight.data.devices.DeviceIdentityMatcher
import com.aqua.aqualight.data.devices.DevicesDataStoreManager.DeviceInfo
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object DeviceDiscoveryService {

    private val scanMutex = Mutex()
    private val liveCheckRequested = AtomicBoolean(false)

    data class ScanResult(
        val devices: List<DiscoveredAquaDevice>,
        val startedAtMillis: Long,
        val finishedAtMillis: Long,
        val reason: DeviceScanReason,
        val skippedBecauseBusy: Boolean = false,
        val error: Throwable? = null
    )

    suspend fun scan(
        context: Context,
        timeoutMs: Long,
        reason: DeviceScanReason
    ): ScanResult {
        val appContext = context.applicationContext

        return when (reason) {
            DeviceScanReason.MONITOR -> {
                scanForMonitor(
                    context = appContext,
                    timeoutMs = timeoutMs,
                    reason = reason
                )
            }

            DeviceScanReason.MANUAL_SCAN -> {
                scanMutex.withLock {
                    executeScan(
                        context = appContext,
                        timeoutMs = timeoutMs,
                        reason = reason
                    )
                }
            }

            DeviceScanReason.LIVE_CHECK -> {
                scanForLiveCheck(
                    context = appContext,
                    timeoutMs = timeoutMs,
                    reason = reason
                )
            }
        }
    }

    suspend fun scanForDevice(
        context: Context,
        timeoutMs: Long,
        savedDevice: DeviceInfo
    ): ScanResult {
        val appContext = context.applicationContext

        liveCheckRequested.set(true)

        return scanMutex.withLock {
            liveCheckRequested.set(false)

            val result = executeScan(
                context = appContext,
                timeoutMs = timeoutMs,
                reason = DeviceScanReason.LIVE_CHECK,
                stopWhen = { discoveredDevice ->
                    DeviceIdentityMatcher.samePhysicalDevice(
                        savedDevice = savedDevice,
                        discoveredDevice = discoveredDevice
                    )
                }
            )

            result.copy(
                devices = result.devices.filter { discoveredDevice ->
                    DeviceIdentityMatcher.samePhysicalDevice(
                        savedDevice = savedDevice,
                        discoveredDevice = discoveredDevice
                    )
                }
            )
        }
    }

    private suspend fun scanForLiveCheck(
        context: Context,
        timeoutMs: Long,
        reason: DeviceScanReason
    ): ScanResult {
        liveCheckRequested.set(true)

        return scanMutex.withLock {
            liveCheckRequested.set(false)

            executeScan(
                context = context,
                timeoutMs = timeoutMs,
                reason = reason
            )
        }
    }

    private suspend fun scanForMonitor(
        context: Context,
        timeoutMs: Long,
        reason: DeviceScanReason
    ): ScanResult {
        val startedAt = SystemClock.elapsedRealtime()

        if (!scanMutex.tryLock()) {
            return ScanResult(
                devices = emptyList(),
                startedAtMillis = startedAt,
                finishedAtMillis = SystemClock.elapsedRealtime(),
                reason = reason,
                skippedBecauseBusy = true
            )
        }

        return try {
            val result = executeScan(
                context = context,
                timeoutMs = timeoutMs,
                reason = reason,
                shouldStopEarly = {
                    liveCheckRequested.get()
                }
            )

            if (liveCheckRequested.get()) {
                ScanResult(
                    devices = emptyList(),
                    startedAtMillis = result.startedAtMillis,
                    finishedAtMillis = SystemClock.elapsedRealtime(),
                    reason = reason,
                    skippedBecauseBusy = true
                )
            } else {
                result
            }
        } finally {
            scanMutex.unlock()
        }
    }

    private suspend fun executeScan(
        context: Context,
        timeoutMs: Long,
        reason: DeviceScanReason,
        stopWhen: ((DiscoveredAquaDevice) -> Boolean)? = null,
        shouldStopEarly: (() -> Boolean)? = null
    ): ScanResult = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()

        try {
            val devices = UdpDeviceDiscovery.discover(
                context = context,
                timeoutMs = timeoutMs,
                stopWhen = stopWhen,
                shouldStopEarly = shouldStopEarly
            )
                .filter { device ->
                    isValidDevice(device) && device.isSupported
                }
                .distinctBy { device ->
                    device.id
                }

            ScanResult(
                devices = devices,
                startedAtMillis = startedAt,
                finishedAtMillis = SystemClock.elapsedRealtime(),
                reason = reason
            )
        } catch (exception: Exception) {
            ScanResult(
                devices = emptyList(),
                startedAtMillis = startedAt,
                finishedAtMillis = SystemClock.elapsedRealtime(),
                reason = reason,
                error = exception
            )
        }
    }

    private fun isValidDevice(
        device: DiscoveredAquaDevice
    ): Boolean {
        if (device.id <= 0L) {
            return false
        }

        if (device.ip.isBlank()) {
            return false
        }

        if (
            device.name.isBlank() &&
            device.aquaName.isBlank() &&
            device.productId.isNullOrBlank()
        ) {
            return false
        }

        return true
    }
}