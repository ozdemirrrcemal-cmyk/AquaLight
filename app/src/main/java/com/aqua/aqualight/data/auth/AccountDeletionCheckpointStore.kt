package com.aqua.aqualight.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal data class AccountDeletionCheckpoint(
    val ownerUid: String,
    val stage: Stage,
    val startedAtMillis: Long
) {
    enum class Stage {
        STARTED,
        CLOUD_CLEARED,
        AUTH_DELETE_REQUESTED,
        ACCOUNT_DELETED
    }
}

internal interface AccountDeletionCheckpointStore {
    fun read(): AccountDeletionCheckpoint?

    fun begin(ownerUid: String): AccountDeletionCheckpoint

    fun advance(
        ownerUid: String,
        stage: AccountDeletionCheckpoint.Stage
    ): AccountDeletionCheckpoint

    fun clear(ownerUid: String)
}

internal object AccountDeletionCheckpointPolicy {
    fun canAdvance(
        current: AccountDeletionCheckpoint.Stage,
        requested: AccountDeletionCheckpoint.Stage
    ): Boolean {
        return requested == current || requested.ordinal == current.ordinal + 1
    }
}

internal class EncryptedAccountDeletionCheckpointStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) : AccountDeletionCheckpointStore {

    private val preferences: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val appContext = context.applicationContext
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun read(): AccountDeletionCheckpoint? = synchronized(LOCK) {
        val schemaVersion = preferences.getInt(KEY_SCHEMA_VERSION, 0)
        if (schemaVersion == 0) return@synchronized null
        check(schemaVersion == SCHEMA_VERSION) {
            "Unsupported account deletion checkpoint schema."
        }

        val ownerUid = preferences.getString(KEY_OWNER_UID, null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: error("Account deletion checkpoint owner is missing.")
        val stageName = preferences.getString(KEY_STAGE, null)
            ?: error("Account deletion checkpoint stage is missing.")
        val stage = runCatching {
            AccountDeletionCheckpoint.Stage.valueOf(stageName)
        }.getOrElse {
            throw IllegalStateException("Account deletion checkpoint stage is invalid.", it)
        }
        val startedAtMillis = preferences.getLong(KEY_STARTED_AT, 0L)
        check(startedAtMillis > 0L) {
            "Account deletion checkpoint timestamp is invalid."
        }

        AccountDeletionCheckpoint(
            ownerUid = ownerUid,
            stage = stage,
            startedAtMillis = startedAtMillis
        )
    }

    override fun begin(ownerUid: String): AccountDeletionCheckpoint = synchronized(LOCK) {
        val normalizedUid = ownerUid.trim()
        require(normalizedUid.isNotBlank()) {
            "Account deletion owner uid is blank."
        }

        read()?.let { existing ->
            check(existing.ownerUid == normalizedUid) {
                "A different owner's account deletion is already pending."
            }
            return@synchronized existing
        }

        val checkpoint = AccountDeletionCheckpoint(
            ownerUid = normalizedUid,
            stage = AccountDeletionCheckpoint.Stage.STARTED,
            startedAtMillis = clock()
        )
        write(checkpoint)
        checkpoint
    }

    override fun advance(
        ownerUid: String,
        stage: AccountDeletionCheckpoint.Stage
    ): AccountDeletionCheckpoint = synchronized(LOCK) {
        val current = requireNotNull(read()) {
            "Account deletion checkpoint is unavailable."
        }
        check(current.ownerUid == ownerUid.trim()) {
            "Account deletion checkpoint owner mismatch."
        }
        if (current.stage == stage) return@synchronized current
        check(AccountDeletionCheckpointPolicy.canAdvance(current.stage, stage)) {
            "Account deletion checkpoint stages must advance exactly once."
        }

        current.copy(stage = stage).also(::write)
    }

    override fun clear(ownerUid: String) = synchronized(LOCK) {
        val current = read() ?: return@synchronized
        check(current.ownerUid == ownerUid.trim()) {
            "Account deletion checkpoint owner mismatch while clearing."
        }
        check(
            preferences.edit().clear().commit()
        ) {
            "Account deletion checkpoint could not be cleared."
        }
    }

    private fun write(checkpoint: AccountDeletionCheckpoint) {
        check(
            preferences.edit()
                .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
                .putString(KEY_OWNER_UID, checkpoint.ownerUid)
                .putString(KEY_STAGE, checkpoint.stage.name)
                .putLong(KEY_STARTED_AT, checkpoint.startedAtMillis)
                .commit()
        ) {
            "Account deletion checkpoint could not be persisted."
        }
    }

    private companion object {
        val LOCK = Any()
        const val FILE_NAME = "account_deletion_recovery"
        const val SCHEMA_VERSION = 1
        const val KEY_SCHEMA_VERSION = "schema_version"
        const val KEY_OWNER_UID = "owner_uid"
        const val KEY_STAGE = "stage"
        const val KEY_STARTED_AT = "started_at"
    }
}
