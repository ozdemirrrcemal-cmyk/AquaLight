package com.aqua.aqualight.debug.devices

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.application.devices.TankDeviceAssignmentOperations
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationOperations
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticOperations
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanOperations
import com.aqua.aqualight.application.devices.light.control.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankOperations
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemOperations
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlOperations
import com.aqua.aqualight.composition.AppContainer
import com.aqua.aqualight.composition.OwnerDependencyGraph
import com.aqua.aqualight.composition.OwnerDependencyGraphAccess
import com.aqua.aqualight.data.aquarium.devices.DefaultTankDeviceAssignmentOperations
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.DefaultOwnerDevicesOperations
import com.aqua.aqualight.data.devices.light.automatic.DefaultDeviceLightAutomaticOperations
import com.aqua.aqualight.data.devices.light.library.DefaultDeviceLightLibraryOperations
import com.aqua.aqualight.data.devices.light.library.DeviceLightLibraryStore
import com.aqua.aqualight.data.devices.menu.DefaultDeviceMenuAccessOperations
import com.aqua.aqualight.data.devices.remove.OwnerDeviceDataCleaner
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select.TankDeviceSelectViewModel
import com.aqua.aqualight.ui.tabs.devices.DevicesViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootOverviewViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramsViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramEditorViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation.DeviceLightAdaptationViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library.DeviceLightLibraryViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom.DeviceLightCustomCurveViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual.DeviceLightManualControlViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup.DeviceLightQuickSetupViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root.DeviceLightRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system.DeviceLightSystemViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.settings.DeviceFamilySettingsViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.root.DeviceTimerRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.channel.DeviceTimerChannelViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.program.DeviceTimerProgramViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateViewModel
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver

/** Debug-only AppContainer decorator for catalog-backed Light and Timer fixtures. */
internal class DebugDeviceFixtureAppContainer(
    context: Context,
    private val delegate: AppContainer
) : AppContainer by delegate {
    private val ownerGraphAccess = requireNotNull(delegate as? OwnerDependencyGraphAccess) {
        "Debug fixture composition requires the production owner graph."
    }

    override val defaultViewModelFactory: ViewModelProvider.Factory =
        DebugDeviceFixtureViewModelFactory(
            context = context.applicationContext,
            delegate = delegate.defaultViewModelFactory,
            ownerGraphAccess = ownerGraphAccess
        )
}

