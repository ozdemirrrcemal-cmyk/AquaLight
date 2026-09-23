package com.aqua.aqualight.composition

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.application.user.UserProfileOperations
import com.aqua.aqualight.data.aquarium.DefaultAquariumTankOperations
import com.aqua.aqualight.data.aquarium.delete.OwnerTankDataCleaner
import com.aqua.aqualight.data.aquarium.delete.OwnerTankDeletionDependencies
import com.aqua.aqualight.data.aquarium.delete.TankCareDeletionDependencies
import com.aqua.aqualight.data.aquarium.delete.TankHealthDeletionDependencies
import com.aqua.aqualight.data.aquarium.devices.DefaultTankDeviceAssignmentOperations
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.care.DefaultMaintenanceOperations
import com.aqua.aqualight.data.care.integrity.restoreTaskSnapshotsForIntegrity
import com.aqua.aqualight.data.care.integrity.snapshotTasksForIntegrity
import com.aqua.aqualight.data.devices.DefaultDeviceFamilySettingsOperations
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.DefaultDeviceStatusOperations
import com.aqua.aqualight.data.devices.DefaultOwnerDevicesOperations
import com.aqua.aqualight.data.devices.cooling.DefaultDeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.data.devices.cooling.DefaultDeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.data.devices.cooling.control.DefaultDeviceCoolingControlOperations
import com.aqua.aqualight.data.devices.cooling.program.DefaultDeviceCoolingProgramOperations
import com.aqua.aqualight.data.devices.menu.DefaultDeviceMenuAccessOperations
import com.aqua.aqualight.data.devices.provisioning.DefaultProvisioningDiscoveryOperations
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleDeviceInfoPreflightClient
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
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.automatic.DeviceCoolingAutomaticSettingsViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.history.DeviceCoolingTemperatureHistoryViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.manual.DeviceCoolingManualSettingsViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.program.DeviceCoolingProgramSettingsViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.DeviceCoolingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status.DeviceCoolingSystemStatusViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration.DeviceDosingChannelCalibrationViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail.DeviceDosingChannelDetailViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DeviceDosingPlanViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir.DeviceDosingReservoirViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.root.DeviceDosingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.programs.DeviceLightAutomaticProgramsViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor.DeviceLightAutomaticProgramEditorViewModel
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
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.ui.tabs.settings.SettingsViewModel
import com.aqua.aqualight.ui.tabs.settings.app.DataManagementViewModel
import com.aqua.aqualight.ui.tabs.settings.device.DeviceStatusViewModel
import com.aqua.aqualight.ui.tabs.settings.device.SystemDeviceStatusClock

