package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.aquarium.devices.TankAssignmentRepairResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.integrity.TankCareIntegrityRecovery
import com.aqua.aqualight.data.devices.provisioning.repository.AqlProvisioningHandoffSaver
import com.aqua.aqualight.data.devices.provisioning.store.ProvisioningCommitRecoveryStore
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore
import com.aqua.aqualight.data.media.AppMediaRecoveryManager
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class OwnerSessionCoordinator private constructor(
    private val appContext: Context
) {

    sealed interface OpenResult {
        data class Active(
            val ownerUid: String,
            val generation: Long,
            val repairedAssignmentCount: Int,
            val repairedCareTaskCount: Int,
            val removedOrphanCredentialCount: Int
        ) : OpenResult

        data class AlreadyActive(
            val ownerUid: String,
            val generation: Long
        ) : OpenResult

        data class Superseded(
            val ownerUid: String,
            val generation: Long
        ) : OpenResult

        data class Failure(
            val ownerUid: String,
            val generation: Long,
            val error: Throwable
        ) : OpenResult
    }

    sealed interface CloseResult {
        data class Closed(
            val ownerUid: String?,
            val generation: Long,
            val stopResult: SessionBoundServiceManager.StopResult
        ) : CloseResult

        data class StaleRequestIgnored(
            val expectedOwnerUid: String
        ) : CloseResult
    }

    suspend fun open(ownerUid: String): OpenResult {
        val normalizedOwnerUid = ownerUid.trim().also { normalized ->
            require(normalized.isNotBlank()) { "ownerUid must not be blank" }
        }
        return transitionMutex.withLock {
            OwnerSessionOpenFlow(appContext, stateMachine).open(normalizedOwnerUid)
        }
    }

    suspend fun close(
        expectedOwnerUid: String? = null,
        cancelNotifications: Boolean = true
    ): CloseResult {
        val normalizedExpected = expectedOwnerUid
            ?.trim()
            ?.takeIf(String::isNotBlank)

        return transitionMutex.withLock {
            val transition = stateMachine.close(normalizedExpected)
                ?: return@withLock CloseResult.StaleRequestIgnored(
                    expectedOwnerUid = normalizedExpected.orEmpty()
                )

            val stopResult = SessionBoundServiceManager.stop(
                context = appContext,
                cancelNotifications = cancelNotifications,
                expectedOwnerUid = transition.previousOwnerUid
            )

            CloseResult.Closed(
                ownerUid = transition.previousOwnerUid,
                generation = transition.generation,
                stopResult = stopResult
            )
        }
    }

    fun snapshot(): OwnerSessionStateMachine.Snapshot = stateMachine.snapshot()

    companion object {
        private val stateMachine = OwnerSessionStateMachine()
        private val transitionMutex = Mutex()

        fun create(context: Context): OwnerSessionCoordinator {
            return OwnerSessionCoordinator(
                appContext = context.applicationContext
            )
        }
    }
}

private typealias OwnerOpenResult = OwnerSessionCoordinator.OpenResult

