package com.aqua.aqualight.debug.devices

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.composition.ActiveOwnerDependencyGraphResolver
import com.aqua.aqualight.composition.AppContainer
import com.aqua.aqualight.composition.OwnerDependencyGraph
import com.aqua.aqualight.data.devices.DefaultDeviceDosingCalibrationOperations
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.DefaultOwnerDevicesOperations
import com.aqua.aqualight.data.devices.menu.DefaultDeviceMenuAccessOperations
import com.aqua.aqualight.data.devices.remove.OwnerDeviceDataCleaner
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.ui.tabs.devices.DevicesViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootOverviewViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.DeviceCoolingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DeviceDosingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.calibration.DeviceDosingCalibrationViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.settings.DeviceFamilySettingsViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateViewModel
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver

/**
 * Debug-only AppContainer decorator. Every non-device binding remains owned by the production
 * container. Device bindings retain the active production owner graph and decorate only the small
 * application boundaries needed by catalog-backed fixtures.
 */
internal class DebugDeviceFixtureAppContainer(
    context: Context,
    private val delegate: AppContainer
) : AppContainer by delegate {

    override val defaultViewModelFactory: ViewModelProvider.Factory =
        DebugDeviceFixtureViewModelFactory(
            context = context.applicationContext,
            delegate = delegate.defaultViewModelFactory,
            appContainer = delegate
        )
}

private class DebugDeviceFixtureViewModelFactory(
    context: Context,
    private val delegate: ViewModelProvider.Factory,
    appContainer: AppContainer
) : ViewModelProvider.Factory {

    private val appContext = context.applicationContext
    private val fixtures = DebugDeviceFixtureCatalog()
    private val ownerGraphResolver = ActiveOwnerDependencyGraphResolver(
        context = appContext,
        deviceFirmwareNotifications = NotificationPlatform.get(appContext).deviceFirmwareUpdates,
        notificationPreferenceUseCase = appContainer.notificationPreferenceUseCase,
        userPreferencesManager = appContainer.userPreferencesManager
    )

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when (modelClass) {
            DevicesViewModel::class.java -> createDevicesViewModel(requireGraph())
            DeviceLightRootViewModel::class.java ->
                DeviceLightRootViewModel(rootOperations(requireGraph()))
            DeviceCoolingRootViewModel::class.java ->
                DeviceCoolingRootViewModel(rootOperations(requireGraph()))
            DeviceTimerRootViewModel::class.java ->
                DeviceTimerRootViewModel(rootOperations(requireGraph()))
            DeviceDosingRootViewModel::class.java ->
                DeviceDosingRootViewModel(rootOperations(requireGraph()))
            DeviceDosingCalibrationViewModel::class.java ->
                createDosingCalibrationViewModel(requireGraph())
            DeviceRootOverviewViewModel::class.java ->
                DeviceRootOverviewViewModel(rootOperations(requireGraph()))
            DeviceFamilySettingsViewModel::class.java -> createSettingsViewModel(requireGraph())
            DeviceFirmwareUpdateViewModel::class.java -> createFirmwareViewModel(requireGraph())
            else -> return delegate.create(modelClass)
        }

        return modelClass.cast(viewModel)
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
            menuAccessOperations = DebugFixtureMenuAccessOperations(
                delegate = DefaultDeviceMenuAccessOperations.create(repository),
                fixtures = fixtures
            ),
            routeResolver = DeviceRouteResolver()
        )
    }

    private fun createDosingCalibrationViewModel(
        graph: OwnerDependencyGraph
    ): DeviceDosingCalibrationViewModel = DeviceDosingCalibrationViewModel(
        DebugFixtureDosingCalibrationOperations(
            delegate = DefaultDeviceDosingCalibrationOperations(graph.devicesRepository),
            fixtures = fixtures
        )
    )

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

    private fun requireGraph(): OwnerDependencyGraph = ownerGraphResolver.requireActive()
}