/** Exact ViewModel bindings that require one committed authenticated-owner graph. */
internal class OwnerViewModelFactory(
    context: Context,
    userProfileOperations: UserProfileOperations,
    notificationPreferenceUseCase: NotificationPreferenceUseCase,
    private val ownerGraphResolver: OwnerDependencyGraphResolver
) : ScopedViewModelFactory {

    private val appContext = context.applicationContext
    private val services = OwnerViewModelFactoryServices(
        userProfileOperations = userProfileOperations,
        notificationPreferenceUseCase = notificationPreferenceUseCase,
        appTextResolver = AndroidAppTextResolver(appContext),
        maintenanceTextResolver =
            AndroidMaintenanceTextResolver(appContext)
    )

    override fun supports(
        modelClass: Class<out ViewModel>
    ): Boolean = modelClass in OWNER_BINDINGS

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        check(
            modelClass !=
                DeviceLightQuickSetupViewModel::class.java
        ) {
            "DeviceLightQuickSetupViewModel requires " +
                "CreationExtras for SavedStateHandle."
        }
        return createInternal(
            modelClass = modelClass,
            quickSetupSavedStateHandle = null
        )
    }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras
    ): T = createInternal(
        modelClass = modelClass,
        quickSetupSavedStateHandle =
            quickSetupHandle(modelClass, extras)
    )

    private fun <T : ViewModel> createInternal(
        modelClass: Class<T>,
        quickSetupSavedStateHandle: SavedStateHandle?
    ): T {
        check(supports(modelClass)) {
            "No owner-scoped ViewModel binding for " +
                modelClass.name + "."
        }
        val context = OwnerViewModelBindingContext(
            appContext = appContext,
            graph = ownerGraphResolver.requireActive(),
            services = services,
            quickSetupSavedStateHandle =
                quickSetupSavedStateHandle
        )
        val viewModel =
            OwnerGeneralViewModelBindings.create(
                modelClass,
                context
            )
                ?: OwnerLightViewModelBindings.create(
                    modelClass,
                    context
                )
                ?: OwnerCoolingTimerViewModelBindings.create(
                    modelClass,
                    context
                )
                ?: OwnerDosingTankViewModelBindings.create(
                    modelClass,
                    context
                )
                ?: error(
                    "Unreachable owner ViewModel binding: " +
                        modelClass.name
                )

        return modelClass.cast(viewModel)
    }

    private fun <T : ViewModel> quickSetupHandle(
        modelClass: Class<T>,
        extras: CreationExtras
    ): SavedStateHandle? =
        if (
            modelClass ==
            DeviceLightQuickSetupViewModel::class.java
        ) {
            extras.createSavedStateHandle()
        } else {
            null
        }

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
            DeviceLightAdaptationViewModel::class.java,
            DeviceLightAutomaticProgramsViewModel::class.java,
            DeviceLightAutomaticProgramEditorViewModel::class.java,
            DeviceLightManualControlViewModel::class.java,
            DeviceLightCustomCurveViewModel::class.java,
            DeviceLightQuickSetupViewModel::class.java,
            DeviceLightLibraryViewModel::class.java,
            DeviceLightSystemViewModel::class.java,
            DeviceCoolingRootViewModel::class.java,
            DeviceCoolingTemperatureHistoryViewModel::class.java,
            DeviceCoolingSystemStatusViewModel::class.java,
            DeviceCoolingAutomaticSettingsViewModel::class.java,
            DeviceCoolingManualSettingsViewModel::class.java,
            DeviceCoolingProgramSettingsViewModel::class.java,
            DeviceTimerRootViewModel::class.java,
            DeviceTimerChannelViewModel::class.java,
            DeviceTimerProgramViewModel::class.java,
            DeviceDosingRootViewModel::class.java,
            DeviceDosingChannelCalibrationViewModel::class.java,
            DeviceDosingChannelDetailViewModel::class.java,
            DeviceDosingPlanViewModel::class.java,
            DeviceDosingReservoirViewModel::class.java,
            DeviceRootOverviewViewModel::class.java,
            DeviceFamilySettingsViewModel::class.java,
            DeviceFirmwareUpdateViewModel::class.java,
            TankDetailDevicesViewModel::class.java,
            TankDeviceSelectViewModel::class.java
        )
    }
}

private data class OwnerViewModelFactoryServices(
    val userProfileOperations: UserProfileOperations,
    val notificationPreferenceUseCase:
        NotificationPreferenceUseCase,
    val appTextResolver: AndroidAppTextResolver,
    val maintenanceTextResolver:
        AndroidMaintenanceTextResolver
)

private class OwnerViewModelBindingContext(
    val appContext: Context,
    val graph: OwnerDependencyGraph,
    val services: OwnerViewModelFactoryServices,
    val quickSetupSavedStateHandle: SavedStateHandle?
) {
    val repository: DevicesRepository
        get() = graph.devicesRepository

    val assignments: TankDeviceAssignmentRepository
        get() = graph.assignmentRepository

    val rootOperations: DefaultDeviceRootOperations
        get() = DefaultDeviceRootOperations(repository)

    fun deviceMenuOpenUseCase(): DeviceMenuOpenUseCase =
        DeviceMenuOpenUseCase(
            menuAccessOperations =
                DefaultDeviceMenuAccessOperations.create(
                    repository
                ),
            controlSurfacePreparationOperations =
                graph.controlSurfacePreparationOperations
        )

    fun ownerDevicesOperations():
        DefaultOwnerDevicesOperations =
        DefaultOwnerDevicesOperations(
            devicesRepository = repository,
            assignmentRepository = assignments,
            deviceDataCleaner =
                OwnerDeviceDataCleaner.create(
                    repository,
                    assignments
                ),
            cleanupDeletedDeviceNotifications = {
                    deviceUids ->
                graph.deviceFirmwareNotifications
                    .clearDeletedDevices(
                        ownerUid = graph.ownerUid,
                        deviceUids = deviceUids
                    )
            }
        )

    fun aquariumTankOperations():
        DefaultAquariumTankOperations =
        DefaultAquariumTankOperations(
            context = appContext,
            tankStore = graph.aquariumTankStore,
            healthStore = graph.aquariumHealthStore,
            tankDataCleaner = OwnerTankDataCleaner(
                OwnerTankDeletionDependencies(
                    deleteTankRecords =
                        graph.aquariumTankStore::deleteTanks,
                    care = careDeletionDependencies(),
                    health = healthDeletionDependencies(),
                    removeDeviceAssignmentsForTank =
                        assignments::removeAssignmentsForTank
                )
            ),
            notificationPreferences =
                services.notificationPreferenceUseCase
        )

    private fun careDeletionDependencies() =
        TankCareDeletionDependencies(
            snapshotForTank = { tankId ->
                graph.careTaskStore
                    .snapshotTasksForIntegrity(tankId)
            },
            deleteForTank =
                graph.careTaskStore::deleteTasksForTank,
            restoreForTank = { tankId, snapshots ->
                graph.careTaskStore
                    .restoreTaskSnapshotsForIntegrity(
                        tankId,
                        snapshots
                    )
            },
            cancelCareTaskReminder =
                services.notificationPreferenceUseCase::
                    cancelCareTask,
            reconcileReminders =
                services.notificationPreferenceUseCase::
                    reconcileOwner
        )

    private fun healthDeletionDependencies() =
        TankHealthDeletionDependencies(
            snapshotForTank =
                graph.aquariumHealthStore.integrity::
                    snapshotForTank,
            deleteForTank =
                graph.aquariumHealthStore.integrity::
                    deleteRecordsForTank,
            restoreForTank =
                graph.aquariumHealthStore.integrity::
                    restoreSnapshotForIntegrity
        )
}