private class OwnerSessionOpenFlow(
    private val appContext: Context,
    private val stateMachine: OwnerSessionStateMachine
) {

    suspend fun open(ownerUid: String): OwnerOpenResult {
        val activeResult = activeResultOrNull(ownerUid)
        return activeResult ?: openTransition(ownerUid)
    }

    private suspend fun activeResultOrNull(ownerUid: String): OwnerOpenResult? {
        val snapshot = stateMachine.snapshot()
        val providersAlreadyBound =
            DevicesRepositoryProvider.currentOwnerUid() == ownerUid &&
                TankDeviceAssignmentRepositoryProvider.currentOwnerUid() == ownerUid
        return if (snapshot.activeOwnerUid == ownerUid && providersAlreadyBound) {
            runCatching { AppMediaRecoveryManager(appContext).reconcileOwner(ownerUid) }
            OwnerSessionCoordinator.OpenResult.AlreadyActive(
                ownerUid = ownerUid,
                generation = snapshot.generation
            )
        } else {
            null
        }
    }

    private suspend fun openTransition(ownerUid: String): OwnerOpenResult {
        val transition = stateMachine.begin(ownerUid)
        val previousStopError = try {
            stopPreviousOwnerIfRequired(transition, ownerUid)
        } catch (error: TimeoutCancellationException) {
            error
        } catch (error: CancellationException) {
            stateMachine.abort(transition)
            throw error
        }
        val blockingResult = when {
            previousStopError != null -> OwnerSessionCoordinator.OpenResult.Failure(
                ownerUid = ownerUid,
                generation = transition.generation,
                error = previousStopError
            )
            UserDataScope.currentUid() != ownerUid ->
                OwnerSessionCoordinator.OpenResult.Superseded(
                    ownerUid = ownerUid,
                    generation = transition.generation
                )
            else -> null
        }
        if (blockingResult != null) {
            stateMachine.abort(transition)
        }
        return blockingResult ?: activateTransition(ownerUid, transition)
    }

    private suspend fun stopPreviousOwnerIfRequired(
        transition: OwnerSessionStateMachine.Transition,
        targetOwnerUid: String
    ): Throwable? {
        val previousOwnerUid = transition.previousOwnerUid
        return if (previousOwnerUid == null || previousOwnerUid == targetOwnerUid) {
            null
        } else {
            SessionBoundServiceManager.stop(
                context = appContext,
                cancelNotifications = true,
                expectedOwnerUid = previousOwnerUid
            ).exceptionOrNull()
        }
    }

    private suspend fun activateTransition(
        ownerUid: String,
        transition: OwnerSessionStateMachine.Transition
    ): OwnerOpenResult {
        val result = runCatching { activateOwner(ownerUid, transition) }
        val error = result.exceptionOrNull()
        if (error != null) {
            abortTransition(transition)
        }
        return when {
            result.isSuccess -> result.getOrThrow()
            error is CancellationException && error !is TimeoutCancellationException ->
                throw error
            else -> OwnerSessionCoordinator.OpenResult.Failure(
                ownerUid = ownerUid,
                generation = transition.generation,
                error = requireNotNull(error)
            )
        }
    }

    private suspend fun activateOwner(
        ownerUid: String,
        transition: OwnerSessionStateMachine.Transition
    ): OwnerOpenResult {
        val runtime = prepareOwnerRuntime(ownerUid)
        return if (!stateMachine.isCurrent(transition)) {
            abortTransition(transition)
            OwnerSessionCoordinator.OpenResult.Superseded(
                ownerUid = ownerUid,
                generation = transition.generation
            )
        } else {
            val repairs = repairOwnerData(ownerUid)
            runCatching { AppMediaRecoveryManager(appContext).reconcileOwner(ownerUid) }
            SessionBoundServiceManager.start(appContext, ownerUid)
            if (stateMachine.commit(transition)) {
                OwnerSessionCoordinator.OpenResult.Active(
                    ownerUid = ownerUid,
                    generation = transition.generation,
                    repairedAssignmentCount = repairs.assignmentCount,
                    repairedCareTaskCount = repairs.careTaskCount,
                    removedOrphanCredentialCount = runtime.removedCredentialCount
                )
            } else {
                abortTransition(transition)
                OwnerSessionCoordinator.OpenResult.Superseded(
                    ownerUid = ownerUid,
                    generation = transition.generation
                )
            }
        }
    }

    private suspend fun prepareOwnerRuntime(
        normalizedOwnerUid: String
    ): PreparedOwnerRuntime {
        AqlProvisioningHandoffSaver(appContext)
            .rollbackPendingRegistrationsForOwner(normalizedOwnerUid)
            .getOrThrow()
        ProvisioningCommitRecoveryStore(appContext).recoverOwner(normalizedOwnerUid)

        val credentialStore = DeviceCredentialStore(
            context = appContext,
            ownerUid = normalizedOwnerUid
        )
        credentialStore.discardStagedTokens()
        val repository = DevicesRepositoryProvider.get(appContext)
        withTimeout(REPOSITORY_READY_TIMEOUT_MILLIS) {
            repository.ready.first { ready -> ready }
        }
        val removedCredentialCount = credentialStore.retainTokensFor(
            repository.currentDevices().map { device -> device.deviceUid }
        )
        return PreparedOwnerRuntime(removedCredentialCount)
    }

    private suspend fun repairOwnerData(
        normalizedOwnerUid: String
    ): OwnerRepairCounts {
        val assignmentCount = when (
            val repairResult = TankDeviceAssignmentRepositoryProvider
                .get(appContext)
                .repairOwnerAssignments()
        ) {
            is TankAssignmentRepairResult.Completed -> repairResult.removedAssignments.size
            is TankAssignmentRepairResult.Failure -> throw repairResult.error
        }
        val tankCareRecovery = TankCareIntegrityRecovery
            .create(appContext)
            .recover(normalizedOwnerUid)
        val careTaskCount = tankCareRecovery.removedTaskCount +
            CareTaskDataStoreManager
                .create(appContext)
                .repairOrphanedTankTasks(normalizedOwnerUid)
        return OwnerRepairCounts(assignmentCount, careTaskCount)
    }

    private suspend fun abortTransition(
        transition: OwnerSessionStateMachine.Transition
    ) {
        stateMachine.abort(transition)
        val ownerUid = transition.targetOwnerUid
        if (ownerUid != null) {
            SessionBoundServiceManager.stop(
                context = appContext,
                cancelNotifications = true,
                expectedOwnerUid = ownerUid
            )
        }
    }

    private companion object {
        const val REPOSITORY_READY_TIMEOUT_MILLIS = 15_000L
    }
}

private data class PreparedOwnerRuntime(
    val removedCredentialCount: Int
)

private data class OwnerRepairCounts(
    val assignmentCount: Int,
    val careTaskCount: Int
)
