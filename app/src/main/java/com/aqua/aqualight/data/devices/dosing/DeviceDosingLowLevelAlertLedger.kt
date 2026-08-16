package com.aqua.aqualight.data.devices.dosing

import android.content.Context
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Owner/channel-scoped app intent and transition ledger for Dosing low-level alerts.
 *
 * This is deliberately not a notification preference store and never owns Android notification
 * channels. Owner preference and delivery readiness remain centralized in NotificationDispatchUseCase.
 */
internal interface DeviceDosingLowLevelAlertLedger {
    fun isEnabled(deviceUid: String, slotId: String): Boolean

    fun setEnabled(deviceUid: String, slotId: String, enabled: Boolean)

    /**
     * Records one authoritative observation and returns true while a false-to-true alert transition
     * is pending dispatch. The first observation establishes a baseline and never dispatches.
     */
    fun observeLowLevel(deviceUid: String, slotId: String, lowLevelActive: Boolean): Boolean

    /** Completes the current dispatch attempt so repeated low snapshots cannot alert again. */
    fun completeDispatch(deviceUid: String, slotId: String)
}

internal class SharedPreferencesDeviceDosingLowLevelAlertLedger private constructor(
    context: Context,
    private val ownerUid: String
) : DeviceDosingLowLevelAlertLedger {
    private val lock = Any()
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    init {
        require(ownerUid.isNotBlank())
    }

    override fun isEnabled(deviceUid: String, slotId: String): Boolean = synchronized(lock) {
        read(deviceUid, slotId).enabled
    }

    override fun setEnabled(deviceUid: String, slotId: String, enabled: Boolean) = synchronized(lock) {
        val current = read(deviceUid, slotId)
        write(
            deviceUid,
            slotId,
            current.copy(enabled = enabled, pending = current.pending && enabled)
        )
    }

    override fun observeLowLevel(
        deviceUid: String,
        slotId: String,
        lowLevelActive: Boolean
    ): Boolean = synchronized(lock) {
        val current = read(deviceUid, slotId)
        val next = if (!current.observed) {
            current.copy(observed = true, lastLowLevelActive = lowLevelActive, pending = false)
        } else {
            current.copy(
                lastLowLevelActive = lowLevelActive,
                pending = when {
                    !lowLevelActive -> false
                    !current.lastLowLevelActive && current.enabled -> true
                    else -> current.pending
                }
            )
        }
        write(deviceUid, slotId, next)
        next.enabled && lowLevelActive && next.pending
    }

    override fun completeDispatch(deviceUid: String, slotId: String) = synchronized(lock) {
        val current = read(deviceUid, slotId)
        if (current.pending) write(deviceUid, slotId, current.copy(pending = false))
    }

    private fun read(deviceUid: String, slotId: String): AlertRecord =
        preferences.getString(key(deviceUid, slotId), null)
            ?.let(AlertRecord::decode)
            ?: AlertRecord()

    private fun write(deviceUid: String, slotId: String, record: AlertRecord) {
        preferences.edit().putString(key(deviceUid, slotId), record.encode()).commit()
    }

    private fun key(deviceUid: String, slotId: String): String {
        require(deviceUid.isNotBlank())
        require(slotId.isNotBlank())
        val digest = MessageDigest.getInstance("SHA-256").digest(
            "$ownerUid\u0000$deviceUid\u0000$slotId".toByteArray(Charsets.UTF_8)
        )
        return digest.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "aql_dosing_low_level_alert_ledger_v1"

        fun create(
            context: Context,
            ownerUid: String
        ): SharedPreferencesDeviceDosingLowLevelAlertLedger =
            SharedPreferencesDeviceDosingLowLevelAlertLedger(context, ownerUid.trim())
    }
}

/** Process-memory implementation used only by pre-production wiring and focused tests. */
internal class InMemoryDeviceDosingLowLevelAlertLedger : DeviceDosingLowLevelAlertLedger {
    private val records = ConcurrentHashMap<String, AlertRecord>()

    override fun isEnabled(deviceUid: String, slotId: String): Boolean =
        records[key(deviceUid, slotId)]?.enabled ?: false

    override fun setEnabled(deviceUid: String, slotId: String, enabled: Boolean) {
        records.compute(key(deviceUid, slotId)) { _, current ->
            (current ?: AlertRecord()).let { record ->
                record.copy(enabled = enabled, pending = record.pending && enabled)
            }
        }
    }

    override fun observeLowLevel(
        deviceUid: String,
        slotId: String,
        lowLevelActive: Boolean
    ): Boolean {
        val key = key(deviceUid, slotId)
        val updated = records.compute(key) { _, currentValue ->
            val current = currentValue ?: AlertRecord()
            if (!current.observed) {
                current.copy(observed = true, lastLowLevelActive = lowLevelActive, pending = false)
            } else {
                current.copy(
                    lastLowLevelActive = lowLevelActive,
                    pending = when {
                        !lowLevelActive -> false
                        !current.lastLowLevelActive && current.enabled -> true
                        else -> current.pending
                    }
                )
            }
        } ?: AlertRecord()
        return updated.enabled && lowLevelActive && updated.pending
    }

    override fun completeDispatch(deviceUid: String, slotId: String) {
        records.computeIfPresent(key(deviceUid, slotId)) { _, current ->
            current.copy(pending = false)
        }
    }

    private fun key(deviceUid: String, slotId: String): String {
        require(deviceUid.isNotBlank())
        require(slotId.isNotBlank())
        return "$deviceUid\u0000$slotId"
    }
}

private data class AlertRecord(
    val enabled: Boolean = false,
    val observed: Boolean = false,
    val lastLowLevelActive: Boolean = false,
    val pending: Boolean = false
) {
    fun encode(): String = listOf(enabled, observed, lastLowLevelActive, pending)
        .joinToString(separator = ",") { value -> if (value) "1" else "0" }

    companion object {
        fun decode(encoded: String): AlertRecord? {
            val values = encoded.split(',')
            if (values.size != FIELD_COUNT || values.any { it != "0" && it != "1" }) return null
            return AlertRecord(
                enabled = values[0] == "1",
                observed = values[1] == "1",
                lastLowLevelActive = values[2] == "1",
                pending = values[3] == "1"
            )
        }

        private const val FIELD_COUNT = 4
    }
}
