package com.aqua.aqualight.composition

import android.content.Context
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.application.auth.AuthenticatedOwnerIdentity
import com.aqua.aqualight.application.devices.DefaultDeviceAccessPolicy
import com.aqua.aqualight.application.devices.DeviceAccessPolicy
import com.aqua.aqualight.application.devices.DeviceCompatibilityOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceFeatureAccessOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationDraftOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticOperations
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightCardOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightFixtureCalibration
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightManagedAutoPlanOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupContextOperations
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftRequest
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftSession
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlOperations
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.application.user.UserDataArchiveOperations
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.OwnerSessionCoordinator
import com.aqua.aqualight.data.auth.OwnerSessionStateMachine
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.devices.DefaultDeviceFirmwareUpdateOperations
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.compatibility.DefaultDeviceCompatibilityOperations
import com.aqua.aqualight.data.devices.compatibility.DefaultDeviceFeatureAccessOperations
import com.aqua.aqualight.data.devices.cooling.DefaultDeviceCoolingCardOperations
import com.aqua.aqualight.data.devices.cooling.control.DefaultDeviceCoolingControlOperations
import com.aqua.aqualight.data.devices.dosing.DefaultDeviceDosingChannelNavigationOperations
import com.aqua.aqualight.data.devices.dosing.SharedPreferencesDeviceDosingCalibrationDraftStore
import com.aqua.aqualight.data.devices.dosing.SharedPreferencesDeviceDosingLowLevelAlertLedger
import com.aqua.aqualight.data.devices.dosing.v1.DeviceDosingV1ProductionRuntime
import com.aqua.aqualight.data.devices.light.automatic.DefaultDeviceLightAutomaticOperations
import com.aqua.aqualight.data.devices.light.adaptation.DefaultDeviceLightAdaptationOperations
import com.aqua.aqualight.data.devices.light.dashboard.DefaultDeviceLightCardOperations
import com.aqua.aqualight.data.devices.light.dashboard.DefaultDeviceLightControlOperations
import com.aqua.aqualight.data.devices.light.custom.DefaultDeviceLightCustomOperations
import com.aqua.aqualight.data.devices.light.library.DefaultDeviceLightLibraryOperations
import com.aqua.aqualight.data.devices.light.library.DeviceLightLibraryStore
import com.aqua.aqualight.data.devices.light.manual.DefaultDeviceLightManualOperations
import com.aqua.aqualight.data.devices.light.quicksetup.DefaultDeviceLightFixtureCalibration
import com.aqua.aqualight.data.devices.light.quicksetup.DefaultDeviceLightManagedAutoPlanOperations
import com.aqua.aqualight.data.devices.light.quicksetup.DefaultDeviceLightQuickSetupContextOperations
import com.aqua.aqualight.data.devices.light.system.DefaultDeviceLightSystemOperations
import com.aqua.aqualight.data.devices.menu.DefaultDeviceControlSurfacePreparationOperations
import com.aqua.aqualight.data.devices.menu.DeviceControlSurfaceDependencies
import com.aqua.aqualight.data.devices.provisioning.repository.DefaultProvisioningDraftOperations
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningQrSecretStore
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.devices.runtime.modules.firmware.SharedPreferencesDeviceOtaTransactionStore
import com.aqua.aqualight.data.devices.timer.control.DefaultDeviceTimerControlOperations
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.data.user.archive.DefaultUserDataArchiveOperations
import com.aqua.aqualight.data.user.archive.UserDataArchiveDataSources
import com.aqua.aqualight.data.user.archive.UserDataArchiveRuntimeDependencies
import com.aqua.aqualight.data.user.archive.UserDataArchiveSnapshotCollector
import com.aqua.aqualight.data.user.archive.UserDataArchiveStaging
import com.aqua.aqualight.data.user.archive.UserDataBackupRestorer
import com.aqua.aqualight.platform.documents.AndroidUserDataDocumentOperations
import com.aqua.aqualight.platform.media.UserDataArchiveMediaGateway
import com.aqua.aqualight.platform.notifications.DeviceFirmwareUpdateNotificationOperations
import com.aqua.aqualight.platform.text.AndroidDeviceDosingLowLevelAlertTextResolver

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
    val provisioningDraftOperations: ProvisioningDraftOperations,
    val compatibilityOperations: DeviceCompatibilityOperations,
    val accessPolicy: DeviceAccessPolicy,
    val featureAccessOperations: DeviceFeatureAccessOperations,
    val controlSurfacePreparationOperations: DeviceControlSurfacePreparationOperations,
    val lightOperations: OwnerLightOperations,
    val timerControlOperations: DeviceTimerControlOperations,
    val coolingCardOperations: DeviceCoolingCardOperations,
    val dosingOperations: OwnerDosingOperations
)

