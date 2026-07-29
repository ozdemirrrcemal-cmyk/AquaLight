package com.aqua.aqualight.data.devices.provisioning.repository

import android.content.Context
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore
import com.aqua.aqualight.data.devices.store.DeviceSnapshotMerger
import com.aqua.aqualight.data.user.UserDataScope
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AqlProvisioningHandoffSaver(
    context: Context
) {

    private class PendingRegistration(
        val ownerUid: String,
        val mode: AqlProvisioningRegistrationMode,
        val deviceUid: DeviceUid,
        val previousSnapshot: DeviceSnapshot?,
        val previousRuntimeToken: String?
    ) {
        val operationMutex = Mutex()
        var stagedCredentialCommitted = false
    }

    private val appContext = context.applicationContext
    private val metadataResolver = AqlProvisioningRuntimeMetadataResolver()

    suspend fun prepareAndConnect(
        draft: AqlProvisioningDraft,
        handoff: AqlProvisioningRuntimeHandoff
    ): Result<DeviceSnapshot> {
        return try {
            Result.success(
                prepareAndConnectOrThrow(
                    draft = draft,
                    handoff = handoff
                )
            )
        } catch (error: Throwable) {
            error.throwIfCancellation()
            Result.failure(error)
        }
    }

    private suspend fun prepareAndConnectOrThrow(
        draft: AqlProvisioningDraft,
        handoff: AqlProvisioningRuntimeHandoff
    ): DeviceSnapshot {
        require(handoff.isUsable) {
            "Runtime handoff is missing device uid, WebSocket endpoint or token."
        }

        val ownerUid = UserDataScope.requireCurrentUid()
        val repository = DevicesRepositoryProvider.get(appContext)
        val previousSnapshot = repository.currentDevice(handoff.deviceUid)
        val credentialStore = DeviceCredentialStore(
            context = appContext,
            ownerUid = ownerUid
        )
        val previousRuntimeToken = credentialStore.getCommittedToken(
            handoff.deviceUid
        )
        val mode = AqlProvisioningRegistrationModeResolver.resolve(
            existingDeviceUid = previousSnapshot?.deviceUid,
            verifiedDeviceUid = handoff.deviceUid
        )
        val pendingRegistration = PendingRegistration(
            ownerUid = ownerUid,
            mode = mode,
            deviceUid = handoff.deviceUid,
            previousSnapshot = previousSnapshot,
            previousRuntimeToken = previousRuntimeToken
        )
        check(pendingRegistry.registerIfAbsent(pendingRegistration)) {
            "A provisioning transaction is already active for this device."
        }

        return pendingRegistration.operationMutex.withLock {
            try {
                credentialStore.stageToken(
                    deviceUid = handoff.deviceUid,
                    token = handoff.webSocketToken
                )

                val incomingSnapshot = DeviceSnapshot(
                    identity = DeviceIdentity(
                        uid = handoff.deviceUid,
                        macAddress = draft.bleAddress,
                        serialNumber = draft.deviceSerial,
                        displayName = resolvedTitle(draft)
                    ),
                    product = DeviceProduct(
                        brand = "AquaLight",
                        family = DeviceFamily.UNKNOWN,
                        familyRaw = "",
                        model = draft.deviceModel,
                        displayName = resolvedTitle(draft)
                    ),
                    firmwareVersion = "",
                    firmwareBuild = "",
                    endpoint = handoff.endpoint,
                    capabilities = DeviceCapabilities(),
                    limits = DeviceLimits(),
                    connectionState = DeviceConnectionState(
                        onlineState = DeviceOnlineState.ONLINE_LAN,
                        lastUdpSeenAtMillis = System.currentTimeMillis(),
                        lastErrorMessage = null
                    ),
                    lastSeenAtMillis = System.currentTimeMillis()
                )

                val staged = repository.stageProvisioningSnapshot(
                    DeviceSnapshotMerger.merge(
                        previous = previousSnapshot,
                        incoming = incomingSnapshot
                    )
                )

                val resolved = metadataResolver.resolveAndConnect(
                    repository = repository,
                    provisionalSnapshot = staged
                ).getOrThrow()

                require(resolved.product.family != DeviceFamily.UNKNOWN) {
                    "Runtime device identity did not include a supported product family."
                }

                resolved
            } catch (error: Throwable) {
                val rollbackError = withContext(NonCancellable) {
                    rollbackRegistration(
                        repository = repository,
                        pendingRegistration = pendingRegistration
                    )
                }

                if (rollbackError == null) {
                    pendingRegistry.remove(pendingRegistration)
                }

                rollbackError?.let(error::addSuppressed)
                throw error
            }
        }
    }

    suspend fun commitPreparedRegistration(
        snapshot: DeviceSnapshot
    ): Result<DeviceSnapshot> {
        return try {
            val ownerUid = UserDataScope.requireCurrentUid()
            val pendingRegistration = pendingRegistry.find(
                ownerUid = ownerUid,
                deviceUid = snapshot.deviceUid
            ) ?: error(
                "No verified provisioning transaction exists for this device."
            )
            val repository = DevicesRepositoryProvider.get(appContext)
            val committed = pendingRegistration.operationMutex.withLock {
                withContext(NonCancellable) {
                    val currentPending = pendingRegistry.find(
                        ownerUid = ownerUid,
                        deviceUid = snapshot.deviceUid
                    )
                    check(currentPending === pendingRegistration) {
                        "Provisioning transaction is no longer active."
                    }

                    val credentialStore = DeviceCredentialStore(
                        context = appContext,
                        ownerUid = pendingRegistration.ownerUid
                    )

                    try {
                        credentialStore.commitStagedToken(
                            pendingRegistration.deviceUid
                        )
                        pendingRegistration.stagedCredentialCommitted = true

                        repository.commitProvisioningSnapshot(snapshot).also {
                            check(pendingRegistry.remove(pendingRegistration)) {
                                "Provisioning transaction is no longer active."
                            }
                        }
                    } catch (error: Throwable) {
                        rollbackCredential(
                            credentialStore = credentialStore,
                            pendingRegistration = pendingRegistration
                        )?.let(error::addSuppressed)
                        throw error
                    }
                }
            }

            Result.success(committed)
        } catch (error: Throwable) {
            error.throwIfCancellation()
            Result.failure(error)
        }
    }

    suspend fun rollbackProvisioningRegistration(
        deviceUid: DeviceUid
    ): Result<Unit> {
        val ownerUid = UserDataScope.currentUid().trim()
        if (ownerUid.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "An authenticated owner is required to roll back provisioning."
                )
            )
        }

        return rollbackProvisioningRegistration(
            ownerUid = ownerUid,
            deviceUid = deviceUid
        ).map { Unit }
    }

    suspend fun rollbackPendingRegistrationsForOwner(
        ownerUid: String
    ): Result<Int> {
        val normalizedOwnerUid = ownerUid.trim()
        if (normalizedOwnerUid.isBlank()) {
            return Result.failure(
                IllegalArgumentException("ownerUid must not be blank")
            )
        }

        val deviceUids = pendingRegistry.deviceUidsForOwner(normalizedOwnerUid)

        var rolledBackCount = 0
        val failures = mutableListOf<Throwable>()

        deviceUids.forEach { deviceUid ->
            rollbackProvisioningRegistration(
                ownerUid = normalizedOwnerUid,
                deviceUid = deviceUid
            ).fold(
                onSuccess = { rolledBack ->
                    if (rolledBack) rolledBackCount += 1
                },
                onFailure = failures::add
            )
        }

        return if (failures.isEmpty()) {
            Result.success(rolledBackCount)
        } else {
            Result.failure(
                AqlProvisioningRollbackException(failures)
            )
        }
    }

    suspend fun rollbackProvisioningRegistrationForOwner(
        ownerUid: String,
        deviceUid: DeviceUid
    ): Result<Unit> {
        val normalizedOwnerUid = ownerUid.trim()
        if (normalizedOwnerUid.isBlank()) {
            return Result.failure(
                IllegalArgumentException("ownerUid must not be blank")
            )
        }

        return rollbackProvisioningRegistration(
            ownerUid = normalizedOwnerUid,
            deviceUid = deviceUid
        ).map { Unit }
    }

    private suspend fun rollbackProvisioningRegistration(
        ownerUid: String,
        deviceUid: DeviceUid
    ): Result<Boolean> {
        val pendingRegistration = pendingRegistry.find(
            ownerUid = ownerUid,
            deviceUid = deviceUid
        ) ?: return Result.success(false)

        return pendingRegistration.operationMutex.withLock {
            val currentPending = pendingRegistry.find(
                ownerUid = ownerUid,
                deviceUid = deviceUid
            )
            if (currentPending !== pendingRegistration) {
                return@withLock Result.success(false)
            }

            try {
                val repository = DevicesRepositoryProvider.currentRepository(
                    expectedOwnerUid = pendingRegistration.ownerUid
                )
                val rollbackError = withContext(NonCancellable) {
                    rollbackRegistration(
                        repository = repository,
                        pendingRegistration = pendingRegistration
                    )
                }

                if (rollbackError != null) {
                    Result.failure(rollbackError)
                } else {
                    check(pendingRegistry.remove(pendingRegistration)) {
                        "Provisioning transaction is no longer active."
                    }
                    Result.success(true)
                }
            } catch (error: Throwable) {
                error.throwIfCancellation()
                Result.failure(error)
            }
        }
    }

    private suspend fun rollbackRegistration(
        repository: DevicesRepository?,
        pendingRegistration: PendingRegistration
    ): Throwable? {
        return runCatching {
            when (pendingRegistration.mode) {
                AqlProvisioningRegistrationMode.NEW_DEVICE -> {
                    if (repository != null) {
                        repository.removeProvisioningRegistration(
                            pendingRegistration.deviceUid
                        )
                    } else {
                        DeviceCredentialStore(
                            context = appContext,
                            ownerUid = pendingRegistration.ownerUid
                        ).clearToken(pendingRegistration.deviceUid)
                    }
                    pendingRegistration.stagedCredentialCommitted = false
                }

                AqlProvisioningRegistrationMode.RECONFIGURE_EXISTING -> {
                    val previousSnapshot = checkNotNull(
                        pendingRegistration.previousSnapshot
                    ) {
                        "Existing-device recovery is missing its rollback snapshot."
                    }
                    val credentialStore = DeviceCredentialStore(
                        context = appContext,
                        ownerUid = pendingRegistration.ownerUid
                    )

                    rollbackCredential(
                        credentialStore = credentialStore,
                        pendingRegistration = pendingRegistration
                    )?.let { error -> throw error }

                    repository?.commitProvisioningSnapshot(previousSnapshot)
                }
            }
        }.exceptionOrNull()
    }

    private suspend fun rollbackCredential(
        credentialStore: DeviceCredentialStore,
        pendingRegistration: PendingRegistration
    ): Throwable? {
        return runCatching {
            if (pendingRegistration.stagedCredentialCommitted) {
                val previousToken = pendingRegistration.previousRuntimeToken
                if (previousToken == null) {
                    credentialStore.clearToken(pendingRegistration.deviceUid)
                } else {
                    credentialStore.saveToken(
                        deviceUid = pendingRegistration.deviceUid,
                        token = previousToken
                    )
                }
            } else {
                credentialStore.rollbackStagedToken(
                    pendingRegistration.deviceUid
                )
            }

            pendingRegistration.stagedCredentialCommitted = false
        }.exceptionOrNull()
    }

    private fun Throwable.throwIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }

    private fun resolvedTitle(
        draft: AqlProvisioningDraft
    ): String {
        return draft.deviceTitle
            .ifBlank { draft.deviceModel }
            .ifBlank { draft.candidateId }
    }

    private companion object {
        val pendingRegistry =
            AqlProvisioningTransactionRegistry<PendingRegistration>(
                ownerUidOf = PendingRegistration::ownerUid,
                deviceUidOf = PendingRegistration::deviceUid
            )
    }
}

class AqlProvisioningRollbackException(
    val failures: List<Throwable>
) : IllegalStateException(
    "${failures.size} provisioning transaction(s) could not be rolled back.",
    failures.firstOrNull()
)
