package com.aqua.aqualight.data.devices.routing

import android.content.Context
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceControllerRoute
import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.toControllerRoute
import kotlinx.coroutines.flow.first

/**
 * Resolves the commercial controller destination for a saved Aqua device.
 *
 * The Fragment must not know how a device is loaded, how catalog definitions are
 * resolved, or how a user-facing controller title is chosen. Keeping those rules
 * here makes deep links, device-list clicks and post-setup routing use the same
 * business contract.
 */
class DeviceRouterUseCase(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val devicesStore =
        DevicesDataStoreManager.create(
            appContext
        )

    private val tankStore =
        AquariumTankDataStoreManager(
            appContext
        )

    suspend fun resolveDestination(
        request: DeviceRouterRequest
    ): DeviceRouterDestination {
        if (request.deviceId <= 0L) {
            return DeviceRouterDestination.Unsupported(
                title = DEFAULT_DEVICE_TITLE,
                message = "Device information could not be found."
            )
        }

        val device = devicesStore.devicesFlow
            .first()
            .firstOrNull { savedDevice ->
                savedDevice.id == request.deviceId
            }
            ?: return DeviceRouterDestination.Unsupported(
                title = "Device Not Found",
                message = "This device is no longer available."
            )

        val definition = AquaDeviceCatalog.findDefinition(
            productId = device.productId,
            productKey = device.productKey,
            category = device.category
        ) ?: return DeviceRouterDestination.Unsupported(
            title = device.productModel.ifBlank {
                "Unsupported Device"
            },
            message = "This device is not supported by this app version."
        )

        val routerTitle = resolveRouterTitle(
            productModel = device.productModel,
            definition = definition
        )

        val controllerTitle = request.deviceTitle.ifBlank {
            resolveControllerTitleFromDevice(
                device = device,
                fallbackDeviceTitle = routerTitle
            )
        }

        return when (definition.category.toControllerRoute()) {
            AquaDeviceControllerRoute.LIGHT -> {
                DeviceRouterDestination.Controller(
                    deviceId = device.id,
                    deviceTitle = controllerTitle,
                    controller = DeviceRouterController.LIGHT
                )
            }

            AquaDeviceControllerRoute.DOSING -> {
                DeviceRouterDestination.Controller(
                    deviceId = device.id,
                    deviceTitle = controllerTitle,
                    controller = DeviceRouterController.DOSING
                )
            }

            AquaDeviceControllerRoute.TIMER -> {
                DeviceRouterDestination.Controller(
                    deviceId = device.id,
                    deviceTitle = controllerTitle,
                    controller = DeviceRouterController.TIMER
                )
            }

            AquaDeviceControllerRoute.COOLING -> {
                DeviceRouterDestination.Controller(
                    deviceId = device.id,
                    deviceTitle = controllerTitle,
                    controller = DeviceRouterController.COOLING
                )
            }

            AquaDeviceControllerRoute.UNSUPPORTED -> {
                DeviceRouterDestination.Unsupported(
                    title = routerTitle,
                    message = "This device controller is not supported by this app version."
                )
            }
        }
    }

    private suspend fun resolveControllerTitleFromDevice(
        device: DevicesDataStoreManager.DeviceInfo,
        fallbackDeviceTitle: String
    ): String {
        val tanks = tankStore.tanksFlow.first()

        val assignedTankName = device.tankId?.let { tankId ->
            tanks.firstOrNull { tank ->
                tank.id == tankId
            }?.name
        }.orEmpty()

        return assignedTankName.ifBlank {
            device.name.ifBlank {
                fallbackDeviceTitle
            }
        }
    }

    private fun resolveRouterTitle(
        productModel: String,
        definition: AquaDeviceDefinition
    ): String {
        return productModel.ifBlank {
            definition.displayName.ifBlank {
                DEFAULT_DEVICE_TITLE
            }
        }
    }

    private companion object {
        const val DEFAULT_DEVICE_TITLE = "Device"
    }
}

data class DeviceRouterRequest(
    val deviceId: Long,
    val deviceTitle: String
)

sealed class DeviceRouterDestination {

    data class Controller(
        val deviceId: Long,
        val deviceTitle: String,
        val controller: DeviceRouterController
    ) : DeviceRouterDestination()

    data class Unsupported(
        val title: String,
        val message: String
    ) : DeviceRouterDestination()
}

enum class DeviceRouterController {
    LIGHT,
    DOSING,
    TIMER,
    COOLING
}
