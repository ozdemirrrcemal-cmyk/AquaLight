package com.aqua.aqualight.data.devices.provisioning.ble

import android.os.Handler
import java.util.ArrayDeque

internal class AqlBleGattOperationQueue(
    private val handler: Handler,
    private val startOperation: (AqlBleGattOperation) -> Boolean,
    private val onStartFailure: (AqlBleGattOperation) -> Unit
) {
    private val lock = Any()
    private val pending = ArrayDeque<AqlBleGattOperation>()
    private var active: AqlBleGattOperation? = null

    fun enqueue(operation: AqlBleGattOperation) {
        synchronized(lock) {
            if (active == operation) return
            if (pending.any { item -> item == operation }) return
            pending.addLast(operation)
        }
        drain()
    }

    fun enqueueDelayed(operation: AqlBleGattOperation, delayMillis: Long) {
        handler.postDelayed({ enqueue(operation) }, delayMillis)
    }

    fun complete(operation: AqlBleGattOperation) {
        var shouldDrain = false
        synchronized(lock) {
            if (active == operation) {
                active = null
                shouldDrain = true
            }
        }
        if (shouldDrain) drain()
    }

    fun clear() {
        synchronized(lock) {
            pending.clear()
            active = null
        }
    }

    private fun drain() {
        var next: AqlBleGattOperation? = null
        synchronized(lock) {
            if (active == null) {
                next = pending.pollFirst()
                active = next
            }
        }

        val operation = next ?: return
        if (!startOperation(operation)) {
            synchronized(lock) {
                if (active == operation) active = null
            }
            onStartFailure(operation)
        }
    }
}

internal enum class AqlBleGattOperation {
    REQUEST_MTU,
    READ_DEVICE_INFO,
    ENABLE_STATUS_NOTIFICATIONS,
    ENABLE_RUNTIME_NOTIFICATIONS,
    WRITE_START_SESSION,
    WRITE_WIFI_CREDENTIALS,
    READ_PROVISIONING_STATUS,
    READ_RUNTIME_ENDPOINT
}
