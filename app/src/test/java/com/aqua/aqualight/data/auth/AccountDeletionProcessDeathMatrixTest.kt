package com.aqua.aqualight.data.auth

import com.aqua.aqualight.data.user.CloudUserDataCleaner
import com.aqua.aqualight.data.user.UserDataCleaner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionProcessDeathMatrixTest {

    @Test
    fun everyDurableStageResumesInANewProcessWithoutRepeatingCompletedWork() = runTest {
        processDeathScenarios().forEach { scenario ->
            val store = InMemoryCheckpointStore()
            store.seed(scenario.stage)
            val backend = FakeDurableBackend(currentUid = scenario.currentUid)

            val result = newProcess(store, backend).resumePendingDeletion()

            requireNotNull(result)
            assertTrue("${scenario.name}: account deletion", result.isAccountDeleted)
            assertFalse("${scenario.name}: post-delete cleanup", result.hasPostDeleteCleanupErrors)
            assertNull("${scenario.name}: checkpoint", store.read())
            assertEquals("${scenario.name}: operation counts", scenario.expected, backend.counts())
        }
    }

    @Test
    fun transientCloudFailureStaysAtStartedAndARecreatedProcessRetries() = runTest {
        val store = InMemoryCheckpointStore().apply {
            seed(AccountDeletionCheckpoint.Stage.STARTED)
        }
        val backend = FakeDurableBackend(
            currentUid = OWNER_UID,
            cloudFailuresRemaining = 1
        )

        val interrupted = newProcess(store, backend).resumePendingDeletion()
        requireNotNull(interrupted)
        assertFalse(interrupted.isAccountDeleted)
        assertEquals(AccountDeletionCheckpoint.Stage.STARTED, store.read()?.stage)

        val recovered = newProcess(store, backend).resumePendingDeletion()
        requireNotNull(recovered)
        assertTrue(recovered.isAccountDeleted)
        assertFalse(recovered.hasPostDeleteCleanupErrors)
        assertNull(store.read())
        assertEquals(OperationCounts(cloud = 2, authDelete = 1, local = 1, revoke = 1, signOut = 1), backend.counts())
    }

    @Test
    fun reauthenticationFailureStaysAtAuthRequestedAndARecreatedProcessRetries() = runTest {
        val store = InMemoryCheckpointStore().apply {
            seed(AccountDeletionCheckpoint.Stage.AUTH_DELETE_REQUESTED)
        }
        val backend = FakeDurableBackend(
            currentUid = OWNER_UID,
            authFailuresRemaining = 1
        )

        val interrupted = newProcess(store, backend).resumePendingDeletion()
        requireNotNull(interrupted)
        assertFalse(interrupted.isAccountDeleted)
        assertEquals(
            AccountDeletionCheckpoint.Stage.AUTH_DELETE_REQUESTED,
            store.read()?.stage
        )

        val recovered = newProcess(store, backend).resumePendingDeletion()
        requireNotNull(recovered)
        assertTrue(recovered.isAccountDeleted)
        assertFalse(recovered.hasPostDeleteCleanupErrors)
        assertNull(store.read())
        assertEquals(OperationCounts(cloud = 0, authDelete = 2, local = 1, revoke = 1, signOut = 1), backend.counts())
    }

    @Test
    fun repeatedLocalFailureKeepsAccountDeletedCheckpointUntilAnotherProcessSucceeds() = runTest {
        val store = InMemoryCheckpointStore().apply {
            seed(AccountDeletionCheckpoint.Stage.ACCOUNT_DELETED)
        }
        val backend = FakeDurableBackend(
            currentUid = null,
            localFailuresRemaining = 2
        )

        val interrupted = newProcess(store, backend).resumePendingDeletion()
        requireNotNull(interrupted)
        assertTrue(interrupted.isAccountDeleted)
        assertTrue(interrupted.hasLocalCleanupErrors)
        assertEquals(AccountDeletionCheckpoint.Stage.ACCOUNT_DELETED, store.read()?.stage)

        val recovered = newProcess(store, backend).resumePendingDeletion()
        requireNotNull(recovered)
        assertTrue(recovered.isAccountDeleted)
        assertFalse(recovered.hasPostDeleteCleanupErrors)
        assertNull(store.read())
        assertEquals(OperationCounts(cloud = 0, authDelete = 0, local = 3, revoke = 2, signOut = 2), backend.counts())
    }

    @Test
    fun aDifferentAuthenticatedOwnerFailsClosedWithoutRunningAnyDeletionStep() = runTest {
        val store = InMemoryCheckpointStore().apply {
            seed(AccountDeletionCheckpoint.Stage.STARTED)
        }
        val backend = FakeDurableBackend(currentUid = "different-owner")

        val result = newProcess(store, backend).resumePendingDeletion()

        requireNotNull(result)
        assertFalse(result.isAccountDeleted)
        assertEquals(AccountDeletionCheckpoint.Stage.STARTED, store.read()?.stage)
        assertEquals(OperationCounts(), backend.counts())
    }

    @Test
    fun noCheckpointMeansThereIsNothingToRecover() = runTest {
        val store = InMemoryCheckpointStore()
        val backend = FakeDurableBackend(currentUid = OWNER_UID)

        assertNull(newProcess(store, backend).resumePendingDeletion())
        assertEquals(OperationCounts(), backend.counts())
    }

    private fun newProcess(
        store: AccountDeletionCheckpointStore,
        backend: FakeDurableBackend
    ): AccountDeletionManager {
        return AccountDeletionManager.createForRecoveryTest(
            operations = FakeOperations(backend),
            checkpointStore = store
        )
    }

    private fun processDeathScenarios(): List<ProcessDeathScenario> = listOf(
        ProcessDeathScenario(
            name = "after STARTED",
            stage = AccountDeletionCheckpoint.Stage.STARTED,
            currentUid = OWNER_UID,
            expected = OperationCounts(cloud = 1, authDelete = 1, local = 1, revoke = 1, signOut = 1)
        ),
        ProcessDeathScenario(
            name = "after CLOUD_CLEARED",
            stage = AccountDeletionCheckpoint.Stage.CLOUD_CLEARED,
            currentUid = OWNER_UID,
            expected = OperationCounts(cloud = 0, authDelete = 1, local = 1, revoke = 1, signOut = 1)
        ),
        ProcessDeathScenario(
            name = "after AUTH_DELETE_REQUESTED",
            stage = AccountDeletionCheckpoint.Stage.AUTH_DELETE_REQUESTED,
            currentUid = OWNER_UID,
            expected = OperationCounts(cloud = 0, authDelete = 1, local = 1, revoke = 1, signOut = 1)
        ),
        ProcessDeathScenario(
            name = "after Firebase confirmed deletion before checkpoint advance",
            stage = AccountDeletionCheckpoint.Stage.AUTH_DELETE_REQUESTED,
            currentUid = null,
            expected = OperationCounts(cloud = 0, authDelete = 0, local = 1, revoke = 1, signOut = 1)
        ),
        ProcessDeathScenario(
            name = "after ACCOUNT_DELETED",
            stage = AccountDeletionCheckpoint.Stage.ACCOUNT_DELETED,
            currentUid = null,
            expected = OperationCounts(cloud = 0, authDelete = 0, local = 1, revoke = 1, signOut = 1)
        )
    )

    private data class ProcessDeathScenario(
        val name: String,
        val stage: AccountDeletionCheckpoint.Stage,
        val currentUid: String?,
        val expected: OperationCounts
    )

    private data class OperationCounts(
        val cloud: Int = 0,
        val authDelete: Int = 0,
        val local: Int = 0,
        val revoke: Int = 0,
        val signOut: Int = 0
    )

    private class FakeDurableBackend(
        var currentUid: String?,
        var cloudFailuresRemaining: Int = 0,
        var authFailuresRemaining: Int = 0,
        var localFailuresRemaining: Int = 0,
        var cloudCalls: Int = 0,
        var authDeleteCalls: Int = 0,
        var localCalls: Int = 0,
        var revokeCalls: Int = 0,
        var signOutCalls: Int = 0
    ) {
        fun counts(): OperationCounts = OperationCounts(
            cloud = cloudCalls,
            authDelete = authDeleteCalls,
            local = localCalls,
            revoke = revokeCalls,
            signOut = signOutCalls
        )
    }

    private class FakeOperations(
        private val backend: FakeDurableBackend
    ) : AccountDeletionOperations {

        override fun currentOwnerUid(): String? = backend.currentUid

        override suspend fun clearCloudUserData(
            ownerUid: String
        ): CloudUserDataCleaner.CleanupResult {
            backend.cloudCalls += 1
            if (backend.cloudFailuresRemaining > 0) {
                backend.cloudFailuresRemaining -= 1
                return CloudUserDataCleaner.CleanupResult(
                    error = IllegalStateException("simulated cloud interruption")
                )
            }
            return CloudUserDataCleaner.CleanupResult.Success
        }

        override suspend fun deleteCurrentAccount(ownerUid: String): Throwable? {
            backend.authDeleteCalls += 1
            if (backend.authFailuresRemaining > 0) {
                backend.authFailuresRemaining -= 1
                return IllegalStateException("simulated recent-login requirement")
            }
            backend.currentUid = null
            return null
        }

        override suspend fun clearLocalUserData(
            ownerUid: String
        ): UserDataCleaner.CleanupResult {
            backend.localCalls += 1
            if (backend.localFailuresRemaining > 0) {
                backend.localFailuresRemaining -= 1
                return UserDataCleaner.CleanupResult(
                    issues = listOf(
                        UserDataCleaner.CleanupIssue(
                            step = UserDataCleaner.Step.USER_PREFERENCES,
                            error = IllegalStateException("simulated local interruption")
                        )
                    )
                )
            }
            return UserDataCleaner.CleanupResult.Success
        }

        override suspend fun revokeGoogleAccess(): Throwable? {
            backend.revokeCalls += 1
            return null
        }

        override fun signOut(): Throwable? {
            backend.signOutCalls += 1
            backend.currentUid = null
            return null
        }
    }

    private class InMemoryCheckpointStore : AccountDeletionCheckpointStore {
        private var checkpoint: AccountDeletionCheckpoint? = null

        override fun read(): AccountDeletionCheckpoint? = checkpoint

        override fun begin(ownerUid: String): AccountDeletionCheckpoint {
            checkpoint?.let { return it }
            return AccountDeletionCheckpoint(
                ownerUid = ownerUid,
                stage = AccountDeletionCheckpoint.Stage.STARTED,
                startedAtMillis = 1L
            ).also { checkpoint = it }
        }

        override fun advance(
            ownerUid: String,
            stage: AccountDeletionCheckpoint.Stage
        ): AccountDeletionCheckpoint {
            val current = requireNotNull(checkpoint)
            check(current.ownerUid == ownerUid)
            check(AccountDeletionCheckpointPolicy.canAdvance(current.stage, stage))
            return current.copy(stage = stage).also { checkpoint = it }
        }

        override fun clear(ownerUid: String) {
            check(checkpoint?.ownerUid == ownerUid)
            checkpoint = null
        }

        fun seed(stage: AccountDeletionCheckpoint.Stage) {
            begin(OWNER_UID)
            AccountDeletionCheckpoint.Stage.entries
                .filter { it.ordinal in 1..stage.ordinal }
                .forEach { next -> advance(OWNER_UID, next) }
        }
    }

    private companion object {
        const val OWNER_UID = "process-death-owner"
    }
}
