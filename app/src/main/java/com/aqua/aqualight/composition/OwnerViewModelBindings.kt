package com.aqua.aqualight.composition

import android.content.Context
import androidx.lifecycle.ViewModel
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.application.user.UserProfileOperations
import com.aqua.aqualight.data.aquarium.DefaultAquariumTankOperations
import com.aqua.aqualight.data.aquarium.delete.OwnerTankDataCleaner
import com.aqua.aqualight.data.aquarium.devices.DefaultTankDeviceAssignmentOperations
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.care.DefaultMaintenanceOperations
import com.aqua.aqualight.data.care.integrity.restoreTaskSnapshotsForIntegrity
import com.aqua.aqualight.data.care.integrity.snapshotTasksForIntegrity
import com.aqua.aqualight.data.devices.DefaultDeviceDosingCalibrationOperations
import com.aqua.aqualight.data.devices.DefaultDeviceFamilySettingsOperations
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.DefaultDeviceStatusOperations
import com.aqua.aqualight.data.devices.DefaultOwnerDevicesOperations
import com.aqua.aqualight.data.devices.menu.DefaultDeviceMenuAccessOperations
import com.aqua.aqualight.data.devices.provisioning.DefaultProvisioningDiscoveryOperations
import com.aqua.aqualight.data.devices.provisioning.DefaultProvisioningProgressOperations
import com.aqua.aqualight.data.devices.provisioning.ble.DefaultBleProvisioningScanner
import com.aqua.aqualight.data.devices.provisioning.qr.AqlProvisioningQrParser
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningQrSecretStore
import com.aqua.aqualight.data.devices.remove.OwnerDeviceDataCleaner
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.platform.text.AndroidAppTextResolver
import com.aqua.aqualight.platform.text.AndroidMaintenanceTextResolver
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select.TankDeviceSelectViewModel
import com.aqua.aqualight.ui.tabs.devices.DevicesViewModel
import com.aqua.aqualight.ui.tabs.devices.add.DeviceAddViewModel
import com.aqua.aqualight.ui.tabs.devices.add.DeviceProvisioningProgressViewModel
import com.aqua.aqualight.ui.tabs.devices.add.DeviceQrScanViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootOverviewViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.DeviceCoolingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DeviceDosingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.calibration.DeviceDosingCalibrationViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.settings.DeviceFamilySettingsViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateViewModel
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.ui.tabs.settings.SettingsViewModel
import com.aqua.aqualight.ui.tabs.settings.app.DataManagementViewModel
import com.aqua.aqualight.ui.tabs.settings.device.DeviceStatusViewModel
import com.aqua.aqualight.ui.tabs.settings.device.SystemDeviceStatusClock

