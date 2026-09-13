package com.aqua.aqualight.debug.devices

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.application.devices.light.control.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlOperations
import com.aqua.aqualight.composition.AppContainer
import com.aqua.aqualight.composition.OwnerDependencyGraph
import com.aqua.aqualight.composition.OwnerDependencyGraphAccess
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.DefaultOwnerDevicesOperations
import com.aqua.aqualight.data.devices.light.library.DefaultDeviceLightLibraryOperations
import com.aqua.aqualight.data.devices.light.library.DeviceLightLibraryStore
import com.aqua.aqualight.data.devices.menu.DefaultDeviceMenuAccessOperations
import com.aqua.aqualight.data.devices.remove.OwnerDeviceDataCleaner
import com.aqua.aqualight.ui.tabs.devices.DevicesViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootOverviewViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library.DeviceLightLibraryViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom.DeviceLightCustomCurveViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual.DeviceLightManualControlViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root.DeviceLightRootViewModel
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
        val viewModel: ViewModel = when (modelClass) {
            DevicesViewModel::class.java -> createDevicesViewModel(requireGraph())
            DeviceLightRootViewModel::class.java ->
                createLightRootViewModel(requireGraph())
            DeviceLightManualControlViewModel::class.java ->
                DeviceLightManualControlViewModel(
                    timerDependencies(requireGraph()).lightLibraryOperations
                )
            DeviceLightCustomCurveViewModel::class.java ->
                DeviceLightCustomCurveViewModel(
                    customOperations = requireGraph().lightOperations.customOperations,
                    libraryOperations = timerDependencies(requireGraph()).lightLibraryOperations
                )
            DeviceLightLibraryViewModel::class.java ->
                DeviceLightLibraryViewModel(
                    timerDependencies(requireGraph()).lightLibraryOperations
                )
            DeviceTimerRootViewModel::class.java ->
                createTimerRootViewModel(requireGraph())
            DeviceTimerProgramViewModel::class.java ->
                DeviceTimerProgramViewModel(timerDependencies(requireGraph()).timerControlOperations)
            DeviceTimerChannelViewModel::class.java ->
                DeviceTimerChannelViewModel(timerDependencies(requireGraph()).timerControlOperations)
            DeviceRootOverviewViewModel::class.java ->
                DeviceRootOverviewViewModel(rootOperations(requireGraph()))
            DeviceFamilySettingsViewModel::class.java ->
                createSettingsViewModel(requireGraph())
            DeviceFirmwareUpdateViewModel::class.java ->
                createFirmwareViewModel(requireGraph())
            else -> return delegate.create(modelClass)
        }

        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }

    private fun requireGraph(): OwnerDependencyGraph = ownerGraphAccess.requireActiveOwnerGraph()

    private fun createDevicesViewModel(graph: OwnerDependencyGraph): DevicesViewModel {
        val timerDependencies = timerDependencies(graph)
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
                    timerDependencies.controlSurfacePreparationOperations
            ),
            routeResolver = DeviceRouteResolver()
        )
    }

    private fun createSettingsViewModel(graph: OwnerDependencyGraph): DeviceFamilySettingsViewModel =
        DeviceFamilySettingsViewModel(
            settingsOperations = DebugFixtureFamilySettingsOperations(
                repository = graph.devicesRepository,
                lightProtectionOperations = graph.lightOperations.protectionOperations,
                fixtures = fixtures
            ),
            firmwareUpdateOperations = fixtureFirmwareOperations(graph, fixtures),
            manifestUrl = BuildConfig.AQL_OTA_MANIFEST_URL
        )

    private fun createFirmwareViewModel(graph: OwnerDependencyGraph): DeviceFirmwareUpdateViewModel =
        DeviceFirmwareUpdateViewModel(
            rootOperations = rootOperations(graph),
            firmwareUpdateOperations = fixtureFirmwareOperations(graph, fixtures),
            manifestUrl = BuildConfig.AQL_OTA_MANIFEST_URL
        )

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
        val lightControlOperations = DebugFixtureLightControlOperations(
            delegate = graph.lightOperations.controlOperations,
            fixtures = fixtures
        )
        val lightLibraryOperations = DefaultDeviceLightLibraryOperations(
            ownerUid = graph.ownerUid,
            store = DeviceLightLibraryStore.create(appContext, graph.ownerUid),
            devicesRepository = graph.devicesRepository,
            controlOperations = lightControlOperations
        )
        return DebugTimerFixtureDependencies(
            graph = graph,
            lightControlOperations = lightControlOperations,
            lightLibraryOperations = lightLibraryOperations,
            timerControlOperations = timerControlOperations,
            controlSurfacePreparationOperations =
                DebugFixtureControlSurfacePreparationOperations(
                    delegate = graph.controlSurfacePreparationOperations,
                    fixtures = fixtures,
                    timerControlOperations = timerControlOperations,
                    lightControlOperations = lightControlOperations
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

private data class DebugTimerFixtureDependencies(
    val graph: OwnerDependencyGraph,
    val lightControlOperations: DeviceLightControlOperations,
    val lightLibraryOperations: DeviceLightLibraryOperations,
    val timerControlOperations: DeviceTimerControlOperations,
    val controlSurfacePreparationOperations: DeviceControlSurfacePreparationOperations
)
