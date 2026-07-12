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
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AqlProvisioningHandoffSaver(
    context: Context
) {

    private data class PendingRegistration(
        val ownerUid: String,
        val mode: AqlProvisioningRegistrationMode,
        val deviceUid: DeviceUid,
        val previousSnapshot: DeviceSnapshot?,
        val previousRuntimeToken: String?
    )

    private val appContext = context.applicationContext
    private val metadataResolver = AqlProvisioningRuntimeMetadataResolver()
    private val transactionMutex = Mutex()
    private val pendingRegistrations = mutableMapOf<String, PendingRegistration>()

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
        val previousRuntimeToken = credentialStore.getToken(handoff.deviceUid)
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
        val transactionKey = transactionKey(handoff.deviceUid)

        transactionMutex.withLock {
            check(pendingRegistrations[transactionKey] == null) {
                "A provisioning transaction is already active for this device."
            }
            pendingRegistrations[transactionKey] = pendingRegistration
        }

        try {
            repository.saveRuntimeToken(
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

            return repository.stageProvisioningSnapshot(
                DeviceSnapshotMerger.merge(
                    previous = previousSnapshot,
                    incoming = resolved
                )
            )
        } catch (error: Throwable) {
            val rollbackError = withContext(NonCancellable) {
                rollbackRegistration(
                    repository = repository,
                    pendingRegistration = pendingRegistration
                )
            }

            transactionMutex.withLock {
                pendingRegistrations.remove(transactionKey)
            }

            rollbackError?.let(error::addSuppressed)
            throw error
        }
    }

    suspend fun commitPreparedRegistration(
        snapshot: DeviceSnapshot
    ): Result<DeviceSnapshot> {
        return try {
            val repository = DevicesRepositoryProvider.get(appContext)
            val committed = repository.commitProvisioningSnapshot(snapshot)

            transactionMutex.withLock {
                pendingRegistrations.remove(transactionKey(snapshot.deviceUid))
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
        val transactionKey = transactionKey(deviceUid)
        val pendingRegistration = transactionMutex.withLock {
            pendingRegistrations.remove(transactionKey)
        } ?: return Result.failure(
            IllegalStateException(
                "No pending provisioning transaction exists for this device."
            )
        )

        return try {
            val repository = DevicesRepositoryProvider
                .takeIf {
                    UserDataScope.currentUid() == pendingRegistration.ownerUid
                }
                ?.get(appContext)

            val rollbackError = withContext(NonCancellable) {
                rollbackRegistration(
                    repository = repository,
                    pendingRegistration = pendingRegistration
                )
            }

            if (rollbackError != null) {
                transactionMutex.withLock {
                    pendingRegistrations[transactionKey] = pendingRegistration
                }
                Result.failure(rollbackError)
            } else {
                Result.success(Unit)
            }
        } catch (error: Throwable) {
            transactionMutex.withLock {
                pendingRegistrations[transactionKey] = pendingRegistration
            }
            error.throwIfCancellation()
            Result.failure(error)
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

                    if (pendingRegistration.previousRuntimeToken == null) {
                        credentialStore.clearToken(pendingRegistration.deviceUid)
                    } else {
                        credentialStore.saveToken(
                            deviceUid = pendingRegistration.deviceUid,
                            token = pendingRegistration.previousRuntimeToken
                        )
                    }

                    repository?.stageProvisioningSnapshot(previousSnapshot)
                }
            }
        }.exceptionOrNull()
    }

    private fun transactionKey(
        deviceUid: DeviceUid
    ): String {
        return deviceUid.value
            .trim()
            .uppercase(Locale.US)
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
}
