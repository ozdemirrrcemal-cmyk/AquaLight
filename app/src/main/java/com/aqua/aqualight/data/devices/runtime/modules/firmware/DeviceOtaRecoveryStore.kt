package com.aqua.aqualight.data.devices.runtime.modules.firmware

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import org.json.JSONArray
import org.json.JSONObject

/** Minimal owner-scoped record required to verify an OTA after Android process recreation. */
internal data class DeviceOtaRecoveryRecord(
    val deviceUid: String,
    val previousVersion: String,
    val targetVersion: String,
    val productKey: String,
    val productId: String,
    val model: String,
    val hardwareRevision: String,
    val runtimeMetadataGeneration: Long,
    val manifestTag: String,
    val firmwareSha256: String,
    val releaseContent: DeviceFirmwareReleaseContent,
    val recoveryStartedAtMillis: Long
)

internal interface DeviceOtaRecoveryStore {
    fun load(deviceUid: String): DeviceOtaRecoveryRecord?
    fun save(record: DeviceOtaRecoveryRecord)
    fun remove(deviceUid: String)
    fun clearOwner()
}

internal object NoOpDeviceOtaRecoveryStore : DeviceOtaRecoveryStore {
    override fun load(deviceUid: String): DeviceOtaRecoveryRecord? = null
    override fun save(record: DeviceOtaRecoveryRecord) = Unit
    override fun remove(deviceUid: String) = Unit
    override fun clearOwner() = Unit
}

/**
 * Encrypted, owner-scoped OTA recovery storage.
 *
 * The record contains no credentials. It exists only long enough to distinguish
 * target-version success, ESP-IDF rollback to the previous version, and a bounded
 * post-restart timeout even when Android is killed while the device reboots.
 */
