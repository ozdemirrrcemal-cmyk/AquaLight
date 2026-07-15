package com.aqua.aqualight.composition

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.application.user.UserProfileOperations
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.AndroidMaintenanceTextResolver
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.DefaultMaintenanceRepository
import com.aqua.aqualight.data.devices.provisioning.ble.DefaultBleProvisioningScanner
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.platform.text.AndroidAppTextResolver
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select.TankDeviceSelectViewModel
import com.aqua.aqualight.ui.tabs.devices.DevicesViewModel
import com.aqua.aqualight.ui.tabs.devices.add.DeviceAddViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootOverviewViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.DeviceCoolingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DeviceDosingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerRootViewModel
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.ui.tabs.settings.SettingsViewModel
import com.aqua.aqualight.ui.tabs.settings.device.DeviceStatusViewModel

/**
 * Process composition-root factory for non-auth feature ViewModels.
 *
 * Owner-bound repositories are resolved only when a ViewModel is requested, so
 * startup and unauthenticated screens do not open device runtime.
 */
internal class AquaViewModelFactory(
    context: Context,
    private val userProfileOperations: UserProfileOperations
) : ViewModelProvider.Factory {

    private val appContext = context.applicationContext
    private val fallbackFactory = ViewModelProvider.AndroidViewModelFactory.getInstance(
        appContext as Application
    )
    private val appTextResolver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidAppTextResolver(appContext)
    }
    private val aquariumTankStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AquariumTankDataStoreManager(appContext)
    }
    private val careTaskStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CareTaskDataStoreManager.create(appContext)
    }
    private val maintenanceRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DefaultMaintenanceRepository(
            context = appContext,
            manager = careTaskStore
        )
    }
    private val maintenanceTextResolver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidMaintenanceTextResolver(appContext)
    }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    userProfileOperations = userProfileOperations,
                    devicesRepository = devicesRepository()
                )
            }

            modelClass.isAssignableFrom(DeviceStatusViewModel::class.java) ->
                DeviceStatusViewModel(devicesRepository())

            modelClass.isAssignableFrom(DevicesViewModel::class.java) ->
                DevicesViewModel(
                    repository = devicesRepository(),
                    assignmentRepository = assignmentRepository()
                )

            modelClass.isAssignableFrom(DeviceAddViewModel::class.java) ->
                DeviceAddViewModel(
                    bleScanner = DefaultBleProvisioningScanner(appContext),
                    textResolver = appTextResolver
                )

            modelClass.isAssignableFrom(AquariumTankViewModel::class.java) ->
                AquariumTankViewModel(
                    tankDataStoreManager = aquariumTankStore,
                    careTaskDataStoreManager = careTaskStore,
                    assignmentRepository = assignmentRepository()
                )

            modelClass.isAssignableFrom(MaintenanceViewModel::class.java) ->
                MaintenanceViewModel(
                    repository = maintenanceRepository,
                    textResolver = maintenanceTextResolver
                )

            modelClass.isAssignableFrom(DeviceLightRootViewModel::class.java) ->
                DeviceLightRootViewModel(devicesRepository())

            modelClass.isAssignableFrom(DeviceCoolingRootViewModel::class.java) ->
                DeviceCoolingRootViewModel(devicesRepository())

            modelClass.isAssignableFrom(DeviceTimerRootViewModel::class.java) ->
                DeviceTimerRootViewModel(devicesRepository())

            modelClass.isAssignableFrom(DeviceDosingRootViewModel::class.java) ->
                DeviceDosingRootViewModel(devicesRepository())

            modelClass.isAssignableFrom(DeviceRootOverviewViewModel::class.java) ->
                DeviceRootOverviewViewModel(devicesRepository())

            modelClass.isAssignableFrom(TankDetailDevicesViewModel::class.java) ->
                TankDetailDevicesViewModel(
                    devicesRepository = devicesRepository(),
                    assignmentRepository = assignmentRepository()
                )

            modelClass.isAssignableFrom(TankDeviceSelectViewModel::class.java) ->
                TankDeviceSelectViewModel(
                    assignmentRepository = assignmentRepository(),
                    devicesRepository = devicesRepository()
                )

            else -> return fallbackFactory.create(modelClass)
        }

        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }

    private fun devicesRepository() = DevicesRepositoryProvider.get(appContext)

    private fun assignmentRepository() =
        TankDeviceAssignmentRepositoryProvider.get(appContext)
}
