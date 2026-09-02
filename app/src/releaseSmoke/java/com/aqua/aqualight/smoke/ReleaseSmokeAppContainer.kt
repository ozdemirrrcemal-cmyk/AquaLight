package com.aqua.aqualight.smoke

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.application.auth.AccountSecurityOperations
import com.aqua.aqualight.application.auth.AppSessionOperations
import com.aqua.aqualight.application.auth.AuthenticatedOwnerIdentity
import com.aqua.aqualight.application.auth.SessionExitOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftOperations
import com.aqua.aqualight.application.feedback.FeedbackSubmissionUseCase
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.application.user.LocalDataRecoveryOperations
import com.aqua.aqualight.application.user.UserAddressInput
import com.aqua.aqualight.application.user.UserProfileOperations
import com.aqua.aqualight.application.user.UserProfileSnapshot
import com.aqua.aqualight.application.user.UserSettingsOperations
import com.aqua.aqualight.composition.AppContainer
import com.aqua.aqualight.data.aquarium.DefaultAquariumTankOperations
import com.aqua.aqualight.data.aquarium.delete.OwnerTankDataCleaner
import com.aqua.aqualight.data.aquarium.devices.DefaultTankDeviceAssignmentOperations
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentStore
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.AppSessionCoordinator
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.DefaultMaintenanceOperations
import com.aqua.aqualight.data.care.integrity.restoreTaskSnapshotsForIntegrity
import com.aqua.aqualight.data.care.integrity.snapshotTasksForIntegrity
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.DefaultDeviceStatusOperations
import com.aqua.aqualight.data.devices.DefaultOwnerDevicesOperations
import com.aqua.aqualight.data.devices.cooling.DefaultDeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.data.devices.cooling.DefaultDeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.data.devices.cooling.control.DefaultDeviceCoolingControlOperations
import com.aqua.aqualight.data.devices.menu.DefaultDeviceMenuAccessOperations
import com.aqua.aqualight.data.devices.provisioning.DefaultProvisioningDiscoveryOperations
import com.aqua.aqualight.data.devices.provisioning.DefaultProvisioningProgressOperations
import com.aqua.aqualight.data.devices.remove.OwnerDeviceDataCleaner
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.data.recovery.DefaultLocalDataRecoveryOperations
import com.aqua.aqualight.data.user.StartupAppearanceCache
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.platform.auth.GoogleIdentityClient
import com.aqua.aqualight.platform.media.ImageMediaProcessor
import com.aqua.aqualight.platform.text.AndroidAppTextResolver
import com.aqua.aqualight.platform.text.AndroidMaintenanceTextResolver
import com.aqua.aqualight.platform.vision.MlKitProvisioningQrFrameDecoderFactory
import com.aqua.aqualight.platform.vision.ProvisioningQrFrameDecoderFactory
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select.TankDeviceSelectViewModel
import com.aqua.aqualight.ui.tabs.devices.DevicesViewModel
import com.aqua.aqualight.ui.tabs.devices.add.DeviceAddViewModel
import com.aqua.aqualight.ui.tabs.devices.add.DeviceProvisioningProgressViewModel
import com.aqua.aqualight.ui.tabs.devices.add.DeviceQrScanViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootOverviewViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.root.DeviceCoolingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerRootViewModel
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.ui.tabs.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * CI-only release-smoke container.
 *
 * Dosing is intentionally absent: Dosing acceptance runs only through the real production owner
 * composition against physical devices, never through a smoke or fixture implementation.
 */
internal class ReleaseSmokeAppContainer(context: Context) : AppContainer {
    override val appSessionOperations: AppSessionOperations =
        AppSessionCoordinator.create(context.applicationContext)
    override val localDataRecoveryOperations: LocalDataRecoveryOperations =
        DefaultLocalDataRecoveryOperations
    private val profileOperations = SmokeUserProfileOperations()

    override val defaultViewModelFactory: ViewModelProvider.Factory =
        ReleaseSmokeViewModelFactory(context.applicationContext, profileOperations)

