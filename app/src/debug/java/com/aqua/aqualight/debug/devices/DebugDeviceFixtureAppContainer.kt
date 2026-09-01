package com.aqua.aqualight.debug.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.composition.AppContainer
import com.aqua.aqualight.composition.OwnerDependencyGraph
import com.aqua.aqualight.composition.OwnerDependencyGraphAccess
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.DefaultOwnerDevicesOperations
import com.aqua.aqualight.data.devices.cooling.DefaultDeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.data.devices.cooling.DefaultDeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.data.devices.cooling.control.DefaultDeviceCoolingControlOperations
import com.aqua.aqualight.data.devices.menu.DefaultDeviceMenuAccessOperations
import com.aqua.aqualight.data.devices.remove.OwnerDeviceDataCleaner
import com.aqua.aqualight.ui.tabs.devices.DevicesViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootOverviewViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.root.DeviceCoolingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.settings.DeviceFamilySettingsViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateViewModel
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver

/**
 * Debug-only AppContainer decorator for non-Dosing catalog fixtures.
 *
 * The decorator shares the exact production owner graph. Dosing is never overridden here, so every
 * Dosing ViewModel uses the production runtime, adapter and canonical state owner in debug builds.
 */
internal class DebugDeviceFixtureAppContainer(
    private val delegate: AppContainer
) : AppContainer by delegate {
    private val ownerGraphAccess = requireNotNull(delegate as? OwnerDependencyGraphAccess) {
        "Debug fixture composition requires the production owner graph."
    }

    override val defaultViewModelFactory: ViewModelProvider.Factory =
        DebugDeviceFixtureViewModelFactory(
            delegate = delegate.defaultViewModelFactory,
            ownerGraphAccess = ownerGraphAccess
        )
}

private class DebugDeviceFixtureViewModelFactory(
    private val delegate: ViewModelProvider.Factory,
    private val ownerGraphAccess: OwnerDependencyGraphAccess
) : ViewModelProvider.Factory {

    private val fixtures = DebugDeviceFixtureCatalog()

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when (modelClass) {
            DevicesViewModel::class.java -> createDevicesViewModel(requireGraph())
            DeviceLightRootViewModel::class.java ->
                DeviceLightRootViewModel(rootOperations(requireGraph()))
            DeviceCoolingRootViewModel::class.java ->
                createCoolingRootViewModel(requireGraph())
            DeviceTimerRootViewModel::class.java ->
                DeviceTimerRootViewModel(rootOperations(requireGraph()))
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

    private fun createCoolingRootViewModel(graph: OwnerDependencyGraph): DeviceCoolingRootViewModel {
        val repository = graph.devicesRepository
        return DeviceCoolingRootViewModel(
            operations = rootOperations(graph),
            controlOperations = DefaultDeviceCoolingControlOperations(repository),
            historyOperations = DefaultDeviceCoolingTemperatureHistoryOperations(repository),
            automaticSettingsOperations = DefaultDeviceCoolingAutomaticSettingsOperations(repository)
        )
    }

    private fun createDevicesViewModel(graph: OwnerDependencyGraph): DevicesViewModel {
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
                    graph.dosingOperations.controlSurfacePreparationOperations
            ),
            routeResolver = DeviceRouteResolver()
        )
    }

    private fun createSettingsViewModel(graph: OwnerDependencyGraph): DeviceFamilySettingsViewModel =
        DeviceFamilySettingsViewModel(
            settingsOperations = DebugFixtureFamilySettingsOperations(
                repository = graph.devicesRepository,
                fixtures = fixtures
            ),
            firmwareUpdateOperations = firmwareOperations(graph),
            manifestUrl = BuildConfig.AQL_OTA_MANIFEST_URL
        )

    private fun createFirmwareViewModel(graph: OwnerDependencyGraph): DeviceFirmwareUpdateViewModel =
        DeviceFirmwareUpdateViewModel(
            rootOperations = rootOperations(graph),
            firmwareUpdateOperations = firmwareOperations(graph),
            manifestUrl = BuildConfig.AQL_OTA_MANIFEST_URL
        )

    private fun rootOperations(graph: OwnerDependencyGraph) = DebugFixtureDeviceRootOperations(
        delegate = DefaultDeviceRootOperations(graph.devicesRepository),
        fixtures = fixtures
    )

    private fun firmwareOperations(graph: OwnerDependencyGraph) =
        DebugFixtureFirmwareUpdateOperations(
            delegate = graph.firmwareUpdateOperations,
            fixtures = fixtures
        )
}
