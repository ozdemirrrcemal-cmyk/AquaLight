package com.aqua.aqualight.data.devices.provisioning.repository

import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-level, owner-scoped registry for in-flight provisioning work. */
internal class AqlProvisioningTransactionRegistry<T : Any>(
    private val ownerUidOf: (T) -> String,
    private val deviceUidOf: (T) -> DeviceUid
) {

    private val mutex = Mutex()
    private val entries = mutableMapOf<String, T>()

    suspend fun registerIfAbsent(value: T): Boolean {
        return mutex.withLock {
            val key = keyFor(value)
            if (entries.containsKey(key)) {
                false
            } else {
                entries[key] = value
                true
            }
        }
    }

    suspend fun find(
        ownerUid: String,
        deviceUid: DeviceUid
    ): T? {
        return mutex.withLock {
            entries[key(ownerUid, deviceUid)]
        }
    }

    suspend fun remove(value: T): Boolean {
        return mutex.withLock {
            val key = keyFor(value)
            if (entries[key] !== value) {
                false
            } else {
                entries.remove(key)
                true
            }
        }
    }

    suspend fun deviceUidsForOwner(
        ownerUid: String
    ): List<DeviceUid> {
        val normalizedOwnerUid = ownerUid.trim().also { normalized ->
            require(normalized.isNotBlank()) {
                "ownerUid must not be blank"
            }
        }

        return mutex.withLock {
            entries.values
                .asSequence()
                .filter { value ->
                    ownerUidOf(value).trim() == normalizedOwnerUid
                }
                .map(deviceUidOf)
                .distinctBy { deviceUid -> deviceUid.normalizedValue() }
                .toList()
        }
    }

    private fun keyFor(value: T): String {
        return key(
            ownerUid = ownerUidOf(value),
            deviceUid = deviceUidOf(value)
        )
    }

    private fun key(
        ownerUid: String,
        deviceUid: DeviceUid
    ): String {
        val normalizedOwnerUid = ownerUid.trim()
        require(normalizedOwnerUid.isNotBlank()) {
            "ownerUid must not be blank"
        }

        return "$normalizedOwnerUid\u0000${deviceUid.normalizedValue()}"
    }

    private fun DeviceUid.normalizedValue(): String {
        return value.trim()
            .uppercase(Locale.US)
            .also { normalized ->
                require(normalized.isNotBlank()) {
                    "deviceUid must not be blank"
                }
            }
    }
}
