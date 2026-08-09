package com.aqua.aqualight.composition

import android.content.Context
import androidx.lifecycle.ViewModel
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.application.user.UserProfileOperations
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
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.ui.tabs.settings.SettingsViewModel
import com.aqua.aqualight.ui.tabs.settings.app.DataManagementViewModel
import com.aqua.aqualight.ui.tabs.settings.device.DeviceStatusViewModel

/** Exact ViewModel bindings that require one committed authenticated-owner graph. */
internal class OwnerViewModelFactory(
    context: Context,
    private val userProfileOperations: UserProfileOperations,
    private val notificationPreferenceUseCase: NotificationPreferenceUseCase,
    private val ownerGraphResolver: OwnerDependencyGraphResolver
) : ScopedViewModelFactory {

    private val appContext = context.applicationContext

    override fun supports(modelClass: Class<out ViewModel>): Boolean = modelClass in OWNER_BINDINGS

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(supports(modelClass)) {
            "No owner-scoped ViewModel binding for ${modelClass.name}."
        }
        val viewModel = checkNotNull(
            OwnerViewModelBindings(
                context = appContext,
                userProfileOperations = userProfileOperations,
                notificationPreferenceUseCase = notificationPreferenceUseCase,
                graph = ownerGraphResolver.requireActive()
            ).create(modelClass)
        ) {
            "Owner ViewModel binding was registered but could not be created: ${modelClass.name}."
        }
        return modelClass.cast(viewModel)
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