    override val authViewModelFactory: ViewModelProvider.Factory
        get() = defaultViewModelFactory
    override val userProfileOperations: UserProfileOperations
        get() = profileOperations
    override val startupAppearanceCache: StartupAppearanceCache
        get() = unused("startupAppearanceCache")
    override val userPreferencesManager: UserPreferencesManager
        get() = unused("userPreferencesManager")
    override val userSettingsOperations: UserSettingsOperations
        get() = unused("userSettingsOperations")
    override val notificationPreferenceUseCase: NotificationPreferenceUseCase
        get() = unused("notificationPreferenceUseCase")
    override val notificationDispatchUseCase: NotificationDispatchUseCase
        get() = unused("notificationDispatchUseCase")
    override val authenticatedOwnerIdentity: AuthenticatedOwnerIdentity =
        AuthenticatedOwnerIdentity { SMOKE_OWNER_UID }
    override val feedbackSubmissionOperations: FeedbackSubmissionUseCase
        get() = unused("feedbackSubmissionOperations")
    override val imageMediaProcessor: ImageMediaProcessor
        get() = unused("imageMediaProcessor")
    override val provisioningDraftOperations: ProvisioningDraftOperations
        get() = unused("provisioningDraftOperations")
    override val provisioningQrFrameDecoderFactory: ProvisioningQrFrameDecoderFactory =
        MlKitProvisioningQrFrameDecoderFactory()
    override val sessionExitOperations: SessionExitOperations
        get() = unused("sessionExitOperations")
    override val accountSecurityOperations: AccountSecurityOperations
        get() = unused("accountSecurityOperations")
    override val googleIdentityClient: GoogleIdentityClient
        get() = unused("googleIdentityClient")

    private fun <T> unused(name: String): T =
        error("Release smoke dependency was not expected: $name")

    private companion object {
        const val SMOKE_OWNER_UID = "release-smoke-owner"
    }
}

