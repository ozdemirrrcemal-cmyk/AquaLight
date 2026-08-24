package com.aqua.aqualight.debug.dosing

import android.os.SystemClock
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Temporary owner-process diagnostic trace for Dosing transaction analysis.
 *
 * This object deliberately owns diagnostic text only. It never owns or mutates device state,
 * revisions, retry policy, transport state or UI business state. The debug branch removes it after
 * the production root cause is confirmed.
 */
object DosingDebugTrace {
    private const val TAG = "DosingTrace"
    private const val MAX_LINES = 600
    private const val MAX_JSON_CHARS = 1_400
    private const val DEVICE_SUFFIX_CHARS = 8
    private const val CATEGORY_WIDTH = 8

    private val lock = Any()
    private val sequence = AtomicLong(0L)
    private val operationSequence = AtomicLong(0L)
    private val startedAtElapsedMs = SystemClock.elapsedRealtime()
    private val wallClockFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val mutableLines = MutableStateFlow<List<String>>(emptyList())

    val lines: StateFlow<List<String>> = mutableLines.asStateFlow()

    fun nextOperationId(prefix: String): String =
        "${prefix.uppercase(Locale.US)}-${operationSequence.incrementAndGet()}"

    fun log(
        category: String,
        message: String,
        operationId: String? = null
    ) {
        val seq = sequence.incrementAndGet()
        val wall = synchronized(wallClockFormat) {
            wallClockFormat.format(Date())
        }
        val elapsed = SystemClock.elapsedRealtime() - startedAtElapsedMs
        val operation = operationId?.let { " [$it]" }.orEmpty()
        val line = "%04d %s +%06dms %-8s%s %s".format(
            Locale.US,
            seq,
            wall,
            elapsed,
            category.take(CATEGORY_WIDTH),
            operation,
            message.replace('\n', ' ')
        )
        synchronized(lock) {
            mutableLines.value = (mutableLines.value + line).takeLast(MAX_LINES)
        }
        Log.d(TAG, line)
    }

    fun clear() {
        synchronized(lock) {
            mutableLines.value = emptyList()
        }
        log("TRACE", "CLEARED")
    }

    fun snapshotText(): String = mutableLines.value.joinToString(separator = "\n")

    fun isDosingModule(module: String): Boolean =
        module.contains("dosing", ignoreCase = true)

    fun shortDevice(deviceUid: String): String {
        val trimmed = deviceUid.trim()
        return if (trimmed.length <= DEVICE_SUFFIX_CHARS) {
            trimmed
        } else {
            "…${trimmed.takeLast(DEVICE_SUFFIX_CHARS)}"
        }
    }

    fun compactJson(value: JSONObject?): String = compact(value?.toString().orEmpty())

    fun compact(value: String, maxChars: Int = MAX_JSON_CHARS): String {
        val oneLine = value.replace('\n', ' ').replace('\r', ' ')
        return if (oneLine.length <= maxChars) oneLine else oneLine.take(maxChars) + "…"
    }
}