private class DebugDeviceFixtureViewModelFactory(
    context: Context,
    private val delegate: ViewModelProvider.Factory,
    private val ownerGraphAccess: OwnerDependencyGraphAccess
) : ViewModelProvider.Factory {

    private val appContext = context.applicationContext
    private val fixtures = DebugDeviceFixtureCatalog()
    private var cachedTimerDependencies: DebugTimerFixtureDependencies? = null

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val graph = requireGraph()
        val dependencies = timerDependencies(graph)
        val fixtureViewModel = lightFeatureViewModelOrNull(
            modelClass = modelClass,
            automaticOperations = { dependencies.lightAutomaticOperations },
            adaptationOperations = { dependencies.lightAdaptationOperations },
            systemOperations = { dependencies.lightSystemOperations }
        ) ?: directFixtureViewModelOrNull(
            modelClass = modelClass,
            dependencies = dependencies,
            rootOperations = rootOperations(graph)
        ) ?: createCompositeViewModelOrNull(
            modelClass = modelClass,
            graph = graph,
            dependencies = dependencies
        )
        val viewModel: ViewModel = when {
            fixtureViewModel != null -> fixtureViewModel
            else -> return delegate.create(modelClass)
        }

        return modelClass.cast(viewModel)
    }

    private fun requireGraph(): OwnerDependencyGraph = ownerGraphAccess.requireActiveOwnerGraph()

    private fun createDevicesViewModel(graph: OwnerDependencyGraph): DevicesViewModel {
        val fixtureDependencies = timerDependencies(graph)
        val repository = graph.devicesRepository
        val realOperations = DefaultOwnerDevicesOperations(
            devicesRepository = repository,
            assignmentRepository = graph.assignmentRepository,
            deviceDataCleaner = OwnerDeviceDataCleaner.create(
                devicesRepository = repository,
                assignmentRepository = graph.assignmentRepository
            ),
            cleanupDeletedDeviceNotifications = { deviceUids ->
                graph.deviceFirmwareNotifications.clearDeletedDevices(
                    ownerUid = graph.ownerUid,
                    deviceUids = deviceUids
                )
            }
        )
        return DevicesViewModel(
            operations = DebugFixtureOwnerDevicesOperations(realOperations, fixtures),
            menuOpenUseCase = DeviceMenuOpenUseCase(
                menuAccessOperations = DebugFixtureMenuAccessOperations(
                    delegate = DefaultDeviceMenuAccessOperations.create(repository),
                    fixtures = fixtures
                ),
                controlSurfacePreparationOperations =
                    fixtureDependencies.controlSurfacePreparationOperations
            ),
            routeResolver = DeviceRouteResolver()
        )
    }

    private fun createCompositeViewModelOrNull(
        modelClass: Class<*>,
        graph: OwnerDependencyGraph,
        dependencies: DebugTimerFixtureDependencies
    ): ViewModel? = when (modelClass) {
        DevicesViewModel::class.java -> createDevicesViewModel(graph)
        DeviceLightRootViewModel::class.java -> createLightRootViewModel(graph)
        DeviceTimerRootViewModel::class.java -> createTimerRootViewModel(graph)
        DeviceFamilySettingsViewModel::class.java -> DeviceFamilySettingsViewModel(
            settingsOperations = DebugFixtureFamilySettingsOperations(
                repository = graph.devicesRepository,
                fixtures = fixtures
            ),
            firmwareUpdateOperations = fixtureFirmwareOperations(graph, fixtures),
            manifestUrl = BuildConfig.AQL_OTA_MANIFEST_URL
        )
        DeviceFirmwareUpdateViewModel::class.java -> DeviceFirmwareUpdateViewModel(
            rootOperations = rootOperations(graph),
            firmwareUpdateOperations = fixtureFirmwareOperations(graph, fixtures),
            manifestUrl = BuildConfig.AQL_OTA_MANIFEST_URL
        )
        TankDetailDevicesViewModel::class.java ->
            createTankDetailDevicesViewModel(graph, dependencies)
        else -> null
    }

    private fun createTimerRootViewModel(graph: OwnerDependencyGraph): DeviceTimerRootViewModel =
        timerDependencies(graph).let { dependencies ->
            DeviceTimerRootViewModel(
                operations = rootOperations(graph),
                timerControlOperations = dependencies.timerControlOperations,
                controlSurfacePreparationOperations =
                    dependencies.controlSurfacePreparationOperations
            )
        }

    private fun createLightRootViewModel(graph: OwnerDependencyGraph): DeviceLightRootViewModel =
        timerDependencies(graph).let { dependencies ->
            DeviceLightRootViewModel(
                rootOperations = rootOperations(graph),
                lightControlOperations = dependencies.lightControlOperations,
                controlSurfacePreparationOperations =
                    dependencies.controlSurfacePreparationOperations
            )
        }

    private fun createTankDetailDevicesViewModel(
        graph: OwnerDependencyGraph,
        dependencies: DebugTimerFixtureDependencies
    ): TankDetailDevicesViewModel =
        TankDetailDevicesViewModel(
            assignmentOperations = dependencies.tankAssignmentOperations,
            menuOpenUseCase = DeviceMenuOpenUseCase(
                menuAccessOperations = DebugFixtureMenuAccessOperations(
                    delegate = DefaultDeviceMenuAccessOperations.create(graph.devicesRepository),
                    fixtures = fixtures
                ),
                controlSurfacePreparationOperations =
                    dependencies.controlSurfacePreparationOperations
            ),
            routeResolver = DeviceRouteResolver(),
            dosingCardOperations = graph.dosingOperations.cardOperations,
            coolingCardOperations = graph.coolingCardOperations
        )

    private fun timerDependencies(graph: OwnerDependencyGraph): DebugTimerFixtureDependencies =
        synchronized(this) {
            cachedTimerDependencies
                ?.takeIf { dependencies -> dependencies.graph === graph }
                ?: createTimerDependencies(graph).also { dependencies ->
                    cachedTimerDependencies = dependencies
                }
        }

    private fun createTimerDependencies(
        graph: OwnerDependencyGraph
    ): DebugTimerFixtureDependencies {
        val runtime = DebugTimerFixtureRuntime(fixtures)
        val timerControlOperations = DebugFixtureTimerControlOperations(
            delegate = graph.timerControlOperations,
            runtime = runtime
        )
        val light = createLightFixtureDependencies(appContext, graph, fixtures)
        val tank = createTankFixtureDependencies(graph, fixtures)
        return DebugTimerFixtureDependencies(
            graph = graph,
            lightAdaptationOperations = light.adaptationOperations,
            lightAutomaticOperations = light.automaticOperations,
            lightManagedPlanOperations = light.managedPlanOperations,
            lightControlOperations = light.controlOperations,
            lightCustomOperations = light.customOperations,
            lightLibraryOperations = light.libraryOperations,
            lightSystemOperations = light.systemOperations,
            quickSetupTankOperations = tank.quickSetupOperations,
            tankAssignmentOperations = tank.assignmentOperations,
            timerControlOperations = timerControlOperations,
            controlSurfacePreparationOperations =
                DebugFixtureControlSurfacePreparationOperations(
                    delegate = graph.controlSurfacePreparationOperations,
                    fixtures = fixtures,
                    timerControlOperations = timerControlOperations,
                    lightControlOperations = light.controlOperations
                )
        )
    }

    private fun rootOperations(graph: OwnerDependencyGraph) = DebugFixtureDeviceRootOperations(
        delegate = DefaultDeviceRootOperations(graph.devicesRepository),
        fixtures = fixtures
    )

}