private class ReleaseSmokeViewModelFactory(
    context: Context,
    private val profileOperations: UserProfileOperations
) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext
    private val notificationPreferences = NotificationPlatform.get(appContext).preferenceUseCase
    private val devicesRepository = DevicesRepository()
    private val tankStore = AquariumTankDataStoreManager(appContext)
    private val careTaskStore = CareTaskDataStoreManager.create(appContext)
    private val assignmentRepository = TankDeviceAssignmentRepository(
        ownerUid = SMOKE_OWNER_UID,
        devicesRepository = devicesRepository,
        assignmentStore = TankDeviceAssignmentStore.get(appContext),
        tankStore = tankStore
    )
    private val maintenanceOperations = DefaultMaintenanceOperations(
        context = appContext,
        manager = careTaskStore,
        notificationPreferences = notificationPreferences
    )
    private val maintenanceTextResolver = AndroidMaintenanceTextResolver(appContext)
    private val appTextResolver = AndroidAppTextResolver(appContext)
    private val deviceMenuOpenUseCase = DeviceMenuOpenUseCase(
        menuAccessOperations = DefaultDeviceMenuAccessOperations.create(devicesRepository),
        controlSurfacePreparationOperations = ReleaseSmokeControlSurfacePreparationOperations
    )

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel = createPrimaryViewModel(modelClass)
            ?: createDeviceRootViewModel(modelClass)
            ?: createTankDeviceViewModel(modelClass)
            ?: error("Release smoke factory has no binding for ${modelClass.name}")

        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }

    private fun createPrimaryViewModel(modelClass: Class<out ViewModel>): ViewModel? = when {
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(
                userProfileOperations = profileOperations,
                deviceStatusOperations = DefaultDeviceStatusOperations(devicesRepository)
            )
        modelClass.isAssignableFrom(DevicesViewModel::class.java) -> createDevicesViewModel()
        modelClass.isAssignableFrom(DeviceAddViewModel::class.java) ->
            DeviceAddViewModel(
                discoveryOperations = DefaultProvisioningDiscoveryOperations.create(
                    context = appContext,
                    repository = devicesRepository
                ),
                textResolver = appTextResolver
            )
        modelClass.isAssignableFrom(DeviceQrScanViewModel::class.java) ->
            DeviceQrScanViewModel(
                discoveryOperations = DefaultProvisioningDiscoveryOperations.create(
                    context = appContext,
                    repository = devicesRepository
                ),
                textResolver = appTextResolver
            )
        modelClass.isAssignableFrom(DeviceProvisioningProgressViewModel::class.java) ->
            DeviceProvisioningProgressViewModel(
                operations = DefaultProvisioningProgressOperations(appContext),
                menuOpenUseCase = deviceMenuOpenUseCase,
                textResolver = appTextResolver
            )
        modelClass.isAssignableFrom(AquariumTankViewModel::class.java) -> createAquariumTankViewModel()
        modelClass.isAssignableFrom(MaintenanceViewModel::class.java) ->
            MaintenanceViewModel(
                operations = maintenanceOperations,
                textResolver = maintenanceTextResolver
            )
        else -> null
    }

    private fun createDevicesViewModel(): DevicesViewModel =
        DevicesViewModel(
            operations = DefaultOwnerDevicesOperations(
                devicesRepository = devicesRepository,
                assignmentRepository = assignmentRepository,
                deviceDataCleaner = OwnerDeviceDataCleaner.create(
                    devicesRepository = devicesRepository,
                    assignmentRepository = assignmentRepository
                )
            ),
            menuOpenUseCase = deviceMenuOpenUseCase,
            routeResolver = DeviceRouteResolver()
        )

    private fun createAquariumTankViewModel(): AquariumTankViewModel =
        AquariumTankViewModel(
            operations = DefaultAquariumTankOperations(
                context = appContext,
                tankStore = tankStore,
                tankDataCleaner = OwnerTankDataCleaner(
                    deleteTankRecords = tankStore::deleteTanks,
                    snapshotCareTasksForTank = { tankId ->
                        careTaskStore.snapshotTasksForIntegrity(tankId)
                    },
                    deleteCareTasksForTank = careTaskStore::deleteTasksForTank,
                    restoreCareTasksForTank = { tankId, snapshots ->
                        careTaskStore.restoreTaskSnapshotsForIntegrity(
                            tankId = tankId,
                            snapshots = snapshots
                        )
                    },
                    removeDeviceAssignmentsForTank = assignmentRepository::removeAssignmentsForTank,
                    cancelCareTaskReminder = notificationPreferences::cancelCareTask,
                    reconcileCareReminders = notificationPreferences::reconcileOwner,
                    ownerUidProvider = { SMOKE_OWNER_UID }
                ),
                notificationPreferences = notificationPreferences
            )
        )

    private fun createDeviceRootViewModel(
        modelClass: Class<out ViewModel>
    ): ViewModel? = when {
        modelClass.isAssignableFrom(DeviceLightRootViewModel::class.java) ->
            DeviceLightRootViewModel(rootOperations = DefaultDeviceRootOperations(devicesRepository))
        modelClass.isAssignableFrom(DeviceCoolingRootViewModel::class.java) ->
            DeviceCoolingRootViewModel(
                operations = DefaultDeviceRootOperations(devicesRepository),
                controlOperations = DefaultDeviceCoolingControlOperations(devicesRepository),
                historyOperations = DefaultDeviceCoolingTemperatureHistoryOperations(devicesRepository),
                automaticSettingsOperations =
                    DefaultDeviceCoolingAutomaticSettingsOperations(devicesRepository)
            )
        modelClass.isAssignableFrom(DeviceTimerRootViewModel::class.java) ->
            DeviceTimerRootViewModel(DefaultDeviceRootOperations(devicesRepository))
        modelClass.isAssignableFrom(DeviceRootOverviewViewModel::class.java) ->
            DeviceRootOverviewViewModel(DefaultDeviceRootOperations(devicesRepository))
        else -> null
    }

    private fun createTankDeviceViewModel(
        modelClass: Class<out ViewModel>
    ): ViewModel? = when {
        modelClass.isAssignableFrom(TankDetailDevicesViewModel::class.java) ->
            TankDetailDevicesViewModel(
                assignmentOperations = DefaultTankDeviceAssignmentOperations(
                    assignmentRepository = assignmentRepository,
                    devicesRepository = devicesRepository
                ),
                menuOpenUseCase = deviceMenuOpenUseCase,
                routeResolver = DeviceRouteResolver()
            )
        modelClass.isAssignableFrom(TankDeviceSelectViewModel::class.java) ->
            TankDeviceSelectViewModel(
                assignmentOperations = DefaultTankDeviceAssignmentOperations(
                    assignmentRepository = assignmentRepository,
                    devicesRepository = devicesRepository
                )
            )
        else -> null
    }

    private companion object {
        const val SMOKE_OWNER_UID = "release-smoke-owner"
    }
}

private object ReleaseSmokeControlSurfacePreparationOperations :
    DeviceControlSurfacePreparationOperations {
    override suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult = DeviceControlSurfacePreparationResult.Ready
}

private class SmokeUserProfileOperations : UserProfileOperations {
    override val profile: Flow<UserProfileSnapshot> = MutableStateFlow(
        UserProfileSnapshot(
            username = "Release Smoke",
            email = "release-smoke@aqualight.invalid"
        )
    )

    override suspend fun updateProfilePhoto(photoUri: String) = Unit
    override suspend fun updateUsername(username: String) = Unit
    override suspend fun saveAddress(address: UserAddressInput) = Unit
}