/** One owner-scoped application boundary set backed by one central Dosing state owner. */
internal data class OwnerDosingOperations(
    val channelOperations: DeviceDosingChannelOperations,
    val cardOperations: DeviceDosingCardOperations,
    val calibrationOperations: DeviceDosingCalibrationOperations,
    val calibrationDraftOperations: DeviceDosingCalibrationDraftOperations,
    val navigationOperations: DeviceDosingChannelNavigationOperations
)

/** One owner-scoped application boundary set backed by the single central Light runtime. */
internal data class OwnerLightOperations(
    val adaptationOperations: DeviceLightAdaptationOperations,
    val automaticOperations: DeviceLightAutomaticOperations,
    val cardOperations: DeviceLightCardOperations,
    val controlOperations: DeviceLightControlOperations,
    val customOperations: DeviceLightCustomOperations,
    val manualOperations: DeviceLightManualOperations,
    val systemOperations: DeviceLightSystemOperations,
    val libraryOperations: DeviceLightLibraryOperations,
    val quickSetupContextOperations: DeviceLightQuickSetupContextOperations,
    val managedAutoPlanOperations: DeviceLightManagedAutoPlanOperations,
    val quickSetupCalibration: DeviceLightFixtureCalibration
)

private data class OwnerDeviceControlOperations(
    val dosing: OwnerDosingOperations,
    val timer: DeviceTimerControlOperations,
    val light: OwnerLightOperations
)

private data class DeviceAccessComposition(
    val compatibility: DeviceCompatibilityOperations,
    val policy: DeviceAccessPolicy,
    val featureAccess: DeviceFeatureAccessOperations
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
    private val notificationDispatchUseCase: NotificationDispatchUseCase,
    private val userPreferencesManager: UserPreferencesManager
) : OwnerDependencyGraphResolver {

    private val appContext = context.applicationContext
    private val sessionCoordinator = OwnerSessionCoordinator.create(appContext)
    private val graphComposer = OwnerDependencyGraphComposer(
        appContext = appContext,
        deviceFirmwareNotifications = deviceFirmwareNotifications,
        notificationPreferenceUseCase = notificationPreferenceUseCase,
        notificationDispatchUseCase = notificationDispatchUseCase,
        userPreferencesManager = userPreferencesManager
    )

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
        return graphComposer.compose(dependencies).also { graph -> cachedGraph = graph }
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

}


