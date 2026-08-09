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

/** Exact ViewModel bindings that require one committed authenticated-owner graph. */
internal class OwnerViewModelFactory(
    context: Context,
    private val userProfileOperations: UserProfileOperations,
    private val notificationPreferenceUseCase: NotificationPreferenceUseCase,
    private val ownerGraphResolver: OwnerDependencyGraphResolver
) : ScopedViewModelFactory {

    private val appContext = context.applicationContext
    private val appTextResolver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidAppTextResolver(appContext)
    }
    private val maintenanceTextResolver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidMaintenanceTextResolver(appContext)
    }

    private val creators: Map<Class<out ViewModel>, (OwnerDependencyGraph) -> ViewModel> = mapOf(
        SettingsViewModel::class.java to { graph ->
            SettingsViewModel(
                userProfileOperations = userProfileOperations,
                deviceStatusOperations = DefaultDeviceStatusOperations(graph.devicesRepository)
            )
        },
        DataManagementViewModel::class.java to { graph ->
            DataManagementViewModel(archiveOperations = graph.userDataArchiveOperations)
        },
        DeviceStatusViewModel::class.java to { graph ->
            DeviceStatusViewModel(
                operations = DefaultDeviceStatusOperations(graph.devicesRepository),
                clock = SystemDeviceStatusClock()
            )
        },
        DevicesViewModel::class.java to { graph ->
            DevicesViewModel(
                operations = createOwnerDevicesOperations(
                    graph = graph,
                    repository = graph.devicesRepository,
                    assignments = graph.assignmentRepository
                ),
                menuAccessOperations = DefaultDeviceMenuAccessOperations.create(
                    graph.devicesRepository
                ),
                routeResolver = DeviceRouteResolver()
            )
        },
        DeviceAddViewModel::class.java to { graph ->
            DeviceAddViewModel(
                discoveryOperations = DefaultProvisioningDiscoveryOperations(
                    scanner = DefaultBleProvisioningScanner(appContext),
                    repository = graph.devicesRepository,
                    qrParser = AqlProvisioningQrParser(),
                    qrSecretStore = AqlProvisioningQrSecretStore(
                        context = appContext,
                        ownerUidProvider = { graph.ownerUid }
                    )
                ),
                textResolver = appTextResolver
            )
        },
        DeviceQrScanViewModel::class.java to { graph ->
            DeviceQrScanViewModel(
                discoveryOperations = DefaultProvisioningDiscoveryOperations(
                    scanner = DefaultBleProvisioningScanner(appContext),
                    repository = graph.devicesRepository,
                    qrParser = AqlProvisioningQrParser(),
                    qrSecretStore = AqlProvisioningQrSecretStore(
                        context = appContext,
                        ownerUidProvider = { graph.ownerUid }
                    )
                ),
                textResolver = appTextResolver
            )
        },
        DeviceProvisioningProgressViewModel::class.java to { graph ->
            DeviceProvisioningProgressViewModel(
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
        },
        AquariumTankViewModel::class.java to { graph ->
            AquariumTankViewModel(
                operations = DefaultAquariumTankOperations(
                    context = appContext,
                    tankStore = graph.aquariumTankStore,
                    tankDataCleaner = OwnerTankDataCleaner(
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
                        removeDeviceAssignmentsForTank =
                            graph.assignmentRepository::removeAssignmentsForTank,
                        cancelCareTaskReminder =
                            notificationPreferenceUseCase::cancelCareTask,
                        reconcileCareReminders =
                            notificationPreferenceUseCase::reconcileOwner
                    ),
                    notificationPreferences = notificationPreferenceUseCase
                )
            )
        },
        MaintenanceViewModel::class.java to { graph ->
            MaintenanceViewModel(
                operations = DefaultMaintenanceOperations(
                    context = appContext,
                    manager = graph.careTaskStore,
                    notificationPreferences = notificationPreferenceUseCase
                ),
                textResolver = maintenanceTextResolver
            )
        },
        DeviceLightRootViewModel::class.java to { graph ->
            DeviceLightRootViewModel(DefaultDeviceRootOperations(graph.devicesRepository))
        },
        DeviceCoolingRootViewModel::class.java to { graph ->
            DeviceCoolingRootViewModel(DefaultDeviceRootOperations(graph.devicesRepository))
        },
        DeviceTimerRootViewModel::class.java to { graph ->
            DeviceTimerRootViewModel(DefaultDeviceRootOperations(graph.devicesRepository))
        },
        DeviceDosingRootViewModel::class.java to { graph ->
            DeviceDosingRootViewModel(DefaultDeviceRootOperations(graph.devicesRepository))
        },
        DeviceDosingCalibrationViewModel::class.java to { graph ->
            DeviceDosingCalibrationViewModel(
                DefaultDeviceDosingCalibrationOperations(graph.devicesRepository)
            )
        },
        DeviceRootOverviewViewModel::class.java to { graph ->
            DeviceRootOverviewViewModel(DefaultDeviceRootOperations(graph.devicesRepository))
        },
        DeviceFamilySettingsViewModel::class.java to { graph ->
            DeviceFamilySettingsViewModel(
                settingsOperations = DefaultDeviceFamilySettingsOperations(graph.devicesRepository),
                firmwareUpdateOperations = graph.firmwareUpdateOperations,
                manifestUrl = BuildConfig.AQL_OTA_MANIFEST_URL
            )
        },
        DeviceFirmwareUpdateViewModel::class.java to { graph ->
            DeviceFirmwareUpdateViewModel(
                rootOperations = DefaultDeviceRootOperations(graph.devicesRepository),
                firmwareUpdateOperations = graph.firmwareUpdateOperations,
                manifestUrl = BuildConfig.AQL_OTA_MANIFEST_URL
            )
        },
        TankDetailDevicesViewModel::class.java to { graph ->
            TankDetailDevicesViewModel(
                assignmentOperations = DefaultTankDeviceAssignmentOperations(
                    assignmentRepository = graph.assignmentRepository,
                    devicesRepository = graph.devicesRepository
                ),
                menuAccessOperations = DefaultDeviceMenuAccessOperations.create(
                    graph.devicesRepository
                ),
                routeResolver = DeviceRouteResolver()
            )
        },
        TankDeviceSelectViewModel::class.java to { graph ->
            TankDeviceSelectViewModel(
                assignmentOperations = DefaultTankDeviceAssignmentOperations(
                    assignmentRepository = graph.assignmentRepository,
                    devicesRepository = graph.devicesRepository
                )
            )
        }
    )

    override fun supports(modelClass: Class<out ViewModel>): Boolean = modelClass in OWNER_BINDINGS

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(supports(modelClass)) {
            "No owner-scoped ViewModel binding for ${modelClass.name}."
        }
        val graph = ownerGraphResolver.requireActive()
        val creator = checkNotNull(creators[modelClass]) {
            "Registered owner ViewModel binding has no creator: ${modelClass.name}."
        }
        return modelClass.cast(creator(graph))
    }

    private fun createOwnerDevicesOperations(
        graph: OwnerDependencyGraph,
        repository: DevicesRepository,
        assignments: TankDeviceAssignmentRepository
    ): DefaultOwnerDevicesOperations = DefaultOwnerDevicesOperations(
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

    private companion object {
        val OWNER_BINDINGS: Set<Class<out ViewModel>> = setOf(
            SettingsViewModel::class.java,
            DataManagementViewModel::class.java,
            DeviceStatusViewModel::class.java,
            DevicesViewModel::class.java,
            DeviceAddViewModel::class.java,
            DeviceQrScanViewModel::class.java,
            DeviceProvisioningProgressViewModel::class.java,
            AquariumTankViewModel::class.java,
            MaintenanceViewModel::class.java,
            DeviceLightRootViewModel::class.java,
            DeviceCoolingRootViewModel::class.java,
            DeviceTimerRootViewModel::class.java,
            DeviceDosingRootViewModel::class.java,
            DeviceDosingCalibrationViewModel::class.java,
            DeviceRootOverviewViewModel::class.java,
            DeviceFamilySettingsViewModel::class.java,
            DeviceFirmwareUpdateViewModel::class.java,
            TankDetailDevicesViewModel::class.java,
            TankDeviceSelectViewModel::class.java
        )
    }
}
