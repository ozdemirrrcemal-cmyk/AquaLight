package com.aqua.aqualight.debug.diagnostics

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class DebugDiagnosticRecord(
    val sequence: Long,
    val event: AppDiagnosticTrace.Event
)

/** Debug-only, process-local ring buffer. It never persists or uploads diagnostic entries. */
internal class DebugDiagnosticRecorder(
    private val capacity: Int = DEFAULT_CAPACITY
) : AppDiagnosticTrace.Sink {

    init {
        require(capacity in MIN_CAPACITY..MAX_CAPACITY)
    }

    /*
     * Trace formatting, ring snapshots and Logcat writes run away from runtime/state threads.
     * The one process-long worker also establishes a deterministic total order for concurrent
     * event/ACK callbacks without extending their critical sections.
     */
    private val workerThread = HandlerThread(
        WORKER_THREAD_NAME,
        Process.THREAD_PRIORITY_BACKGROUND
    ).apply { start() }
    private val worker = Handler(workerThread.looper)
    private var nextSequence = 0L
    private val ring = ArrayDeque<DebugDiagnosticRecord>(capacity)
    private val mutableRecords = MutableStateFlow<List<DebugDiagnosticRecord>>(emptyList())

    val records: StateFlow<List<DebugDiagnosticRecord>> = mutableRecords.asStateFlow()

    override fun record(event: AppDiagnosticTrace.Event) {
        worker.post { append(event) }
    }

    private fun append(event: AppDiagnosticTrace.Event) {
        nextSequence += 1L
        val record = DebugDiagnosticRecord(
            sequence = nextSequence,
            event = event
        )
        while (ring.size >= capacity) ring.removeFirst()
        ring.addLast(record)
        mutableRecords.value = ring.toList()
        Log.d(LOG_TAG, DebugDiagnosticFormatter.format(record))
    }

    fun clear() {
        worker.post {
            ring.clear()
            mutableRecords.value = emptyList()
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 320
        const val MIN_CAPACITY = 32
        const val MAX_CAPACITY = 1_000
        const val LOG_TAG = "AqlDiagnostic"
        const val WORKER_THREAD_NAME = "aql-diagnostic-trace"
    }
}

internal object DebugDiagnosticFormatter {

    private val timeFormatter = DateTimeFormatter
        .ofPattern("HH:mm:ss.SSS", Locale.ROOT)
        .withZone(ZoneId.systemDefault())

    fun format(record: DebugDiagnosticRecord): String = buildString {
        append('#')
        append(record.sequence)
        append(' ')
        append(timeFormatter.format(Instant.ofEpochMilli(record.event.wallClockMillis)))
        append(" [")
        append(record.event.category)
        append("] ")
        append(record.event.name)
        record.event.fields.forEach { field ->
            append(' ')
            append(field.key)
            append('=')
            append(field.value)
        }
    }
}