/** Small exact-match binding groups for the owner-scoped ViewModel factory. */
internal class OwnerViewModelBindings(
    context: Context,
    private val userProfileOperations: UserProfileOperations,
    private val notificationPreferenceUseCase: NotificationPreferenceUseCase,
    private val graph: OwnerDependencyGraph
) {
    private val appContext = context.applicationContext
    private val repository = graph.devicesRepository
    private val assignments = graph.assignmentRepository
    private val appTextResolver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidAppTextResolver(appContext)
    }
    private val maintenanceTextResolver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidMaintenanceTextResolver(appContext)
    }

    fun create(modelClass: Class<out ViewModel>): ViewModel? =
        createAccountViewModel(modelClass)
            ?: createProvisioningViewModel(modelClass)
            ?: createDeviceListViewModel(modelClass)
            ?: createDeviceRootViewModel(modelClass)
            ?: createDeviceSettingsViewModel(modelClass)
            ?: createAquariumViewModel(modelClass)
            ?: createMaintenanceViewModel(modelClass)

    private fun createAccountViewModel(modelClass: Class<out ViewModel>): ViewModel? =
        when (modelClass) {
            SettingsViewModel::class.java -> SettingsViewModel(
                userProfileOperations = userProfileOperations,
                deviceStatusOperations = DefaultDeviceStatusOperations(repository)
            )
            DataManagementViewModel::class.java -> DataManagementViewModel(
                archiveOperations = graph.userDataArchiveOperations
            )
            DeviceStatusViewModel::class.java -> DeviceStatusViewModel(
                operations = DefaultDeviceStatusOperations(repository),
                clock = SystemDeviceStatusClock()
            )
            else -> null
        }

    private fun createProvisioningViewModel(modelClass: Class<out ViewModel>): ViewModel? =
        when (modelClass) {
            DeviceAddViewModel::class.java -> DeviceAddViewModel(
                discoveryOperations = provisioningDiscoveryOperations(),
                textResolver = appTextResolver
            )
            DeviceQrScanViewModel::class.java -> DeviceQrScanViewModel(
                discoveryOperations = provisioningDiscoveryOperations(),
                textResolver = appTextResolver
            )
            DeviceProvisioningProgressViewModel::class.java -> DeviceProvisioningProgressViewModel(
                operations = DefaultProvisioningProgressOperations(
                    context = appContext,
                    ownerUid = graph.ownerUid,
                    draftStore = AqlProvisioningDraftStore(
                        context = appContext,
                        ownerUidProvider = { graph.ownerUid }
                    )
                ),
                textResolver = appTextResolver
            )
            else -> null
        }

    private fun createDeviceListViewModel(modelClass: Class<out ViewModel>): ViewModel? =
        when (modelClass) {
            DevicesViewModel::class.java -> DevicesViewModel(
                operations = createOwnerDevicesOperations(),
                menuAccessOperations = DefaultDeviceMenuAccessOperations.create(repository),
                routeResolver = DeviceRouteResolver()
            )
            else -> null
        }

    private fun createDeviceRootViewModel(modelClass: Class<out ViewModel>): ViewModel? =
        when (modelClass) {
            DeviceLightRootViewModel::class.java ->
                DeviceLightRootViewModel(DefaultDeviceRootOperations(repository))
            DeviceCoolingRootViewModel::class.java ->
                DeviceCoolingRootViewModel(DefaultDeviceRootOperations(repository))
            DeviceTimerRootViewModel::class.java ->
                DeviceTimerRootViewModel(DefaultDeviceRootOperations(repository))
            DeviceDosingRootViewModel::class.java ->
                DeviceDosingRootViewModel(DefaultDeviceRootOperations(repository))
            DeviceDosingCalibrationViewModel::class.java -> DeviceDosingCalibrationViewModel(
                DefaultDeviceDosingCalibrationOperations(repository)
            )
            DeviceRootOverviewViewModel::class.java ->
                DeviceRootOverviewViewModel(DefaultDeviceRootOperations(repository))
            else -> null
        }

    private fun createDeviceSettingsViewModel(modelClass: Class<out ViewModel>): ViewModel? =
        when (modelClass) {
            DeviceFamilySettingsViewModel::class.java -> DeviceFamilySettingsViewModel(
                settingsOperations = DefaultDeviceFamilySettingsOperations(repository),
                firmwareUpdateOperations = graph.firmwareUpdateOperations,
                manifestUrl = BuildConfig.AQL_OTA_MANIFEST_URL
            )
            DeviceFirmwareUpdateViewModel::class.java -> DeviceFirmwareUpdateViewModel(
                rootOperations = DefaultDeviceRootOperations(repository),
                firmwareUpdateOperations = graph.firmwareUpdateOperations,
                manifestUrl = BuildConfig.AQL_OTA_MANIFEST_URL
            )
            else -> null
        }

    private fun createAquariumViewModel(modelClass: Class<out ViewModel>): ViewModel? =
        when (modelClass) {
            AquariumTankViewModel::class.java -> AquariumTankViewModel(
                operations = DefaultAquariumTankOperations(
                    context = appContext,
                    tankStore = graph.aquariumTankStore,
                    tankDataCleaner = createTankDataCleaner(),
                    notificationPreferences = notificationPreferenceUseCase
                )
            )
            TankDetailDevicesViewModel::class.java -> TankDetailDevicesViewModel(
                assignmentOperations = DefaultTankDeviceAssignmentOperations(
                    assignmentRepository = assignments,
                    devicesRepository = repository
                ),
                menuAccessOperations = DefaultDeviceMenuAccessOperations.create(repository),
                routeResolver = DeviceRouteResolver()
            )
            TankDeviceSelectViewModel::class.java -> TankDeviceSelectViewModel(
                assignmentOperations = DefaultTankDeviceAssignmentOperations(
                    assignmentRepository = assignments,
                    devicesRepository = repository
                )
            )
            else -> null
        }

    private fun createMaintenanceViewModel(modelClass: Class<out ViewModel>): ViewModel? =
        when (modelClass) {
            MaintenanceViewModel::class.java -> MaintenanceViewModel(
                operations = DefaultMaintenanceOperations(
                    context = appContext,
                    manager = graph.careTaskStore,
                    notificationPreferences = notificationPreferenceUseCase
                ),
                textResolver = maintenanceTextResolver
            )
            else -> null
        }

    private fun provisioningDiscoveryOperations() = DefaultProvisioningDiscoveryOperations(
        scanner = DefaultBleProvisioningScanner(appContext),
        repository = repository,
        qrParser = AqlProvisioningQrParser(),
        qrSecretStore = AqlProvisioningQrSecretStore(
            context = appContext,
            ownerUidProvider = { graph.ownerUid }
        )
    )

    private fun createTankDataCleaner() = OwnerTankDataCleaner(
        deleteTankRecords = graph.aquariumTankStore::deleteTanks,
        snapshotCareTasksForTank = { tankId ->
            graph.careTaskStore.snapshotTasksForIntegrity(tankId)
        },
        deleteCareTasksForTank = graph.careTaskStore::deleteTasksForTank,
        restoreCareTasksForTank = { tankId, snapshots ->
            graph.careTaskStore.restoreTaskSnapshotsForIntegrity(
                tankId = tankId,
                snapshots = snapshots
            )
        },
        removeDeviceAssignmentsForTank = assignments::removeAssignmentsForTank,
        cancelCareTaskReminder = notificationPreferenceUseCase::cancelCareTask,
        reconcileCareReminders = notificationPreferenceUseCase::reconcileOwner
    )

    private fun createOwnerDevicesOperations(): DefaultOwnerDevicesOperations =
        DefaultOwnerDevicesOperations(
            devicesRepository = repository,
            assignmentRepository = assignments,
            deviceDataCleaner = OwnerDeviceDataCleaner.create(
                devicesRepository = repository,
                assignmentRepository = assignments
            ),
            cleanupDeletedDeviceNotifications = { deviceUids ->
                graph.deviceFirmwareNotifications.clearDeletedDevices(
                    ownerUid = graph.ownerUid,
                    deviceUids = deviceUids
                )
            }
        )
}
