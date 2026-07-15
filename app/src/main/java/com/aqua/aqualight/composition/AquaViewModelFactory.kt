package com.aqua.aqualight.composition

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.application.user.UserProfileOperations
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
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

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    userProfileOperations = userProfileOperations,
                    devicesRepository = DevicesRepositoryProvider.get(appContext)
                )
            }

            modelClass.isAssignableFrom(DeviceStatusViewModel::class.java) -> {
                DeviceStatusViewModel(
                    devicesRepository = DevicesRepositoryProvider.get(appContext)
                )
            }

            else -> return fallbackFactory.create(modelClass)
        }

        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }
}
