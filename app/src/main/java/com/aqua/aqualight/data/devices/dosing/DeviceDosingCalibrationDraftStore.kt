package com.aqua.aqualight.data.devices.dosing

import android.content.Context
import android.content.SharedPreferences
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationDraftOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingDisplayNamePolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingDisplayNameValidation
import java.security.MessageDigest

/** Process-durable calibration name drafts isolated by authenticated owner, device and channel. */
internal class SharedPreferencesDeviceDosingCalibrationDraftStore private constructor(
    context: Context,
    ownerUid: String
) : DeviceDosingCalibrationDraftOperations {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val ownerKeyPrefix = DeviceDosingCalibrationDraftKey.ownerPrefix(ownerUid)

    override fun loadDisplayName(deviceUid: String, slotId: String): String? = synchronized(LOCK) {
        preferences.getString(recordKey(deviceUid, slotId), null)
            ?.takeIf(String::isNotBlank)
    }

    override fun saveDisplayName(deviceUid: String, slotId: String, displayName: String) {
        val normalizedName = when (
            val validation = DeviceDosingDisplayNamePolicy.validateRequired(displayName)
        ) {
            is DeviceDosingDisplayNameValidation.Accepted -> validation.normalizedValue
            is DeviceDosingDisplayNameValidation.Rejected -> {
                throw IllegalArgumentException("Invalid Dosing calibration display-name draft.")
            }
        }
        synchronized(LOCK) {
            preferences.edit()
                .putString(recordKey(deviceUid, slotId), normalizedName)
                .commitOrThrow()
        }
    }

    override fun clearDisplayName(deviceUid: String, slotId: String) = synchronized(LOCK) {
        preferences.edit()
            .remove(recordKey(deviceUid, slotId))
            .commitOrThrow()
    }

    fun clearOwner() = synchronized(LOCK) {
        val editor = preferences.edit()
        preferences.all.keys
            .filter { key -> key.startsWith(ownerKeyPrefix) }
            .forEach { key -> editor.remove(key) }
        editor.commitOrThrow()
    }

    private fun recordKey(deviceUid: String, slotId: String): String =
        DeviceDosingCalibrationDraftKey.record(ownerKeyPrefix, deviceUid, slotId)

    private fun SharedPreferences.Editor.commitOrThrow() {
        check(commit()) { "Dosing calibration draft storage write failed." }
    }

    companion object {
        private const val PREFERENCES_NAME = "aql_dosing_calibration_name_drafts_v1"
        private val LOCK = Any()

        fun create(
            context: Context,
            ownerUid: String
        ): SharedPreferencesDeviceDosingCalibrationDraftStore =
            SharedPreferencesDeviceDosingCalibrationDraftStore(context, ownerUid.trim())
    }
}

internal object DeviceDosingCalibrationDraftKey {
    private const val KEY_SEPARATOR = "."
    private const val BYTE_MASK = 0xff
    private const val HEX_RADIX = 16
    private const val HEX_BYTE_WIDTH = 2

    fun ownerPrefix(ownerUid: String): String {
        val normalizedOwnerUid = ownerUid.trim()
        require(normalizedOwnerUid.isNotBlank())
        return digest(normalizedOwnerUid) + KEY_SEPARATOR
    }

    fun record(ownerKeyPrefix: String, deviceUid: String, slotId: String): String {
        require(ownerKeyPrefix.endsWith(KEY_SEPARATOR))
        val normalizedDeviceUid = deviceUid.trim()
        val normalizedSlotId = slotId.trim()
        require(normalizedDeviceUid.isNotBlank())
        require(normalizedSlotId.isNotBlank())
        return ownerKeyPrefix + digest("$normalizedDeviceUid\u0000$normalizedSlotId")
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte ->
            (byte.toInt() and BYTE_MASK)
                .toString(HEX_RADIX)
                .padStart(HEX_BYTE_WIDTH, '0')
        }
}
