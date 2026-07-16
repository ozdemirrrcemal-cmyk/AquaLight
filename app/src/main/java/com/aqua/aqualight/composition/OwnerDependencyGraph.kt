package com.aqua.aqualight.composition

import android.content.Context
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftRequest
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftSession
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.OwnerSessionCoordinator
import com.aqua.aqualight.data.auth.OwnerSessionStateMachine
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.devices.provisioning.repository.DefaultProvisioningDraftOperations
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningQrSecretStore
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.user.UserDataScope

/**
 * Immutable dependency snapshot for one committed authenticated-owner session.
 *
 * This graph never opens runtime repositories. OwnerSessionCoordinator remains
 * the only authority allowed to create/start them. The resolver accepts only a
 * committed session whose two repository identities are already bound to the
 * same owner; pending transitions fail closed before any ViewModel is built.
 */
internal data class OwnerDependencyGraph(
    val ownerUid: String,
    val sessionGeneration: Long,
    val devicesRepository: DevicesRepository,
    val assignmentRepository: TankDeviceAssignmentRepository,
    val aquariumTankStore: AquariumTankDataStoreManager,
    val careTaskStore: CareTaskDataStoreManager,
    val provisioningDraftOperations: ProvisioningDraftOperations
)

internal fun interface OwnerDependencyGraphResolver {
    fun requireActive(): OwnerDependencyGraph
}

internal fun requireActiveOwnerGeneration(
    ownerUid: String,
    snapshot: OwnerSessionStateMachine.Snapshot
): Long {
    check(
        snapshot.activeOwnerUid == ownerUid &&
            snapshot.pendingOwnerUid == null
    ) {
        "Authenticated owner session is not committed."
    }
    return snapshot.generation
}

internal class ActiveOwnerDependencyGraphResolver(
    context: Context
) : OwnerDependencyGraphResolver {

    private val appContext = context.applicationContext
    private val sessionCoordinator = OwnerSessionCoordinator.create(appContext)

    @Volatile
    private var cachedGraph: OwnerDependencyGraph? = null

    override fun requireActive(): OwnerDependencyGraph {
        val ownerUid = UserDataScope.requireCurrentUid()
        val initialGeneration = requireActiveOwnerGeneration(
            ownerUid = ownerUid,
            snapshot = sessionCoordinator.snapshot()
        )
        val devicesRepository = requireNotNull(
            DevicesRepositoryProvider.currentRepository(ownerUid)
        ) {
            "Authenticated owner device runtime is not active."
        }
        val assignmentRepository = requireNotNull(
            TankDeviceAssignmentRepositoryProvider.currentRepository(ownerUid)
        ) {
            "Authenticated owner assignment repository is not active."
        }
        val confirmedGeneration = requireActiveOwnerGeneration(
            ownerUid = ownerUid,
            snapshot = sessionCoordinator.snapshot()
        )
        check(confirmedGeneration == initialGeneration) {
            "Authenticated owner session changed while resolving dependencies."
        }

        cachedGraph?.takeIf { graph ->
            graph.ownerUid == ownerUid &&
                graph.sessionGeneration == confirmedGeneration &&
                graph.devicesRepository === devicesRepository &&
                graph.assignmentRepository === assignmentRepository
        }?.let { graph ->
            return graph
        }

        return synchronized(this) {
            val synchronizedGeneration = requireActiveOwnerGeneration(
                ownerUid = ownerUid,
                snapshot = sessionCoordinator.snapshot()
            )
            check(synchronizedGeneration == confirmedGeneration) {
                "Authenticated owner session changed while composing dependencies."
            }

            val synchronizedGraph = cachedGraph
            if (
                synchronizedGraph?.ownerUid == ownerUid &&
                synchronizedGraph.sessionGeneration == synchronizedGeneration &&
                synchronizedGraph.devicesRepository === devicesRepository &&
                synchronizedGraph.assignmentRepository === assignmentRepository
            ) {
                synchronizedGraph
            } else {
                check(DevicesRepositoryProvider.currentOwnerUid() == ownerUid) {
                    "Device repository owner changed while resolving dependencies."
                }
                check(TankDeviceAssignmentRepositoryProvider.currentOwnerUid() == ownerUid) {
                    "Assignment repository owner changed while resolving dependencies."
                }
                check(DevicesRepositoryProvider.currentRepository(ownerUid) === devicesRepository) {
                    "Device repository identity changed while resolving dependencies."
                }
                check(
                    TankDeviceAssignmentRepositoryProvider.currentRepository(ownerUid) ===
                        assignmentRepository
                ) {
                    "Assignment repository identity changed while resolving dependencies."
                }

                val ownerUidProvider = { ownerUid }
                OwnerDependencyGraph(
                    ownerUid = ownerUid,
                    sessionGeneration = synchronizedGeneration,
                    devicesRepository = devicesRepository,
                    assignmentRepository = assignmentRepository,
                    aquariumTankStore = AquariumTankDataStoreManager(appContext),
                    careTaskStore = CareTaskDataStoreManager.create(appContext),
                    provisioningDraftOperations = DefaultProvisioningDraftOperations(
                        draftStore = AqlProvisioningDraftStore(
                            context = appContext,
                            ownerUidProvider = ownerUidProvider
                        ),
                        qrSecretStore = AqlProvisioningQrSecretStore(
                            context = appContext,
                            ownerUidProvider = ownerUidProvider
                        )
                    )
                ).also { graph ->
                    cachedGraph = graph
                }
            }
        }
    }
}

/**
 * Process-owned facade that resolves the authenticated owner at operation time.
 * It never retains a dependency from a previous account.
 */
internal class ResolvingProvisioningDraftOperations(
    private val ownerGraphResolver: OwnerDependencyGraphResolver
) : ProvisioningDraftOperations {

    override fun createDraft(
        request: ProvisioningDraftRequest
    ): Result<ProvisioningDraftSession> {
        return runCatching {
            ownerGraphResolver
                .requireActive()
                .provisioningDraftOperations
                .createDraft(request)
                .getOrThrow()
        }
    }
}
