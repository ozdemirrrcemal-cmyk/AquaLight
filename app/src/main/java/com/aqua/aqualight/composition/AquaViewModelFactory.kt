package com.aqua.aqualight.composition

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.application.user.UserProfileOperations
import com.aqua.aqualight.data.aquarium.delete.OwnerTankDataCleaner
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.AndroidMaintenanceTextResolver
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.DefaultMaintenanceRepository
import com.aqua.aqualight.data.devices.DefaultDeviceFirmwareUpdateOperations
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.DefaultDeviceStatusOperations
import com.aqua.aqualight.data.devices.DefaultOwnerDevicesOperations
import com.aqua.aqualight.data.devices.menu.DefaultDeviceMenuAccessOperations
import com.aqua.aqualight.data.devices.provisioning.ble.DefaultBleProvisioningScanner
import com.aqua.aqualight.data.devices.provisioning.qr.AqlProvisioningQrParser
import com.aqua.aqualight.data.devices.remove.OwnerDeviceDataCleaner
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.platform.text.AndroidAppTextResolver
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.TankDetailViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select.TankDeviceSelectViewModel
import com.aqua.aqualight.ui.tabs.devices.DevicesViewModel
import com.aqua.aqualight.ui.tabs.devices.add.DeviceAddViewModel
import com.aqua.aqualight.ui.tabs.devices.add.DeviceProvisioningProgressViewModel
import com.aqua.aqualight.ui.tabs.devices.add.DeviceQrScanViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootOverviewViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.DeviceCoolingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DeviceDosingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerRootViewModel
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.ui.tabs.settings.SettingsViewModel
import com.aqua.aqualight.ui.tabs.settings.device.DeviceStatusViewModel
import com.aqua.aqualight.ui.tabs.settings.device.SystemDeviceStatusClock

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
                    deviceStatusOperations = DefaultDeviceStatusOperations(devicesRepository())
                )
            }

            modelClass.isAssignableFrom(DeviceStatusViewModel::class.java) ->
                DeviceStatusViewModel(
                    operations = DefaultDeviceStatusOperations(devicesRepository()),
                    clock = SystemDeviceStatusClock()
                )

            modelClass.isAssignableFrom(DevicesViewModel::class.java) -> {
                val repository = devicesRepository()
                val assignments = assignmentRepository()
                DevicesViewModel(
                    operations = DefaultOwnerDevicesOperations(
                        devicesRepository = repository,
                        assignmentRepository = assignments,
                        deviceDataCleaner = OwnerDeviceDataCleaner.create(
                            devicesRepository = repository,
                            assignmentRepository = assignments
                        )
                    ),
                    menuAccessOperations = DefaultDeviceMenuAccessOperations.create(repository),
                    routeResolver = DeviceRouteResolver()
                )
            }

            modelClass.isAssignableFrom(DeviceAddViewModel::class.java) ->
                DeviceAddViewModel(
                    bleScanner = DefaultBleProvisioningScanner(appContext),
                    textResolver = appTextResolver
                )

            modelClass.isAssignableFrom(DeviceQrScanViewModel::class.java) ->
                DeviceQrScanViewModel(
                    bleScanner = DefaultBleProvisioningScanner(appContext),
                    repository = devicesRepository(),
                    textResolver = appTextResolver,
                    qrParser = AqlProvisioningQrParser()
                )

            modelClass.isAssignableFrom(DeviceProvisioningProgressViewModel::class.java) ->
                DeviceProvisioningProgressViewModel(
                    operations = DefaultDeviceProvisioningProgressOperations(appContext),
                    textResolver = appTextResolver
                )

            modelClass.isAssignableFrom(AquariumTankViewModel::class.java) -> {
                val assignments = assignmentRepository()
                AquariumTankViewModel(
                    tankDataStoreManager = aquariumTankStore,
                    careTaskDataStoreManager = careTaskStore,
                    assignmentRepository = assignments,
                    tankDataCleaner = OwnerTankDataCleaner(
                        deleteTankRecords = aquariumTankStore::deleteTanks,
                        deleteCareTasksForTank = careTaskStore::deleteTasksForTank,
                        removeDeviceAssignmentsForTank = assignments::removeAssignmentsForTank
                    )
                )
            }

            modelClass.isAssignableFrom(TankDetailViewModel::class.java) ->
                TankDetailViewModel()

            modelClass.isAssignableFrom(MaintenanceViewModel::class.java) ->
                MaintenanceViewModel(
                    repository = maintenanceRepository,
                    textResolver = maintenanceTextResolver
                )

            modelClass.isAssignableFrom(DeviceLightRootViewModel::class.java) -> {
                val repository = devicesRepository()
                DeviceLightRootViewModel(
                    rootOperations = DefaultDeviceRootOperations(repository),
                    firmwareUpdateOperations = DefaultDeviceFirmwareUpdateOperations(repository)
                )
            }

            modelClass.isAssignableFrom(DeviceCoolingRootViewModel::class.java) ->
                DeviceCoolingRootViewModel(
                    operations = DefaultDeviceRootOperations(devicesRepository())
                )

            modelClass.isAssignableFrom(DeviceTimerRootViewModel::class.java) ->
                DeviceTimerRootViewModel(
                    operations = DefaultDeviceRootOperations(devicesRepository())
                )

            modelClass.isAssignableFrom(DeviceDosingRootViewModel::class.java) ->
                DeviceDosingRootViewModel(
                    operations = DefaultDeviceRootOperations(devicesRepository())
                )

            modelClass.isAssignableFrom(DeviceRootOverviewViewModel::class.java) ->
                DeviceRootOverviewViewModel(
                    operations = DefaultDeviceRootOperations(devicesRepository())
                )

            modelClass.isAssignableFrom(TankDetailDevicesViewModel::class.java) -> {
                val repository = devicesRepository()
                TankDetailDevicesViewModel(
                    devicesRepository = repository,
                    assignmentRepository = assignmentRepository(),
                    menuAccessOperations = DefaultDeviceMenuAccessOperations.create(repository),
                    routeResolver = DeviceRouteResolver()
                )
            }

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

    private fun devicesRepository(): DevicesRepository = DevicesRepositoryProvider.get(appContext)

    private fun assignmentRepository() =
        TankDeviceAssignmentRepositoryProvider.get(appContext)
}
