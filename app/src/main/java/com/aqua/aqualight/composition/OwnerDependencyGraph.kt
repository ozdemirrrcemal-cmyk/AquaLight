package com.aqua.aqualight.composition

import android.content.Context
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.application.auth.AuthenticatedOwnerIdentity
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftRequest
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftSession
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.application.user.UserDataArchiveOperations
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.OwnerSessionCoordinator
import com.aqua.aqualight.data.auth.OwnerSessionStateMachine
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.devices.DefaultDeviceFirmwareUpdateOperations
import com.aqua.aqualight.data.devices.provisioning.repository.DefaultProvisioningDraftOperations
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningQrSecretStore
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.data.user.archive.DefaultUserDataArchiveOperations
import com.aqua.aqualight.data.user.archive.UserDataArchiveDataSources
import com.aqua.aqualight.data.user.archive.UserDataArchiveSnapshotCollector
import com.aqua.aqualight.data.user.archive.UserDataBackupRestorer
import com.aqua.aqualight.platform.media.UserDataArchiveMediaGateway
import com.aqua.aqualight.platform.notifications.DeviceFirmwareUpdateNotificationOperations

/** Immutable dependency snapshot for one committed authenticated-owner session. */
internal data class OwnerDependencyGraph(
    val ownerUid: String,
    val sessionGeneration: Long,
    val devicesRepository: DevicesRepository,
    val firmwareUpdateOperations: DeviceFirmwareUpdateOperations,
    val deviceFirmwareNotifications: DeviceFirmwareUpdateNotificationOperations,
    val assignmentRepository: TankDeviceAssignmentRepository,
    val aquariumTankStore: AquariumTankDataStoreManager,
    val careTaskStore: CareTaskDataStoreManager,
    val userDataArchiveOperations: UserDataArchiveOperations,
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
    context: Context,
    private val deviceFirmwareNotifications: DeviceFirmwareUpdateNotificationOperations,
    private val notificationPreferenceUseCase: NotificationPreferenceUseCase,
    private val userPreferencesManager: UserPreferencesManager
) : OwnerDependencyGraphResolver {

    private val appContext = context.applicationContext
    private val sessionCoordinator = OwnerSessionCoordinator.create(appContext)

    @Volatile
    private var cachedGraph: OwnerDependencyGraph? = null

    override fun requireActive(): OwnerDependencyGraph {
        val dependencies = resolveActiveDependencies()
        cachedGraph?.takeIf { graph -> graph.matches(dependencies) }?.let { graph ->
            return graph
        }
        return synchronized(this) { requireSynchronizedGraph(dependencies) }
    }

    private fun resolveActiveDependencies(): ActiveOwnerDependencies {
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
        return ActiveOwnerDependencies(
            ownerUid = ownerUid,
            sessionGeneration = confirmedGeneration,
            devicesRepository = devicesRepository,
            assignmentRepository = assignmentRepository
        )
    }

    private fun requireSynchronizedGraph(
        dependencies: ActiveOwnerDependencies
    ): OwnerDependencyGraph {
        val synchronizedGeneration = requireActiveOwnerGeneration(
            ownerUid = dependencies.ownerUid,
            snapshot = sessionCoordinator.snapshot()
        )
        check(synchronizedGeneration == dependencies.sessionGeneration) {
            "Authenticated owner session changed while composing dependencies."
        }

        cachedGraph?.takeIf { graph -> graph.matches(dependencies) }?.let { graph ->
            return graph
        }
        validateRepositoryIdentities(dependencies)
        return composeGraph(dependencies).also { graph -> cachedGraph = graph }
    }

    private fun validateRepositoryIdentities(dependencies: ActiveOwnerDependencies) {
        check(DevicesRepositoryProvider.currentOwnerUid() == dependencies.ownerUid) {
            "Device repository owner changed while resolving dependencies."
        }
        check(TankDeviceAssignmentRepositoryProvider.currentOwnerUid() == dependencies.ownerUid) {
            "Assignment repository owner changed while resolving dependencies."
        }
        check(
            DevicesRepositoryProvider.currentRepository(dependencies.ownerUid) ===
                dependencies.devicesRepository
        ) {
            "Device repository identity changed while resolving dependencies."
        }
        check(
            TankDeviceAssignmentRepositoryProvider.currentRepository(dependencies.ownerUid) ===
                dependencies.assignmentRepository
        ) {
            "Assignment repository identity changed while resolving dependencies."
        }
    }

    private fun composeGraph(
        dependencies: ActiveOwnerDependencies
    ): OwnerDependencyGraph {
        val ownerUidProvider = { dependencies.ownerUid }
        val aquariumTankStore = AquariumTankDataStoreManager(appContext)
        val careTaskStore = CareTaskDataStoreManager.create(appContext)
        val archiveDataSources = UserDataArchiveDataSources(
            aquariumStore = aquariumTankStore,
            careTaskStore = careTaskStore,
            assignmentRepository = dependencies.assignmentRepository
        )
        val mediaGateway = UserDataArchiveMediaGateway(appContext)
        val snapshotCollector = UserDataArchiveSnapshotCollector(
            ownerUid = dependencies.ownerUid,
            dataSources = archiveDataSources,
            devicesRepository = dependencies.devicesRepository,
            preferences = userPreferencesManager,
            mediaGateway = mediaGateway
        )
        val restorer = UserDataBackupRestorer(
            ownerUid = dependencies.ownerUid,
            dataSources = archiveDataSources,
            mediaGateway = mediaGateway,
            reconcileCareReminders = notificationPreferenceUseCase::reconcileOwner
        )
        return OwnerDependencyGraph(
            ownerUid = dependencies.ownerUid,
            sessionGeneration = dependencies.sessionGeneration,
            devicesRepository = dependencies.devicesRepository,
            firmwareUpdateOperations = createFirmwareUpdateOperations(dependencies),
            deviceFirmwareNotifications = deviceFirmwareNotifications,
            assignmentRepository = dependencies.assignmentRepository,
            aquariumTankStore = aquariumTankStore,
            careTaskStore = careTaskStore,
            userDataArchiveOperations = DefaultUserDataArchiveOperations(
                sourceAppVersion = BuildConfig.VERSION_NAME,
                snapshotCollector = snapshotCollector,
                restorer = restorer
            ),
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
        )
    }

    private fun createFirmwareUpdateOperations(
        dependencies: ActiveOwnerDependencies
    ): DeviceFirmwareUpdateOperations {
        return DefaultDeviceFirmwareUpdateOperations(
            devicesRepository = dependencies.devicesRepository,
            statePublisher = { state, deviceName ->
                deviceFirmwareNotifications.publishOtaState(
                    ownerUid = dependencies.ownerUid,
                    state = state,
                    deviceName = deviceName
                )
            }
        ).also(dependencies.devicesRepository::registerOwnerScopedResource)
    }
}

private data class ActiveOwnerDependencies(
    val ownerUid: String,
    val sessionGeneration: Long,
    val devicesRepository: DevicesRepository,
    val assignmentRepository: TankDeviceAssignmentRepository
)

private fun OwnerDependencyGraph.matches(dependencies: ActiveOwnerDependencies): Boolean = listOf(
    ownerUid == dependencies.ownerUid,
    sessionGeneration == dependencies.sessionGeneration,
    devicesRepository === dependencies.devicesRepository,
    assignmentRepository === dependencies.assignmentRepository
).all { matches -> matches }

/** Resolves owner identity through the same committed-session barrier as owner services. */
internal class ResolvingAuthenticatedOwnerIdentity(
    private val ownerGraphResolver: OwnerDependencyGraphResolver
) : AuthenticatedOwnerIdentity {
    override fun requireOwnerUid(): String {
        return ownerGraphResolver.requireActive().ownerUid
    }
}

/** Process-owned facade that resolves the authenticated owner at operation time. */
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