private fun fixtureFirmwareOperations(
    graph: OwnerDependencyGraph,
    fixtures: DebugDeviceFixtureCatalog
) = DebugFixtureFirmwareUpdateOperations(
    delegate = graph.firmwareUpdateOperations,
    fixtures = fixtures
)

private fun lightFeatureViewModelOrNull(
    modelClass: Class<*>,
    automaticOperations: () -> DeviceLightAutomaticOperations,
    adaptationOperations: () -> DeviceLightAdaptationOperations,
    systemOperations: () -> DeviceLightSystemOperations
): ViewModel? = when (modelClass) {
    DeviceLightAutomaticProgramsViewModel::class.java ->
        DeviceLightAutomaticProgramsViewModel(automaticOperations())
    DeviceLightAutomaticProgramEditorViewModel::class.java ->
        DeviceLightAutomaticProgramEditorViewModel(automaticOperations())
    DeviceLightAdaptationViewModel::class.java ->
        DeviceLightAdaptationViewModel(adaptationOperations())
    DeviceLightSystemViewModel::class.java ->
        DeviceLightSystemViewModel(systemOperations())
    else -> null
}

private fun directFixtureViewModelOrNull(
    modelClass: Class<*>,
    dependencies: DebugTimerFixtureDependencies,
    rootOperations: DebugFixtureDeviceRootOperations
): ViewModel? = when (modelClass) {
    DeviceLightManualControlViewModel::class.java ->
        DeviceLightManualControlViewModel(dependencies.lightLibraryOperations)
    DeviceLightCustomCurveViewModel::class.java -> DeviceLightCustomCurveViewModel(
        customOperations = dependencies.lightCustomOperations,
        libraryOperations = dependencies.lightLibraryOperations
    )
    DeviceLightLibraryViewModel::class.java ->
        DeviceLightLibraryViewModel(dependencies.lightLibraryOperations)
    DeviceLightQuickSetupViewModel::class.java -> DeviceLightQuickSetupViewModel(
        tankOperations = dependencies.quickSetupTankOperations,
        managedPlanOperations = dependencies.lightManagedPlanOperations
    )
    DeviceTimerProgramViewModel::class.java ->
        DeviceTimerProgramViewModel(dependencies.timerControlOperations)
    DeviceTimerChannelViewModel::class.java ->
        DeviceTimerChannelViewModel(dependencies.timerControlOperations)
    DeviceRootOverviewViewModel::class.java -> DeviceRootOverviewViewModel(rootOperations)
    TankDeviceSelectViewModel::class.java ->
        TankDeviceSelectViewModel(dependencies.tankAssignmentOperations)
    else -> null
}