private class OwnerDependencyGraphComposer(
    private val appContext: Context,
    private val deviceFirmwareNotifications: DeviceFirmwareUpdateNotificationOperations,
    private val notificationPreferenceUseCase: NotificationPreferenceUseCase,
    private val notificationDispatchUseCase: NotificationDispatchUseCase,
    private val userPreferencesManager: UserPreferencesManager
) {

    fun compose(
        dependencies: ActiveOwnerDependencies
    ): OwnerDependencyGraph {
        val aquariumTankStore = AquariumTankDataStoreManager(appContext)
        val careTaskStore = CareTaskDataStoreManager.create(appContext)
        val controls = createOwnerDeviceControlOperations(dependencies, aquariumTankStore)
        val firmwareUpdateOperations = createFirmwareUpdateOperations(dependencies)
        val access = createDeviceAccessComposition(
            dependencies = dependencies,
            firmwareUpdateOperations = firmwareUpdateOperations
        )
        return OwnerDependencyGraph(
            ownerUid = dependencies.ownerUid,
            sessionGeneration = dependencies.sessionGeneration,
            devicesRepository = dependencies.devicesRepository,
            firmwareUpdateOperations = firmwareUpdateOperations,
            deviceFirmwareNotifications = deviceFirmwareNotifications,
            assignmentRepository = dependencies.assignmentRepository,
            aquariumTankStore = aquariumTankStore,
            careTaskStore = careTaskStore,
            userDataArchiveOperations = createUserDataArchiveOperations(
                dependencies = dependencies,
                aquariumTankStore = aquariumTankStore,
                careTaskStore = careTaskStore
            ),
            provisioningDraftOperations = createProvisioningDraftOperations(dependencies.ownerUid),
            compatibilityOperations = access.compatibility,
            accessPolicy = access.policy,
            featureAccessOperations = access.featureAccess,
            controlSurfacePreparationOperations = createControlSurfacePreparationOperations(
                dependencies = dependencies,
                controls = controls,
                access = access
            ),
            lightOperations = controls.light,
            timerControlOperations = controls.timer,
            coolingCardOperations = createCoolingCardOperations(dependencies),
            dosingOperations = controls.dosing
        )
    }

    private fun createOwnerDeviceControlOperations(
        dependencies: ActiveOwnerDependencies,
        aquariumTankStore: AquariumTankDataStoreManager
    ): OwnerDeviceControlOperations = OwnerDeviceControlOperations(
        dosing = createDosingOperations(dependencies),
        timer = DefaultDeviceTimerControlOperations(dependencies.devicesRepository),
        light = createOwnerLightOperations(
            context = appContext,
            ownerUid = dependencies.ownerUid,
            devicesRepository = dependencies.devicesRepository,
            assignmentRepository = dependencies.assignmentRepository,
            aquariumTankStore = aquariumTankStore
        )
    )

    private fun createDeviceAccessComposition(
        dependencies: ActiveOwnerDependencies,
        firmwareUpdateOperations: DeviceFirmwareUpdateOperations
    ): DeviceAccessComposition {
        val compatibility = DefaultDeviceCompatibilityOperations(
            devicesRepository = dependencies.devicesRepository,
            updatePlanProvider = { deviceUid ->
                (firmwareUpdateOperations.observe(deviceUid).value as? DeviceOtaState.UpdateAvailable)
                    ?.plan
            }
        )
        val policy: DeviceAccessPolicy = DefaultDeviceAccessPolicy
        return DeviceAccessComposition(
            compatibility = compatibility,
            policy = policy,
            featureAccess = DefaultDeviceFeatureAccessOperations(
                compatibilityOperations = compatibility,
                accessPolicy = policy,
                firmwareUpdateOperations = firmwareUpdateOperations
            )
        )
    }

    private fun createProvisioningDraftOperations(
        ownerUid: String
    ): ProvisioningDraftOperations {
        val ownerUidProvider = { ownerUid }
        return DefaultProvisioningDraftOperations(
            draftStore = AqlProvisioningDraftStore(
                context = appContext,
                ownerUidProvider = ownerUidProvider
            ),
            qrSecretStore = AqlProvisioningQrSecretStore(
                context = appContext,
                ownerUidProvider = ownerUidProvider
            )
        )
    }

    private fun createUserDataArchiveOperations(
        dependencies: ActiveOwnerDependencies,
        aquariumTankStore: AquariumTankDataStoreManager,
        careTaskStore: CareTaskDataStoreManager
    ): UserDataArchiveOperations {
        val archiveDataSources = UserDataArchiveDataSources(
            aquariumStore = aquariumTankStore,
            careTaskStore = careTaskStore,
            assignmentRepository = dependencies.assignmentRepository
        )
        val mediaGateway = UserDataArchiveMediaGateway(appContext)
        return DefaultUserDataArchiveOperations(
            sourceAppVersion = BuildConfig.VERSION_NAME,
            snapshotCollector = UserDataArchiveSnapshotCollector(
                ownerUid = dependencies.ownerUid,
                dataSources = archiveDataSources,
                preferences = userPreferencesManager,
                mediaGateway = mediaGateway
            ),
            restorer = UserDataBackupRestorer(
                context = appContext,
                ownerUid = dependencies.ownerUid,
                dataSources = archiveDataSources,
                mediaGateway = mediaGateway,
                reconcileCareReminders = notificationPreferenceUseCase::reconcileOwner
            ),
            runtime = UserDataArchiveRuntimeDependencies(
                staging = UserDataArchiveStaging(appContext),
                documents = AndroidUserDataDocumentOperations(appContext)
            )
        )
    }

    private fun createCoolingCardOperations(
        dependencies: ActiveOwnerDependencies
    ): DeviceCoolingCardOperations = DefaultDeviceCoolingCardOperations(
        dependencies.devicesRepository
    )

    private fun createControlSurfacePreparationOperations(
        dependencies: ActiveOwnerDependencies,
        controls: OwnerDeviceControlOperations,
        access: DeviceAccessComposition
    ): DeviceControlSurfacePreparationOperations =
        DefaultDeviceControlSurfacePreparationOperations(
            dependencies = DeviceControlSurfaceDependencies(
                rootOperations = DefaultDeviceRootOperations(dependencies.devicesRepository),
                dosingChannelOperations = controls.dosing.channelOperations,
                coolingControlOperations = DefaultDeviceCoolingControlOperations(
                    dependencies.devicesRepository
                ),
                timerControlOperations = controls.timer,
                lightControlOperations = controls.light.controlOperations
            ),
            compatibilityOperations = access.compatibility,
            accessPolicy = access.policy
        )

    private fun createDosingOperations(
        dependencies: ActiveOwnerDependencies
    ): OwnerDosingOperations {
        val runtime = DeviceDosingV1ProductionRuntime(
            devicesRepository = dependencies.devicesRepository,
            ownerUid = dependencies.ownerUid,
            lowLevelAlertLedger = SharedPreferencesDeviceDosingLowLevelAlertLedger.create(
                context = appContext,
                ownerUid = dependencies.ownerUid
            ),
            notificationDispatch = notificationDispatchUseCase,
            alertTextResolver = AndroidDeviceDosingLowLevelAlertTextResolver(appContext)
        ).also(dependencies.devicesRepository::registerOwnerScopedResource)
        val channelOperations = runtime.channelOperations
        return OwnerDosingOperations(
            channelOperations = channelOperations,
            cardOperations = runtime.cardOperations,
            calibrationOperations = runtime.calibrationOperations,
            calibrationDraftOperations = SharedPreferencesDeviceDosingCalibrationDraftStore.create(
                context = appContext,
                ownerUid = dependencies.ownerUid
            ),
            navigationOperations = DefaultDeviceDosingChannelNavigationOperations(
                rootOperations = DefaultDeviceRootOperations(dependencies.devicesRepository),
                channelOperations = channelOperations
            )
        )
    }

    private fun createFirmwareUpdateOperations(
        dependencies: ActiveOwnerDependencies
    ): DeviceFirmwareUpdateOperations {
        return DefaultDeviceFirmwareUpdateOperations(
            devicesRepository = dependencies.devicesRepository,
            transactionStore = SharedPreferencesDeviceOtaTransactionStore.create(
                context = appContext,
                ownerUid = dependencies.ownerUid
            ),
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

private fun createOwnerLightOperations(
    context: Context,
    ownerUid: String,
    devicesRepository: DevicesRepository,
    assignmentRepository: TankDeviceAssignmentRepository,
    aquariumTankStore: AquariumTankDataStoreManager
): OwnerLightOperations {
    val controlOperations = DefaultDeviceLightControlOperations(devicesRepository)
    return OwnerLightOperations(
        adaptationOperations = DefaultDeviceLightAdaptationOperations(devicesRepository),
        automaticOperations = DefaultDeviceLightAutomaticOperations(devicesRepository),
        cardOperations = DefaultDeviceLightCardOperations(
            devicesRepository = devicesRepository,
            controlOperations = controlOperations
        ),
        controlOperations = controlOperations,
        customOperations = DefaultDeviceLightCustomOperations(devicesRepository),
        manualOperations = DefaultDeviceLightManualOperations(devicesRepository),
        systemOperations = DefaultDeviceLightSystemOperations(devicesRepository),
        libraryOperations = DefaultDeviceLightLibraryOperations(
            ownerUid = ownerUid,
            store = DeviceLightLibraryStore.create(context, ownerUid),
            devicesRepository = devicesRepository,
            controlOperations = controlOperations
        ),
        quickSetupContextOperations = DefaultDeviceLightQuickSetupContextOperations(
            ownerUid = ownerUid,
            tankStore = aquariumTankStore,
            assignmentRepository = assignmentRepository,
            devicesRepository = devicesRepository
        ),
        managedAutoPlanOperations = DefaultDeviceLightManagedAutoPlanOperations(devicesRepository),
        quickSetupCalibration = DefaultDeviceLightFixtureCalibration()
    )
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
