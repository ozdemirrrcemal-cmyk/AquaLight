package com.aqua.aqualight.data.aquarium.devices

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.card.DeviceCardStateMapper
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Data source for Aquarium > Tank detail device cards.
 *
 * This repository owns only the shared assigned-device stream, shared presence
 * state and the pluggable runtime contract. It does not depend on any concrete
 * device module such as Light.
 */
class TankAssignedDevicesRepository(
    context: Context,
    private val devicesStore: DevicesDataStoreManager = DevicesDataStoreManager.create(
        context.applicationContext
    ),
    private val runtimeDataSource: TankDeviceRuntimeDataSource = NoTankDeviceRuntimeDataSource,
    private val cardStateMapper: DeviceCardStateMapper = DeviceCardStateMapper()
) {

    private val appContext =
        context.applicationContext

    @OptIn(ExperimentalCoroutinesApi::class)
    fun assignedDeviceCardsFlow(
        tankId: Long,
        unknownAquariumText: String
    ): Flow<List<TankAssignedDeviceCardSnapshot>> {
        if (tankId <= 0L) {
            return flowOf(
                emptyList()
            )
        }

        DevicePresenceMonitor.start(
            context = appContext
        )

        return devicesStore.devicesForTankFlow(
            tankId = tankId
        ).flatMapLatest { devices ->
            combine(
                DevicePresenceMonitor.statuses,
                runtimeDataSource.observeRuntimeSnapshots(
                    devices = devices
                )
            ) { statuses, runtimeSnapshots ->
                val now =
                    System.currentTimeMillis()

                devices.map { device ->
                    val commonCard =
                        cardStateMapper.map(
                            device = device,
                            statuses = statuses,
                            nowMillis = now,
                            unknownTankText = unknownAquariumText
                        )

                    TankAssignedDeviceCardSnapshot(
                        device = device,
                        commonCard = commonCard,
                        runtime = runtimeSnapshots[device.id]
                    )
                }
            }
        }
    }

    suspend fun removeDeviceFromTank(
        deviceId: Long
    ) {
        devicesStore.removeDeviceFromTank(
            deviceId = deviceId
        )
    }
}