private object OwnerGeneralViewModelBindings {

    fun create(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? =
        createAccountAndDevice(modelClass, context)
            ?: createProvisioningAndAquarium(
                modelClass,
                context
            )

    private fun createAccountAndDevice(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? = when (modelClass) {
        SettingsViewModel::class.java ->
            SettingsViewModel(
                userProfileOperations =
                    context.services.userProfileOperations,
                deviceStatusOperations =
                    DefaultDeviceStatusOperations(
                        context.repository
                    )
            )

        DataManagementViewModel::class.java ->
            DataManagementViewModel(
                archiveOperations =
                    context.graph.userDataArchiveOperations
            )

        DeviceStatusViewModel::class.java ->
            DeviceStatusViewModel(
                operations = DefaultDeviceStatusOperations(
                    context.repository
                ),
                clock = SystemDeviceStatusClock()
            )

        DevicesViewModel::class.java ->
            DevicesViewModel(
                operations =
                    context.ownerDevicesOperations(),
                menuOpenUseCase =
                    context.deviceMenuOpenUseCase(),
                routeResolver = DeviceRouteResolver()
            )

        else -> null
    }

    private fun createProvisioningAndAquarium(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? = when (modelClass) {
        DeviceAddViewModel::class.java ->
            DeviceAddViewModel(
                discoveryOperations =
                    context.discoveryOperations(),
                textResolver =
                    context.services.appTextResolver
            )

        DeviceQrScanViewModel::class.java ->
            DeviceQrScanViewModel(
                discoveryOperations =
                    context.discoveryOperations(),
                textResolver =
                    context.services.appTextResolver
            )

        DeviceProvisioningProgressViewModel::class.java ->
            DeviceProvisioningProgressViewModel(
                operations =
                    DefaultProvisioningProgressOperations(
                        context = context.appContext,
                        ownerUid = context.graph.ownerUid,
                        draftStore = AqlProvisioningDraftStore(
                            context = context.appContext,
                            ownerUidProvider = {
                                context.graph.ownerUid
                            }
                        )
                    ),
                menuOpenUseCase =
                    context.deviceMenuOpenUseCase(),
                textResolver =
                    context.services.appTextResolver
            )

        AquariumTankViewModel::class.java ->
            AquariumTankViewModel(
                operations =
                    context.aquariumTankOperations()
            )

        MaintenanceViewModel::class.java ->
            MaintenanceViewModel(
                operations = DefaultMaintenanceOperations(
                    context = context.appContext,
                    manager = context.graph.careTaskStore,
                    notificationPreferences =
                        context.services
                            .notificationPreferenceUseCase
                ),
                textResolver =
                    context.services.maintenanceTextResolver
            )

        else -> null
    }
}

private fun OwnerViewModelBindingContext.discoveryOperations() =
    DefaultProvisioningDiscoveryOperations(
        scanner = DefaultBleProvisioningScanner(appContext),
        repository = repository,
        qrParser = AqlProvisioningQrParser(),
        qrSecretStore = AqlProvisioningQrSecretStore(
            context = appContext,
            ownerUidProvider = { graph.ownerUid }
        ),
        manualPreflightClient =
            AqlBleDeviceInfoPreflightClient(appContext)
    )

private object OwnerLightViewModelBindings {

    fun create(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? =
        createControl(modelClass, context)
            ?: createAdvanced(modelClass, context)

    private fun createControl(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? = when (modelClass) {
        DeviceLightRootViewModel::class.java ->
            DeviceLightRootViewModel(
                rootOperations = context.rootOperations,
                lightControlOperations =
                    context.graph.lightOperations
                        .controlOperations,
                controlSurfacePreparationOperations =
                    context.graph
                        .controlSurfacePreparationOperations
            )

        DeviceLightAdaptationViewModel::class.java ->
            DeviceLightAdaptationViewModel(
                operations =
                    context.graph.lightOperations
                        .adaptationOperations,
                rootOperations = context.rootOperations
            )

        DeviceLightAutomaticProgramsViewModel::class.java ->
            DeviceLightAutomaticProgramsViewModel(
                operations =
                    context.graph.lightOperations
                        .automaticOperations,
                rootOperations = context.rootOperations
            )

        DeviceLightAutomaticProgramEditorViewModel::class.java ->
            DeviceLightAutomaticProgramEditorViewModel(
                operations =
                    context.graph.lightOperations
                        .automaticOperations,
                rootOperations = context.rootOperations
            )

        else -> null
    }

    private fun createAdvanced(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? = when (modelClass) {
        DeviceLightManualControlViewModel::class.java ->
            DeviceLightManualControlViewModel(
                manualOperations =
                    context.graph.lightOperations
                        .manualOperations,
                libraryOperations =
                    context.graph.lightOperations
                        .libraryOperations,
                rootOperations = context.rootOperations
            )

        DeviceLightCustomCurveViewModel::class.java ->
            DeviceLightCustomCurveViewModel(
                customOperations =
                    context.graph.lightOperations
                        .customOperations,
                libraryOperations =
                    context.graph.lightOperations
                        .libraryOperations,
                rootOperations = context.rootOperations
            )

        DeviceLightQuickSetupViewModel::class.java ->
            DeviceLightQuickSetupViewModel(
                savedStateHandle = checkNotNull(
                    context.quickSetupSavedStateHandle
                ),
                operations =
                    context.graph.lightOperations
                        .quickSetupOperations
            )

        DeviceLightLibraryViewModel::class.java ->
            DeviceLightLibraryViewModel(
                operations =
                    context.graph.lightOperations
                        .libraryOperations,
                rootOperations = context.rootOperations
            )

        DeviceLightSystemViewModel::class.java ->
            DeviceLightSystemViewModel(
                operations =
                    context.graph.lightOperations
                        .systemOperations,
                rootOperations = context.rootOperations
            )

        else -> null
    }
}

private object OwnerCoolingTimerViewModelBindings {

    fun create(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? =
        createCoolingPrimary(modelClass, context)
            ?: createCoolingSettings(modelClass, context)
            ?: createTimer(modelClass, context)

    private fun createCoolingPrimary(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? = when (modelClass) {
        DeviceCoolingRootViewModel::class.java ->
            DeviceCoolingRootViewModel(
                operations = context.rootOperations,
                controlOperations =
                    DefaultDeviceCoolingControlOperations(
                        context.repository
                    ),
                historyOperations =
                    DefaultDeviceCoolingTemperatureHistoryOperations(
                        context.repository
                    ),
                automaticSettingsOperations =
                    DefaultDeviceCoolingAutomaticSettingsOperations(
                        context.repository
                    ),
                controlSurfacePreparationOperations =
                    context.graph
                        .controlSurfacePreparationOperations
            )

        DeviceCoolingTemperatureHistoryViewModel::class.java ->
            DeviceCoolingTemperatureHistoryViewModel(
                DefaultDeviceCoolingTemperatureHistoryOperations(
                    context.repository
                )
            )

        DeviceCoolingSystemStatusViewModel::class.java ->
            DeviceCoolingSystemStatusViewModel(
                rootOperations = context.rootOperations,
                controlOperations =
                    DefaultDeviceCoolingControlOperations(
                        context.repository
                    )
            )

        else -> null
    }

    private fun createCoolingSettings(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? = when (modelClass) {
        DeviceCoolingAutomaticSettingsViewModel::class.java ->
            DeviceCoolingAutomaticSettingsViewModel(
                DefaultDeviceCoolingAutomaticSettingsOperations(
                    context.repository
                )
            )

        DeviceCoolingManualSettingsViewModel::class.java ->
            DeviceCoolingManualSettingsViewModel(
                DefaultDeviceCoolingControlOperations(
                    context.repository
                )
            )

        DeviceCoolingProgramSettingsViewModel::class.java ->
            DeviceCoolingProgramSettingsViewModel(
                operations =
                    DefaultDeviceCoolingProgramOperations(
                        context.repository
                    )
            )

        else -> null
    }

    private fun createTimer(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? = when (modelClass) {
        DeviceTimerRootViewModel::class.java ->
            DeviceTimerRootViewModel(
                operations = context.rootOperations,
                timerControlOperations =
                    context.graph.timerControlOperations,
                controlSurfacePreparationOperations =
                    context.graph
                        .controlSurfacePreparationOperations
            )

        DeviceTimerProgramViewModel::class.java ->
            DeviceTimerProgramViewModel(
                context.graph.timerControlOperations
            )

        DeviceTimerChannelViewModel::class.java ->
            DeviceTimerChannelViewModel(
                context.graph.timerControlOperations
            )

        else -> null
    }
}

private object OwnerDosingTankViewModelBindings {

    fun create(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? =
        createDosing(modelClass, context)
            ?: createRootAndTank(modelClass, context)

    private fun createDosing(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? = when (modelClass) {
        DeviceDosingRootViewModel::class.java ->
            DeviceDosingRootViewModel(
                operations = context.rootOperations,
                channelNavigationOperations =
                    context.graph.dosingOperations
                        .navigationOperations,
                channelOperations =
                    context.graph.dosingOperations
                        .channelOperations,
                controlSurfacePreparationOperations =
                    context.graph
                        .controlSurfacePreparationOperations
            )

        DeviceDosingChannelCalibrationViewModel::class.java ->
            DeviceDosingChannelCalibrationViewModel(
                operations =
                    context.graph.dosingOperations
                        .calibrationOperations,
                draftOperations =
                    context.graph.dosingOperations
                        .calibrationDraftOperations
            )

        DeviceDosingChannelDetailViewModel::class.java ->
            DeviceDosingChannelDetailViewModel(
                context.graph.dosingOperations
                    .channelOperations
            )

        DeviceDosingPlanViewModel::class.java ->
            DeviceDosingPlanViewModel(
                context.graph.dosingOperations
                    .channelOperations
            )

        DeviceDosingReservoirViewModel::class.java ->
            DeviceDosingReservoirViewModel(
                context.graph.dosingOperations
                    .channelOperations
            )

        else -> null
    }

    private fun createRootAndTank(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? =
        createRootAndSettings(modelClass, context)
            ?: createTankDevices(modelClass, context)

    private fun createRootAndSettings(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? = when (modelClass) {
        DeviceRootOverviewViewModel::class.java ->
            DeviceRootOverviewViewModel(
                context.rootOperations
            )

        DeviceFamilySettingsViewModel::class.java ->
            DeviceFamilySettingsViewModel(
                settingsOperations =
                    DefaultDeviceFamilySettingsOperations(
                        devicesRepository =
                            context.repository
                    ),
                firmwareUpdateOperations =
                    context.graph
                        .firmwareUpdateOperations,
                manifestUrl =
                    BuildConfig.AQL_OTA_MANIFEST_URL
            )

        DeviceFirmwareUpdateViewModel::class.java ->
            DeviceFirmwareUpdateViewModel(
                rootOperations = context.rootOperations,
                firmwareUpdateOperations =
                    context.graph
                        .firmwareUpdateOperations,
                manifestUrl =
                    BuildConfig.AQL_OTA_MANIFEST_URL
            )

        else -> null
    }

    private fun createTankDevices(
        modelClass: Class<out ViewModel>,
        context: OwnerViewModelBindingContext
    ): ViewModel? = when (modelClass) {
        TankDetailDevicesViewModel::class.java ->
            TankDetailDevicesViewModel(
                assignmentOperations =
                    DefaultTankDeviceAssignmentOperations(
                        context.assignments,
                        context.repository
                    ),
                menuOpenUseCase =
                    context.deviceMenuOpenUseCase(),
                routeResolver = DeviceRouteResolver(),
                lightCardOperations =
                    context.graph.lightOperations
                        .cardOperations,
                dosingCardOperations =
                    context.graph.dosingOperations
                        .cardOperations,
                coolingCardOperations =
                    context.graph.coolingCardOperations
            )

        TankDeviceSelectViewModel::class.java ->
            TankDeviceSelectViewModel(
                assignmentOperations =
                    DefaultTankDeviceAssignmentOperations(
                        context.assignments,
                        context.repository
                    )
            )

        else -> null
    }

}