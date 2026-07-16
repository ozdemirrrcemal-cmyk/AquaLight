package com.aqua.aqualight.composition

import android.content.Context
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftRequest
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftSession
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.devices.provisioning.repository.DefaultProvisioningDraftOperations
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningQrSecretStore
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.user.UserDataScope

/**
 * Immutable dependency snapshot for one already-open authenticated owner.
 *
 * This graph never opens runtime repositories. OwnerSessionCoordinator remains
 * the only authority allowed to create/start them. The resolver only accepts
 * repositories already bound to the same owner and otherwise fails closed.
 */
internal data class OwnerDependencyGraph(
    val ownerUid: String,
    val devicesRepository: DevicesRepository,
    val assignmentRepository: TankDeviceAssignmentRepository,
    val aquariumTankStore: AquariumTankDataStoreManager,
    val careTaskStore: CareTaskDataStoreManager,
    val provisioningDraftOperations: ProvisioningDraftOperations
)

internal fun interface OwnerDependencyGraphResolver {
    fun requireActive(): OwnerDependencyGraph
}

internal class ActiveOwnerDependencyGraphResolver(
    context: Context
) : OwnerDependencyGraphResolver {

    private val appContext = context.applicationContext

    @Volatile
    private var cachedGraph: OwnerDependencyGraph? = null

    override fun requireActive(): OwnerDependencyGraph {
        val ownerUid = UserDataScope.requireCurrentUid()
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

        cachedGraph?.takeIf { graph ->
            graph.ownerUid == ownerUid &&
                graph.devicesRepository === devicesRepository &&
                graph.assignmentRepository === assignmentRepository
        }?.let { graph ->
            return graph
        }

        return synchronized(this) {
            val synchronizedGraph = cachedGraph
            if (
                synchronizedGraph?.ownerUid == ownerUid &&
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

                val ownerUidProvider = { ownerUid }
                OwnerDependencyGraph(
                    ownerUid = ownerUid,
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
