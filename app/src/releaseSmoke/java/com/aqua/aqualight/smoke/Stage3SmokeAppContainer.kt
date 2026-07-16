package com.aqua.aqualight.smoke

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.application.auth.AccountSecurityOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftOperations
import com.aqua.aqualight.application.feedback.FeedbackSubmissionUseCase
import com.aqua.aqualight.application.auth.SessionExitOperations
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
import com.aqua.aqualight.data.care.AndroidMaintenanceTextResolver
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.DefaultMaintenanceRepository
import com.aqua.aqualight.data.devices.DefaultDeviceFirmwareUpdateOperations
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.DefaultDeviceStatusOperations
import com.aqua.aqualight.data.devices.DefaultOwnerDevicesOperations
import com.aqua.aqualight.data.devices.menu.DefaultDeviceMenuAccessOperations
import com.aqua.aqualight.data.devices.remove.OwnerDeviceDataCleaner
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.user.StartupAppearanceCache
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.platform.auth.GoogleIdentityClient
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select.TankDeviceSelectViewModel
import com.aqua.aqualight.ui.tabs.devices.DevicesViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootOverviewViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.DeviceCoolingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DeviceDosingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerRootViewModel
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.ui.tabs.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class Stage3SmokeAppContainer(context: Context) : AppContainer {
    private val profileOperations = SmokeUserProfileOperations()

    override val defaultViewModelFactory: ViewModelProvider.Factory =
        Stage3SmokeViewModelFactory(context.applicationContext, profileOperations)

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
    override val feedbackSubmissionOperations: FeedbackSubmissionUseCase
        get() = unused("feedbackSubmissionOperations")
    override val provisioningDraftOperations: ProvisioningDraftOperations
        get() = unused("provisioningDraftOperations")
    override val sessionExitOperations: SessionExitOperations
        get() = unused("sessionExitOperations")
    override val accountSecurityOperations: AccountSecurityOperations
        get() = unused("accountSecurityOperations")
    override val googleIdentityClient: GoogleIdentityClient
        get() = unused("googleIdentityClient")

    private fun <T> unused(name: String): T =
        error("Release smoke dependency was not expected: $name")
}

private class Stage3SmokeViewModelFactory(
    context: Context,
    private val profileOperations: UserProfileOperations
) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext
    private val devicesRepository = DevicesRepository()
    private val tankStore = AquariumTankDataStoreManager(appContext)
    private val careTaskStore = CareTaskDataStoreManager.create(appContext)
    private val assignmentRepository = TankDeviceAssignmentRepository(
        ownerUid = SMOKE_OWNER_UID,
        devicesRepository = devicesRepository,
        assignmentStore = TankDeviceAssignmentStore.get(appContext),
        tankStore = tankStore
    )
    private val maintenanceRepository = DefaultMaintenanceRepository(
        context = appContext,
        manager = careTaskStore
    )
    private val maintenanceTextResolver = AndroidMaintenanceTextResolver(appContext)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    userProfileOperations = profileOperations,
                    deviceStatusOperations = DefaultDeviceStatusOperations(devicesRepository)
                )

            modelClass.isAssignableFrom(DevicesViewModel::class.java) ->
                DevicesViewModel(
                    operations = DefaultOwnerDevicesOperations(
                        devicesRepository = devicesRepository,
                        assignmentRepository = assignmentRepository,
                        deviceDataCleaner = OwnerDeviceDataCleaner.create(
                            devicesRepository = devicesRepository,
                            assignmentRepository = assignmentRepository
                        )
                    ),
                    menuAccessOperations =
                        DefaultDeviceMenuAccessOperations.create(devicesRepository),
                    routeResolver = DeviceRouteResolver()
                )

            modelClass.isAssignableFrom(AquariumTankViewModel::class.java) ->
                AquariumTankViewModel(
                    operations = DefaultAquariumTankOperations(
                        tankStore = tankStore,
                        careTaskStore = careTaskStore,
                        tankDataCleaner = OwnerTankDataCleaner(
                            deleteTankRecords = tankStore::deleteTanks,
                            deleteCareTasksForTank = careTaskStore::deleteTasksForTank,
                            removeDeviceAssignmentsForTank =
                                assignmentRepository::removeAssignmentsForTank
                        )
                    )
                )

            modelClass.isAssignableFrom(MaintenanceViewModel::class.java) ->
                MaintenanceViewModel(
                    repository = maintenanceRepository,
                    textResolver = maintenanceTextResolver
                )

            modelClass.isAssignableFrom(DeviceLightRootViewModel::class.java) ->
                DeviceLightRootViewModel(
                    rootOperations = DefaultDeviceRootOperations(devicesRepository),
                    firmwareUpdateOperations =
                        DefaultDeviceFirmwareUpdateOperations(devicesRepository)
                )

            modelClass.isAssignableFrom(DeviceCoolingRootViewModel::class.java) ->
                DeviceCoolingRootViewModel(DefaultDeviceRootOperations(devicesRepository))

            modelClass.isAssignableFrom(DeviceTimerRootViewModel::class.java) ->
                DeviceTimerRootViewModel(DefaultDeviceRootOperations(devicesRepository))

            modelClass.isAssignableFrom(DeviceDosingRootViewModel::class.java) ->
                DeviceDosingRootViewModel(DefaultDeviceRootOperations(devicesRepository))

            modelClass.isAssignableFrom(DeviceRootOverviewViewModel::class.java) ->
                DeviceRootOverviewViewModel(DefaultDeviceRootOperations(devicesRepository))

            modelClass.isAssignableFrom(TankDetailDevicesViewModel::class.java) ->
                TankDetailDevicesViewModel(
                    assignmentOperations = DefaultTankDeviceAssignmentOperations(
                        assignmentRepository = assignmentRepository,
                        devicesRepository = devicesRepository
                    ),
                    menuAccessOperations =
                        DefaultDeviceMenuAccessOperations.create(devicesRepository),
                    routeResolver = DeviceRouteResolver()
                )

            modelClass.isAssignableFrom(TankDeviceSelectViewModel::class.java) ->
                TankDeviceSelectViewModel(
                    assignmentOperations = DefaultTankDeviceAssignmentOperations(
                        assignmentRepository = assignmentRepository,
                        devicesRepository = devicesRepository
                    )
                )

            else -> error("Release smoke factory has no binding for ${modelClass.name}")
        }

        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }

    private companion object {
        const val SMOKE_OWNER_UID = "stage3-release-smoke-owner"
    }
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
