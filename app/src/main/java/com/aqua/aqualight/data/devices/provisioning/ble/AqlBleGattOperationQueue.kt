package com.aqua.aqualight.data.devices.provisioning.ble

import android.os.Handler
import java.util.ArrayDeque

internal class AqlBleGattOperationQueue(
    private val handler: Handler,
    private val startOperation: (AqlBleGattOperation) -> AqlBleGattOperationStartResult,
    private val onStartFailure: (AqlBleGattOperation, AqlBleGattOperationStartResult.NotStarted) -> Unit,
    private val operationTimeoutMillis: Long = DEFAULT_OPERATION_TIMEOUT_MS
) {
    private val lock = Any()
    private val pending = ArrayDeque<AqlBleGattOperation>()
    private var active: AqlBleGattOperation? = null

    private val operationTimeoutRunnable = Runnable {
        val timedOutOperation = synchronized(lock) {
            val operation = active
            active = null
            operation
        } ?: return@Runnable

        onStartFailure(
            timedOutOperation,
            AqlBleGattOperationStartResult.NotStarted(
                retryable = false,
                message = "BLE GATT operation timed out: " + timedOutOperation.name + "."
            )
        )
        drain()
    }

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
        if (shouldDrain) {
            handler.removeCallbacks(operationTimeoutRunnable)
            drain()
        }
    }

    fun clear() {
        synchronized(lock) {
            pending.clear()
            active = null
        }
        handler.removeCallbacks(operationTimeoutRunnable)
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
        handler.removeCallbacks(operationTimeoutRunnable)
        when (val result = startOperation(operation)) {
            AqlBleGattOperationStartResult.Started -> {
                handler.postDelayed(operationTimeoutRunnable, operationTimeoutMillis)
            }
            is AqlBleGattOperationStartResult.NotStarted -> {
                synchronized(lock) {
                    if (active == operation) active = null
                }
                handler.removeCallbacks(operationTimeoutRunnable)
                onStartFailure(operation, result)
            }
        }
    }

    private companion object {
        const val DEFAULT_OPERATION_TIMEOUT_MS = 15_000L
    }
}

internal sealed class AqlBleGattOperationStartResult {
    object Started : AqlBleGattOperationStartResult()
    data class NotStarted(
        val retryable: Boolean,
        val message: String
    ) : AqlBleGattOperationStartResult()
}

internal enum class AqlBleGattOperation {
    REQUEST_MTU,
    READ_DEVICE_INFO,
    ENABLE_STATUS_NOTIFICATIONS,
    ENABLE_RUNTIME_NOTIFICATIONS,
    WRITE_START_SESSION,
    WRITE_WIFI_CREDENTIALS,
    READ_PROVISIONING_STATUS,
    READ_RUNTIME_ENDPOINT,
    WRITE_FINALIZE_SETUP
}
