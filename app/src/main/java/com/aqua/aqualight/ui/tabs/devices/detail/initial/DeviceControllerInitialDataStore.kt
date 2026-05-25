package com.aqua.aqualight.ui.tabs.devices.detail.initial

import java.util.concurrent.ConcurrentHashMap

object DeviceControllerInitialDataStore {

    private const val ENTRY_TTL_MS = 30_000L

    private data class Entry(
        val data: DeviceControllerInitialData,
        val createdAtMillis: Long
    )

    private val entries = ConcurrentHashMap<Long, Entry>()

    fun put(
        deviceId: Long,
        data: DeviceControllerInitialData
    ) {
        entries[deviceId] = Entry(
            data = data,
            createdAtMillis = System.currentTimeMillis()
        )
    }

    fun consume(
        deviceId: Long
    ): DeviceControllerInitialData? {
        val entry = entries.remove(
            deviceId
        ) ?: return null

        val age = System.currentTimeMillis() - entry.createdAtMillis

        return if (age <= ENTRY_TTL_MS) {
            entry.data
        } else {
            null
        }
    }

    fun clear(
        deviceId: Long
    ) {
        entries.remove(
            deviceId
        )
    }

    fun clearAll() {
        entries.clear()
    }
}