internal class EncryptedDeviceOtaRecoveryStore(
    context: Context,
    private val ownerUidProvider: () -> String
) : DeviceOtaRecoveryStore {

    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        FILE_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun load(deviceUid: String): DeviceOtaRecoveryRecord? {
        val normalized = deviceUid.trim()
        if (normalized.isBlank()) return null
        val ownerUid = requireOwnerUid()
        return synchronized(LOCK) {
            val stored = preferences.getString(key(normalized), null) ?: return@synchronized null
            val decoded = runCatching { decode(stored) }.getOrNull()
            when {
                decoded == null -> {
                    preferences.edit().remove(key(normalized)).commitOrThrow()
                    null
                }
                decoded.ownerUid != ownerUid -> null
                else -> decoded.record
            }
        }
    }

    override fun save(record: DeviceOtaRecoveryRecord) {
        require(record.deviceUid.isNotBlank()) { "OTA recovery device UID is missing." }
        synchronized(LOCK) {
            preferences.edit()
                .putString(key(record.deviceUid), encode(requireOwnerUid(), record))
                .commitOrThrow()
        }
    }

    override fun remove(deviceUid: String) {
        val normalized = deviceUid.trim()
        if (normalized.isBlank()) return
        val ownerUid = requireOwnerUid()
        synchronized(LOCK) {
            val storedKey = key(normalized)
            val stored = preferences.getString(storedKey, null) ?: return
            val decoded = runCatching { decode(stored) }.getOrNull()
            if (decoded == null || decoded.ownerUid == ownerUid) {
                preferences.edit().remove(storedKey).commitOrThrow()
            }
        }
    }

    override fun clearOwner() {
        val ownerUid = requireOwnerUid()
        synchronized(LOCK) {
            val editor = preferences.edit()
            preferences.all.forEach { (storedKey, value) ->
                if (!storedKey.startsWith(KEY_PREFIX)) return@forEach
                val stored = value as? String
                val decoded = stored?.let { runCatching { decode(it) }.getOrNull() }
                if (decoded == null || decoded.ownerUid == ownerUid) editor.remove(storedKey)
            }
            editor.commitOrThrow()
        }
    }

    private fun encode(ownerUid: String, record: DeviceOtaRecoveryRecord): String = JSONObject()
        .put(FIELD_OWNER_UID, ownerUid)
        .put(FIELD_DEVICE_UID, record.deviceUid)
        .put(FIELD_PREVIOUS_VERSION, record.previousVersion)
        .put(FIELD_TARGET_VERSION, record.targetVersion)
        .put(FIELD_PRODUCT_KEY, record.productKey)
        .put(FIELD_PRODUCT_ID, record.productId)
        .put(FIELD_MODEL, record.model)
        .put(FIELD_HARDWARE_REVISION, record.hardwareRevision)
        .put(FIELD_RUNTIME_GENERATION, record.runtimeMetadataGeneration)
        .put(FIELD_MANIFEST_TAG, record.manifestTag)
        .put(FIELD_FIRMWARE_SHA256, record.firmwareSha256)
        .put(FIELD_RECOVERY_STARTED_AT, record.recoveryStartedAtMillis)
        .put(FIELD_RELEASE_LOCALE, record.releaseContent.localeTag)
        .put(FIELD_RELEASE_TITLE, record.releaseContent.title)
        .put(FIELD_RELEASE_SUMMARY, record.releaseContent.summary)
        .put(FIELD_RELEASE_CHANGES, JSONArray(record.releaseContent.changes))
        .put(FIELD_RELEASE_WARNINGS, JSONArray(record.releaseContent.warnings))
        .put(FIELD_RELEASE_MANDATORY, record.releaseContent.mandatory)
        .toString()

    private fun decode(raw: String): StoredRecord {
        val json = JSONObject(raw)
        return StoredRecord(
            ownerUid = json.getString(FIELD_OWNER_UID),
            record = DeviceOtaRecoveryRecord(
                deviceUid = json.getString(FIELD_DEVICE_UID),
                previousVersion = json.getString(FIELD_PREVIOUS_VERSION),
                targetVersion = json.getString(FIELD_TARGET_VERSION),
                productKey = json.getString(FIELD_PRODUCT_KEY),
                productId = json.getString(FIELD_PRODUCT_ID),
                model = json.getString(FIELD_MODEL),
                hardwareRevision = json.getString(FIELD_HARDWARE_REVISION),
                runtimeMetadataGeneration = json.optLong(FIELD_RUNTIME_GENERATION, 0L),
                manifestTag = json.optString(FIELD_MANIFEST_TAG),
                firmwareSha256 = json.optString(FIELD_FIRMWARE_SHA256),
                releaseContent = DeviceFirmwareReleaseContent(
                    localeTag = json.optString(FIELD_RELEASE_LOCALE),
                    title = json.optString(FIELD_RELEASE_TITLE),
                    summary = json.optString(FIELD_RELEASE_SUMMARY),
                    changes = json.optJSONArray(FIELD_RELEASE_CHANGES).toStringList(),
                    warnings = json.optJSONArray(FIELD_RELEASE_WARNINGS).toStringList(),
                    mandatory = json.optBoolean(FIELD_RELEASE_MANDATORY, false)
                ),
                recoveryStartedAtMillis = json.getLong(FIELD_RECOVERY_STARTED_AT)
            )
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) add(optString(index))
        }
    }

    private fun requireOwnerUid(): String = ownerUidProvider().trim().also { ownerUid ->
        require(ownerUid.isNotBlank()) { "OTA recovery owner is unavailable." }
    }

    private fun key(deviceUid: String): String = "$KEY_PREFIX${deviceUid.trim()}"

    private fun android.content.SharedPreferences.Editor.commitOrThrow() {
        check(commit()) { "Encrypted OTA recovery storage write failed." }
    }

    private data class StoredRecord(
        val ownerUid: String,
        val record: DeviceOtaRecoveryRecord
    )

    private companion object {
        val LOCK = Any()
        const val FILE_NAME = "aql_ota_recovery"
        const val KEY_PREFIX = "pending."

        const val FIELD_OWNER_UID = "owner_uid"
        const val FIELD_DEVICE_UID = "device_uid"
        const val FIELD_PREVIOUS_VERSION = "previous_version"
        const val FIELD_TARGET_VERSION = "target_version"
        const val FIELD_PRODUCT_KEY = "product_key"
        const val FIELD_PRODUCT_ID = "product_id"
        const val FIELD_MODEL = "model"
        const val FIELD_HARDWARE_REVISION = "hardware_revision"
        const val FIELD_RUNTIME_GENERATION = "runtime_generation"
        const val FIELD_MANIFEST_TAG = "manifest_tag"
        const val FIELD_FIRMWARE_SHA256 = "firmware_sha256"
        const val FIELD_RECOVERY_STARTED_AT = "recovery_started_at"
        const val FIELD_RELEASE_LOCALE = "release_locale"
        const val FIELD_RELEASE_TITLE = "release_title"
        const val FIELD_RELEASE_SUMMARY = "release_summary"
        const val FIELD_RELEASE_CHANGES = "release_changes"
        const val FIELD_RELEASE_WARNINGS = "release_warnings"
        const val FIELD_RELEASE_MANDATORY = "release_mandatory"
    }
}
