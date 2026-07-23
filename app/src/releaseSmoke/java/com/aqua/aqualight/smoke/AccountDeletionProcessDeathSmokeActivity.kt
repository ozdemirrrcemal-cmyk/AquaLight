package com.aqua.aqualight.smoke

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.widget.TextView
import com.aqua.aqualight.data.auth.AccountDeletionCheckpoint
import com.aqua.aqualight.data.auth.AccountDeletionCheckpointStore
import com.aqua.aqualight.data.auth.AccountDeletionManager
import com.aqua.aqualight.data.auth.AccountDeletionOperations
import com.aqua.aqualight.data.auth.EncryptedAccountDeletionCheckpointStore
import com.aqua.aqualight.data.user.CloudUserDataCleaner
import com.aqua.aqualight.data.user.UserDataCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CI-only harness packaged exclusively in the minified releaseSmoke APK.
 *
 * The shell runner prepares one durable checkpoint, force-stops the package, and launches this
 * Activity again. Recovery therefore reads the encrypted checkpoint in a different Linux process
 * and verifies that already-completed deletion operations are not repeated.
 */
class AccountDeletionProcessDeathSmokeActivity : Activity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render(RUNNING_MARKER)

        activityScope.launch {
            val marker = runCatching {
                withContext(Dispatchers.IO) {
                    executeRequestedAction()
                }
            }.fold(
                onSuccess = { it },
                onFailure = { error ->
                    "$FAIL_MARKER:${error::class.java.simpleName}:${error.message.orEmpty()}"
                }
            )
            render(marker)
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private suspend fun executeRequestedAction(): String {
        val scenario = Scenario.fromId(
            intent.getStringExtra(EXTRA_SCENARIO).orEmpty()
        )
        return when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_PREPARE -> prepare(scenario)
            ACTION_RESUME -> resume(scenario)
            else -> error("Unsupported process-death smoke action.")
        }
    }

    private fun prepare(scenario: Scenario): String {
        val store = checkpointStore()
        store.read()?.let { previous -> store.clear(previous.ownerUid) }

        val state = statePreferences()
        check(state.edit().clear().commit()) {
            "Process-death smoke state could not be reset."
        }
        check(
            state.edit()
                .putString(KEY_SCENARIO, scenario.id)
                .putString(KEY_CURRENT_UID, scenario.currentUid)
                .putInt(KEY_PREPARE_PID, Process.myPid())
                .commit()
        ) {
            "Process-death smoke preparation could not be persisted."
        }

        store.begin(OWNER_UID)
        AccountDeletionCheckpoint.Stage.entries
            .filter { it.ordinal in 1..scenario.stage.ordinal }
            .forEach { next -> store.advance(OWNER_UID, next) }

        return "$PREPARED_MARKER:${scenario.id}:${scenario.stage.name}"
    }

    private suspend fun resume(scenario: Scenario): String {
        val state = statePreferences()
        check(state.getString(KEY_SCENARIO, null) == scenario.id) {
            "Prepared scenario does not match the requested recovery scenario."
        }
        val preparePid = state.getInt(KEY_PREPARE_PID, 0)
        check(preparePid > 0 && preparePid != Process.myPid()) {
            "Recovery did not start in a new application process."
        }

        val store = checkpointStore()
        val result = AccountDeletionManager.createForRecoveryTest(
            operations = PersistentSmokeOperations(applicationContext),
            checkpointStore = store
        ).resumePendingDeletion()

        checkNotNull(result) { "Prepared checkpoint was not recovered." }
        check(result.isAccountDeleted) {
            "Account deletion recovery failed: ${result.accountDeleteError?.message.orEmpty()}"
        }
        check(!result.hasPostDeleteCleanupErrors) {
            "Post-delete cleanup remained incomplete."
        }
        check(store.read() == null) {
            "Completed account deletion checkpoint was not cleared."
        }
        check(readCounts(state) == scenario.expected) {
            "Unexpected operation counts: ${readCounts(state)} != ${scenario.expected}"
        }

        return "$PASS_MARKER:${scenario.id}:pid-$preparePid-to-${Process.myPid()}"
    }

    private fun checkpointStore(): AccountDeletionCheckpointStore {
        return EncryptedAccountDeletionCheckpointStore(
            context = applicationContext,
            fileName = CHECKPOINT_FILE
        )
    }

    private fun statePreferences() = applicationContext.getSharedPreferences(
        STATE_FILE,
        Context.MODE_PRIVATE
    )

    private fun render(marker: String) {
        setContentView(
            TextView(this).apply {
                text = marker
                contentDescription = marker
                gravity = Gravity.CENTER
                textSize = 18f
            }
        )
    }

    private enum class Scenario(
        val id: String,
        val stage: AccountDeletionCheckpoint.Stage,
        val currentUid: String?,
        val expected: OperationCounts
    ) {
        STARTED(
            id = "started",
            stage = AccountDeletionCheckpoint.Stage.STARTED,
            currentUid = OWNER_UID,
            expected = OperationCounts(cloud = 1, authDelete = 1, local = 1, revoke = 1, signOut = 1)
        ),
        CLOUD_CLEARED(
            id = "cloud-cleared",
            stage = AccountDeletionCheckpoint.Stage.CLOUD_CLEARED,
            currentUid = OWNER_UID,
            expected = OperationCounts(cloud = 0, authDelete = 1, local = 1, revoke = 1, signOut = 1)
        ),
        AUTH_DELETE_REQUESTED(
            id = "auth-delete-requested",
            stage = AccountDeletionCheckpoint.Stage.AUTH_DELETE_REQUESTED,
            currentUid = OWNER_UID,
            expected = OperationCounts(cloud = 0, authDelete = 1, local = 1, revoke = 1, signOut = 1)
        ),
        AUTH_CONFIRMED_BEFORE_CHECKPOINT(
            id = "auth-confirmed-before-checkpoint",
            stage = AccountDeletionCheckpoint.Stage.AUTH_DELETE_REQUESTED,
            currentUid = null,
            expected = OperationCounts(cloud = 0, authDelete = 0, local = 1, revoke = 1, signOut = 1)
        ),
        ACCOUNT_DELETED(
            id = "account-deleted",
            stage = AccountDeletionCheckpoint.Stage.ACCOUNT_DELETED,
            currentUid = null,
            expected = OperationCounts(cloud = 0, authDelete = 0, local = 1, revoke = 1, signOut = 1)
        );

        companion object {
            fun fromId(id: String): Scenario = entries.firstOrNull { it.id == id }
                ?: error("Unknown process-death smoke scenario: $id")
        }
    }

    private data class OperationCounts(
        val cloud: Int,
        val authDelete: Int,
        val local: Int,
        val revoke: Int,
        val signOut: Int
    )

    private class PersistentSmokeOperations(
        context: Context
    ) : AccountDeletionOperations {
        private val state = context.applicationContext.getSharedPreferences(
            STATE_FILE,
            Context.MODE_PRIVATE
        )

        override fun currentOwnerUid(): String? = state.getString(KEY_CURRENT_UID, null)

        override suspend fun clearCloudUserData(
            ownerUid: String
        ): CloudUserDataCleaner.CleanupResult {
            check(ownerUid == OWNER_UID)
            increment(KEY_CLOUD_CALLS)
            return CloudUserDataCleaner.CleanupResult.Success
        }

        override suspend fun deleteCurrentAccount(ownerUid: String): Throwable? {
            if (currentOwnerUid() != ownerUid) {
                return IllegalStateException("Smoke authenticated owner mismatch.")
            }
            increment(KEY_AUTH_DELETE_CALLS)
            check(state.edit().remove(KEY_CURRENT_UID).commit()) {
                "Smoke authentication state could not be persisted."
            }
            return null
        }

        override suspend fun clearLocalUserData(
            ownerUid: String
        ): UserDataCleaner.CleanupResult {
            check(ownerUid == OWNER_UID)
            increment(KEY_LOCAL_CALLS)
            return UserDataCleaner.CleanupResult.Success
        }

        override suspend fun revokeGoogleAccess(): Throwable? {
            increment(KEY_REVOKE_CALLS)
            return null
        }

        override fun signOut(): Throwable? {
            increment(KEY_SIGN_OUT_CALLS)
            check(state.edit().remove(KEY_CURRENT_UID).commit()) {
                "Smoke sign-out state could not be persisted."
            }
            return null
        }

        private fun increment(key: String) {
            check(state.edit().putInt(key, state.getInt(key, 0) + 1).commit()) {
                "Smoke operation counter could not be persisted: $key"
            }
        }
    }

    private companion object {
        const val EXTRA_ACTION = "aqua_account_deletion_action"
        const val EXTRA_SCENARIO = "aqua_account_deletion_scenario"
        const val ACTION_PREPARE = "prepare"
        const val ACTION_RESUME = "resume"
        const val OWNER_UID = "release-smoke-deletion-owner"
        const val CHECKPOINT_FILE = "account_deletion_recovery_release_smoke"
        const val STATE_FILE = "account_deletion_process_death_smoke"
        const val RUNNING_MARKER = "ACCOUNT_DELETION_PROCESS_DEATH_RUNNING"
        const val PREPARED_MARKER = "ACCOUNT_DELETION_PROCESS_DEATH_PREPARED"
        const val PASS_MARKER = "ACCOUNT_DELETION_PROCESS_DEATH_PASS"
        const val FAIL_MARKER = "ACCOUNT_DELETION_PROCESS_DEATH_FAIL"
        const val KEY_SCENARIO = "scenario"
        const val KEY_CURRENT_UID = "current_uid"
        const val KEY_PREPARE_PID = "prepare_pid"
        const val KEY_CLOUD_CALLS = "cloud_calls"
        const val KEY_AUTH_DELETE_CALLS = "auth_delete_calls"
        const val KEY_LOCAL_CALLS = "local_calls"
        const val KEY_REVOKE_CALLS = "revoke_calls"
        const val KEY_SIGN_OUT_CALLS = "sign_out_calls"

        fun readCounts(state: android.content.SharedPreferences): OperationCounts {
            return OperationCounts(
                cloud = state.getInt(KEY_CLOUD_CALLS, 0),
                authDelete = state.getInt(KEY_AUTH_DELETE_CALLS, 0),
                local = state.getInt(KEY_LOCAL_CALLS, 0),
                revoke = state.getInt(KEY_REVOKE_CALLS, 0),
                signOut = state.getInt(KEY_SIGN_OUT_CALLS, 0)
            )
        }
    }
}
