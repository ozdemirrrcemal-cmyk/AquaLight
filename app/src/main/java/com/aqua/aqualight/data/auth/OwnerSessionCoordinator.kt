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
            val snapshot = stateMachine.snapshot()
            val providersAlreadyBound =
                DevicesRepositoryProvider.currentOwnerUid() == normalizedOwnerUid &&
                    TankDeviceAssignmentRepositoryProvider.currentOwnerUid() == normalizedOwnerUid

            if (
                snapshot.activeOwnerUid == normalizedOwnerUid &&
                providersAlreadyBound
            ) {
                // Cleanup is retryable and must never make an otherwise active session unavailable.
                runCatching {
                    AppMediaRecoveryManager(appContext).reconcileOwner(normalizedOwnerUid)
                }
                return@withLock OpenResult.AlreadyActive(
                    ownerUid = normalizedOwnerUid,
                    generation = snapshot.generation
                )
            }

            val transition = stateMachine.begin(normalizedOwnerUid)
            val previousOwnerUid = transition.previousOwnerUid

            if (previousOwnerUid != null && previousOwnerUid != normalizedOwnerUid) {
                // Runtime/socket/token teardown is the first and awaited owner-switch barrier.
                DevicesRepositoryProvider.clear(expectedOwnerUid = previousOwnerUid)
                TankDeviceAssignmentRepositoryProvider.clear(expectedOwnerUid = previousOwnerUid)
            }

            if (UserDataScope.currentUid() != normalizedOwnerUid) {
                stateMachine.abort(transition)
                return@withLock OpenResult.Superseded(
                    ownerUid = normalizedOwnerUid,
                    generation = transition.generation
                )
            }

            try {
                AqlProvisioningHandoffSaver(appContext)
                    .rollbackPendingRegistrationsForOwner(normalizedOwnerUid)
                    .getOrThrow()

                ProvisioningCommitRecoveryStore(appContext)
                    .recoverOwner(normalizedOwnerUid)

                val credentialStore = DeviceCredentialStore(
                    context = appContext,
                    ownerUid = normalizedOwnerUid
                )
                credentialStore.discardStagedTokens()

                val devicesRepository = DevicesRepositoryProvider.get(appContext)

                withTimeout(REPOSITORY_READY_TIMEOUT_MILLIS) {
                    devicesRepository.ready.first { ready -> ready }
                }

                val removedOrphanCredentialCount = credentialStore.retainTokensFor(
                    devicesRepository.currentDevices().map { device -> device.deviceUid }
                )

                if (!stateMachine.isCurrent(transition)) {
                    clearTransitionProviders(transition)
                    return@withLock OpenResult.Superseded(
                        ownerUid = normalizedOwnerUid,
                        generation = transition.generation
                    )
                }

                val repairedAssignmentCount = when (
                    val repairResult = TankDeviceAssignmentRepositoryProvider
                        .get(appContext)
                        .repairOwnerAssignments()
                ) {
                    is TankAssignmentRepairResult.Completed ->
                        repairResult.removedAssignments.size

                    is TankAssignmentRepairResult.Failure ->
                        throw repairResult.error
                }

                val tankCareRecovery = TankCareIntegrityRecovery
                    .create(appContext)
                    .recover(normalizedOwnerUid)
                val repairedCareTaskCount = tankCareRecovery.removedTaskCount +
                    CareTaskDataStoreManager
                        .create(appContext)
                        .repairOrphanedTankTasks(normalizedOwnerUid)

                // Application.onCreate may run before UserDataScope is installed. The owner session
                // barrier is the authoritative point for crash/process-death media reconciliation.
                // A cleanup failure retains the journal and is retried on the next session opening.
                runCatching {
                    AppMediaRecoveryManager(appContext).reconcileOwner(normalizedOwnerUid)
                }

                SessionBoundServiceManager.start(
                    context = appContext,
                    ownerUid = normalizedOwnerUid
                )

                if (!stateMachine.commit(transition)) {
                    clearTransitionProviders(transition)
                    OpenResult.Superseded(
                        ownerUid = normalizedOwnerUid,
                        generation = transition.generation
                    )
                } else {
                    OpenResult.Active(
                        ownerUid = normalizedOwnerUid,
                        generation = transition.generation,
                        repairedAssignmentCount = repairedAssignmentCount,
                        repairedCareTaskCount = repairedCareTaskCount,
                        removedOrphanCredentialCount = removedOrphanCredentialCount
                    )
                }
            } catch (error: Throwable) {
                stateMachine.abort(transition)
                clearTransitionProviders(transition)

                if (
                    error is CancellationException &&
                    error !is TimeoutCancellationException
                ) {
                    throw error
                }

                OpenResult.Failure(
                    ownerUid = normalizedOwnerUid,
                    generation = transition.generation,
                    error = error
                )
            }
        }
    }

    internal suspend fun openForeground(ownerUid: String): OpenResult {
        val normalizedOwnerUid = ownerUid.trim().also { normalized ->
            require(normalized.isNotBlank()) { "ownerUid must not be blank" }
        }
        return backgroundLeaseMutex.withLock {
            val result = open(normalizedOwnerUid)
            val generation = result.activeGenerationOrNull()
            val backgroundOwned = generation != null &&
                backgroundLeases.isOwned(normalizedOwnerUid, generation)
            if (!backgroundOwned) {
                result
            } else {
                val resolvedGeneration = checkNotNull(generation)
                val startFailure = runCatching {
                    SessionBoundServiceManager.start(
                        context = appContext,
                        ownerUid = normalizedOwnerUid,
                        enqueueFirmwareImmediate = false
                    )
                }.exceptionOrNull()
                if (startFailure is CancellationException) throw startFailure
                if (startFailure == null) {
                    backgroundLeases.promote(normalizedOwnerUid, resolvedGeneration)
                    result
                } else {
                    OpenResult.Failure(
                        ownerUid = normalizedOwnerUid,
                        generation = resolvedGeneration,
                        error = startFailure
                    )
                }
            }
        }
    }

    internal suspend fun acquireBackgroundLease(
        ownerUid: String
    ): Result<OwnerBackgroundLeaseRegistry.Lease> {
        val normalizedOwnerUid = ownerUid.trim().also { normalized ->
            require(normalized.isNotBlank()) { "ownerUid must not be blank" }
        }
        return backgroundLeaseMutex.withLock {
            val snapshot = stateMachine.snapshot()
            val activeOwnerMatches = snapshot.activeOwnerUid == normalizedOwnerUid &&
                snapshot.pendingOwnerUid == null
            val providersMatch = DevicesRepositoryProvider.currentOwnerUid() == normalizedOwnerUid &&
                TankDeviceAssignmentRepositoryProvider.currentOwnerUid() == normalizedOwnerUid
            val sessionBusy = snapshot.activeOwnerUid != null || snapshot.pendingOwnerUid != null
            val providersBusy = DevicesRepositoryProvider.currentOwnerUid() != null ||
                TankDeviceAssignmentRepositoryProvider.currentOwnerUid() != null

            when {
                activeOwnerMatches && providersMatch -> Result.success(
                    backgroundLeases.acquireExisting(normalizedOwnerUid, snapshot.generation)
                )
                sessionBusy -> Result.failure(
                    IllegalStateException("Owner runtime is busy with another session transition.")
                )
                providersBusy -> Result.failure(
                    IllegalStateException("Owner runtime providers are not in a cold-start state.")
                )
                else -> openBackgroundLease(normalizedOwnerUid)
            }
        }
    }

    private suspend fun openBackgroundLease(
        ownerUid: String
    ): Result<OwnerBackgroundLeaseRegistry.Lease> {
        return when (
            val result = OwnerRuntimeOpenMode.withBackgroundOpen {
                open(ownerUid)
            }
        ) {
            is OpenResult.Active -> Result.success(
                backgroundLeases.markCreated(ownerUid, result.generation)
            )
            is OpenResult.AlreadyActive -> Result.success(
                backgroundLeases.acquireExisting(ownerUid, result.generation)
            )
            is OpenResult.Superseded -> Result.failure(
                IllegalStateException("Background owner runtime was superseded before commit.")
            )
            is OpenResult.Failure -> Result.failure(result.error)
        }
    }

    internal suspend fun releaseBackgroundLease(
        lease: OwnerBackgroundLeaseRegistry.Lease
    ) {
        backgroundLeaseMutex.withLock {
            if (backgroundLeases.release(lease)) {
                transitionMutex.withLock {
                    val snapshot = stateMachine.snapshot()
                    val sameCommittedRuntime = snapshot.activeOwnerUid == lease.ownerUid &&
                        snapshot.pendingOwnerUid == null &&
                        snapshot.generation == lease.generation
                    if (sameCommittedRuntime) {
                        stateMachine.close(lease.ownerUid)
                        DevicesRepositoryProvider.clear(expectedOwnerUid = lease.ownerUid)
                        TankDeviceAssignmentRepositoryProvider.clear(expectedOwnerUid = lease.ownerUid)
                    }
                }
            }
        }
    }

    internal suspend fun closeForeground(
        expectedOwnerUid: String? = null,
        cancelNotifications: Boolean = true
    ): CloseResult {
        return backgroundLeaseMutex.withLock {
            backgroundLeases.clear(expectedOwnerUid?.trim()?.takeIf(String::isNotBlank))
            close(
                expectedOwnerUid = expectedOwnerUid,
                cancelNotifications = cancelNotifications
            )
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

    private suspend fun clearTransitionProviders(
        transition: OwnerSessionStateMachine.Transition
    ) {
        val ownerUid = transition.targetOwnerUid ?: return
        DevicesRepositoryProvider.clear(expectedOwnerUid = ownerUid)
        TankDeviceAssignmentRepositoryProvider.clear(expectedOwnerUid = ownerUid)
    }

    companion object {
        private const val REPOSITORY_READY_TIMEOUT_MILLIS = 15_000L

        private val stateMachine = OwnerSessionStateMachine()
        private val transitionMutex = Mutex()
        private val backgroundLeaseMutex = Mutex()
        private val backgroundLeases = OwnerBackgroundLeaseRegistry()

        fun create(context: Context): OwnerSessionCoordinator {
            return OwnerSessionCoordinator(
                appContext = context.applicationContext
            )
        }
    }
}

private fun OwnerSessionCoordinator.OpenResult.activeGenerationOrNull(): Long? = when (this) {
    is OwnerSessionCoordinator.OpenResult.Active -> generation
    is OwnerSessionCoordinator.OpenResult.AlreadyActive -> generation
    is OwnerSessionCoordinator.OpenResult.Superseded,
    is OwnerSessionCoordinator.OpenResult.Failure -> null
}
