package com.aqua.aqualight.application.devices.dosing

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Temporary on-device diagnostic trace for the Dosing save investigation branch.
 *
 * This trace is presentation-only and never participates in authoritative Dosing state,
 * validation, revision selection or command execution. It is intentionally bounded and will be
 * removed together with the diagnostics branch after the root cause is confirmed.
 */
internal object DeviceDosingDiagnosticTrace {
    private data class Entry(
        val sequence: Long,
        val deviceUid: String,
        val slotId: String,
        val operationId: Long,
        val elapsedMillis: Long,
        val stage: String,
        val detail: String
    )

    private val lock = Any()
    private val sequence = AtomicLong(0L)
    private val operationSequence = AtomicLong(0L)
    private val operationStartNanos = ConcurrentHashMap<Long, Long>()
    private val entries = MutableStateFlow<List<Entry>>(emptyList())

    fun beginPersistedMutation(deviceUid: String, slotId: String): Long {
        val operationId = operationSequence.incrementAndGet()
        operationStartNanos[operationId] = System.nanoTime()
        synchronized(lock) {
            val retained = entries.value.filterNot { entry ->
                entry.deviceUid == deviceUid && entry.slotId == slotId
            }
            entries.value = (retained + entry(
                deviceUid = deviceUid,
                slotId = slotId,
                operationId = operationId,
                stage = "SAVE",
                detail = "BEGIN persisted mutation"
            )).takeLast(MAX_GLOBAL_ENTRIES)
        }
        return operationId
    }

    fun record(
        deviceUid: String,
        slotId: String,
        operationId: Long?,
        stage: String,
        detail: String
    ) {
        if (operationId == null) return
        synchronized(lock) {
            entries.value = (entries.value + entry(
                deviceUid = deviceUid,
                slotId = slotId,
                operationId = operationId,
                stage = stage,
                detail = detail
            )).takeLast(MAX_GLOBAL_ENTRIES)
        }
    }

    fun observe(deviceUid: String, slotId: String): Flow<List<String>> = entries
        .map { allEntries ->
            val selected = allEntries.filter { entry ->
                entry.deviceUid == deviceUid && entry.slotId == slotId
            }.takeLast(MAX_VISIBLE_ENTRIES)
            if (selected.isEmpty()) {
                emptyList()
            } else {
                buildList {
                    add("TEŞHİS LOGU · geçici branch")
                    selected.forEach { entry -> add(entry.format()) }
                }
            }
        }
        .distinctUntilChanged()

    private fun entry(
        deviceUid: String,
        slotId: String,
        operationId: Long,
        stage: String,
        detail: String
    ): Entry {
        val startNanos = operationStartNanos[operationId] ?: System.nanoTime()
        val elapsedMillis = (System.nanoTime() - startNanos).coerceAtLeast(0L) / NANOS_PER_MILLI
        return Entry(
            sequence = sequence.incrementAndGet(),
            deviceUid = deviceUid,
            slotId = slotId,
            operationId = operationId,
            elapsedMillis = elapsedMillis,
            stage = stage,
            detail = detail.take(MAX_DETAIL_CHARS)
        )
    }

    private fun Entry.format(): String =
        "+${elapsedMillis}ms #$operationId [$stage] $detail"

    private const val NANOS_PER_MILLI = 1_000_000L
    private const val MAX_DETAIL_CHARS = 220
    private const val MAX_VISIBLE_ENTRIES = 16
    private const val MAX_GLOBAL_ENTRIES = 96
}