private fun createLightFixtureDependencies(
    appContext: Context,
    graph: OwnerDependencyGraph,
    fixtures: DebugDeviceFixtureCatalog
): DebugLightFixtureDependencies {
    val adaptationOperations = DebugFixtureLightAdaptationOperations(
        delegate = graph.lightOperations.adaptationOperations,
        fixtures = fixtures
    )
    val controlOperations = DebugFixtureLightControlOperations(
        delegate = graph.lightOperations.controlOperations,
        fixtures = fixtures,
        adaptationOperations = adaptationOperations
    )
    val automaticOperations = DebugFixtureLightAutomaticOperations(
        delegate = DefaultDeviceLightAutomaticOperations(graph.devicesRepository),
        fixtures = fixtures
    )
    val managedPlanOperations = DebugFixtureLightManagedPlanOperations(
        delegate = graph.lightOperations.managedPlanOperations,
        fixtures = fixtures
    )
    val runtime = DebugLightFixtureRuntime(fixtures)
    val customOperations = DebugFixtureLightCustomOperations(
        delegate = graph.lightOperations.customOperations,
        runtime = runtime
    )
    val libraryOperations = DebugFixtureLightLibraryOperations(
        delegate = DefaultDeviceLightLibraryOperations(
            ownerUid = graph.ownerUid,
            store = DeviceLightLibraryStore.create(appContext, graph.ownerUid),
            devicesRepository = graph.devicesRepository,
            controlOperations = controlOperations
        ),
        runtime = runtime
    )
    return DebugLightFixtureDependencies(
        adaptationOperations = adaptationOperations,
        automaticOperations = automaticOperations,
        managedPlanOperations = managedPlanOperations,
        controlOperations = controlOperations,
        customOperations = customOperations,
        libraryOperations = libraryOperations,
        systemOperations = DebugFixtureLightSystemOperations(
            delegate = graph.lightOperations.systemOperations,
            fixtures = fixtures
        )
    )
}

private fun createTankFixtureDependencies(
    graph: OwnerDependencyGraph,
    fixtures: DebugDeviceFixtureCatalog
): DebugTankFixtureDependencies {
    val assignments = DebugFixtureTankAssignmentRuntime()
    val assignmentOperations = DebugFixtureTankDeviceAssignmentOperations(
        delegate = DefaultTankDeviceAssignmentOperations(
            assignmentRepository = graph.assignmentRepository,
            devicesRepository = graph.devicesRepository
        ),
        fixtures = fixtures,
        runtime = assignments,
        tankExists = { tankId ->
            graph.aquariumTankStore.tanksSnapshotForOwner(graph.ownerUid)
                .any { tank -> tank.id == tankId }
        }
    )
    return DebugTankFixtureDependencies(
        assignmentOperations = assignmentOperations,
        quickSetupOperations = DebugFixtureLightQuickSetupTankOperations(
            delegate = graph.lightOperations.quickSetupTankOperations,
            fixtures = fixtures,
            assignments = assignments,
            tankById = { tankId ->
                graph.aquariumTankStore.tanksSnapshotForOwner(graph.ownerUid)
                    .singleOrNull { tank -> tank.id == tankId }
            }
        )
    )
}

private data class DebugLightFixtureDependencies(
    val adaptationOperations: DeviceLightAdaptationOperations,
    val automaticOperations: DeviceLightAutomaticOperations,
    val managedPlanOperations: DeviceLightManagedPlanOperations,
    val controlOperations: DeviceLightControlOperations,
    val customOperations: DeviceLightCustomOperations,
    val libraryOperations: DeviceLightLibraryOperations,
    val systemOperations: DeviceLightSystemOperations
)

private data class DebugTankFixtureDependencies(
    val assignmentOperations: TankDeviceAssignmentOperations,
    val quickSetupOperations: DeviceLightQuickSetupTankOperations
)

private data class DebugTimerFixtureDependencies(
    val graph: OwnerDependencyGraph,
    val lightAdaptationOperations: DeviceLightAdaptationOperations,
    val lightAutomaticOperations: DeviceLightAutomaticOperations,
    val lightManagedPlanOperations: DeviceLightManagedPlanOperations,
    val lightControlOperations: DeviceLightControlOperations,
    val lightCustomOperations: DeviceLightCustomOperations,
    val lightLibraryOperations: DeviceLightLibraryOperations,
    val lightSystemOperations: DeviceLightSystemOperations,
    val quickSetupTankOperations: DeviceLightQuickSetupTankOperations,
    val tankAssignmentOperations: TankDeviceAssignmentOperations,
    val timerControlOperations: DeviceTimerControlOperations,
    val controlSurfacePreparationOperations: DeviceControlSurfacePreparationOperations
)
