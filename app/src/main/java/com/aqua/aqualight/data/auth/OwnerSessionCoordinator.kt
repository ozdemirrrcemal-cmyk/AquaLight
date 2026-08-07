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
            val previousStopError = try {
                stopPreviousOwnerIfRequired(
                    previousOwnerUid = transition.previousOwnerUid,
                    targetOwnerUid = normalizedOwnerUid
                )
            } catch (error: TimeoutCancellationException) {
                stateMachine.abort(transition)
                return@withLock OpenResult.Failure(
                    ownerUid = normalizedOwnerUid,
                    generation = transition.generation,
                    error = error
                )
            } catch (error: CancellationException) {
                stateMachine.abort(transition)
                throw error
            }
            if (previousStopError != null) {
                stateMachine.abort(transition)
                return@withLock OpenResult.Failure(
                    ownerUid = normalizedOwnerUid,
                    generation = transition.generation,
                    error = previousStopError
                )
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

    private suspend fun stopPreviousOwnerIfRequired(
        previousOwnerUid: String?,
        targetOwnerUid: String
    ): Throwable? {
        if (previousOwnerUid == null || previousOwnerUid == targetOwnerUid) {
            return null
        }
        return SessionBoundServiceManager.stop(
            context = appContext,
            cancelNotifications = true,
            expectedOwnerUid = previousOwnerUid
        ).exceptionOrNull()
    }

    private suspend fun clearTransitionProviders(
        transition: OwnerSessionStateMachine.Transition
    ) {
        val ownerUid = transition.targetOwnerUid ?: return
        SessionBoundServiceManager.stop(
            context = appContext,
            cancelNotifications = true,
            expectedOwnerUid = ownerUid
        )
    }

    companion object {
        private const val REPOSITORY_READY_TIMEOUT_MILLIS = 15_000L

        private val stateMachine = OwnerSessionStateMachine()
        private val transitionMutex = Mutex()

        fun create(context: Context): OwnerSessionCoordinator {
            return OwnerSessionCoordinator(
                appContext = context.applicationContext
            )
        }
    }
}
