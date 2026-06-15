package com.aqua.aqualight.data.devices.add

import android.content.Context
import com.aqua.aqualight.data.devices.DeviceStoreWriter
import com.aqua.aqualight.data.devices.DevicesDataStoreManager

/**
 * Business entry point for the Add Device screen.
 *
 * The Fragment should not know how candidate discovery is loaded, how an already
 * connected device is persisted, or which candidate should open setup. Keeping
 * this flow here makes the add-device journey reusable, testable and consistent
 * with the rest of the device data layer.
 */
class DeviceAddUseCase(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val devicesStore =
        DevicesDataStoreManager.create(
            appContext
        )

    private val candidateLoader =
        DeviceAddCandidateLoader(
            context = appContext,
            devicesStore = devicesStore
        )

    private val deviceStoreWriter =
        DeviceStoreWriter(
            devicesStore = devicesStore
        )

    suspend fun loadCandidates(): List<DeviceAddCandidate> {
        return candidateLoader.loadCandidates()
    }

    suspend fun selectCandidate(
        candidate: DeviceAddCandidate
    ): DeviceAddSelection {
        return when (candidate.source) {
            DeviceAddSource.LOCAL_NETWORK -> {
                val device = candidate.localDevice
                    ?: throw DeviceAddFlowException(
                        error = DeviceAddFlowError.MISSING_LOCAL_DEVICE
                    )

                val savedDeviceId = deviceStoreWriter.saveDiscoveredDevice(
                    device = device
                )

                DeviceAddSelection.OpenDevice(
                    deviceId = savedDeviceId,
                    deviceTitle = candidate.displayName
                )
            }

            DeviceAddSource.SETUP_AP -> {
                DeviceAddSelection.OpenSetupFlow(
                    setupTarget = DeviceAddSetupTarget.fromCandidate(
                        candidate = candidate
                    )
                )
            }
        }
    }
}

sealed class DeviceAddSelection {

    data class OpenDevice(
        val deviceId: Long,
        val deviceTitle: String
    ) : DeviceAddSelection()

    data class OpenSetupFlow(
        val setupTarget: DeviceAddSetupTarget
    ) : DeviceAddSelection()
}

data class DeviceAddSetupTarget(
    val setupSsid: String,
    val displayName: String,
    val familyName: String,
    val productId: String,
    val productKey: String,
    val category: String,
    val setupCode: String,
    val setupShortId: String
) {

    companion object {

        fun fromCandidate(
            candidate: DeviceAddCandidate
        ): DeviceAddSetupTarget {
            return DeviceAddSetupTarget(
                setupSsid = candidate.setupSsid.orEmpty(),
                displayName = candidate.displayName,
                familyName = candidate.familyName,
                productId = candidate.productId,
                productKey = candidate.productKey.storageKey,
                category = candidate.category.storageKey,
                setupCode = candidate.setupCode,
                setupShortId = candidate.setupShortId.orEmpty()
            )
        }
    }
}

enum class DeviceAddFlowError {
    MISSING_LOCAL_DEVICE
}

class DeviceAddFlowException(
    val error: DeviceAddFlowError
) : Exception()
