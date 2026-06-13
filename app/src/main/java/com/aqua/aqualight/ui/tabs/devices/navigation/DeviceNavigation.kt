package com.aqua.aqualight.ui.tabs.devices.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import com.aqua.aqualight.NavAppDirections
import com.aqua.aqualight.R

enum class DeviceRouterBackStackMode {
    /**
     * Cihaz menüsünden geri dönünce çağıran ekrana dönülür.
     * Örnek: Devices listesinden cihaz açıldıysa geri Devices listesine döner.
     * Örnek: Tank detail içinden cihaz açıldıysa geri Tank detail ekranına döner.
     */
    KEEP_CALLER,

    /**
     * Cihaz ekleme/kurulum akışı tamamlanmıştır.
     * Cihaz menüsünden geri dönünce DeviceAdd/DeviceSetup ekranlarına değil,
     * doğrudan Devices ana listesine dönülür.
     */
    RETURN_TO_DEVICES
}

fun NavController.navigateToDeviceRouter(
    deviceId: Long,
    deviceIp: String = "",
    deviceTitle: String = "",
    backStackMode: DeviceRouterBackStackMode = DeviceRouterBackStackMode.KEEP_CALLER
) {
    navigate(
        NavAppDirections.actionGlobalDeviceRouterFragment(
            deviceId = deviceId,
            deviceIp = deviceIp,
            deviceTitle = deviceTitle
        ),
        createDeviceRouterNavOptions(
            backStackMode = backStackMode
        )
    )
}

private fun createDeviceRouterNavOptions(
    backStackMode: DeviceRouterBackStackMode
): NavOptions {
    return navOptions {
        anim {
            enter = R.anim.nav_slide_in_right
            exit = R.anim.nav_slide_out_left
            popEnter = R.anim.nav_slide_in_left
            popExit = R.anim.nav_slide_out_right
        }

        when (backStackMode) {
            DeviceRouterBackStackMode.KEEP_CALLER -> {
                // Çağıran ekran back stack'te kalır.
            }

            DeviceRouterBackStackMode.RETURN_TO_DEVICES -> {
                popUpTo(R.id.devicesFragment) {
                    inclusive = false
                }
                launchSingleTop = true
            }
        }
    }
}
