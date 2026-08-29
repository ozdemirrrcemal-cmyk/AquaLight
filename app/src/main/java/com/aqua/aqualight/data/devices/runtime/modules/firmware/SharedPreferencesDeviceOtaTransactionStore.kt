@file:Suppress("LargeClass", "MagicNumber", "TooManyFunctions")

package com.aqua.aqualight.data.devices.runtime.modules.firmware

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Owner-isolated, crash-safe OTA attempt journal. It contains no device credentials. */
internal class SharedPreferencesDeviceOtaTransactionStore private constructor(
    private val preferences: SharedPreferences,
    ownerUid: String
) : DeviceOtaTransactionStore {

    private val ownerKey = ownerUid.sha256()

    override fun activeTransactions(): List<DeviceOtaTransaction> = synchronized(LOCK) {
        preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith("active.$ownerKey.")) return@mapNotNull null
            decodeActive(value as? String)
        }
    }

    override fun active(deviceUid: DeviceUid): DeviceOtaTransaction? = synchronized(LOCK) {
        decodeActive(preferences.getString(activeKey(deviceUid), null)).also { decoded ->
            if (decoded == null && preferences.contains(activeKey(deviceUid))) {
                preferences.edit().remove(activeKey(deviceUid)).commitOrThrow()
            }
        }
    }

    override fun saveActive(transaction: DeviceOtaTransaction) {
        val deviceUid = DeviceUid(transaction.plan.deviceUid)
        synchronized(LOCK) {
            preferences.edit()
                .putString(activeKey(deviceUid), encodeActive(transaction))
                .commitOrThrow()
        }
    }

    override fun clearActive(deviceUid: DeviceUid) {
        synchronized(LOCK) {
            preferences.edit().remove(activeKey(deviceUid)).commitOrThrow()
        }
    }

    override fun quarantine(deviceUid: DeviceUid): DeviceOtaQuarantine? = synchronized(LOCK) {
        decodeQuarantine(preferences.getString(quarantineKey(deviceUid), null)).also { decoded ->
            if (decoded == null && preferences.contains(quarantineKey(deviceUid))) {
                preferences.edit().remove(quarantineKey(deviceUid)).commitOrThrow()
            }
        }
    }

    override fun saveQuarantine(quarantine: DeviceOtaQuarantine) {
        val deviceUid = DeviceUid(quarantine.deviceUid)
        synchronized(LOCK) {
            preferences.edit()
                .putString(quarantineKey(deviceUid), encodeQuarantine(quarantine))
                .commitOrThrow()
        }
    }

    fun clearOwner() {
        synchronized(LOCK) {
            val activePrefix = "active.$ownerKey."
            val quarantinePrefix = "quarantine.$ownerKey."
            val editor = preferences.edit()
            preferences.all.keys.forEach { key ->
                if (key.startsWith(activePrefix) || key.startsWith(quarantinePrefix)) {
                    editor.remove(key)
                }
            }
            editor.commitOrThrow()
        }
    }

    private fun activeKey(deviceUid: DeviceUid): String =
        "active.$ownerKey.${deviceUid.value.sha256()}"

    private fun quarantineKey(deviceUid: DeviceUid): String =
        "quarantine.$ownerKey.${deviceUid.value.sha256()}"

    private fun encodeActive(transaction: DeviceOtaTransaction): String = JSONObject()
        .put(FIELD_SCHEMA, SCHEMA_VERSION)
        .put(FIELD_STARTED_AT, transaction.startedAtEpochMillis)
        .put(FIELD_RECOVERY_DEADLINE, transaction.recoveryDeadlineEpochMillis)
        .put(FIELD_AWAITING_VERSION, transaction.awaitingVersionVerification)
        .put(FIELD_PLAN, encodePlan(transaction.plan))
        .toString()

    private fun decodeActive(raw: String?): DeviceOtaTransaction? = raw?.let {
        runCatching {
            val json = JSONObject(it)
            require(json.getInt(FIELD_SCHEMA) == SCHEMA_VERSION)
            DeviceOtaTransaction(
                plan = decodePlan(json.getJSONObject(FIELD_PLAN)),
                startedAtEpochMillis = json.getLong(FIELD_STARTED_AT),
                recoveryDeadlineEpochMillis = json.optLong(FIELD_RECOVERY_DEADLINE, 0L),
                awaitingVersionVerification = json.optBoolean(FIELD_AWAITING_VERSION, false)
            )
        }.getOrNull()
    }

    private fun encodePlan(plan: PreparedDeviceFirmwareUpdate): JSONObject = JSONObject()
        .put(FIELD_DEVICE_UID, plan.deviceUid)
        .put(FIELD_CURRENT_VERSION, plan.currentVersion)
        .put(FIELD_TARGET_VERSION, plan.targetVersion)
        .put(FIELD_CHANNEL, plan.channel)
        .put(FIELD_ENVIRONMENT, plan.environment)
        .put(FIELD_PRODUCT_KEY, plan.productKey)
        .put(FIELD_PRODUCT_ID, plan.productId)
        .put(FIELD_MODEL, plan.model)
        .put(FIELD_HARDWARE_REVISION, plan.hardwareRevision)
        .put(FIELD_DISPLAY_NAME, plan.displayName)
        .put(FIELD_FILENAME, plan.filename)
        .put(FIELD_DOWNLOAD_URL, plan.downloadUrl)
        .put(FIELD_SHA256, plan.sha256.lowercase())
        .put(FIELD_SIZE_BYTES, plan.sizeBytes)
        .put(FIELD_APPLY_NOW, plan.applyNow)
        .put(FIELD_RUNTIME_GENERATION, plan.runtimeMetadataGeneration)
        .put(FIELD_MANIFEST_TAG, plan.manifestTag)
        .put(FIELD_RELEASE_CONTENT, encodeReleaseContent(plan.releaseContent))

    private fun decodePlan(json: JSONObject): PreparedDeviceFirmwareUpdate =
        PreparedDeviceFirmwareUpdate(
            deviceUid = json.getString(FIELD_DEVICE_UID),
            currentVersion = json.getString(FIELD_CURRENT_VERSION),
            targetVersion = json.getString(FIELD_TARGET_VERSION),
            channel = json.getString(FIELD_CHANNEL),
            environment = json.getString(FIELD_ENVIRONMENT),
            productKey = json.getString(FIELD_PRODUCT_KEY),
            productId = json.getString(FIELD_PRODUCT_ID),
            model = json.getString(FIELD_MODEL),
            hardwareRevision = json.getString(FIELD_HARDWARE_REVISION),
            displayName = json.optString(FIELD_DISPLAY_NAME),
            filename = json.getString(FIELD_FILENAME),
            downloadUrl = json.getString(FIELD_DOWNLOAD_URL),
            sha256 = json.getString(FIELD_SHA256),
            sizeBytes = json.getInt(FIELD_SIZE_BYTES),
            applyNow = json.getBoolean(FIELD_APPLY_NOW),
            runtimeMetadataGeneration = json.getLong(FIELD_RUNTIME_GENERATION),
            manifestTag = json.optString(FIELD_MANIFEST_TAG),
            releaseContent = decodeReleaseContent(json.getJSONObject(FIELD_RELEASE_CONTENT))
        )

    private fun encodeReleaseContent(content: DeviceFirmwareReleaseContent): JSONObject =
        JSONObject()
            .put(FIELD_LOCALE_TAG, content.localeTag)
            .put(FIELD_TITLE, content.title)
            .put(FIELD_SUMMARY, content.summary)
            .put(FIELD_CHANGES, JSONArray(content.changes))
            .put(FIELD_WARNINGS, JSONArray(content.warnings))
            .put(FIELD_MANDATORY, content.mandatory)

    private fun decodeReleaseContent(json: JSONObject): DeviceFirmwareReleaseContent =
        DeviceFirmwareReleaseContent(
            localeTag = json.optString(FIELD_LOCALE_TAG),
            title = json.optString(FIELD_TITLE),
            summary = json.optString(FIELD_SUMMARY),
            changes = json.getJSONArray(FIELD_CHANGES).strings(),
            warnings = json.getJSONArray(FIELD_WARNINGS).strings(),
            mandatory = json.optBoolean(FIELD_MANDATORY, false)
        )

    private fun encodeQuarantine(quarantine: DeviceOtaQuarantine): String = JSONObject()
        .put(FIELD_SCHEMA, SCHEMA_VERSION)
        .put(FIELD_DEVICE_UID, quarantine.deviceUid)
        .put(FIELD_CURRENT_VERSION, quarantine.previousVersion)
        .put(FIELD_TARGET_VERSION, quarantine.rejectedVersion)
        .put(FIELD_PRODUCT_KEY, quarantine.productKey)
        .put(FIELD_HARDWARE_REVISION, quarantine.hardwareRevision)
        .put(FIELD_MANIFEST_TAG, quarantine.manifestTag)
        .put(FIELD_SHA256, quarantine.sha256.lowercase())
        .put(FIELD_RECORDED_AT, quarantine.recordedAtEpochMillis)
        .toString()

    private fun decodeQuarantine(raw: String?): DeviceOtaQuarantine? = raw?.let {
        runCatching {
            val json = JSONObject(it)
            require(json.getInt(FIELD_SCHEMA) == SCHEMA_VERSION)
            DeviceOtaQuarantine(
                deviceUid = json.getString(FIELD_DEVICE_UID),
                previousVersion = json.getString(FIELD_CURRENT_VERSION),
                rejectedVersion = json.getString(FIELD_TARGET_VERSION),
                productKey = json.getString(FIELD_PRODUCT_KEY),
                hardwareRevision = json.getString(FIELD_HARDWARE_REVISION),
                manifestTag = json.optString(FIELD_MANIFEST_TAG),
                sha256 = json.getString(FIELD_SHA256),
                recordedAtEpochMillis = json.getLong(FIELD_RECORDED_AT)
            )
        }.getOrNull()
    }

    private fun JSONArray.strings(): List<String> = buildList {
        repeat(length()) { index -> add(getString(index)) }
    }

    private fun SharedPreferences.Editor.commitOrThrow() {
        check(commit()) { "OTA transaction journal write failed." }
    }

    companion object {
        fun create(
            context: Context,
            ownerUid: String
        ): SharedPreferencesDeviceOtaTransactionStore {
            require(ownerUid.isNotBlank()) { "OTA transaction owner is missing." }
            val appContext = context.applicationContext
            return SharedPreferencesDeviceOtaTransactionStore(
                preferences = EncryptedSharedPreferences.create(
                    appContext,
                    FILE_NAME,
                    MasterKey.Builder(appContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ),
                ownerUid = ownerUid
            )
        }

        private val LOCK = Any()
        private const val FILE_NAME = "aql_device_ota_transactions"
        private const val SCHEMA_VERSION = 1
        private const val FIELD_SCHEMA = "schema"
        private const val FIELD_STARTED_AT = "started_at"
        private const val FIELD_RECOVERY_DEADLINE = "recovery_deadline"
        private const val FIELD_AWAITING_VERSION = "awaiting_version"
        private const val FIELD_RECORDED_AT = "recorded_at"
        private const val FIELD_PLAN = "plan"
        private const val FIELD_DEVICE_UID = "device_uid"
        private const val FIELD_CURRENT_VERSION = "current_version"
        private const val FIELD_TARGET_VERSION = "target_version"
        private const val FIELD_CHANNEL = "channel"
        private const val FIELD_ENVIRONMENT = "environment"
        private const val FIELD_PRODUCT_KEY = "product_key"
        private const val FIELD_PRODUCT_ID = "product_id"
        private const val FIELD_MODEL = "model"
        private const val FIELD_HARDWARE_REVISION = "hardware_revision"
        private const val FIELD_DISPLAY_NAME = "display_name"
        private const val FIELD_FILENAME = "filename"
        private const val FIELD_DOWNLOAD_URL = "download_url"
        private const val FIELD_SHA256 = "sha256"
        private const val FIELD_SIZE_BYTES = "size_bytes"
        private const val FIELD_APPLY_NOW = "apply_now"
        private const val FIELD_RUNTIME_GENERATION = "runtime_generation"
        private const val FIELD_MANIFEST_TAG = "manifest_tag"
        private const val FIELD_RELEASE_CONTENT = "release_content"
        private const val FIELD_LOCALE_TAG = "locale_tag"
        private const val FIELD_TITLE = "title"
        private const val FIELD_SUMMARY = "summary"
        private const val FIELD_CHANGES = "changes"
        private const val FIELD_WARNINGS = "warnings"
        private const val FIELD_MANDATORY = "mandatory"
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte ->
        HEX[(byte.toInt() ushr 4) and 0x0f].toString() + HEX[byte.toInt() and 0x0f]
    }

private const val HEX = "0123456789abcdef"
